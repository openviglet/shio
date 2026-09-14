/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

import { AREAS, census } from "./support/chrome-vocabulary.mjs"

// @scans both — reason: the census reads code and comments on purpose; see
// support/chrome-vocabulary.mjs, which owns the probes (SH740).

/**
 * SH957 — the two-chrome vocabulary Block AW removes, as a number that may only shrink.
 *
 * <p>SH886 opened Block AQ with three figures grepped by hand, and two were wrong. This block's own
 * line did it a fourth time: it says 108 source lines and 180 test and script lines, and the first
 * run of this reader measured <b>89 and 81</b>. The hand figure counted every occurrence of the
 * word, including the ones a one-chrome console would still write — "the panel carries the chrome",
 * "whatever chrome sits above it". A sweep scoped by that number could never have reached zero, and
 * would not have known why.</p>
 *
 * <h2>What is counted</h2>
 *
 * <p>Five probes, in `support/chrome-vocabulary.mjs`: `SectionCardChromeProvider`,
 * `useSectionChrome`, a comparison against `"bento"`/`"console"`, a `chrome=` prop, and a comment
 * naming a chrome beside an era. A line is counted once however many probes it matches.</p>
 *
 * <h2>Why a list and not a ceiling</h2>
 *
 * <p>The contract `console-chrome-census.test.mjs` and `assertion-debt.test.mjs` use, for their
 * reason: a ceiling lets one task's deletions fund another's additions. Each file is named with its
 * count, so a new reference fails naming the file, and a removal fails until its line here is
 * lowered or deleted in the same commit — which is what makes each of SH958–SH962 say, in its own
 * diff, how much it took.</p>
 *
 * <p><b>The list reaching zero is Block AW's acceptance criterion.</b> It cannot, while SH963 waits
 * on the design system: `SectionCardChromeProvider` is its export, and the root provider has to
 * name a chrome until the package stops having two.</p>
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const ROOT = join(HERE, "..")

/** This reader's own two files, which describe the probes and so match them by construction. */
const SELF = ["test/chrome-vocabulary-census.test.mjs", "test/support/chrome-vocabulary.mjs"]

/**
 * Every file still naming the two-chrome vocabulary, with its line count. **The debt.**
 *
 * <p>Lower a number or delete a line in the commit that removes the references; add one only in a
 * diff that says why a new reference to a retired era is the right call.</p>
 */
const STILL_TWO_CHROMES = {
  "e2e/console-check.mjs": 7,
  // SH958 — 39 → 11: twenty-one per-route wrappers became one provider above <Routes>.
  "src/App.tsx": 11,
  "src/app/bento/bento-nav.ts": 1,
  // SH959 — the ChromeWitness is gone; the provider around the adapter specimen stays until SH963.
  "src/app/bento/bento-vocabulary.page.tsx": 5,
  "src/app/bento/bento.root.page.tsx": 1,
  "src/app/bento/content/post-type-select.page.tsx": 3,
  "src/app/bento/home/home.page.tsx": 1,
  "src/app/console/content/object.children.page.tsx": 2,
  "src/app/console/content/post-type.edit.page.tsx": 4,
  "src/app/console/content/post.edit.page.tsx": 2,
  // SH959 — the console branches in these three are deleted; not-found.page.tsx leaves the list.
  "src/app/console/content/static-file.list.page.tsx": 1,
  "src/app/console/graphql/graphql.page.tsx": 2,
  "src/app/routes/graphql.routes.tsx": 1,
  "src/components/page-hero.tsx": 2,
  "src/components/panel.tsx": 2,
  "test/accent-token.test.mjs": 2,
  "test/bento-admin-slice.test.mjs": 3,
  "test/bento-browser.test.mjs": 6,
  "test/bento-home.test.mjs": 2,
  "test/bento-i18n.test.mjs": 3,
  "test/bento-identity-slice.test.mjs": 1,
  "test/bento-is-the-default.test.mjs": 1,
  "test/bento-nav.test.mjs": 1,
  "test/bento-post-slice.test.mjs": 3,
  "test/bento-shell.test.mjs": 2,
  "test/bento-vocabulary.test.mjs": 2,
  "test/chrome-adapter.test.mjs": 10,
  // SH958 — holds the root provider in place, so it names it until SH963 deletes both.
  "test/chrome-provider-root.test.mjs": 10,
  // SH960 — the page census (20 lines) is deleted, and the digest and its test lost the era field.
  "test/empty-state.test.mjs": 1,
  "test/jsx-elements.test.mjs": 1,
  "test/outside-shell-chrome.test.mjs": 11,
}

