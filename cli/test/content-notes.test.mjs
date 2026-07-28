/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
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
