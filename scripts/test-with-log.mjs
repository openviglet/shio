/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH645 — `pnpm test`, with the output kept.
 *
 * ## The defect this exists for is not a failing test
 *
 * `pnpm test` has reported `cli test: ℹ fail 1` on a tree whose other suites were green, twice, and
 * **neither time was the failing test's name captured**. Both runs were read through a `grep` for
 * the summary line, which is the one thing that survives when the output is filtered — so what was
 * recorded was a count. It has never reproduced: nine consecutive runs after the sightings were
 * clean, and `node --test` inside `cli/` has never failed.
 *
 * That combination is what makes it a task. A red that is green on the next attempt teaches people
 * that red means nothing, and it teaches that whether or not the red was true. `agents.md` already
 * says this about the Java suite in SH354's words — *tee the run, never pipe it straight into
 * `grep`* — and the JS side had no equivalent, so the same lesson had to be learned twice and the
 * second time cost the evidence.
 *
 * ## Why a script and not a shell pipeline
 *
 * `tee` is not portable here: this repository is driven from PowerShell and from Git Bash, and
 * `Tee-Object` and `tee` differ in buffering, in encoding and in what they do to the exit code. A
 * runner that swallows a non-zero exit would turn the one gate that *is* reliable into one that is
 * not, which would be a far worse trade than the flake it is chasing.
 *
 * So: spawn the same command, stream every byte to this process's own stdout **and** to a file, and
 * exit with the child's code. Nothing is filtered, nothing is summarised, and a caller who pipes
 * *this* into `grep` still has the whole run on disk afterwards — which is the entire point.
 */
import { spawn } from "node:child_process";
import { createWriteStream, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

/**
 * Where the run is kept. One fixed name rather than a timestamp, for the reason the markup cache
 * takes the same decision: the interesting comparison is against the *previous* run, and a
 * directory that grows a file per invocation is one nobody prunes and everybody greps twice.
 */
const LOG = join(ROOT, "shio-js-test.log");

/**
 * The workspace filter, in one place. Duplicated from `package.json`'s `test` script when this
 * shipped, because that is the list the gate has always run — changing which packages are tested is
 * a decision, and doing it as a side effect of adding a log would be exactly the kind of quiet
 * scope change this repository keeps finding.
 */
const PACKAGES = [
  "@viglet/shio-client", "@viglet/shio-model", "@viglet/shio-sections",
  "@viglet/create-shio-app", "@viglet/shio", "@viglet/shio-claude-plugin",
  "shio-react", "shio-site",
];

export const PNPM_ARGS = ["--reporter", "append-only",
  ...PACKAGES.flatMap((name) => ["--filter", name]), "run", "test"];

/**
 * Runs a command, keeping every byte, and resolves with the exit code it should be given.
 *
 * <p>Exported and parameterised for one reason: the two properties this file exists for — <b>the
 * output is kept</b> and <b>the exit code is not swallowed</b> — are exactly the two a test can
 * drive in milliseconds against a trivial child, where driving them against `pnpm test` would mean
 * running the suite to test the thing that runs the suite. SH527's rule is the assertion that would
 * have caught the defect at the boundary it crossed, and this is that boundary.</p>
 */
export function runWithLog(command, args, logPath, { io = process, shell = true } = {}) {
  return new Promise((resolve) => {
    mkdirSync(dirname(logPath), { recursive: true });
    const log = createWriteStream(logPath, { flags: "w" });
    log.write(`$ ${command} ${args.join(" ")}\n`);

    // `shell` defaults true for `pnpm`, which is a `.cmd` on Windows that `spawn` will not find
    // otherwise; the arguments are this file's own constants, so there is no injection surface to
    // weigh against it. It is an OPTION because a shell re-splits the command on spaces, and
    // `process.execPath` — the one a test drives this with — is a path that has them.
    const child = spawn(command, args, { cwd: ROOT, shell });

    for (const [stream, sink] of [[child.stdout, io.stdout], [child.stderr, io.stderr]]) {
      stream.on("data", (chunk) => {
        sink.write(chunk);
        log.write(chunk);
      });
    }

    child.on("error", (error) => {
      const message = `\ntest-with-log: could not start ${command}: ${error.message}\n`;
      io.stderr.write(message);
      log.write(message);
      log.end(() => resolve(1));
    });

    child.on("close", (code, signal) => {
      // Said on both streams, because the whole failure mode is a caller who reads one of them
      // through a filter. A green run says where the log is too: the next red one is somebody
      // else's, and they should not have to find this file to learn the evidence was kept.
      const outcome = code === 0
        ? `\nJS suites green — full output in ${logPath}\n`
        : `\nJS suites FAILED (${signal ? `signal ${signal}` : `exit ${code}`}) — the failing `
          + `test's name is in ${logPath}, which this run kept precisely because the last two `
          + "sightings did not.\n";
      io.stdout.write(outcome);
      log.write(outcome);
      // `?? 1` rather than `?? 0`: a child killed by a signal reports a null code, and reading that
      // as success is the exact swallow this whole file refuses.
      log.end(() => resolve(code === 0 ? 0 : (code ?? 1)));
    });
  });
}

// Only when run, never when read: the test imports this module for `runWithLog`, and a module that
// launched the whole suite on import would make that test the thing it is testing.
if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  process.exit(await runWithLog("pnpm", PNPM_ARGS, LOG));
}
