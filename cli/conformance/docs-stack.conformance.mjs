/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH628 — **a version number a page states about this stack is derived from a build file, or the
 * build fails.**
 *
 * ## Why this is a third instrument and not a line in `docs-vocabulary`
 *
 * SH499 built two: a *vocabulary* check (does this verb / route / tool / key still exist) and an
 * *execution* check (does the documented request still work). A stack claim is neither. Nothing
 * about `Elasticsearch 9.3.3` is a name this product declares — it is a **number about a
 * dependency**, and both halves can be wrong independently: the version can drift, or the
 * dependency can leave entirely and the row stay behind. `docs-vocabulary`'s reader would resolve
 * every name on that page and report green, which is exactly what it did for the two years the
 * developer guide told a contributor this product depends on a search engine it does not have.
 *
 * ## What it checks
 *
 * Two questions, both answered from files in *this* repository rather than from a list kept here:
 *
 * 1. **Every stated version matches.** For each claim in {@link CLAIMS}, every occurrence in the
 *    docs is compared with the value read out of `pom.xml` / `package.json`. A page may omit a
 *    version; it may not state a wrong one.
 * 2. **No retired dependency is presented as part of the stack.** {@link RETIRED} names things
 *    this product genuinely does not depend on, each with the sentence that says what is true
 *    instead — because "remove the row" is not actionable and "the search story is Turing over
 *    HTTP, optional and off by default" is.
 *
 * The docs live in `shio-site/docs` (SH1262; a different repository before that), and this file
 * keeps `docs-vocabulary`'s bargain: absent pages are a failed run naming the fix, never a skip.
 */
import assert from "node:assert/strict";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, test } from "node:test";

import { DOCS_DIR } from "../../scripts/docs-location.mjs";

// @scans prose reason: it holds a published Markdown table against build files, both read as
// text; the versions inside them are the subject (SH740).

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = join(HERE, "..", "..");

const DOCS = DOCS_DIR;

const read = (path) => readFileSync(path, "utf8");

/** A `<name>value</name>` from the root POM's properties — the one place a version is declared. */
function pomProperty(name) {
  const pom = read(join(REPO, "pom.xml"));
  const found = new RegExp(`<${name}>([^<]+)</${name}>`).exec(pom);
  assert.ok(found, `SH628: <${name}> is no longer declared in pom.xml, so this check reads nothing`);
  return found[1].trim();
}

/** The same, from a module POM — `maven.compiler.*` lives on `shio-app`, not the aggregator. */
function modulePomProperty(module, name) {
  const pom = read(join(REPO, module, "pom.xml"));
  const found = new RegExp(`<${name}>([^<]+)</${name}>`).exec(pom);
  assert.ok(found, `SH628: <${name}> is no longer declared in ${module}/pom.xml`);
  return found[1].trim();
}

const rootPackage = JSON.parse(read(join(REPO, "package.json")));

/** The leading integer of a range like `>=26` or `11.8.0` — a doc states a floor, not a range. */
const major = (spec) => /(\d+)/.exec(spec)[1];

/**
 * What the docs are allowed to claim, and where the truth is read from.
 *
 * <p>`pattern` must capture the version in group 1 and is matched globally: the same claim can
 * appear in a table, a prose sentence and a prerequisite list, and all three go stale together.</p>
 */
const CLAIMS = [
  {
    label: "Spring Boot",
    pattern: /Spring Boot\s+(\d+(?:\.\d+)+)/g,
    actual: () => pomProperty("spring-boot.version"),
    where: "pom.xml <spring-boot.version>",
  },
  {
    label: "Java",
    // `(?!\.\d)` and not `(?![\d.])`: a sentence ends `Java 17.` and that period is punctuation,
    // where `Java 17.0.2` is a different claim. Rejecting both was this check's own first bug.
    pattern: /Java\s+(\d+)(?!\.?\d)/g,
    actual: () => modulePomProperty("shio-app", "maven.compiler.target"),
    where: "shio-app/pom.xml <maven.compiler.target>",
  },
  {
    label: "Node",
    pattern: /Node(?:\.js)?\s+(\d+)/g,
    actual: () => major(rootPackage.engines.node),
    where: "package.json engines.node",
  },
  {
    label: "pnpm",
    pattern: /pnpm\s+(\d+)(?:\.\d+)*\+?/g,
    actual: () => major(rootPackage.packageManager.split("@")[1]),
    where: "package.json packageManager",
  },
];

/**
 * Named as part of the stack, and not part of it.
 *
 * <p>Each entry carries what is true instead. A check that only says "do not write this" produces
 * a page with a hole where a reader had a question.</p>
 */
const RETIRED = [
  {
    name: "Elasticsearch",
    instead:
      "Shio has no search engine dependency. Indexing is Viglet Turing over HTTP through "
      + "turing-java-sdk, optional and off by default (shio.turing.enabled=false).",
  },
  {
    name: "GraalJS",
    instead: "There is no server-side script engine; templates are Handlebars.java.",
  },
  {
    name: "AngularJS",
    instead: "The console is React 19 + Vite. The AngularJS console is gone.",
  },
];

