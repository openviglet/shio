/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1003 — the work list, and the file that makes Grep one.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as notes from "../src/commands/notes.mjs";
import { openNotesPathFor, serializeOpenNotes } from "../src/content-notes.mjs";

const OPEN = [
  {
    id: "n-1", objectId: "p-about", address: "post:Blog/about", site: "Blog",
    field: "data.subtitle", kind: "REQUEST", body: "the price table is last quarter's",
    author: "ana",
  },
  {
    id: "n-2", objectId: "p-news", address: "post:Blog/news", site: "Blog",
    kind: "COMMENT", body: "nice", author: "ana",
  },
];

function ctxWith(payload) {
  const out = { text: "" };
  const calls = [];
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    calls,
    stdout: { write: (text) => { out.text += text; } },
    stderr: { write: () => {} },
    text: () => out.text,
    fetch: async (url) => {
      calls.push(String(url));
      return {
        ok: true,
        status: 200,
        headers: { get: () => null },
        async text() { return JSON.stringify(payload); },
        async json() { return payload; },
      };
    },
  };
}

test("SH1003: the work list names each page by address, and --strict is the CI gate", async () => {
  const ctx = ctxWith(OPEN);

  const result = await notes.run({ kind: "request", strict: true }, ["notes"], ctx);

  assert.equal(result.exitCode, 1,
    "SH1003: a pipeline that will not ship with requests outstanding needs one exit code, not a "
      + "grep over prose");
  assert.match(ctx.text(), /REQUEST post:Blog\/about data\.subtitle/,
    "SH1003: a note names something somebody has to go and fix, and a uuid names nothing anybody "
      + "can act on");
  assert.match(ctx.text(), /the price table is last quarter's/);
  assert.match(ctx.calls[0], /\/api\/v2\/note\/open\?kind=request/);
});

test("SH1003: an empty work list exits 0 even with --strict", async () => {
  const ctx = ctxWith([]);

  const result = await notes.run({ strict: true }, ["notes"], ctx);

  assert.equal(result.exitCode, 0);
  assert.match(ctx.text(), /shio notes: 0 open/,
    "SH1003: 'nothing is open' is the good news a pipeline is asking for, and a blank answer is "
      + "indistinguishable from a read that failed");
});

test("SH1003: the projection is one file per site, sorted, with the address on every row", () => {
  assert.equal(openNotesPathFor("Blog"), "Blog/.notes.yaml");

  const text = serializeOpenNotes({ site: "Blog", notes: OPEN });

  // Quoted where YAML needs it: an address carries a colon, which is structure in this format
  // and prose in a note.
  assert.match(text, /address: "post:Blog\/about"/);
  assert.match(text, /field: data\.subtitle/);
  assert.match(text, /says: "the price table is last quarter's"/);
  assert.ok(
    text.indexOf("post:Blog/about") < text.indexOf("post:Blog/news"),
    "SH1003: sorted by address, so a re-pull of an unchanged site is an empty diff (P5)",
  );
  assert.match(text, /Never pushed/,
    "SH1003: the note is the instance's record of what somebody asked for, and a tree that could "
      + "edit it would be a second writer of the same row");
});

test("SH1003: the projection escapes a body that would break the file", () => {
  const text = serializeOpenNotes({
    site: "Blog",
    notes: [{ id: "n-3", address: "post:Blog/x", kind: "REQUEST", body: "says: \"this\" # here" }],
  });

  assert.match(text, /says: "says: \\"this\\" # here"/,
    "SH1003: a correction is prose somebody typed, so a colon or a hash in it must not become "
      + "structure");
});
