/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH145 — the binaries a content tree needs to be a complete description of a site.
 *
 * WHAT WAS MISSING
 * `shio/content/**` projected the posts; the bytes stayed on the server, id-keyed under
 * `store/file_source/<postId>/` (SH38). So a projected `File` field read as a bare UUID,
 * which cost three of the four things "content as files" is supposed to buy: the tree could
 * not be moved to another instance, a diff of it was not reviewable, and it was not a
 * complete description of the site.
 *
 * THE ADDRESSING QUESTION, AND WHY IT IS NOT THE POST'S URL
 * Every other projection path in Block I is a friendly URL, because that is a post's
 * identity. A `File` post has **no friendly URL** — `ShStaticFileService.upload` never
 * assigns one, which is exactly why SH172 had to invent the `id:` fallback. So an asset is
 * addressed the way the *server* already thinks about files: its **folder chain plus its
 * file name**, which is the pair `fileExists` checks and the pair the console's pre-upload
 * check uses. `shio/assets/<site><folder-chain>/<name>` is therefore both readable and
 * already unique, and it needs no change to how File posts are created.
 *
 * IDEMPOTENT BY PATH, WHICH IS THE WHOLE POINT
 * A second push must not upload a second copy. Resolution is by (site, folder, file name)
 * against the server's own inventory — not by a locally remembered id — so a fresh clone
 * with no base snapshot resolves exactly as an old one does. That is SH81's `post.upsert`
 * argument applied to bytes.
 *
 * PATHS ARE THE PROJECTION'S CURRENCY; IDS EXIST ONLY ON THE WIRE
 * The layering that keeps this from breaking the SH89 merge: an incoming post has its
 * `File` fields rewritten id → path *as it is read*, so the base, the server side and the
 * file side all speak paths and a File field does not look changed on every single sync.
 * The inverse happens only when a document is compiled. Nothing in between ever sees a UUID.
 */

/** The widget whose value is a `File` post's id. The catalogue is closed, so this is exact. */
const FILE_WIDGET = "File";

/** Where the bytes live, beside the content and folder roots. */
export const ASSETS_DIR = "shio/assets";

/**
 * `File`-field names per post-type, from the lossless defs (SH16).
 *
 * Nested fields count: a `Relator` holds sub-fields, and an image inside a repeatable item
 * is exactly as much of an asset as one at the top level.
 *
 * @param {object[]} defs post-type defs (`shio pull`'s source)
 * @returns {Map<string, Set<string>>} post-type name → field names
 */
export function fileFields(defs) {
  const map = new Map();
  for (const def of defs ?? []) {
    if (!def?.name) continue;
    const names = new Set();
    collectFileFields(def.fields, names);
    map.set(def.name, names);
  }
  return map;
}

function collectFileFields(fields, into) {
  for (const field of fields ?? []) {
    if (field?.widget === FILE_WIDGET && field.name) into.add(field.name);
    if (field?.fields) collectFileFields(field.fields, into);
  }
}

/**
 * Path segments that are not names (SH256).
 *
 * <p>An asset path is the only projection path whose output is consumed as a <b>filesystem
 * path</b> — `readAssetBytes`/`writeAssetBytes` join it under `shio/assets` — so a segment that
 * navigates instead of naming does not merely produce drift, it leaves the tree. `..` is the
 * escape; `.` is refused with it because it makes two paths for one asset, which breaks the
 * idempotence the whole design rests on (P5).</p>
 */
const TRAVERSAL = new Set([".", ".."]);

/** A separator inside a single name means the name is claiming to be a path. */
const SEPARATOR = /[\\/]/;

/**
 * The asset path for one server-side file, or **null** when the pair has no safe path (SH256).
 *
 * <h3>Why null and not a throw</h3>
 *
 * This runs while building the inventory from the server's own media listing, so the bad input
 * is somebody else's data — a `File` post whose name contains a separator. Taking a whole pull
 * down over one unprojectable asset is SH173's mistake (an un-addressable post in an unrelated
 * site aborting the command); the caller skips it, counts it, and the `File` field stays an id,
 * which `idsToPaths` already reports as unresolved.
 *
 * @param {{siteName: string, fileName: string}} item a `ShStaticFileItem`
 * @param {string} chain the containing folder's name chain, e.g. `/media`
 * @returns {string|null}
 */
