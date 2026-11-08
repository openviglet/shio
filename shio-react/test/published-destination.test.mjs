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
import { dirname, join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

import { publishedDestination } from "../src/lib/site-front-end.ts";

/**
 * SH1361 — the console's "Open published" opens the address the server computed.
 *
 * A site can have a host of its own, `<furl>.<shio.sites.host-domain>` (SH1354), and only the server
 * knows that domain. The console used to mirror `ShSiteFrontEnd.publishedUrl` by hand and so kept
 * opening `/sites/<furl>/` on the instance. `ShSiteHostTest` pins the server's `publishedUrl` on the
 * site reads; this pins the console to use it verbatim, which is what makes the two one answer.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const site = (extra) => ({ id: "1", name: "acme", furl: "acme", url: "", ...extra });

test("SH1361: the server's publishedUrl wins over the console's own composition", () => {
  assert.deepEqual(publishedDestination(site({ publishedUrl: "https://acme.sites.example/" })),
    { url: "https://acme.sites.example/", external: true });
  assert.deepEqual(publishedDestination(site({ publishedUrl: "/sites/acme/" })),
    { url: "/sites/acme/", external: false });
});

test("SH1361: a site object no read produced still opens the /sites fallback", () => {
  assert.deepEqual(publishedDestination(site({})), { url: "/sites/acme/", external: false });
  assert.deepEqual(publishedDestination(site({ url: "https://www.acme.com" })),
    { url: "https://www.acme.com/", external: true });
});

test("SH1361: all three Open published actions go through publishedDestination", () => {
  for (const path of ["src/app/content/site.list.page.tsx", "src/app/content/site.edit.page.tsx",
    "src/app/content/content-browser/use-browser-open.ts"]) {
    assert.match(readFileSync(join(ROOT, path), "utf8"), /publishedDestination\(/u, path);
  }
});
