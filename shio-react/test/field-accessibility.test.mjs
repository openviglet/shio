/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

import { stripComments } from "./support/source-text.mjs";

/**
 * SH1052 — a field with a name, a state, and a way in from the keyboard.
 *
 * ## What every field of every post type used to be
 *
 * A `<Label>` with no `htmlFor`, then a widget whose props were `attr`, `onChange` and `postId`.
 * Nothing in that contract could carry an id, so clicking a label focused nothing and a screen
 * reader met each field as an **unlabelled edit box** — not one form, not one post type: all of
 * them, because this is the component every post is edited through.
 *
 * Three smaller things went with it. The required marker was a bare `*`, which is punctuation and
 * is announced as nothing. The description was an orphan paragraph no control pointed at. And the
 * File widget's drop zone was a `div` with an `onClick` over an `<input type="file">` hidden with
 * `display: none` — so there was no keyboard path to attach a file at all.
 *
 * ## Why a source rule, when a browser could see it
 *
 * `e2e/console-check.mjs` could render a post and read the accessibility tree, and SH1054 is that
 * task. What a scan does that a probe cannot is fail on the **widget that has not been written
 * yet**: the map takes a component, a new one arrives with the old three props, and the defect is
 * back for one post type with every existing test green. So this walks the map.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const WIDGETS = join(ROOT, "src", "components", "widgets");

const read = (...path) => readFileSync(join(ROOT, ...path), "utf8");
const code = (...path) => stripComments(read(...path));

/**
 * The three questions this file asks, as functions of a widget's source.
 *
 * <p>Pulled out so they can be driven against a planted widget rather than only against a tree that
 * satisfies them — every check below passed on its first run, which is exactly when a check has not
 * been shown to check anything.</p>
 */
export const threadsId = (source) => /id=\{fieldId\}/u.test(source);
export const namesItself = (source) => /aria-labelledby=\{labelledBy\}/u.test(source);
export const pointsAtProse = (source) => /aria-describedby=\{describedBy\}/u.test(source);
export function hiddenControls(source) {
  return [...source.matchAll(/<(input|select|textarea|button)\b[^>]*/gu)]
    .map(([control]) => control)
    .filter((control) => /className="[^"]*\bhidden\b/u.test(control));
}

/** Every widget file the map can reach, by its own name. */
function widgetFiles() {
  return readdirSync(WIDGETS)
    .filter((name) => name.endsWith(".widget.tsx") || name === "ace-editor.lazy.tsx")
    .sort();
}

/**
 * A widget with no control of its own, which names itself by the field's label instead.
 *
 * <p>Not an excuse list — it is the other half of the contract. `htmlFor` points at one element,
 * and a widget that renders a group of checkboxes, a code editor with its own focusable surface, or
 * a set of rows has no single element for it to point at. Those take `labelledBy` and put it on a
 * `role="group"`; pointing a label at one of several controls would focus an arbitrary one.</p>
 */
const COMPOSITE = new Set([
  "ace-editor.widget.tsx",
  "blocks.widget.tsx",
  "checkbox.widget.tsx",
  "content-select.widget.tsx",
  "file.widget.tsx",
  "relator.widget.tsx",
]);

/**
 * Widgets that take neither, and why each one does not.
 *
 * <p>Both are real, and both would be wrong to give an id. A reader meeting this list should be
 * able to tell in one sentence why a new entry does not belong on it.</p>
 */
const NO_CONTROL = new Map([
  ["hidden.widget.tsx", "an <input type=\"hidden\">: it is not focusable, it is not announced, and "
    + "the form does not draw a label for it at all"],
  ["tab.widget.tsx", "a heading and a rule — it is a section break in the form, not a field, and "
    + "the schema uses it to group the ones around it"],
  ["ace-editor.lazy.tsx", "a Suspense boundary that forwards {...props} to the editor, so the "
    + "widget it defers to is where the contract is kept"],
]);

test("SH1052: every widget takes the field's id, or names itself by its label", () => {
  const files = widgetFiles();

  // The vacuity guard: a directory walk that stopped matching would pass this file in silence,
  // which is the same failure as the drift it exists to catch.
  assert.ok(files.length >= 14,
    `SH1052: only ${files.length} widget file(s) found — this scan is measuring nothing`);

  const mute = [];
  for (const file of files) {
    if (NO_CONTROL.has(file)) continue;
    const source = code("src", "components", "widgets", file);
    if (!(COMPOSITE.has(file) ? namesItself(source) : threadsId(source))) mute.push(file);
  }

  assert.deepEqual(mute, [],
    "SH1052: these widgets take the field's id from the contract and do not put it anywhere, so "
    + "the label above them points at nothing: clicking it focuses nothing and a screen reader "
    + "announces an unlabelled edit box. Put `id={fieldId}` on the control, or — if this widget "
    + "renders several and has no single control — `aria-labelledby={labelledBy}` on a "
    + "`role=\"group\"` and add it to COMPOSITE with that as the reason.");
});

test("SH1052: a widget that has a control also carries its state", () => {
  // The half that is easy to leave half-done: an id makes the field *named*, and `describedby`,
  // `required` and `invalid` are what make it say anything about itself. A field whose description
  // is an orphan paragraph reads as a box with no rules.
  const missing = [];
  for (const file of widgetFiles()) {
    if (NO_CONTROL.has(file)) continue;
    const source = code("src", "components", "widgets", file);
    if (!pointsAtProse(source)) missing.push(file);
  }

  assert.deepEqual(missing, [],
    "SH1052: these widgets never point at their own description, so the sentence under the box — "
    + "its description, its constraint hint, the server's refusal — is read by nobody using a "
    + "screen reader. `aria-describedby={describedBy}`, and the form passes `undefined` when there "
    + "is no prose, because an id pointing at a missing element is announced as nothing.");
});

