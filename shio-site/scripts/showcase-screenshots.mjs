// SH1258: the showcase's screenshots, captured from a running instance rather than cropped by hand.
//
// ## What this writes
//
//   marketplace/quilhara/screenshots/<n>-<name>.jpg     one per page in SHOTS
//
// `scripts/marketplace.mjs` publishes that folder beside the package (SH1253), the catalogue entry
// lists the images, the console shows them before an install, and this site shows the same files.
// So the images change when somebody runs this again against a newer package, and nowhere else.
//
// ## Running it
//
// It needs an instance with the showcase applied, which the build does not have, so it is run by
// hand and its output is committed:
//
//   java -jar shio-app/target/viglet-shio.jar --server.port=2711 \
//     --shio.blueprint.path=<repo>/marketplace --shio.marketplace.enabled=false
//   shio apply --blueprint quilhara --site quilhara --create-site --merge-post-types
//   SHIO_URL=http://localhost:2711 node shio-site/scripts/showcase-screenshots.mjs
//
// ## It refuses a page that does not render cleanly
//
// A 200 is not proof: SH1265 served pages whose every image was broken. A console error, a failed
// request or an image with no pixels stops the run naming the page, because a screenshot of a
// broken page published as the product's showcase is worse than no screenshot.

import { mkdirSync, readFileSync, readdirSync, renameSync, rmSync } from "node:fs";
import { join, resolve } from "node:path";
import { chromium } from "playwright";

const ROOT = resolve(import.meta.dirname, "..", "..");
const MATRIX = JSON.parse(readFileSync(join(ROOT, "docs", "specs", "SH1250-showcase-matrix.json"), "utf8"));
const OUT = join(ROOT, "marketplace", MATRIX.site, "screenshots");
const STAGING = `${OUT}.capturing`;
const BASE = (process.env.SHIO_URL ?? "http://localhost:2711").replace(/\/+$/, "");
const LOCALE = MATRIX.locales[0];

/**
 * The brand pages and the tour's index, in the order the console shows them. Four, because
 * `ShMarketplaceItem.MAX_SCREENSHOTS` keeps no more. Each path must be a page the matrix lists.
 */
const SHOTS = [
  ["home", "/"],
  ["trail", "/trails/black-rocks"],
  ["story", "/stories/the-step-keepers"],
  ["tour", "/tour"],
];

for (const [, path] of SHOTS) {
  if (!(path in MATRIX.pages)) {
    throw new Error(`showcase-screenshots: ${path} is not a page in SH1250-showcase-matrix.json`);
  }
}

const browser = await chromium.launch();
try {
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 }, deviceScaleFactor: 1 });
  const problems = [];
  page.on("console", (message) => {
    if (message.type() === "error") problems.push(`console: ${message.text()}`);
  });
  page.on("requestfailed", (request) => problems.push(`failed: ${request.url()}`));
  page.on("response", (response) => {
    if (response.status() >= 400) problems.push(`${response.status()}: ${response.url()}`);
  });

  // Captured into a staging folder and swapped in only when every page rendered: a run against an
  // instance that is down used to empty the committed screenshots before its first request.
  rmSync(STAGING, { recursive: true, force: true });
  mkdirSync(STAGING, { recursive: true });

  for (const [index, [name, path]] of SHOTS.entries()) {
    problems.length = 0;
    const url = `${BASE}/sites/${MATRIX.site}/default/${LOCALE}${path === "/" ? "/" : path}`;
    await page.goto(url, { waitUntil: "networkidle" });
    await page.evaluate(() => document.fonts.ready);
    const broken = await page.$$eval("img", (images) =>
      images.filter((img) => img.complete && img.naturalWidth === 0).map((img) => img.src));
    problems.push(...broken.map((src) => `broken image: ${src}`));
    if (problems.length) {
      throw new Error(`showcase-screenshots: ${url} did not render cleanly:\n  ${problems.join("\n  ")}`);
    }
    const file = `${index + 1}-${name}.jpg`;
    await page.screenshot({ path: join(STAGING, file), type: "jpeg", quality: 80 });
    process.stdout.write(`[SH1258] ${url} -> ${file}\n`);
  }
  rmSync(OUT, { recursive: true, force: true });
  renameSync(STAGING, OUT);
} finally {
  await browser.close();
  rmSync(STAGING, { recursive: true, force: true });
}

process.stdout.write(`[SH1258] ${readdirSync(OUT).length} screenshot(s) in ${OUT}\n`);
