/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH499, instrument two of two — **the published requests are issued against a running instance.**
 *
 * ## What this asserts that the vocabulary check cannot
 *
 * `docs-vocabulary.conformance.mjs` reads declarations: it proves the *names* on the pages are
 * names this source tree still has, and it does it with no instance and in milliseconds. It is
 * structurally blind to three things, and they are the three a reader actually hits:
 *
 * - an endpoint that is mapped and **500s**;
 * - an endpoint that is mapped and answers, but no longer carries the key the page shows;
 * - an endpoint the page presents as open that now needs a credential it does not mention.
 *
 * ## The 404 problem, and why the assertion is shaped the way it is
 *
 * The obvious assertion — "a documented request must not 404" — is wrong here, and quietly. Half
 * these examples carry a placeholder site (`mysite`) that no instance has, so a correct server
 * answers 404 for a reason that has nothing to do with the docs being stale. Asserting on the
 * status alone would therefore be red on a perfect run, and the fix everyone reaches for is to
 * stop asserting.
 *
 * P6 resolves it: **every 4xx on this product carries `fix`.** So a domain 404 is an RFC 9457
 * problem document and a *routing* 404 — the one that means the page is wrong — is Spring's bare
 * error body. The assertion is "reached a handler", and it reads the body to decide. That makes
 * this suite a second reader of P6 as a side effect: a 4xx with no problem document fails here
 * whether the cause is a moved route or an error that forgot its instructions.
 *
 * ## Read-only on purpose
 *
 * Nothing below writes. The documented mutations (`POST /api/v2/agent/batch`, `PUT` on a
 * post-type, `DELETE`) are **declared as not executed** and printed with the reason, rather than
 * dropped — a suite that silently covers a third of its subjects reads exactly like one that
 * covers all of them. Executing them belongs with a seeded scratch site, which is what
 * `render.conformance.mjs` and `replication.conformance.mjs` already do for the paths they own.
 */
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { before, describe, test } from "node:test";
import { fileURLToPath } from "node:url";

import { DOCS_DIR } from "../../scripts/docs-location.mjs";

import { extract } from "./docs-examples.mjs";
import { basic, requireInstance } from "./support/instance.mjs";

// @scans prose reason: it extracts the requests published on Markdown pages, so the prose is
// what it reads and a fenced block is the payload rather than noise (SH740).

const DOCS = DOCS_DIR;

/**
 * Endpoints a page presents as working on any instance, with no site of its own.
 *
 * <p>Named rather than inferred: "has no `{placeholder}`" would also sweep in `POST /api/v2/login`
 * and the CDA reads, whose 200 depends on state this suite does not create. These four are the
 * discovery surface — P2's *one call replaces a session* — and a page that shows them is promising
 * a reader they answer immediately.</p>
 */
const MUST_ANSWER_200 = [
  "GET /api/v2/agent/manifest",
  "GET /api/v2/agent/context",
  "GET /api/v2/agent/blueprints",
  "GET /api/v2/post-type",
];

/** Issued as written; anything else is declared unexecuted below. */
const READ_ONLY = new Set(["GET"]);

let conn;

/** The path as a page writes it, with the placeholders left in — the server decides, not us. */
const requestOf = (subject) => {
  const [method, ...rest] = subject.split(" ");
  return { method, path: rest.join(" ") };
};

/**
 * Whether the response came from a handler rather than from the absence of one.
 *
 * <p>`true` for any 2xx/3xx, and for a 4xx/5xx whose body is an RFC 9457 problem document — which
 * on this product means a handler chose it. Spring's own 404 for an unmapped path carries
 * `{timestamp, status, error, path}` and no `type`, so the two are distinguishable without
 * guessing.</p>
 */
async function reachedAHandler(res) {
  if (res.status < 400) return { ok: true };
  const body = await res.text();
  let parsed;
  try {
    parsed = JSON.parse(body);
  } catch {
    return { ok: false, why: `${res.status} with a non-JSON body`, body: body.slice(0, 200) };
  }
  if (typeof parsed?.type === "string" && typeof parsed?.title === "string") {
    return { ok: true, problem: parsed };
  }
  return {
    ok: false,
    why: `${res.status} with no RFC 9457 problem document`,
    body: JSON.stringify(parsed).slice(0, 200),
  };
}

function docs() {
  if (!existsSync(DOCS)) {
    throw new Error(
      `SH499: the published documentation is not at ${DOCS}.\n` +
        "They are shio-site/docs/src/content/docs in this tree (SH1262), or SHIO_DOCS_DIR; without them this suite\n" +
        "has nothing to execute and would pass by having no subjects.",
    );
  }
  return extract(DOCS);
}

const { blocks } = docs();

const HELD = JSON.parse(
  readFileSync(fileURLToPath(new URL("./docs-open-findings.json", import.meta.url)), "utf8"),
).findings;

/**
 * A case this run reports instead of failing on, because an open task owns it.
 *
 * <p>SH338's order — the instrument lands *before* the fixes — means the first run of a new
 * assertion is red by construction, and SH579's rule means a red suite cannot enter the gate. The
 * corpus resolved the same collision with the same device (`fixtures/source-sites/open-findings.json`)
 * and it is copied deliberately rather than reinvented: `cli/test/known-debt.test.mjs` reads all of
 * these as one population and fails on an entry whose owner has shipped.</p>
 *
 * <p>Checked in **both** directions. A held request that starts passing fails the run naming the id
 * to delete, which is the half that stops this becoming the place findings go to be forgotten.</p>
 */
function holding(request, failure) {
  const held = HELD.filter((each) => each.request === request);
  if (!held.length) return failure;
  if (!failure) {
    return `  ${request}\n    is GREEN, and docs-open-findings.json still holds it for `
      + `${[...new Set(held.map((each) => each.id))].join(", ")} — delete the entry`;
  }
  return null;
}

