/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { test } from "node:test";

import { ShioAdminClient } from "../src/admin-client.mjs";
import { UPLOAD_SESSION_PATH, UPLOAD_SESSION_THRESHOLD, uploadInSession } from "../src/upload-session.mjs";

/**
 * SH1363 — the CLI resumes a large upload instead of restarting it.
 *
 * The fake below keeps SH1355's contract: a chunk that starts where the received bytes end is
 * appended, one inside them is a retry, anything else is a 409; finish checks the sha256. Its one
 * fault is the case that matters: a connection cut after the server has stored part of a chunk,
 * so the client cannot know how much landed and has to ask.
 */
function fakeServer({ cutOnPut = new Set(), conflictOnPut = new Set() } = {}) {
  const state = { received: [], size: 0, sha256: "", puts: 0, multipart: 0, finished: null };
  const json = (status, body) => new Response(JSON.stringify(body), {
    status, headers: { "Content-Type": "application/json" },
  });
  const fetch = async (url, init) => {
    const { pathname, searchParams } = new URL(url);
    if (pathname === "/api/v2/staticfile/upload") {
      state.multipart += 1;
      return json(200, { id: "single" });
    }
    if (pathname === UPLOAD_SESSION_PATH && init.method === "POST") {
      const begin = JSON.parse(init.body);
      state.size = begin.size;
      state.sha256 = begin.sha256;
      return json(201, { id: "s1", offset: 0, size: begin.size });
    }
    if (pathname === `${UPLOAD_SESSION_PATH}/s1` && init.method === "GET") {
      return json(200, { id: "s1", offset: state.received.length });
    }
    if (pathname === `${UPLOAD_SESSION_PATH}/s1` && init.method === "PUT") {
      state.puts += 1;
      const offset = Number(searchParams.get("offset"));
      const chunk = [...init.body];
      if (conflictOnPut.has(state.puts) || offset > state.received.length) {
        return json(409, { code: "upload-session-offset", offset: state.received.length });
      }
      const fresh = chunk.slice(state.received.length - offset);
      if (cutOnPut.has(state.puts)) {
        state.received.push(...fresh.slice(0, Math.floor(fresh.length / 2)));
        throw new TypeError("fetch failed: socket hang up");
      }
      state.received.push(...fresh);
      return json(200, { id: "s1", offset: state.received.length });
    }
    if (pathname === `${UPLOAD_SESSION_PATH}/s1/finish`) {
      const digest = createHash("sha256").update(Uint8Array.from(state.received)).digest("hex");
      if (digest !== state.sha256) return json(422, { code: "upload-session-digest" });
      state.finished = Uint8Array.from(state.received);
      return json(200, { id: "asset-1" });
    }
    return json(404, {});
  };
  return { fetch, state };
}

const bytesOf = (length) => Uint8Array.from({ length }, (_, i) => (i * 31 + 7) % 251);

test("SH1363: a connection cut mid-chunk resumes from the server's offset and lands one exact asset", async () => {
  const { fetch, state } = fakeServer({ cutOnPut: new Set([2]) });
  const client = new ShioAdminClient({ baseUrl: "http://shio.test", user: "u", password: "p", fetch });
  const bytes = bytesOf(10_000);
  const created = await client.uploadStaticFile({ folderId: "f", fileName: "clip.mp4", bytes },
    { threshold: 1000, chunkSize: 3000 });
  assert.equal(created.id, "asset-1");
  assert.deepEqual(state.finished, bytes, "SH1363: the asset is the declared bytes, nothing doubled or lost");
  assert.equal(state.multipart, 0);
  // 0-3000, 3000-6000 cut after 4500 landed, then 4500-7500 and 7500-10000: the resume starts at
  // the server's 4500, so the half that arrived is not sent twice and the run is still four PUTs.
  assert.equal(state.puts, 4, "SH1363: the cut chunk was sent again from where the server says it stopped");
});

test("SH1363: a 409 is answered by asking the offset, not by guessing", async () => {
  const { fetch, state } = fakeServer({ conflictOnPut: new Set([2]) });
  const send = (method, path, { json, bytes } = {}) => fetch(`http://shio.test${path}`, {
    method, body: json !== undefined ? JSON.stringify(json) : bytes,
  });
  const bytes = bytesOf(7000);
  await uploadInSession(send, { folderId: "f", fileName: "a.bin", bytes }, { chunkSize: 2000 });
  assert.deepEqual(state.finished, bytes);
});

test("SH1363: a link that stays down ends with the session id, so a second run can resume it", async () => {
  const fetch = async (url, init) => {
    if (init.method === "POST" && new URL(url).pathname === UPLOAD_SESSION_PATH) {
      return new Response(JSON.stringify({ id: "s9", offset: 0 }), { status: 201 });
    }
    throw new TypeError("fetch failed");
  };
  const client = new ShioAdminClient({ baseUrl: "http://shio.test", user: "u", password: "p", fetch });
  await assert.rejects(
    client.uploadStaticFile({ folderId: "f", fileName: "big.mov", bytes: bytesOf(5000) },
      { threshold: 100, chunkSize: 1000, retries: 2 }),
    /upload session s9 for big\.mov stopped at byte 0 of 5000 after 2 retries/u);
});

test("SH1363: below the threshold a small asset is still one multipart request", async () => {
  const { fetch, state } = fakeServer();
  const client = new ShioAdminClient({ baseUrl: "http://shio.test", user: "u", password: "p", fetch });
  const created = await client.uploadStaticFile({ folderId: "f", fileName: "logo.png", bytes: bytesOf(500) });
  assert.equal(created.id, "single");
  assert.equal(state.multipart, 1);
  assert.equal(state.puts, 0);
  assert.equal(UPLOAD_SESSION_THRESHOLD, 8 * 1024 * 1024, "the default the docs name");
});
