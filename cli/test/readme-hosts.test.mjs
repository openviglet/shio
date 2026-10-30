/*
 * SH1340 — a README sends its reader to where the documentation lives, not to a redirect.
 *
 * docs.viglet.org/shio was frozen with a moved notice by SH1262 and now answers 301, and
 * shio.viglet.org moved to openviglet.github.io/shio on 2026-10-29. Nine READMEs still linked the
 * first, so the first click from GitHub or npm was a redirect, and anything indexing the README
 * learned the old host.
 */
// @scans prose reason: the subject is the links written in README files, read as text (SH740).
import assert from "node:assert/strict"
import { execFileSync } from "node:child_process"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..")
const OLD_HOSTS = /docs\.viglet\.org\/shio|shio\.viglet\.org/u

test("SH1340: no README links the documentation's old hosts", () => {
  const readmes = execFileSync("git", ["ls-files", "--", "README.md", "**/README.md"], { cwd: ROOT, encoding: "utf8" })
    .split(/\r?\n/u).filter(Boolean)
  assert.ok(readmes.length >= 9, `SH1340: read only ${readmes.length} README(s), so a clean result proves nothing`)
  const stale = readmes.flatMap((path) => readFileSync(join(ROOT, path), "utf8").split(/\r?\n/u)
    .map((line, index) => (OLD_HOSTS.test(line) ? `${path}:${index + 1}` : null)).filter(Boolean))
  assert.deepEqual(stale, [],
    "SH1340: these lines link docs.viglet.org/shio or shio.viglet.org, both redirects now. The "
      + "documentation is at https://openviglet.github.io/shio/docs/ and the site at https://openviglet.github.io/shio/.")
})
