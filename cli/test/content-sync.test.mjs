/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH89 — the three-way reconciliation, and SH87's push plan.
 *
 * The properties under test are the ones that decide whether a sync is safe to run
 * unattended: one side changing is never a conflict, both sides changing *different
 * fields* is never a conflict, both sides changing the *same* field always is, a deletion
 * on either side is never silently undone, and a push declares the server's own values for
 * a post the server changed — the rule that keeps a push from reverting a console edit.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import {
  addressOf,
  ancestorsOf,
  documentsFor,
  planPushContent,
  reconcile,
  reconcileFolders,
  resolveConflict,
  same,
} from "../src/content-sync.mjs";

const post = (data, extra = {}) => ({
  site: "Blog",
  url: "/blog/hello",
  type: "Article",
  folder: "/blog",
  data,
  ...extra,
});

const BASE = post({ headline: "Hello", body: "First" });

test("an address is the post's identity in every report", () => {
  assert.equal(addressOf(BASE), "post:Blog/blog/hello");
});

test("nothing changed is nothing to do", () => {
  const result = reconcile({ base: BASE, remote: BASE, local: BASE });

  assert.equal(result.kind, "unchanged");
  assert.ok(same(BASE, BASE));
});

test("one side changing takes that side, with no conflict", () => {
  const remote = post({ headline: "Hello there", body: "First" });
  const local = post({ headline: "Hello", body: "Rewritten" });

  assert.equal(reconcile({ base: BASE, remote, local: BASE }).kind, "remote-changed");
  assert.equal(reconcile({ base: BASE, remote: BASE, local }).kind, "local-changed");
});

test("different fields on each side merge instead of colliding", () => {
  // A human fixing a headline and an agent rewriting the body are not in conflict, and
  // calling them one is what teaches people to pass --theirs reflexively.
  const remote = post({ headline: "Hello there", body: "First" });
  const local = post({ headline: "Hello", body: "Rewritten" });

  const result = reconcile({ base: BASE, remote, local });

  assert.equal(result.kind, "merged");
  assert.deepEqual(result.doc.data, { headline: "Hello there", body: "Rewritten" });
});

test("the same field on both sides is a conflict, named by field", () => {
  const remote = post({ headline: "Theirs", body: "First" });
  const local = post({ headline: "Ours", body: "First" });

  const result = reconcile({ base: BASE, remote, local });

  assert.equal(result.kind, "conflict");
  assert.deepEqual(result.conflicts, ["fields.headline"]);
});

test("moving a post on one side and editing it on the other still merges", () => {
  const remote = post({ headline: "Hello", body: "First" }, { folder: "/archive" });
  const local = post({ headline: "Hello", body: "Rewritten" });

  const result = reconcile({ base: BASE, remote, local });

  assert.equal(result.kind, "merged");
  assert.equal(result.doc.folder, "/archive");
  assert.equal(result.doc.data.body, "Rewritten");
});

test("a deletion on either side is reported, never silently undone", () => {
  // Server deleted, file untouched → the deletion is the news.
  assert.equal(reconcile({ base: BASE, remote: null, local: BASE }).kind, "remote-deleted");
  // File deleted, server untouched → push --prune makes it real; pull must not restore it.
  assert.equal(reconcile({ base: BASE, remote: BASE, local: null }).kind, "local-deleted");
  // Deleted on one side, edited on the other → a human decides.
  const edited = post({ headline: "Edited", body: "First" });
  assert.deepEqual(reconcile({ base: BASE, remote: null, local: edited }).conflicts, [
    "deleted-remotely",
  ]);
  assert.deepEqual(reconcile({ base: BASE, remote: edited, local: null }).conflicts, [
    "deleted-locally",
  ]);
});

test("with no base, two independent creations conflict only where they differ", () => {
  const remote = post({ headline: "Theirs", body: "Same" });
  const local = post({ headline: "Ours", body: "Same" });

  assert.equal(reconcile({ base: null, remote: local, local }).kind, "unchanged");
  assert.deepEqual(reconcile({ base: null, remote, local }).conflicts, ["fields.headline"]);
});

test("a conflict resolves in one direction on request", () => {
  const remote = post({ headline: "Theirs" });
  const local = post({ headline: "Ours" });
  const versions = { base: BASE, remote, local };

  assert.equal(resolveConflict(versions, "theirs").doc.data.headline, "Theirs");
  assert.equal(resolveConflict(versions, "ours").doc.data.headline, "Ours");
  assert.throws(() => resolveConflict(versions, "mine"), /unknown conflict side/);
});

test("publish is an instruction the file carries, not state the merge compares", () => {
  // The projection holds the DRAFT. If `publish` were merged, a pull would delete the
  // instruction the moment the server reported nothing about it.
  const local = post({ headline: "Hello", body: "First" }, { publish: true });

  assert.equal(reconcile({ base: BASE, remote: BASE, local }).kind, "unchanged");
});

// --- the push plan ---

test("push declares the server's values for a post only the server changed", () => {
  // The subtle one: declaring the base values would revert the console edit, and omitting
  // the post would put it outside the document's scope, where --prune would delete it.
  const remote = post({ headline: "Edited in the console", body: "First" });
  const address = addressOf(BASE);

  const plan = planPushContent({
    base: { [address]: BASE },
    remote: { [address]: remote },
    local: { [address]: BASE },
  });

  assert.deepEqual(plan.behind, [address]);
  assert.deepEqual(plan.posts[0].data, remote.data);
  assert.equal(plan.conflicts.length, 0);
});

