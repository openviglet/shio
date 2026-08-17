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
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
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
