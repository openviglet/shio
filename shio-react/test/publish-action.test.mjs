/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH654 — the post edit screen can publish, and publishes what is on screen.
 *
 * <h2>The defect this exists to have caught</h2>
 *
 * <p>`post.edit.page.tsx` carried Preview Draft, Translations, History, Schedule, Save,
 * Save & Close and Cancel, and no Publish. `POST /v2/post-unified/{id}/publish` was called
 * from exactly one place in the whole console — the Universal Editor — so a curator editing
 * a page had to leave for the visual editor or wait for an agent run to be approved.
 * Draft-default (P7) rests on publishing being a separate deliberate act by a person, and
 * the screen where that person works could not perform it.
 *
 * <p>The tell was a translated key nothing rendered: `content.publish` existed in both
 * locales. Somebody wrote the label. Nothing failed, because a label with no call site is
 * indistinguishable from a label whose call site is elsewhere.
 *
 * <h2>Why a source read and not a render</h2>
 *
 * <p>This workspace has no React harness (see `field-labels.test.mjs`), and two of the three
 * properties below are not about rendering anyway — they are about which calls the module
 * makes and in which order. The third, that the label is rendered at all, is the one a
 * render test would state better; reading the module is the cheapest honest stand-in, and it
 * fails for the same reason the real thing would: the string is not there.
 */

import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

const PAGE = new URL("../src/app/content/post.edit.page.tsx", import.meta.url);
const SERVICE = new URL("../src/services/post-unified/post-unified.service.ts", import.meta.url);

const page = await readFile(PAGE, "utf8");
const service = await readFile(SERVICE, "utf8");

test("SH654: the edit screen renders the publish labels somebody had already translated", () => {
  assert.match(
    page,
    /t\("content\.publish"\)/,
    "SH654: `content.publish` is translated in both locales; a screen that does not render it "
      + "leaves the Universal Editor as the console's only publish path",
  );
  assert.match(
    page,
    /t\("content\.unpublish"\)/,
    "SH654: taking a page off the site is the same authority and the same screen",
  );
});

test("SH654: the service reaches the two endpoints the server already answers", () => {
  assert.match(
    service,
    /post-unified\/\$\{id\}\/publish/,
    "SH654: nothing is needed on the server — `ShPostUnifiedAPI` answers POST /{id}/publish",
  );
  assert.match(
    service,
    /post-unified\/\$\{id\}\/unpublish/,
    "SH654: and POST /{id}/unpublish, under the same SH107 gate",
  );
});

test("SH654: publish saves the form first, so what goes live is what the curator saw", () => {
  const body = page.slice(page.indexOf("async function handlePublish"));
  const handler = body.slice(0, body.indexOf("\n  }"));

  const update = handler.indexOf("unifiedService.update");
  const publish = handler.indexOf("unifiedService.publish");

  assert.ok(update !== -1, "SH654: the publish handler has to write the draft it is promoting");
  assert.ok(publish !== -1, "SH654: and then promote it");
  assert.ok(
    update < publish,
    "SH654: the server promotes the *stored* draft, so publishing before saving puts a version "
      + "on the site that the curator never saw — the one thing draft-default exists to prevent",
  );
});

test("SH654: Unpublish is offered on a live page, not only on a post with no draft", () => {
  // The button's *own* guard, not any `isLive` in the file: the live badge above it is
  // guarded the same way, and a whole-file match passed with the wrong gate on the button.
  const label = page.indexOf('t("content.unpublish")');
  assert.ok(label !== -1, "the label has to be rendered before its guard can be read");
  // Unpublish is an item of the header's commit menu, so its guard wraps a DropdownMenuItem.
  const guard = page.slice(page.lastIndexOf("{", page.lastIndexOf("<DropdownMenuItem", label)), label);

  assert.match(
    guard,
    /isLive\s*&&/,
    "SH654: a post published and then edited has both rows, so its `state` is DRAFT while the "
      + "site serves it — gating Unpublish on `state === \"PUBLISHED\"` hides the button in "
      + "exactly the case a curator needs it",
  );
  assert.ok(
    !guard.includes('state === "PUBLISHED"'),
    "and the edited row's state is not the live page's existence, however similar they read",
  );
  // SH1158 — the two rows are read by one query and seeded once per post, so the PUBLISHED row's
  // existence is now `loaded.data.live`. Still the row's existence and still read on load.
  assert.match(
    page,
    /setIsLive\(loaded\.data\.live !== null\)/,
    "`isLive` is the PUBLISHED row's existence, read on load",
  );
});
