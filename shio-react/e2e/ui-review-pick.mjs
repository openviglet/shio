/*
 * SH1321 — the console review's route picker, the half of `shio-console-review` that is code.
 *
 * Reads the last `console-check.mjs` report and the committed review ledger, and prints the next
 * route families to judge as JSON: their paths, screenshots and whatever the check said about
 * them. After the review, `--record` writes the ledger so the next iteration moves on.
 *
 * Usage
 *   node e2e/ui-review-pick.mjs                          # the next 3 families
 *   node e2e/ui-review-pick.mjs --count 5
 *   node e2e/ui-review-pick.mjs --record sites,post-edit --findings SH1400,SH1401
 *   node e2e/ui-review-pick.mjs --record trash            # reviewed, nothing found
 *
 *   --report  the check's report (default e2e/report/console-check.json)
 *   --ledger  the review ledger  (default ui-review-ledger.json)
 *
 * Paths are anchored to this script, not to the cwd, for the reason SH902 gives in
 * `console-check.mjs`: run from the repo root, a cwd-relative default lands somewhere else.
 */
import { execFileSync } from "node:child_process"
import { readFile, writeFile } from "node:fs/promises"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"
import { pickNext, recordReview, reviewableFamilies } from "../scripts/ui-review.mjs"

const HERE = dirname(fileURLToPath(import.meta.url))
const args = process.argv.slice(2)
const flag = (name, fallback) => {
  const i = args.indexOf(`--${name}`)
  return i >= 0 && args[i + 1] && !args[i + 1].startsWith("--") ? args[i + 1] : fallback
}
const list = (value) => (value ? value.split(",").map((s) => s.trim()).filter(Boolean) : [])

const REPORT = flag("report", join(HERE, "report", "console-check.json"))
const LEDGER = flag("ledger", join(HERE, "..", "ui-review-ledger.json"))

async function main() {
  const ledger = JSON.parse(await readFile(LEDGER, "utf8"))
  const record = list(flag("record"))

  if (record.length) {
    const commit = execFileSync("git", ["rev-parse", "--short", "HEAD"], { encoding: "utf8" }).trim()
    const reviewed = new Date().toISOString().slice(0, 10)
    const next = recordReview(ledger, record, { reviewed, commit, findings: list(flag("findings")) })
    await writeFile(LEDGER, `${JSON.stringify(next, null, 2)}\n`)
    console.log(`ui-review: recorded ${record.join(", ")} as reviewed ${reviewed} at ${commit}`)
    return
  }

  let report
  try {
    report = JSON.parse(await readFile(REPORT, "utf8"))
  } catch {
    throw new Error(`no report at ${REPORT}: run \`pnpm -C shio-react run console:check\` first`)
  }
  const families = reviewableFamilies(report)
  const picked = pickNext(families, ledger, Number(flag("count", "3")))
  console.log(JSON.stringify({
    report: REPORT,
    families: families.length,
    reviewed: Object.keys(ledger.families ?? {}).length,
    picked,
  }, null, 2))
}

main().catch((e) => {
  console.error(`ui-review-pick: ${e.message}`)
  process.exitCode = 1
})
