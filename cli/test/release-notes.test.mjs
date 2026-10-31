/*
 * SH1339 — the release is described for the people who read it, and the workflow publishes it.
 */
// @scans prose reason: two workflow files are read as text, and the steps they name are the subject
// rather than any code around them (SH740).
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { notesFor, parse } from "../../scripts/release-notes.mjs"

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..")

test("SH1339: notes group a release's commits by who they are for, and leave the plumbing out", () => {
  const notes = notesFor([
    "feat(console): offer New page in the reader's one site from the palette (SH1338)",
    "feat(agent): mark a live post with unpublished changes in find (SH1324)",
    "fix(render): preview a page stored without its leading slash (SH1346)",
    "test(benchmark): time reads past H2's result cache (SH1327)",
    "docs: ship SH1333 now its child page previews beneath /about (SH1333)",
    "Merge branch 'x'",
  ], "2026.3.1")
  assert.match(notes, /^# Shio 2026\.3\.1/u)
  assert.match(notes, /## For curators\n\n- offer New page/u)
  assert.match(notes, /## For agents\n\n- mark a live post/u)
  assert.match(notes, /## Fixes\n\n- preview a page stored/u)
  assert.doesNotMatch(notes, /time reads past|ship SH1333|Merge branch/u,
    "SH1339: tests, docs and merges are how the work was done, not what the release gives a reader")
})

test("SH1339: a subject that is not a Conventional Commit is skipped, not misfiled", () => {
  assert.equal(parse("Merge branch '2026.3'"), null)
  assert.deepEqual(parse("feat(console)!: drop the legacy route"),
    { type: "feat", scope: "console", text: "drop the legacy route" })
})

test("SH1339: the release workflow tags pom.xml's revision, attaches the jar and publishes the image", () => {
  const workflow = readFileSync(join(ROOT, ".github", "workflows", "release.yml"), "utf8")
  assert.match(workflow, /workflow_dispatch/u, "SH1339: a release is a deliberate act, dispatched by a person")
  assert.match(workflow, /<revision>/u, "SH1339: the tag no longer comes from pom.xml's revision")
  assert.match(workflow, /viglet-shio\.jar/u, "SH1339: the release no longer attaches the jar")
  assert.match(workflow, /scripts\/release-notes\.mjs/u, "SH1339: the notes are no longer written for people")
  assert.match(workflow, /--latest/u, "SH1339: the release no longer marks itself Latest")
  assert.match(workflow, /uses: \.\/\.github\/workflows\/publish-docker\.yml/u,
    "SH1339: the image is published apart from the release again, so the two can disagree")
  const docker = readFileSync(join(ROOT, ".github", "workflows", "publish-docker.yml"), "utf8")
  assert.match(docker, /workflow_call/u, "SH1339: publish-docker.yml cannot be called by the release")
})

test("SH1349: the image is tagged with the version the release is, read from the same pom.xml", () => {
  const docker = readFileSync(join(ROOT, ".github", "workflows", "publish-docker.yml"), "utf8")
  assert.match(docker, /<revision>/u, "SH1349: the image's version no longer comes from pom.xml's revision")
  assert.match(docker, /type=raw,value=\$\{\{ steps\.version\.outputs\.version \}\}/u,
    "SH1349: the image is pushed without a version tag, so shio:2026.3 cannot match release 2026.3")
})
