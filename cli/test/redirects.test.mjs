/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { orphans, render, rows } from "../src/commands/redirects.mjs";

/**
 * SH1064 — `shio redirects`, and the half of it nobody could ask for.
 *
 * The table itself is a `find --type Redirect` with the fields joined on, and what is worth
 * holding here is the join and the second half: an address the site used to serve, still linked
 * to, answering nothing. The op that repairs it is printed beside it, because a listing that
 * names a defect and not its repair is the P6 shape this project keeps paying for.
 */
const FOUND = {
  results: [
    { address: "post:Acme/plans-old", state: "published" },
    { address: "post:Acme/pricing", state: "published" },
  ],
};

const READ = [
  { address: "post:Acme/pricing", data: { TITLE: "x", TO: "/plans", PERMANENT: "1" } },
  { address: "post:Acme/plans-old", data: { TITLE: "y", TO: "/plans", PERMANENT: false } },
];

const REPORT = {
  findings: [
    { rule: "link-unresolved", address: "post:Acme/home", message: "Link '/docs' matches nothing" },
    {
      rule: "url-orphaned",
      address: "post:Acme/docs",
      message: "Site 'Acme' used to serve '/docs' and does not now, and 3 published page(s) "
        + "still link to it",
      fix: "Keep it answering with a post.redirect op",
    },
  ],
};

test("SH1064: the table joins each redirect's fields, and 301 is not guessed from the type", () => {
  const table = rows(FOUND, READ);

  assert.deepEqual(table.map((row) => [row.from, row.to, row.permanent]), [
    ["/plans-old", "/plans", false],
    ["/pricing", "/plans", true],
  ], "SH1064: a Check Box is \"1\" from the console and a boolean from an op, and a redirect that "
    + "reads as 302 because the value arrived as a string is the defect nobody finds by looking");
});

test("SH1064: an orphaned address is listed with the op that repairs it", () => {
  const out = render(rows(FOUND, READ), orphans(REPORT));

  assert.match(out, /2 redirect\(s\)/u);
  assert.match(out, /\/pricing -> \/plans {2}301/u);
  assert.match(out, /\/plans-old -> \/plans {2}302/u);
  assert.match(out, /\/docs {2}3 inbound link\(s\)/u);
  assert.match(out, /"op":"post\.redirect","address":"post:Acme\/docs"/u,
    "SH1064: the reader has to be able to act on the row without composing the op themselves");
});

test("SH1064: nothing orphaned says so, and --no-check says it did not look", () => {
  assert.match(render(rows(FOUND, READ), []), /no orphaned addresses/u);
  assert.match(render(rows(FOUND, READ), [], { checked: false }), /orphans not checked/u);
  assert.doesNotMatch(render(rows(FOUND, READ), [], { checked: false }), /no orphaned addresses/u,
    "SH1064: 'I did not look' and 'there are none' are different answers, and printing the "
    + "second for the first is how a skipped check reads as a clean bill");
});

test("SH1133: a site with a front-end URL is told which table its front end has to read", () => {
  const frontEnds = new Map([["Acme", { id: "site-42", url: "https://www.acme.test" }]]);
  const table = rows(FOUND, READ, frontEnds);

  assert.deepEqual(table.map((row) => row.frontEnd), ["https://www.acme.test", "https://www.acme.test"]);
  const out = render(table, [], { frontEnds });
  assert.match(out, /Acme is served at https:\/\/www\.acme\.test\./u);
  assert.match(out, /GET \/api\/v2\/cda\/site\/site-42\/redirects/u,
    "SH1133: this instance honours the rows; a front end of its own honours them only by reading "
      + "the table, and the note names the exact read");

  assert.doesNotMatch(render(rows(FOUND, READ), []), /served at/u,
    "SH1133: a site with no front-end URL is served here, where the rows are the whole answer");
});

test("SH1064: a redirect with no target is the row worth seeing", () => {
  const table = rows({ results: [{ address: "post:Acme/old", state: "published" }] },
    [{ address: "post:Acme/old", data: { TITLE: "z", TO: "" } }]);

  assert.match(render(table, []), /\/old -> \(no target — it renders as a page\)/u,
    "SH1064: an empty TO renders as an ordinary page (SH385), so a blank column would read as a "
    + "rendering problem rather than as the unfinished redirect it is");
});