/** Every documented request, deduplicated, with the pages that publish it. */
const requests = (() => {
  const out = new Map();
  for (const block of blocks.filter((each) => each.kind === "http")) {
    for (const subject of block.subjects) {
      if (!out.has(subject)) out.set(subject, []);
      out.get(subject).push(`${block.file}:${block.line}`);
    }
  }
  return out;
})();

describe("SH499 conformance: the published examples, executed", () => {
  before(async () => {
    conn = await requireInstance();
  });

  test("every documented GET reaches a handler, and its 4xx carries instructions", async () => {
    const failures = [];
    for (const [subject, where] of requests) {
      const { method, path } = requestOf(subject);
      if (!READ_ONLY.has(method)) continue;
      const res = await fetch(`${conn.baseUrl}${path}`, {
        headers: { Authorization: basic(conn.user, conn.password), Accept: "application/json" },
        signal: AbortSignal.timeout(20_000),
      });
      const verdict = await reachedAHandler(res);
      const failure = verdict.ok
        ? null
        : `  ${subject}\n    ${verdict.why}\n    published at ${where.join(", ")}\n    `
          + `body: ${verdict.body}`;
      const held = holding(subject, failure);
      if (held) failures.push(held);
    }
    assert.deepEqual(
      failures,
      [],
      "SH499: a request a page tells a reader to make does not reach a handler on a live "
        + "instance. Either the route moved — in which case the page sends every reader to a 404 "
        + "— or a 4xx came back without the `fix`/`allowed` P6 requires, which is the same "
        + `problem one layer in.\n${failures.join("\n")}`,
    );
  });

  test("the discovery calls a page promises answer immediately actually do", async () => {
    const failures = [];
    for (const subject of MUST_ANSWER_200) {
      if (!requests.has(subject)) {
        failures.push(`  ${subject} is no longer published — this list has outlived its page`);
        continue;
      }
      const { path } = requestOf(subject);
      const res = await fetch(`${conn.baseUrl}${path}`, {
        headers: { Authorization: basic(conn.user, conn.password), Accept: "application/json" },
        signal: AbortSignal.timeout(20_000),
      });
      if (!res.ok) {
        failures.push(`  ${subject} answered ${res.status}`);
        continue;
      }
      try {
        await res.json();
      } catch {
        failures.push(`  ${subject} answered 200 with a body that is not JSON`);
      }
    }
    assert.deepEqual(
      failures,
      [],
      "SH499: P2 says one call replaces a session, and these are the calls the pages make that "
        + `promise on. A reader's first request failing is the worst first impression there is.\n${
          failures.join("\n")}`,
    );
  });

  test("every MCP tool a page names is one `tools/list` returns", async () => {
    const documented = new Set(
      blocks.filter((each) => each.kind === "mcp").flatMap((each) => each.subjects),
    );
    const res = await fetch(`${conn.baseUrl}/mcp`, {
      method: "POST",
      headers: {
        Authorization: basic(conn.user, conn.password),
        "Content-Type": "application/json",
        Accept: "application/json, text/event-stream",
      },
      body: JSON.stringify({ jsonrpc: "2.0", id: 1, method: "tools/list" }),
      signal: AbortSignal.timeout(20_000),
    });
    assert.ok(res.ok, `POST /mcp answered ${res.status} — the page publishes it as the endpoint`);
    const text = await res.text();
    // The transport may answer SSE (`data: {…}`) or plain JSON; both are the same document.
    const payload = JSON.parse(text.startsWith("data:") ? text.slice(text.indexOf("{")) : text);
    const live = new Set((payload.result?.tools ?? []).map((tool) => tool.name));
    const missing = [...documented].filter((name) => !live.has(name));
    assert.deepEqual(
      missing,
      [],
      "SH499: a page shows a tools/call for a tool this server does not offer. Unlike the "
        + "vocabulary check, this one is answered by the *running* server, so it also catches a "
        + `tool that exists in source and failed to register.\n  live: ${[...live].sort().join(" ")}`,
    );
  });

  test("SH499: what was not executed, and what is held, are named rather than dropped", () => {
    const byName = (a, b) => a.localeCompare(b);
    const skipped = [...requests.keys()]
      .filter((subject) => !READ_ONLY.has(requestOf(subject).method))
      .sort(byName);
    const executed = requests.size - skipped.length;
    const owners = [...new Set(HELD.map((each) => each.id))].sort(byName);
    const notRun = skipped.map((each) => `    ${each}`).join("\n");
    const debt = HELD.map((each) => `    ${each.id}  ${each.request}\n      ${each.what}`).join("\n");

    process.stdout.write(
      `\nSH499 executed: ${executed} of ${requests.size} documented request(s), `
        + `${HELD.length} held red by ${owners.length} open task(s): ${owners.join(" ")}\n`
        + `  not executed (mutating, and this suite is read-only):\n${notRun}\n`
        + `  held:\n${debt}\n`,
    );
    assert.ok(
      executed > 10,
      `only ${executed} request(s) were executable — if the pages stopped publishing GETs this `
        + "suite has quietly become a no-op",
    );
    // The corpus's group G, one ledger over: a held entry naming a request nobody issues is a
    // finding that stopped being checked without anybody deciding to stop checking it.
    const inert = HELD.filter((each) => !requests.has(each.request));
    assert.deepEqual(
      inert.map((each) => `${each.id} ${each.request}`),
      [],
      "SH499: docs-open-findings.json holds a request no page publishes any more, so the finding "
        + "is neither fixed nor checked — decide which, and either delete the row or restore the "
        + "example.",
    );
  });
});
