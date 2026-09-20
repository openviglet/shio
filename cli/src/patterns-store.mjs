/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1040 — a good block becomes a file: `shio/patterns/<name>.json`.
 *
 * ## What was missing
 *
 * Every blueprint is whole-site or whole-page, with a fixed target and no mode that inserts into an
 * existing page, and nothing captures an arrangement that already exists. So a curator who got a
 * pricing block right could only reuse it by rebuilding it — by hand, or by asking an agent to
 * rebuild it, which is the same work moved.
 *
 * ## A file in the tree, not a package in the registry
 *
 * SH1040's design said patterns join the blueprint registry. **They do not, and the reason is
 * where each thing lives.** The registry scans the classpath for a `blueprint.json` in every
 * package directory, plus one operator directory on the *server*; the tree is the *project*,
 * which the server cannot see and
 * has no business writing into. Putting a captured pattern into the registry therefore means
 * uploading it, which is the marketplace's job and a different task. A pattern is content the agent
 * authors, and P4 is explicit that files beat APIs for authoring: `Edit`, `Write` and `Grep` are
 * what makes a captured block reviewable in a pull request, which is the property the design was
 * actually after (it says so — "the shape Gutenberg reached by registering patterns as files").
 *
 * ## The format half is pure
 *
 * No fs and no network here, like `content-files.mjs`, `gates-store.mjs` and `releases-store.mjs`:
 * what a pattern looks like on disk is testable without an instance, and the command is then a thin
 * caller. Byte-stable output (sorted keys, two-space JSON, one trailing newline) so re-capturing an
 * unchanged page is an empty diff (P5).
 */

/** Where patterns land in the tree, beside `shio/content` rather than inside it. */
export const PATTERNS_DIR = "shio/patterns";

/** The format this module writes, so a reader can refuse one it does not understand. */
export const PATTERN_FORMAT = 1;

/** The folder a captured section lands in when the target page has none of its own. */
export const DEFAULT_SECTIONS_FOLDER = "/sections";

/** `{{name}}` — the same spelling a blueprint's parameters use, so one vocabulary covers both. */
const PLACEHOLDER = /\{\{\s*([A-Za-z0-9_.-]+)\s*}}/g;

export function patternPathFor(name) {
  return `${PATTERNS_DIR}/${name}.json`;
}

/**
 * A name that is safe as a file and as a URL segment.
 *
 * Refused rather than slugged: a pattern is addressed by the name somebody typed, and quietly
 * turning `My Block` into `my-block` means the next command's `apply my-block` is a name they never
 * chose and cannot guess (P5, and SH244's argument about server-chosen names).
 */
export function validPatternName(name) {
  return typeof name === "string" && /^[a-z0-9][a-z0-9-]*$/.test(name);
}

/**
 * What a captured pattern looks like on disk.
 *
 * `from` and `captured` are provenance and nothing reads them — which is the point: the first
 * question about a file like this is always *where did this come from*, and answering it in the
 * file costs one line and saves a search nobody can complete.
 */
export function renderPattern({ name, title, from, captured, sections, params }) {
  const document = {
    formatVersion: PATTERN_FORMAT,
    name,
    ...(title ? { title } : {}),
    ...(from ? { from } : {}),
    ...(captured ? { captured } : {}),
    ...(params && Object.keys(params).length ? { params: sortedKeys(params) } : {}),
    sections: (sections ?? []).map((section) => ({
      type: section.type,
      data: sortedKeys(section.data ?? {}),
    })),
  };
  return `${JSON.stringify(document, null, 2)}\n`;
}

/**
 * Reads one back, refusing what it cannot understand rather than applying half of it.
 *
 * A pattern writes posts into a site, so a file that parses to fewer sections than it has is a
 * silent partial apply — the shape every reader in this repository is written against.
 */
