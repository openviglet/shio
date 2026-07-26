/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio mcp` (SH75) — a stdio bridge to a Shio instance's MCP endpoint.
 *
 * Why the CLI carries it, given the server already speaks MCP over HTTP: because
 * `claude mcp add shio -- shio mcp` is one line and needs no token, no CORS story
 * and no reverse proxy, while wiring an HTTP MCP server into a local client needs
 * all three. The bridge is deliberately dumb — it frames stdio into HTTP POSTs and
 * copies the answers back. It parses nothing, validates nothing and knows no tool
 * names, so there is exactly one implementation of the protocol (the server's) and
 * this file cannot drift from it.
 *
 * Framing: newline-delimited JSON, which is what MCP stdio transport specifies —
 * one JSON-RPC message per line, no embedded newlines. Anything the server sends
 * back is written verbatim as one line.
 */
import { resolveAdmin } from "../context.mjs";

/** The header the server mints on initialize and expects echoed afterwards. */
const SESSION_HEADER = "mcp-session-id";

export async function run(flags, _positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const endpoint = `${String(baseUrl).replace(/\/+$/, "")}/mcp`;
  const auth = basicAuth(user, password);
  const fetchImpl = ctx.fetch ?? globalThis.fetch;

  let sessionId = null;
  let sent = 0;

  // stderr, never stdout: stdout is the protocol channel, and one stray line of
  // human text on it desynchronises the client for the rest of the session.
  ctx.stderr?.write?.(`shio mcp: bridging stdio to ${endpoint}\n`);

  for await (const line of readLines(ctx.stdin ?? process.stdin)) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    sent += 1;

    const headers = {
      "Content-Type": "application/json",
      Accept: "application/json, text/event-stream",
      Authorization: auth,
    };
    if (sessionId) headers["Mcp-Session-Id"] = sessionId;

    let res;
    try {
      res = await fetchImpl(endpoint, { method: "POST", headers, body: trimmed });
    } catch (cause) {
      // A transport failure still has to come back as JSON-RPC, or the client waits
      // forever for a response to a request it believes is in flight.
      ctx.stdout.write(`${transportError(trimmed, cause?.message ?? String(cause))}\n`);
      continue;
    }

    const minted = res.headers?.get?.(SESSION_HEADER);
    if (minted) sessionId = minted;

    // 202 is the server acknowledging a notification: JSON-RPC forbids a response,
    // so writing anything here would be a protocol violation, not a courtesy.
    if (res.status === 202) continue;

    const body = await res.text();
    if (!body.trim()) continue;
    if (!res.ok && !looksLikeJsonRpc(body)) {
      ctx.stdout.write(`${transportError(trimmed, `HTTP ${res.status}: ${body.slice(0, 200)}`)}\n`);
      continue;
    }
    // Written as-is: re-serialising would risk changing a payload whose exact bytes
    // the client may be hashing, and the bridge has no reason to look inside.
    ctx.stdout.write(`${body.replace(/\n/g, " ")}\n`);
  }

  return { endpoint, sessionId, messages: sent };
}

/** Async line reader over any Node readable stream. */
async function* readLines(stream) {
  let buffer = "";
  stream.setEncoding?.("utf8");
  for await (const chunk of stream) {
    buffer += chunk;
    let index = buffer.indexOf("\n");
    while (index !== -1) {
      yield buffer.slice(0, index);
      buffer = buffer.slice(index + 1);
      index = buffer.indexOf("\n");
    }
  }
  if (buffer.trim()) yield buffer;
}

/**
 * A JSON-RPC error carrying the id of the request that failed, so the client can
 * settle the right pending promise. -32603 (internal error) is the honest code: the
 * message was well-formed, the server just could not be reached.
 */
function transportError(requestLine, detail) {
  let id = null;
  try {
    id = JSON.parse(requestLine)?.id ?? null;
  } catch {
    /* keep id null — the client will surface it as an unmatched error */
  }
  return JSON.stringify({
    jsonrpc: "2.0",
    id,
    error: { code: -32603, message: `shio mcp bridge: ${detail}` },
  });
}

function looksLikeJsonRpc(body) {
  try {
    return typeof JSON.parse(body)?.jsonrpc === "string";
  } catch {
    return false;
  }
}

function basicAuth(user, password) {
  const raw = `${user ?? ""}:${password ?? ""}`;
  const encoded =
    typeof Buffer !== "undefined" ? Buffer.from(raw, "utf8").toString("base64") : btoa(raw);
  return `Basic ${encoded}`;
}
