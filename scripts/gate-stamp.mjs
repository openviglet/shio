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

/** The three keys `red-suites.json` allows, and the only names a stamp may take. */
export const GATES = ["java", "js", "conformance"];

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

/** Every stamp this tree carries, keyed by gate. Absent and unreadable both read as absent. */
export function stamps(root) {
  const found = {};
  for (const gate of GATES) {
    const path = join(root, GATES_DIR, `${gate}.json`);
    if (!existsSync(path)) {
      continue;
    }
    try {
      const record = JSON.parse(readFileSync(path, "utf8"));
      if (record && typeof record === "object" && typeof record.red === "boolean") {
        found[gate] = record;
      }
    } catch {
      // A corrupt stamp is no stamp. It cannot be allowed to fail a gate, because the thing it
      // would fail is not the thing it describes.
    }
  }
  return found;
}
