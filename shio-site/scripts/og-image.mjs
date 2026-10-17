// Rasterize public/og.svg -> dist/og.png for social-share cards.
//
// X / LinkedIn / Slack / Facebook do NOT render an SVG og:image, so we ship a
// real PNG. This runs after the Vite build (see package.json > build).
//
// Defensive by design: if the optional `@resvg/resvg-js` dependency is not
// installed (a bare local build, or a platform it has no prebuilt binary for),
// we warn and skip WITHOUT failing the build; Vite still copies the SVG into
// dist/ as a fallback. CI installs the dep, so the deployed site gets the PNG.

import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { resolve } from "node:path";

const svgPath = resolve(process.cwd(), "public", "og.svg");
const outPath = resolve(process.cwd(), "dist", "og.png");

if (!existsSync(svgPath)) {
  console.warn(`og-image: ${svgPath} not found; skipping.`);
  process.exit(0);
}

let Resvg;
try {
  ({ Resvg } = await import("@resvg/resvg-js"));
} catch {
  console.warn(
    "og-image: @resvg/resvg-js not installed; skipping PNG rasterization " +
      "(dist/og.svg still ships as a fallback)."
  );
  process.exit(0);
}

const svg = readFileSync(svgPath, "utf-8");
const resvg = new Resvg(svg, {
  fitTo: { mode: "width", value: 1200 },
  font: { loadSystemFonts: true },
});
const png = resvg.render().asPng();
writeFileSync(outPath, png);
console.log(`og-image: wrote dist/og.png (${(png.length / 1024).toFixed(0)} KB)`);
