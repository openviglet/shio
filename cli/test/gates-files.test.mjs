/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH997 — a gate as a file, and `shio gate run` as the CI form.
 *
 * The split this asserts is the one a curator would otherwise learn from a refusal: the file is the
 * whole checklist, the instance keeps the checks it can evaluate at publish time, and `audit` runs
 * here because the instance deliberately has no browser.
 */
import assert from "node:assert/strict"
import { test } from "node:test"

import { ciSide, parseGate, renderGate, serverSide } from "../src/gates-store.mjs"
import * as gate from "../src/commands/gate.mjs"

const declared = {
  name: "ready",
  site: "Acme",
  enabled: true,
  checks: [
    { check: "verify", severity: "error" },
    { check: "audit", severity: "warning" },
  ],
}

function sink() {
  const out = { text: "" }
  out.write = (s) => {
    out.text += s
  }
  return out
}

function ctxWith(handler) {
  const calls = []
  const fetchImpl = async (url, init = {}) => {
    calls.push({ url: String(url), method: init.method ?? "GET" })
    const { status = 200, payload = {} } = handler(String(url)) ?? {}
    return {
      ok: status >= 200 && status < 300,
      status,
      headers: { get: () => null },
      async text() {
        return JSON.stringify(payload)
      },
      async json() {
        return payload
      },
    }
  }
  fetchImpl.calls = calls
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    fetch: fetchImpl,
  }
}

test("SH997: a gate round-trips through the file it projects to", () => {
  const text = renderGate(declared)
  const read = parseGate(text, "shio/gates/Acme/ready.yaml")

  assert.equal(read.name, "ready")
  assert.equal(read.enabled, true)
  assert.deepEqual(read.checks, declared.checks)
  assert.equal(renderGate(read), text, "SH997: a re-pull of an unchanged gate must be an empty diff")
})

test("SH997: the file is the whole checklist; the instance keeps what it can run", () => {
  assert.deepEqual(serverSide(declared).checks, [{ check: "verify", severity: "error" }])
  assert.deepEqual(ciSide(declared), [{ check: "audit", severity: "warning" }])
  assert.match(renderGate(declared), /`audit`, which needs a browser/,
    "SH997: the split is in the file, so nobody learns it from a refusal")
})

test("SH997: a line the parser cannot read is a refusal, not a shorter checklist", () => {
  assert.throws(() => parseGate("name: x\nsite: Acme\nchecks:\n  * verify\n", "x.yaml"),
    /cannot read line/,
    "SH997: a gate that parses to half its checks passes what nobody looked at")
  assert.throws(() => parseGate("site: Acme\n", "x.yaml"), /needs `name` and `site`/)
})

test("SH997: gate run asks the server for the verdict, and exits non-zero when blocked", async () => {
  const ctx = ctxWith((url) => {
    if (url.includes("/gates/check")) {
      return {
        payload: [{
          gate: "ready",
          failures: [{ check: "verify", severity: "error", detail: "1 error(s)" }],
        }],
      }
    }
    return { payload: { count: 1, truncated: false, results: [{ address: "post:Acme/about" }] } }
  })

  const result = await gate.run({ site: "Acme" }, ["run"], ctx)

  assert.equal(result.blocked, 1)
  assert.equal(result.ok, false, "SH997: a gate that reported a block and exited 0 publishes anyway")
  assert.match(ctx.stdout.text, /gate 'ready' verify/)
  assert.ok(
    ctx.fetch.calls.some((call) => call.url.includes("/agent/gates/check")),
    "SH997: the verdict is the server's — a pipeline with a second opinion about its own site is "
      + "how a gate passes in CI and refuses in production",
  )
})

test("SH997: gate run needs a site, because a gate belongs to one", async () => {
  await assert.rejects(() => gate.run({}, ["run"], ctxWith(() => ({}))), /--site <name>/)
})
