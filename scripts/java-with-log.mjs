/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH800 — the Java gate, with the outcome recorded where the other gates can read it.
 *
 * ## The gate no comparator could judge
 *
 * SH798 made `docs` the fourth key, so `.gates/` held `js`, `conformance` and `docs`. It never held
 * `java`, and both comparators said so in their own words: `ShDeclaredRedTest` prints *this suite,
 * running now (it cannot stamp its own outcome)*, and `red-suites.test.mjs` found no file. Each was
 * right alone. Together they meant a red Java gate was the one state SH579's machinery could not
 * see — not declared, not undeclared, outside the comparison — and it is the fastest gate, the one
 * needing nothing, and the one somebody actually types. SH755's own finding says why that is the
 * wrong one to lose: eight red CI runs went unread because *the only gate anybody typed was the
 * Java one, and it was green*.
 *
 * ## Why a wrapper, which was not the preferred answer
 *
 * SH800 asked first whether the exit code could be stamped from outside the JVM without changing
 * the command a person is told to type. Four mechanisms, and none survives:
 *
 * <ul>
 *   <li>A Maven <b>build extension</b> (an `EventSpy` under `.mvn/extensions.xml`) does see the
 *       session result on both colours. It also needs a built artifact, which a repository that
 *       builds itself cannot depend on before it is built.</li>
 *   <li><b>`exec-maven-plugin` bound after `test`</b> never runs when the tests fail — SH416 set
 *       `testFailureIgnore=false` on purpose — so it can only ever stamp green, which is the half
 *       nobody needs.</li>
 *   <li>A <b>test inside the suite</b> cannot know whether the tests after it passed. That is the
 *       sentence `ShDeclaredRedTest` has been printing all along.</li>
 *   <li><b>Deriving it afterwards</b> from `shio-test.log` or `target/surefire-reports/` gives the
 *       colour and not the commit, so the stamp would have to assume HEAD at *reading* time. SH770
 *       exists to refuse exactly that: a stamp taken against another commit is not a colour. The
 *       project's own rule rules out its own shortcut.</li>
 * </ul>
 *
 * So: a wrapper, and the residue is honest rather than hidden. Somebody who types the raw `./mvnw`
 * still leaves no stamp — the same residue `pnpm conformance:raw` already has, and the same one the
 * other three wrappers have. What changes is that the recorded path is now the documented one.
 */
import { join } from "node:path";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

import { stamp } from "./gate-stamp.mjs";
import { runWithLog } from "./test-with-log.mjs";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

/** The name `agents.md` gives it, kept so a red run's names are where that file says they are. */
const LOG = join(ROOT, "shio-test.log");

/**
 * The Maven wrapper, by full path.
 *
 * <p>`.cmd` on Windows, and **absolute**: `spawn` runs with `cwd` set to the root, but the shell it
 * runs under resolves a bare name against `PATH` rather than that directory, so the first version
 * of this line failed with `'mvnw.cmd' is not recognized` — a stamp recording a red that was the
 * wrapper's own fault, which is the worst thing a gate recorder can write.</p>
 */
const MVNW = join(ROOT, process.platform === "win32" ? "mvnw.cmd" : "mvnw");

/**
 * The command, unchanged from what `agents.md` documents. Wrapping a gate must not quietly change
 * what it runs — `clean` is not optional (SH282) and `-Dskip.npm=true` is what keeps this the
 * backend's question, so both are here rather than left to a caller who might drop one.
 */
export const MVN_ARGS = ["-pl", "shio-app", "-am", "clean", "test", "-Dskip.npm=true"];

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const code = await runWithLog(MVNW, [...MVN_ARGS, ...process.argv.slice(2)], LOG,
    { label: "The Java suite" });
  stamp(ROOT, "java", code);
  process.exit(code);
}
