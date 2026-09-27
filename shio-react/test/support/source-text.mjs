/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * Strip comments before scanning source, because a reader that counts its own explanation makes
 * the explanation unwritable.
 *
 * This has now caught three readers in this directory, each time the same way and each time
 * after the assertion was written:
 *
 * - `console-english.test.mjs` (SH823) — a JSDoc `<h2>Why the install dialog is a dialog</h2>`
 *   matched its hardcoded-English pattern; three of the first fifteen findings were comments.
 * - `accent-token.test.mjs` (SH890) — `index.css` reported four orange utilities, which were the
 *   four the SH890 comment *quotes* while explaining what it had just replaced.
 * - `bento-shell.test.mjs` (SH888) — two assertions that a file must **not** mention
 *   `SidebarProvider` or `@/contexts/user.context` failed against the paragraphs explaining why
 *   it does not.
 *
 * The pattern is specific to this repository and not incidental: readers here are expected to
 * carry their reasoning in prose beside the code, so any scan for "this file must not say X" is
 * a scan over a file that is very likely to *discuss* X. Three copies of a three-line strip is
 * how the fourth one gets written slightly differently and stops matching.
 *
 * `cli/test/support/source-text.mjs` exports the same idea and is deliberately not imported:
 * SH823 refused to reach across a workspace package boundary from one package's tests into
 * another's test support, and that reasoning has not changed. This is the console's copy, and
 * the duplication is between packages rather than within one.
 *
 * <h2>It used to be a regex over the whole file, and that deleted code (SH1118)</h2>
 *
 * <p>The old body was `source.replace(/\/\*[\s\S]*?\*\//g, "")`: an opener **anywhere** — string
 * literals included — swallowing everything up to the next closer, hundreds of lines later or to
 * the end of the file. Everything between was deleted from the scanned text, and every assertion
 * over that region then matched an empty string and <b>passed</b>.</p>
 *
 * <p>Measured across this repository's JS sources before the fix: <b>19 spans, 77,285
 * characters</b> removed by a strip that began in the middle of a line. The worst was
 * `cli/test/clone.test.mjs`, where a `robots.txt` fixture containing `Disallow: /*` hid
 * <b>656 lines</b>; `cli/test/theme.test.mjs` lost 508 to a CSS selector. In the console's own
 * tree `src/app/routes.const.ts` lost its route table to `` `${consolePath}/*` ``, and SH1049 had
 * already measured one Playwright glob cutting `console-check.mjs`'s stripped view almost in half
 * and turning four assertions green over nothing. Thirty-one suites across two packages read
 * source through this function, and any one of them could be silenced by a string in a file it
 * does not own.</p>
 *
 * <p><b>The fix is to read correctly, not to refuse.</b> §LXIV.19 proposed a fraction ceiling —
 * throw when the strip removes too much — and the measurement refutes it: files here are
 * legitimately up to <b>91%</b> comment (`src/app/routes/index.ts`, `src/theme.ts`,
 * `src/index.css`), so any ceiling low enough to catch the defect rejects the honest files. What
 * separates the two is not how much was removed but <b>where the removal began</b>, and that is
 * decidable per line.</p>
 *
 * <h2>The rule, which is SH630's rule</h2>
 *
 * <p>A block comment in this repository opens at the <b>start of a line</b> — `/*`, `/**`, or
 * `{/*` in JSX. A `/*` anywhere else is either a trailing comment or a string, and the two are
 * told apart by blanking the line's quoted spans first, so a literal can never open a comment.
 * Per line and holding no cross-line state it can avoid, which is what SH630 measured: a proper
 * scanner met a regex literal with a quote inside it, read that as a string opening, and
 * misclassified every line after it — a plausible number, quietly wrong.</p>
 *
 * <p>Same-line only for the quoted spans, for that reason. A template literal spanning lines is
 * not blanked past its first line, so a `/*` at the start of a line <i>inside</i> one would still
 * read as an opener. That shape does not occur here and the honest place to say so is this
 * sentence rather than a tokenizer.</p>
 *
 * <h2>And it says when it could not read the file</h2>
 *
 * <p>One case remains genuinely undecidable: an opener at a line start that is never closed. The
 * file ends inside a comment, and every assertion over the tail would match an empty string. That
 * throws, naming SH1118 and the line, because <b>a reader that cannot see the file should say so
 * rather than agree with everything</b>.</p>
 */

/**
 * Quoted spans, same-line only — see the note above on why this is not a tokenizer.
 *
 * <p>Escapes are consumed so a `\"` inside a string does not end the span early, which is what
 * would let the character after it open a comment.</p>
 */
const QUOTED = /'(?:[^'\\\n]|\\.)*'|"(?:[^"\\\n]|\\.)*"|`(?:[^`\\\n]|\\.)*`/g

/** A line whose whole content is comment: `//` or a JSDoc continuation. */
function isProse(text) {
  return text.startsWith("//") || text.startsWith("*")
}

/** A block comment opening where one can legitimately open: the start of a line, JSX included. */
function opensALine(text) {
  return text.startsWith("/*") || text.startsWith("{/*")
}

/**
 * Source with comments removed, and an exception where they cannot be told from code.
 *
 * @param source the file's text
 * @param label  what to name in the failure — a path, usually. Optional, because most callers
 *               scan one file and the throw is then unambiguous without it.
 * @returns the source with every comment removed and every line of code intact
 * @throws Error naming SH1118 where a block comment opens and never closes
 */
export function stripComments(source, label = "this source") {
  const out = []
  let openedAt = 0
  source.split("\n").forEach((line, index) => {
    if (openedAt) {
      const close = line.indexOf("*/")
      if (close < 0) return
      openedAt = 0
      out.push(line.slice(close + 2))
      return
    }
    const text = line.trim()
    if (isProse(text)) return
    // Where the line already IS a comment, its quotes are prose and blanking them is a hazard
    // rather than a safeguard: a `*/` inside an apostrophe pair would vanish and the comment would
    // read as unclosed. Where the line is code, blanking is the whole point.
    let kept = line
    let blanked = opensALine(text)
      ? kept
      : kept.replace(QUOTED, (span) => " ".repeat(span.length))
    for (;;) {
      const at = blanked.indexOf("/*")
      if (at < 0) break
      const close = blanked.indexOf("*/", at + 2)
      if (close < 0) {
        openedAt = index + 1
        kept = kept.slice(0, at)
        break
      }
      kept = kept.slice(0, at) + kept.slice(close + 2)
      blanked = blanked.slice(0, at) + blanked.slice(close + 2)
    }
    out.push(kept)
  })
  if (openedAt) {
    throw new Error(
      `SH1118: a block comment opened at line ${openedAt} of ${label} and never closed, so `
      + "everything after it was deleted from the scanned text and every assertion over that "
      + "region would have matched an empty string and passed. That silence is the defect this "
      + "throw exists to replace — a reader that cannot see the file has to say so. Close the "
      + "comment, or if this is a string that merely looks like one, quote it so the line's "
      + "opener is inside a quoted span.",
    )
  }
  return out.join("\n")
}
