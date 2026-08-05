/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH387 — the coverage gate, which is the one that says when a replication is done.
 *
 * Most of this file is about where the group stays **silent** and where it says "not checked", and
 * that is the point rather than an accident. A coverage report's only real failure mode is reading
 * as complete when it is not: a page nobody compared, counted as a clean one, ends the replication
 * early with a number instead of with the work. Every rule here is an *absence*, which is the class
 * of claim SH143 says a partial read must never make.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { REPLICATION_CHECK, READS_VERSION, lintReplication, replicationRules } from "../src/verify-replication.mjs";

const DEFS = [
  {
    name: "Article",
    fields: [
      { name: "headline", widget: "Text", ordinal: 1 },
      { name: "email", widget: "Text", ordinal: 2 },
    ],
  },
];

const page = (url, extra = {}) => ({ site: "Acme", url, type: "Article", data: {}, ...extra });

function capture(overrides = {}) {
  return {
    version: READS_VERSION,
    source: { origin: "https://acme.test", seed: "https://acme.test" },
    crawl: { pages: 2, truncated: false },
    pages: [{ path: "/" }, { path: "/about" }],
    assets: [],
    forms: [],
    locales: [],
    unfollowed: [],
    problems: [],
    ...overrides,
  };
}

const rules = (findings) => findings.map((each) => each.rule);

// --- the group's boundaries ---

test("no capture is a note, not a clean run", () => {
  // "0 findings" would be a claim about a comparison that never happened, on the most common
  // project there is: one that never cloned anything.
  const result = lintReplication({ inventory: null });

  assert.deepEqual(result.findings, []);
  assert.match(result.note, /no capture in this tree/);
});

test("a scoped run is skipped and says so, because every rule here is an absence", () => {
  const result = lintReplication({
    posts: [],
    defs: DEFS,
    inventory: capture(),
    scoped: true,
  });

  assert.deepEqual(result.findings, []);
  assert.match(result.note, /a scoped run cannot prove an absence/);
});

test("every finding carries the group and an address naming the source, not an invented post", () => {
  const { findings } = lintReplication({ posts: [], defs: DEFS, inventory: capture() });

  assert.ok(findings.length);
  for (const finding of findings) {
    assert.equal(finding.source, REPLICATION_CHECK);
    // An invented `post:Acme/about` would be an address `shio resolve` cannot resolve — the subject
    // of every rule here is something that does NOT exist in the replica.
    assert.equal(finding.address, "source:https://acme.test");
  }
});

// --- source-page-missing (SH385's rule, in the group that owns it) ---

test("a source page the tree neither serves nor redirects is a warning", () => {
  const { findings } = lintReplication({
    posts: [page("/")],
    defs: DEFS,
    inventory: capture(),
  });

  const missing = findings.filter((each) => each.rule === "source-page-missing");
  assert.equal(missing.length, 1);
  assert.equal(missing[0].severity, "warning");
  assert.match(missing[0].message, /\/about/);
});

