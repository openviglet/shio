/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH528 — the catalogue of what a real site does that breaks a replica, kept offline.
 *
 * ## Why the fixture needed to grow
 *
 * Every defect this work found came from a real site, and none of them could be reproduced without
 * one. The fixture was 134 lines — a page, a stylesheet, a webfont, an image, a favicon — and real
 * sites broke the replica with traits it did not have.
 *
 * A real site is how a defect is *found*: `viglet.org` had a consent banner and a React canvas
 * nobody planned. It is a bad way to *keep* one. The site changes, the network fails, and a red run
 * then means nothing until somebody works out which of the three it was — so a finding kept only in
 * a live source is a finding that decays into noise.
 *
 * ## What this file is, and what it is not
 *
 * It is not a test of the replication chain; `replication-chain.test.mjs` is that, and the
 * conformance suite is the same question over a socket. This asserts the **fixture itself exhibits
 * each trait**, so a future edit cannot quietly remove the one shape a defect needed. A catalogue
 * whose entries can vanish without a failure is a list, not a catalogue.
 *
 * Each entry names the task that needed it. The order follows SH527's list: a trait exists because
 * a defect required it, never because a site happened to have it.
 */
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

import { scanPage } from "../src/clone.mjs";
import { serveFixture } from "../conformance/support/fixture-site.mjs";

/** Fetches one path off the running fixture and scans it the way `clone` does. */
async function scan(origin, path) {
  const url = `${origin}${path}`;
  const response = await fetch(url);
  return { response, doc: scanPage(await response.text(), url) };
}

test("SH528: the fixture exhibits every trait that broke a real replica", async (t) => {
  const source = await serveFixture();
  t.after(() => source.close());

  await t.test("SH476/SH565: a `&` escaped twice, in the title a scanner reads first", async () => {
    const { doc } = await scan(source.origin, "/traits");
    // The trait was pinned here as it behaved — `Terms &amp;amp; Conditions`, decoded neither time
    // — precisely so SH565 would have something offline to turn green. It has, so this now pins the
    // cure, and the source bytes it is read from are unchanged: the catalogue's job is to keep the
    // awkward input, not the wrong output.
    //
    // Decoded ONCE. `&amp;amp;` is an author escaping an ampersand-entity deliberately, so the
    // correct reading is the literal text `&amp;`; a second pass would yield `&` and lose the
    // distinction the author was drawing (`decodeEntities` is single-pass for that reason).
    assert.equal(doc.title, "Terms &amp; Conditions",
      "decoded once — twice would be `Terms & Conditions`, which is a different title from the one "
        + "the source wrote (SH565)");
  });

  await t.test("SH507: a query string on an asset path", async () => {
    const { doc } = await scan(source.origin, "/traits");
    // `from` says where the reference was found (markup, css, script); the href is `url`.
    const busted = doc.assets.find((asset) => String(asset.url).includes("hero.png?v=2"));
    assert.ok(busted,
      `the cache-busted image is a reference like any other: ${JSON.stringify(doc.assets)}`);
    assert.equal(busted.kind, "image",
      "and the query string does not stop it being recognised as one — a file name that swallowed "
        + "the `?v=2` is the shape that wrote an asset nobody could serve");
  });

  await t.test("SH514: a zero-byte file behind a 200 and an image content-type", async () => {
    const response = await fetch(`${source.origin}/img/empty.png`);
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("content-type"), "image/png");
    assert.equal((await response.arrayBuffer()).byteLength, 0,
      "nothing in the response says it is empty, which is exactly how the real one arrived and why "
        + "the upload refused it somewhere far from here");
  });

  await t.test("SH512: a reference resolved four segments below its source", async () => {
    const { doc } = await scan(source.origin, "/docs/guide/intro/detail/");
    const logo = doc.assets.map((asset) => String(asset.url)).join(" ");
    assert.match(logo, /\/docs\/guide\/logo\.svg/,
      "`./../../logo.svg` from /docs/guide/intro/detail/ is /docs/guide/logo.svg — against the "
        + "origin it would be /logo.svg and against the page's own directory /docs/guide/intro/, "
        + "three different files of which only one exists");
    const served = await fetch(`${source.origin}/docs/guide/logo.svg`);
    assert.equal(served.status, 200, "and the one that exists is the one the depth resolves to");
  });

  await t.test("SH505: a fixed overlay, first in the source", async () => {
    const css = await (await fetch(`${source.origin}/css/traits.css`)).text();
    assert.match(css, /position:\s*fixed/,
      "a consent banner is `position: fixed` and first in the markup, which is the position every "
        + "scanner reads as the lead — it became three sections' `intro`");
    const html = await (await fetch(`${source.origin}/traits`)).text();
    assert.ok(html.indexOf('class="consent"') < html.indexOf("<main>"),
      "and it precedes <main>, or the trait is not the one that caused the defect");
  });

  await t.test("SH517: a canvas that exists only after a script runs", async () => {
    const html = await (await fetch(`${source.origin}/traits`)).text();
    assert.match(html, /createElement\("canvas"\)/);
    assert.ok(!/<canvas/i.test(html),
      "absent from the bytes, so a plain fetch and `--render` disagree about this page — which is "
        + "the whole trait: --render baked one dead and nothing said so");
  });

  await t.test("a page under a locale prefix, and a link that is wrong", async () => {
    assert.equal((await fetch(`${source.origin}/en-us/privacy`)).status, 200,
      "a site whose real content sits under a locale prefix is the shape SH433/SH576 are about");
    assert.equal((await fetch(`${source.origin}/gone`)).status, 404,
      "and one link that resolves to nothing, so a broken reference is reproducible offline");
  });
});

test("SH528: the two fixtures stay in step", async () => {
  // Their shared header says they should differ in WHERE the bytes come from and nowhere else, so
  // a divergence is a statement about the boundary rather than about the fixture. Nothing enforced
  // that, and the socket one has been edited alone before.
  const socket = await readFile(new URL("../conformance/support/fixture-site.mjs", import.meta.url), "utf8");
  const injected = await readFile(new URL("./replication-chain.test.mjs", import.meta.url), "utf8");

  for (const path of ["/traits", "/docs/guide/intro/detail/", "/docs/guide/logo.svg",
    "/en-us/privacy", "/img/empty.png", "/css/traits.css"]) {
    assert.ok(socket.includes(path), `the socket fixture lost ${path}`);
    assert.ok(injected.includes(path), `the injected-fetch twin never gained ${path}`);
  }
  for (const trait of ["&amp;amp;", "hero.png?v=2", "./../../logo.svg", 'createElement("canvas")',
    "position: fixed"]) {
    assert.ok(socket.includes(trait), `the socket fixture lost the trait ${trait}`);
    assert.ok(injected.includes(trait), `the injected-fetch twin lost the trait ${trait}`);
  }
});
