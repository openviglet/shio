/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH86 — the filesystem projection format: one post, one Markdown file with
 * frontmatter. Pure (no fs, no network), like `post-types-io.mjs`, so the format is
 * unit-testable without a server and the commands stay thin.
 *
 * WHY A FILE AND NOT AN API CALL
 * An agent's cheapest tools are `Edit`, `Write` and `Grep` (design law P4). Reading a
 * post over HTTP, changing one paragraph and writing it back costs two round trips, a
 * full JSON body in and out, and an id it has to keep alive in between. The same edit
 * against a text tree costs one `Edit` and no protocol at all — and it is reviewable in
 * a diff, which is the property that makes a human comfortable letting an agent write.
 *
 * WHAT MAKES A FORMAT SAFE TO SYNC (the SH68/SH63 discipline, again)
 * One canonical serialization, byte-for-byte: keys in a fixed order, field names sorted,
 * one value encoding per shape. Two writers that disagree about formatting turn every
 * pull into a diff and every diff into a conflict, so the format has to be a function of
 * the content and nothing else (law P5).
 *
 * THE SETTLED DESIGN QUESTIONS
 *  - **Path = the friendly URL**, not the folder chain: a URL is unique per site by
 *    definition, so the tree can never collide, and it is the structure a person
 *    actually pictures. The folder is recorded in frontmatter because SH79 is explicit
 *    that a folder path and a URL are separate namespaces — deriving one from the other
 *    would place content in the wrong folder silently.
 *  - **Only DRAFT projects.** Publishing stays an explicit act (P7); a file tree that
 *    contained the published copy too would make "what does the site show?" a question
 *    about which of two files you happened to open.
 *  - **`fields:` is nested**, not flattened next to the metadata: a post-type is free to
 *    have a field called `type` or `url`, and a flat map would let content overwrite the
 *    document's own identity.
 *  - **Relator items and any other non-scalar stay JSON** on one line. A YAML rendering
 *    of arbitrary nesting is a second serialization format to specify and get wrong, and
 *    the write side already speaks JSON.
 *  - **Code gets a file of its own** (SH191, see `SIDECAR_WIDGETS`): one field, one
 *    sidecar, named in the frontmatter the way `body:` already names the document body.
 */

/** Frontmatter delimiter. */
const FENCE = "---";

/** Metadata key order — fixed, so a diff never shows a reordering. */
const META_ORDER = ["site", "url", "type", "folder", "publish", "movedFrom", "body"];

/**
 * The widgets whose value is **code**, and the extension each one's sidecar takes (SH191).
 *
 * <h3>Why the widget decides, and never the post-type</h3>
 *
 * A rule keyed on the post-type would work for exactly the three types SH183 ships and
 * stop working the moment somebody models a layout type of their own — which is the
 * expected case, since a layout is content. The widget is the thing that already says "this
 * value is a program": the console gives it a code editor for precisely that reason.
 *
 * <h3>Why only these two</h3>
 *
 * `HTML Editor` is the *body* (`bodyFieldFor`) and has been since SH86; moving it here
 * would change every existing file. `Text Area` is prose. What is left is the `Ace Editor`
 * pair, which is what a `PageLayout`'s `HTML`, a `Region`'s `HTML`/`JAVASCRIPT` and a
 * `Theme`'s `CSS`/`JAVASCRIPT` are declared as.
 *
 * A `Theme`'s `CSS` field is declared `Ace Editor - HTML` because no CSS widget is seeded,
 * so its sidecar is `.html` today — wrong for highlighting, right for lines, `Grep` and a
 * reviewable diff, which is the whole of law P4's claim. Fixing the extension means
 * seeding the widget; that is **SH252**, and this map is the one line it has to change.
 */
export const SIDECAR_WIDGETS = new Map([
  ["Ace Editor - HTML", "html"],
  ["Ace Editor - Javascript", "js"],
]);

/** The frontmatter key that names the sidecars — `body:` generalized past one field. */
const FILES_KEY = "files";

/**
 * Scalars that must be quoted because YAML would read them as something else, or
 * because the round trip would not be exact.
 */
