// @scans prose reason: this file reads one JSON matrix; the Java it is checked against is read by
// the product-vocabulary readers, whose own scan product-vocabulary.test.mjs holds (SH740).
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { test } from "node:test";

import { manifestFeatures, mcpTools } from "../conformance/support/product-vocabulary.mjs";

/**
 * SH1250 — the showcase's feature-to-page matrix is complete against the product as it is.
 *
 * <p>The matrix (docs/specs/SH1250-showcase-matrix.json) is the design every later showcase task
 * builds from, and it is only worth anything while it names every capability. So: each manifest
 * `features` key and each MCP tool appears exactly once, each entry is a page from the page map or
 * an exclusion with a reason, and nothing appears that the product no longer declares. A feature
 * that ships after the spec turns this red until somebody decides where the demo shows it.</p>
 *
 * <p>Whether those pages exist in the package is SH1255's question, asked once the package does.</p>
 */

const ROOT = fileURLToPath(new URL("../../", import.meta.url));
const matrix = JSON.parse(readFileSync(join(ROOT, "docs", "specs", "SH1250-showcase-matrix.json"), "utf8"));

function judge(label, rows, declared) {
  const named = Object.keys(rows);
  assert.ok(declared.size > 0, `SH1250: the ${label} reader found nothing, so every row would pass`);
  assert.deepEqual(named.filter((key) => !declared.has(key)), [],
    `SH1250: the matrix names ${label} the product does not declare`);
  assert.deepEqual([...declared].filter((key) => !(key in rows)).sort(), [],
    `SH1250: the product declares ${label} the matrix does not place on a page or exclude`);
  for (const [key, row] of Object.entries(rows)) {
    const shapes = ["page", "exclude"].filter((field) => field in row);
    assert.equal(shapes.length, 1, `SH1250: ${key} needs exactly one of page or exclude`);
    if (row.page !== undefined) {
      assert.ok(row.page in matrix.pages, `SH1250: ${key} points at ${row.page}, which the page map lacks`);
    } else {
      assert.ok(typeof row.exclude === "string" && row.exclude.length > 20,
        `SH1250: ${key} is excluded without a reason a reviewer can weigh`);
    }
  }
  process.stdout.write(`SH1250 ${label}: ${named.length} rows, `
    + `${named.filter((key) => rows[key].exclude).length} excluded\n`);
}

test("SH1250: every manifest feature key has a showcase page or a stated exclusion", () => {
  judge("features", matrix.features, manifestFeatures());
});

test("SH1250: every MCP tool has a showcase page", () => {
  judge("MCP tools", matrix.mcpTools, mcpTools());
  assert.deepEqual(Object.entries(matrix.mcpTools).filter(([, row]) => row.exclude).map(([key]) => key), [],
    "SH1250: a tool is what an agent calls, so the tour shows every one");
});

test("SH1250: the showcase is bilingual and every curator surface has a page", () => {
  assert.deepEqual(matrix.locales, ["en-us", "pt-br"]);
  for (const [surface, row] of Object.entries(matrix.curatorSurfaces)) {
    assert.ok(row.page in matrix.pages, `SH1250: ${surface} points at a page the map lacks`);
  }
});