export function parsePattern(text, path = "<pattern>") {
  let document;
  try {
    document = JSON.parse(text);
  } catch (error) {
    throw new Error(`${path}: not valid JSON (${error.message}).`);
  }
  if (!document || typeof document !== "object" || Array.isArray(document)) {
    throw new Error(`${path}: a pattern is a JSON object with a \`sections\` array.`);
  }
  if (document.formatVersion !== PATTERN_FORMAT) {
    throw new Error(
      `${path}: formatVersion ${JSON.stringify(document.formatVersion)} — this shio writes and ` +
        `reads ${PATTERN_FORMAT}.`,
    );
  }
  if (!Array.isArray(document.sections) || document.sections.length === 0) {
    throw new Error(`${path}: \`sections\` is missing or empty, so there is nothing to place.`);
  }
  document.sections.forEach((section, index) => {
    if (!section || typeof section.type !== "string" || !section.type) {
      throw new Error(`${path}: section ${index} has no \`type\`, so nothing says what to create.`);
    }
  });
  return document;
}

/** Every `{{name}}` a pattern's values mention, in first-seen order. */
export function placeholdersIn(document) {
  const found = [];
  walk(document.sections, (text) => {
    for (const [, name] of text.matchAll(PLACEHOLDER)) {
      if (!found.includes(name)) found.push(name);
    }
  });
  return found;
}

/**
 * The pattern with its placeholders filled.
 *
 * **An unfilled placeholder is refused, and the refusal lists what the pattern declares.** The
 * alternative — leaving `{{headline}}` in the content — writes the literal braces onto a live page,
 * which is the failure mode that reads as a rendering bug three steps from its cause. P6: the error
 * carries what would fix it.
 */
export function fillPattern(document, values, path = "<pattern>") {
  const declared = document.params ?? {};
  const resolved = { ...defaultsOf(declared), ...(values ?? {}) };
  const missing = placeholdersIn(document).filter((name) => resolved[name] === undefined);
  if (missing.length) {
    const names = Object.keys(declared);
    throw new Error(
      `${path}: no value for ${missing.map((name) => `{{${name}}}`).join(", ")}. ` +
        (names.length
          ? `This pattern declares: ${names.join(", ")}. `
          : "This pattern declares no parameters, so every placeholder in it needs a --param. ") +
        `Example: shio patterns apply ${document.name ?? "<name>"} --at post:<site>/<url> ` +
        `--param ${missing[0]}=<value>`,
    );
  }
  return {
    ...document,
    sections: document.sections.map((section) => ({
      type: section.type,
      data: substitute(section.data ?? {}, resolved),
    })),
  };
}

/** Declared defaults, which is the half of a parameter a caller does not have to repeat. */
function defaultsOf(declared) {
  const out = {};
  for (const [name, spec] of Object.entries(declared)) {
    if (spec && typeof spec === "object" && spec.default !== undefined) out[name] = spec.default;
  }
  return out;
}

/**
 * Where the sections a pattern creates land, and what each one is called.
 *
 * Beside the page's existing sections when it has any, because that is the convention a conversion
 * and both blueprints already follow and a pattern must not invent a second one. Names are
 * **derived and therefore idempotent** (P5): applying the same pattern to the same page twice
 * writes the same posts rather than a second copy, and `--as` is how a caller asks for two.
 */
export function sectionUrlsFor({ instance, count, folder }) {
  const base = folder && folder.startsWith("/") ? folder.replace(/\/$/, "")
    : DEFAULT_SECTIONS_FOLDER;
  return Array.from({ length: count }, (unused, index) => `${base}/${instance}-${index + 1}`);
}

/** The folder part of a section's friendly URL, or `null` when it has none. */
export function folderOf(url) {
  if (typeof url !== "string" || !url.startsWith("/")) return null;
  const cut = url.lastIndexOf("/");
  return cut <= 0 ? "/" : url.slice(0, cut);
}

function substitute(value, values) {
  if (typeof value === "string") {
    return value.replace(PLACEHOLDER, (whole, name) =>
      (values[name] === undefined ? whole : String(values[name])));
  }
  if (Array.isArray(value)) return value.map((item) => substitute(item, values));
  if (value && typeof value === "object") {
    const out = {};
    for (const key of Object.keys(value).sort()) out[key] = substitute(value[key], values);
    return out;
  }
  return value;
}

function walk(value, onText) {
  if (typeof value === "string") {
    onText(value);
  } else if (Array.isArray(value)) {
    for (const item of value) walk(item, onText);
  } else if (value && typeof value === "object") {
    for (const item of Object.values(value)) walk(item, onText);
  }
}

function sortedKeys(object) {
  const out = {};
  for (const key of Object.keys(object).sort()) out[key] = object[key];
  return out;
}
