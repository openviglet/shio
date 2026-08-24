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
 * Not a parser. Block comments inside string literals would be stripped too — which is fine for
 * every reader here, and worth knowing before pointing it at something else.
 */
export function stripComments(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .split("\n")
    .filter((line) => {
      const text = line.trim()
      return !text.startsWith("//") && !text.startsWith("*")
    })
    .join("\n")
}
