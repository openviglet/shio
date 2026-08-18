/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH755 — what each gate last answered, written down where the other gates can read it.
 *
 * ## The hole this fills
 *
 * `red-suites.json` is a **claim** about which gates are red, and until now nothing compared it
 * against what the gates actually said. The two can disagree in both directions and both were
 * unobservable: a gate red with no entry (which is the state the project was in for eight
 * consecutive CI runs), and an entry for a gate that has since gone green.
 *
 * SH579 built the ledger and the rule; what it could not build was the other side of the
 * comparison, because a suite cannot run the suites — it is one of them. So the gates leave a
 * stamp, and the comparison becomes possible without anybody running anything twice.
 *
 * ## Local evidence, deliberately untracked
 *
 * `.gates/` is git-ignored. A stamp describes **this working tree's** last run, and committing one
 * would mean a clone inheriting somebody else's claim about a command it has never executed —
 * which is the same shape of lie the ledger already had. A tree with no stamps is honest: nobody
 * has run that gate here, and the readers say exactly that rather than passing quietly.
 *
 * ## What is recorded, and what is not
 *
 * The exit code, the commit, and when. **Not the failing test's name** — that is what the kept
 * logs are for (SH645/SH735), and a stamp that tried to summarise a run would be the filtered
 * `grep` those tasks exist to refuse.
 */
import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";

/**
 * The keys `red-suites.json` allows, and the only names a stamp may take.
 *
 * <p>`docs` joined as the fourth in SH798. It had been a gate in every sense that matters — its own
 * job in `suites.yml`, its own trigger, its own input, its own red — and the only one of the four
 * that could be run without leaving evidence it had been, which is how it carried twelve commits of
 * debt while the comparators reported the tree honestly and said nothing about it.</p>
 */
export const GATES = ["java", "js", "conformance", "docs"];

/** Where stamps live, relative to the repository root. */
export const GATES_DIR = ".gates";

/** The commit a stamp is about, or `null` where git cannot say — a stamp with no sha is still one. */
export function headSha(root) {
  try {
    return execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();
  } catch {
    return null;
  }
}

/**
 * Whether a process id is one this machine is still running (SH803).
 *
 * <p>Signal `0` is the portable "does this exist" probe: it delivers nothing and throws `ESRCH` when
 * the target is gone. `EPERM` means it exists and belongs to somebody else, which for this purpose
 * is alive. A lock whose holder has died must never outlast it — a gate that refuses to run because
 * of a crash three days ago is worse than the collision it was guarding.</p>
 */
export function alive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch (error) {
    return error?.code === "EPERM";
  }
}

/**
 * Take the lock for one gate, or say who holds it.
 *
 * <h2>What this is for</h2>
 *
 * <p>SH803 — two runs of the same gate at once corrupt each other. Measured, by accident: a
 * backgrounded `pnpm java` and a foreground one overlapped, and the foreground reported <b>3 errors
 * over 1092 tests</b> where the tree alone reports <b>1894 green</b>, while `grep` answered
 * `Binary file shio-test.log matches` because two writers had interleaved into one fixed-name log.
 * Neither number was about the code.</p>
 *
 * <p>Before SH800 that was merely confusing. Now it is <b>recorded</b>: the wrapper stamped
 * `red: true` for a failure that never happened, and the comparators read that stamp and demand a
 * `red-suites.json` entry against an open task. A false red is worse than an absent stamp — absence
 * says nothing, and this manufactures a declaration for a defect nobody can find.</p>
 *
 * <p><b>Per gate, not global.</b> Two `java` runs share `target/` and one log; `java` and `js` share
 * neither, and this session ran them together for hours. A global lock would refuse the thing people
 * actually do to save time.</p>
 *
 * @returns `{ taken: true, stale }` where `stale` is a dead holder that was cleared, or
 *          `{ taken: false, holder }` naming who has it and since when
 */
