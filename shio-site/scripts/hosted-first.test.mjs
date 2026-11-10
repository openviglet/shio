// SH1432 — the hosted way first. The site and the README led with `docker run`, so a reader who is
// not a developer left at the first step, and the free hosted Viglet Shio on viglet.cloud was not
// mentioned at the top. Source lints, like `site-claims.test.mjs` beside them.

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const read = (path) => readFileSync(fileURLToPath(new URL(path, import.meta.url)), "utf8");
const SIGNUP = "https://console.viglet.cloud/?register=1&product=shio";

test("SH1432: the hero offers the hosted sign-up before the install", () => {
  const hero = read("../src/components/sections/hero.tsx");
  const hosted = hero.indexOf("LINKS.hosted");
  const install = hero.indexOf("LINKS.install");
  assert.ok(hosted > 0, "SH1432: the hero does not offer viglet.cloud");
  assert.ok(install > hosted, "SH1432: the install leads the hero again, before the hosted sign-up");
  assert.match(read("../src/lib/destinations.ts"), new RegExp(SIGNUP.replace(/[?.]/gu, "\\$&")),
    "SH1432: the hosted destination is not the viglet.cloud sign-up for Viglet Shio");
});

test("SH1432: the README's first steps name viglet.cloud before docker run", () => {
  const readme = read("../../README.md");
  const hosted = readme.indexOf(SIGNUP);
  const docker = readme.indexOf("docker run");
  assert.ok(hosted > 0, "SH1432: the README does not offer the hosted sign-up");
  assert.ok(docker > hosted, "SH1432: the README leads with docker run again");
});

test("SH1432: the showcase ends with the same two calls", () => {
  const showcase = read("../src/pages/showcase.tsx");
  assert.ok(showcase.indexOf("LINKS.hosted") > 0 && showcase.indexOf("LINKS.hosted") < showcase.indexOf("SHOWCASE_RUN}"),
    "SH1432: the showcase offers docker run without the hosted way first");
});
