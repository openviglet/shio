/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH695 — the JavaScript half of a judgement that exists twice.
 *
 * The shared cases live in `fixtures/regions.json` and are read by this file and by
 * `ShHtmlRegionsAgreementTest` on the Java side. Neither reader may answer a case the other does
 * not: they read two different renderings of the same conversion, and `shio diff` is about to score
 * one against the other (SH692).
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

import { SECTIONS, VERBATIM, countByMarkup, readRegions } from "../src/regions.mjs";

const FIXTURES = JSON.parse(
  readFileSync(fileURLToPath(new URL("./fixtures/regions.json", import.meta.url)), "utf8"),
);

const shape = (regions) => regions.map((region) => `${region.tag}:${region.markup}`);

test("SH695: every shared case answers what the fixture says it answers", () => {
  assert.ok(FIXTURES.cases.length >= 10, "a fixture set that shrank is a guard that stopped asking");

  for (const each of FIXTURES.cases) {
    assert.deepEqual(
      shape(readRegions(each.html)),
      each.expect,
      `${each.name}\n  html: ${each.html}`,
    );
  }
});

test("SH695: the order is the document's, which is the field the histogram could not carry", () => {
  const regions = readRegions(FIXTURES.cases[0].html);

  assert.deepEqual(
    regions.map((region) => region.order),
    [1, 2, 3, 4, 5, 6],
    "1-based and contiguous, so `footer 3` beside `section 5` is a comparison a reader can make",
  );
  assert.equal(
    regions.find((region) => region.tag === "footer").order < regions.find((region) => region.tag === "section").order,
    true,
    "and on the replica's own shape the footer really does come first (SH687)",
  );
});

test("SH695: containment is a stack, so a tag count cannot be substituted for it", () => {
  // The case that separates the two: three sections' worth of markers, one of them closed before
  // the landmark it would wrongly claim. Counting occurrences gets this wrong; a stack does not.
  const html = "<body><section sh-post=a1>x</section><div><footer>f</footer></div>"
    + "<section sh-post=b2><aside>a</aside></section></body>";

  assert.deepEqual(shape(readRegions(html)), [
    `section:${SECTIONS}`,
    `footer:${VERBATIM}`,
    `section:${SECTIONS}`,
    `aside:${SECTIONS}`,
  ]);
});

test("SH695: the counts diff will score with", () => {
  assert.deepEqual(countByMarkup(readRegions(FIXTURES.cases[0].html)), {
    [VERBATIM]: 4,
    [SECTIONS]: 2,
  });
  assert.deepEqual(countByMarkup([]), { [VERBATIM]: 0, [SECTIONS]: 0 });
});

test("SH695: nothing in, nothing out — and never a throw", () => {
  // `diff` reads whatever the replica served, including a 500 body and an empty response.
  assert.deepEqual(readRegions(""), []);
  assert.deepEqual(readRegions(null), []);
  assert.deepEqual(readRegions(undefined), []);
  assert.deepEqual(readRegions("not markup at all"), []);
});
