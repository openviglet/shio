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
 */

/** Frontmatter delimiter. */
const FENCE = "---";

/** Metadata key order — fixed, so a diff never shows a reordering. */
const META_ORDER = ["site", "url", "type", "folder", "publish", "body"];

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
 * The file path a post projects to, relative to the content root.
 *
 * @param {{ site: string, url: string }} doc
 * @returns {string} e.g. `Blog/blog/hello.md`
 */
export function filePathFor({ site, url }) {
  return `${siteDir(site)}/${urlToPath(url)}`;
}

/** The site's directory name. Refuses a name that would escape the tree. */
export function siteDir(site) {
  const name = String(site ?? "").trim();
  if (!name) throw new Error("shio content: a post must say which site it belongs to (`site:`).");
  if (/[\\/]|^\.+$/.test(name)) {
    throw new Error(
      `shio content: cannot project site "${name}" — a site name with a slash has no ` +
        "safe directory name. Rename the site, or pass --site to project a different one.",
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
 * @returns {string}
 */
export function serializePost(doc, bodyField = null) {
  const data = doc.data && typeof doc.data === "object" ? doc.data : {};
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
    // Recorded only when the body is actually in the document, so a file never claims a
    // body field whose value stayed in `fields`.
    body: body === null ? undefined : bodyField,
  };

  const lines = [FENCE];
  for (const key of META_ORDER) {
    if (meta[key] === undefined) continue;
    lines.push(`${key}: ${encodeScalar(meta[key])}`);
  }
  const fieldNames = Object.keys(data)
    .filter((name) => !(body !== null && name === bodyField))
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
 * @param {string} relPath path relative to the content root, e.g. `Blog/blog/hello.md`
 * @param {string} text    file contents
 * @returns {{ site, url, type, folder, publish, bodyField, data }}
 */
export function parsePost(relPath, text) {
  const { meta, fields, body } = splitDocument(relPath, text);
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
    bodyField,
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
 * Canonical form of a post: the exact bytes the file *would* hold.
 *
 * Useful for a whole-document comparison (has this file's content changed at all?) and for
 * asserting the format is a function of the content and nothing else. The three-way merge
 * does **not** use it — a merge has to know *which* fields differ, so `content-sync.mjs`
 * compares field by field.
 */
export function canonicalize(doc, bodyField = null) {
  return serializePost(doc, bodyField);
}

// --- internals ---

/** A body has to survive the round trip exactly, or it stays a field. */
function bodySafe(value) {
  return typeof value === "string" && value.trim() === value && value !== "";
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

/** Splits a file into frontmatter, fields and body — the only place the fence matters. */
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
  let inFields = false;
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
    if (!indented && key === "fields") {
      inFields = true;
      lastField = null;
      continue;
    }
    if (inFields && indented) {
      lastField = key;
      // `key:` with nothing after it opens a sequence (that is the only shape that emits
      // it), so an empty list stays an empty list rather than becoming null.
      fields[key] = rest.trim() === "" ? [] : decodeScalar(rest);
      continue;
    }
    inFields = false;
    lastField = null;
    meta[key] = decodeScalar(rest);
  }

  const body = lines
    .slice(end + 1)
    .join("\n")
    .replace(/^\n/, "")
    .replace(/\s+$/, "");
  return { meta, fields, body };
}

function fault(relPath, why) {
  return new Error(`shio content: ${relPath ? `${relPath}: ` : ""}${why}.`);
}
