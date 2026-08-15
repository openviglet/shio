/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH561 — a client for the transport Claude Code actually uses.
 *
 * ## Why this exists at all
 *
 * `replication.conformance.mjs` was written to remove substitutions and removed three: the served
 * source, the running instance, the registry. It kept a fourth nobody named — three of its four
 * scenarios `import * as convert from "../src/commands/convert.mjs"`, which is a function call.
 * Claude Code does not call functions. It spawns `shio mcp` and speaks newline-delimited JSON-RPC,
 * and everything that layer adds is what the import removes: the tool's declared input schema, the
 * name an agent has to get right, the framing, the `tools/list` payload P3 holds a budget on, and
 * the shape of a refusal an agent has to read to recover.
 *
 * ## Deliberately hand-rolled, and deliberately small
 *
 * No MCP SDK. The point is to exercise the wire the way a client does, and a library that knows the
 * protocol would paper over exactly the framing this is here to check — the same reason
 * `fixture-site.mjs` is a `node:http` server rather than an injected `fetch`. It speaks the parts a
 * conformance run needs (`initialize`, `tools/list`, `tools/call`) and nothing else.
 *
 * ## One rule the bridge itself states
 *
 * stdout is the protocol channel and stderr is for humans. `shio mcp` writes its one status line to
 * stderr for that reason, so this reads *only* stdout for frames — a client that parsed both would
 * be a client the bridge's own contract says is wrong.
 */
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const ENTRY = fileURLToPath(new URL("../../src/index.mjs", import.meta.url));

/** MCP's own version handshake — the value the server answers `initialize` with. */
const PROTOCOL_VERSION = "2025-06-18";

/**
 * Spawns `shio mcp` and returns a client over its stdio.
 *
 * @param {{baseUrl: string, user: string, password: string}} connection where the bridge points
 * @returns {Promise<{call: Function, request: Function, stderr: () => string, close: () => Promise<void>}>}
 */
export async function openMcp({ baseUrl, user, password }) {
  const child = spawn(process.execPath, [ENTRY, "mcp"], {
    env: {
      ...process.env,
      SHIO_URL: baseUrl,
      SHIO_USER: user,
      SHIO_PASSWORD: password,
    },
    stdio: ["pipe", "pipe", "pipe"],
  });

  let errors = "";
  child.stderr.setEncoding("utf8");
  child.stderr.on("data", (chunk) => {
    errors += chunk;
  });

  const pending = new Map();
  let buffered = "";
  let id = 0;

  child.stdout.setEncoding("utf8");
  child.stdout.on("data", (chunk) => {
    buffered += chunk;
    // Newline-delimited, which is what the MCP stdio transport specifies and what the bridge
    // promises: one JSON-RPC message per line, no embedded newlines. A frame split across two
    // reads is the normal case on a pipe and the one a naive client gets wrong.
    let cut = buffered.indexOf("\n");
    while (cut >= 0) {
      const line = buffered.slice(0, cut).trim();
      buffered = buffered.slice(cut + 1);
      if (line) {
        let message = null;
        try {
          message = JSON.parse(line);
        } catch (error) {
          // Reported to whichever request is waiting rather than thrown into an event handler,
          // where it would become an unhandled rejection and read as a timeout.
          for (const [, settle] of pending) settle.reject(new Error(`unframed line: ${line}`));
          pending.clear();
        }
        const settle = message && pending.get(message.id);
        if (settle) {
          pending.delete(message.id);
          settle.resolve(message);
        }
      }
      cut = buffered.indexOf("\n");
    }
  });

  const exited = new Promise((resolve) => child.on("exit", resolve));
  child.on("exit", () => {
    for (const [, settle] of pending) {
      settle.reject(new Error(`shio mcp exited before answering. stderr:\n${errors}`));
    }
    pending.clear();
  });

  function request(method, params, { timeout = 30000 } = {}) {
    id += 1;
    const frame = { jsonrpc: "2.0", id, method, ...(params ? { params } : {}) };
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        pending.delete(frame.id);
        reject(new Error(`${method} timed out after ${timeout}ms. stderr:\n${errors}`));
      }, timeout);
      pending.set(frame.id, {
        resolve: (message) => {
          clearTimeout(timer);
          resolve(message);
        },
        reject: (error) => {
          clearTimeout(timer);
          reject(error);
        },
      });
      child.stdin.write(`${JSON.stringify(frame)}\n`);
    });
  }

  const initialized = await request("initialize", {
    protocolVersion: PROTOCOL_VERSION,
    capabilities: {},
    clientInfo: { name: "shio-conformance", version: "0" },
  });

  return {
    initialized,
    request,
    /** One tool, by the name an agent has to get right, with the arguments it declares. */
    call: (name, args = {}) => request("tools/call", { name, arguments: args }),
    // SH760 — a named method rather than a bare `request("resources/read", …)`, for the
    // reason `call` is one: the live-coverage reader keys on a CALL SITE with a literal
    // argument, and a population it cannot see is a population nobody covers.
    readResource: (uri) => request("resources/read", { uri }),
    stderr: () => errors,
    async close() {
      child.stdin.end();
      child.kill();
      await exited;
    },
  };
}
