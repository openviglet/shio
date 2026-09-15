/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH161 — the site memory as a file, and the merge that decides what happened to it.
 *
 * The interesting assertions here are all about **absence**, because that is the only place
 * this can lose somebody's work. A key present on one side and missing on the other is either
 * an addition or a deletion, and nothing but the base can say which — the same lesson SH149
 * learned for folder markers, applied to a thing that is even less recoverable, since a note
 * is one sentence somebody wrote and there is no version history behind it.
 */
import assert from "node:assert/strict";
import { describe, it } from "node:test";

import {
  NOTES_FILE,
  notesPathFor,
  notesSiteOf,
  parseNotes,
  reconcileNotes,
  serializeInstanceNotes,
  serializeNotes,
} from "../src/content-notes.mjs";

describe("content-notes: the file format", () => {
  it("round-trips a site's notes", () => {
    const notes = [
      { key: "blog.post-type", note: "This site's blog uses Article, not Post.", author: "alex" },
      { key: "tone", note: 'Headlines are sentence case, never "Title Case".', author: null },
    ];
    const text = serializeNotes({ site: "Blog", notes });
    const parsed = parseNotes("Blog/.memory.yaml", text);

    assert.equal(parsed.site, "Blog");
    assert.deepEqual(
      parsed.notes.map((entry) => [entry.key, entry.note]),
      [
        ["blog.post-type", "This site's blog uses Article, not Post."],
        ["tone", 'Headlines are sentence case, never "Title Case".'],
      ],
    );
    assert.equal(parsed.notes[0].author, "alex");
  });

  it("is byte-stable whatever order the notes arrive in", () => {
    const a = serializeNotes({ site: "Blog", notes: [{ key: "b", note: "B" }, { key: "a", note: "A" }] });
    const b = serializeNotes({ site: "Blog", notes: [{ key: "a", note: "A" }, { key: "b", note: "B" }] });
    assert.equal(a, b);
  });

  it("omits `updated` — it is bookkeeping, and it would rewrite the file on every pull", () => {
    const text = serializeNotes({
      site: "Blog",
      notes: [{ key: "a", note: "A", author: "alex", updated: "2026-07-27T00:00:00Z" }],
    });
    assert.ok(!text.includes("updated"));
    assert.ok(text.includes("author: alex"));
  });

  it("an entry with no note is a named error, not a silently dropped line", () => {
    assert.throws(
      () => parseNotes("Blog/.memory.yaml", "site: Blog\nnotes:\n  - key: orphan\n"),
      /missing `key:` or `note:`/,
    );
  });

  it("a note with a trailing space is not a permanent conflict (SH254)", () => {
    // `scalar()` claimed it used "the same rule `content-files` uses" and was missing that
    // rule's `value !== value.trim()` clause, while `parseNotes` strips trailing whitespace off
    // every line. So the file could not hold the value — and the symptom was not cosmetic.
    const note = "keep it terse ";
    const remote = [{ key: "tone", note, author: "ana" }];
    const local = parseNotes(
      "Blog/.memory.yaml",
      serializeNotes({ site: "Blog", notes: remote }),
    ).notes;

    assert.equal(local[0].note, note);
    // The operator-facing half: with an empty base — a fresh clone, the case a projection exists
    // to serve — both sides looked moved and the values differed, so every pull reported a
    // conflict, wrote nothing, exited 1, and advised two fixes that cannot settle a value the
    // format drops on the way in.
    const plan = reconcileNotes({ base: {}, remote, local });
    assert.deepEqual(plan.conflicts, []);
    assert.equal(plan.agreed.tone, note);
  });

  it("addresses a site's notes inside its own content directory", () => {
    assert.equal(notesPathFor("Blog"), `Blog/${NOTES_FILE}`);
    assert.equal(notesSiteOf("Blog/.memory.yaml"), "Blog");
    // Not a notes file: a post, and a marker one level down.
    assert.equal(notesSiteOf("Blog/hello.md"), null);
    // SH1004 — the open-notes projection is NOT site memory, and the reader that decides which
    // files a push parses has to say so. Both live at a site's root with a leading dot, which is
    // exactly the pair a prefix match would confuse; the memory reader keys on the whole name.
    assert.equal(notesSiteOf("Blog/.notes.yaml"), null,
      "SH1004: `.notes.yaml` is projected and never read back — a reader that claimed it would "
        + "make the push parse a file the server owns");
    assert.equal(notesSiteOf("Blog/news/.memory.yaml"), null);
  });
});

