/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

import { rank, remember, score } from "../src/lib/command-match.ts"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1028 — the palette offers verbs and content, and cannot perform a write.
 *
 * Three claims, and the third is the one that would hurt. **Compiled, not listed**: every command
 * names a surface the backend's declaration declares, so the palette's verbs cannot become a
 * fourth list beside the rail, the declaration and the agent's ops. **Ranked by the product**,
 * because the package does not reorder a product group (VDS123) — so contextual first, then match
 * quality, then recency, and all of it a function of two strings. And **nothing writes from the
 * palette**: a palette is where people press Enter fast, and fast is exactly what turns a
 * mis-typed Enter into a published page.
 *
 * The declaration is read as source rather than imported, the way `bento-nav.test.mjs` reads its
 * own: the module imports the backend's JSON the way Vite resolves it, and Node does not — so an
 * import here would be testing the bundler.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const SURFACES = JSON.parse(readFileSync(
  join(ROOT, "..", "shio-app", "src", "main", "resources", "console", "surfaces.json"), "utf8"))
const COMMANDS_SOURCE = stripComments(
  readFileSync(join(ROOT, "src", "app", "system", "bento-commands.ts"), "utf8"))
const SHELL = readFileSync(join(ROOT, "src", "app", "system", "bento.root.page.tsx"), "utf8")

