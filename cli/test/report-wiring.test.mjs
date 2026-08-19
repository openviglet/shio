/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH849 — a report section computed, tested, and passed to nobody.
 *
 * ## The defect, and the reason a test suite cannot see it
 *
 * `buildConversion` returns `palette` — which of the theme's colours took a captured value, which
 * were refused and which kept a default. `describeConversion` formats it into the most useful lines
 * a conversion prints about appearance. `convert.test.mjs` asserts those lines, at length, across
 * four tasks. And `shio convert --site VigR6 --create-site` wrote 65 objects, a `Theme`, a
 * `Theme (Shio)` and a `PageLayout` in **38 lines of output**, none of which names a colour, a role
 * or a token: the command destructures the builder's result by hand, rebuilds an object literal for
 * the formatter, and `palette` is not in it. The formatter's parameter defaults to `null` and the
 * whole section is skipped, silently, on every real run.
 *
 * Every existing test calls `describeConversion(conversion)` with the builder's own object, so all
 * of them pass through the one join the command does not make. That is why four tasks' worth of
 * assertions could not see it, and why the fix for SH849 alone would leave the next one invisible:
 * this is the **third** time. SH684 wrote the comment `a field the builder returns that nobody
 * names here reaches no reader at all` at that exact call site, about `kept` and `declined`; SH763's
 * `markup` was dropped there too and its SH772 payload warning has never printed either.
 *
 * ## The instrument, not the two edits (SH338)
 *
 * So the rule is stated once and read off the source: **every parameter a report formatter
 * destructures must be passed at every call site that formatter has.** A default is what makes the
 * omission silent, so a default is not an excuse — it is the mechanism. The check needs no registry
 * and no list to keep in step: it finds the formatters by their shape (`export function describe*`
 * taking a destructured object) and the call sites by their name, so a formatter added tomorrow is
 * held the day it is written.
 */
import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

import { masked } from "./support/source-text.mjs";

const SRC = fileURLToPath(new URL("../src/", import.meta.url));

/** Every `.mjs` under `cli/src`, with its path relative to that directory. */
async function modules(dir = SRC, prefix = "") {
  const found = [];
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const at = join(dir, entry.name);
    const name = prefix ? `${prefix}/${entry.name}` : entry.name;
    if (entry.isDirectory()) found.push(...await modules(at, name));
    else if (entry.name.endsWith(".mjs")) {
      // @scans code — the javadoc above quotes a call site and names the parameters it drops, and a
      // reader that took its own prose for source would find a formatter this file does not define.
      found.push({ name, source: masked(await readFile(at, "utf8")) });
    }
  }
  return found;
}

/**
 * The span from `open` to its matching close, brace-counted over already-masked source.
 *
 * <p>Stateless the way {@link masked}'s own doc argues for: strings and comments are blanked before
 * this runs, so a brace inside either is already a space and cannot move the match.</p>
 */
function balanced(source, open) {
  let depth = 0;
  for (let at = open; at < source.length; at += 1) {
    if (source[at] === "{") depth += 1;
    else if (source[at] === "}") {
      depth -= 1;
      if (depth === 0) return source.slice(open, at + 1);
    }
  }
  return null;
}

/**
 * The keys an object literal states at its own level — a nested object's keys are not its own.
 *
 * <p><b>`=` is in the terminator class, and leaving it out was this reader's own first bug.</b> A
 * destructured parameter carrying a default reads `markup = []`, so a pattern ending at `:`, `,` or
 * `}` finds only the parameters that have none — which is precisely the set that cannot fail
 * silently. The scan came back clean over three real omissions. Caught by the test below rather
 * than by review, which is the argument for a reader having a subject it names.</p>
 */
function keysOf(literal) {
  const keys = new Set();
  let depth = 0;
  const KEY = /([A-Za-z_$][\w$]*)\s*[:,}=]/y;
  for (let at = 0; at < literal.length; at += 1) {
    const char = literal[at];
    if (char === "{" || char === "[" || char === "(") depth += 1;
    else if (char === "}" || char === "]" || char === ")") depth -= 1;
    // A key starts where the previous meaningful character opened the literal or ended an entry.
    else if (depth === 1 && /[A-Za-z_$]/.test(char) && /[{,]\s*$/.test(literal.slice(0, at))) {
      KEY.lastIndex = at;
      const match = KEY.exec(literal);
      if (match) keys.add(match[1]);
    }
  }
  return keys;
}

