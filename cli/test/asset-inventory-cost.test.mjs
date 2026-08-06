/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH213 — what one asset reference costs a push, measured.
 *
 * WHY THIS FILE EXISTS BEFORE THE FIX
 * "Measure before you fix, and the measurement is the first deliverable." A cost that is argued
 * rather than measured fails quietly and in somebody else's environment, so the number lands
 * first and the fix is whatever the number argues for. It prints on every run, like the SH264
 * render sweep, and the ceilings are asserted so a regression is a red build rather than a
 * slow command nobody notices.
 *
 * WHAT IS COUNTED, AND WHY IT IS NOT MILLISECONDS
 * Requests and **listed rows** — the inventory rows the server has to build and this process has
 * to parse. A timing ceiling on a shared machine is a flake (SH250), and the defect here is not
 * latency: it is a site paying for its whole media library to resolve one reference.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as contentPull from "../src/commands/content-pull.mjs";
import * as contentPush from "../src/commands/content-push.mjs";

/** A media library of this many files, spread over this many folders. */
const LIBRARY = 200;
const FOLDERS = 20;

const DEFS = [
  {
    name: "Article",
    fields: [
      { name: "headline", widget: "Text", ordinal: 1 },
      { name: "hero", widget: "File", ordinal: 2 },
    ],
  },
  // A type with ten `File` fields, for the threshold cases: what decides the shape is how many
  // folders the tree references, so a fixture needs a post that can reference several.
  {
    name: "Wide",
    fields: [
      { name: "headline", widget: "Text", ordinal: 1 },
      ...Array.from({ length: 10 }, (_, index) => ({
        name: `hero${index}`,
        widget: "File",
        ordinal: index + 2,
      })),
    ],
  },
];

/** `/media0` … `/media19`, plus the root and the content folder. */
const folderChains = () => [
  "/",
  "/blog",
  ...Array.from({ length: FOLDERS }, (_, index) => `/media${index}`),
];

/** 200 files, ten to a folder. The one the tree references is `media0/asset0.png`. */
const library = (stamped = true) =>
  Array.from({ length: LIBRARY }, (_, index) => ({
    id: `file-${index}`,
    fileName: `asset${index}.png`,
    folderId: `Blog:/media${index % FOLDERS}`,
    siteName: "Blog",
    size: 5,
    // SH398 — a listing carries the digest now, which is what removes a request per asset. A row
    // written before the stamp has none, which is the fallback case.
    ...(stamped ? { sha256: SHA_OF_BYTES } : {}),
  }));

function instance({ withPost = false, stamped = true } = {}) {
  const calls = [];
  const listings = [];
  /** Folders an `apply` created, so the second look-up can find one — SH213's other half. */
  const created = new Set();
  const fetchImpl = async (url, init = {}) => {
    const target = String(url);
    calls.push(target);
    if (target.includes("/agent/context")) {
      const params = new URL(target).searchParams;
      const wantIds = params.get("ids") === "true";
      return json({
        sitemap: [...folderChains(), ...created].map((path) => ({
          kind: "folder",
          path,
          ...(wantIds ? { id: `Blog:${path}` } : {}),
        })),
        budget: { estTokens: 10, limitTokens: 0, truncated: false },
      });
    }
    if (target.includes("/api/v2/staticfile/upload")) return json({ id: "uploaded-1" });
    if (/\/api\/v2\/staticfile\/[^/?]+\/digest$/.test(target)) {
      return json({ id: "x", size: 5, sha256: SHA_OF_BYTES });
    }
    if (/\/api\/v2\/staticfile\/[^/?]+\/replace$/.test(target)) {
      return json({ id: "replaced" });
    }
    // The bytes of one asset — not an inventory read, and counting it as one would make a pull's
    // downloads look like the defect this file is about.
    if (/\/api\/v2\/staticfile\/[^/?]+$/.test(target)) return json({});
    if (target.includes("/api/v2/staticfile")) {
      // The narrowing under test: a `folderId` filter is the server's own (SH62), so a caller
      // that knows which folders it needs can be answered with those rows and no others.
      const folderId = new URL(target).searchParams.get("folderId");
      const rows = folderId
        ? library(stamped).filter((item) => item.folderId === folderId)
        : library(stamped);
      listings.push({ folderId, rows: rows.length });
      return json(rows);
    }
    if (target.includes("/api/v2/post-type")) return json(DEFS);
    if (target.includes("/agent/find")) {
      return json({
        count: withPost ? 1 : 0,
        truncated: false,
        results: withPost ? [{ address: "post:Blog/blog/hello", type: "Article" }] : [],
      });
    }
    if (target.includes("/agent/read")) {
      return json(!withPost ? [] : [{
        address: "post:Blog/blog/hello",
        site: "Blog",
        url: "/blog/hello",
        type: "Article",
        folder: "/blog",
        data: { headline: "Hello", hero: "file-0" },
      }]);
    }
    if (target.includes("/agent/changes")) {
      return json({ found: 0, window: true, cursor: "2026-07-26T10:00:00Z|abc", more: false, changes: [], notes: [] });
    }
    if (target.includes("/agent/plan") || target.includes("/agent/apply")) {
      const body = init.body ? JSON.parse(init.body) : {};
      if (!target.includes("plan")) {
        for (const folder of body.folders ?? []) {
          if (folder?.path) created.add(folder.path);
        }
      }
      return json({ dryRun: target.includes("plan"), ops: 2, changed: 1, results: [] });
    }
    return json({});
  };
  fetchImpl.calls = calls;
  fetchImpl.listings = listings;
  return fetchImpl;
}

