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
 * <p><b>The list reaching zero is Block AW's acceptance criterion, and it reached it in SH963.</b>
 * The design system dropped the switch (VDS146, 2026.3.11), the root provider went, and the debt
 * below is empty and asserted to stay empty.</p>
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const ROOT = join(HERE, "..")

/** This reader's own two files, which describe the probes and so match them by construction. */
const SELF = ["test/chrome-vocabulary-census.test.mjs", "test/support/chrome-vocabulary.mjs"]

/**
 * SH963 — the files that name the retired switch in order to refuse it, each with why.
 *
 * <p>Not debt. Each is a guard that fails if the vocabulary comes back, and a guard against a name
 * has to spell the name. Listed rather than silently excluded, so a fourth file has to argue its way
 * in here the way a debt line had to.</p>
 */
const REFUSES_THE_SWITCH = {
  "test/chrome-provider-root.test.mjs":
    "fails if any console source names the provider or its hook again",
  "test/outside-shell-chrome.test.mjs":
    "fails if any component calls the hook again (SH959's walk)",
  "test/chrome-adapter.test.mjs":
    "fails if the package exports a chrome switch again, and says which SH892 rules it retired",
}
const EXCLUDED = [...SELF, ...Object.keys(REFUSES_THE_SWITCH)]

/**
 * Every file still naming the two-chrome vocabulary, with its line count. **The debt.**
 *
 * <p>Lower a number or delete a line in the commit that removes the references; add one only in a
 * diff that says why a new reference to a retired era is the right call.</p>
 */
/*
 * SH963 — empty. The last entries were the switch itself and the tests of its two branches; the
 * package removed the switch, the root provider went with it, and the guards that refuse its return
 * are listed in REFUSES_THE_SWITCH above rather than counted here.
 */
const STILL_TWO_CHROMES = {}

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
  const { found, byProbe } = census(ROOT, EXCLUDED)
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

/**
 * Files whose only census hits are prose, and why each is not narration. **None, since SH963.**
 *
 * <p>SH962 removed the comments that explained code by the migration that produced it, and left two
 * that named `chrome-adapter.test.mjs` by file name while it tested the package's two branches.
 * SH963 rewrote both when the branches went. A file here now is narration of a retired look.</p>
 */
const NAMES_A_SWITCH_TEST = []

test("SH962: prose about two chromes survives only beside the switch it describes", () => {
  const { probesByFile } = census(ROOT, EXCLUDED)
  const proseOnly = Object.entries(probesByFile)
    .filter(([, probes]) => probes.every((name) => name === "comment"))
    .map(([path]) => path)
    .sort()
  assert.deepEqual(proseOnly, NAMES_A_SWITCH_TEST,
    "SH962: a file's only two-chrome hits are comments, so it narrates a console look that no longer"
      + " exists. Say what the code does now, and leave the migration to git log. If it is a file"
      + " name that happens to match, add it to NAMES_A_SWITCH_TEST with the reason.")
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
