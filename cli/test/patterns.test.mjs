/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1040 — `shio patterns`. Network-free and disk-free: a routed fake fetch answers the reads and
 * an injected `ctx.fs` collects the writes, the way `blueprints.test.mjs` does.
 *
 * The properties held here are the ones a caller is hurt by if they break: the target page is
 * written back WHOLE (a partial upsert clears its title, because a document declares whole state),
 * the placement lands where `--after` said, an unfilled placeholder is refused before anything is
 * written, and a re-apply writes the same posts rather than a second copy.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as patterns from "../src/commands/patterns.mjs";
import {
  fillPattern, parsePattern, renderPattern, sectionUrlsFor, validPatternName,
} from "../src/patterns-store.mjs";
import { parseArgs } from "../src/args.mjs";
import { norm } from "./support/paths.mjs";

const PAGE = {
  address: "post:Acme/pricing",
  type: "Page",
  data: {
    title: "Pricing",
    description: "What it costs",
    sections: ["/sections/hero-1", "/sections/cta-1"],
  },
};

const SECTIONS = {
  "post:Acme/sections/hero-1": {
    address: "post:Acme/sections/hero-1",
    type: "SectionHero",
    data: { heading: "Simple pricing", subheading: "No surprises" },
  },
  "post:Acme/sections/cta-1": {
    address: "post:Acme/sections/cta-1",
    type: "SectionCta",
    data: { heading: "Start today", ctaLabel: "Sign up" },
  },
};

function sink() {
  const chunks = [];
  return { write: (text) => chunks.push(text), text: () => chunks.join("") };
}

/** Answers `/agent/read` for whatever addresses were asked, and records every write. */
function ctxWith({ files = {}, dirs = {}, pushed = [] } = {}) {
  const posted = [];
  const written = [];
  const puts = [];
  const fetchImpl = async (url, init) => {
    const href = String(url);
    let body;
    if (href.includes("/agent/patterns")) {
      if (init?.method === "PUT") {
        puts.push({ href, body: init.body });
        body = { name: href.split("/").pop(), digest: "sha256:stored" };
      } else {
        body = pushed;
      }
    } else if (href.includes("/agent/read")) {
      const asked = [...new URL(href).searchParams.getAll("address")];
      body = asked.map((address) => (address === PAGE.address ? PAGE : SECTIONS[address]))
        .filter(Boolean);
    } else {
      posted.push(JSON.parse(init.body));
      body = { changed: posted.at(-1).posts.length, results: [] };
    }
    return {
      ok: true,
      status: 200,
      headers: { get: () => null },
      async text() { return JSON.stringify(body); },
      async json() { return body; },
    };
  };
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    fetch: fetchImpl,
    posted,
    written,
    puts,
    fs: {
      async readFile(path) {
        const key = Object.keys(files).find((name) => norm(path).endsWith(name));
        if (key) return files[key];
        const error = new Error("not found");
        error.code = "ENOENT";
        throw error;
      },
      async readdir(path) {
        const key = Object.keys(dirs).find((name) => norm(path).endsWith(name));
        if (key) return dirs[key];
        const error = new Error("not found");
        error.code = "ENOENT";
        throw error;
      },
      async mkdir() {},
      async writeFile(path, text) { written.push({ path: norm(path), text }); },
    },
  };
}

function args(line) {
  return parseArgs(line.split(" "), { repeatable: ["param", "section"] });
}

// --- the format half, which needs neither ---

test("SH1040: a pattern is byte-stable, so re-capturing an unchanged page is an empty diff", () => {
  const once = renderPattern({
    name: "pricing", from: "post:Acme/pricing", captured: "2026-09-20T00:00:00Z",
    sections: [{ type: "SectionHero", data: { subheading: "b", heading: "a" } }],
  });
  const again = renderPattern({
    name: "pricing", from: "post:Acme/pricing", captured: "2026-09-20T00:00:00Z",
    sections: [{ type: "SectionHero", data: { heading: "a", subheading: "b" } }],
  });
  assert.equal(once, again, "keys are sorted, so a field order the instance happened to answer "
    + "with cannot move the file (P5)");
  assert.ok(once.endsWith("}\n"));
});

test("SH1040: a file that cannot be understood is refused rather than half applied", () => {
  // A pattern writes posts into a site, so a reader that skipped what it could not parse would be
  // a silent partial apply - the failure mode every reader in this repository is written against.
  assert.throws(() => parsePattern("{", "p.json"), /not valid JSON/);
  assert.throws(() => parsePattern('{"formatVersion":2,"sections":[]}', "p.json"),
    /formatVersion 2/);
  assert.throws(() => parsePattern('{"formatVersion":1,"sections":[]}', "p.json"),
    /nothing to place/);
  assert.throws(() => parsePattern('{"formatVersion":1,"sections":[{}]}', "p.json"),
    /section 0 has no `type`/);
});