/**
 * The licence families this check can tell apart — the one this repository carries, and the ones
 * a page must therefore not attribute to it.
 *
 * <p>`inFile` is the phrase the corresponding `LICENSE` text carries, which is what makes the SPDX
 * id in `package.json` checkable rather than merely present. `named` is how a *page* writes it,
 * which is prose rather than SPDX: nobody puts `Apache-2.0` in a pill.</p>
 *
 * <p>`Apache` requires the `2` — `architecture-overview` names **Apache Tika** as a dependency, and
 * a reader that could not tell a licence from a vendor would report the page that is right.</p>
 */
const LICENCES = [
  {
    spdx: "Apache-2.0",
    inFile: "Apache License",
    named: /Apache(?:\s+License)?[\s-]v?2(?:\.0)?/g,
  },
  { spdx: "GPL-3.0", inFile: "GNU GENERAL PUBLIC LICENSE", named: /\b[AL]?GPL[\s-]?v?3(?:\.0)?/gi },
  { spdx: "MIT", inFile: "MIT License", named: /\bMIT[\s-]Licen[cs]e\b/gi },
];

/** Every published page, so a claim cannot escape by moving to a neighbouring file. */
function pages() {
  if (!existsSync(DOCS)) {
    throw new Error(
      `SH628: the published documentation is not at ${DOCS}.\n`
        + "This suite compares the versions those pages state against this repository's own build\n"
        + "files, so without them it proves nothing — and a stale stack table is precisely the\n"
        + "defect that survives by nobody looking. They are shio-site/docs/src/content/docs in\n"
        + "this tree (SH1262), or SHIO_DOCS_DIR.",
    );
  }
  const out = [];
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const path = join(dir, entry.name);
      if (entry.isDirectory()) walk(path);
      else if (/\.mdx?$/.test(entry.name)) {
        out.push({ label: path.slice(DOCS.length + 1), text: read(path) });
      }
    }
  };
  walk(DOCS);
  // The repo's own README is the same kind of claim in a different repository, and SH499 found
  // this exact class of defect there first (13 MCP tools claimed, 11 registered). It is the page
  // most people read before any of the others, so a stack line going stale in it is the widest
  // reach a stale claim has.
  out.push({ label: "README.md", text: read(join(REPO, "README.md")) });
  return out;
}

const PAGES = pages();

/**
 * `COVERAGE.md` is the audit that *records* stale pages, so it has to be able to write the name of
 * the defect it is tracking. Excluding it is not a suppression: it is not a page a reader is sent
 * to, it is the ledger this whole directory answers to.
 */
const CLAIMABLE = PAGES.filter((page) => page.label !== "COVERAGE.md");

/**
 * A *claim* is the name in a stack table cell, or the name carrying a version — not any mention.
 *
 * <p>The distinction is load-bearing rather than a convenience: `architecture-overview` says
 * **"Elasticsearch is not a dependency"**, which is the sentence this check exists to make true.
 * A check that forbade the word would delete the one page that already answers the question
 * correctly, and would teach the next author that the fix is silence.</p>
 */
function isClaim(page, match) {
  if (match[1]) return true;
  const lineStart = page.text.lastIndexOf("\n", match.index) + 1;
  return page.text[lineStart] === "|";
}

/**
 * `file:line` of an offset, so a failure names the line a person has to open.
 *
 * <p>This read `page.file` until SH669, and `pages()` has only ever produced `label` — so every
 * message here died on `Cannot read properties of undefined` instead of naming the line it
 * promises. It survived because it is only reached on a **failure**, and the suite has been green:
 * an instrument's error path is the half nothing exercises, which is why the test below asks this
 * one to fail rather than trusting four green claims. `label` is already relative to `DOCS`, so
 * the second `slice` was wrong in the same expression.</p>
 */
const at = (page, index) => `${page.label}:${page.text.slice(0, index).split("\n").length}`;