describe("content-notes: the three-way merge", () => {
  const base = { tone: "Sentence case." };

  it("a note only the server changed is taken", () => {
    const plan = reconcileNotes({
      base,
      remote: [{ key: "tone", note: "Sentence case, always." }],
      local: [{ key: "tone", note: "Sentence case." }],
    });
    assert.deepEqual(plan.write.map((entry) => entry.note), ["Sentence case, always."]);
    assert.deepEqual(plan.conflicts, []);
  });

  it("a note only the file changed is kept, and nothing is written back to disk", () => {
    const plan = reconcileNotes({
      base,
      remote: [{ key: "tone", note: "Sentence case." }],
      local: [{ key: "tone", note: "Sentence case, always." }],
    });
    assert.deepEqual(plan.write, []);
    assert.equal(plan.agreed.tone, "Sentence case, always.");
  });

  it("both sides changed differently is a conflict, and nothing is chosen", () => {
    const plan = reconcileNotes({
      base,
      remote: [{ key: "tone", note: "Server's wording." }],
      local: [{ key: "tone", note: "Local wording." }],
    });
    assert.deepEqual(plan.write, []);
    assert.equal(plan.conflicts.length, 1);
    assert.equal(plan.conflicts[0].key, "tone");
    assert.ok(!Object.hasOwn(plan.agreed, "tone"));
  });

  it("both sides made the same edit is agreement, not a conflict", () => {
    const plan = reconcileNotes({
      base,
      remote: [{ key: "tone", note: "Same conclusion." }],
      local: [{ key: "tone", note: "Same conclusion." }],
    });
    assert.deepEqual(plan.conflicts, []);
    assert.equal(plan.agreed.tone, "Same conclusion.");
  });

  /**
   * The case the base exists for. Without it, "on the server, not in the file" reads as an
   * addition and the note is written back the moment somebody deletes it — a deletion that
   * silently undoes itself is worse than one that fails.
   */
  it("a note dropped from the file after both sides agreed is a deletion", () => {
    const plan = reconcileNotes({
      base,
      remote: [{ key: "tone", note: "Sentence case." }],
      local: [],
    });
    assert.deepEqual(plan.forgetRemote, ["tone"]);
    assert.deepEqual(plan.write, []);
  });

  it("a note the server has and no base knows about is an addition, never a deletion", () => {
    const plan = reconcileNotes({
      base: {},
      remote: [{ key: "tone", note: "Sentence case." }],
      local: [],
    });
    assert.deepEqual(plan.forgetRemote, []);
    assert.deepEqual(plan.write.map((entry) => entry.key), ["tone"]);
  });

  it("a note the server deleted is removed from the file", () => {
    const plan = reconcileNotes({ base, remote: [], local: [{ key: "tone", note: "Sentence case." }] });
    assert.deepEqual(plan.deleteLocal, ["tone"]);
  });

  it("an unchanged note is neither written nor forgotten, and stays agreed", () => {
    const plan = reconcileNotes({
      base,
      remote: [{ key: "tone", note: "Sentence case." }],
      local: [{ key: "tone", note: "Sentence case." }],
    });
    assert.deepEqual(plan.write, []);
    assert.deepEqual(plan.deleteLocal, []);
    assert.deepEqual(plan.forgetRemote, []);
    assert.equal(plan.agreed.tone, "Sentence case.");
  });
});

describe("SH412: absence is a conclusion both sides can reach", () => {
  it("treats both sides forgetting a note as agreement, not a permanent conflict", () => {
  // SH160 makes absence the representation of a forgotten note, so both sides forgetting one is
  // the same conclusion reached twice — the rule the both-moved branch already states. Its test
  // required both sides to *hold* the note, which excluded exactly that case.
  const plan = reconcileNotes({
    base: { tone: "formal", stale: "old advice" },
    remote: [{ key: "tone", note: "formal" }],
    local: [{ key: "tone", note: "formal" }],
  });

  assert.deepEqual(plan.conflicts, []);
  // Dropped from the base, which is what makes the resolution stick: a conflicted key never
  // reaches `agreed`, so the base kept it and every later run recomputed the same conflict.
  assert.deepEqual(Object.keys(plan.agreed), ["tone"]);
  assert.deepEqual(plan.write, []);
  assert.deepEqual(plan.deleteLocal, []);
    assert.deepEqual(plan.forgetRemote, []);
  });

  it("still reports a genuine disagreement as a conflict", () => {
  // The property that must survive: one side forgetting while the other rewrites is a real
  // disagreement, and nothing about SH412 may quietly resolve it.
  const plan = reconcileNotes({
    base: { tone: "formal" },
    remote: [{ key: "tone", note: "playful" }],
    local: [],
  });

    assert.deepEqual(plan.conflicts.map((entry) => entry.key), ["tone"]);
    assert.deepEqual(plan.agreed, {});
  });
});

describe("content-notes: the instance-wide file (SH231)", () => {
  it("its header is the safety argument, not decoration", () => {
    const text = serializeInstanceNotes({
      notes: [
        { key: "publishing", note: "Publishing happens on Fridays." },
        { key: "authoring", note: "Copy is authored in files here.", author: "ana" },
      ],
    });

    // A reader who does not know a push leaves this alone would adopt another installation's
    // house rules by accident, which is the outcome SH161 refused. So the file says so.
    assert.match(text, /NOT applied by `shio push --content`/);
    assert.match(text, /--adopt-memory/);
    // No `site:` line: an instance-wide note filed under a site is not what it is.
    assert.doesNotMatch(text, /^site:/m);
    // Sorted by key, like the site file, so a committed diff shows an edited note (P5).
    assert.ok(text.indexOf("authoring") < text.indexOf("publishing"));
    // And it round-trips through the reader the push uses.
    const parsed = parseNotes("shio/.memory.yaml", text);
    assert.equal(parsed.site, null);
    assert.deepEqual(parsed.notes.map((entry) => entry.key), ["authoring", "publishing"]);
    assert.equal(parsed.notes[0].author, "ana");
  });
});