test("SH433: the finding for the source's root says how to spell the address", () => {
  // `/` is the first row this rule reports on every replication, and its fix said "give the replica a
  // page at that URL" — while nothing written down said how to write one. `folder:<site>` is refused
  // (a folder needs a path), agents.md gave post:<site>/<friendly-url>, and an empty friendly URL
  // reads as the same malformed case. Finding `post:<site>/` cost probes against a live instance.
  const { findings } = lintReplication({ posts: [], defs: DEFS, inventory: capture() });

  const root = findings.find((each) => each.rule === "source-page-missing" && /`\/`/.test(each.message));
  assert.ok(root, "the root is reported like any other path");
  assert.match(root.fix, /post:<site>\//, "and the fix names the address, which is the whole task");
  assert.match(root.fix, /friendly URL is `\/`/);

  const other = findings.find((each) => each.rule === "source-page-missing" && /about/.test(each.message));
  assert.ok(
    !/post:<site>\//.test(other.fix),
    "and only the root carries it: a sentence about the home page on every missing page is the kind "
      + "of padding that makes a fix line stop being read",
  );
});

test("a Redirect answering the path counts as served, because it is a post with that url", () => {
  const { findings } = lintReplication({
    posts: [page("/"), { site: "Acme", url: "/about", type: "Redirect", data: { TO: "/company" } }],
    defs: DEFS,
    inventory: capture(),
  });

  assert.ok(!rules(findings).includes("source-page-missing"));
});

test("a trailing slash, a case difference and a query string are not gaps", () => {
  const { findings } = lintReplication({
    posts: [page("/"), page("/About/")],
    defs: DEFS,
    inventory: capture({ pages: [{ path: "/" }, { path: "/about" }, { path: "/about?utm=1" }] }),
  });

  assert.ok(!rules(findings).includes("source-page-missing"));
});

// --- source-link-unresolved ---

test("an internal link the crawl never followed and the tree lacks is its own finding", () => {
  // Distinct from source-page-missing on purpose: this is a gap in the CAPTURE, not in the replica,
  // and before SH387 the inventory did not record it — so a fidelity report counted it as absent
  // from the source, which is the opposite claim.
  const { findings } = lintReplication({
    posts: [page("/"), page("/about")],
    defs: DEFS,
    inventory: capture({ unfollowed: [{ path: "/deep/offer", refs: 4 }] }),
  });

  const found = findings.filter((each) => each.rule === "source-link-unresolved");
  assert.equal(found.length, 1);
  assert.match(found[0].message, /the capture never crawled it/);
  assert.match(found[0].fix, /Raise --depth or --max-pages/);
});

test("an unfollowed link the replica does hold is not a finding", () => {
  const { findings } = lintReplication({
    posts: [page("/"), page("/about"), page("/deep/offer")],
    defs: DEFS,
    inventory: capture({ unfollowed: [{ path: "/deep/offer", refs: 4 }] }),
  });

  assert.ok(!rules(findings).includes("source-link-unresolved"));
});

// --- source-asset-missing ---

test("an asset never downloaded and one downloaded then lost are one rule with two messages", () => {
  const { findings } = lintReplication({
    posts: [page("/"), page("/about")],
    defs: DEFS,
    assets: [],
    inventory: capture({
      assets: [
        { path: "/img/a.png", kind: "image", refs: 3 },
        { path: "/img/b.png", kind: "image", refs: 1, file: "Acme/img/b.png" },
      ],
    }),
  });

  const found = findings.filter((each) => each.rule === "source-asset-missing");
  assert.equal(found.length, 2);
  // The fixes are different commands, so the messages have to be distinguishable.
  assert.ok(found.some((each) => /was never downloaded/.test(each.message)));
  assert.ok(found.some((each) => /which is not in the tree/.test(each.message)));
});

test("an off-origin asset is not a gap, because SH378 decided not to mirror it", () => {
  const { findings } = lintReplication({
    posts: [page("/"), page("/about")],
    defs: DEFS,
    assets: [],
    inventory: capture({
      assets: [{ path: "https://cdn.test/x.png", kind: "image", refs: 2, external: true }],
    }),
  });

  assert.ok(!rules(findings).includes("source-asset-missing"));
});

test("an asset present on disk is not a gap", () => {
  const { findings } = lintReplication({
    posts: [page("/"), page("/about")],
    defs: DEFS,
    assets: ["Acme/img/b.png"],
    inventory: capture({
      assets: [{ path: "/img/b.png", kind: "image", refs: 1, file: "Acme/img/b.png" }],
    }),
  });

  assert.ok(!rules(findings).includes("source-asset-missing"));
});

// --- source-form-field-lost ---

test("a source form field no post-type declares is a warning, and the limit is stated", () => {
  const { findings } = lintReplication({
    posts: [page("/"), page("/about")],
    defs: DEFS,
    inventory: capture({
      forms: [{
        path: "/contact",
        action: "/contact",
        method: "post",
        fields: [
          { name: "email", type: "email", required: true },
          { name: "company", type: "text", required: false },
          { name: null, type: "submit", required: false },
        ],
        on: 1,
      }],
    }),
  });

  const found = findings.filter((each) => each.rule === "source-form-field-lost");
  // `email` is declared by Article; `company` is not; the submit button carries no value.
  assert.equal(found.length, 1);
  assert.match(found[0].message, /company/);
  // The claim is bounded and says so — there is no form storage in this product.
  assert.match(found[0].fix, /can only check that a field is DECLARED/);
});

test("a field a Relator's child declares counts as declared", () => {
  const { findings } = lintReplication({
    posts: [page("/"), page("/about")],
    defs: [
      ...DEFS,
      { name: "Grid", fields: [{ name: "rows", widget: "Relator", fields: [{ name: "company" }] }] },
    ],
    inventory: capture({
      forms: [{ path: "/c", action: "/c", method: "post", fields: [{ name: "company", type: "text" }], on: 1 }],
    }),
  });

  assert.ok(!rules(findings).includes("source-form-field-lost"));
});

// --- source-locale-missing ---

test("a source locale no post carries is a warning, and x-default is not a language", () => {
  const { findings } = lintReplication({
    posts: [page("/", { locale: "en" }), page("/about", { locale: "en" })],
    defs: DEFS,
    inventory: capture({
      locales: [
        { locale: "en", shape: "declared", sample: "/", pages: 2 },
        { locale: "pt-BR", shape: "prefix", sample: "/pt/", pages: 1 },
        { locale: "x-default", shape: "default", sample: "/", pages: 1 },
      ],
    }),
  });

  const found = findings.filter((each) => each.rule === "source-locale-missing");
  assert.equal(found.length, 1);
  assert.match(found[0].message, /pt-BR/);
});

test("a post with no locale means the instance default, not a missing locale", () => {
  // The defect a smoke run caught: the projection writes `locale:` only for a NON-default locale
  // (SH348), so a monolingual English tree states its locale by omission — and reading omission as
  // absence reported `en` missing on a tree whose only page was English.
  //
  // Which locale the default IS cannot be known server-free, so the capture answers it: `declared`
  // is the source's own <html lang>. The inference is in the notes rather than silent.
  const result = lintReplication({
    posts: [page("/"), page("/about")],
    defs: DEFS,
    inventory: capture({
      locales: [
        { locale: "en", shape: "declared", sample: "/", pages: 2 },
        { locale: "pt-BR", shape: "prefix", sample: "/pt/", pages: 1 },
      ],
    }),
  });

  const missing = result.findings.filter((each) => each.rule === "source-locale-missing");
  assert.deepEqual(missing.map((each) => each.message.match(/`([^`]+)`/)[1]), ["pt-BR"]);
  assert.match(result.note, /counted as covering .en., the locale the source itself declares/);
});

test("an unlabelled post with no primary locale in the capture is unproven, not inferred", () => {
  const result = lintReplication({
    posts: [page("/"), page("/about")],
    defs: DEFS,
    inventory: capture({
      locales: [{ locale: "fr", shape: "prefix", sample: "/fr/", pages: 1 }],
    }),
  });

  // No `declared` and no `x-default` means the capture cannot say which locale the source calls its
  // own, so guessing would be the fabrication this whole group is built to avoid.
  assert.match(result.note, /which language they are in is unproven here/);
});

test("a locale matches case-insensitively, so pt-BR and pt-br are one locale", () => {
  const { findings } = lintReplication({
    posts: [page("/", { locale: "en" }), page("/about", { locale: "pt-br" })],
    defs: DEFS,
    inventory: capture({
      locales: [
        { locale: "en", shape: "declared", sample: "/", pages: 2 },
        { locale: "pt-BR", shape: "prefix", sample: "/pt/", pages: 1 },
      ],
    }),
  });

  assert.ok(!rules(findings).includes("source-locale-missing"));
});

// --- replication-unchecked: the rule that keeps the report honest ---

test("a truncated capture is reported as unchecked, and the note says every count is a lower bound", () => {
  // The failure this exists for: a clean run over a capture that stopped at its ceiling reads as
  // "the replica is complete" when it means "complete among the pages we happened to see".
  const result = lintReplication({
    posts: [page("/"), page("/about")],
    defs: DEFS,
    inventory: capture({ crawl: { pages: 2, truncated: true } }),
  });

  assert.ok(rules(result.findings).includes("replication-unchecked"));
  assert.match(result.note, /PARTIAL/);
  assert.match(result.note, /lower bound/);
});

test("a source page the crawl could not read at all is unchecked, not absent", () => {
  const { findings } = lintReplication({
    posts: [page("/"), page("/about")],
    defs: DEFS,
    inventory: capture({ problems: [{ path: "/broken", status: 500, reason: "HTTP 500" }] }),
  });

  const found = findings.filter((each) => each.rule === "replication-unchecked");
  assert.equal(found.length, 1);
  assert.match(found[0].message, /could not be read at all/);
  // A 500 on the source is the source's defect, not a gap in the replica, and the fix says so.
  assert.match(found[0].fix, /neither is a gap in the replica/);
});

test("a locale the capture could not place in a URL is unchecked", () => {
  const { findings } = lintReplication({
    posts: [page("/", { locale: "en" }), page("/about", { locale: "en" })],
    defs: DEFS,
    inventory: capture({
      locales: [{ locale: "en", shape: "unknown", sample: "/", pages: 2 }],
    }),
  });

  assert.ok(findings.some((each) => each.rule === "replication-unchecked"
    && /appear nowhere in a URL/.test(each.message)));
});

test("a newer capture than this reader understands is unchecked, not silently trusted", () => {
  const { findings } = lintReplication({
    posts: [page("/"), page("/about")],
    defs: DEFS,
    inventory: capture({ version: READS_VERSION + 5 }),
  });

  assert.ok(findings.some((each) => each.rule === "replication-unchecked"
    && /this command reads/.test(each.message)));
});

test("an older capture says which rule checked nothing, rather than reporting a clean one", () => {
  // A version-1 file has no `unfollowed`, so source-link-unresolved compared an empty list. Reading
  // that as "no unresolved links" is the lower-bound-as-a-pass defect, one field along.
  const result = lintReplication({
    posts: [page("/"), page("/about")],
    defs: DEFS,
    inventory: capture({ version: 1, unfollowed: undefined }),
  });

  assert.match(result.note, /source-link-unresolved checked nothing/);
});

test("SH429: bytes in the tree that nothing references are their own finding", () => {
  // The composition three right rules produced. SH378 downloads each referenced image into
  // shio/assets/<site>/...; SH145 uploads an asset only when a File field names its path; SH379 makes
  // FIDELITY the default, and verbatim markup has no fields. So on a real replica the logo sat on
  // disk with correct bytes, the layout's <img src="/viglet-logo.png"> resolved to nothing, and the
  // coverage gate passed - because source-asset-missing asks whether the bytes were downloaded, and
  // they were.
  const { findings } = lintReplication({
    posts: [page("/"), page("/about")],
    defs: DEFS,
    assets: ["Acme/img/logo.png"],
    inventory: capture({
      assets: [{ path: "/img/logo.png", kind: "image", refs: 4, file: "Acme/img/logo.png" }],
    }),
  });

  const stranded = findings.filter((each) => each.rule === "source-asset-stranded");
  assert.equal(stranded.length, 1);
  assert.match(stranded[0].message, /nothing serves it/);
  assert.match(stranded[0].fix, /FIDELITY mode has no fields/,
    "and the fix names the composition rather than the symptom: the download is not the delivery");
  assert.ok(
    !findings.some((each) => each.rule === "source-asset-missing"),
    "the bytes ARE downloaded, so the older rule is right to stay quiet - these are two states",
  );
});

test("SH429: an asset a File field names is served, and says nothing", () => {
  const { findings } = lintReplication({
    posts: [page("/", { data: { image: "Acme/img/logo.png" } }), page("/about")],
    defs: DEFS,
    assets: ["Acme/img/logo.png"],
    inventory: capture({
      assets: [{ path: "/img/logo.png", kind: "image", refs: 4, file: "Acme/img/logo.png" }],
    }),
  });

  assert.ok(!findings.some((each) => each.rule === "source-asset-stranded"));
});

test("SH429: an asset that was never downloaded stays the older rule's, not this one's", () => {
  const { findings } = lintReplication({
    posts: [page("/"), page("/about")],
    defs: DEFS,
    inventory: capture({ assets: [{ path: "/img/logo.png", kind: "image", refs: 4 }] }),
  });

  assert.equal(findings.filter((each) => each.rule === "source-asset-missing").length, 1);
  assert.ok(
    !findings.some((each) => each.rule === "source-asset-stranded"),
    "bytes that are not here cannot be stranded here, and two findings over one asset would be two "
      + "reports that can disagree",
  );
});

test("SH431: a served non-HTML response is reported and never counted as unchecked", () => {
  // The defect this closes, in the form it was measured: 24 rows of `application/json` — a site's
  // data surface, linked on purpose and served correctly — arrived in `problems` and became "24
  // source page(s) could not be read at all". Two dozen unfixable warnings on a first run is how a
  // coverage report loses the trust that makes it the answer to "is this replication done".
  const result = lintReplication({
    posts: [page("/"), page("/about")],
    defs: DEFS,
    inventory: capture({
      data: [
        { path: "/catalog-v1.json", status: 200, type: "application/json" },
        { path: "/badge.json", status: 200, type: "application/json" },
      ],
    }),
  });

  assert.ok(
    !result.findings.some((each) => each.rule === "replication-unchecked"),
    "not a finding: nothing here was unchecked, and a warning nobody can act on is how the group "
      + "stops being read",
  );
  assert.match(
    result.note,
    /2 served non-HTML response\(s\)/,
    "reported, though — a data endpoint may well deserve replicating, and a reader should be able "
      + "to see one without opening the inventory",
  );
});

test("SH431: a real failure still downgrades the run, which is the case the rule exists for", () => {
  const result = lintReplication({
    posts: [page("/"), page("/about")],
    defs: DEFS,
    inventory: capture({
      problems: [{ path: "/pricing", status: 500, reason: "HTTP 500" }],
      data: [{ path: "/catalog-v1.json", status: 200, type: "application/json" }],
    }),
  });

  const unchecked = result.findings.filter((each) => each.rule === "replication-unchecked");
  assert.equal(unchecked.length, 1, "one, for the 500 — not two, and not none");
  assert.match(unchecked[0].message, /1 source page\(s\) could not be read/);
});

test("SH431: a version-2 capture says the count may include a data surface", () => {
  // The honest half of the version bump. In a version-2 file the data files ARE in `problems`, so a
  // reader that knows the difference has to say that its own count is the older, blunter one —
  // naming the rule that is affected, not just "this file is older".
  const result = lintReplication({
    posts: [page("/"), page("/about")],
    defs: DEFS,
    inventory: capture({ version: 2, data: undefined }),
  });

  assert.match(result.note, /filed as an unreadable page before 3/);
  assert.ok(
    !/unfollowed. arrived in 2/.test(result.note),
    "and it does not claim source-link-unresolved checked nothing: version 2 has `unfollowed`",
  );
});

// --- shared shapes ---

test("the rows are capped and the cut is declared in every message, not in a note", () => {
  // A reader who sees twenty rows and a note three screens down has not been told. Every row
  // carries the total, so any one of them is enough.
  const many = capture({ pages: [] });
  for (let index = 0; index < 30; index += 1) many.pages.push({ path: `/gone-${index}` });

  const { findings } = lintReplication({ posts: [], defs: DEFS, inventory: many });
  const missing = findings.filter((each) => each.rule === "source-page-missing");

  assert.equal(missing.length, 20);
  for (const finding of missing) {
    assert.match(finding.message, /30 in all; 20 shown/);
  }
});

test("two runs over one tree and one capture read the same", () => {
  const input = () => ({
    posts: [page("/")],
    defs: DEFS,
    inventory: capture({
      assets: [{ path: "/img/a.png", kind: "image", refs: 1 }],
      locales: [{ locale: "de", shape: "prefix", sample: "/de/", pages: 1 }],
    }),
  });

  assert.deepEqual(lintReplication(input()), lintReplication(input()));
});

test("every rule this group emits is in replicationRules(), or the baseline misfiles it", () => {
  // The coverage split (SH410/SH411) reads this set to decide which group an accepted entry belongs
  // to. A rule missing from it is classified as the server's, so a tree-only run would call the
  // acceptance stale and fail the build.
  const emitted = new Set();
  for (const input of [
    { posts: [], defs: DEFS, inventory: capture({ problems: [{ path: "/x", status: 500, reason: "x" }] }) },
    {
      posts: [],
      defs: DEFS,
      inventory: capture({
        assets: [{ path: "/a.png", kind: "image", refs: 1 }],
        forms: [{ path: "/c", action: "/c", method: "post", fields: [{ name: "zz", type: "text" }], on: 1 }],
        locales: [{ locale: "de", shape: "prefix", sample: "/de/", pages: 1 }],
        unfollowed: [{ path: "/deep", refs: 1 }],
      }),
    },
  ]) {
    for (const finding of lintReplication(input).findings) emitted.add(finding.rule);
  }

  assert.ok(emitted.size >= 6, [...emitted].join(" "));
  for (const rule of emitted) {
    assert.ok(replicationRules().has(rule), `${rule} is not in replicationRules()`);
  }
});
