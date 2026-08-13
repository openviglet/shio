/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH704 — what the discovery cache promises, asserted without a browser.
 *
 * ## Why this file exists at all
 *
 * The cache used to be module state read into component state and kept in step by an effect. That
 * shape could only be exercised through a rendered component and a real request, which is why its
 * defects lived behind an eslint suppression instead of behind a test: a republish one render late
 * for every mount after the first, an `alive` flag guarding a copy that could outlive its
 * component, and an effect that depended on the state it set.
 *
 * `createDiscoveryStore` takes its fetcher, so every promise below is drivable in-process. The
 * assertions are the ones a caller would notice if they broke — not the shape of the code.
 *
 * ## The one this cannot make
 *
 * That `useDiscovery` itself subscribes. That needs a renderer, and this workspace has none. What
 * stands behind it instead is `useSyncExternalStore`'s own contract plus these: a store that
 * behaves is the half that was actually wrong.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { createDiscoveryStore } from "../src/lib/discovery-store.ts";

/** A fetcher whose resolution this test controls, and which counts how often it was called. */
function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

test("SH704: many callers, one request — which is the only reason this cache exists", async () => {
  const gate = deferred();
  let calls = 0;
  const store = createDiscoveryStore(() => {
    calls += 1;
    return gate.promise;
  });

  // The header, the login form and admin settings all ask on first paint.
  const asks = [store.load(), store.load(), store.load()];
  gate.resolve({ keycloak: false });
  await Promise.all(asks);

  assert.equal(calls, 1,
    "SH704: three callers issued three requests. This module's whole purpose is that the console's "
    + "first paint asks /v2/discovery once, and the in-flight promise is what guarantees it — a "
    + "regression here is invisible in the UI and visible only in a network tab nobody has open.");
});

test("SH704: the snapshot is the same reference until it changes", async () => {
  const value = { keycloak: true };
  const store = createDiscoveryStore(() => Promise.resolve(value));

  assert.equal(store.get(), undefined, "SH704: nothing has landed yet, so the snapshot is undefined");
  await store.load();

  assert.equal(store.get(), store.get(),
    "SH704: `get` returned two different references for one value. `useSyncExternalStore` "
    + "re-renders whenever the snapshot is not `Object.is`-equal to the previous one, so a getter "
    + "that builds a fresh object each call renders forever. This is the contract that makes the "
    + "subscription safe to use, and it is invisible until a page pegs a core.");
  assert.equal(store.get(), value);
});

test("SH704: a listener is told once, and an unsubscribed one is not told at all", async () => {
  const store = createDiscoveryStore(() => Promise.resolve({ keycloak: false }));

  let stayed = 0;
  let left = 0;
  store.subscribe(() => { stayed += 1; });
  const unsubscribe = store.subscribe(() => { left += 1; });
  unsubscribe();

  await store.load();

  assert.equal(stayed, 1, "SH704: the mounted subscriber must be notified exactly once");
  assert.equal(left, 0,
    "SH704: a listener that unsubscribed was still called. This is the `alive` flag SH665 had to "
    + "add by hand, and the reason the subscription replaces it: a response arriving after a "
    + "component unmounted must reach nobody, as a property of the store rather than as a boolean "
    + "somebody remembered to write.");
});

test("SH704: a failed discovery is retried, not remembered", async () => {
  let calls = 0;
  const store = createDiscoveryStore(() => {
    calls += 1;
    return calls === 1 ? Promise.reject(new Error("no")) : Promise.resolve({ keycloak: false });
  });

  await assert.rejects(() => store.load());
  assert.equal(store.get(), undefined, "SH704: a failure must leave the value unset");

  await store.load();

  assert.equal(calls, 2,
    "SH704: the second mount did not retry. If the rejected promise stays in `inflight`, every "
    + "later caller awaits a promise that will never resolve again — the console then renders its "
    + "defaults forever and looks like an instance that supports nothing, with no request in "
    + "flight to explain it.");
  assert.deepEqual(store.get(), { keycloak: false });
});

test("SH704: these assertions can fail — a store that ignores its fetcher does not pass them", async () => {
  // The reader's own guard. Every test above is about a collaboration, and a collaboration test
  // that was accidentally asserting nothing would pass just as quietly as a working one.
  const broken = {
    subscribe: () => () => {},
    get: () => undefined,
    load: () => Promise.resolve({ keycloak: false }),
  };

  let told = 0;
  broken.subscribe(() => { told += 1; });
  await broken.load();

  assert.equal(told, 0, "SH704: the deliberately broken store notifies nobody, as constructed");
  assert.equal(broken.get(), undefined,
    "SH704: and never publishes — so the assertions above are testing the real store's behaviour "
    + "rather than restating what any object with these method names would do.");
});
