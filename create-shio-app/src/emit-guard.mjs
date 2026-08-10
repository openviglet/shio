/**
 * What a release's re-emit is allowed to change (SH218).
 *
 * <h2>Why a release needs a guard the suite already provides</h2>
 *
 * <p>`publish-js-sdk.yml` bumps every publishable package, re-runs `emit` and commits the result.
 * That step exists because without it the generated trees keep the old dependency range and
 * `emit.test.mjs` turns the **next** push red — a release's side effect reported against whoever
 * pushed after it, where the obvious reading of a red build ("what did I break?") is wrong.</p>
 *
 * <p>The drift test cannot do this job. It runs on a push and answers "the tree matches its
 * source"; the release runs `emit` and then *commits whatever came out*, so the one thing nobody
 * checks is whether that output was the output anybody expected. A job that regenerates content
 * into `shio-app/src/main/resources/` and commits it unread is a job that can land anything.</p>
 *
 * <h2>The rule, and why it is about files rather than about lines</h2>
 *
 * <p>A release changes <b>versions</b>. It does not add or remove files: a package that gains or
 * loses one during a release is either an overlay somebody edited without running `emit`, or a
 * generator that broke — and in both cases the release is the worst possible place for it to land,
 * because a release commit is the one nobody reviews.</p>
 *
 * <p>So: <b>an addition or a deletion under the generated trees fails the release; a modification
 * is expected and reported.</b> Deliberately not "every changed line must be a semver range" —
 * that reads the diff's *text*, and a rule that has to parse a diff to decide whether a release is
 * sound is a rule that will one day be wrong about a quoted string. The file-level question is the
 * one with a clean answer, and it is the one that catches the case that matters.</p>
 *
 * @module
 */

/** Where the generated trees live, relative to the repository root. */
export const GENERATED = "shio-app/src/main/resources/blueprints/";

/**
 * Splits `git status --porcelain` into what a release may and may not have produced.
 *
 * <p>Porcelain v1, whose first two columns are the index and worktree status. Only paths under
 * {@link GENERATED} are considered: the same command reports the bumped `package.json` files,
 * which are exactly what the release is *for*.</p>
 *
 * @param {string} porcelain raw `git status --porcelain` output
 */
export function classify(porcelain) {
  const added = [];
  const deleted = [];
  const modified = [];
  for (const line of String(porcelain ?? "").split("\n")) {
    if (line.length < 4) continue;
    const status = line.slice(0, 2);
    // A rename is reported as `R  old -> new`; the arrow form would otherwise be read as one
    // path with an arrow in it, and a renamed generated file is exactly the surprise this catches.
    const path = line.slice(3).replace(/^"|"$/g, "").split(" -> ").pop();
    if (!path.startsWith(GENERATED)) continue;
    if (status.includes("?") || status.includes("A") || status.includes("R")) added.push(path);
    else if (status.includes("D")) deleted.push(path);
    else modified.push(path);
  }
  return {
    added: added.sort(),
    deleted: deleted.sort(),
    modified: modified.sort(),
    surprising: added.length + deleted.length,
  };
}

/**
 * What the release prints, and the exit code it should carry.
 *
 * <p>The modified list is printed on success too. A release that says nothing about what it
 * regenerated is one whose commit nobody can read afterwards, and "it changed six files" is the
 * cheapest possible proof that the step ran at all (P9).</p>
 */
export function describe(result) {
  const lines = [];
  if (result.surprising === 0) {
    lines.push(`emit: ${result.modified.length} generated file(s) changed, all modifications.`);
    for (const path of result.modified) lines.push(`  M ${path}`);
    return { ok: true, text: `${lines.join("\n")}\n` };
  }

  lines.push(
    `emit: the release regenerated ${result.surprising} file(s) it was not supposed to.`,
    "",
    "A release changes versions. It does not add or remove generated files — one that does is",
    "either an overlay somebody edited without running `emit`, or a generator that broke. Either",
    "way a release commit is the worst place for it to land, because it is the one nobody reads.",
    "",
  );
  for (const path of result.added) lines.push(`  + ${path}`);
  for (const path of result.deleted) lines.push(`  - ${path}`);
  lines.push(
    "",
    "Fix: run `pnpm -C create-shio-app run emit` on a branch, review what it produces, and commit",
    "that as its own change. Then re-run the release.",
  );
  return { ok: false, text: `${lines.join("\n")}\n` };
}