test("SH1040: an unfilled placeholder is refused, and the refusal says what would fill it", () => {
  const document = parsePattern(JSON.stringify({
    formatVersion: 1,
    name: "pricing",
    params: { headline: { description: "The page's promise" }, cta: { default: "Start" } },
    sections: [{ type: "SectionHero", data: { heading: "{{headline}}", ctaLabel: "{{cta}}" } }],
  }));

  // Leaving `{{headline}}` in the content writes the literal braces onto a live page, which reads
  // as a rendering bug three steps from its cause. P6: the error carries what would fix it.
  assert.throws(() => fillPattern(document, {}), /\{\{headline}}[\s\S]*headline, cta/);
  // And a declared default is the half a caller does not have to repeat.
  const filled = fillPattern(document, { headline: "Plans" });
  assert.deepEqual(filled.sections[0].data, { ctaLabel: "Start", heading: "Plans" });
});

test("SH1040: placement names are derived, so applying twice is not two copies", () => {
  assert.deepEqual(
    sectionUrlsFor({ instance: "pricing", count: 2, folder: "/sections" }),
    ["/sections/pricing-1", "/sections/pricing-2"]);
  // `--as` is how a caller asks for a second placement on one page; without it the write is
  // idempotent, which is what P5 asks of every write here.
  assert.deepEqual(
    sectionUrlsFor({ instance: "pricing-b", count: 1, folder: "/sections" }),
    ["/sections/pricing-b-1"]);
  assert.ok(validPatternName("pricing-2"));
  assert.ok(!validPatternName("My Block"), "refused rather than slugged: the name is how the next "
    + "command addresses it, and a name nobody chose is one nobody can guess");
});

// --- the command ---

test("SH1040: save captures the sections a page names, with their values", async () => {
  const ctx = ctxWith();
  const { flags, positionals } = args("save pricing --from post:Acme/pricing");

  await patterns.run(flags, positionals, ctx);

  assert.equal(ctx.written.length, 1);
  assert.ok(ctx.written[0].path.endsWith("shio/patterns/pricing.json"));
  const saved = JSON.parse(ctx.written[0].text);
  assert.deepEqual(saved.sections.map((section) => section.type),
    ["SectionHero", "SectionCta"]);
  assert.equal(saved.sections[0].data.heading, "Simple pricing");
  assert.equal(saved.from, "post:Acme/pricing", "provenance, because the first question about a "
    + "file like this is always where it came from");
});

test("SH1040: save takes only the positions asked for", async () => {
  const ctx = ctxWith();
  const { flags, positionals } = args("save cta --from post:Acme/pricing --section 1");

  await patterns.run(flags, positionals, ctx);

  const saved = JSON.parse(ctx.written[0].text);
  assert.deepEqual(saved.sections.map((section) => section.type), ["SectionCta"],
    "a curator who got one block right out of six captures that one");
});

test("SH1040: a position the page does not have is refused, with the range", async () => {
  const ctx = ctxWith();
  const { flags, positionals } = args("save cta --from post:Acme/pricing --section 7");

  await assert.rejects(() => patterns.run(flags, positionals, ctx), /positions are 0\.\.1/);
  assert.deepEqual(ctx.written, [], "and nothing is written on the way to the refusal");
});

const STORED = JSON.stringify({
  formatVersion: 1,
  name: "promo",
  sections: [
    { type: "SectionHero", data: { heading: "{{headline}}" } },
    { type: "SectionCta", data: { heading: "Go" } },
  ],
});

test("SH1040: apply writes the new sections and the page, whole, in one document", async () => {
  const ctx = ctxWith({ files: { "shio/patterns/promo.json": STORED } });
  const { flags, positionals } = args(
    "apply promo --at post:Acme/pricing --after 1 --param headline=Plans");

  await patterns.run(flags, positionals, ctx);

  assert.equal(ctx.posted.length, 1, "one document, so one batch and one transaction (P10)");
  const document = ctx.posted[0];
  assert.deepEqual(document.posts.map((post) => post.url),
    ["/sections/promo-1", "/sections/promo-2", "/pricing"]);
  assert.equal(document.posts[0].data.heading, "Plans");
  assert.deepEqual(document.posts.at(-1).data, {
    title: "Pricing",
    description: "What it costs",
    sections: ["/sections/hero-1", "/sections/promo-1", "/sections/promo-2", "/sections/cta-1"],
  }, "SH168: a document's upsert declares WHOLE state, so sending `sections` alone would clear "
    + "the page's title - the target is read first and written back with one field changed");
});

