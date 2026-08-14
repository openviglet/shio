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

// @scans prose reason: IMPROVEMENTS.md and ROADMAP.md are Markdown, and a section heading is
// exactly the prose this counts (SH740).

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
 * Every anchor token the table of contents links to, in order.
 *
 * <p>Hoisted rather than parsed twice: this file's own rule is that two readers of one format are
 * two readers waiting to disagree. A row is identified by its **anchor token** — every row opens
 * with one — rather than by rebuilding GitHub's slug, which involves emoji and en-dashes.</p>
 */
function contents() {
  const start = improvements.indexOf("## Table of contents");
  const toc = improvements.slice(start, improvements.indexOf("\n## ", start + 5));
  return [...toc.matchAll(/^\s*-\s+\[((?:[IVXLC]+|0)(?:\.\d+)*)\.?[^\]]*\]\(#/gm)]
    .map(([, ref]) => ref);
}

/**
 * Every `§X.Y` the prose cites, in order, duplicates kept.
 *
 * <p>A citation is the section sign and a local anchor: `§XI.5`, `§0.4`, `§XV.0.5`, and `§XIV` for
 * a block family — which is a heading in this file like any other, so it is checked rather than
 * excused. Trailing punctuation is not part of an anchor (`§0.4.` ends a sentence), and a `§` with
 * nothing addressable after it is not a citation.</p>
 *
 * <p><b>Two things that look like citations and are not</b>, both found by running the first draft
 * of this reader over the real file rather than by reasoning about it. Code spans are stripped,
 * because `§X.Y` written between backticks is the *notation* — this file and `agents.md` both
 * discuss the form, and a reader that cannot tell a mention from a use reports the sentence
 * describing the defect as an instance of it. And the anchor shape is exact — a roman family or
 * the arabic `0` one, each optionally numbered — because *"Concept spec §10"* addresses a section
 * of `specs/SH74-agent-native-cms.md`, a document this reader has not read and must not judge.</p>
 */
function citations(text) {
  const prose = text.replace(/```[\s\S]*?```/g, "").replace(/`[^`\n]*`/g, "");
  return [...prose.matchAll(/§((?:[IVXLC]+|0)(?:\.\d+)*)\b/g)].map(([, ref]) => ref);
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

  /**
   * SH662 — an id shipped inside somebody else's entry is in the file and in nothing that reads it.
   *
   * <p>`docs/CHANGELOG.md` opened one entry `- **SH154** — … and **SH169** — …`: two tasks that
   * shipped together, written as one, which reads well and is invisible to every reader that keys
   * on an id. `roadkeep show SH169` answered *"never written or was retired"* about a task three
   * lines from the id it did recognise, the gate read the prose mention as a promise nothing
   * carries, and the assertion ledger counted it neither as shipped nor as debt.</p>
   *
   * <p><b>Bold is the signal, and the measurement is why.</b> Thirteen entries bold an id that is
   * not their own. Ten of those lead an entry of their own; the other three — SH230, SH231, SH232 —
   * are open roadmap lines, which is a forward reference (*"this opened SH232"*) and not a
   * co-headline. Nothing else in the ledger has the shape, so the rule is exact rather than a
   * heuristic with an exemption list. A plain `SH154` in running prose is a citation and is left
   * alone, which is the distinction the bold carries.</p>
   */
  test("SH662: an id bolded inside another entry leads one of its own, or is still open", () => {
    // One pass, two answers. An earlier version counted the co-ids with a second regex, and an
    // injection that widened only the first left the count untouched and the test green — a
    // non-vacuity check that does not read the code under test is decoration.
    const leads = new Set([...changelog.matchAll(/^- \*\*(SH\d+)/gm)].map(([, id]) => id));
    const coIds = [];
    for (const line of changelog.split(/\r?\n/)) {
      const own = /^- \*\*(SH\d+)/.exec(line)?.[1];
      if (!own) continue;
      for (const [, other] of line.matchAll(/\*\*(SH\d+)\*\*/g)) {
        if (other !== own) coIds.push({ own, other });
      }
    }

    assert.ok(
      coIds.length > 5,
      `only ${coIds.length} entr(y|ies) bold a second id — the reader found almost none, which `
        + "would agree with a ledger that had hidden every one of them",
    );

    const hidden = coIds
      .filter(({ other }) => !leads.has(other) && !open.has(other))
      .map(({ own, other }) => `${other} (inside ${own}'s entry)`);

    assert.deepEqual(
      hidden,
      [],
      "SH662: this id shipped inside another entry and leads none of its own, so `show` says it "
        + "was never written, the gate reads a prose mention as a broken promise, and neither "
        + "direction of the assertion ledger counts it. `record add --id <id>` writes the entry.",
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

  /**
   * SH649 — an id named in the rationale is carried by a line, recorded in the ledger, or declared
   * spoken for. Nothing else.
   *
   * <p>The fourth possibility is the defect: a number this project cites and never writes, which
   * `next-id` derives past and burns, and which a reader follows to nothing. Twelve of them had
   * accumulated in the positioning sections, all pointing at epics — `SH74`'s spec, `SH62`'s Media
   * Library, the retired GraalJS range in the non-goals — and every one was a live hazard rather
   * than a wording nit.</p>
   *
   * <p><b>Held here as well as in `roadkeep lint` on purpose.</b> That gate is not one of this
   * project's three: it runs when somebody types it. `pnpm test` runs on every push, so the check
   * that a reservation is *declared* rather than merely intended belongs where the build can see
   * it — and this file already reads all three documents, so it costs one more pass over text it
   * has in memory.</p>
   *
   * <p>The one exemption is deliberate and narrow: an id the ledger records inside <b>another
   * entry's</b> sentence is neither carried nor reserved, and declaring it reserved would be a lie
   * about work that shipped. That is its own finding, and while it is open this reader must not
   * force the lie — so a ledger mention counts, and the roadmap line for it is what keeps the case
   * visible.</p>
   */
  test("SH649: every id the rationale names is carried, recorded, or declared reserved", () => {
    const toml = readFileSync(join(ROOT, "roadkeep.toml"), "utf8");
    const reservedBlock = /reserved_ids\s*=\s*\[([^\]]*)\]/.exec(toml)?.[1] ?? "";
    const reserved = new Set([...reservedBlock.matchAll(/SH\d+/g)].map(([id]) => id));
    // Anywhere in the ledger, not only as an entry's leading id: a co-shipped task is recorded,
    // however badly, and this reader's job is not to re-litigate that.
    const mentioned = new Set([...changelog.matchAll(/SH\d+/g)].map(([id]) => id));

    const unaccounted = [...new Set([...improvements.matchAll(/\bSH\d+\b/g)].map(([id]) => id))]
      .filter((id) => !open.has(id) && !shipped.has(id) && !mentioned.has(id) && !reserved.has(id))
      .sort();

    assert.deepEqual(
      unaccounted,
      [],
      "SH649: the rationale names an id that no line carries, no ledger entry mentions and "
        + "`roadkeep.toml`'s `reserved_ids` does not declare. `next-id` derives past it, so the "
        + "number is burned and a reader following the pointer finds nothing. Add the id to "
        + "`reserved_ids` if the project spoke for it and will never write it as a line, or name "
        + "the id that actually carries the work.\n"
        + `  unaccounted: ${unaccounted.join(" ")}`,
    );

    assert.ok(
      reserved.size >= 9,
      `only ${reserved.size} reservation(s) read from roadkeep.toml — the declaration moved, and `
        + "this check passes vacuously the moment it cannot find it",
    );
  });

  /**
   * SH668 — a `§X.Y` written in the prose resolves to a heading, or it is a dead end.
   *
   * <p>The sibling test above asks the same question from the roadmap: does every `→ §X.Y` on a
   * task line find its section. This asks it from *inside* the rationale, which is where the
   * citations actually are — prose is how these files carry an argument across blocks, and
   * thirteen of seventeen distinct citations pointed at nothing when this was filed while
   * `roadkeep lint` called the file clean.</p>
   *
   * <p><b>The mechanism is `ship` doing its job.</b> Its first edit drops the section of the task
   * that shipped, which is right — `IMPROVEMENTS.md` is rationale for *unshipped* work. What it
   * cannot know is who was pointing at that section, so every ship is a chance to make a dead end,
   * and two of the thirteen were made and repaired inside one day with nothing failing either
   * time. `section.stale` is this question asked the other way and roadkeep already holds it; this
   * is the half nobody held.</p>
   *
   * <p><b>Three readers of these pointers now, and none of them overlaps.</b> `roadkeep lint` owns
   * `ref.unresolved` — does a task line's `→ §` resolve. `ShDesignRationalePointerLintTest` owns
   * the harder version of that same direction — does it resolve to the section that *names the
   * task*. Neither reads the prose, which is where the citations are. Adding a fourth would be two
   * readers of one format waiting to disagree, which is that lint's own stated rule.</p>
   *
   * <p><b>Only this file, and deliberately.</b> `CHANGELOG.md` is append-only history: an entry
   * written in May citing a section that shipped in August is a true statement about that day, and
   * a gate demanding it resolve would ask the ledger to be rewritten every time a section is
   * correctly dropped. The roadmap's side is the test above.</p>
   */
  test("SH668: every section the rationale cites exists, in either direction", () => {
    const anchors = new Set(sections().map((each) => each.anchor.replace(/\.$/, "")));
    const cited = citations(improvements);
    const dangling = [...new Set(cited)].filter((ref) => !anchors.has(ref)).sort();

    assert.deepEqual(
      dangling,
      [],
      "SH668: the rationale cites a section that is not in the file, so a reader following the "
        + "pointer cannot tell whether the pointer is wrong or the argument was never written. "
        + "A repair differs per pointer: a section that shipped points at the ledger instead, one "
        + "renumbered follows its new anchor (`roadkeep section move`), one that was always wrong "
        + "goes. Amend the prose with `roadkeep section amend <anchor>` — the hook denies the hand "
        + "edit.\n"
        + `  dangling: ${dangling.map((ref) => `§${ref}`).join(" ")}`,
    );

    process.stdout.write(
      `\nSH668 IMPROVEMENTS.md: ${cited.length} citation(s), `
        + `${new Set(cited).size} distinct, over ${anchors.size} anchor(s) — 0 dangling\n`,
    );
    // A reader whose regex stopped matching would report zero dangling out of zero citations and
    // be green for the one reason that proves nothing.
    assert.ok(cited.length > 0, `only ${cited.length} citation(s) parsed — the § form changed`);
    assert.ok(anchors.size > 0, `only ${anchors.size} anchor(s) parsed — the heading shape changed`);
  });

  /**
   * SH668 — and the table of contents is the same pointer with a different syntax.
   *
   * <p>Found by the reader above rather than looked for: `§VIII` was cited in the preamble
   * *and* listed in the contents, because Block H's family heading went when the block shipped.
   * A markdown link is checked by its **anchor token** — every row here opens with one — rather
   * than by rebuilding GitHub's slug, which involves emoji and en-dashes and would be a second
   * thing that can be wrong about a heading.</p>
   */
  test("SH668: the table of contents lists no section that is gone", () => {
    const anchors = new Set(sections().map((each) => each.anchor.replace(/\.$/, "")));
    const listed = contents();

    assert.ok(listed.length > 0, `only ${listed.length} contents row(s) parsed — the shape changed`);
    assert.deepEqual(
      listed.filter((ref) => !anchors.has(ref)),
      [],
      "SH668: the contents offers a section the file no longer has, which is the citation defect "
        + "in link form — a reader clicks it and lands nowhere. `ship` drops the section; the row "
        + "above it is nobody's job.",
    );
  });

  test("SH668: the reader flags a dead citation and spares the six kinds that are not one", () => {
    const fixture = [
      "A block family is an anchor too: §XI is the heading over §XI.21.",
      "A live subsection: §XI.21 is in the file.",
      "A deep anchor: §XV.0.5 has three parts and still resolves.",
      "An arabic family: §0.6 is a heading under Part 0.",
      "The notation itself, quoted: `§X.Y` is the form, not a pointer.",
      "Another document's section: Concept spec §10 has the original table.",
      "A bare section sign § with no anchor after it.",
      "And one that shipped: §XI.5 is gone.",
    ].join("\n");
    const anchors = new Set(["XI", "XI.21", "XV.0.5", "0.6"]);
    const dangling = [...new Set(citations(fixture))].filter((ref) => !anchors.has(ref)).sort();

    assert.deepEqual(
      dangling,
      ["XI.5"],
      "SH668: only §XI.5 is dead. The other six are the ways this reader could become a nuisance "
        + "somebody deletes — a family heading read as if it needed a number, a three-part anchor "
        + "truncated, an arabic family missed because the roman ones were assumed, the notation "
        + "read as a use of itself, a cross-document reference judged against the wrong file, and "
        + "a lone § read as an empty citation. Five of the six were live findings on the real "
        + "file, not hypotheticals.",
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
    // reader that parsed none of it would agree with all of that and with anything else — so these
    // floors exist to say the reader read something, and nothing more.
    //
    // **None of them may be a number the corpus can fall below by being correct.** Three of the
    // four here were, and all three fired in one session for the same non-reason: shipping five
    // tasks took the open backlog from 24 to 19 while every floor still said 20. The ledger only
    // grows, so `shipped` may keep an absolute one; the roadmap and this file are *meant* to empty,
    // and a floor over them is a second deadline nobody declared. Two suites away,
    // `ShRoadmapLineLengthRatchetTest` had the same line and the same morning.
    // The fourth one was this line, and it fired the morning §XII.10 shipped with SH226 and took
    // the file from 51 sections to 50 while `> 50` still stood. So it is a **coupling** now rather
    // than a number: the contents block and the headings are two independent structures over the
    // same file, SH668 keeps them in step, and a reader whose heading regex stopped matching
    // disagrees with the contents in the same breath — which is the thing the floor was for. It
    // holds at any size, including the day the last rationale ships, because §0's non-goals family
    // is permanent (agents.md puts the product's positioning there).
    const listed = contents();
    assert.ok(listed.length > 0,
      "no contents row parsed — §0 is permanent, so an empty contents is the reader, not the file");
    assert.ok(all.length >= listed.length,
      `only ${all.length} section(s) parsed while the contents lists ${listed.length} — the `
        + "heading shape changed");
    assert.ok(shipped.size > 100, `only ${shipped.size} shipped id(s) read from the ledger`);

    // Relative where it can be: one pointer per open line is the invariant, and it holds at any
    // size — including the day the backlog is empty, which is the point of having one.
    assert.ok(
      withIds.length >= pointed.size,
      `only ${withIds.length} section(s) name a task while the roadmap points at ${pointed.size}`,
    );
    assert.ok(open.size >= pointed.size,
      `the roadmap points at ${pointed.size} section(s) with only ${open.size} open id(s), so the `
        + "id scan and the pointer scan disagree about the same file");
    assert.ok(open.size > 0, "no open id read from the roadmap — the line shape changed");
  });
});
