/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

import { deleteEach, problemOf, refusalSummary } from "../src/lib/batch-delete.ts";

/**
 * SH953 — a count is the number acted on, not the number selected.
 *
 * `object.children.page.tsx` looped the selection, awaited each `DELETE` with `.catch(() => {})`,
 * and then announced `content.itemsDeleted` with `selectedItems.size`. The select-all cap is a
 * thousand (SH237), so **a thousand refused deletes reported a thousand deleted** — and the folder
 * write service has real refusals, restore conflicts and 409s, none of which could reach the
 * curator through that loop.
 *
 * The outcome is asserted here rather than through a rendered dialog: the page is a dialog, a toast
 * and a refresh, and a test that renders all three to find out whether a number is right is a test
 * nobody keeps. What was wrong was the arithmetic and the swallowing, and both are in this module.
 */

/** A delete that refuses the ids named, with the shape Shio's teaching errors actually have. */
const refusing = (refused, problem) => async (target) => {
  if (refused.includes(target.id)) {
    throw { response: { data: problem } };
  }
  return { status: 204 };
};

const targets = [
  { id: "a", name: "About", objectType: "POST" },
  { id: "b", name: "Blog", objectType: "FOLDER" },
  { id: "c", name: "Contact", objectType: "POST" },
];

test("SH953: a batch in which one delete is refused reports the successful count", async () => {
  const outcome = await deleteEach(targets, refusing(["b"], {
    title: "Folder is not empty",
    detail: "Move or delete its 12 children first.",
  }));

  assert.equal(outcome.deleted, 2,
    "SH953: the number announced must be the number that actually went. Reporting the selection "
    + "size is how a thousand refusals came to read as a thousand deletions.");
  assert.equal(outcome.attempted, 3);
  assert.notEqual(outcome.deleted, outcome.attempted,
    "SH953: the control on the assertion above — if these were equal the test would pass on the "
    + "very bug it is about");
});

test("SH953: the refused item is named, in the words the server used", async () => {
  const outcome = await deleteEach(targets, refusing(["b"], {
    title: "Folder is not empty",
    detail: "Move or delete its 12 children first.",
  }));

  assert.equal(outcome.refusals.length, 1);
  assert.deepEqual(
    { id: outcome.refusals[0].id, name: outcome.refusals[0].name },
    { id: "b", name: "Blog" },
    "SH953: a curator cannot act on 'something failed' — the row has to be nameable");
  assert.equal(outcome.refusals[0].title, "Folder is not empty");
  assert.equal(outcome.refusals[0].detail, "Move or delete its 12 children first.",
    "SH953: P6 — the detail is the instruction, and it is the half that says what to do next");
});

test("SH953: every refusal is collected, not just the first", async () => {
  // The loop is sequential and a throw used to be swallowed; a fix that stopped at the first
  // refusal would be a different wrong answer, and the batch's whole point is that it continues.
  const outcome = await deleteEach(targets, refusing(["a", "b", "c"], { title: "Denied" }));

  assert.equal(outcome.deleted, 0);
  assert.deepEqual(outcome.refusals.map((refusal) => refusal.id), ["a", "b", "c"]);
});

test("SH953: a batch that all succeeds says so, and reports nothing refused", async () => {
  const outcome = await deleteEach(targets, async () => ({ status: 204 }));

  assert.equal(outcome.deleted, 3);
  assert.deepEqual(outcome.refusals, []);
});

test("SH953: a rejection with no problem document still yields something true", () => {
  // A network failure has no `response`, and `message` is then the only honest thing available.
  assert.deepEqual(problemOf(new Error("Network Error")), { title: "Network Error" });
  assert.deepEqual(problemOf({}), { title: "Request failed" });
  assert.deepEqual(
    problemOf({ response: { data: { title: "Conflict", detail: "A live post holds that URL." } } }),
    { title: "Conflict", detail: "A live post holds that URL." },
  );
  // Spring's plain error body has `message` and no `title`; reading it is better than "Request
  // failed" over a server that did say something.
  assert.deepEqual(problemOf({ response: { data: { message: "Access denied" } } }),
    { title: "Access denied" });
});

test("SH953: a long list of refusals is capped and says how many it is not showing", () => {
  const many = Array.from({ length: 7 }, (_, i) => ({ id: `${i}`, name: `Row ${i}`, title: "Denied" }));

  const summary = refusalSummary(many);

  assert.match(summary, /Row 0: Denied/);
  assert.match(summary, /\+4 more/,
    "SH953: a thousand refusals is a toast nobody reads — but a truncated list that does not say "
    + "it is truncated reads as the whole of what went wrong");
  assert.equal(refusalSummary([{ id: "1", name: "One", title: "Denied" }]), "One: Denied");
});

/**
 * The page's own half, which the module cannot hold: the toast must be built from the outcome,
 * and the refresh must return to the page the curator was on.
 */
test("SH953: the page reports the outcome's count and refreshes where it was", () => {
  const page = readFileSync(new URL("../src/app/console/content/object.children.page.tsx", import.meta.url), "utf8");

  assert.match(page, /count:\s*outcome\.deleted/,
    "SH953: the toast must count what went, not `selectedItems.size` — that substitution IS the "
    + "defect, and it reads as correct at the call site");
  assert.doesNotMatch(page, /itemsDeleted",\s*\{\s*count:\s*selectedItems\.size/,
    "SH953: the selection's size must never again be announced as the number deleted");
  assert.match(page, /fetchPage\(listData\.page/,
    "SH953: a batch delete on page 14 used to answer by scrolling the curator back to page zero, "
    + "which hides whichever rows were refused");
  assert.doesNotMatch(page, /deleteObject\([^)]*\)\.catch\(\(\) => \{\}\)/,
    "SH953: an empty catch on a delete is the swallowing this task removed");
});