const YAML_KEYWORDS = new Set(["true", "false", "null", "yes", "no", "on", "off", "~", ""]);
const NUMERIC = /^[-+]?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?$/;
const NEEDS_QUOTE_START = /^[-?:,[\]{}#&*!|>'"%@`]/;

/**
 * The field a post's body maps to: the first rich-text field by ordinal.
 *
 * The choice is narrow on purpose — only an `HTML Editor` is treated as the body, never
 * a `Text Area`. A rule that guessed at the "main" text field would move the body from
 * one field to another when someone added a field, which is a silent content change in a
 * diff that looks like a formatting change. The chosen name is written into the file as
 * `body:`, so reading a file back never depends on the model at all.
 *
 * @param {object} def a post-type def (`shio pull` shape) or `{ fields: [...] }`
 * @returns {string|null}
 */
export function bodyFieldFor(def) {
  const fields = (def?.fields ?? []).filter((field) => field?.name);
  const rich = fields
    .filter((field) => field.widget === "HTML Editor")
    .sort((a, b) => (a.ordinal ?? 0) - (b.ordinal ?? 0));
  return rich.length ? rich[0].name : null;
}

/**
 * The fields of a post-type that project to a sidecar file, sorted by name (SH191).
 *
 * Sorted rather than ordinal-ordered on purpose: the order is only ever used to emit the
 * `files:` block, and a diff must not move a line because somebody reordered fields in the
 * console. The body field is excluded — a post has one body and it is already a file.
 *
 * @param {object} def a post-type def (`shio pull` shape) or `{ fields: [...] }`
 * @returns {{name: string, ext: string}[]}
 */
export function sidecarFieldsFor(def) {
  const body = bodyFieldFor(def);
  return (def?.fields ?? [])
    .filter((field) => field?.name && field.name !== body && SIDECAR_WIDGETS.has(field.widget))
    .map((field) => ({ name: field.name, ext: SIDECAR_WIDGETS.get(field.widget) }))
    .sort((a, b) => a.name.localeCompare(b.name));
}

/**
 * A sidecar's file name — `<post stem>.<FIELD>.<ext>` (SH191).
 *
 * <h3>Why the field name is always in it</h3>
 *
 * `home.html` beside `home.md` is prettier, and it collides the moment a post-type declares
 * two `Ace Editor - HTML` fields — silently, because the second write would overwrite the
 * first and the file would still parse. Including the field unconditionally is the same
 * discipline `bodyFieldFor` follows: a rule that changes a file name when somebody adds a
 * field is a silent content move wearing a formatting diff.
 *
 * It is a **basename**, not a path: the sidecar always sits beside its `.md`, so there is
 * nowhere for a `/` or a `..` to be meaningful and every one is a traversal attempt.
 *
 * @param {string} postFileName the post's own file name, e.g. `hello.md`
 * @param {{name: string, ext: string}} field
 * @returns {string} e.g. `hello.HTML.html`
 */
export function sidecarNameFor(postFileName, { name, ext }) {
  const stem = String(postFileName).replace(/\.md$/i, "");
  if (/[\\/]/.test(name)) {
    throw new Error(
      `shio content: field "${name}" cannot project to a file — a field name with a slash ` +
        "has no safe file name.",
    );
  }
  return `${stem}.${name}.${ext}`;
}

/**
 * The file path a post projects to, relative to the content root.
 *
 * @param {{ site: string, url: string }} doc
 * @returns {string} e.g. `Blog/blog/hello.md`
 */
export function filePathFor({ site, url }) {
  return `${siteDir(site)}/${urlToPath(url)}`;
}

/**
 * The sidecar files one post projects to, as tree-relative paths and their contents (SH191).
 *
 * Only a field the post actually holds a non-empty string in gets a file: an absent or empty
 * `JAVASCRIPT` would otherwise litter the tree with empty programs, and — worse — a file
 * whose emptiness is indistinguishable from one whose content somebody deleted.
 *
 * @param {object} doc     the same shape `serializePost` takes
 * @param {{name: string, ext: string}[]} [sidecarFields]
 * @returns {{ field: string, path: string, name: string, text: string }[]}
 */
export function sidecarsOf(doc, sidecarFields = []) {
  const data = doc?.data && typeof doc.data === "object" ? doc.data : {};
  const relPath = filePathFor(doc);
  const dir = relPath.slice(0, relPath.lastIndexOf("/"));
  const fileName = relPath.slice(relPath.lastIndexOf("/") + 1);
  return sidecarFields
    .filter((field) => sidecarSafe(data[field.name]))
    .map((field) => {
      const name = sidecarNameFor(fileName, field);
      return {
        field: field.name,
        name,
        path: `${dir}/${name}`,
        // **Verbatim** — the one place in this format that normalizes nothing, and
        // deliberately unlike the body's CRLF → LF. A sidecar is read back as the whole file
        // with no framing to strip, so byte-for-byte is achievable here and it is what keeps
        // the round trip exact: normalize on the way out and a server value holding CRLF (or
        // a trailing blank line) reads back different from the fingerprint the base took
        // from the *server's* copy, and every later pull reports a "local change" nobody
        // made, forever, until somebody pushes. That is a live defect on the body field —
        // SH253 — and it is not worth reproducing on the artifact an iterative loop edits most.
        text: String(data[field.name]),
      };
    });
}

/**
 * The site's directory name. Refuses a name that has no faithful one.
 *
 * <p><b>Whitespace is refused rather than trimmed (SH257).</b> This used to `trim()` and keep
 * going, which made the projection write a file it could not read back: `filePathFor` produced
 * `Blog/x.md` from a site called `"Blog "` while `serializePost` wrote the untrimmed name into
 * the frontmatter, and `parsePost` then refused the pair — correctly, since a `site:` that
 * disagrees with its directory is SH134's half-done rename. So `shio pull --content` emitted a
 * tree that every later command failed on, and the message blamed the disagreement rather than
 * the trim that caused it.
 *
 * <p>Found while extending SH255's value table to the frontmatter, and worth being precise about
 * how: the table did <b>not</b> fail on it. It asserts "exact, or declared", and this case was
 * already a refusal — just the wrong one — so it satisfied the property. It came out of reading
 * the refusal's message. That gap is SH258.</p></p>
 *
 * <p>Refusing is the same answer the slash already gets, and for the same reason: a directory
 * name that is not the site's name is a lie the rest of the format is built on. Trimming into
 * the frontmatter too would have been the other candidate and is worse — the file would then
 * declare a site name that is not the server's, so a push would target or create the wrong
 * site.</p>
 */
export function siteDir(site) {
  const name = String(site ?? "");
  if (!name.trim()) {
    throw new Error("shio content: a post must say which site it belongs to (`site:`).");
  }
  if (/[\\/]|^\.+$/.test(name)) {
    throw new Error(
      `shio content: cannot project site "${name}" — a site name with a slash has no ` +
        "safe directory name. Rename the site, or pass --site to project a different one.",
    );
  }
  if (name !== name.trim()) {
    throw new Error(
      `shio content: cannot project site ${JSON.stringify(name)} — its name starts or ends ` +
        "with whitespace, so its directory name would not be its name and every file in it " +
        "would disagree with its own path. Rename the site (the surrounding space is almost " +
        "certainly a typo).",
    );
  }
  return name;
}

/** `/blog/hello` → `blog/hello.md`; the site home (`/`) → `index.md`. */
export function urlToPath(url) {
  const normalized = normalizeUrl(url);
  return normalized === "/" ? "index.md" : `${normalized.slice(1)}.md`;
}

/** `blog/hello.md` → `/blog/hello`; `index.md` → `/`. The inverse of {@link urlToPath}. */
export function pathToUrl(relPath) {
  const withoutExt = String(relPath).replace(/\.md$/i, "");
  const segments = withoutExt.split("/").filter((segment) => segment !== "");
  if (segments.length && segments[segments.length - 1] === "index") segments.pop();
  return segments.length ? `/${segments.join("/")}` : "/";
}

/** Leading slash, no trailing slash, no empty segments. */
export function normalizeUrl(url) {
  const segments = String(url ?? "")
    .split("/")
    .filter((segment) => segment !== "");
  return segments.length ? `/${segments.join("/")}` : "/";
}

/**
 * Serializes one post to its file contents.
 *
 * @param {object} doc
 * @param {string} doc.site    site name
 * @param {string} doc.url     friendly URL — the post's identity
 * @param {string} doc.type    post-type name
 * @param {string} doc.folder  containing folder path
 * @param {boolean} [doc.publish] published state, when known
 * @param {object} doc.data    field values keyed by attribute name
 * @param {string|null} [bodyField] field to render as the document body
 * @param {{name: string, ext: string}[]} [sidecarFields] fields that project to files (SH191)
 * @returns {string}
 */
export function serializePost(doc, bodyField = null, sidecarFields = []) {
  const data = doc.data && typeof doc.data === "object" ? doc.data : {};
  // SH191 — computed from the same function the writer uses, so a `files:` line can never
  // name a file the writer did not produce.
  const sidecars = sidecarFields.length ? sidecarsOf(doc, sidecarFields) : [];
  // The one normalization in the format: a body's line endings become LF. Without it a
  // server value holding CRLF would serialize one way and parse back another, and every
  // pull would report a change nobody made.
  const body =
    bodyField && bodySafe(data[bodyField])
      ? String(data[bodyField]).replace(/\r\n?/g, "\n")
      : null;
  const meta = {
    site: doc.site,
    url: normalizeUrl(doc.url),
    type: doc.type ?? null,
    folder: doc.folder == null ? null : normalizeUrl(doc.folder),
    publish: typeof doc.publish === "boolean" ? doc.publish : undefined,
    // SH150 — an instruction, like `publish`: it says where this post came from, so a push
    // can move it instead of re-creating it. A pull never writes one (the server has no such
    // thing to report), but it is preserved when a merge rewrites the file, because losing
    // the author's instruction mid-sync is how a rename turns back into a delete plus create.
    movedFrom: doc.movedFrom ? normalizeUrl(doc.movedFrom) : undefined,
    // Recorded only when the body is actually in the document, so a file never claims a
    // body field whose value stayed in `fields`.
    body: body === null ? undefined : bodyField,
  };

  const lines = [FENCE];
  for (const key of META_ORDER) {
    if (meta[key] === undefined) continue;
    lines.push(`${key}: ${encodeScalar(meta[key])}`);
  }
  // SH191 — after the metadata and before `fields:`, because a reader scanning the top of the
  // file is asking "what is this and where is its content", and the answer is now two places.
  if (sidecars.length) {
    lines.push(`${FILES_KEY}:`);
    for (const sidecar of sidecars) {
      lines.push(`  ${sidecar.field}: ${encodeScalar(sidecar.name)}`);
    }
  }
  const inFiles = new Set(sidecars.map((sidecar) => sidecar.field));
  const fieldNames = Object.keys(data)
    .filter((name) => !(body !== null && name === bodyField))
    .filter((name) => !inFiles.has(name))
    .sort();
  if (fieldNames.length) {
    lines.push("fields:");
    for (const name of fieldNames) {
      lines.push(...encodeField(name, data[name]));
    }
  }
  lines.push(FENCE);
  return `${lines.join("\n")}\n${body === null ? "" : `${body}\n`}`;
}

/* --- folder markers (SH149) --- */

/**
 * The file that gives a folder a presence on disk.
 *
 * A folder existed in the projection only because a post named it, so an empty section
 * could not be authored locally (there was no file to write) and a pull dropped one that
 * existed on the server (there was nothing to write it to). "Scaffold the structure, then
 * fill it" is how a site actually gets built, and that first step was the one step that
 * had to leave the file tree and go through the API.
 */
export const FOLDER_MARKER = "_folder.md";

/**
 * Where folder markers live — a root of their own, **beside** `shio/content`, never inside it.
 *
 * This is the load-bearing decision in SH149 and it follows straight from SH79: a folder
 * chain and a friendly URL are separate namespaces. Under `shio/content` the directories
 * are *URL* segments — `shio/content/Blog/blog/hello.md` is the post at `/blog/hello`,
 * whatever folder it lives in — so a marker at `shio/content/Blog/blog/_folder.md` would
 * be claiming that a URL prefix is a folder chain. It usually looks right, which is what
 * makes it dangerous: the one time the two disagree, the marker creates a folder nobody
 * asked for and the real one goes unmanaged. Two roots cannot make that mistake.
 */
export const FOLDERS_SUBDIR = "folders";

/**
 * The file path a folder marker projects to, relative to the folders root.
 *
 * @param {{ site: string, folder: string }} marker
 * @returns {string} e.g. `Blog/news/_folder.md`; a site root → `Blog/_folder.md`
 */
export function folderMarkerPathFor({ site, folder }) {
  const chain = normalizeUrl(folder);
  const dir = chain === "/" ? siteDir(site) : `${siteDir(site)}${chain}`;
  return `${dir}/${FOLDER_MARKER}`;
}

/** `Blog/news/_folder.md` → `{ site: "Blog", folder: "/news" }`. The inverse. */
export function folderLocationOf(relPath) {
  const parts = String(relPath).replace(/\\/g, "/").split("/").filter(Boolean);
  if (!parts.length) return {};
  if (parts[parts.length - 1].toLowerCase() === FOLDER_MARKER) parts.pop();
  if (!parts.length) return {};
  return { site: parts[0], folder: normalizeUrl(parts.slice(1).join("/")) };
}

/**
 * Serializes a folder marker.
 *
 * Frontmatter and nothing else: a folder has no content, so a body would be a place for
 * text that the CMS has nowhere to put. The two keys are the folder's whole identity, and
 * they are written even though the path implies them — a marker read on its own has to be
 * able to say what it is, and the path/frontmatter pair is what lets a half-done rename be
 * *caught* rather than guessed at (the same trade `parsePost` makes).
 *
 * @param {{ site: string, folder: string }} marker
 * @returns {string}
 */
export function serializeFolder({ site, folder }) {
  return `${FENCE}\nsite: ${encodeScalar(site)}\nfolder: ${encodeScalar(normalizeUrl(folder))}\n${FENCE}\n`;
}

/**
 * Parses a folder marker.
 *
 * @param {string} relPath path relative to the folders root, e.g. `Blog/news/_folder.md`
 * @param {string} text    file contents
 * @returns {{ site: string, folder: string }}
 */
export function parseFolderMarker(relPath, text) {
  const { meta, body } = splitDocument(relPath, text);
  const fromPath = relPath ? folderLocationOf(relPath) : {};

  const site = meta.site ?? fromPath.site;
  const folder = meta.folder == null ? fromPath.folder : normalizeUrl(meta.folder);
  if (!site) throw fault(relPath, "it has no `site:` and its path does not name one");
  if (folder == null) throw fault(relPath, "it has no `folder:` and its path does not imply one");
  if (meta.folder != null && fromPath.folder != null && normalizeUrl(meta.folder) !== fromPath.folder) {
    throw fault(
      relPath,
      `its \`folder: ${normalizeUrl(meta.folder)}\` disagrees with its path ` +
        `(${fromPath.folder}). One of them was renamed and the other was not — fix ` +
        "whichever is wrong. To move a folder, change both",
    );
  }
  if (meta.site && fromPath.site && meta.site !== fromPath.site) {
    throw fault(
      relPath,
      `its \`site: ${meta.site}\` disagrees with its directory (${fromPath.site})`,
    );
  }
  if (body) {
    // Not a warning: text in a marker is content somebody wrote expecting it to land
    // somewhere, and there is nowhere for it to go. Dropping it silently is worse.
    throw fault(
      relPath,
      "a folder marker has no body — a folder holds posts, not text. Move the text into " +
        "a post in this folder",
    );
  }
  return { site, folder };
}

/**
 * Parses one post file.
 *
 * `relPath` fills in `site` and `url` when the frontmatter omits them, so a file an
 * agent wrote by hand — the whole point of law P4 — is a valid post without ceremony.
 * When both are present and disagree, that is an **error**: a file renamed without its
 * frontmatter (or the reverse) is a move on one side and a create on the other, and
 * guessing which would either duplicate the post or delete the original. SH134 learned
 * this on the API side; the file side gets it for free by refusing.
 *
 * Since SH191 a post's code fields live in sidecar files that the frontmatter names, so what
 * comes back here is `sidecarFiles` — field → file name — and **not** their values: this
 * module is pure, and reading them is `content-store.mjs`' job.
 *
 * @param {string} relPath path relative to the content root, e.g. `Blog/blog/hello.md`
 * @param {string} text    file contents
 * @returns {{ site, url, type, folder, publish, bodyField, sidecarFiles, data }}
 */
export function parsePost(relPath, text) {
  const { meta, fields, files, body } = splitDocument(relPath, text);
  const fromPath = relPath ? locationOf(relPath) : {};

  const site = meta.site ?? fromPath.site;
  const url = meta.url ? normalizeUrl(meta.url) : fromPath.url;
  if (!site) throw fault(relPath, "it has no `site:` and its path does not name one");
  if (!url) throw fault(relPath, "it has no `url:` and its path does not imply one");
  if (meta.url && fromPath.url && normalizeUrl(meta.url) !== fromPath.url) {
    throw fault(
      relPath,
      `its \`url: ${normalizeUrl(meta.url)}\` disagrees with its path (${fromPath.url}). ` +
        "One of them was renamed and the other was not — fix whichever is wrong. " +
        "To move a post, change both",
    );
  }
  if (meta.site && fromPath.site && meta.site !== fromPath.site) {
    throw fault(
      relPath,
      `its \`site: ${meta.site}\` disagrees with its directory (${fromPath.site})`,
    );
  }

  if (meta.movedFrom && normalizeUrl(meta.movedFrom) === url) {
    // A post cannot have moved from where it already is, and the mistake this catches is the
    // likely one: the URL was edited and `movedFrom` was set to the *new* value rather than
    // the old. Left alone it compiles to a move onto itself, which the server then refuses
    // for a reason that says nothing about the actual error.
    throw fault(
      relPath,
      `its \`movedFrom: ${normalizeUrl(meta.movedFrom)}\` is its own URL. ` +
        "`movedFrom` is the URL this post used to have — the one that is no longer here",
    );
  }

  // SH191 — the sidecars, validated here and read by the store. Two refusals, both the
  // `url:`-versus-path rule applied to a second pair of places one value can live.
  const sidecarFiles = {};
  for (const [field, name] of Object.entries(files)) {
    if (!name) {
      throw fault(relPath, `its \`${FILES_KEY}: ${field}:\` names no file`);
    }
    if (/[\\/]|^\.\.?$/.test(name)) {
      // A sidecar always sits beside its `.md`, so a path separator here is never a location
      // — it is a way out of the tree, and this file may have arrived from anywhere.
      throw fault(
        relPath,
        `its \`${FILES_KEY}: ${field}: ${name}\` is a path, not a file name — a sidecar sits ` +
          "beside its post file",
      );
    }
    if (Object.hasOwn(fields, field)) {
      // Two places holding one field's value, and no way to tell which the author edited.
      // Whichever side won would silently discard the other, which is the failure this
      // format refuses everywhere else it can be caught.
      throw fault(
        relPath,
        `its \`${field}\` is in both \`${FILES_KEY}:\` (${name}) and \`fields:\`. One field has ` +
          `one value — delete whichever is stale`,
      );
    }
    if (field === meta.body) {
      throw fault(
        relPath,
        `its \`${field}\` is both the document body (\`body: ${field}\`) and a sidecar ` +
          `(\`${FILES_KEY}: ${field}: ${name}\`). One field has one value`,
      );
    }
    sidecarFiles[field] = name;
  }

  const data = { ...fields };
  const bodyField = meta.body ?? null;
  if (body !== null && body !== "") {
    if (!bodyField) {
      throw fault(
        relPath,
        "it has a body but no `body:` key saying which field the body is. Add " +
          "`body: <field name>` to the frontmatter, or move the text into `fields:`",
      );
    }
    data[bodyField] = body;
  }
  return {
    site,
    url,
    type: meta.type ?? null,
    folder: meta.folder == null ? null : normalizeUrl(meta.folder),
    publish: typeof meta.publish === "boolean" ? meta.publish : undefined,
    movedFrom: meta.movedFrom ? normalizeUrl(meta.movedFrom) : undefined,
    bodyField,
    sidecarFiles,
    data,
  };
}

/** `Blog/blog/hello.md` → `{ site: "Blog", url: "/blog/hello" }`. */
export function locationOf(relPath) {
  const parts = String(relPath).replace(/\\/g, "/").split("/").filter(Boolean);
  if (parts.length < 2) return {};
  return { site: parts[0], url: pathToUrl(parts.slice(1).join("/")) };
}

/**
 * The document as the file tree will hold it — the projection's own domain (SH253).
 *
 * <h3>The defect this exists to close</h3>
 *
 * `serializePost` normalizes a body's CRLF to LF, and its comment says why: "without it a
 * server value holding CRLF would serialize one way and parse back another, and every pull
 * would report a change nobody made". Sound about the file, wrong about the *comparison*,
 * because the base snapshot is not taken from the file — `content-pull.mjs` fingerprinted the
 * merge result, which for a post the server changed **is the server's document**, CRLF intact.
 * So a pull wrote LF to disk, recorded CRLF in the base, and every later pull found the two
 * different and reported a local change nobody made. Forever, until somebody pushed. The
 * normalization caused precisely the drift it was added to prevent.
 *
 * <h3>Why the fix is here and not in the snapshot</h3>
 *
 * Fingerprinting the projected document instead of the remote one is the obvious repair and it
 * is only half: measured, it turns a permanent `local-changed` into a permanent
 * `remote-changed` — the file converges and the pull still reports a write on every run,
 * because the *server's* value is still outside the domain everything else is compared in.
 * So the rule is one step earlier and one step wider: **a remote document is projected on the
 * way in, and the merge only ever compares documents that have been.** Then the base, the
 * server side and the file side all speak the same language and an unrepresentable value is
 * unchanged rather than eternally in flight.
 *
 * That is not a new idea in this codebase — SH145 does exactly this for `File` fields
 * (`idsToPaths` rewrites an id to a path as it is read, "so the base, the server side and the
 * file side all speak paths and a File field does not read as changed on every sync"). SH253
 * is that argument applied to every normalization the format has, instead of one at a time.
 *
 * <h3>Why it round-trips rather than reimplementing the rules</h3>
 *
 * It serializes and parses, so it is exact **by construction**: a normalization added to the
 * format cannot escape it, and there is no second list of "things the projection changes" to
 * keep in step with the first. That is the property the CRLF bug came from lacking.
 *
 * @param {object} doc
 * @param {string|null} [bodyField]
 * @param {{name: string, ext: string}[]} [sidecarFields]
 * @returns {object} the same document, as it reads back off disk
 */
export function projectedPost(doc, bodyField = null, sidecarFields = []) {
  const projected = parsePost(filePathFor(doc), serializePost(doc, bodyField, sidecarFields));
  // A sidecar's bytes are verbatim (SH191), so they come back exactly as they went out — but
  // they are not in the document `parsePost` returns, because reading them is the store's job.
  for (const sidecar of sidecarsOf(doc, sidecarFields)) projected.data[sidecar.field] = sidecar.text;
  // Carried rather than round-tripped: neither is state the merge compares (`publish` is an
  // instruction, `movedFrom` is one too), and both would be dropped by a projection that only
  // ever writes what the server reported.
  if (doc.publish !== undefined) projected.publish = doc.publish;
  if (doc.movedFrom !== undefined) projected.movedFrom = doc.movedFrom;
  return projected;
}

/**
 * Canonical form of a post: the exact bytes the file *would* hold.
 *
 * Useful for a whole-document comparison (has this file's content changed at all?) and for
 * asserting the format is a function of the content and nothing else. The three-way merge
 * does **not** use it — a merge has to know *which* fields differ, so `content-sync.mjs`
 * compares field by field.
 */
export function canonicalize(doc, bodyField = null, sidecarFields = []) {
  return serializePost(doc, bodyField, sidecarFields);
}

// --- internals ---

/** A body has to survive the round trip exactly, or it stays a field. */
function bodySafe(value) {
  return typeof value === "string" && value.trim() === value && value !== "";
}

/**
 * A sidecar holds a whole file, so any non-empty string survives it verbatim (SH191).
 *
 * The bar is lower than `bodySafe`'s on purpose: a body is framed by the rest of the
 * document and has to be told apart from it, while a sidecar *is* the file. An empty value
 * is excluded because an empty file cannot be told apart from one somebody emptied.
 */
function sidecarSafe(value) {
  return typeof value === "string" && value !== "";
}

function encodeField(name, value) {
  if (Array.isArray(value) && value.every(isScalar) && value.length) {
    return [`  ${name}:`, ...value.map((item) => `    - ${encodeScalar(item)}`)];
  }
  return [`  ${name}: ${encodeScalar(value)}`];
}

function isScalar(value) {
  return value === null || ["string", "number", "boolean"].includes(typeof value);
}

/**
 * One value encoding per shape. Anything that is not a plain scalar becomes compact
 * JSON — valid YAML flow style, and the exact bytes the write side accepts.
 */
function encodeScalar(value) {
  if (value === null || value === undefined) return "null";
  if (typeof value === "boolean" || typeof value === "number") return JSON.stringify(value);
  if (typeof value !== "string") return JSON.stringify(value);
  const needsQuote =
    YAML_KEYWORDS.has(value.toLowerCase()) ||
    NUMERIC.test(value) ||
    NEEDS_QUOTE_START.test(value) ||
    value !== value.trim() ||
    value.includes("\n") ||
    value.includes(": ") ||
    value.includes(" #");
  return needsQuote ? JSON.stringify(value) : value;
}

function decodeScalar(raw) {
  const text = raw.trim();
  if (text === "" || text === "null" || text === "~") return null;
  if (text === "true") return true;
  if (text === "false") return false;
  if (text.startsWith('"') || text.startsWith("[") || text.startsWith("{")) {
    try {
      return JSON.parse(text);
    } catch {
      // A value that looks like JSON and is not is far more likely to be prose than a
      // broken document, so it stays the string it is rather than failing the file.
      return text;
    }
  }
  if (NUMERIC.test(text)) return Number(text);
  return text;
}

/** Splits a file into frontmatter, fields, files and body — the only place the fence matters. */
function splitDocument(relPath, text) {
  const source = String(text ?? "").replace(/^﻿/, "");
  const lines = source.split(/\r?\n/);
  if (lines[0]?.trim() !== FENCE) {
    throw fault(
      relPath,
      "it does not start with a `---` frontmatter block. A content file starts with " +
        "`---`, then `site:`/`url:`/`type:`/`folder:`, then `---`, then the body",
    );
  }
  const end = lines.findIndex((line, index) => index > 0 && line.trim() === FENCE);
  if (end < 0) throw fault(relPath, "its frontmatter block is never closed by a second `---`");

  const meta = {};
  const fields = {};
  const files = {};
  // Which nested block the reader is inside: `fields`, `files` (SH191) or neither.
  let block = null;
  let lastField = null;
  for (const line of lines.slice(1, end)) {
    if (!line.trim() || line.trim().startsWith("#")) continue;
    if (/^\s+-\s/.test(line) && lastField) {
      const current = fields[lastField];
      const items = Array.isArray(current) ? current : [];
      items.push(decodeScalar(line.trim().slice(1)));
      fields[lastField] = items;
      continue;
    }
    const indented = /^\s+\S/.test(line);
    const split = line.indexOf(":");
    if (split < 0) continue;
    const key = line.slice(0, split).trim();
    const rest = line.slice(split + 1);
    if (!indented && (key === "fields" || key === FILES_KEY)) {
      block = key;
      lastField = null;
      continue;
    }
    if (block === FILES_KEY && indented) {
      // No sequences here: a field has exactly one sidecar, and `lastField` deliberately
      // stays null so a stray `- item` under `files:` cannot append to a field's value.
      files[key] = String(decodeScalar(rest) ?? "");
      continue;
    }
    if (block === "fields" && indented) {
      lastField = key;
      // `key:` with nothing after it opens a sequence (that is the only shape that emits
      // it), so an empty list stays an empty list rather than becoming null.
      fields[key] = rest.trim() === "" ? [] : decodeScalar(rest);
      continue;
    }
    block = null;
    lastField = null;
    meta[key] = decodeScalar(rest);
  }

  const body = lines
    .slice(end + 1)
    .join("\n")
    .replace(/^\n/, "")
    .replace(/\s+$/, "");
  return { meta, fields, files, body };
}

function fault(relPath, why) {
  return new Error(`shio content: ${relPath ? `${relPath}: ` : ""}${why}.`);
}
