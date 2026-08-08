/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH208 — help text is prose, and prose is not JavaScript.
 *
 * ## The failure this removes
 *
 * Every `HELP` used to be a template literal. Help text about a CLI names commands, command names
 * in Markdown-ish prose attract backticks, and a backtick inside a template literal ends it — at
 * which point the file is a syntax error and `shio` does not load. Not the command that was
 * edited: **the whole binary**.
 *
 * It happened three times in three sessions — adding `verify --delivery`, adding `shio report`,
 * and documenting `movedFrom:` in the content section — and each time the only tests that noticed
 * were the three that spawn the CLI as a subprocess. Every other CLI test imports the command
 * modules directly, so a full green suite ran against a package whose entry point could not be
 * parsed. Three occurrences with one detection path is not bad luck; it is a file whose failure
 * mode is disproportionate to the edit.
 *
 * ## Why a file and not `String.raw`
 *
 * §IX.9 offered `String.raw` as the other candidate and it does not work: `String.raw` changes how
 * backslash escapes are read and has no effect whatever on the delimiter, so a backtick still ends
 * the literal and still breaks the parse. It would have looked like a fix and removed nothing.
 *
 * A `.txt` has no syntax to get wrong. It also stops the help from being reindented by whatever
 * formats the JavaScript, and makes the thing a contributor is most likely to edit the thing with
 * the fewest ways to be wrong.
 *
 * ## Cost
 *
 * One synchronous read per command, at module load, and commands are imported lazily — `shio
 * verify` reads `verify.txt` and nothing else. `ShHelpTextLintTest`'s CLI counterpart
 * (`help-source.test.mjs`) keeps the next one from being written back into JavaScript.
 */
import { readFileSync } from "node:fs";

/**
 * One command's help text, verbatim.
 *
 * @param {string} name the command, or `shio` for the global text. Matches the module name, so
 *   `commands/verify.mjs` reads `help/verify.txt` — a mapping nobody has to look up.
 * @returns {string} the file's contents, unmodified. Not trimmed here: the global text is written
 *   to stdout as-is and each command's is trimmed at the point it is printed, so trimming at the
 *   source would move a decision two callers already make.
 */
export function helpText(name) {
  return readFileSync(new URL(`./help/${name}.txt`, import.meta.url), "utf8");
}
