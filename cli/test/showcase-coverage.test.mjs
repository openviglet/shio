// @scans prose reason: this file reads the showcase matrix and the package's content as JSON; the
// Java the subjects come from is read by the product-vocabulary readers, held by their own scan.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { test } from "node:test";

import { manifestFeatures, mcpTools } from "../conformance/support/product-vocabulary.mjs";

/**
 * SH1255 — every capability Shio ships has a built showcase page, or a reason it has none.
 *
 * <p>A demo that shows every feature on the day it ships shows most of them a year later. SH1250's
 * matrix says where each capability is shown and `showcase-matrix.test.mjs` holds it complete
 * against the product; this asks the next question, whether the page it names is actually in the
 * Quilhara package (marketplace/quilhara), in both languages.</p>
 *
 * <p>A subject (manifest `features` key, MCP tool, curator surface) whose page is not built is
 * **debt**, held below as a named list the way `assertion-debt.test.mjs` holds its own: a new
 * feature, or a page renamed out from under its subjects, lands red until somebody builds the
 * page or writes the exclusion. Paying debt is red too, until the name leaves the list, so the
 * list cannot drift above the truth. SH1256 builds the tour pages that drain it.</p>
 */

const ROOT = fileURLToPath(new URL("../../", import.meta.url));
const PACKAGE = join(ROOT, "marketplace", "quilhara");
const matrix = JSON.parse(readFileSync(join(ROOT, "docs", "specs", "SH1250-showcase-matrix.json"), "utf8"));
const blueprint = JSON.parse(readFileSync(join(PACKAGE, "blueprint.json"), "utf8"));
const posts = blueprint.content.flatMap((file) =>
  JSON.parse(readFileSync(join(PACKAGE, file), "utf8")).posts ?? []);

/**
 * Matrix pages the package does not hold yet. `/members` needs a content ACL, which a package
 * cannot declare (SH1277).
 */
const PAGES_OWED = [
  "/members",
];

/** Subjects whose page is in PAGES_OWED, as `<kind>:<key>`. */
const DEBT = [
  "feature:contentAcl",
];

const [primary, ...others] = matrix.locales;
const built = new Set(posts.filter((post) => post.locale === primary).map((post) => post.url));

function subjects() {
  const rows = [
    ...[...manifestFeatures()].map((key) => ["feature", key, matrix.features[key]]),
    ...[...mcpTools()].map((key) => ["mcp", key, matrix.mcpTools[key]]),
    ...Object.entries(matrix.curatorSurfaces).map(([key, row]) => ["curator", key, row]),
  ];
  return rows.map(([kind, key, row]) => ({ name: `${kind}:${key}`, row }));
}

test("SH1255: the package and the subjects were read, so an empty read cannot agree with every claim", () => {
  assert.ok(posts.length > 0, "SH1255: no posts were read from the Quilhara package");
  assert.ok(built.size > 0, `SH1255: the package holds no ${primary} page`);
  assert.ok(manifestFeatures().size > 0 && mcpTools().size > 0, "SH1255: the product readers found nothing");
});

test("SH1255: every built showcase page exists in every showcase language", () => {
  for (const locale of others) {
    const twins = new Set(posts.filter((post) => post.locale === locale).map((post) => post.translationOf));
    const missing = Object.keys(matrix.pages).filter((page) => built.has(page) && !twins.has(page));
    assert.deepEqual(missing, [], `SH1255: these ${primary} pages have no ${locale} translation`);
  }
});

test("SH1255: the matrix pages the package lacks are exactly the ones owed", () => {
  const owed = Object.keys(matrix.pages).filter((page) => !built.has(page)).sort();
  assert.deepEqual(owed, [...PAGES_OWED].sort(),
    "SH1255: a matrix page was built (drop it from PAGES_OWED) or renamed out of the package");
});

test("SH1255: every subject has a built showcase page or an exclusion, except the named debt", () => {
  const all = subjects();
  const debt = all.filter(({ row }) => !row || (row.exclude === undefined && !built.has(row.page)))
    .map(({ name }) => name).sort();
  process.stdout.write(`SH1255 showcase coverage: ${all.length} subjects, ${debt.length} in debt\n`);
  assert.deepEqual(debt, [...DEBT].sort(),
    "SH1255: build the page or write its exclusion in the matrix; a paid entry leaves DEBT");
});

test("SH1256: every tour page, in every language, ends with a complete how-it-was-built note", () => {
  const byUrl = new Map(posts.map((post) => [post.url, post]));
  const tourPages = posts.filter((post) => post.type === "InfoPage"
    && Object.keys(matrix.pages).some((page) => page.startsWith("/tour")
      && (post.url === page || post.translationOf === page)));
  assert.equal(tourPages.length, Object.keys(matrix.pages).filter((page) => page.startsWith("/tour")).length
    * matrix.locales.length, "SH1256: a tour page is missing in one of the showcase languages");
  for (const page of tourPages) {
    const note = byUrl.get(page.data.sections.at(-1));
    assert.equal(note?.type, "BuildNote", `SH1256: ${page.url} does not end with a BuildNote`);
    assert.equal(note.locale, page.locale, `SH1256: ${page.url} borrows another language's note`);
    for (const field of ["consoleRoute", "mcpCall", "cliLine", "cdaRequest"]) {
      assert.ok(note.data[field]?.trim(), `SH1256: ${note.url} has no ${field}`);
    }
  }
});
