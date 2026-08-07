/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH587 — the instance says what it already knows.
 *
 * <p>Three properties, and the third is the one that keeps the other two true. A 5xx names the
 * endpoint that still holds its cause; a command that runs against an instance carrying notes says
 * so before it does anything else; and **every registered command has taken a side** — the
 * classification in `instance-hints.mjs` is checked against `index.mjs`'s own registry, so the
 * nineteenth command cannot be written without deciding, which is the failure SH322 names.</p>
 */
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

import { formatError } from "../src/format-error.mjs";
import {
  DIAGNOSTICS_HINT,
  NOTES_COMMANDS,
  NOTES_EXEMPT,
  writeNotesNotice,
} from "../src/instance-hints.mjs";

const ENTRY = fileURLToPath(new URL("../src/index.mjs", import.meta.url));

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

/** A memory endpoint that answers with what it was handed, and records the query it was asked. */
function memoryClient(answer, seen = {}) {
  return {
    async memory(query) {
      seen.query = query;
      if (answer instanceof Error) throw answer;
      return answer;
    },
  };
}

test("SH587: a 500 names the endpoint that still holds its cause", () => {
  const text = formatError({
    status: 500,
    url: "http://localhost:2710/api/v2/agent/batch",
    body: JSON.stringify({ title: "Internal Server Error" }),
  });
  assert.match(text, /Internal Server Error/);
  assert.match(text, /GET \/api\/v2\/agent\/diagnostics/);
  assert.equal(text.includes(DIAGNOSTICS_HINT), true);
});

test("SH587: a 500 with no teaching body carries the pointer too", () => {
  // The shape that produced the incident: Spring's default document, which teaches nothing and
  // whose only remaining evidence is on the instance.
  const text = formatError({
    status: 500,
    url: "http://localhost:2710/api/v2/staticfile/upload",
    body: '{"timestamp":"2026-08-07T10:00:00Z","status":500,"error":"Internal Server Error"}',
  });
  assert.match(text, /GET \/api\/v2\/agent\/diagnostics/);
});

test("SH587: a body that does not parse still points at diagnostics on a 5xx", () => {
  const text = formatError({ status: 503, url: "http://x/y", body: "<html>gateway</html>" });
  assert.match(text, /the server answered 503/);
  assert.match(text, /GET \/api\/v2\/agent\/diagnostics/);
});

test("SH587: a 4xx does not point at diagnostics — P6 already told the caller what to do", () => {
  const teaching = formatError({
    status: 400,
    url: "http://x/y",
    body: JSON.stringify({ title: "Unknown post type", fix: "Use one of: Article", allowed: ["Article"] }),
  });
  assert.match(teaching, /fix: Use one of/);
  assert.equal(teaching.includes("diagnostics"), false);

  const bare = formatError({ status: 404, url: "http://x/y", body: "not found" });
  assert.equal(bare.includes("diagnostics"), false);
});

test("SH587: the notes notice prints the count and the keys, and never the prose", async () => {
  const stream = sink();
  const seen = {};
  const result = await writeNotesNotice(
    memoryClient(
      {
        found: 4,
        notes: [
          { key: "frontend-routes", note: "/tags/* /search", scope: null },
          { key: "site.postTypeLayout", note: "SH328 — the binding is by name", scope: "Acme" },
        ],
      },
      seen,
    ),
    { site: "Acme", stream },
  );

  assert.deepEqual(result, { found: 4, keys: ["frontend-routes", "site.postTypeLayout@Acme"] });
  assert.match(stream.text, /carries 4 note\(s\)/);
  assert.match(stream.text, /frontend-routes, site\.postTypeLayout@Acme/);
  assert.match(stream.text, /shio remember --site Acme/);
  // The prose is P3's business. A note can be a paragraph and there can be dozens of them.
  assert.equal(stream.text.includes("SH328"), false);
  assert.equal(stream.text.includes("/tags/*"), false);
  assert.deepEqual(seen.query, { site: "Acme" });
});

test("SH587: the keys are sorted, so two runs against one instance print the same bytes", async () => {
  const stream = sink();
  await writeNotesNotice(
    memoryClient({
      found: 3,
      notes: [{ key: "zeta" }, { key: "alpha" }, { key: "alpha", scope: "B" }],
    }),
    { stream },
  );
  assert.match(stream.text, /alpha, alpha@B, zeta/);
  assert.match(stream.text, /`shio remember`/);
});

test("SH587: an instance with no notes, and one that cannot be reached, say nothing", async () => {
  const empty = sink();
  assert.equal(await writeNotesNotice(memoryClient({ found: 0, notes: [] }), { stream: empty }), null);
  assert.equal(empty.text, "");

  const down = sink();
  const err = Object.assign(new Error("ECONNREFUSED"), { status: 0 });
  assert.equal(await writeNotesNotice(memoryClient(err), { stream: down }), null);
  assert.equal(down.text, "");

  const junk = sink();
  assert.equal(await writeNotesNotice(memoryClient({ notes: null }), { stream: junk }), null);
  assert.equal(junk.text, "");
});

test("SH587: every registered command has taken a side on the notes notice", async () => {
  const source = await readFile(ENTRY, "utf8");
  const block = source.slice(source.indexOf("const COMMANDS = {"), source.indexOf("const HELP ="));
  const registered = [...block.matchAll(/^\s{2}(\w+):/gm)].map(([, name]) => name);
  assert.ok(registered.length > 5, `expected a command registry, found ${registered.length}`);

  const unclassified = registered.filter(
    (name) => !NOTES_COMMANDS.has(name) && !NOTES_EXEMPT.has(name),
  );
  assert.deepEqual(
    unclassified,
    [],
    `these commands neither print the notes notice nor say why not — add each to NOTES_COMMANDS ` +
      `or to NOTES_EXEMPT with its reason in cli/src/instance-hints.mjs: ${unclassified.join(", ")}`,
  );

  // Both directions: a name in the classification that no longer exists is a stale rule, and a
  // stale rule is what makes the next reader distrust the whole table.
  const known = new Set(registered);
  const orphans = [...NOTES_COMMANDS, ...NOTES_EXEMPT.keys()].filter((name) => !known.has(name));
  assert.deepEqual(orphans, [], `classified but not registered: ${orphans.join(", ")}`);

  // An exemption without a reason is a list entry pretending to be a decision.
  for (const [name, reason] of NOTES_EXEMPT) {
    assert.ok(reason && reason.length > 15, `exemption for "${name}" needs a reason`);
  }
});
