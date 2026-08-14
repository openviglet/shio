/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH740 — one answer to "is this line a rule, or a sentence about the rule", for the JS suites.
 *
 * ## The failure this exists for
 *
 * A textual guard reads bytes, so **writing about a rule can fail the build for breaking it**.
 * SH613 established that for the Java suites and gave them `ShSourceText` plus an `@ShSourceScan`
 * declaration; twelve scans are held to it. The JavaScript half had nothing, and it has now been
 * bitten three times:
 *
 * - SH722's failure message ended `(SH322)` as a citation of the rule its lint follows.
 *   `assertion-debt.test.mjs` read that as a claim to have closed SH322, dropped it from the debt
 *   list, and the suite went red saying somebody had paid it off. That is what SH731 fixed.
 * - A corrected failure message naming the defect it fixed credited that id again, the same day.
 * - A checklist paragraph **quoting the two false claims it was correcting** failed the assertion
 *   written to catch exactly those claims.
 *
 * Each was caught in under a minute and each cost a diagnosis. The tax is small and constant, which
 * is precisely why it never got fixed on this side.
 *
 * ## Why this is not a port of `ShSourceText`
 *
 * These readers do not only scan code. They scan **Markdown** — a skill file, the published pages,
 * this repository's own governed docs — where prose is not the exception, it is the whole document.
 * "Strip the comments" has no meaning there. The question a Markdown scan is actually asking is
 * *which spans are somebody else's words*: a fenced block, an inline code span, a quotation. So
 * this module ships two strippers with different jobs, and a scan declares which one it means.
 *
 * ## Line-oriented, and holding no cross-line state it can avoid
 *
 * SH630 measured what a stateful stripper costs: `replication.conformance.mjs` contains a regex
 * literal with a double quote inside it, a proper scanner read that as a string opening, and every
 * line after it in the file was misclassified — a plausible number, quietly wrong. So {@link code}
 * decides per line and a regex literal cannot derail it. {@link unquoted} does carry fence state,
 * because a fenced block is a multi-line construct and there is no honest per-line answer; its
 * fence test is anchored to the line start, which is what a Markdown fence actually requires.
 */

/** A line that is nothing but comment. No state, so no regex literal can derail it. */
export function isProse(line) {
  const text = line.trim();
  return text.startsWith("//") || text.startsWith("*") || text.startsWith("/*");
}

/**
 * Source with whole-line comments removed — the half a scan means when it says it reads *code*.
 *
 * <p>Conservative on purpose, and in the direction SH630 chose: an id or a phrase in a trailing
 * comment <i>after</i> code on the same line still survives. A guard that errs this way keeps a
 * payment rather than inventing a debt, and a guard that errs the other way starts reporting
 * findings nobody can act on.</p>
 */
export function code(body) {
  return body.split("\n").filter((line) => !isProse(line)).join("\n");
}

/** A Markdown fence, at the start of a line — three backticks or three tildes. */
const FENCE = /^\s*(```|~~~)/;

/**
 * Markdown with every span that is *somebody else's words* removed.
 *
 * <p>Fenced blocks, inline code spans, and double-quoted phrases. That last one is the whole point
 * and it is worth being explicit about, because it looks aggressive: when a document is asked
 * <i>do you claim X</i>, a phrase the document has put in quotation marks is by definition a
 * quotation of something — an older version of itself, another file, a failure message — and
 * reading it as a claim is the SH613 defect with a different punctuation mark.</p>
 *
 * <p>Measured need: the corrected `shio-roadmap-docs` checklist has to quote the two false
 * sentences it replaced, or a reader cannot tell what changed. Without this, the assertion that the
 * checklist no longer makes those claims fails on the paragraph explaining that it no longer makes
 * them.</p>
 */
export function unquoted(markdown) {
  const out = [];
  let inFence = false;
  for (const line of markdown.split("\n")) {
    if (FENCE.test(line)) {
      inFence = !inFence;
      continue;
    }
    if (inFence) continue;
    out.push(
      line
        // Inline code first: a quotation inside backticks is already covered, and doing it the
        // other way round leaves stray backticks that read as an unclosed span on the next line.
        .replace(/`[^`]*`/g, " ")
        // Then quoted phrases, straight and curly. Non-greedy and same-line only — a quotation
        // spanning a line break is prose either way, and matching across lines would let one
        // stray quote mark swallow a page.
        .replace(/"[^"\n]*"/g, " ")
        .replace(/[“][^”\n]*[”]/g, " "),
    );
  }
  return out.join("\n");
}