export function acquire(root, gate, pid = process.pid, now = null) {
  const path = join(root, GATES_DIR, `${gate}.lock`);
  try {
    mkdirSync(join(root, GATES_DIR), { recursive: true });
    let stale = null;
    if (existsSync(path)) {
      const holder = JSON.parse(readFileSync(path, "utf8"));
      if (holder?.pid !== pid && alive(holder?.pid)) {
        return { taken: false, holder };
      }
      stale = holder?.pid === pid ? null : holder;
    }
    writeFileSync(path, `${JSON.stringify({ gate, pid, since: now }, null, 2)}\n`);
    return { taken: true, stale };
  } catch {
    // Never turn a gate run into a crash about bookkeeping — `stamp` refuses that trade three
    // functions down and this one is the same bargain: an unwritable lock permits the run.
    return { taken: true, stale: null };
  }
}

/** Give the lock back. Missing is fine: a run that never took one still finishes. */
export function release(root, gate) {
  try {
    rmSync(join(root, GATES_DIR, `${gate}.lock`), { force: true });
  } catch {
    // Same bargain as above.
  }
}

/**
 * Record what one gate answered.
 *
 * <p>Never throws. A gate that could not write its stamp must still report its own exit code —
 * turning a green run into a crash about bookkeeping is the trade `keepIfRed` already refuses one
 * file over.</p>
 */
export function stamp(root, gate, code) {
  if (!GATES.includes(gate)) {
    return null;
  }
  try {
    const dir = join(root, GATES_DIR);
    mkdirSync(dir, { recursive: true });
    const record = {
      gate,
      code,
      red: code !== 0,
      sha: headSha(root),
      when: new Date().toISOString(),
    };
    writeFileSync(join(dir, `${gate}.json`), `${JSON.stringify(record, null, 2)}\n`, "utf8");
    return record;
  } catch {
    return null;
  }
}

/**
 * The two gates that host a comparator, and therefore the only pair that can deadlock (SH818).
 *
 * <p>`ShDeclaredRedTest` runs inside `java` and judges the other three; `red-suites.test.mjs` runs
 * inside `js` and judges the other three. Each reads the other's stamp, which is the arrangement
 * that makes every gate judged once — and it has a fixed point: with **both** stamps red, each run
 * re-reads the other's red and stamps itself red again, so no sequence of runs clears it even when
 * neither suite has a failure of its own.</p>
 */
export const COMPARATOR_GATES = ["java", "js"];

/**
 * Are the two comparator gates red about nothing but each other? (SH818)
 *
 * <p>Deliberately a <b>question about the shape</b>, not about the cause: a stamp records an exit
 * code and never why, and inferring a reason from a code is the summarising a stamp refuses to do.
 * So this does not suppress anything — both gates being red is still a red, and a real double
 * failure looks identical from here. What it changes is the <em>report</em>: a reader told "these
 * two can be red only about each other, and here is the exit" spends a minute, where the state
 * itself gives no hint that running the gates again is the one thing that cannot work.</p>
 */
export function comparatorCycle(recorded) {
  return COMPARATOR_GATES.every((gate) => {
    const seen = recorded?.[gate];
    return Boolean(seen) && seen.red === true && seen.stale !== true;
  });
}

/** What to do about a cycle, in the words the reader needs — one sentence per line. */
export const CYCLE_EXIT = [
  "SH818: `java` and `js` are BOTH red, and those are the two gates that judge each other.",
  "  If each suite's only failure is the other's stamp, no run of either can clear it — the",
  "  colours are the pair, not the code. Check the kept logs first (shio-test.log,",
  "  shio-js-test.log): a real failure there is a real failure. If both are clean but for the",
  "  comparator, break the cycle with `pnpm gates:clear java js`, then run `pnpm java` and",
  "  `pnpm test` in that order. Clearing is recorded, not a deletion — see .gates/<gate>.json.",
].join("\n");