test("SH1040: with no --after the placement goes last, which is the common ask", async () => {
  const ctx = ctxWith({ files: { "shio/patterns/promo.json": STORED } });
  const { flags, positionals } = args("apply promo --at post:Acme/pricing --param headline=x");

  await patterns.run(flags, positionals, ctx);

  assert.deepEqual(ctx.posted[0].posts.at(-1).data.sections,
    ["/sections/hero-1", "/sections/cta-1", "/sections/promo-1", "/sections/promo-2"]);
});

test("SH1040: the new sections land beside the page's own, not in a folder of their own", async () => {
  const ctx = ctxWith({ files: { "shio/patterns/promo.json": STORED } });
  const { flags, positionals } = args("apply promo --at post:Acme/pricing --param headline=x");

  await patterns.run(flags, positionals, ctx);

  // The convention a conversion and both blueprints already follow. A pattern inventing a second
  // one would leave a site with two ideas of where a section lives.
  assert.deepEqual(ctx.posted[0].folders, [{ path: "/sections" }]);
  assert.ok(ctx.posted[0].posts.every((post) =>
    post.url === "/pricing" || post.folder === "/sections"));
});

test("SH1040: a dry run reaches the plan and never the write path", async () => {
  const ctx = ctxWith({ files: { "shio/patterns/promo.json": STORED } });
  const { flags, positionals } = args(
    "apply promo --at post:Acme/pricing --param headline=x --dry-run");

  await patterns.run(flags, positionals, ctx);

  assert.match(ctx.stdout.text(), /Would place 2 section\(s\)/);
});

test("SH1040: an unknown action says which three there are", async () => {
  const ctx = ctxWith();
  const { flags, positionals } = args("capture pricing");

  await assert.rejects(() => patterns.run(flags, positionals, ctx), /One of: save, list, apply/);
});

test("SH1040: list says what each pattern would place, and an empty tree says so", async () => {
  const empty = ctxWith();
  await patterns.run(...Object.values(args("list")).slice(0, 2), empty);
  assert.match(empty.stdout.text(), /No patterns in shio\/patterns/);

  const ctx = ctxWith({
    files: { "shio/patterns/promo.json": STORED },
    dirs: { "shio/patterns": ["promo.json"] },
  });
  const { flags, positionals } = args("list");
  await patterns.run(flags, positionals, ctx);
  assert.match(ctx.stdout.text(), /promo\s+2 section\(s\)\s+SectionHero, SectionCta/);
});

// --- SH1108: the copy the console places ---

test("SH1108: push sends the file as it is on disk, so the instance's digest is the file's", async () => {
  // Two spaces of indentation and a trailing newline the way `save` writes it: a client that parsed
  // and re-serialized would send other bytes, and every later `list --remote` would read stale.
  const ctx = ctxWith({ files: { "shio/patterns/promo.json": `${STORED}\n` } });
  const { flags, positionals } = args("push promo");

  await patterns.run(flags, positionals, ctx);

  assert.equal(ctx.puts.length, 1);
  assert.match(ctx.puts[0].href, /\/api\/v2\/agent\/patterns\/promo$/);
  assert.equal(ctx.puts[0].body, `${STORED}\n`, "SH1108: the body is the file, byte for byte");
  assert.match(ctx.stdout.text(), /Pushed shio\/patterns\/promo\.json/);
});

test("SH1108: a file the reader refuses is never pushed", async () => {
  const ctx = ctxWith({ files: { "shio/patterns/promo.json": '{"formatVersion":1,"sections":[]}' } });
  const { flags, positionals } = args("push promo");

  await assert.rejects(() => patterns.run(flags, positionals, ctx), /nothing to place/);
  assert.deepEqual(ctx.puts, []);
});

test("SH1108: list --remote says which copies are current, stale, missing or orphaned", async () => {
  const { createHash } = await import("node:crypto");
  const digest = (text) => `sha256:${createHash("sha256").update(text).digest("hex")}`;
  const edited = STORED.replace('"Go"', '"Go now"').replace('"promo"', '"edited"');
  const ctx = ctxWith({
    files: {
      "shio/patterns/promo.json": STORED,
      "shio/patterns/edited.json": edited,
      "shio/patterns/fresh.json": STORED.replace('"promo"', '"fresh"'),
    },
    dirs: { "shio/patterns": ["edited.json", "fresh.json", "promo.json"] },
    pushed: [
      { name: "promo", digest: digest(STORED) },
      { name: "edited", digest: digest(STORED) },
      { name: "gone", digest: "sha256:x" },
    ],
  });
  const { flags, positionals } = args("list --remote");

  await patterns.run(flags, positionals, ctx);

  const out = ctx.stdout.text();
  assert.match(out, /promo .*  pushed\n/, "SH1108: the same bytes on both sides");
  assert.match(out, /edited .*stale — push it again/, "SH1108: edited since the push");
  assert.match(out, /fresh .*not pushed/);
  assert.match(out, /gone {2}pushed, with no file here/,
    "SH1108: a copy the project no longer has is still offered to curators, which is worth saying");
});
