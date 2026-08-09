/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH356 — the rationale file holds no section whose task has already shipped.
 *
 * ## What SH356 measured, and where it stands
 *
 * SH341 took `ROADMAP.md`'s 42 over-long lines to zero and `roadkeep lint` fell from 249 findings
 * to 107. SH356 asked where the rest went and found it in one file: **45 sections over the
 * 250-word budget, 42 whose task had already shipped, 9 that no line pointed at, and 3 anchors
 * claimed twice**, beside four line-level corruptions. Measured again at this commit, by roadkeep's
 * own counters: **0 over budget, 0 stale, `lint` clean**. The corruption went with SH361's
 * renumbering of the duplicate `SH347`.
 *
 * So this file is not the repair. It is the **ratchet** — the thing that makes the zero mean
 * something tomorrow, which is the half SH356's own prose said was missing: *a section gate cannot
 * ratchet from 45 the way the line gate did, because dropping a section is destructive where
 * shortening a line is not.* At 45 that was true. At 0 it is not: there is nothing to destroy, and
 * the only question left is whether a **new** one can appear.
 *
 * ## Why stale and not the word budget
 *
 * Two of SH356's four counts already have an owner that cannot be bypassed. `add` and
 * `section add` **refuse** prose over budget at write time — this session watched one bounce at 180
 * characters against a limit of 178 — and `lint` reports an unresolved or duplicated anchor. A
 * second implementation of either here would be a reader that can disagree with the tool, which is
 * `agents.md`'s named anti-pattern and the thing SH583 is about.
 *
 * **Stale is different, and it is the one with no gate.** `ship` drops a section as the third of
 * its three edits, so the tool never leaves one behind — but that is a property of the *write path*
 * being taken, not of the file. All 42 survivors predate roadkeep owning these files, which is
 * precisely how they were made: by hand, one at a time, each one reasonable. The hook denies the
 * hand-edit now; this asserts the outcome the hook is trying to produce, because a guard on the
 * door is not the same statement as a fact about the room.
 *
 * And it is the costliest of the four to a reader. An over-long section is tiring. A stale one is
 * **misleading**: `IMPROVEMENTS.md` is defined as design rationale for *unshipped* work, so a
 * survivor reads as a live argument for something that has already been decided and built — and
 * nothing on the page says which it is.
 *
 * ## Not a second implementation of roadkeep
 *
 * The same bargain `cli/test/changelog-blocks.test.mjs` documents, for the same reason: roadkeep
 * resolves to a Python entry point that `pnpm test` cannot depend on. Shelling out would be the
 * stronger check and it is not available. The drift this can produce is a red run here against a
 * green `roadkeep lint`, which is visible and cheap to resolve — unlike the silence it replaces.
 */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { describe, test } from "node:test";

const ROOT = fileURLToPath(new URL("../../", import.meta.url));
const read = (name) => readFileSync(join(ROOT, "docs", name), "utf8");

const improvements = read("IMPROVEMENTS.md");
const roadmap = read("ROADMAP.md");
const changelog = read("CHANGELOG.md");

/** A ledger entry opens its line with the id; anything else in that file is prose about one. */
const shipped = new Set([...changelog.matchAll(/^- \*\*(SH\d+)/gm)].map(([, id]) => id));
/** A roadmap task line bolds its id. A block heading and a non-goal do not. */
const open = new Set([...roadmap.matchAll(/\*\*(SH\d+)\*\*/g)].map(([, id]) => id));
/** `→ §XVI.66` — the pointer that makes a section reachable. */
const pointed = new Set([...roadmap.matchAll(/→ §([\w.]+)/g)].map(([, ref]) => ref));

/**
 * Every anchored section, with the ids its heading names.
 *
 * <p>A block-level heading (`## VIII. The Agent Gateway (Block H)`) names no id and is not a
 * candidate: it introduces the block rather than one task, and it outlives every task under it.
 * Only a heading that names ids can be stale, because staleness is a claim about *those* ids.</p>
 */
function sections() {
  return [...improvements.matchAll(/^#{2,4}\s+([\w.]+)\s+(.*)$/gm)].map(([, anchor, title]) => ({
    anchor,
    title,
    ids: [...new Set([...title.matchAll(/(SH\d+)/g)].map(([, id]) => id))],
  }));
}

/**
 * The sections that outlived their tasks.
 *
 * <p>Pure, and taking its three sets as arguments, so the test below can hand it a file it knows
 * the answer for. A classifier that has only ever been run on a clean repository is green for two
 * indistinguishable reasons.</p>
 */
function staleIn(all, { open: live, shipped: done, pointed: reachable }) {
  return all.filter((section) => {
    if (!section.ids.length) return false;
    // Reachable from a live task line, or naming one — either way it is somebody's argument
    // still. An epic whose umbrella section outlives one of its parts is the case this spares.
    if (reachable.has(section.anchor) || section.ids.some((id) => live.has(id))) return false;
    return section.ids.every((id) => done.has(id));
  });
}

