/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH664 — the console-check fixture site must not declare a front end of its own.
 *
 * <h2>The defect this exists to have caught</h2>
 *
 * <p>`provision.mjs` created the fixture with `url: API` — the instance's own origin — from the
 * commit that introduced the file, written before SH183/SH184 made an empty `url` fall back to
 * Shio's renderer. Two consequences, and neither of them fails anything:
 *
 * <ul>
 *   <li>`siteDestination` reads a non-empty `url` as an <b>external front end</b>, so the console's
 *       open-site link and the Universal Editor's iframe both point at the instance. `GET /` answers
 *       302 to `/login`, so the editor framed the console's login page inside the console.</li>
 *   <li>`checkEditorDrive` excludes every site with a `url` <em>before</em> it asks the renderer
 *       anything, so the one site CI provisions on purpose was never the drive's target. The probe
 *       skipped honestly and the handshake, the panel and the field round trip stayed unexercised.</li>
 * </ul>
 *
 * <h2>Why the two files are read together</h2>
 *
 * <p>Neither half is wrong alone. The fixture may legitimately declare a URL for some other probe,
 * and the drive's exclusion is correct — an external front end really is a page this instance does
 * not serve. What is wrong is the pair, and a test that read only one of them would be asserting a
 * preference rather than a coupling. So this reads the exclusion out of `console-check.mjs` and the
 * declaration out of `provision.mjs`, and fails if they ever line up again.
 */

import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

const PROVISION = new URL("../e2e/provision.mjs", import.meta.url);
const CHECK = new URL("../e2e/console-check.mjs", import.meta.url);

const provision = await readFile(PROVISION, "utf8");
const check = await readFile(CHECK, "utf8");

/** The body of `createSite`'s POST — the only place the fixture's own fields are declared. */
function createSiteBody(source) {
  const start = source.indexOf("async function createSite");
  assert.notEqual(start, -1, "createSite has to exist for its payload to be read");
  const body = source.slice(start);
  const open = body.indexOf("JSON.stringify({");
  assert.notEqual(open, -1, "createSite has to POST a JSON body");
  return body.slice(open, body.indexOf("}),", open));
}

test("SH1144: the fixture's blueprint is applied with the merge a clean instance needs", () => {
  const start = provision.indexOf("async function applyBlueprint");
  assert.notEqual(start, -1, "SH1144: applyBlueprint moved, and this test lost the call it guards");
  const call = provision.slice(start, provision.indexOf("\n}\n", start));

  assert.match(call, /\/api\/v2\/agent\/blueprints\/\$\{BLUEPRINT\}\/apply/u);
  assert.match(call, /mergePostTypes: true/u,
    "SH1144: a clean instance's bootstrap site declares `Page` with title and content only, and "
      + "marketing-site's `Page` adds description and sections. Without SH152's merge the apply keeps "
      + "the existing type and refuses the content (`400 undeclared-field`), so every check that "
      + "provisions first walks a site with no pages on exactly the instance CI starts from");
});

test("SH1176: the provisioning line reads the change count where the apply puts it", () => {
  const start = provision.indexOf("async function applyBlueprint");
  const call = provision.slice(start, provision.indexOf("\n}\n", start));
  // `ShBlueprintApplied` carries the count in its content half, an `ShAgentChangeSet`. Read off the
  // top level it was absent on every run, and `changed=?` said the same for a whole site as for none.
  assert.match(call, /applied\.content\?\.changed/u,
    "SH1176: the change count is read off the top of the apply response, where it never is");
  assert.doesNotMatch(call, /applied\.changed\b/u,
    "SH1176: `applied.changed` is back, and it is always undefined");
});

test("SH664: the fixture site declares no front end of its own", () => {
  const body = createSiteBody(provision);

  assert.ok(
    !/^\s*url\s*:/m.test(body),
    "SH664: a `url` on the console-check fixture makes the Universal Editor frame an external "
      + "front end. It was the instance itself, so the editor framed `GET /` — a 302 to the "
      + "console's own login page. Empty is what a site an agent just built looks like",
  );
  assert.match(
    body,
    /furl\s*:/,
    "the fixture still needs its own address; only the front-end destination is dropped",
  );
});

test("SH659: the publish guard's marker is the one the provisioner writes", () => {
  // The two halves of one fact, in two files. `console-check.mjs` may publish only a site this
  // repository created, and the only evidence of that is the description `provision.mjs` sets.
  // If either string moves, publishing turns itself off — and a probe that quietly stops
  // publishing reads exactly like one that published, which is SH667's whole subject.
  const declared = /const PROVISIONED = "([^"]+)"/.exec(check);
  assert.ok(declared, "SH659: the drive has to name the marker it recognises");

  assert.ok(
    provision.includes(`"Site the console smoke check walks"`)
      || provision.includes(declared[1]),
    `SH659: \`provision.mjs\` no longer writes ${JSON.stringify(declared[1])} as the fixture's `
      + "description, so no site will ever match and the publish step silently stops running",
  );
  assert.match(
    check,
    /site\?\.description === PROVISIONED/,
    "SH659: and it is matched on the description, not the name — `--site` is whatever the caller "
      + "passed, so matching that would let `console:check --site Demo` publish somebody's home page",
  );
});

test("SH664: and the drive really does exclude a site that has one", () => {
  // Not decoration. If `checkEditorDrive` stops skipping url-bearing sites, the assertion above
  // is guarding a coupling that no longer exists, and it should be reconsidered rather than kept
  // passing out of habit.
  assert.match(
    check,
    /if \(site\.url && site\.url\.trim\(\)\) continue/,
    "SH664: the exclusion is what makes the fixture's `url` matter — read here rather than "
      + "assumed, so this test fails loudly if the drive's rule changes instead of silently "
      + "asserting a preference",
  );
});
