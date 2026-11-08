/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1363 — a large asset goes up in a resumable session, not one multipart POST.
 *
 * SH1355 gave the server `/api/v2/staticfile/upload-session`: begin with the name, size, folder
 * and sha256; PUT chunks by offset; finish checks the digest and creates the File post through the
 * same upload the single request uses. The CLI is the agent's path to an asset, and P1 puts it
 * before REST, so it takes the session above a threshold: a dropped link costs one chunk, not the
 * file. Below the threshold the single request stays, so a small asset is still one call.
 *
 * One function for every caller (the admin client and the conformance helper), because the
 * arithmetic is the part that must not differ: where to resume is the server's answer, never a
 * client-side guess, and a resume that guessed would duplicate or drop a chunk silently.
 */
import { createHash } from "node:crypto";

import { ShioHttpError } from "@viglet/shio-client";

/** Above this many bytes an upload takes a session. */
export const UPLOAD_SESSION_THRESHOLD = 8 * 1024 * 1024;
/** The size of one PUT. */
export const UPLOAD_CHUNK_SIZE = 4 * 1024 * 1024;
/** How many failed chunk attempts in a row end the upload. */
export const UPLOAD_RETRIES = 5;
export const UPLOAD_SESSION_PATH = "/api/v2/staticfile/upload-session";

/** A failed response's body, or "". */
async function bodyOf(res) {
  try {
    return await res.text();
  } catch {
    return "";
  }
}

/**
 * Upload `bytes` through a session and return the created File post.
 *
 * @param {(method: string, path: string, body?: {json?: unknown, bytes?: Uint8Array}) => Promise<Response>} send
 *   one request against the instance, carrying the caller's own credential; it may throw on a
 *   network failure, which is exactly the case this exists for
 * @param {{folderId: string, fileName: string, bytes: Uint8Array, contentType?: string}} asset
 * @param {{chunkSize?: number, retries?: number}} [options]
 */
export async function uploadInSession(send, { folderId, fileName, bytes, contentType }, options = {}) {
  const chunkSize = options.chunkSize ?? UPLOAD_CHUNK_SIZE;
  const retries = options.retries ?? UPLOAD_RETRIES;
  const size = bytes.byteLength;
  const sha256 = createHash("sha256").update(bytes).digest("hex");

  const begun = await send("POST", UPLOAD_SESSION_PATH, {
    json: { name: fileName, size, folderId, sha256, contentType: contentType || "application/octet-stream" },
  });
  if (!begun.ok) throw new ShioHttpError(begun.status, UPLOAD_SESSION_PATH, await bodyOf(begun));
  const session = await begun.json();
  const at = `${UPLOAD_SESSION_PATH}/${encodeURIComponent(session.id)}`;

  /** The server's offset, or `fallback` when it cannot be asked either. */
  const offsetNow = async (fallback) => {
    try {
      const res = await send("GET", at);
      return res.ok ? (await res.json()).offset : fallback;
    } catch {
      return fallback;
    }
  };

  let offset = session.offset ?? 0;
  let failures = 0;
  let lastFailure = "";
  while (offset < size) {
    const end = Math.min(offset + chunkSize, size);
    let res;
    try {
      res = await send("PUT", `${at}?offset=${offset}`, { bytes: bytes.subarray(offset, end) });
    } catch (dropped) {
      res = null;
      lastFailure = dropped?.message ?? String(dropped);
    }
    if (res?.ok) {
      offset = (await res.json()).offset;
      failures = 0;
      continue;
    }
    if (res && res.status !== 409 && res.status < 500) {
      // A refusal that resuming cannot fix (a missing session, an overflow): the server's own words.
      throw new ShioHttpError(res.status, at, await bodyOf(res));
    }
    if (res) lastFailure = `${res.status} ${await bodyOf(res)}`.trim();
    failures += 1;
    if (failures > retries) {
      throw new Error(`upload session ${session.id} for ${fileName} stopped at byte ${offset} of ${size} `
        + `after ${retries} retries (${lastFailure}); it stays open on the server until it expires, `
        + `and GET ${at} reports where to resume`);
    }
    // A 409 names the offset, and a dropped link may have landed the chunk or not: either way the
    // server's count is where the next chunk starts.
    offset = await offsetNow(offset);
  }

  const finished = await send("POST", `${at}/finish`);
  if (!finished.ok) throw new ShioHttpError(finished.status, `${at}/finish`, await bodyOf(finished));
  return finished.json();
}