export function assetPathFor(item, chain) {
  const folder = chain && chain !== "/" ? chain : "";
  const names = [item?.siteName, ...String(folder).split("/"), item?.fileName].filter(
    (part) => part !== "" && part != null,
  );
  // Every segment, not just the file name: a folder chain arrives from the sitemap and a site
  // name from the listing, and the pair only has to be wrong in one place to escape.
  if (names.some((part) => TRAVERSAL.has(part) || SEPARATOR.test(part))) return null;
  if (!item?.siteName || !item?.fileName) return null;
  return `${item.siteName}${folder}/${item.fileName}`;
}

/**
 * `Blog/media/logo.png` → `{ site: "Blog", folder: "/media", fileName: "logo.png" }`.
 *
 * `null` means "not path-shaped" — which is how every caller tells an asset path from a bare id
 * (SH145: a value that is not a path is an id already, and is left alone). A path-shaped value
 * that **navigates** is a different answer and it throws (SH256), because the two silent
 * readings are both wrong: treating it as an id sends it to the server as a dangling reference
 * and never mentions that the tree contains an attempted traversal, and treating it as a
 * location reads bytes from outside the tree and uploads them. Refusing and naming it is the
 * rule SH191's missing sidecar follows.
 */
export function assetLocationOf(path) {
  const parts = String(path).replace(/\\/g, "/").split("/").filter(Boolean);
  if (parts.length < 2) return null;
  const escaping = parts.filter((part) => TRAVERSAL.has(part));
  if (escaping.length) {
    throw new Error(
      `shio content: asset path "${path}" contains "${escaping[0]}", which navigates the ` +
        "filesystem instead of naming an asset — under `shio/assets` it can resolve outside " +
        "the project entirely. An asset path is `<site>/<folder chain>/<file name>`; fix the " +
        "`File` field that holds this value.",
    );
  }
  const site = parts[0];
  const fileName = parts[parts.length - 1];
  const chain = parts.slice(1, -1);
  return { site, folder: chain.length ? `/${chain.join("/")}` : "/", fileName };
}

/**
 * Rewrites a post's `File` fields from ids to asset paths (the read direction).
 *
 * An id with no known asset is **left exactly as it is** and reported. It happens for real
 * reasons — the File post lives in a site this run did not read, or it was deleted out from
 * under the reference — and inventing a path for it would put a broken path in the tree,
 * which pushes back as a failed upload instead of the dangling reference `shio verify`
 * already knows how to report.
 *
 * @returns {{ doc: object, unresolved: string[] }}
 */
export function idsToPaths(doc, fileFields, byId) {
  return rewrite(doc, fileFields, (value) => byId.get(value) ?? null);
}

/**
 * Rewrites a post's `File` fields from asset paths back to ids (the write direction).
 *
 * @returns {{ doc: object, unresolved: string[] }} `unresolved` names paths with no id
 */
export function pathsToIds(doc, fileFields, byPath) {
  return rewrite(doc, fileFields, (value) => byPath.get(value)?.id ?? null);
}

/**
 * One pass over the `File`-valued fields of a post.
 *
 * Only the fields the post-type declares as `File` are touched. Guessing from the shape of
 * a value — "this looks like a UUID" — would rewrite a `Text` field that happens to hold
 * one, and the projection has the model available, so it does not need to guess.
 */
function rewrite(doc, fileFields, map) {
  const names = fileFields.get(doc?.type) ?? new Set();
  if (!names.size) return { doc, unresolved: [] };

  const data = {};
  const unresolved = [];
  for (const [name, value] of Object.entries(doc.data ?? {})) {
    if (!names.has(name) || typeof value !== "string" || !value) {
      data[name] = value;
      continue;
    }
    const mapped = map(value);
    if (mapped === null) {
      unresolved.push(value);
      data[name] = value;
    } else {
      data[name] = mapped;
    }
  }
  return { doc: { ...doc, data }, unresolved };
}