/**
 * Every `export function describe*({ … })` in the tree, with the parameter names it destructures.
 *
 * <p>Found by shape rather than by a list, which is the whole point: a formatter written next month
 * is held without anybody remembering to register it. `describe` is the naming this codebase
 * already uses for the one job — turning a builder's result into the lines a run prints.</p>
 */
function formatters(files) {
  const found = new Map();
  for (const file of files) {
    const declaration = /export function (describe[A-Za-z]*)\s*\(\s*\{/g;
    for (const match of file.source.matchAll(declaration)) {
      const open = match.index + match[0].length - 1;
      const literal = balanced(file.source, open);
      if (!literal) continue;
      found.set(match[1], { file: file.name, params: keysOf(literal) });
    }
  }
  return found;
}

/**
 * Every call to one of those formatters, with the keys of the object it hands over.
 *
 * <p><b>A hoisted literal is followed, and that is not a convenience.</b> Two call sites passing the
 * same fourteen fields inline is how they drifted in the first place — one word apart, the same
 * length, and no reason for a reader adding a field to suspect there was a second place to add it.
 * The right repair is one `const reported = {…}` above both, so a scan that only understood an
 * inline literal would go quiet on the very edit that fixes the defect it found.</p>
 *
 * <p><b>What is deliberately not checked is a call that forwards somebody else's object</b> —
 * `describe(spec)`, `describeDefaults(defaults)`, `describeMigration(upgrade)`. There is no
 * enumeration there to be incomplete: whatever the builder put in the object is what the formatter
 * receives, which is the shape that cannot have this defect and the one `convert.test.mjs` was
 * unknowingly testing. The subject is a caller that **assembles** the argument field by field, and
 * only a literal does that. Skipping the rest is not silence about a call site; it is the reader
 * saying the question does not arise.</p>
 */
function callSites(files, names) {
  const sites = [];
  for (const file of files) {
    for (const name of names) {
      const call = new RegExp(`\\b${name}\\s*\\(\\s*(\\{|[A-Za-z_$][\\w$]*)`, "g");
      for (const match of file.source.matchAll(call)) {
        const at = match.index + match[0].length - match[1].length;
        const literal = match[1] === "{"
          ? balanced(file.source, at)
          : declaredObject(file.source, match[1]);
        if (literal) sites.push({ file: file.name, formatter: name, keys: keysOf(literal) });
      }
    }
  }
  return sites;
}

/** The literal a `const <name> = { … }` in this file assigns, so a hoisted argument is followed. */
function declaredObject(source, name) {
  const declaration = new RegExp(`\\bconst\\s+${name}\\s*=\\s*\\{`).exec(source);
  return declaration ? balanced(source, declaration.index + declaration[0].length - 1) : null;
}

/** The object literal `export function <name>` returns, so a builder's own shape can be read. */
function returnedObject(source, name) {
  const declaration = new RegExp(`\\bexport function ${name}\\s*\\(`).exec(source);
  if (!declaration) return null;
  const at = source.indexOf("return {", declaration.index);
  return at < 0 ? null : balanced(source, source.indexOf("{", at));
}

/** The `<name>: { … }` literal in this source, with the span it occupies. */
function nestedLiteral(source, name) {
  const declaration = new RegExp(`\\b${name}:\\s*\\{`).exec(source);
  if (!declaration) return null;
  const open = declaration.index + declaration[0].length - 1;
  const literal = balanced(source, open);
  return literal ? { literal, at: open, end: open + literal.length } : null;
}

test("SH849: every field a report formatter reads is passed at every call site it has", async () => {
  const files = await modules();
  const found = formatters(files);
  const sites = callSites(files, [...found.keys()]);

  // Non-vacuity, and the honest kind: `cli/src` only grows, so an absolute floor over it can only
  // fall if this reader broke (floor-kind.test.mjs's rule). Both halves, because a scan that found
  // the formatters and no call sites would report a clean tree for the one reason that proves
  // nothing — which is the exact failure shape this file exists to catch, one level up.
  assert.ok(found.size >= 3, `formatters found: ${[...found.keys()].join(" ")}`);
  assert.ok(sites.length >= 3, `call sites found: ${sites.length}`);

  const dropped = [];
  for (const site of sites) {
    for (const param of found.get(site.formatter).params) {
      if (!site.keys.has(param)) {
        dropped.push(`${site.file} calls ${site.formatter} without \`${param}\``);
      }
    }
  }

  assert.deepEqual(dropped.sort(), [],
    "A parameter the formatter destructures and the caller never passes takes its default, and the "
      + "section that reads it is skipped in silence. Three times now: SH684's `kept`/`declined`, "
      + "SH763's `markup` and its SH772 payload warning, and the palette SH831/SH846 built and "
      + "SH849 found printing nowhere. Pass it, or stop destructuring it");
});

test("SH849: the reader finds the formatter this was filed on, and the call site that dropped two", async () => {
  // The scan's own subject, named once, so a refactor that renames or reshapes either half fails
  // here saying so rather than quietly reporting a clean tree over nothing. Both halves: a reader
  // that found the parameters and no longer found the caller would pass on every future omission,
  // and hoisting the literal to a `const` is exactly the edit that could have caused it.
  const found = formatters(await modules());
  const conversion = found.get("describeConversion");

  assert.ok(conversion, `describeConversion not found among ${[...found.keys()].join(" ")}`);
  assert.equal(conversion.file, "convert.mjs");
  for (const param of ["document", "sections", "markup", "pages", "extras", "palette"]) {
    assert.ok(conversion.params.has(param), `${param} is not among ${[...conversion.params].join(" ")}`);
  }

  const site = (await modules().then((files) => callSites(files, ["describeConversion"])))
    .find((each) => each.file === "commands/convert.mjs");
  assert.ok(site, "the command's call site is not being read, so nothing about it is being checked");
  assert.ok(site.keys.has("palette") && site.keys.has("markup"),
    `the two fields SH849 found dropped: ${[...site.keys].sort().join(" ")}`);
});

/**
 * SH854 — the same defect one level inside the parameter, where SH849's rule cannot reach.
 *
 * <p>SH852 added `unheld` — the pair the contrast floor judged, failed and was **not allowed to
 * fix**, which is the worst of the three states it can report — to what `vocabularyTokensFrom`
 * returns and to the `palette` literal `--json` carries, and no line to `describeConversion`. The
 * parameter was passed, so the rule above was satisfied and stayed satisfied: what was missing is a
 * loop *inside* it, and no rule about destructured parameters can see one.</p>
 *
 * <p>So the subject here is the palette's own fields, from both ends — the keys the builder returns
 * and the keys the document forwards — and the question is whether each is read somewhere **other
 * than the forwarding literal itself**. Forwarding a field into `--json` is not reading it: that is
 * precisely the state `unheld` was in, and the state a reader of the printed report cannot tell from
 * a field nobody computed. `tokens` passes because it becomes the `Theme` post; every other field
 * passes only by being formatted into a line.</p>
 */
test("SH854: every field of the palette report is read somewhere other than the literal that forwards it", async () => {
  const files = await modules();
  const convert = files.find((file) => file.name === "convert.mjs").source;
  const vocabulary = files.find((file) => file.name === "vocabulary-theme.mjs").source;

  const returned = returnedObject(vocabulary, "vocabularyTokensFrom");
  const forwarded = nestedLiteral(convert, "palette");
  assert.ok(returned, "vocabularyTokensFrom's return is not being read, so nothing here is checked");
  assert.ok(forwarded, "the palette literal convert.mjs builds is not being read");

  const subject = [...new Set([...keysOf(returned), ...keysOf(forwarded.literal)])].sort();
  // Non-vacuity: the palette has had at least six fields since SH849, and this file only grows.
  assert.ok(subject.length >= 6, `palette fields found: ${subject.join(" ")}`);

  const elsewhere = convert.slice(0, forwarded.at) + convert.slice(forwarded.end);
  const unread = subject.filter((key) => !new RegExp(`palette\\s*\\??\\.\\s*${key}\\b`).test(elsewhere));

  assert.deepEqual(unread, [],
    "A palette field the document carries and the report never prints is computed, tested and passed "
      + "to nobody — SH849's defect one level inside the parameter it fixed, which is where SH852 "
      + "put `unheld`. Print it in describeConversion, or stop reporting it");
});