test("push refuses a post that changed on both sides unless forced", () => {
  const address = addressOf(BASE);
  const versions = {
    base: { [address]: BASE },
    remote: { [address]: post({ headline: "Theirs" }) },
    local: { [address]: post({ headline: "Ours" }) },
  };

  assert.deepEqual(planPushContent(versions).conflicts, [
    { address, fields: ["fields.headline"] },
  ]);
  const forced = planPushContent(versions, { force: true });
  assert.equal(forced.conflicts.length, 0);
  assert.equal(forced.posts[0].data.headline, "Ours");
});

test("push reports posts the file tree no longer has, so --prune is a choice", () => {
  const address = addressOf(BASE);

  const plan = planPushContent({
    base: { [address]: BASE },
    remote: { [address]: BASE },
    local: {},
  });

  assert.deepEqual(plan.removed, [address]);
  assert.equal(plan.posts.length, 0);
});

test("a document is built per site, with every ancestor folder declared", () => {
  const documents = documentsFor(
    [
      post({ headline: "a" }, { url: "/blog/2026/a", folder: "/blog/2026" }),
      post({ headline: "b" }, { site: "Docs", url: "/guide", folder: "/guide" }),
    ],
    { prune: true },
  );

  assert.deepEqual(
    documents.map((document) => document.site),
    ["Blog", "Docs"],
  );
  // /blog is declared even though no post lives there: a prune bounded by /blog/2026 alone
  // would leave the level above outside the document's own scope.
  assert.deepEqual(
    documents[0].folders.map((folder) => folder.path),
    ["/", "/blog", "/blog/2026"],
  );
  assert.equal(documents[0].prune, true);
  assert.deepEqual(ancestorsOf("/a/b"), ["/", "/a", "/a/b"]);
});

test("a document omits publish unless the file said so", () => {
  const [document] = documentsFor([post({ headline: "a" })]);
  assert.equal(document.posts[0].publish, undefined);

  const [explicit] = documentsFor([post({ headline: "a" }, { publish: true })]);
  assert.equal(explicit.posts[0].publish, true);
});

// --- folder markers (SH149) ---

test("a folder marker declares a folder no post names, with its ancestors", () => {
  const [document] = documentsFor([post({ headline: "a" })], {
    markers: [{ site: "Blog", folder: "/archive/2019" }],
  });

  // /blog comes from the post, /archive and /archive/2019 from the marker, and the ancestor
  // rule is the same one for both: a document that declared the leaf alone would put the
  // level above outside its own prune scope.
  assert.deepEqual(
    document.folders.map((folder) => folder.path),
    ["/", "/archive", "/archive/2019", "/blog"],
  );
});

test("a tree of only folders still produces a document", () => {
  // "Scaffold the structure, then fill it" — the case that had no file to write at all
  // before SH149, so the whole first step had to go through the API.
  const documents = documentsFor([], { markers: [{ site: "Docs", folder: "/guide" }] });

  assert.equal(documents.length, 1);
  assert.equal(documents[0].site, "Docs");
  assert.deepEqual(documents[0].posts, []);
  assert.deepEqual(
    documents[0].folders.map((folder) => folder.path),
    ["/", "/guide"],
  );
});

test("a folder on both sides is left alone; one only the server has gets a marker", () => {
  const plan = reconcileFolders({
    base: [{ site: "Blog", folder: "/news" }],
    remote: [
      { site: "Blog", folder: "/news" },
      { site: "Blog", folder: "/archive" },
    ],
    local: [{ site: "Blog", folder: "/news" }],
  });

  assert.deepEqual(plan.unchanged.map((m) => m.folder), ["/news"]);
  assert.deepEqual(plan.write.map((m) => m.folder), ["/archive"]);
  assert.deepEqual(plan.delete, []);
});

test("a folder a post already declares gets no marker of its own", () => {
  // Otherwise the projection doubles in file count to restate every post's own `folder:`,
  // and each of those files becomes another thing to keep in sync.
  const plan = reconcileFolders({
    remote: [
      { site: "Blog", folder: "/blog" },
      { site: "Blog", folder: "/empty" },
    ],
    implied: new Set(["Blog|/blog"]),
  });

  assert.deepEqual(plan.write.map((m) => m.folder), ["/empty"]);
  // Still *agreed*: the folder is on both sides, it just needs no file to say so.
  assert.deepEqual(plan.agreed.map((m) => m.folder).sort(), ["/blog", "/empty"]);
});

test("a marker gone from the server is a deletion; one never agreed is a local creation", () => {
  // The one ambiguity a folder has, and it is the destructive one: a marker the server does
  // not have is either a scaffold not yet pushed or a folder somebody deleted. The base is
  // the third value that tells them apart — guessing either way loses work.
  const plan = reconcileFolders({
    base: [{ site: "Blog", folder: "/gone" }],
    remote: [],
    local: [
      { site: "Blog", folder: "/gone" },
      { site: "Blog", folder: "/brand-new" },
    ],
  });

  assert.deepEqual(plan.delete.map((m) => m.folder), ["/gone"]);
  assert.deepEqual(plan.keep.map((m) => m.folder), ["/brand-new"]);
  // The kept one stays agreed, so the next pull does not read it as a server deletion.
  assert.deepEqual(plan.agreed.map((m) => m.folder), ["/brand-new"]);
});