test("SH1052: no form control is hidden with display:none", () => {
  // The File widget's own defect, generalised. Tailwind's `hidden` IS `display: none`, which takes
  // an element out of the accessibility tree AND out of the focus order — so an input hidden that
  // way cannot be reached by keyboard at all, and the visible thing above it was a div with an
  // onClick. `sr-only` is the class that means "not shown, still reachable".
  const offenders = [];
  for (const file of widgetFiles()) {
    const source = code("src", "components", "widgets", file);
    for (const control of hiddenControls(source)) {
      offenders.push(`${file}: ${control.slice(0, 60)}`);
    }
  }

  assert.deepEqual(offenders, [],
    "SH1052: a form control with `className=\"hidden\"` is `display: none` — out of the "
    + "accessibility tree and out of the focus order, so nobody using a keyboard can reach it. If "
    + "it must not be seen and must still be usable, that is `sr-only`. This is the File widget's "
    + "own defect: an input hidden this way under a div with an onClick gave no way to attach a "
    + "file without a mouse.");
});

test("SH1052: the form names the control, and says `required` in a word", () => {
  const form = code("src", "components", "widgets", "post-form-fields.tsx");

  assert.match(form, /htmlFor=\{ids\.control\}/u,
    "SH1052: the label owns no control again. This is the defect: a `<Label>` with no `htmlFor` "
    + "over a widget that cannot take an id, on every field of every post type.");
  assert.match(form, /<span aria-hidden="true" className="text-destructive">\*<\/span>/u,
    "SH1052: the asterisk is punctuation and a screen reader says nothing for it, so it has to be "
    + "hidden and the word said beside it");
  assert.match(form, /<span className="sr-only">\{t\("content\.fieldRequired"\)\}<\/span>/u,
    "SH1052: and the word has to come from the bundles, or the form is English for everybody");
  assert.match(form, /required=\{Boolean\(field\.required\)\}/u,
    "SH1052: `aria-required` on the control is the other half — the glyph is what a sighted reader "
    + "sees, that is what an assistive one hears, and a field carrying only one told half the "
    + "readers nothing");
});

test("SH1052: the id scheme is one function, and it is deterministic", () => {
  const ids = code("src", "lib", "field-ids.ts");

  assert.match(ids, /export function fieldIds/u);
  assert.doesNotMatch(ids, /useId\(/u,
    "SH1052: `useId()` mints a value that differs between renders of different trees, so two "
    + "renders of the same post would differ in their markup — and a render digest (SH687) "
    + "compares markup. The field name is already unique within a post type.");
  assert.doesNotMatch(ids, /Math\.random|Date\.now/u,
    "SH1052: an id that is not a function of the field name is one the label and the control can "
    + "disagree about on a re-render");

  // Both halves of the contract are asserted against the same file, so the scheme cannot be split
  // between two definitions again — which is how it came to be exported from a component file.
  assert.match(code("src", "components", "widgets", "post-form-fields.tsx"),
    /from "@\/lib\/field-ids"/u,
    "SH1052: the form mints the ids from the shared module, or there are two schemes");
  assert.match(code("src", "app", "content", "post.edit.page.tsx"), /from "@\/lib\/field-ids"/u,
    "SH1052: and the page that scrolls to a field resolves the same spelling");
});

test("SH1052: the two widgets that restated the contract use it", () => {
  // The finding this task turned up rather than the one it was filed for. `blocks` and `relator`
  // declared their own `Props` with the three fields `WidgetProps` had, so widening the contract
  // widened everything except the two components that had copied it — and they are the two most
  // complex widgets in the console.
  for (const file of ["blocks.widget.tsx", "relator.widget.tsx"]) {
    const source = code("src", "components", "widgets", file);
    assert.match(source, /type Props = WidgetProps/u,
      `SH1052: ${file} declares its own props again. A hand-written copy of the shared contract is `
      + "why these two were the only widgets the accessibility props did not reach, and the next "
      + "field added to `WidgetProps` would miss them the same way.");
  }
});

test("SH1052: a widget written the old way fails each rule by name", () => {
  // Driven, because every rule above passed on its first run. A check that has only ever been asked
  // of source that satisfies it has not been shown to check anything — and the thing this file is
  // guarding against is a widget nobody has written yet.
  const old = '<Input value={attr.strValue} onChange={(e) => onChange(e)} />';
  const fixed = '<Input id={fieldId} aria-describedby={describedBy} value={attr.strValue} />';

  assert.equal(threadsId(old), false, "a widget that ignores `fieldId` must be named");
  assert.equal(threadsId(fixed), true);
  assert.equal(pointsAtProse(old), false, "and one that points at no description too");
  assert.equal(pointsAtProse(fixed), true);
  assert.equal(namesItself('<div role="group" aria-labelledby={labelledBy}>'), true);
  assert.equal(namesItself('<div role="group">'), false);

  assert.deepEqual(hiddenControls('<input type="file" className="hidden" />').length, 1,
    "SH1052: `className=\"hidden\"` on a control is the File widget's own defect, and the rule "
    + "has to see it in a string it was not handed by the tree it already passes on");
  assert.deepEqual(hiddenControls('<input type="file" className="sr-only" />'), [],
    "and `sr-only` — not shown, still reachable — is the shape that is correct");
  assert.deepEqual(hiddenControls('<div className="hidden" />'), [],
    "SH1052: a hidden DIV is not a control and is not this rule's business; flagging it would "
    + "make the rule noise, which is how a rule gets turned off");
});
