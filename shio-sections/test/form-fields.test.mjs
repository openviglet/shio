/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1386 — the form section posts with no script, and the names it posts are the endpoint's.
 *
 * Read from source, for `components.test.mjs`' reason: importing JSX would mean building the package.
 * The Java side is read from source too, because the hidden field names are a wire contract between
 * two languages and the only thing that keeps two spellings of one name equal is a comparison.
 */
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const read = (relative) => readFile(fileURLToPath(new URL(relative, import.meta.url)), "utf8");

const components = await read("../src/components.tsx");
const JAVA = "../../shio-app/src/main/java/com/viglet/shio/";
const submission = await read(`${JAVA}api/form/ShFormSubmissionAPI.java`);
const tokenFilter = await read(`${JAVA}spring/security/auth/ShApiTokenFilter.java`);

/** The string a Java constant is declared as. */
function javaConstant(source, name) {
  const match = source.match(new RegExp(`${name}\\s*=\\s*"([^"]+)"`));
  assert.ok(match, `${name} is no longer a string constant; this test has nothing to compare`);
  return match[1];
}

/** The value FORM_HIDDEN_FIELDS gives one key. */
function hidden(key) {
  const block = components.slice(components.indexOf("export const FORM_HIDDEN_FIELDS"));
  const match = block.match(new RegExp(`${key}:\\s*"([^"]+)"`));
  assert.ok(match, `FORM_HIDDEN_FIELDS.${key} is missing`);
  return match[1];
}

test("SH1386: the hidden fields the React form posts are the names the endpoint reads", () => {
  assert.equal(hidden("type"), javaConstant(submission, "TYPE_FIELD"),
    "SH1386: a renamed type field posts every submission as a refusal");
  assert.equal(hidden("token"), javaConstant(tokenFilter, "FORM_TOKEN_FIELD"),
    "SH1386: a renamed token field leaves every script-free form unauthenticated");
  assert.equal(hidden("honeypot"), javaConstant(submission, "HONEYPOT_FIELD"),
    "SH1386: a renamed honeypot passes every bot");
});

test("SH1386: the form posts natively, and is drawn only when it can post", () => {
  const form = components.slice(components.indexOf("export function Form("),
    components.indexOf("export function Gallery("));
  assert.match(form, /<form[^>]*method="post"/, "SH1386: a native POST, so it works with script off");
  assert.doesNotMatch(form, /onSubmit|fetch\(/, "SH1386: nothing in the browser is required to submit");
  assert.match(form, /const drawn = Boolean\(type && token && form\?\.action/,
    "SH1386: no token or no action means no form, rather than one that fails on submit");
});
