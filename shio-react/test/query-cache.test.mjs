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

import { STALE_MS, createQueryClient, shouldRetry } from "../src/lib/query-client.ts";
import { SCHEMES, addressKey, keyAddress } from "../src/lib/query-keys.ts";
import { stripComments } from "./support/source-text.mjs";

/**
 * SH1046 — what the console's cache promises, asserted without a browser.
 *
 * ## The claim, and why it is made here
 *
 * Seventeen call sites fetched a whole collection on every mount, and the content browser refetched
 * the *entire* post-type catalogue inside an effect keyed on the folder id — so walking a tree cost
 * one full catalogue per folder. The claim SH1046 makes is that the second read is free.
 *
 * That claim is about the `QueryClient`'s defaults, so this drives the real client with the real
 * defaults rather than asserting on the shape of a component. `createQueryClient` is a `.ts` module
 * with no JSX for exactly this reason: a behaviour reachable only through a rendered provider is
 * one nothing asserts, which is the note `use-discovery.test.mjs` ends on one layer down.
 *
 * ## What it deliberately does not assert
 *
 * That `usePostTypes` subscribes, or that a page re-renders when the answer lands. That needs a
 * renderer and this workspace has none. What stands behind it is TanStack's own contract plus the
 * assertions here: the cache is the half that was actually wrong.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

/**
 * A real client, cleared when the test ends.
 *
 * <p>Not tidiness: `gcTime` is five minutes, so an uncleared client leaves a timer holding the
 * process open for five minutes after the last assertion — measured here on the first run, which
 * is how a suite that takes eight seconds becomes one people stop running.</p>
 */
function client(t, onFailure) {
  const made = createQueryClient(onFailure);
  t.after(() => made.clear());
  return made;
}

/** A fetcher whose answer this test controls, counting how often it was really called. */
function counted(answer = ["one"]) {
  const state = { calls: 0 };
  return [state, async () => { state.calls += 1; return answer; }];
}

test("SH1046: the second navigation costs nothing, which is the whole task", async (t) => {
  // The assertion this task is named for, driven rather than reasoned. Two reads of one catalogue
  // inside the stale window — a curator opening a folder and then a second folder.
  const cache = client(t);
  const [state, fetcher] = counted();
  const options = { queryKey: addressKey("postType:"), queryFn: fetcher };

  const first = await cache.fetchQuery(options);
  const second = await cache.fetchQuery(options);

  assert.deepEqual(second, first);
  assert.equal(state.calls, 1,
    "SH1046: the second read issued a second request. That is the defect: the content browser's "
    + "effect is keyed on the folder id, so a walk down a tree cost one whole post-type catalogue "
    + "per folder for a list that decides a default and fills a filter.");
});

test("SH1046: many screens mounting together share one request", async (t) => {
  // The other half, and the one the hand-rolled discovery store already did for one endpoint: the
  // dashboard, the header and a picker all ask on first paint.
  const cache = client(t);
  const [state, fetcher] = counted();
  const options = { queryKey: addressKey("site:"), queryFn: fetcher };

  await Promise.all([
    cache.fetchQuery(options), cache.fetchQuery(options), cache.fetchQuery(options),
  ]);

  assert.equal(state.calls, 1, "SH1046: three concurrent callers issued three requests");
});

test("SH1046: a write drops the catalogue and every single item under it", async (t) => {
  // The half a cache is wrong without, and the reason the scheme is its own key segment: a delete
  // that only filtered local state would leave this page right and every other page stale.
  const cache = client(t);
  const [catalogue, all] = counted(["Page", "News"]);
  const [one, single] = counted(["Page"]);
  await cache.fetchQuery({ queryKey: addressKey("postType:"), queryFn: all });
  await cache.fetchQuery({ queryKey: addressKey("postType:Page"), queryFn: single });

  await cache.invalidateQueries({ queryKey: addressKey("postType:") });
  await cache.fetchQuery({ queryKey: addressKey("postType:"), queryFn: all });
  await cache.fetchQuery({ queryKey: addressKey("postType:Page"), queryFn: single });

  assert.equal(catalogue.calls, 2, "the catalogue must be re-read after its scheme is invalidated");
  assert.equal(one.calls, 2,
    "SH1046: invalidating `postType:` left `postType:Page` cached. The scheme is the key's first "
    + "segment precisely so that one call reaches both — otherwise every write needs a second list "
    + "of what else to drop, and that list is the one that goes stale.");

  // And the narrow form still means one thing, or the grammar would only ever be usable wholesale.
  const other = await cache.fetchQuery({ queryKey: addressKey("postType:News"), queryFn: single });
  await cache.invalidateQueries({ queryKey: addressKey("postType:Page") });
  assert.deepEqual(cache.getQueryData(addressKey("postType:News")), other,
    "invalidating one address must not reach its siblings");
});