/** The declared commands, read out of the array the module exports. */
function commands() {
  const block = /const COMMANDS: readonly ShioCommand\[\] = \[([\s\S]*?)\n\]/u
    .exec(COMMANDS_SOURCE)?.[1] ?? ""
  assert.ok(block, "SH1028: the command array moved and this scan lost its subject")
  return [...block.matchAll(/\{\s*\n\s*id: "([^"]+)",\s*\n\s*surface: "([^"]+)",\s*\n\s*labelKey: "([^"]+)",\s*\n\s*aliases: \[([^\]]*)\],([\s\S]*?)\n  \}/gu)]
    .map((found) => ({
      id: found[1],
      surface: found[2],
      labelKey: found[3],
      aliases: [...found[4].matchAll(/"([^"]+)"/gu)].map((alias) => alias[1]),
      confirm: /confirm: true/u.test(found[5]),
      contextual: /contextual:/u.test(found[5]),
      body: found[5],
    }))
}

test("SH1028: every command names a surface the backend declares", () => {
  const declared = new Set(SURFACES.surfaces.map((surface) => surface.id))
  assert.ok(declared.size >= 15, `SH1028: read only ${declared.size} surfaces`)

  const found = commands()
  assert.ok(found.length >= 8,
    `SH1028: read ${found.length} commands, which is fewer than the palette was opened for`)
  for (const command of found) {
    assert.ok(declared.has(command.surface),
      `SH1028: "${command.id}" names surface "${command.surface}", which console/surfaces.json `
        + "does not declare. A command for a surface that does not exist is the fourth list this "
        + "was written to avoid — and the module throws on load rather than rendering one.")
  }
  // The module's own guard, which is what makes the above true at runtime rather than only here.
  assert.match(COMMANDS_SOURCE, /if \(!DECLARED\.has\(command\.surface\)\) \{/u,
    "SH1028: a test is not a guard — the console has to refuse to load rather than render a "
      + "command for a surface that is gone")
})

test("SH1028: nothing writes from the palette — a writing command routes to the flow that asks", () => {
  const writes = commands().filter((command) => command.confirm)
  assert.ok(writes.length >= 3,
    `SH1028: only ${writes.length} commands are marked as writing, which is too few to be true`)
  for (const command of writes) {
    assert.match(command.body, /route: \(?[^)]*\)? =>/u,
      `SH1028: "${command.id}" is a write with no route to send the reader to`)
    assert.doesNotMatch(command.body, /\?(confirm|apply|publish)=/u,
      `SH1028: "${command.id}" carries its own act in the URL. The route has to open the plan, `
        + "the form or the dry run — the palette takes you to the page that asks (P7)")
  }

  // And the shell really does only navigate: a service call in the select handler is the shape
  // this is guarding against, and it would pass every assertion above.
  const select = /onSelect: \(chosen: \{ id: string \}\) => \{[\s\S]*?\n    \},/u.exec(SHELL)?.[0] ?? ""
  assert.ok(select, "SH1028: the select handler moved and this scan lost its subject")
  assert.match(select, /navigate\(row\.route\)/u)
  assert.doesNotMatch(stripComments(select), /Service\.|axios\./u,
    "SH1028: the palette navigates and never acts. A call here is the fastest way in the product "
      + "to do something by accident.")
})

test("SH1028: the offers about the page you are on are declared contextual", () => {
  const contextual = commands().filter((command) => command.contextual).map((each) => each.id)
  assert.deepEqual(contextual.sort(), ["new-post", "publish-page"],
    "SH1028: \"publish this page\" and \"create a post here\" are different offers depending on "
      + "where you are, and a palette that ranked them the same everywhere is one a curator "
      + "learns to scroll past")
})

test("SH1028: where you are outranks how well it matches", () => {
  const rows = [
    // The better match by every other measure: `pub` is a prefix of it.
    { id: "elsewhere", label: "Publish something" },
    // A weaker match — `pub` is in the middle — and the one about the page in front of you.
    { id: "here", label: "Unpublish page", contextual: true },
  ]
  assert.equal(rank(rows, "")[0].id, "here")
  assert.equal(rank(rows, "pub")[0].id, "here",
    "SH1028: contextual is worth more than any match quality — the offer about the page in front "
      + "of you is the one the palette was opened for")
  assert.deepEqual(rank(rows, "zzz"), [],
    "SH1028: and contextual does not mean always shown. A row that does not match the query is "
      + "not an answer to it, wherever the reader happens to be.")
})

test("SH1028: a prefix beats a substring beats a subsequence", () => {
  const publish = { id: "a", label: "Publish this page" }
  assert.ok(score(publish, "pub") > score(publish, "pblsh"),
    "SH1028: a tight match outranks a scattered one, which is what makes `pub` land where a "
      + "reader means it")
  assert.equal(score(publish, "zzz"), null)
  assert.ok(score({ id: "b", label: "Open the review queue", aliases: ["approve"] }, "approve")
    !== null, "SH1028: an alias is a word a reader might type — `approve` for the review queue")
})

test("SH1028: recency breaks a tie and never beats a match", () => {
  const rows = [
    { id: "first", label: "Open the trash" },
    { id: "second", label: "Open the review queue" },
  ]
  assert.equal(rank(rows, "open", ["second"])[0].id, "second",
    "SH1028: a console has a handful of verbs somebody runs all day, and no cleverness beats "
      + "remembering them")
  assert.equal(rank(rows, "trash", ["second"])[0].id, "first",
    "SH1028: but a better match still wins — recency is worth at most one place, or the palette "
      + "starts answering the last question instead of this one")
  assert.deepEqual(remember(["a", "b"], "b"), ["b", "a"])
  assert.equal(remember(Array.from({ length: 12 }, (_, index) => `x${index}`), "new").length, 8)
})

test("SH1028: the content half is debounced and, more importantly, cancellable", () => {
  /*
   * SH1157 kept both of this test's claims and changed how each is secured, so the assertions name the
   * property rather than the mechanism that used to carry it.
   *
   * The debounce is a **committed term**: the timer sets what has been asked for, and the query is keyed
   * on that rather than on what has been typed. A keystroke is still not a query.
   */
  assert.match(SHELL, /const timer = setTimeout\(\(\) => setTerm\(wanted\), SEARCH_DEBOUNCE_MS\)/u,
    "SH1028: a keystroke is not a query")
  assert.match(SHELL, /queryKey: addressKey\(`summary:palette\|\$\{term\}`\)/u,
    "SH1028: a slow answer to `pri` arriving after a fast answer to `pricing` would replace the "
      + "right rows with stale ones, and the reader would watch the palette go backwards as they "
      + "typed. That is a correctness problem, not a network one — and SH1157 closes it better than "
      + "the `queryToken` ref this replaced: the term is part of the key, so the answer to `pri` is "
      + "filed under a question nobody is asking and is never rendered, where the ref only ignored "
      + "it after the server had already done the work.")
  assert.match(SHELL, /queryFn: \(\{ signal \}\) => searchService\.search\(\{ q: term \}, 8, \{ signal \}\)/u,
    "SH1028: the content comes from the console's own search, which finds drafts (see SH1027) — and "
      + "the superseded request is cancelled rather than merely disregarded")
  assert.match(SHELL, /pending: searching/u,
    "SH1028: an empty list that is not empty yet reads as \"no matches\" (VDS123)")
  assert.match(SHELL, /const searching = wanted !== term \|\| \(term\.length >= 2 && read\.isFetching\)/u,
    "SH1028: and `searching` has to be true while the debounce has not caught up as well as while the "
      + "request runs, or the palette shows \"no matches\" for the 200ms before it asks")
})

test("SH1028: actions come before content, and the package keeps that order", () => {
  assert.match(SHELL, /items: \[\.\.\.actions, \.\.\.found\]/u,
    "SH1028: the palette does not reorder a product group, so \"actions above content\" is a fact "
      + "this file decides and the component preserves")
})

test("SH1028: every command's label exists in both locales", () => {
  const declared = commands()
  for (const locale of ["en", "pt"]) {
    const strings = JSON.parse(
      readFileSync(join(ROOT, "src", "i18n", "locales", locale, "common.json"), "utf8"))
    assert.ok(strings["palette.group"], `SH1028: ${locale} is missing palette.group`)
    for (const command of declared) {
      assert.ok(strings[command.labelKey], `SH1028: ${locale} is missing ${command.labelKey}`)
    }
  }
})
