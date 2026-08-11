/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH478 — which widgets need a vocabulary declared in advance, and which invent their values.
 *
 * <h2>The premise this exists to have caught</h2>
 *
 * <p>SH474 declared four of the seven `data` keys `ShStaticFileService` writes and held back the
 * three the Media Library writes — `ALT`, `CAPTION`, `TAGS`. `TAGS` is an array, and the reason
 * recorded for holding it was that the catalogue's `Multi Select` "reads `widgetSettings` naming
 * the options a curator may pick", which a tag vocabulary does not have in advance. That reason was
 * false of the shipped component: `multi-select.widget.tsx` is a chip input — type a value, press
 * Enter — and it does not mention `widgetSettings` anywhere. `Combo Box` and `Check Box` are the two
 * that do, and the belief was about them.
 *
 * <p>Nothing caught it because the claim was in a comment, and a comment cannot be wrong out loud.
 * The cost was a block: three fields that each needed one line waited on a decision that did not
 * exist, while SH168's `unknown-field` — an ERROR — fired on every File post a curator had
 * captioned.
 *
 * <h2>Why a source read and not a render</h2>
 *
 * <p>This workspace has no React harness (see `field-labels.test.mjs`), and the property is not
 * about rendering anyway. It is about which of two contracts a widget has with the post-type that
 * declares it: <b>closed</b>, where the declaration must carry the values, or <b>open</b>, where the
 * curator supplies them. That is a fact about the module's inputs, and reading the modules is the
 * cheapest honest way to state it. If `Multi Select` ever becomes option-driven, the `TAGS`
 * declaration in `ShStaticFileService.fileFields` silently becomes a field that renders as a select
 * nobody can select from — and this test fails first, naming it.
 */

import assert from "node:assert/strict";
import { readFile, readdir } from "node:fs/promises";
import { test } from "node:test";

const WIDGET_DIR = new URL("../src/components/widgets/", import.meta.url);

/**
 * The widgets whose values a post-type must declare, and which therefore cannot model a free-form
 * list. Held as a named list rather than derived from the grep alone: the grep says which modules
 * mention the key, and the *decision* — that this is the complete set of closed widgets — is what a
 * reader needs and what a future change has to argue with.
 */
const CLOSED = ["checkbox.widget.tsx", "combobox.widget.tsx"];

/**
 * Modules that read `widgetSettings` for something other than a value vocabulary, so a mention in
 * them says nothing about whether the widget is open or closed. `content-select` uses it to scope
 * which posts may be picked and `file` to scope a folder; neither enumerates the value.
 */
const NOT_A_VOCABULARY = ["content-select.widget.tsx", "file.widget.tsx"];

async function widgetSources() {
  const names = (await readdir(WIDGET_DIR)).filter((name) => name.endsWith(".widget.tsx"));
  const sources = new Map();
  for (const name of names) {
    sources.set(name, await readFile(new URL(name, WIDGET_DIR), "utf8"));
  }
  return sources;
}

test("SH478: Multi Select invents its values, so it can model a vocabulary nobody has yet", async () => {
  const sources = await widgetSources();
  const multiSelect = sources.get("multi-select.widget.tsx");

  assert.ok(multiSelect, "the widget the File type declares TAGS with has to exist to be read");
  assert.ok(
    !multiSelect.includes("widgetSettings"),
    "SH478: `Multi Select` reading an option list would make `ShStaticFileService.fileFields`' "
      + "TAGS declaration a select a curator cannot select from — declare it differently, or "
      + "change this test on purpose",
  );
  assert.match(
    multiSelect,
    /arrayValue/,
    "and it is the array-shaped widget, which is what `data.TAGS` holds",
  );
});

test("SH478: the closed widgets are exactly the two, and a mention is not a vocabulary", async () => {
  const sources = await widgetSources();
  const mentions = [...sources.entries()]
    .filter(([, body]) => body.includes("widgetSettings"))
    .map(([name]) => name)
    .sort();

  assert.deepEqual(
    mentions.filter((name) => !NOT_A_VOCABULARY.includes(name)),
    CLOSED,
    "a third option-driven widget is a real change to what a post-type declaration must carry, and "
      + "it lands here before it lands in somebody's model",
  );
  assert.ok(
    sources.size > CLOSED.length,
    "the reader is not vacuous: it read more widgets than the list it is checking",
  );
});
