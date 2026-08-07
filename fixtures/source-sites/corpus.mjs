/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH560 — the source corpus, and the two drivers that serve it.
 *
 * ## Why a corpus and not a bigger fixture
 *
 * `cli/conformance/support/fixture-site.mjs` is one source, and SH528 grows it: a query string, a
 * relative depth, an overlay, an entity, an empty file, a script-made canvas. That is right for
 * every trait on its list but one. **A trait can be a property of the site rather than of a page**,
 * and those do not compose into one server:
 *
 * - a source with **no home at `/`** cannot also be the source that answers `/`;
 * - a source that is **client-rendered end to end** cannot also be the one whose markup is complete;
 * - a source whose **every page is a query string** (SH557: 91 of 107) cannot also be the one whose
 *   paths address cleanly.
 *
 * So each archetype is a directory: bytes under `site/`, plus an `expectations.json` that declares
 * what the archetype is, what a capture of it should contain, and — the field that makes it worth
 * keeping — **the task ids it exists to catch**.
 *
 * ## One route table, two drivers
 *
 * The fast tier (`cli/test/`) must run with no instance, no browser and no socket, so it gets
 * `injectedFetch`. The live tier (`cli/conformance/`) must not decide the response's shape, so it
 * gets `serve` — a real `node:http` server. Both are built from **the same route table**, which is
 * the property that makes a divergence between the two tiers a statement about the boundary rather
 * than about the fixture. That is the same argument `fixture-site.mjs` makes about itself; the only
 * change here is that the bytes are on disk, where `shio-app` can read them too (SH562).
 *
 * ## Why the bytes are files and not a template literal
 *
 * Three reasons, in the order they bite. A file is what a **third consumer in another language** can
 * read — `ShConvertedLayoutRenderTest` already reaches across to `../cli/src/convert.mjs`, and a
 * Java test cannot call a JS function that returns a string. A file is **byte-stable** under P5: a
 * generator's output changes when somebody edits the generator, and a diff of the generator is not
 * a diff of the fixture. And a file is what a person **reads** when a run goes red — the archetype
 * is a catalogue entry, and a catalogue nobody can open is a list.
 */
import { createServer } from "node:http";
import * as nodeFs from "node:fs/promises";
import { join, posix, relative, sep } from "node:path";
import { fileURLToPath } from "node:url";

/** Where the archetypes live. Repo root, not `cli/`, because three suites in two languages read it. */
export const CORPUS_DIR = fileURLToPath(new URL(".", import.meta.url));

/** The file that declares one archetype. Its presence is what makes a directory an archetype. */
export const DECLARATION_FILE = "expectations.json";

/** The subdirectory whose tree mirrors the source's URL space. */
export const SITE_DIR = "site";

/**
 * Content types by extension.
 *
 * <p>Deliberately short, and deliberately explicit about the charset on HTML: `visit` refuses
 * anything that is not `text/html`, and `fetchBytes` records what arrived, so a wrong type here
 * would produce a `notHtml` row rather than a page and the archetype would assert nothing.
 */
const TYPES = new Map(Object.entries({
  ".html": "text/html; charset=utf-8",
  ".css": "text/css",
  ".js": "application/javascript",
  ".json": "application/json",
  ".txt": "text/plain",
  ".xml": "application/xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".woff2": "font/woff2",
  ".pdf": "application/pdf",
}));

function typeOf(path) {
  const dot = path.lastIndexOf(".");
  return (dot < 0 ? null : TYPES.get(path.slice(dot))) ?? "application/octet-stream";
}

/**
 * Every archetype in the corpus, in a stable order.
 *
 * <p>Sorted by id rather than by directory-read order: P5, and a suite that iterates the corpus
 * reports its failures in the same sequence on every machine.
 */
export async function archetypes(fs = nodeFs) {
  const entries = await fs.readdir(CORPUS_DIR, { withFileTypes: true });
  const found = [];
  for (const entry of entries.filter((each) => each.isDirectory())) {
    const declaration = join(CORPUS_DIR, entry.name, DECLARATION_FILE);
    let raw;
    try {
      raw = await fs.readFile(declaration, "utf8");
    } catch {
      // A directory with no declaration is not an archetype. `node_modules` is the case this is for.
      continue;
    }
    found.push({ id: entry.name, dir: join(CORPUS_DIR, entry.name), declaration: JSON.parse(raw) });
  }
  found.sort((a, b) => a.id.localeCompare(b.id));
  return found;
}

/** One archetype by id, with the teaching failure a typo deserves. */
export async function archetype(id, fs = nodeFs) {
  const all = await archetypes(fs);
  const found = all.find((each) => each.id === id);
  if (found) return found;
  throw new Error(
    `source corpus: there is no archetype "${id}".\n`
      + `  allowed: ${all.map((each) => each.id).join(", ")}\n`
      + `  add one: ${join(CORPUS_DIR, id, DECLARATION_FILE)} plus ${join(id, SITE_DIR)}/`,
  );
}

