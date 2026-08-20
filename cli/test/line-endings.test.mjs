/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH863 — the working tree agrees with what `.gitattributes` declares.
 *
 * ## The failure, and why nothing caught it for so long
 *
 * `core.autocrlf` is a **per-machine** setting, and this repository declared nothing — so the answer
 * differed per contributor and the disagreement lived in the *working tree*, where no gate looks. On
 * a machine with `input`, git stores LF and converts nothing on checkout, so a file a Windows editor
 * created kept CRLF on disk for ever against an LF index. 308 of 1864 tracked files were in that
 * state, `cli/src/convert.mjs` and `cli/src/diff.mjs` among them.
 *
 * Nothing was broken at runtime, which is the whole reason it survived: what it cost was the
 * **review**. A one-character change to `convert.mjs` reported `4172 ++++----`, with the real line
 * somewhere inside it, and `git blame` credited every line to whoever last saved the file. The
 * warning git printed on each such commit had been printing long enough to be furniture.
 *
 * ## Why this asserts the tree and not the file
 *
 * A rule in `.gitattributes` is a fact about *checkout*, not about what is on disk now: declaring it
 * changed nothing already there, and the 308 stayed until the tree was re-materialised once. So an
 * assertion that only read `.gitattributes` would have passed on the exact state the task was filed
 * about. What is checked here is the bytes, against what git says the bytes should be — which is
 * also what makes it survive somebody editing the declaration, since both sides come from it.
 */
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

// @scans neither reason: the subject is BYTES at the end of every line, and the files are read raw
// for `control-characters.test.mjs`' reason — there is no sentence about a carriage return that
// contains one, only the byte or not (SH740).

const ROOT = new URL("../../", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");

function git(args, input) {
  return execFileSync("git", args, {
    cwd: ROOT, encoding: "utf8", maxBuffer: 64 * 1024 * 1024, input,
  });
}

/**
 * Every tracked path git declares an `eol` for, as `path -> "lf" | "crlf"`.
 *
 * <p>One `check-attr` over the whole list rather than one per file: the per-file form is a process
 * launch each, which is the shape that made the first draft of `block-sweep.test.mjs` run past two
 * minutes. `-z` because a path may contain a space, and `--stdin` because a command line may not hold
 * eighteen hundred of them.</p>
 *
 * <p><b>`eol` alone is the wrong question, which this found by failing.</b> `* text=auto eol=lf`
 * declares `eol=lf` on <i>everything</i>, and `check-attr` reports what was declared without applying
 * `text=auto`'s own binary detection — so three PNGs came back *declared lf, holds CRLF*, on a byte
 * pair that is a coincidence inside an image. What `binary` does is unset **`text`**, so that is the
 * attribute that says whether git will convert the file at all, and a path it is unset on is in
 * neither this map nor this reader's business.</p>
 */
function declaredEol() {
  const tracked = git(["ls-files", "-z"]).split("\0").filter(Boolean);
  const answered = git(["check-attr", "-z", "--stdin", "text", "eol"], tracked.join("\0"))
    .split("\0");
  // `-z` emits a flat run of path, attribute, value — two per path here, in no promised order.
  const attrs = new Map();
  for (let at = 0; at + 2 < answered.length; at += 3) {
    const [path, name, value] = [answered[at], answered[at + 1], answered[at + 2]];
    const own = attrs.get(path) ?? {};
    own[name] = value;
    attrs.set(path, own);
  }
  const eol = new Map();
  for (const [path, own] of attrs) {
    if (own.text === "unset") continue;
    if (own.eol === "lf" || own.eol === "crlf") eol.set(path, own.eol);
  }
  return eol;
}

/** Whether a file's bytes hold a CRLF, and whether they hold a bare LF. */
function endingsOf(path) {
  const bytes = readFileSync(path);
  let crlf = false;
  let lf = false;
  for (let at = 0; at < bytes.length; at += 1) {
    if (bytes[at] !== 0x0a) continue;
    if (at > 0 && bytes[at - 1] === 0x0d) crlf = true;
    else lf = true;
  }
  return { crlf, lf };
}

test("SH863: every tracked file's line endings are the ones `.gitattributes` declares", () => {
  const eol = declaredEol();
  assert.ok(eol.size > 500,
    `only ${eol.size} tracked path(s) declare an eol — a reader that found almost nothing would pass `
      + "over exactly the state this task was filed about, which is a tree nobody had declared");

  const wrong = [];
  for (const [path, want] of eol) {
    const { crlf, lf } = endingsOf(join(ROOT, path));
    // Asked in one direction each. A file declared `lf` may not hold a CRLF; one declared `crlf` may
    // not hold a bare LF. Mixed endings fail both ways round, which is right — a file half converted
    // is the state a half-finished renormalise leaves and the one hardest to see.
    if (want === "lf" && crlf) wrong.push(`${path} — declared lf, holds CRLF`);
    if (want === "crlf" && lf) wrong.push(`${path} — declared crlf, holds a bare LF`);
  }

  console.log(`SH863: ${eol.size} tracked file(s) checked against their declared eol`);
  assert.deepEqual(wrong, [],
    "the declaration and the bytes disagree, which is invisible at runtime and costs the review: a "
      + "one-character change to a file in this state diffs as the whole file, and `git blame` "
      + "credits every line to whoever last saved it.\n"
      + "A rule in `.gitattributes` applies at CHECKOUT, so adding one changes nothing already on "
      + "disk. The tree has to be re-materialised once, and it discards anything uncommitted:\n"
      + "  git status --porcelain     # must be empty\n"
      + "  git rm --cached -r . && git reset --hard\n"
      + "Then re-run both gates — this rewrites the bytes of every file it touches (SH863).");
});

test("SH863: the batch files keep their carriage returns, and the images are asked nothing", () => {
  const eol = declaredEol();
  const batch = [...eol].filter(([path]) => /\.(cmd|bat)$/i.test(path));

  assert.ok(batch.length >= 5, `only ${batch.length} batch file(s) found`);
  assert.deepEqual(batch.filter(([, want]) => want !== "crlf"), [],
    "`cmd.exe` is entitled to care about the carriage return — a `goto` label or a line continuation "
      + "can behave differently without it — and `run-commit.cmd` is how this project commits, so "
      + "this is not the place to find out");
  assert.deepEqual([...eol.keys()].filter((path) => /\.(png|jpg|woff2?|jar|zip)$/i.test(path)), [],
    "and a binary type declares no eol at all, so this reader holds it to nothing: a `0D 0A` pair "
      + "inside an image is a coincidence, and three PNGs in this tree contain one");
});
