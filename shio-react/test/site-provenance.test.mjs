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

import { stripComments } from "./support/source-text.mjs";

/**
 * SH1135 — the site editor says where a replicated site came from.
 *
 * The record is a site-scoped memory note `shio convert` writes. The card reads it through the
 * query layer and renders nothing when there is none; the key is written in two packages, and this
 * holds the two spellings together, because a card reading "replication" beside a CLI writing
 * "replicated" is a card that is never shown and a test suite that stays green.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (path) => stripComments(readFileSync(join(ROOT, path), "utf8"));

test("SH1135: the card reads the site's memory with useQuery's signal and keys it on the site", () => {
  const card = read("src/components/site-provenance.section.tsx");
  assert.match(card, /queryFn: \(\{ signal \}\) => siteService\.memory\(siteName \?\? "", \{ signal \}\)/u);
  assert.match(card, /queryKey: addressKey\(`summary:provenance\|\$\{siteName \?\? ""\}`\)/u);
  assert.match(card, /one\.key === REPLICATION_NOTE_KEY && one\.scope === siteName/u,
    "SH1135: an instance-wide note under the same key is not this site's provenance");
  assert.match(card, /if \(!note\) return null/u,
    "SH1135: an absent card makes no claim; an empty one would say the site was not replicated");

  const page = read("src/app/content/site.edit.page.tsx");
  assert.match(page, /<SiteProvenanceSection siteName=\{site\?\.name\} \/>/u);
});

test("SH1135: the console reads the key the CLI writes", () => {
  const card = readFileSync(join(ROOT, "src/components/site-provenance.section.tsx"), "utf8");
  const cli = readFileSync(join(ROOT, "..", "cli", "src", "commands", "convert.mjs"), "utf8");
  const console = /export const REPLICATION_NOTE_KEY = "([^"]+)"/u.exec(card)?.[1];
  const writer = /export const REPLICATION_NOTE_KEY = "([^"]+)"/u.exec(cli)?.[1];
  assert.ok(console && writer, "SH1135: both declarations moved, and this test lost them");
  assert.equal(console, writer);
});

test("SH1135: both languages name the card", () => {
  for (const lang of ["en", "pt"]) {
    const bundle = JSON.parse(readFileSync(join(ROOT, `src/i18n/locales/${lang}/content.json`), "utf8"));
    for (const key of ["content.siteProvenance", "content.siteProvenanceBy"]) {
      assert.ok(key in bundle, `SH1135: ${key} is missing from the ${lang} bundle`);
    }
  }
});