/**
 * The route table: every path the archetype answers, and with what.
 *
 * <p>Two sources, and the second is the whole reason this function exists. Files under `site/`
 * cover the ordinary URL space — `index.html` answers its directory, so `about/index.html` is
 * `/about` and `/about/`. **`routes` in the declaration covers what a filesystem cannot spell**: a
 * query string (`?` is not a filename), a redirect, a status, a deliberately wrong content type, a
 * zero-byte body. Without the second source the corpus could not hold SH557's archetype at all,
 * which is the trait it was built for.
 *
 * @returns {Promise<Map<string, {status: number, type: string, body: Buffer, location?: string}>>}
 *   keyed by path **and query**, exactly as `pathOf` records it
 */
export async function routeTable(entry, fs = nodeFs) {
  const table = new Map();
  const root = join(entry.dir, SITE_DIR);

  for (const file of await walk(root, fs)) {
    const url = "/" + relative(root, file).split(sep).join(posix.sep);
    const body = Buffer.from(await fs.readFile(file));
    const row = { status: 200, type: typeOf(url), body };
    table.set(url, row);
    // `index.html` answers the directory it sits in, in both spellings. A source that serves one
    // and 301s the other is an archetype (`awkward`), never the default.
    if (url.endsWith("/index.html")) {
      const dir = url.slice(0, -"index.html".length);
      table.set(dir, row);
      if (dir !== "/") table.set(dir.replace(/\/$/, ""), row);
    }
  }

  for (const [path, spec] of Object.entries(entry.declaration.routes ?? {})) {
    const body = spec.file
      ? Buffer.from(await fs.readFile(join(entry.dir, spec.file)))
      : Buffer.from(spec.body ?? "", "utf8");
    table.set(path, {
      status: spec.status ?? 200,
      type: spec.type ?? (spec.file ? typeOf(spec.file) : typeOf(path)),
      body,
      ...(spec.location ? { location: spec.location } : {}),
    });
  }

  return table;
}

async function walk(dir, fs) {
  const out = [];
  let entries;
  try {
    entries = await fs.readdir(dir, { withFileTypes: true });
  } catch {
    return out;
  }
  for (const entry of entries) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...(await walk(path, fs)));
    else out.push(path);
  }
  out.sort();
  return out;
}

/**
 * The fast tier's driver: a `fetch` over the route table, no socket.
 *
 * <p>The `Response` it returns is the subset `clone` reads — `ok`, `status`, `headers.get`, `text`,
 * `arrayBuffer` — and no more. A fuller fake would be a claim about the platform's `Response` that
 * only the live tier can make, which is exactly the division of labour between the two tiers.
 *
 * @param origin the origin the archetype is served at; the capture records paths against it
 */
export function injectedFetch(table, origin = "https://fixture.test") {
  const requested = [];
  return {
    origin,
    requested,
    async fetch(url) {
      const parsed = new URL(url, origin);
      requested.push(pathAndQuery(parsed));
      const row = table.get(pathAndQuery(parsed)) ?? table.get(parsed.pathname);
      if (!row) return respond({ status: 404, type: "text/plain", body: Buffer.from("not found") });
      return respond(row);
    },
  };
}

function pathAndQuery(url) {
  return `${url.pathname}${url.search}`;
}

function respond({ status, type, body, location }) {
  const headers = new Map([
    ["content-type", type],
    ["content-length", String(body.length)],
    ...(location ? [["location", location]] : []),
  ]);
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (name) => headers.get(String(name).toLowerCase()) ?? null },
    async text() {
      return body.toString("utf8");
    },
    async arrayBuffer() {
      // A copy, and on purpose: a caller that transfers the buffer must not empty the fixture.
      return body.buffer.slice(body.byteOffset, body.byteOffset + body.byteLength);
    },
  };
}

/**
 * The live tier's driver: the same table behind a real socket on an ephemeral port.
 *
 * <p>`requested` is every path the crawler actually asked for. An assertion that a walk was
 * *bounded* is about the requests and not about the artefact they produced — "absent from the
 * source" and "absent from the capture" are opposite claims (SH387), and only the socket can tell
 * them apart.
 */
export async function serve(table) {
  const requested = [];
  const server = createServer((req, res) => {
    const url = new URL(req.url, "http://127.0.0.1");
    const key = pathAndQuery(url);
    requested.push(key);
    const row = table.get(key) ?? table.get(url.pathname);
    if (!row) {
      res.writeHead(404, { "content-type": "text/plain", "content-length": 9 });
      return res.end("not found");
    }
    res.writeHead(row.status, {
      "content-type": row.type,
      "content-length": row.body.length,
      ...(row.location ? { location: row.location } : {}),
    });
    return res.end(row.body);
  });

  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address();
  return {
    origin: `http://127.0.0.1:${port}`,
    requested,
    close: () => new Promise((resolve) => server.close(resolve)),
  };
}

/** One archetype, loaded and ready for either driver. The call a suite makes. */
export async function load(id, fs = nodeFs) {
  const entry = await archetype(id, fs);
  return { ...entry, table: await routeTable(entry, fs) };
}

/** Every archetype, loaded. The call a parametrised suite makes. */
export async function loadAll(fs = nodeFs) {
  const entries = await archetypes(fs);
  return Promise.all(entries.map(async (entry) => ({ ...entry, table: await routeTable(entry, fs) })));
}
