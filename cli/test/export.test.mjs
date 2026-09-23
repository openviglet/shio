/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1065 — `shio export site:<name>`. Network-free: a scripted fetch answers both halves.
 *
 * The assertion that would have caught the gap is the first one: the export route is strictly
 * id-keyed, so what a caller could not do was reach it with an address. Here the run resolves
 * `site:Acme` on the agent surface and asks the console route for the id it got back — two
 * surfaces, one gesture — and the filename comes off the server's own `Content-Disposition`
 * rather than being derived twice.
 */
import { strict as assert } from "node:assert";
import { test } from "node:test";

import * as exportCmd from "../src/commands/export.mjs";

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

/** Answers the resolve with a site id and the export with four bytes called `acme.zip`. */
function scriptedFetch() {
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url: String(url), init });
    if (String(url).includes("/agent/resolve")) {
      return {
        ok: true,
        status: 200,
        headers: { get: () => null },
        async json() {
          return { kind: "site", id: "site-uuid-1", address: "site:Acme", title: "Acme" };
        },
        async text() {
          return "{}";
        },
      };
    }
    return {
      ok: true,
      status: 200,
      headers: {
        get: (name) => (name.toLowerCase() === "content-disposition"
          ? 'attachment; filename="acme.zip"'
          : null),
      },
      async arrayBuffer() {
        return new Uint8Array([80, 75, 3, 4]).buffer;
      },
      async text() {
        return "";
      },
    };
  };
  fetchImpl.calls = calls;
  return fetchImpl;
}

function ctxWith(fetchImpl, written) {
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    stdout: sink(),
    stderr: sink(),
    fetch: fetchImpl,
    writeFile: async (file, bytes) => written.push({ file, bytes }),
  };
}

test("SH1065: the address resolves, and the id-keyed route is asked with what came back", async () => {
  const fetchImpl = scriptedFetch();
  const written = [];
  const ctx = ctxWith(fetchImpl, written);

  const answer = await exportCmd.run({}, ["site:Acme"], ctx);

  assert.match(fetchImpl.calls[0].url, /\/agent\/resolve\?address=site%3AAcme/u,
    "SH1065: the export route is strictly id-keyed, so reaching it with an address is the "
    + "whole of what this command adds");
  assert.match(fetchImpl.calls[1].url, /\/api\/v2\/site\/site-uuid-1\/export$/u);
  assert.deepEqual(written.map((w) => w.file), ["acme.zip"],
    "SH1065: the server names the file, so a zip written here and one downloaded from the "
    + "console are called the same thing");
  assert.equal(answer.bytes, 4);
  assert.match(ctx.stdout.text, /wrote acme\.zip \(1 KB\) from site:Acme/u);
});

test("SH1065: --out overrides the name, and --json answers with the path", async () => {
  const written = [];
  const ctx = ctxWith(scriptedFetch(), written);

  await exportCmd.run({ site: "Acme", out: "/tmp/backup.zip", json: true }, [], ctx);

  assert.deepEqual(written.map((w) => w.file), ["/tmp/backup.zip"]);
  assert.deepEqual(JSON.parse(ctx.stdout.text),
    { site: "Acme", file: "/tmp/backup.zip", bytes: 4 });
});

test("SH1065: a site is required, and the refusal shows the call that works", async () => {
  await assert.rejects(
    () => exportCmd.run({}, [], ctxWith(scriptedFetch(), [])),
    /shio export: a site is required.*shio export site:Acme/su,
  );
});

test("SH1065: a site is named three ways, because the rest of the CLI names one three ways", () => {
  assert.equal(exportCmd.siteOf({}, ["site:Acme"]), "Acme");
  assert.equal(exportCmd.siteOf({}, ["Acme"]), "Acme");
  assert.equal(exportCmd.siteOf({ site: "Acme" }, []), "Acme");
  assert.equal(exportCmd.siteOf({}, []), null);
});
