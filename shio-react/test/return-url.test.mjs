// SH1299 — the login's `returnUrl` is followed only to a path on this host.
//
// `/sites/**` now sends a visitor turned away from a members-only page to `/login?returnUrl=<page>`,
// so the parameter arrives in links the console did not build. The login used to follow any value.

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

import { safeReturnUrl } from "../src/lib/return-url.ts";

test("SH1299: a page on this host is where the login goes back to", () => {
  assert.equal(safeReturnUrl("/sites/quilhara/default/en-us/members", "/"), "/sites/quilhara/default/en-us/members");
  assert.equal(safeReturnUrl("/console/content?x=1", "/"), "/console/content?x=1");
});

test("SH1299: another host is never followed, in any spelling", () => {
  for (const hostile of ["https://elsewhere.example/", "//elsewhere.example/", "/\\elsewhere.example", "javascript:alert(1)", "", null, undefined]) {
    assert.equal(safeReturnUrl(hostile, "/"), "/", `SH1299: ${String(hostile)} is not a path on this host`);
  }
});

test("SH1299: the login form reads returnUrl through the check", () => {
  const form = readFileSync(new URL("../src/components/login/login-form.tsx", import.meta.url), "utf8");
  assert.match(form, /safeReturnUrl\(searchParams\.get\("returnUrl"\)/,
    "SH1299: a returnUrl followed without the check is the open redirect this closes");
});
