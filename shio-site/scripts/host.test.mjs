// @scans prose reason: the subject is the host spelled in config, static files and the prerender, read as text (SH1359).
//
// SH1359 — the site is served at the root of shio.viglet.org, not under /shio/ on a github.io host.
//
// It moved to openviglet.github.io/shio on 2026-10-29 and back on 2026-11-05, because a custom
// domain carries more search authority than a path on a shared host. The host is spelled in four
// places that must agree, and a disagreement between any two publishes a site whose canonicals,
// assets or docs links point somewhere it is not.

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

const HERE = process.cwd();
const read = (path) => readFileSync(join(HERE, path), "utf8");

test("SH1359: CNAME, vite base, docs base and prerender host all name the root of shio.viglet.org", () => {
  assert.equal(read("public/CNAME").trim(), "shio.viglet.org", "SH1359: public/CNAME must name the custom domain");
  assert.match(read("vite.config.ts"), /base:\s*"\/"/, "SH1359: vite.config.ts must serve the site at the root");
  const astro = read("docs/astro.config.mjs");
  assert.match(astro, /const BASE = "\/docs";/, "SH1359: the docs area's base is /docs");
  assert.match(astro, /site:\s*"https:\/\/shio\.viglet\.org"/, "SH1359: the docs area's site is shio.viglet.org");
  const prerender = read("scripts/prerender.mjs");
  assert.match(prerender, /const SITE_HOST = "https:\/\/shio\.viglet\.org";/, "SH1359: canonicals use shio.viglet.org");
  assert.match(prerender, /const BASE = "";/, "SH1359: the prerender's base is empty at the root");
  for (const file of ["public/robots.txt", "public/sitemap.xml", "public/llms.txt", "index.html"]) {
    assert.doesNotMatch(read(file), /openviglet\.github\.io\/shio/, `SH1359: ${file} still names the old host`);
  }
});