/** sha256 of "bytes", which is what the fixture writes to disk for the referenced asset. */
const SHA_OF_BYTES = "277089d91c0bdf4f2e6862ba7e4a07605119431f5d13f726dd352b06f1b206a9";

function json(body) {
  return {
    ok: true,
    status: 200,
    headers: { get: () => null },
    async text() {
      return JSON.stringify(body);
    },
    async json() {
      return body;
    },
    async arrayBuffer() {
      return new Uint8Array([98, 121, 116, 101, 115]).buffer;
    },
  };
}

function ctxWith(fetchImpl, seed = {}) {
  const files = new Map(Object.entries(seed));
  const norm = (path) => String(path).replaceAll("\\", "/");
  const out = { text: "" };
  out.write = (chunk) => {
    out.text += chunk;
  };
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: out,
    stderr: { write() {} },
    fetch: fetchImpl,
    fs: {
      files,
      async readFile(path) {
        const key = norm(path);
        if (!files.has(key)) {
          const error = new Error(`ENOENT: ${key}`);
          error.code = "ENOENT";
          throw error;
        }
        return files.get(key);
      },
      async writeFile(path, body) {
        files.set(norm(path), body);
      },
      async appendFile(path, body) {
        files.set(norm(path), (files.get(norm(path)) ?? "") + body);
      },
      async mkdir() {},
      async unlink(path) {
        files.delete(norm(path));
      },
      async readdir(path, options) {
        const prefix = `${norm(path)}/`;
        const names = new Set();
        for (const key of files.keys()) {
          if (!key.startsWith(prefix)) continue;
          const rest = key.slice(prefix.length);
          const cut = rest.indexOf("/");
          names.add(cut < 0 ? rest : `${rest.slice(0, cut)}/`);
        }
        if (!names.size) {
          const error = new Error(`ENOENT: ${prefix}`);
          error.code = "ENOENT";
          throw error;
        }
        const sorted = [...names].sort();
        if (!options?.withFileTypes) return sorted.map((name) => name.replace(/\/$/, ""));
        return sorted.map((name) => ({
          name: name.replace(/\/$/, ""),
          isDirectory: () => name.endsWith("/"),
        }));
      },
    },
  };
}

const POST = "/repo/shio/content/Blog/blog/hello.md";