describe("SH356: IMPROVEMENTS.md is rationale for unshipped work, and stays that way", () => {
  test("no section survives its task shipping", () => {
    const stale = staleIn(sections(), { open, shipped, pointed });

    assert.deepEqual(
      stale.map((each) => `§${each.anchor} — ${each.title} (shipped: ${each.ids.join(", ")})`),
      [],
      "SH356: a rationale section outlived the task it argues for. `ship` drops the section as its "
        + "third edit, so this is a section that reached the file some other way — and a reader "
        + "meeting it cannot tell a live design from a decision that was made and built two months "
        + "ago. Drop it (`roadkeep section drop <anchor>`); its history is in `git log`.",
    );
  });

  test("every section a task points at exists, so a pointer is never a dead end", () => {
    const anchors = new Set(sections().map((each) => each.anchor));
    const dangling = [...pointed].filter((ref) => !anchors.has(ref));
    assert.deepEqual(
      dangling,
      [],
      "SH356: a roadmap line points at a section that is not there. roadkeep reports this as "
        + "`ref.unresolved` and it is the honest state *between* `add` and `section add` — but at "
        + "rest it means the one place a task's reasoning lives is gone.",
    );
  });

  /**
   * SH361 — one id per step, and therefore never one id on two steps.
   *
   * <p>Part of what SH356 counted was not editorial at all: a duplicate `SH347`, two entries
   * carrying one id. SH361 resolved that class by deciding an id names a *step* rather than an
   * entry that gets rewritten, and renumbered the six that had collided. Nothing has held the
   * outcome since, and it is the same shape as the three duplicated anchors SH356 found one file
   * over — an id that names two things makes every `deps:` on it ambiguous, and `ship` files the
   * ledger entry under whichever it matched first.</p>
   *
   * <p>Deliberately within a file and not across the two: one id is currently both open and
   * shipped, which is the partial-ship state `⏳` exists to express, so a cross-file rule would be
   * red on a legitimate case. Its id is not written here on purpose —
   * `cli/test/assertion-debt.test.mjs` walks these suites for ids and cannot tell an assertion from
   * a mention, so naming a task this file does not assert would pay off its debt by accident. That
   * is a real limitation of that reader and this is the cheap way to avoid feeding it.</p>
   */
  test("SH361: no id names two entries in one file", () => {
    const twice = (text, pattern) => {
      const seen = new Map();
      for (const [, id] of text.matchAll(pattern)) seen.set(id, (seen.get(id) ?? 0) + 1);
      return [...seen].filter(([, count]) => count > 1).map(([id, count]) => `${id} ×${count}`);
    };
    assert.deepEqual(
      twice(roadmap, /^- .*?\*\*(SH\d+)\*\*/gm),
      [],
      "SH361: two roadmap lines carry one id, so `deps:` on it names both and neither.",
    );
    assert.deepEqual(
      twice(changelog, /^- \*\*(SH\d+)\*\*/gm),
      [],
      "SH361: two ledger entries carry one id. This is the defect SH361 renumbered the colliding "
        + "six to clear, and the decision behind it is that an id names a step, not an entry "
        + "somebody later rewrites.",
    );
  });

  test("SH356: the classifier fires on a stale section, and spares the three that are not", () => {
    const fixture = [
      { anchor: "Z.1", title: "An argument for work that is done (SH1)", ids: ["SH1"] },
      { anchor: "Z.2", title: "Still open (SH2)", ids: ["SH2"] },
      { anchor: "Z.3", title: "An epic, part shipped (SH1, SH2)", ids: ["SH1", "SH2"] },
      { anchor: "Z.4", title: "Shipped, but a live line still points here (SH1)", ids: ["SH1"] },
      { anchor: "Z.5", title: "A block heading, naming nobody", ids: [] },
    ];
    const flagged = staleIn(fixture, {
      open: new Set(["SH2"]),
      shipped: new Set(["SH1"]),
      pointed: new Set(["Z.4"]),
    }).map((each) => each.anchor);

    assert.deepEqual(
      flagged,
      ["Z.1"],
      "SH356: only Z.1 is stale. Z.2 is open, Z.3 is an epic with a part still open, Z.4 is "
        + "reachable from a live line, and Z.5 introduces a block rather than a task. Each of the "
        + "four is a way this check could have become a nuisance that somebody deletes — which is "
        + "the failure mode of a ratchet, not a bug in it.",
    );
  });

  test("SH356: the counts are printed, and the reader is not vacuous", () => {
    const all = sections();
    const withIds = all.filter((each) => each.ids.length);
    process.stdout.write(
      `\nSH356 IMPROVEMENTS.md: ${all.length} section(s), ${withIds.length} naming a task, `
        + `${pointed.size} pointer(s) from the roadmap — 0 stale\n`,
    );
    // SH356 counted 45 over-long, 42 stale, 9 orphaned and 3 duplicated anchors in this file. A
    // reader that parsed none of it would agree with all of that and with anything else.
    assert.ok(all.length > 50, `only ${all.length} section(s) parsed — the heading shape changed`);
    assert.ok(withIds.length > 20, `only ${withIds.length} section(s) name a task`);
    assert.ok(shipped.size > 100, `only ${shipped.size} shipped id(s) read from the ledger`);
    assert.ok(open.size > 20, `only ${open.size} open id(s) read from the roadmap`);
  });
});
