// SH533: the token numbers on the site come from the build's own budget file.
//
// ## Why a generator and not four numbers typed into site-content.ts
//
// P3 makes tokens a measured budget with a ceiling the build enforces, and that
// is the site's sharpest differentiating claim. A number on a marketing page
// that has drifted from the build is worse than no number at all: it turns the
// one falsifiable thing being said into the one thing that is false. So the
// pipeline is the deliverable: `shio-app/src/test/resources/token-budgets.properties`
// is the single source the Java suite already asserts against, this reads it,
// and the site imports what this writes.
//
// The coupling across packages is deliberate and is the point. The alternative
// is a copy, and a second place to keep a number is a place that goes stale,
// which is the reasoning the budgets file itself carries for keeping every
// ceiling in one file rather than in the assertions.
//
// ## Ceilings, and the site says so
//
// Every value here is a *ceiling*, not a measurement: it is what the build
// refuses to exceed. That is the honest thing to publish, because it is the
// only one of the two that is machine-readable and therefore the only one that
// cannot silently drift. The chart labels them as ceilings.
//
// ## The ratio unit, which is a trap
//
// `ShAgentTaskBenchmarkTest.ratio()` returns **integer tenths**: `30` is 3.0x,
// not 30x. Reading it as a whole number would have put a claim on the page an
// order of magnitude larger than anything measured. Converted here, once.

import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const BUDGETS = resolve(
  process.cwd(),
  "..",
  "shio-app",
  "src",
  "test",
  "resources",
  "token-budgets.properties"
);
const OUT = resolve(process.cwd(), "src", "lib", "token-budgets.generated.json");

function readProperties(path) {
  const text = readFileSync(path, "utf-8");
  const out = {};
  for (const line of text.split("\n")) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const eq = trimmed.indexOf("=");
    if (eq === -1) continue;
    out[trimmed.slice(0, eq).trim()] = trimmed.slice(eq + 1).trim();
  }
  return out;
}

const props = readProperties(BUDGETS);

/** A missing key fails the build. A chart that silently drops a bar is the defect. */
function need(key) {
  const raw = props[key];
  if (raw === undefined) {
    throw new Error(
      `token-budgets: no key '${key}' in ${BUDGETS}. ` +
        `The site draws it, so a rename here is a build failure rather than a missing bar.`
    );
  }
  const value = Number(raw);
  if (!Number.isFinite(value)) {
    throw new TypeError(`token-budgets: key '${key}' is '${raw}', which is not a number.`);
  }
  return value;
}

/** Integer tenths → a number of times, e.g. 30 → 3. */
const times = (tenths) => Math.round((tenths / 10) * 10) / 10;

const CEILINGS = [
  {
    key: "response.manifest.check",
    label: "Capability check",
    note: "manifest?include=features,limits: what a session opens with",
  },
  {
    key: "response.manifest",
    label: "Full manifest",
    note: "every capability, every limit, a curated endpoint index",
  },
  {
    key: "mcp.tools.list.total",
    label: "Every MCP tool schema",
    note: "tools/list, re-sent on every turn before any work happens",
  },
  {
    key: "benchmark.build-marketing-site.tokens",
    label: "Build a site, end to end",
    note: "the whole task, measured to the verify, not to a page a person opened",
  },
];

const TASKS = [
  { task: "build-marketing-site", label: "Build a marketing site" },
  { task: "add-type-and-10-posts", label: "Add a type and ten posts" },
  { task: "translate-site", label: "Translate a site" },
  { task: "fix-broken-link", label: "Fix a broken link" },
];

const budgets = {
  source: "shio-app/src/test/resources/token-budgets.properties",
  ceilings: CEILINGS.map((c) => ({ ...c, tokens: need(c.key) })),
  tasks: TASKS.map((t) => ({
    label: t.label,
    calls: need(`benchmark.${t.task}.calls`),
    tokens: need(`benchmark.${t.task}.tokens`),
    // How much worse the same job is over the id-keyed REST surface an agent
    // would otherwise have to drive. Asserted as a floor, so it is "at least".
    callRatio: times(need(`benchmark.${t.task}.min-call-ratio`)),
    tokenRatio: times(need(`benchmark.${t.task}.min-token-ratio`)),
  })),
};

writeFileSync(OUT, `${JSON.stringify(budgets, null, 2)}\n`, "utf-8");
console.log(
  `token-budgets: ${budgets.ceilings.length} ceiling(s) and ${budgets.tasks.length} task(s)` +
    ` from ${budgets.source}`
);