/** Lines and files, split the way §LXII.1 split them: product source, and everything else. */
function totals(found) {
  const out = { src: { lines: 0, files: 0 }, rest: { lines: 0, files: 0 } }
  for (const [path, lines] of Object.entries(found)) {
    const side = path.startsWith("src/") ? out.src : out.rest
    side.lines += lines
    side.files++
  }
  return out
}

test("SH957: the two-chrome vocabulary is a named debt that may only shrink", () => {
  const { found, byProbe } = census(ROOT, SELF)
  const { src, rest } = totals(found)

  // Printed on every run, both halves, because a count somebody has to open a file to learn is the
  // count that drifts — and the probe split says which kind of reference the next task is removing.
  console.log(
    `SH957: two-chrome vocabulary — src ${src.lines} line(s) in ${src.files} file(s), `
      + `${AREAS.filter((a) => a !== "src").join("/")} ${rest.lines} line(s) in ${rest.files} file(s) `
      + `(${Object.entries(byProbe).map(([k, v]) => `${k} ${v}`).join(", ")})`,
  )

  assert.deepEqual(found, STILL_TWO_CHROMES,
    "SH957: the two-chrome census moved.\n"
      + "A file in `+ actual` with a count higher than declared gained a reference to a retired era: "
      + "Block AW is removing that vocabulary, so write the new code for the one chrome that remains.\n"
      + "A file in `- expected` with a higher count than it has now, or absent from actual, lost "
      + "references: lower or delete its line in STILL_TWO_CHROMES in this same commit, so the "
      + "commit says how much it removed.\n"
      + "The probes are in test/support/chrome-vocabulary.mjs.")
})

test("SH957: the census is not vacuous — a planted provider in a clean file is named", () => {
  // A temporary copy of a real file this census finds clean, not a synthetic string: the reader has
  // to read a file off disk, walk into an area and classify its lines, and a planted string checks
  // only the last of those.
  const clean = "src/components/ui/button.tsx"
  const source = readFileSync(join(ROOT, clean), "utf8")
  assert.equal(census(ROOT, SELF).found[clean], undefined,
    `SH957: ${clean} was chosen as a file with no two-chrome vocabulary, and the census finds some`)

  const scratch = mkdtempSync(join(tmpdir(), "sh957-"))
  try {
    mkdirSync(join(scratch, "src", "components", "ui"), { recursive: true })
    writeFileSync(join(scratch, clean), source)
    assert.deepEqual(census(scratch).found, {},
      "SH957: the unmodified copy must read as clean, or the plant below proves nothing")

    writeFileSync(join(scratch, clean), `${source}\n<SectionCardChromeProvider chrome="bento" />\n`)
    assert.deepEqual(census(scratch).found, { [clean]: 1 },
      "SH957: a SectionCardChromeProvider written into a clean file is not named, so this census "
        + "would report a finished sweep over a tree that still has one")
  } finally {
    rmSync(scratch, { recursive: true, force: true })
  }
})

test("SH957: the comment probe tells an era apart from the word", () => {
  const scratch = mkdtempSync(join(tmpdir(), "sh957-"))
  try {
    mkdirSync(join(scratch, "src"), { recursive: true })
    const file = "src/probe.tsx"
    const lines = [
      "/**",
      " * The panel carries the chrome; the button carries the behaviour.",
      " * It renders in console chrome when no provider is mounted.",
      " */",
      "const x = chrome === \"bento\"",
      "// whatever chrome sits above it",
    ]
    writeFileSync(join(scratch, file), lines.join("\n"))
    const { found, byProbe } = census(scratch)

    // Two of the six: the era sentence and the comparison. The panel line and the viewport line use
    // the word in a sense a one-chrome console keeps, and counting them is how the hand figure came
    // out at 108 for a vocabulary this reader measures at 89.
    assert.deepEqual(found, { [file]: 2 })
    assert.equal(byProbe.comment, 1, "only the comment that names an era is vocabulary")
    assert.equal(byProbe.comparison, 1, "and a comparison against a chrome value is code, found in code")
  } finally {
    rmSync(scratch, { recursive: true, force: true })
  }
})
