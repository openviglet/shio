/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { render, unroutable } from "../src/commands/index.mjs";

/**
 * SH1063 — `shio index status`, and the row it exists for.
 *
 * The endpoint is asserted where it is built (`ShIndexStatusTest`); what is worth holding here is
 * that the one state worth acting on — published pages with no route into the index — is both
 * **visible** in the text and **actionable** as an exit code, and that the two are built from the
 * same function so they cannot come apart.
 */
const ADRIFT = {
  enabled: true,
  target: "http://turing:2700",
  sites: [
    { site: "Blog", searchSite: "Blog", published: 12 },
    { site: "Careers", searchSite: null, published: 4 },
    { site: "Scratch", searchSite: null, published: 0 },
  ],
  failures: 2,
};

const OFF = { enabled: false, sites: [{ site: "Blog", searchSite: null, published: 12 }], failures: 0 };

test("SH1063: a site publishing into nothing is named, and is the exit code", () => {
  const out = render(ADRIFT);

  assert.match(out, /Careers -> \(nowhere\) {2}4 published {2}UNROUTABLE/u,
    "SH1063: the failure raises no error anywhere else, so the line has to say it outright");
  assert.doesNotMatch(out, /Blog -> Blog {2}12 published {2}UNROUTABLE/u);
  assert.match(out, /Scratch -> \(nowhere\)/u);
  assert.doesNotMatch(out, /Scratch[^\n]*UNROUTABLE/u,
    "SH1063: a site with nothing published is not missing from an index");
  assert.match(out, /2 indexing failure\(s\)/u);

  assert.deepEqual(unroutable(ADRIFT).map((route) => route.site), ["Careers"],
    "SH1063: a status command that always exits 0 is one a script cannot act on");
});

test("SH1063: with indexing off there is nothing to route and nothing to fix", () => {
  const out = render(OFF);

  assert.match(out, /^indexing off/u);
  assert.deepEqual(unroutable(OFF), [],
    "SH1063: switched off is a decision, not a defect — every site routes nowhere by design");
});
