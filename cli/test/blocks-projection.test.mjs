/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
import { strict as assert } from "node:assert";
import { test } from "node:test";

import { BLOCK_KEYS, fromMarkdown, keysOf, toMarkdown } from "../src/blocks.mjs";
// Imported rather than read: the corpus is data, and an import says so. Reading it with
// `readFileSync` also made `source-scan.test.mjs` classify this file as a scanner of the
// repository's own source, which it is not — it runs the modules instead of reading them.
import FIXTURES from "./fixtures/blocks-parity.json" with { type: "json" };

/**
 * SH1015 — the files side of blocks, and the round trip it has to survive.
 *
 * The property asserted is the one `shio pull` depends on: **blocks → Markdown → blocks is exact**
 * for a document the projection produced. That is what makes a second pull of an unchanged post
 * write identical bytes, and what makes the three-way merge's base mean anything.
 *
 * What is deliberately NOT asserted is exactness after a hand edit that inserts a block. The keys
 * ride in the frontmatter by position, so an insert re-identifies everything after it. Content is
 * never lost or reordered — only re-keyed — and the test below pins that this is the failure mode
 * rather than a worse one.
 *
 * It imports the modules it judges rather than reading their source, so it carries no scan
 * declaration: claiming one would promise a stripper this file has no use for.
 */
test("SH1015: blocks survive the trip to Markdown and back, exactly", () => {
  assert.ok(FIXTURES.length >= 4,
    `SH1015: only ${FIXTURES.length} fixture(s) — the corpus this round trip is proved over is the `
      + "whole of the evidence, so a shrunken one is a weaker claim wearing the same name");

  for (const { name, blocks } of FIXTURES) {
    const markdown = toMarkdown(blocks);
    const back = fromMarkdown(markdown, keysOf(blocks));

    assert.deepEqual(back, blocks,
      `SH1015 (${name}): a document the projection wrote did not read back as itself. This is the `
        + "property `shio pull` depends on — without it a second pull of an unchanged post writes "
        + "different bytes and every later pull reports a local change nobody made (SH253's shape, "
        + "at a new field).");
  }
});

test("SH1015: the projection is idempotent, so a re-pull writes the same bytes", () => {
  for (const { name, blocks } of FIXTURES) {
    const once = toMarkdown(blocks);
    const twice = toMarkdown(fromMarkdown(once, keysOf(blocks)));

    assert.equal(twice, once,
      `SH1015 (${name}): projecting twice produced different Markdown, which is P5 failing at the `
        + "layer the merge reads");
  }
});

test("SH1015: a block added by hand is re-keyed and never dropped", () => {
  const blocks = [
    { key: "b1", type: "heading", level: 2, text: "Why" },
    { key: "b2", type: "paragraph", text: "Because." },
  ];
  // What an author actually does: opens the file and types a paragraph in the middle.
  const edited = "## Why\n\nInserted by hand.\n\nBecause.";
  const back = fromMarkdown(edited, keysOf(blocks));

  assert.equal(back.length, 3, "SH1015: the inserted paragraph is a block, not a lost line");
  assert.deepEqual(back.map((block) => block.text ?? null),
    ["Why", "Inserted by hand.", "Because."],
    "SH1015: content in the author's order — this is the part that must never be wrong");
  assert.equal(back[2].key, "b3",
    "SH1015: the keys shift, which is the named cost of carrying them by position. Nothing "
      + "outside the document references a key, so the price is that an editor re-identifies "
      + "those blocks once — and it is paid here rather than discovered later.");
});

test("SH1015: the frontmatter key is one name, shared", () => {
  assert.equal(BLOCK_KEYS, "blockKeys",
    "SH1015: the projection and the parse read one constant, or a rename breaks the round trip "
      + "silently — the body still parses and every block is re-keyed on every pull");
});

const { serializePost, parsePost, bodyFieldFor, bodyIsBlocks } =
  await import("../src/content-files.mjs");

/** The post-type a blocks body belongs to, as `shio pull` reports one. */
const BLOCKS_TYPE = {
  name: "Article",
  fields: [
    { name: "headline", widget: "Text", ordinal: 0 },
    { name: "BODY", widget: "Blocks", ordinal: 1 },
  ],
};

test("SH1015: a Blocks field is the body, ahead of an HTML Editor", () => {
  assert.equal(bodyFieldFor(BLOCKS_TYPE), "BODY");
  assert.equal(bodyIsBlocks(BLOCKS_TYPE), true);

  // Ahead, not instead — and this is what proves no existing file moves: a type with only an
  // HTML Editor answers exactly as it did.
  assert.equal(bodyFieldFor({ fields: [{ name: "content", widget: "HTML Editor", ordinal: 0 }] }),
    "content");
  assert.equal(bodyFieldFor({ fields: [
    { name: "content", widget: "HTML Editor", ordinal: 0 },
    { name: "BODY", widget: "Blocks", ordinal: 1 },
  ] }), "BODY", "SH1015: a type carrying both is one mid-migration, and the blocks are the body");
});

test("SH1015: a post with a blocks body writes a Markdown file and reads back identical", () => {
  const doc = {
    site: "Acme",
    url: "/about",
    type: "Article",
    folder: "/",
    data: {
      headline: "About",
      BODY: [
        { key: "b1", type: "heading", level: 2, text: "Why" },
        { key: "b2", type: "paragraph", text: "Because." },
      ],
    },
  };

  const file = serializePost(doc, "BODY", []);

  assert.match(file, /^blockKeys: \["b1","b2"\]$/mu,
    "SH1015: the identities ride in the frontmatter, because Markdown has nowhere to put one");
  assert.match(file, /## Why/u,
    "SH1015: the body is real Markdown — this is P4's whole payoff, an Edit that targets a "
      + "heading rather than a byte offset into a blob");
  assert.doesNotMatch(file, /\[object Object\]/u,
    "SH1015: the failure this replaced — a blocks value is an array, and stringifying it writes "
      + "`[object Object]` into somebody's content file");

  const back = parsePost("Acme/about.md", file);
  assert.deepEqual(back.data.BODY, doc.data.BODY,
    "SH1015: the document reads back as itself, which is what makes a second pull write identical "
      + "bytes and the three-way merge's base mean anything");
});

test("SH1015: a file with no blockKeys keeps the body a string", () => {
  const file = serializePost(
    { site: "Acme", url: "/x", type: "Page", folder: "/", data: { content: "<p>hi</p>" } },
    "content", []);
  const back = parsePost("Acme/x.md", file);

  assert.equal(typeof back.data.content, "string",
    "SH1015: the key list is what decides, not the post-type — reading a file back has never "
      + "depended on the model (see SH86) and this does not start now");
});
