import assert from "node:assert/strict";
import { test } from "node:test";

import {
  describeDefaults, parseRecord, recordFor, withRecordDefaults,
} from "../src/blueprint-record.mjs";

/**
 * SH220 — a re-apply defaults to what the site was built with.
 *
 * SH155 records the package, its version and its parameters. Re-applying still meant re-supplying
 * every `--param` from memory or a transcript, and that is a correctness problem rather than a
 * tedium one: **a parameter the operator misremembers does not fail.** It rebuilds the site with a
 * different brand and `changed: n` reports it as ordinary work.
 *
 * The record's format is prose written by `ShBlueprintRecord.noteFor`, so these assertions are
 * written against the sentences that method produces — including the one it produces when the
 * sentence does not fit.
 */

const NOTE = "Built from blueprint 'blog' 1.0.0 — params: authorName=Ada; blogTitle=Notes";

test("SH220: a record parses back into the values that produced the site", () => {
  assert.deepEqual(parseRecord(NOTE), {
    blueprint: "blog",
    version: "1.0.0",
    params: { authorName: "Ada", blogTitle: "Notes" },
    truncated: 0,
  });
});

test("SH220: the version is a version, not its first component", () => {
  // The first implementation used one regex whose terminator alternation included `.`, so
  // `1.0.0` parsed as `1` — a semver read as a major. It looks right in every test that only
  // checks the name, which is why this one checks the thing that was wrong.
  assert.equal(parseRecord(NOTE).version, "1.0.0");
  assert.equal(parseRecord("Built from blueprint 'docs' 2.3.4.").version, "2.3.4",
    "and a parameterless record ends with a full stop that is punctuation, not part of it");
  assert.equal(parseRecord("Built from blueprint 'docs'.").version, null);
});

test("SH220: a value containing '=' survives, because plenty of them do", () => {
  // A CTA href with a tracking parameter is the ordinary case, not the exotic one.
  const record = parseRecord("Built from blueprint 'landing' — params: ctaHref=/signup?ref=a=b");
  assert.equal(record.params.ctaHref, "/signup?ref=a=b");
});

test("SH220: a truncated record says how much of itself is missing", () => {
  // The hazard the whole module turns on. `noteFor` is bounded at 400 characters and admits the
  // cut with `(+n more)`; without carrying that admission forward, a truncated record would
  // supply SOME parameters and silently omit others — the same defect this task is about,
  // arriving from the other side.
  const record = parseRecord("Built from blueprint 'landing' — params: brand=Acme (+3 more)");

  assert.equal(record.truncated, 3);
  assert.deepEqual(record.params, { brand: "Acme" },
    "the parameters it did name are still usable: the record did not LOSE them, it never held "
      + "the rest");
});

test("SH220: text that is not a record is not one", () => {
  assert.equal(parseRecord("The site was rebuilt on Tuesday"), null);
  assert.equal(parseRecord(""), null);
  assert.equal(parseRecord(null), null);
});

test("SH220: an explicit --param always wins over the record", () => {
  // The record is a default, not a policy. An operator re-applying with a new headline is doing
  // the thing the flag is for, and a record that overrode it would make the site unchangeable
  // through the command that changes it.
  const merged = withRecordDefaults({ blogTitle: "Renamed" }, parseRecord(NOTE));

  assert.deepEqual(merged.params, { blogTitle: "Renamed", authorName: "Ada" });
  assert.deepEqual(merged.defaulted, ["authorName"],
    "and only the value that actually came from the record is reported as defaulted");
});

test("SH220: what was defaulted is said, which is the load-bearing half", () => {
  // A parameter that was silently supplied is one nobody reviews — which would make this feature
  // a second way to rebuild a site wrong rather than a fix for the first.
  const merged = withRecordDefaults({}, parseRecord(NOTE));
  const text = describeDefaults(merged, "blog");

  assert.match(text, /authorName, blogTitle/);
  assert.match(text, /--param to override/,
    "and the message names the way out, so a reader who disagrees with a default knows the flag");
});

test("SH220: a truncated record is reported as a limit, not as a failure", () => {
  const text = describeDefaults(
    withRecordDefaults({}, parseRecord("Built from blueprint 'landing' — params: brand=A (+2 more)")),
    "landing",
  );

  assert.match(text, /does not name 2 of its parameter\(s\)/);
  assert.match(text, /fall back to the package's defaults/,
    "an operator reading this knows exactly how much of the answer is theirs to supply");
});

test("SH220: nothing is printed when nothing was defaulted", () => {
  // Silence is the answer on every first apply, and a line saying "0 parameters taken" on every
  // one of them is how a reader learns to skip the output that matters.
  assert.equal(describeDefaults(withRecordDefaults({ brand: "A" }, null), "landing"), "");
});

test("SH220: the record chosen is the one for the package being applied", () => {
  // A site can carry more than one — SH155 files a note per package — and re-applying `docs`
  // into a site that also has `blog` must not inherit `blog`'s parameters.
  const notes = [
    { note: NOTE },
    { note: "Built from blueprint 'docs' 2.0.0 — params: productName=Widget" },
  ];

  assert.equal(recordFor(notes, "docs").params.productName, "Widget");
  assert.equal(recordFor(notes, "docs").params.authorName, undefined);
  assert.equal(recordFor(notes, "marketing-site"), null);
});
