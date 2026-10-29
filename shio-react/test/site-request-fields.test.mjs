// SH1303 — every setting the site form saves is a field the server's request reads.
//
// `PUT /api/v2/site/{id}` binds `ShSiteRequest`, and `ShSiteAPI.toEntity` copies only what that
// record declares. A key the form writes and the record lacks is dropped in silence: the save answers
// 200, the next read shows the old value, and because the update replaces the field from the payload,
// every console save clears what an agent set. `membersLogin` (SH1302) and `pageType` (SH1283) were
// both lost that way and found only by driving the form in a browser. This reads the two files.

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

/** The keys `onSubmit` writes over the loaded site, in `const merged: ShSite = { ...site, … }`. */
function formKeys(source) {
  const start = source.indexOf("const merged: ShSite = {");
  assert.ok(start >= 0, "SH1303: the site form still builds `merged` in onSubmit");
  const body = source.slice(start, source.indexOf("}", start));
  return [...body.matchAll(/^\s*(\w+)\s*[,:]/gm)].map(([, key]) => key);
}

/** The components of `public record ShSiteRequest(…)`. */
function requestFields(source) {
  const start = source.indexOf("public record ShSiteRequest(");
  const body = source.slice(start, source.indexOf(") {", start));
  return [...body.matchAll(/\b(?:String|Boolean)\s+(\w+)/g)].map(([, name]) => name);
}

test("SH1303: every key the site form saves is read by the server's request", () => {
  const form = formKeys(read("../src/app/content/site.edit.page.tsx"));
  const request = requestFields(read(
    "../../shio-app/src/main/java/com/viglet/shio/api/site/dto/ShSiteRequest.java"));
  assert.ok(form.includes("url") && request.includes("url"), "SH1303: both lists were read");
  assert.deepEqual(form.filter((key) => !request.includes(key)), [],
    "SH1303: the form saves these and ShSiteRequest drops them, so the console's save loses them "
      + "and clears what an agent set. Add each to the record and to ShSiteAPI.toEntity.");
});
