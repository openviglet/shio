---
name: shio-docs-sync
description: Validate that Shio's end-user documentation (shio-site/docs, served at shio.viglet.org/docs) is in sync with the code, and report or file every drift. Runs the docs conformance suites (vocabulary, stack, coverage), a reverse code-to-docs report (CLI verbs, MCP tools and resources, batch ops, verify checks, shio.* properties, manifest features no page names), sidebar membership and internal-link/anchor resolution, then a judgment pass over what no instrument reads (defaults, limits, behaviour described in prose, pages that claim to be complete, features shipped since a page was last touched). Use when asked to check, audit, validate or sync the docs against the code, before a release, after a block completes, or when a docs page is suspected stale. Trigger words: docs sync, documentação, shio-site/docs, stale docs, COVERAGE.md, configuration reference, docs drift.
---

# Is shio-site/docs in sync with the code?

The pages live in `shio-site/docs/src/content/docs/` (SH1262), with `COVERAGE.md` beside them
(excluded from the collection). Writing a page and recording its COVERAGE verdict is
`shio-roadmap-docs`'s job; **this skill only measures**, then fixes or files what it measured.

Run from the repo root. Three passes, in this order, because each is cheaper and more certain
than the next.

## Pass 1: the gate (docs → code, mechanical)

```
pnpm conformance:docs
```

It runs `docs-vocabulary` (every verb, route, MCP tool/resource, op, `--checks` group and
`shio.*` property a page names still exists; every registered tool is on the tool table; every
section type is named), `docs-stack` (stated versions match `pom.xml`/`package.json`; no retired
dependency presented as part of the stack) and `docs-coverage` (COVERAGE's summary matches its
tables). Read `shio-conformance-docs.log`; **never pipe the gate into `grep`** (agents.md, SH354/
SH788). A red here is a confirmed drift with the fix printed: start with it.

The fourth instrument, `docs-examples.conformance.mjs`, *issues* the documented requests and
needs a running instance (it is in `pnpm conformance`, not here). Run it only when the question
is "does the documented request still work", not "is the name right".

## Pass 2: the reverse report (code → docs, mechanical)

```
node .claude/skills/shio-docs-sync/docs-sync-report.mjs          # add --json or --strict
```

The gate is blind in this direction: something the product **has** and no page mentions passes
all three suites. The report reads the same populations the vocabulary suite does
(`cli/conformance/support/product-vocabulary.mjs`), so the two cannot disagree about what exists,
and prints:

- **declared, named on no page**, per population. `shio.*` properties accept the camelCase form
  (Spring relaxed binding). `manifest feature keys` is **advisory**: pages describe a feature in
  prose, rarely by its key, so a miss is a question, not a finding;
- **pages missing from the sidebar** in `shio-site/docs/astro.config.mjs` (they build as unlisted
  routes nobody reaches);
- **hand-typed `/docs/...` links** whose page, heading anchor or `public/` file does not exist.

`--strict` exits 1 on any non-advisory gap; the default is 0, because some gaps are deliberate.

## Pass 3: what only a reader can judge

No instrument reads meaning. Spend the reading where drift is likely, not across every page:

1. **What shipped since the page was last touched.** `git log -1 --format=%cs -- <page>` gives
   the page's date; `git log --since=<date> --oneline -- <the code the page documents>` lists the
   commits it may not know about. A `feat:` there with no COVERAGE row, or a row pointing at an
   anchor that does not describe it, is drift.
2. **Pages that claim completeness.** "Every property below…", "the twelve tools", "all
   endpoints": a completeness claim is falsified by a single gap from Pass 2 (`configuration-reference.md`
   is the standing example: it says it lists every property).
3. **Defaults and limits in tables.** For each `| key | default |` row, compare with the
   `@Value("${key:default}")` / `@ConfigurationProperties` field or `application.properties`.
   A name check passes a wrong default.
4. **Behaviour in prose.** Status codes, response shapes, CLI flags, console steps. Check the
   handler or the command's `--help`, not memory. A ⚠️ COVERAGE row means a page already
   describes a shape the product left; re-read those first.

## Classify, then act

For every gap, decide one of three, and say which:

| Verdict | Action |
|---|---|
| **Drift**: the page is wrong or silent about something a user would do differently | Fix it if the user asked for fixes; otherwise **file a task** |
| **Deliberately internal** (an internal limit, a test-only property) | A 🔒 row in `COVERAGE.md` if the cluster has none; nothing else |
| **False positive** of the report | Note it; if it recurs, fix the report rather than re-judging it each run |

**Filing:** prove the gap with a command first (a grep with zero hits, a `--help`, a handler
line), never from reading alone. Search the backlog before adding (`mcp__roadkeep__list` /
`section_find`), then `mcp__roadkeep__add` under the docs block (`shio-roadmap-docs` names it),
one task per page or per coherent cluster, not one per missing key. Name the evidence in the line.

**Fixing:** follow `shio-roadmap-docs` § end-user docs (write for the user, fold into the closest
page, update the COVERAGE row, `pnpm run coverage:mirror`). Then re-run Pass 1 and Pass 2; a fix
that leaves its gap in the report is not done.

## Report back

One table per pass: what was checked (counts, printed by each tool), what failed, the verdict
per gap, and what was fixed or filed (task ids). State plainly what was **not** checked (for
example, Pass 3 sampled four pages, or `docs-examples` was skipped for lack of an instance).