describe("SH628 conformance: a version the docs state is a version this repo declares", () => {
  for (const claim of CLAIMS) {
    test(`every stated ${claim.label} version matches ${claim.where}`, () => {
      const expected = claim.actual();
      const wrong = [];
      for (const page of PAGES) {
        for (const found of page.text.matchAll(claim.pattern)) {
          // A doc may state a floor (`Maven 3.9+`) or the exact version; both must be a prefix of
          // or equal to what is declared, which is the same test for `21` against `21` and for
          // `4.1.0` against `4.1.0` while still catching `4.0.4`.
          if (expected === found[1] || expected.startsWith(`${found[1]}.`)) continue;
          wrong.push(`  ${claim.label} ${found[1]} at ${at(page, found.index)} — declared: ${expected}`);
        }
      }
      assert.deepEqual(
        wrong,
        [],
        `SH628: a page states a ${claim.label} version this repository does not declare. A reader `
          + "sets up an environment against it and hits a failure the page caused. The declared "
          + `value is read from ${claim.where}.\n${wrong.join("\n")}`,
      );
    });
  }

  test("no page presents a retired dependency as part of the stack", () => {
    const found = [];
    for (const retired of RETIRED) {
      for (const page of CLAIMABLE) {
        const hit = new RegExp(`${retired.name}(\\s+\\d+(?:\\.\\d+)*)?`, "g");
        for (const match of page.text.matchAll(hit)) {
          if (!isClaim(page, match)) continue;
          found.push(`  ${retired.name} at ${at(page, match.index)}\n    true instead: ${retired.instead}`);
        }
      }
    }
    assert.deepEqual(
      found,
      [],
      "SH628: a page names a technology this product does not depend on. This is the worse half of "
        + "a stale stack table — a wrong version misconfigures an environment, a wrong dependency "
        + `sends a contributor away believing this is a different product.\n${found.join("\n")}`,
    );
  });

  /**
   * SH669 — the licence is a claim about this repository, and it is the one that was wrong.
   *
   * <p>Found by Block N's completion sweep: SH541 moved 195 files from GPL-3.0 to Apache-2.0 —
   * `LICENSE`, the POM, ten `package.json`s, the plugin manifest, 182 source headers — and the
   * published landing page kept a `GPL v3` pill for the whole time after. A version claim
   * misconfigures an environment; **a licence claim is the one a reader may not act on wrongly at
   * all**, and it is the single most-read fact on that page.</p>
   *
   * <p>Read the same way the version claims are: from a build file, cross-checked against
   * `LICENSE` so no one file can move alone — which is exactly the drift SH541 closed, where the
   * only file carrying the right answer was the one nobody fetches.</p>
   */
  test("no page attributes a licence to this product that this repository does not carry", () => {
    const declared = rootPackage.license;
    const family = LICENCES.find((each) => each.spdx === declared);
    assert.ok(
      family,
      `SH669: package.json declares "${declared}", which this check has no family for. Add it to `
        + "LICENCES with the phrase its LICENSE file carries, or the reader below silently stops "
        + "knowing what is true.",
    );
    assert.ok(
      read(join(REPO, "LICENSE")).includes(family.inFile),
      `SH669: package.json says ${declared} and LICENSE does not contain "${family.inFile}", so `
        + "the two files this check reads disagree and neither can be believed. This is SH541's "
        + "defect exactly, in the pair that is supposed to catch it.",
    );

    const found = [];
    for (const other of LICENCES.filter((each) => each !== family)) {
      for (const page of CLAIMABLE) {
        for (const match of page.text.matchAll(other.named)) {
          found.push(`  ${match[0]} at ${at(page, match.index)}`);
        }
      }
    }
    assert.deepEqual(
      found,
      [],
      `SH669: a page names a licence this product does not carry. package.json and LICENSE both `
        + `say ${declared}, so a reader is being told the terms wrongly on the one subject where `
        + "that decides whether they may use it at all. If the mention is about a *dependency* "
        + `rather than about Shio, say whose it is — this reader is deliberately blunt because `
        + `there were no such mentions when it landed.\n${found.join("\n")}`,
    );

    // Non-vacuity, and the direction that matters: a reader matching nothing would report the
    // same empty list on a page that says GPL in every paragraph.
    const states = CLAIMABLE.filter((page) => family.named.test(page.text)).map((p) => p.label);
    family.named.lastIndex = 0;
    assert.ok(
      states.length > 0,
      `SH669: no page states ${declared} at all, so this check just proved nothing about `
        + `${CLAIMABLE.length} page(s). The README's License section is the usual carrier.`,
    );
  });

  /**
   * The instrument is asked to fail, because four green claim tests are exactly what a reader that
   * matches nothing would also produce — and a regex that stopped matching is the failure mode a
   * pattern-driven check has and a name-driven one does not.
   */
  test("SH628: the check itself fails on a version this repo does not declare", () => {
    const springBoot = pomProperty("spring-boot.version");
    assert.notEqual(springBoot, "4.0.4", "the version the developer guide carried must not be current");
    const probe = "Shio runs on Spring Boot 4.0.4 with Java 17.";
    const versions = [...probe.matchAll(CLAIMS[0].pattern)].map((each) => each[1]);
    assert.deepEqual(versions, ["4.0.4"], "the Spring Boot pattern must still find a stated version");
    const javas = [...probe.matchAll(CLAIMS[1].pattern)].map((each) => each[1]);
    assert.deepEqual(javas, ["17"], "the Java pattern must still find a stated version");
    assert.notEqual(
      modulePomProperty("shio-app", "maven.compiler.target"),
      "17",
      "a Java version nobody builds with must not read as correct",
    );
  });
});
