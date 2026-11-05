import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { test } from "node:test";

// @scans prose reason: workflow YAML is read as text, and the tag name a release step spells is
// the subject rather than any code around it (SH740).

/**
 * SH1358 — a release tag never shares a branch's name.
 *
 * This repository names its version branches after the version (`2026.3`, one worktree per line),
 * and SH1339 tagged the first release with the same bare version. Git resolves a short push source
 * against heads and tags alike and refuses one that matches both, so from the first release on,
 * `git push origin 2026.3:2026.3` — what an editor's sync runs after every commit — failed with
 * `src refspec 2026.3 matches more than one`. The commit succeeded, so the failure was reported one
 * step away from its cause and read as "the commit fails".
 *
 * The branch cannot move (the worktree layout, the `latest` junction, the CI branch filters and the
 * Dockerfile's `REPO_BRANCH` all name it), so the tag carries a `v`, which is what every tag before
 * 2026.3 already did. This holds the two places release.yml spells the tag; the jar name, the title
 * and the Docker image tag keep the bare version because none of them is a git ref.
 */

const ROOT = fileURLToPath(new URL("../../", import.meta.url));
const WORKFLOWS = join(ROOT, ".github", "workflows");

const read = (name) => readFileSync(join(WORKFLOWS, name), "utf8");

/** The tag `gh release create` is given, and the ref the "already released" check verifies. */
function releaseTagSpellings(text) {
  const created = /gh release create\s+"([^"]+)"/.exec(text);
  const checked = /git rev-parse -q --verify\s+"refs\/tags\/([^"]+)"/.exec(text);
  return { created: created?.[1] ?? null, checked: checked?.[1] ?? null };
}

test("SH1358: release.yml tags a release as v<version>, in both places it spells the tag", () => {
  const { created, checked } = releaseTagSpellings(read("release.yml"));

  assert.equal(created, "v$VERSION",
    "SH1358: the tag `gh release create` makes must be `v$VERSION`. A bare `$VERSION` is the "
      + "version branch's own name, and git refuses to push a branch whose short name also names "
      + "a tag — which is how every editor sync after a commit failed on 2026.3.");
  assert.equal(checked, "v$VERSION",
    "SH1358: the \"already released\" check must verify the same spelling the create step writes, "
      + "or a repeat release is refused against a tag nobody creates and admitted against the one "
      + "that exists.");
});

test("SH1358: the artefacts that are not git refs keep the bare version", () => {
  const release = read("release.yml");
  const docker = read("publish-docker.yml");

  assert.match(release, /viglet-shio-\$VERSION\.jar/,
    "SH1358: the jar is named by the bare version; a `v` there would rename a download for a "
      + "reason that has nothing to do with files.");
  assert.match(release, /--title "Shio \$VERSION"/,
    "SH1358: the release title reads `Shio 2026.3`, the way people say it.");
  assert.match(docker, /type=raw,value=\$\{\{ steps\.version\.outputs\.version \}\}/,
    "SH1358: the Docker image tag is the pom version, read from pom.xml and never from the git "
      + "tag, so renaming the tag moved no image.");
});

test("SH1358: the reader is not vacuous — a bare tag in either place is reported", () => {
  const sample = [
    "          if git rev-parse -q --verify \"refs/tags/$VERSION\" >/dev/null; then",
    "          gh release create \"$VERSION\" \"$RUNNER_TEMP/viglet-shio-$VERSION.jar#viglet-shio.jar\" \\",
  ].join("\n");

  assert.deepEqual(releaseTagSpellings(sample), { created: "$VERSION", checked: "$VERSION" },
    "SH1358: the reader must find both spellings, so that the first test fails on the bare "
      + "version rather than on a regex that matched nothing.");
});
