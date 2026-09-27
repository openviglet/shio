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

/**
 * Source with comment lines and quoted spans blanked out, **same length as the input**.
 *
 * <p>SH786 — the third stripper, and the one a reader needs when it is looking for a <i>position</i>
 * rather than for text. {@link code} deletes whole lines, so an offset into its result means nothing
 * in the original; a scan that must slice the file at what it found cannot use it.</p>
 *
 * <p>The measured need: `known-debt.test.mjs` finds where a declaration ends by matching its
 * bracket, and the prose inside those declarations is full of brackets. `AT_THE_WALL`'s reasons
 * carry **eleven** pairs of parentheses inside Java strings. They balance, so the scan lands on the
 * right character — by luck, eleven times, and one `(` in a new reason moves it.</p>
 *
 * <p>Per line and stateless, which is SH630's rule and the reason this is not a tokenizer: a regex
 * literal containing a quote made a proper scanner misread every line below it. So an escaped quote
 * inside a string ends the span early here, and that is the conservative direction — a *shorter*
 * blanked span can only leave a real bracket visible, never hide one.</p>
 *
 * <h2>It blanks a trailing comment where {@link code} deliberately keeps one</h2>
 *
 * <p>That is not a disagreement about what a comment is, which SH740 exists to prevent. It is the
 * two functions having opposite conservative directions, because they are asked different
 * questions. {@link code} looks for a <b>claim</b>, so keeping a trailing comment errs towards
 * crediting a payment rather than inventing a debt. This one looks for <b>structure</b>, and a
 * bracket in a trailing comment is not structure — counting it truncates the span and under-reports
 * whatever the caller was measuring. Blanking the strings first is what makes it safe: a `//` inside
 * a string is already gone by the time this looks for one.</p>
 *
 * <p>Measured at 0 occurrences across the four declarations that use this today. It is here because
 * it is what the function <i>means</i>, not because something is currently broken by its absence.</p>
 */
export function masked(body) {
  const blank = (span) => " ".repeat(span.length);
  return body
    .split("\n")
    .map((line) => {
      if (isProse(line)) return blank(line);
      const noStrings = line.replace(/"[^"\n]*"/g, blank);
      const comment = noStrings.indexOf("//");
      return comment < 0 ? noStrings : noStrings.slice(0, comment) + blank(noStrings.slice(comment));
    })
    .join("\n");
}

/**
 * Source with whole-line comments removed **and** a trailing `//` comment cut, strings kept.
 *
 * <p>SH1147 — the reading six scans needed and each wrote as
 * `.replace(/\/\*[\s\S]*?\*\//g, "").replace(/(^|[^:])\/\/.*$/gm, "$1")`. The first half opens on a
 * slash and two stars inside a string literal and deletes to the next closer, hundreds of lines on
 * (SH1118 measured 656 behind one `robots.txt` fixture). Neither existing reading fits them: {@link
 * code} keeps a trailing comment, which a scan asserting an absence can trip on, and {@link masked}
 * blanks strings, which is where a scan for flag names finds its subject.</p>
 *
 * <p>Per line and stateless, SH630's rule: a quote opened on one line never swallows the next. A
 * trailing `//` is cut only outside a string and only after whitespace, so `http://` and a regex
 * literal's `\/\/` stay; a block comment that starts mid-line stays too. Both are the conservative
 * direction — text a scan may read, never text it silently loses.</p>
 */
export function uncommented(body) {
  return body
    .split("\n")
    .filter((line) => !isProse(line))
    .map(withoutTrailingComment)
    .join("\n");
}

function withoutTrailingComment(line) {
  let quote = null;
  for (let at = 0; at < line.length; at += 1) {
    const char = line[at];
    if (quote) {
      if (char === "\\") at += 1;
      else if (char === quote) quote = null;
      continue;
    }
    if (char === "\"" || char === "'" || char === "`") {
      quote = char;
    } else if (char === "/" && line[at + 1] === "/" && (at === 0 || /\s/u.test(line[at - 1]))) {
      return line.slice(0, at).trimEnd();
    }
  }
  return line;
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