function tree({ hero = "Blog/media0/asset0.png", bytes = true } = {}) {
  const seed = {
    [POST]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Hello\n  hero: ${hero}\n---\n`,
  };
  if (bytes) seed[`/repo/shio/assets/${hero}`] = "bytes";
  return seed;
}

function report(label, fetchImpl) {
  const rows = fetchImpl.listings.reduce((sum, listing) => sum + listing.rows, 0);
  const line =
    `SH213 ${label}: ${fetchImpl.calls.length} request(s), `
    + `${fetchImpl.listings.length} inventory read(s), ${rows} row(s) listed `
    + `(library ${LIBRARY} files in ${FOLDERS} folders)`;
  // eslint-disable-next-line no-console
  console.log(line);
  return { requests: fetchImpl.calls.length, reads: fetchImpl.listings.length, rows };
}

test("SH213: one asset reference costs the folders it names, not the library", async () => {
  const fetchImpl = instance();
  const ctx = ctxWith(fetchImpl, tree());

  await contentPush.run({}, [], ctx);

  const cost = report("push, 1 reference", fetchImpl);
  // The measurement that ordered this task: before the fix this was 200 — the whole library, to
  // resolve one path. The ceiling is the referenced folder's own contents.
  assert.ok(cost.rows <= 10, `one reference listed ${cost.rows} row(s)`);
  assert.ok(cost.reads <= 1, `one reference took ${cost.reads} inventory read(s)`);
});

test("SH213: an upload into a missing folder does not re-read the inventory", async () => {
  // The second read was the straightforward half: when the target folder does not exist the push
  // creates it and then needed its id — and re-read the whole inventory to find one folder.
  const fetchImpl = instance();
  const ctx = ctxWith(fetchImpl, tree({ hero: "Blog/new-section/logo.png" }));

  await contentPush.run({}, [], ctx);

  const cost = report("push, 1 upload into a new folder", fetchImpl);
  assert.ok(cost.reads <= 1, `an upload took ${cost.reads} inventory read(s)`);
});

test("SH213: a pull still reads the whole inventory, because it projects all of it", async () => {
  // Not a regression — the opposite. A pull rewrites every `File` field id → path, so it needs
  // every id the site has; narrowing it would silently leave ids in the tree.
  const fetchImpl = instance({ withPost: true });
  const ctx = ctxWith(fetchImpl, tree());

  await contentPull.run({ theirs: true }, [], ctx);

  const cost = report("pull", fetchImpl);
  assert.equal(cost.rows, LIBRARY, "a pull is answered with the whole library, on purpose");
});

test("SH213: past the threshold it goes back to one listing per site", async () => {
  // The threshold is the point of the fix rather than a replacement: resolving N folders
  // individually is N requests against one, and that stops being a win. Ten referenced folders
  // is above the ceiling of eight, so this run takes the whole-library shape on purpose.
  const fetchImpl = instance();
  const seed = {};
  const fields = Array.from({ length: 10 }, (_, index) => `  hero${index}: Blog/media${index}/asset${index}.png`);
  seed["/repo/shio/content/Blog/blog/hello.md"] =
    `---\nsite: Blog\nurl: /blog/hello\ntype: Wide\nfolder: /blog\nfields:\n  headline: Hello\n${fields.join("\n")}\n---\n`;
  for (let index = 0; index < 10; index += 1) {
    seed[`/repo/shio/assets/Blog/media${index}/asset${index}.png`] = "bytes";
  }
  const ctx = ctxWith(fetchImpl, seed);

  await contentPush.run({}, [], ctx);

  const cost = report("push, 10 references in 10 folders", fetchImpl);
  assert.equal(cost.reads, 1, "one listing, not ten");
  assert.equal(cost.rows, LIBRARY, "and it is the whole library, which is the cheaper shape here");
});

test("SH213: a handful of folders is answered per folder", async () => {
  const fetchImpl = instance();
  const seed = {};
  const fields = Array.from({ length: 3 }, (_, index) => `  hero${index}: Blog/media${index}/asset${index}.png`);
  seed["/repo/shio/content/Blog/blog/hello.md"] =
    `---\nsite: Blog\nurl: /blog/hello\ntype: Wide\nfolder: /blog\nfields:\n  headline: Hello\n${fields.join("\n")}\n---\n`;
  for (let index = 0; index < 3; index += 1) {
    seed[`/repo/shio/assets/Blog/media${index}/asset${index}.png`] = "bytes";
  }
  const ctx = ctxWith(fetchImpl, seed);

  await contentPush.run({}, [], ctx);

  const cost = report("push, 3 references in 3 folders", fetchImpl);
  assert.equal(cost.reads, 3, "one listing per referenced folder");
  assert.equal(cost.rows, 30, "and only those folders' contents");
});

test("SH398: a settled pull asks for no digest at all", async () => {
  // What SH373 cost and SH398 removed, measured. It was one `GET /staticfile/{id}/digest` per
  // asset already on disk — 208 requests over a 200-file library, because nothing but the server's
  // own hash could say whether its copy had moved. The listing carries the hash now, so the whole
  // comparison rides the read the pull was making anyway.
  const fetchImpl = instance({ withPost: true });
  const seed = { [POST]: tree()[POST] };
  for (let index = 0; index < LIBRARY; index += 1) {
    seed[`/repo/shio/assets/Blog/media${index % FOLDERS}/asset${index}.png`] = "bytes";
  }
  const ctx = ctxWith(fetchImpl, seed);

  await contentPull.run({ theirs: true }, [], ctx);

  const cost = report("pull, every asset already on disk", fetchImpl);
  assert.ok(cost.requests <= 12, `a settled pull took ${cost.requests} request(s)`);
  assert.equal(
    fetchImpl.calls.filter((url) => /\/digest$/.test(url)).length,
    0,
    "the listing answered it, so nothing was asked",
  );
  assert.equal(
    fetchImpl.calls.filter((url) => /\/api\/v2\/staticfile\/[^/?]+$/.test(url)).length,
    0,
    "and nothing was downloaded",
  );
});

test("SH398: a row written before the stamp still falls back to the endpoint", async () => {
  // A stamp covers only what was written after it, so the on-demand path is not an alternative to
  // it — it is what every pre-existing asset in every existing instance still uses. Dropping the
  // endpoint would make this tree re-download its whole library instead.
  const fetchImpl = instance({ withPost: true, stamped: false });
  const seed = { [POST]: tree()[POST] };
  for (let index = 0; index < LIBRARY; index += 1) {
    seed[`/repo/shio/assets/Blog/media${index % FOLDERS}/asset${index}.png`] = "bytes";
  }
  const ctx = ctxWith(fetchImpl, seed);

  await contentPull.run({ theirs: true }, [], ctx);

  const cost = report("pull, legacy rows with no stamp", fetchImpl);
  assert.equal(
    fetchImpl.calls.filter((url) => /\/digest$/.test(url)).length,
    LIBRARY,
    "one digest per unstamped asset, exactly as before SH398",
  );
  assert.equal(
    fetchImpl.calls.filter((url) => /\/api\/v2\/staticfile\/[^/?]+$/.test(url)).length,
    0,
    "and it still concludes they are unchanged, so nothing is downloaded",
  );
});