/**
 * Record that a stamp was deliberately discarded, rather than deleting the file (SH818).
 *
 * <p>Deleting works — the readers treat an absent stamp as *no run recorded*, which is honest —
 * and it leaves nothing behind, so a stamp cleared to break a cycle is indistinguishable from one
 * cleared to dodge a red somebody did not want to fix. A cleared stamp carries no colour, for the
 * same reason an absent one does not, and it does say <em>when</em> and <em>at which commit</em>
 * somebody decided the last run was not evidence. That is the difference between an exit and a
 * hole, and it costs one field.</p>
 */
export function clear(root, gate) {
  if (!GATES.includes(gate)) {
    return null;
  }
  try {
    const dir = join(root, GATES_DIR);
    mkdirSync(dir, { recursive: true });
    const record = { gate, cleared: true, sha: headSha(root), when: new Date().toISOString() };
    writeFileSync(join(dir, `${gate}.json`), `${JSON.stringify(record, null, 2)}\n`, "utf8");
    return record;
  } catch {
    return null;
  }
}

/**
 * Was this stamp taken against the tree that is being judged? (SH770)
 *
 * <h3>The comparison that was missing</h3>
 *
 * <p>The readers compared a **claim** — what `red-suites.json` declares — against a
 * **measurement**, and nothing asked whether the measurement was about this tree. Measured: this
 * repository ran a Java suite that failed over `.gates/conformance.json` holding `red: true` at
 * `4b20d8e0` while HEAD was `42d02432`, two commits later. It happened to be right, because the
 * defect was still open. That is luck, not a property of a check.</p>
 *
 * <p>Both directions are wrong and the second is worse. A **stale red** blocks a commit over a
 * failure that may already be fixed, which teaches people to re-run until it clears. A **stale
 * green** reads as *this tree is green*, which is the exact sentence SH755 exists to stop somebody
 * believing — one level up from where it stopped them.</p>
 *
 * <h3>Why the commit alone, and not SH764's `compiles()`</h3>
 *
 * <p>That predicate answers *could this change have produced different classes*, and it is the
 * right question for the **JVM**. It is the wrong one here, because **no gate writes a `java`
 * stamp** — this test is the java gate and cannot stamp its own outcome — so every stamp that
 * exists belongs to `js` or `conformance`, and both read the whole tree. `pnpm test` reads
 * `ROADMAP.md` and `CHANGELOG.md`; `pnpm conformance` reads `docs/`. A documentation commit really
 * can flip either colour, so there is nothing provably inert to exempt and the honest rule is the
 * simple one.</p>
 *
 * <p>Unknown either side is <b>not</b> stale: a stamp with no sha, or a tree with no git, cannot be
 * shown to be about another commit, and inventing staleness from an absence is the same mistake in
 * the other direction.</p>
 */
export function isStale(record, head) {
  if (!head || !record?.sha) {
    return false;
  }
  return String(record.sha).trim() !== String(head).trim();
}

/**
 * Every stamp this tree carries, keyed by gate. Absent and unreadable both read as absent.
 *
 * <p>Each record carries `stale` (SH770). A reader judges on the fresh ones and <b>prints</b> the
 * stale ones, because "the last run here was two commits ago" is a more useful sentence than either
 * silence or a colour — it names the run somebody has to do.</p>
 */
export function stamps(root) {
  const found = {};
  const head = headSha(root);
  for (const gate of GATES) {
    const path = join(root, GATES_DIR, `${gate}.json`);
    if (!existsSync(path)) {
      continue;
    }
    try {
      const record = JSON.parse(readFileSync(path, "utf8"));
      // SH818 — a cleared stamp has no `red`, so it falls out here exactly as an absent one does,
      // and that is the whole design: the readers already say "no run recorded in this tree" and
      // need no new branch to be correct. The file survives so `git status` and a curious reader
      // can see the clearing happened, which a deletion does not offer.
      if (record && typeof record === "object" && typeof record.red === "boolean") {
        found[gate] = { ...record, stale: isStale(record, head) };
      }
    } catch {
      // A corrupt stamp is no stamp. It cannot be allowed to fail a gate, because the thing it
      // would fail is not the thing it describes.
    }
  }
  return found;
}