test("SH1046: a key is an address, and an address that is not one fails where it is written", () => {
  assert.deepEqual(addressKey("postType:"), ["postType:"]);
  assert.deepEqual(addressKey("postType:Page"), ["postType:", "Page"]);
  assert.deepEqual(addressKey("folder:Cafeteria/Menu/Drinks"), ["folder:", "Cafeteria/Menu/Drinks"]);
  assert.equal(keyAddress(addressKey("site:Cafeteria")), "site:Cafeteria");
  assert.equal(keyAddress(addressKey("site:")), "site:");

  assert.throws(() => addressKey("postTypes"), /carries no known address scheme/u,
    "SH1046: a key built from an unknown scheme would cache and never be invalidated, because "
    + "every invalidation names a scheme — a screen showing yesterday's content with no error "
    + "anywhere. The typo has to fail where it is written.");
  assert.throws(() => addressKey(""), /carries no known address scheme/u);

  // SH79's four, spelled the way every other surface spells them. The console-local schemes may
  // come and go; these are a contract shared with the MCP tools, the CLI and REST.
  for (const scheme of ["post:", "folder:", "site:", "postType:", "id:"]) {
    assert.ok(SCHEMES.includes(scheme), `SH1046: the grammar's \`${scheme}\` is not a key scheme`);
  }
});

test("SH1046: a refusal is delivered, not repeated", () => {
  // P6 says an error is an instruction. Asking a second time for something the server has explained
  // it will not do doubles the cost of every refusal and delays the message by a round trip.
  assert.equal(shouldRetry(0, { response: { status: 403 } }), false, "a refusal is an answer");
  assert.equal(shouldRetry(0, { response: { status: 404 } }), false);
  assert.equal(shouldRetry(0, { response: { status: 500 } }), true, "a server fault may be a blip");
  assert.equal(shouldRetry(0, new Error("Network Error")), true,
    "a request that never arrived carries no status, and that is the case a retry is for");
  assert.equal(shouldRetry(1, new Error("Network Error")), false, "once, and then it is an outage");
});

test("SH1046: a failed read reaches the handler once, with the address it failed on", async (t) => {
  const seen = [];
  const cache = client(t, (error, address) => seen.push([String(error.message), address]));

  await cache.fetchQuery({
    queryKey: addressKey("widget:"),
    queryFn: async () => { throw new Error("refused"); },
    retry: false,
  }).catch(() => {});

  assert.deepEqual(seen, [["refused", "widget:"]],
    "SH1046: the handler is the cache's, not each component's — three screens sharing one failed "
    + "read is one thing that went wrong, and TanStack v5 moved `onError` off `useQuery` because "
    + "the per-component form toasted once per screen.");
});

test("SH1046: nothing left behind — no page still fetches a catalogue on mount", () => {
  // The sweep, as a lint rather than as a claim in a commit message (SH322). A page that goes back
  // to constructing a service and calling `.query()` in an effect is the defect returning, and it
  // would pass every other assertion in this file.
  const pages = [];
  for (const entry of readdirSync(join(ROOT, "src", "app"), { recursive: true })) {
    if (typeof entry === "string" && entry.endsWith(".tsx")) pages.push(entry);
  }
  assert.ok(pages.length >= 40, `SH1046: only ${pages.length} page(s) walked — the scan is lost`);

  /*
   * The shape this replaced, and only that shape: a catalogue read whose answer is consumed
   * inline. `useCallback(() => service.query(), [])` is not it — that is a fetcher handed to
   * `AdminListPage`, which caches it under the `address` prop — and comments are stripped first,
   * because that component's own doc comment quotes the defect it describes. SH740's trap, met
   * again here on this scan's first run.
   */
  const CATALOGUE = /\.query\(\)\s*\.\s*(?:then|catch|finally)\b/u;
  const offenders = pages.filter((page) =>
    CATALOGUE.test(stripComments(readFileSync(join(ROOT, "src", "app", page), "utf8"))));

  assert.deepEqual(offenders, [],
    "SH1046: these pages read a whole catalogue directly instead of through the cached hooks in "
    + "`hooks/use-catalogue.ts`. One of them is one folder navigation that refetches everything "
    + "again, and nothing else in this file would notice.");
});

test("SH1046: the stale window is a number with an argument beside it", () => {
  // SH736's lesson, one gate over: a constant nobody can see is one that gets tuned by whoever is
  // annoyed that day. Held here so moving it is a reviewed diff.
  assert.ok(STALE_MS >= 10_000,
    "SH1046: under ten seconds and a folder-to-folder walk pays for the catalogue again, which is "
    + "the whole thing this task removed");
  assert.ok(STALE_MS <= 120_000,
    "and over two minutes a curator who edits a post type in another tab keeps seeing the old "
    + "list for longer than they would believe was a cache");
});
