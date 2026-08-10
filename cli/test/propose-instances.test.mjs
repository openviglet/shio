import assert from "node:assert/strict";
import { test } from "node:test";

import {
  describeInstanceProposal,
  kindOf,
  proposeFromInstances,
} from "../src/propose-instances.mjs";

/**
 * SH525 — modelling by example, pointed inward.
 *
 * `propose` derived a post-type from a stranger's markup and had no way to answer the same question
 * about content the reader wrote. What these assertions hold is the two things the conversion
 * version does not need: **which instances disagree**, because a field two of three share is a
 * different proposal from one all three do; and **the honest limit**, because three instances of a
 * thing are evidence about those three.
 */

const product = (address, extra = {}) => ({
  address,
  data: { TITLE: "A product", SUMMARY: "One line", ...extra },
});

test("SH525: a field every instance carries is proposed as required", () => {
  const proposal = proposeFromInstances(
    [product("post:acme/a"), product("post:acme/b"), product("post:acme/c")],
    "Product",
  );

  assert.equal(proposal.fields.length, 2);
  assert.deepEqual(
    proposal.fields.map((field) => [field.name, field.kind, field.required]),
    [["SUMMARY", "text", true], ["TITLE", "text", true]],
    "all three carry both, which is the strongest claim three instances can support",
  );
});

test("SH525: a field a majority carries is proposed, and names who lacks it", () => {
  const proposal = proposeFromInstances(
    [
      product("post:acme/a", { PRICE: "9.99" }),
      product("post:acme/b", { PRICE: "19.99" }),
      product("post:acme/c"),
    ],
    "Product",
  );

  const price = proposal.fields.find((field) => field.name === "PRICE");
  assert.equal(price.required, false, "one instance has no PRICE, so requiring it is a claim the evidence does not support");
  assert.deepEqual(
    price.missing,
    ["post:acme/c"],
    "the disagreement IS the interesting part: naming the instance that lacks it is what lets a "
      + "reader decide between an optional field, a data gap, and a second type",
  );
});

test("SH525: a field a minority carries is reported and not swept into the type", () => {
  const proposal = proposeFromInstances(
    [
      product("post:acme/a", { ISBN: "978-3-16-148410-0" }),
      product("post:acme/b"),
      product("post:acme/c"),
    ],
    "Product",
  );

  assert.ok(
    !proposal.fields.some((field) => field.name === "ISBN"),
    "one of three is more likely another type's field than an optional one, and sweeping it in "
      + "produces exactly the over-wide type this verb exists to avoid",
  );
  assert.deepEqual(
    proposal.outliers.map((field) => field.name),
    ["ISBN"],
    "reported rather than dropped — 'these instances carry something the others do not' is a "
      + "finding about the model, and hiding it makes the proposal look more settled than it is",
  );
});

test("SH525: an empty value is absent, so a blank field is not evidence of a field", () => {
  const proposal = proposeFromInstances(
    [
      product("post:acme/a", { NOTES: "" }),
      product("post:acme/b", { NOTES: "" }),
      product("post:acme/c", { NOTES: "" }),
    ],
    "Product",
  );

  const notes = proposal.fields.find((field) => field.name === "NOTES");
  assert.equal(
    notes,
    undefined,
    "a field every instance declares and none fills is a form control somebody left blank; "
      + "counting it would make the disagreement read as agreement",
  );
});

test("SH525: the kind comes from the values that exist, in the model's own vocabulary", () => {
  assert.equal(kindOf("A short label"), "text");
  assert.equal(kindOf("<p>prose with <b>markup</b></p>"), "htmlEditor");
  assert.equal(kindOf("x".repeat(200)), "textArea");
  assert.equal(kindOf(["a", "b"]), "relator");
  assert.equal(kindOf(true), "checkBox");
  assert.equal(kindOf("2026-08-10"), "date");
  // The one that costs a curator a working control if it is wrong: a stored path needs a picker,
  // not a text box holding a path.
  assert.equal(kindOf("/assets/hero.png"), "file");
  assert.equal(kindOf("photo.jpg"), "file");
});

test("SH525: an absent value does not vote on the kind", () => {
  const proposal = proposeFromInstances(
    [
      product("post:acme/a", { BODY: "<p>real prose</p>" }),
      product("post:acme/b", { BODY: "<p>more prose</p>" }),
      product("post:acme/c", { BODY: "" }),
    ],
    "Product",
  );

  assert.equal(
    proposal.fields.find((field) => field.name === "BODY").kind,
    "htmlEditor",
    "letting an absent value vote would make every partly-filled field a `text`",
  );
});

test("SH525: the report says it is evidence about the instances it read", () => {
  const text = describeInstanceProposal(
    proposeFromInstances([product("post:acme/a"), product("post:acme/b")], "Product"),
  );

  assert.match(
    text,
    /A proposal, not a conclusion/,
    "the property most easily lost when the input stops being a stranger's site and starts being "
      + "content the reader wrote — a report that reads like a conclusion is one somebody applies "
      + "without looking",
  );
  assert.match(text, /Check it against one you did not include/);
});

test("SH525: nothing derivable is null rather than an empty type", () => {
  assert.equal(proposeFromInstances([], "Product"), null);
  assert.equal(proposeFromInstances([{ address: "post:acme/a", data: {} }], "Product"), null);
  // The same rule SH506 made for the markup path: the reader's question is "what would I get", and
  // the honest answer when nothing is shared is "nothing" rather than a type with no fields.
  assert.match(describeInstanceProposal(null), /Nothing to model/);
});
