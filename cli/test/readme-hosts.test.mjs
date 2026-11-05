/*
 * SH1340 — a README sends its reader to where the documentation lives, not to a redirect.
 *
 * docs.viglet.org/shio was frozen with a moved notice by SH1262 and now answers 301. The site
 * moved to openviglet.github.io/shio on 2026-10-29 and back to shio.viglet.org on 2026-11-05,
 * because a custom domain carries more search authority than a project page on a shared host.
 * A README that still links either old host sends the first click from GitHub or npm through a
 * redirect, and teaches anything indexing the README the wrong host.
 */
// @scans prose reason: the subject is the links written in README files, read as text (SH740).
import assert from "node:assert/strict"
import { execFileSync } from "node:child_process"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..")
const OLD_HOSTS = /docs\.viglet\.org\/shio|openviglet\.github\.io(?:\/|%2F)shio/u

test("SH1340: no README links the documentation's old hosts", () => {
  const readmes = execFileSync("git", ["ls-files", "--", "README.md", "**/README.md"], { cwd: ROOT, encoding: "utf8" })
    .split(/\r?\n/u).filter(Boolean)
  assert.ok(readmes.length >= 9, `SH1340: read only ${readmes.length} README(s), so a clean result proves nothing`)
  const stale = readmes.flatMap((path) => readFileSync(join(ROOT, path), "utf8").split(/\r?\n/u)
    .map((line, index) => (OLD_HOSTS.test(line) ? `${path}:${index + 1}` : null)).filter(Boolean))
  assert.deepEqual(stale, [],
    "SH1340: these lines link docs.viglet.org/shio or openviglet.github.io/shio, both redirects now. The "
      + "documentation is at https://shio.viglet.org/docs/ and the site at https://shio.viglet.org/.")
})
