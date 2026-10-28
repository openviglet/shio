#!/usr/bin/env node
// SH1249 — measures one Shio image variant the same way every time, so the JVM and the native
// image are compared by numbers and not by argument.
//
// For each run: a fresh container on a fresh store volume, then the time from `docker run` to
// the first 200 from POST /mcp (tools/list) and from the CDA (GET /api/v2/cda/site, with a READ
// token minted the way the console mints one), idle RSS after ten quiet seconds, and a fixed
// replay of agent and CDA requests (p95 latency, RSS and peak RSS after it). RSS is VmRSS of
// pid 1, which is the java process or the native binary, never the container's page cache.
//
//   node scripts/native-spike/measure.mjs <label> <image> [--runs 3] [--env K=V ...]
//        [--docker-arg <arg> ...] [-- <command and args for the image>]
//
// `--docker-arg` and the trailing command exist for the tracing-agent run that collects the
// native image's reachability metadata: the same boot and the same replay, on a JVM.
//
// Prints one JSON object per run and a median line. Docker runs through freewilly.

import { execFileSync } from "node:child_process";
import { setTimeout as sleep } from "node:timers/promises";

import { ConsoleSession, basic } from "../../cli/conformance/support/instance.mjs";

const PORT = 2799;
const BASE = `http://localhost:${PORT}`;
const USER = "admin";
const PASSWORD = "spike-sh1249";
const REPLAY_ROUNDS = 100;
const CONCURRENCY = 8;

const [label, image, ...rest] = process.argv.slice(2);
if (!label || !image) {
  console.error("usage: measure.mjs <label> <image> [--runs n] [--env K=V ...]");
  process.exit(2);
}
let runs = 3;
const env = [];
const dockerArgs = [];
let command = [];
for (let i = 0; i < rest.length; i++) {
  if (rest[i] === "--runs") runs = Number(rest[++i]);
  else if (rest[i] === "--env") env.push(rest[++i]);
  else if (rest[i] === "--docker-arg") dockerArgs.push(rest[++i]);
  else if (rest[i] === "--") {
    command = rest.slice(i + 1);
    break;
  }
}

const docker = (...args) => execFileSync("docker", args, { encoding: "utf8" }).trim();

const mcp = (method, params) =>
  fetch(`${BASE}/mcp`, {
    method: "POST",
    headers: {
      Authorization: basic(USER, PASSWORD),
      "Content-Type": "application/json",
      Accept: "application/json, text/event-stream",
    },
    body: JSON.stringify({ jsonrpc: "2.0", id: 1, method, ...(params ? { params } : {}) }),
    signal: AbortSignal.timeout(30_000),
  });

const cda = (key, path) =>
  fetch(`${BASE}/api/v2/cda${path}`, { headers: { Key: key }, signal: AbortSignal.timeout(30_000) });

/** Polls until `probe` answers 200, and returns the milliseconds since `t0`. */
async function firstOk(t0, probe, limitMs = 300_000) {
  while (Date.now() - t0 < limitMs) {
    try {
      const res = await probe();
      await res.arrayBuffer();
      if (res.status === 200) return Date.now() - t0;
    } catch {
      // not listening yet
    }
    await sleep(25);
  }
  throw new Error(`no 200 within ${limitMs} ms`);
}

function memory(name) {
  const status = docker("exec", name, "cat", "/proc/1/status");
  const kb = (field) => Number(new RegExp(`^${field}:\\s+(\\d+) kB`, "m").exec(status)?.[1] ?? NaN);
  return { rssMb: Math.round(kb("VmRSS") / 1024), peakMb: Math.round(kb("VmHWM") / 1024) };
}

/** The fixed replay: the same requests in the same order, CONCURRENCY at a time. */
function replayRequests(key) {
  const round = [
    () => mcp("tools/list"),
    () => mcp("tools/call", { name: "shio_context", arguments: {} }),
    () => cda(key, "/site"),
    () => cda(key, "/post-type"),
    () => cda(key, "/query?size=20"),
    () => cda(key, "/search?q=viglet"),
  ];
  return Array.from({ length: REPLAY_ROUNDS }, () => round).flat();
}

async function replay(key) {
  const queue = replayRequests(key);
  const latencies = [];
  const failures = {};
  let next = 0;
  async function worker() {
    while (next < queue.length) {
      const request = queue[next++];
      const start = performance.now();
      const res = await request();
      await res.arrayBuffer();
      latencies.push(performance.now() - start);
      if (res.status !== 200) failures[res.status] = (failures[res.status] ?? 0) + 1;
    }
  }
  await Promise.all(Array.from({ length: CONCURRENCY }, worker));
  latencies.sort((a, b) => a - b);
  const p = (q) => Math.round(latencies[Math.min(latencies.length - 1, Math.floor(q * latencies.length))]);
  return { requests: latencies.length, p50Ms: p(0.5), p95Ms: p(0.95), failures };
}

async function once(run) {
  const name = `shio-spike-${process.pid}-${run}`;
  const args = ["run", "-d", "--rm", "--name", name, "-p", `${PORT}:2710`,
    "-e", `SHIO_ADMIN_PASSWORD=${PASSWORD}`, "-e", "SHIO_CDA_RATELIMIT_ENABLED=false"];
  for (const each of env) args.push("-e", each);
  args.push(...dockerArgs);
  const t0 = Date.now();
  docker(...args, image, ...command);
  try {
    const mcpMs = await firstOk(t0, () => mcp("tools/list"));
    const session = new ConsoleSession({ baseUrl: BASE, user: USER, password: PASSWORD });
    const token = await session.createToken("SH1249 spike", "READ");
    const cdaMs = await firstOk(t0, () => cda(token.token, "/site"));
    await sleep(10_000);
    const idle = memory(name);
    const load = await replay(token.token);
    const after = memory(name);
    return { label, run, mcpMs, cdaMs, idleRssMb: idle.rssMb, loadRssMb: after.rssMb, peakRssMb: after.peakMb, ...load };
  } finally {
    docker("rm", "-f", name);
  }
}

const results = [];
for (let run = 1; run <= runs; run++) {
  const result = await once(run);
  results.push(result);
  console.log(JSON.stringify(result));
}
const median = (field) => {
  const values = results.map((r) => r[field]).sort((a, b) => a - b);
  return values[Math.floor(values.length / 2)];
};
const size = Math.round(Number(docker("image", "inspect", image, "--format", "{{.Size}}")) / 1024 / 1024);
console.log(JSON.stringify({
  label, median: true, imageMb: size,
  ...Object.fromEntries(["mcpMs", "cdaMs", "idleRssMb", "loadRssMb", "peakRssMb", "p50Ms", "p95Ms"].map((f) => [f, median(f)])),
}));
