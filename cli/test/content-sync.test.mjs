/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
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
  baseEntryFor,
  compareFingerprints,
  detectFolderMoves,
  digestsOf,
  documentsFor,
  fingerprint,
  opsOf,
  planPushContent,
  reconcile,
  reconcileFolders,
  resolveConflict,
  same,
  splitDocuments,
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

// --- a large tree splits, except when it prunes (SH199) ---

/** A document of `n` posts, as `documentsFor` would build it. */
function bigDocument(n, { prune = false, publish = false } = {}) {
  const posts = [];
  for (let index = 0; index < n; index += 1) {
    posts.push({ url: `/p${index}`, type: "Article", folder: "/blog", data: { headline: `p${index}` },
      ...(publish ? { publish: true } : {}) });
  }
  return { site: "Blog", folders: [{ path: "/" }, { path: "/blog" }], posts, prune };
}

test("ops are counted the way the server counts them, publishes included", () => {
  // Easy to forget and the reason a document of 1000 posts is 2000 ops: a declared `publish`
  // compiles to a second op on top of the upsert.
  assert.equal(opsOf(bigDocument(10)), 12);
  assert.equal(opsOf(bigDocument(10, { publish: true })), 22);
  assert.equal(opsOf({ folders: [{ path: "/" }], moves: [{ from: "/a", to: "/b" }], posts: [] }), 2);
});

test("a document past the ceiling becomes several that each fit", () => {
  const parts = splitDocuments([bigDocument(10)], { maxOps: 6 });

  assert.equal(parts.length, 3);
  assert.ok(parts.every((part) => opsOf(part) <= 6), "every part fits the ceiling");
  // The union is exactly the whole: upserts are independent and idempotent, so the parts
  // converge where one document would have.
  assert.deepEqual(
    parts.flatMap((part) => part.posts.map((post) => post.url)),
    bigDocument(10).posts.map((post) => post.url),
  );
  // Each part carries the folders, because whichever posts landed in it need them.
  assert.ok(parts.every((part) => part.folders.length === 2));
});

test("a document that already fits is passed through untouched", () => {
  const whole = bigDocument(3);
  const parts = splitDocuments([whole], { maxOps: 1500 });

  assert.equal(parts.length, 1);
  assert.equal(parts[0], whole, "the same object — a no-op costs nothing");
});

test("a pruning document is never split, however large", () => {
  // The finding: every document `documentsFor` builds declares the site root (folders come from
  // `ancestorsOf`, which always yields `/`), so each part of a split would prune the *whole
  // site* and delete everything the other parts declare. The command turns this into a refusal.
  const parts = splitDocuments([bigDocument(10, { prune: true })], { maxOps: 6 });

  assert.equal(parts.length, 1);
  assert.equal(parts[0].prune, true);
  assert.equal(parts[0].posts.length, 10);
});

test("a split part never prunes, even inside a pruning run", () => {
  // Belt and braces: if a caller ever splits a document that carried `prune`, the parts must
  // not inherit it — one prune per part is the failure mode this whole rule exists to prevent.
  const parts = splitDocuments([{ ...bigDocument(10), prune: false }], { maxOps: 6 });
  assert.ok(parts.every((part) => part.prune === false));
});

test("moves ride every part, because a move that already happened is a no-op", () => {
  const whole = { ...bigDocument(6), moves: [{ from: "/old", to: "/p0" }] };
  const parts = splitDocuments([whole], { maxOps: 5 });

  assert.ok(parts.length > 1);
  // Repeating it is cheaper than working out which part its post landed in, and SH150 made a
  // move whose source is gone a noop precisely so that repeating is safe.
  assert.ok(parts.every((part) => part.moves.length === 1));
});

// --- the base is fingerprints, not values (SH148) ---

test("a digest base decides the same three cases a full base did", () => {
  const digested = baseEntryFor(BASE);

  // No values survive in it at all.
  assert.deepEqual(Object.keys(digested).sort(), ["digest", "site", "url"]);
  assert.equal(JSON.stringify(digested).includes("Hello"), false);

  // …and every branch of the merge still resolves as before.
  assert.equal(reconcile({ base: digested, remote: BASE, local: BASE }).kind, "unchanged");
  assert.equal(
    reconcile({ base: digested, remote: post({ headline: "Theirs", body: "First" }), local: BASE }).kind,
    "remote-changed",
  );
  assert.equal(
    reconcile({ base: digested, remote: BASE, local: post({ headline: "Ours", body: "First" }) }).kind,
    "local-changed",
  );
  const both = reconcile({
    base: digested,
    remote: post({ headline: "Theirs", body: "First" }),
    local: post({ headline: "Ours", body: "First" }),
  });
  assert.equal(both.kind, "conflict");
  assert.deepEqual(both.conflicts, ["fields.headline"]);
});

test("a field only the server has takes the server's value, not a conflict", () => {
  // The trap in going digest-only: absence used to compare equal to absence, and a fingerprint
  // map simply has no key for a field nobody had. Without treating "not here" as its own
  // fingerprint, a field the server added came back a *conflict* — and this needs both sides to
  // have moved, or `reconcile` short-circuits on "only the server changed" and never merges.
  const result = reconcile({
    base: baseEntryFor(post({ headline: "Hello" })),
    remote: post({ headline: "Hello", subtitle: "New on the server" }),
    local: post({ headline: "Retitled locally" }),
  });

  assert.equal(result.kind, "merged");
  assert.equal(result.doc.data.subtitle, "New on the server");
  assert.equal(result.doc.data.headline, "Retitled locally");
});

test("a fingerprint is stable and order-independent, like the values it replaces", () => {
  assert.equal(fingerprint({ a: 1, b: 2 }), fingerprint({ b: 2, a: 1 }));
  assert.notEqual(fingerprint("a"), fingerprint("b"));
  // A digested entry re-digested is itself, so re-writing a snapshot read from disk is not a
  // hash of a hash.
  const once = baseEntryFor(BASE);
  assert.deepEqual(baseEntryFor(once), once);
});

// --- a rename is a move, not a re-creation (SH150) ---

/** The three sides for one renamed post: it was at `from`, the file is now at `to`. */
function renameSides(from, to, { data = { headline: "Hello" }, local: localExtra = {} } = {}) {
  const was = post(data, { url: from });
  return {
    base: { [addressOf(was)]: was },
    remote: { [addressOf(was)]: was },
    local: { [`post:Blog${to}`]: post(data, { url: to, ...localExtra }) },
  };
}

test("a renamed file with unchanged content is a move, and stops being a deletion", () => {
  const plan = planPushContent(renameSides("/blog/old", "/blog/new"));

  assert.deepEqual(plan.moves, [
    { site: "Blog", from: "/blog/old", to: "/blog/new", folder: null, explicit: false },
  ]);
  // The half that matters as much as emitting the move: the old address must leave `removed`,
  // or `--prune` deletes the very post the move preserved.
  assert.deepEqual(plan.removed, []);
  assert.deepEqual(plan.ambiguousMoves, []);
});

test("movedFrom declares a rename that a rewrite would otherwise hide", () => {
  // The case content-matching cannot reach: after a rewrite there is nothing left to match on,
  // so the only thing that can still connect the two is the author saying so.
  const sides = renameSides("/blog/old", "/blog/new");
  sides.local["post:Blog/blog/new"] = post(
    { headline: "Rewritten from scratch" },
    { url: "/blog/new", movedFrom: "/blog/old" },
  );

  const plan = planPushContent(sides);

  assert.deepEqual(plan.moves, [
    { site: "Blog", from: "/blog/old", to: "/blog/new", folder: null, explicit: true },
  ]);
  assert.deepEqual(plan.removed, []);
});

test("a rename that also changes folder carries both axes in one move", () => {
  const sides = renameSides("/blog/old", "/blog/new", {
    local: { folder: "/archive", movedFrom: "/blog/old" },
  });

  assert.deepEqual(planPushContent(sides).moves, [
    { site: "Blog", from: "/blog/old", to: "/blog/new", folder: "/archive", explicit: true },
  ]);
});

test("two files that could each be the same rename are refused, not guessed", () => {
  // Either could be the rename and the other the copy. Picking wrong moves the wrong post's
  // id and history, and nothing in the files says which is which.
  const was = post({ headline: "Hello" }, { url: "/blog/old" });
  const plan = planPushContent({
    base: { [addressOf(was)]: was },
    remote: { [addressOf(was)]: was },
    local: {
      "post:Blog/blog/a": post({ headline: "Hello" }, { url: "/blog/a" }),
      "post:Blog/blog/b": post({ headline: "Hello" }, { url: "/blog/b" }),
    },
  });

  assert.equal(plan.moves.length, 0);
  assert.equal(plan.ambiguousMoves.length, 2);
  assert.ok(plan.ambiguousMoves.every((row) => row.candidates.includes("post:Blog/blog/old")));
});

test("a movedFrom naming a post the tree still declares is refused", () => {
  const was = post({ headline: "Hello" }, { url: "/blog/old" });
  const plan = planPushContent({
    base: { [addressOf(was)]: was },
    remote: { [addressOf(was)]: was },
    local: {
      "post:Blog/blog/old": was,
      "post:Blog/blog/new": post({ headline: "Other" }, { url: "/blog/new", movedFrom: "/blog/old" }),
    },
  });

  assert.equal(plan.moves.length, 0);
  assert.match(plan.ambiguousMoves[0].reason, /still declares/);
});

test("a stale movedFrom is a no-op, so a pushed rename can be pushed again", () => {
  // The state right after a successful move: the file still says where it came from, and
  // nothing is there any more. Erroring here would make the second push of the same tree fail.
  const plan = planPushContent({
    base: {},
    remote: {},
    local: {
      "post:Blog/blog/new": post({ headline: "Hello" }, { url: "/blog/new", movedFrom: "/blog/old" }),
    },
  });

  assert.deepEqual(plan.moves, []);
  assert.deepEqual(plan.ambiguousMoves, []);
});

test("a folder change on a post that stays put is a move, because upsert does not relocate", () => {
  // Not a rename at all. It is here because `post.upsert` deliberately leaves an existing
  // post's folder alone, so before SH150 editing `folder:` was a push that reported success
  // and moved nothing.
  const server = post({ headline: "Hello" }, { folder: "/blog" });
  const plan = planPushContent({
    base: { [addressOf(server)]: server },
    remote: { [addressOf(server)]: server },
    local: { [addressOf(server)]: post({ headline: "Hello" }, { folder: "/archive" }) },
  });

  assert.deepEqual(plan.moves, [
    { site: "Blog", from: "/blog/hello", to: null, folder: "/archive", explicit: false },
  ]);
});

test("a probable rename it cannot prove is reported, never performed", () => {
  // Same type, different content: plausible, unprovable. Saying nothing would let a
  // delete-plus-create destroy the post's history in silence.
  const was = post({ headline: "Old headline" }, { url: "/blog/old" });
  const plan = planPushContent({
    base: { [addressOf(was)]: was },
    remote: { [addressOf(was)]: was },
    local: { "post:Blog/blog/new": post({ headline: "New headline" }, { url: "/blog/new" }) },
  });

  assert.deepEqual(plan.moves, []);
  assert.deepEqual(plan.probableMoves, [
    { from: "post:Blog/blog/old", to: "post:Blog/blog/new" },
  ]);
  // Still a deletion as far as the push is concerned — it was not proven to be a move.
  assert.deepEqual(plan.removed, ["post:Blog/blog/old"]);
});

test("a document carries its moves, sorted, and omits the key when there are none", () => {
  const [withMove] = documentsFor([post({ headline: "a" }, { url: "/b" })], {
    moves: [
      { site: "Blog", from: "/z", to: "/b", folder: null },
      { site: "Blog", from: "/a", to: null, folder: "/news" },
    ],
  });

  assert.deepEqual(withMove.moves, [
    { from: "/a", to: undefined, folder: "/news" },
    { from: "/z", to: "/b", folder: undefined },
  ]);
  // A document without renames serializes exactly as it did before `moves` existed.
  const [plain] = documentsFor([post({ headline: "a" })]);
  assert.equal("moves" in plain, false);
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

// --- renaming a folder is a move, not a delete plus a create (SH211) ---

const declaring = (...keys) => new Set(keys);

test("one folder gone and one arrived in the same site is a rename", () => {
  // The defect: `--prune` would delete /news, and a folder delete takes every post beneath it.
  const { moves, ambiguous } = detectFolderMoves({
    base: [{ site: "Blog", folder: "/news" }],
    local: [{ site: "Blog", folder: "/updates" }],
    declared: declaring("Blog|/", "Blog|/updates"),
  });

  assert.deepEqual(ambiguous, []);
  assert.deepEqual(moves, [{ site: "Blog", from: "/news", to: "/updates", explicit: false }]);
});

test("a renamed section with subfolders is one move, not one per folder", () => {
  // `folder.move` takes the subtree with it, so the descendants are explained by the root pair.
  // Emitting a move per folder would try to move /news/2026 after its parent had already gone.
  const { moves, ambiguous } = detectFolderMoves({
    base: [
      { site: "Blog", folder: "/news" },
      { site: "Blog", folder: "/news/2026" },
      { site: "Blog", folder: "/news/2026/q1" },
    ],
    local: [
      { site: "Blog", folder: "/updates" },
      { site: "Blog", folder: "/updates/2026" },
      { site: "Blog", folder: "/updates/2026/q1" },
    ],
    declared: declaring("Blog|/", "Blog|/updates", "Blog|/updates/2026", "Blog|/updates/2026/q1"),
  });

  assert.deepEqual(ambiguous, []);
  assert.deepEqual(moves, [{ site: "Blog", from: "/news", to: "/updates", explicit: false }]);
});

test("two folders gone and two arrived is refused, not guessed", () => {
  // Either pairing is plausible and moving the wrong section moves the wrong pages. The
  // remedy is one line of `movedFrom:`, which is why this is a refusal rather than a warning.
  const { moves, ambiguous } = detectFolderMoves({
    base: [
      { site: "Blog", folder: "/news" },
      { site: "Blog", folder: "/press" },
    ],
    local: [
      { site: "Blog", folder: "/updates" },
      { site: "Blog", folder: "/releases" },
    ],
    declared: declaring("Blog|/", "Blog|/updates", "Blog|/releases"),
  });

  assert.deepEqual(moves, []);
  // One row per source that cannot be resolved, naming it — more actionable than one aggregate
  // row, because `movedFrom:` is written on a directory and the message says which.
  assert.deepEqual(ambiguous.map((row) => row.from), ["/news", "/press"]);
  for (const row of ambiguous) {
    assert.deepEqual(row.candidates, ["/releases", "/updates"]);
  }
});

test("`movedFrom:` decides an ambiguity the shapes cannot", () => {
  const { moves, ambiguous } = detectFolderMoves({
    base: [
      { site: "Blog", folder: "/news" },
      { site: "Blog", folder: "/press" },
    ],
    local: [
      { site: "Blog", folder: "/updates", movedFrom: "/news" },
      { site: "Blog", folder: "/releases", movedFrom: "/press" },
    ],
    declared: declaring("Blog|/", "Blog|/updates", "Blog|/releases"),
  });

  assert.deepEqual(ambiguous, []);
  assert.deepEqual(moves.map((move) => [move.from, move.to, move.explicit]), [
    ["/news", "/updates", true],
    ["/press", "/releases", true],
  ]);
});

test("a shape that matches picks the right pair out of several", () => {
  // Stronger than one-of-each: /news has a child and /press does not, so only one of the two
  // arrivals can be /news — and that is decided without asking the author for anything.
  const { moves, ambiguous } = detectFolderMoves({
    base: [
      { site: "Blog", folder: "/news" },
      { site: "Blog", folder: "/news/2026" },
      { site: "Blog", folder: "/press" },
    ],
    local: [
      { site: "Blog", folder: "/updates" },
      { site: "Blog", folder: "/updates/2026" },
      { site: "Blog", folder: "/releases" },
    ],
    declared: declaring("Blog|/", "Blog|/updates", "Blog|/updates/2026", "Blog|/releases"),
  });

  assert.deepEqual(ambiguous, []);
  assert.deepEqual(moves.map((move) => [move.from, move.to]), [
    ["/news", "/updates"],
    ["/press", "/releases"],
  ]);
});

test("a `movedFrom:` naming a folder the tree still declares is refused", () => {
  // Two markers, one claiming to be the new home of a folder the other still declares. One of
  // them is wrong and no reading of the pair is safe.
  const { moves, ambiguous } = detectFolderMoves({
    base: [{ site: "Blog", folder: "/news" }],
    local: [
      { site: "Blog", folder: "/news" },
      { site: "Blog", folder: "/updates", movedFrom: "/news" },
    ],
    declared: declaring("Blog|/", "Blog|/news", "Blog|/updates"),
  });

  assert.deepEqual(moves, []);
  assert.equal(ambiguous.length, 1);
  assert.match(ambiguous[0].reason, /still declares/);
});

test("a folder a post declares is not gone, even with no marker", () => {
  // A marker declares a folder *nothing else does*, so the declared set has to include every
  // ancestor a projected post names — otherwise a post's own folder reads as a rename source.
  const { moves, ambiguous } = detectFolderMoves({
    base: [{ site: "Blog", folder: "/blog" }],
    local: [],
    declared: declaring("Blog|/", "Blog|/blog"),
  });

  assert.deepEqual(moves, []);
  assert.deepEqual(ambiguous, []);
});

test("nothing arrived, so a folder that left is a deletion and not a move", () => {
  const { moves, ambiguous } = detectFolderMoves({
    base: [{ site: "Blog", folder: "/gone" }],
    local: [],
    declared: declaring("Blog|/"),
  });

  assert.deepEqual(moves, []);
  assert.deepEqual(ambiguous, []);
});

test("the site root is never a move source or target", () => {
  // Every folder address resolves from it, and the server refuses a move onto or off it.
  const { moves } = detectFolderMoves({
    base: [{ site: "Blog", folder: "/" }],
    local: [{ site: "Blog", folder: "/" }],
    declared: declaring("Blog|/"),
  });

  assert.deepEqual(moves, []);
});

test("a rename never pairs across sites", () => {
  const { moves, ambiguous } = detectFolderMoves({
    base: [{ site: "Blog", folder: "/news" }],
    local: [{ site: "Docs", folder: "/updates" }],
    declared: declaring("Blog|/", "Docs|/", "Docs|/updates"),
  });

  assert.deepEqual(moves, []);
  assert.deepEqual(ambiguous, []);
});

test("a document carries the rename on the folder entry that is its destination", () => {
  const documents = documentsFor([], {
    markers: [{ site: "Blog", folder: "/updates" }],
    folderMoves: [{ site: "Blog", from: "/news", to: "/updates" }],
  });

  assert.deepEqual(documents[0].folders, [
    { path: "/" },
    { path: "/updates", movedFrom: "/news" },
  ]);
});

test("a document with no rename carries no movedFrom anywhere", () => {
  // P5/P3: the same tree serializes exactly as it did before this existed.
  const documents = documentsFor([], { markers: [{ site: "Blog", folder: "/updates" }] });

  assert.deepEqual(documents[0].folders, [{ path: "/" }, { path: "/updates" }]);
});

// --- a fingerprint collision is reported, not dropped (SH215) ---

/**
 * A base whose fingerprint for `headline` hashes the same as the local value and cannot be
 * describing it — which is exactly the observable state a real 64-bit collision produces, and the
 * only way to test one without finding one.
 */
const collidingBase = (value, digestOf) => ({
  site: "Blog",
  url: "/blog/hello",
  digest: { ...digestOf, ".headline": forge(digestOf[".headline"]) },
});

/** The same hash, a length that cannot belong to it. */
function forge(fingerprintValue) {
  const [hash] = String(fingerprintValue).split(".");
  return `${hash}.9999`;
}

test("a fingerprint carries the canonical form's byte length beside its hash", () => {
  assert.match(fingerprint("hello"), /^[0-9a-f]{16}\.\d+$/);
  // `stable` quotes a string, so the length is the canonical form's and not the value's — which is
  // the form the hash is taken over, so the two halves describe the same bytes.
  assert.equal(fingerprint("hello").split(".")[1], "7");
});

test("equal hashes with equal lengths are the same field", () => {
  assert.equal(compareFingerprints(fingerprint("a"), fingerprint("a")), "same");
  assert.equal(compareFingerprints(fingerprint("a"), fingerprint("b")), "different");
});

test("equal hashes with different lengths are a collision, not an unchanged field", () => {
  // The whole of SH215: before this the hash match alone said "unchanged", so the edit was
  // dropped and the run reported success. Two different lengths *prove* two different values.
  const real = fingerprint("hello");
  assert.equal(compareFingerprints(real, forge(real)), "collision");
});

test("a length-free fingerprint compares by hash alone, so an old base still merges", () => {
  // A v2 snapshot carries no length. Reading it as "different" would make every field of every
  // existing project look changed on the first sync after an upgrade.
  const [hash] = fingerprint("hello").split(".");
  assert.equal(compareFingerprints(hash, fingerprint("hello")), "same");
  assert.equal(compareFingerprints(hash, fingerprint("other")), "different");
  assert.equal(compareFingerprints(hash, forge(fingerprint("hello"))), "same");
});

test("a collision is a conflict that names itself, and nothing is written", () => {
  const local = post({ headline: "Mine", body: "<p>Same</p>" });
  const remote = post({ headline: "Theirs", body: "<p>Same</p>" });
  const base = collidingBase("Mine", digestsOf(local));

  const result = reconcile({ base, remote, local });

  assert.equal(result.kind, "conflict");
  assert.deepEqual(result.collisions, ["fields.headline"]);
  // Also an ordinary conflict, because everything a conflict causes is what should happen: the
  // file is left alone and the run stops. `collisions` is the *reason*, not a different outcome.
  assert.ok(result.conflicts.includes("fields.headline"));
});

test("without the length that same state is silently read as unchanged", () => {
  // The defect, stated as a test rather than as a comment: strip the lengths and the base's
  // forged fingerprint matches, so the merge takes the remote value and the local edit is gone
  // with nothing said. This is what SH148 shipped and what SH215 makes impossible to miss.
  const local = post({ headline: "Mine", body: "<p>Same</p>" });
  const remote = post({ headline: "Theirs", body: "<p>Same</p>" });
  const hashOnly = Object.fromEntries(
    Object.entries(collidingBase("Mine", digestsOf(local)).digest)
      .map(([key, value]) => [key, String(value).split(".")[0]]),
  );

  const result = reconcile({ base: { site: "Blog", url: "/blog/hello", digest: hashOnly }, remote, local });

  assert.equal(result.kind, "remote-changed");
  assert.equal(result.doc.data.headline, "Theirs", "the local edit is gone, and nothing said so");
});

test("a whole-document comparison cannot hide a collision either", () => {
  // `same()` used to compare one serialized digest against another, which answers "not the same"
  // for a collision — right by accident. Field by field it is right on purpose, and it is also
  // what lets a v2 base compare at all.
  const local = post({ headline: "Mine" });
  const base = collidingBase("Mine", digestsOf(local));

  assert.equal(same(base, local), false);
});

/*
 * SH1122 — a site's own settings, from the tree, as a `settings` block on the document.
 *
 * The server compiles it to one more `site.upsert`, which is the op that already fills each of these
 * and already refuses to replace a value somebody chose (SH170's P7 line). So this adds a way to
 * *author* a site's settings and no new rule about them.
 */
test("SH1122: a site marker becomes the document's settings block", () => {
  const [document] = documentsFor([], {
    siteMarkers: [{
      site: "Blog",
      url: "https://blog.example",
      furl: "blog",
      description: "The company blog",
      locales: { default: "en", enabled: ["en"] },
    }],
  });

  assert.equal(document.site, "Blog",
    "SH1122: a tree holding nothing but a `_site.md` still emits a document — configuring a site is "
    + "the step before scaffolding it, and it was the one step with no file to do it in");
  assert.deepEqual(document.settings, {
    url: "https://blog.example",
    furl: "blog",
    description: "The company blog",
    locales: { default: "en", enabled: ["en"] },
  });
  assert.ok(!("site" in document.settings),
    "SH1122: the document already names its site, and a second spelling of it is a second thing "
    + "that can disagree");
});

test("SH1122: a document for a site with no marker is byte-identical to before", () => {
  const [document] = documentsFor([
    { site: "Blog", url: "/hello", type: "Post", folder: "/", data: {} },
  ]);
  assert.ok(!("settings" in document),
    "SH1122: the key is deleted rather than written as null, so every existing tree's document "
    + "serializes exactly as it did (P5)");
  const [blank] = documentsFor([], { siteMarkers: [{ site: "Blog" }] });
  assert.ok(!("settings" in blank),
    "SH1122: and a marker that declares nothing declares nothing — a blank `settings` would ask the "
    + "server to fill four fields with empty strings");
});

test("SH1122: a marker cannot smuggle a key the projection refuses", () => {
  // Belt and braces with the server's own allow-list, and deliberately so: a document is written by
  // whoever is pushing, so the rule has to hold for a hand-written one too. What this side adds is
  // that the TREE never writes a key the server would drop — a file whose contents are silently
  // ignored is the failure §LXXIV.12 is about, one layer along.
  const [document] = documentsFor([], {
    siteMarkers: [{
      site: "Blog",
      url: "https://blog.example",
      scratch: true,
      bindings: { Page: "Layout" },
      searchablePostTypes: "Page",
      formSuccess: "/thanks",
    }],
  });
  assert.deepEqual(Object.keys(document.settings), ["url"],
    "SH1122: `scratch` is settable on a create and never afterwards, `bindings` have a field of "
    + "their own, and the other two have no write op — none of them reaches the op through here");
});

test("SH1301: pull carries a server gate into the marker, and a gate edited in the file survives until it is pushed", () => {
  const plain = { site: "Blog", folder: "/members" };
  const gated = { ...plain, visibility: "members", groups: ["Members"] };

  const serverGated = reconcileFolders({ base: [plain], remote: [gated], local: [plain] });
  assert.deepEqual(serverGated.write, [gated], "SH1301: the server gated it and the file did not move, so the file follows");
  assert.deepEqual(serverGated.agreed, [gated]);

  const fileGated = reconcileFolders({ base: [plain], remote: [plain], local: [gated] });
  assert.deepEqual(fileGated.write, [], "SH1301: the file gated it, so it stays for the next push");
  assert.deepEqual(fileGated.agreed, [plain],
    "SH1301: and the unpushed gate is not recorded as agreed, or the next pull would undo it");

  const implied = reconcileFolders({ base: [], remote: [gated], local: [], implied: new Set(["Blog|/members"]) });
  assert.deepEqual(implied.write, [gated],
    "SH1301: a gated folder gets a marker even when a post already implies the folder");
});

test("SH1301: push declares a marker's gate on its folder entry, and nothing for a marker without one", () => {
  const [doc] = documentsFor([], {
    sites: ["Blog"],
    markers: [
      { site: "Blog", folder: "/members", visibility: "members", groups: ["Members"] },
      { site: "Blog", folder: "/news" },
    ],
  });
  const byPath = Object.fromEntries(doc.folders.map((folder) => [folder.path, folder]));
  assert.deepEqual(byPath["/members"], { path: "/members", visibility: "members", groups: ["Members"] });
  assert.deepEqual(byPath["/news"], { path: "/news" });
});

test("SH1301: a post's gate merges like its folder: the server's gate reaches the file, and push declares it", () => {
  const plain = { site: "Blog", url: "/vip", type: "Page", folder: "/", data: { title: "Vip" } };
  const gated = { ...plain, visibility: "members", groups: ["Members"] };
  const merged = reconcile({ base: plain, remote: gated, local: plain });
  assert.equal(merged.kind, "remote-changed", "SH1301: the server gated it and the file did not move");
  assert.equal(merged.doc.visibility, "members");

  const [document] = documentsFor([gated], { sites: ["Blog"] });
  assert.equal(document.posts[0].visibility, "members");
  assert.deepEqual(document.posts[0].groups, ["Members"]);
  const [ungated] = documentsFor([plain], { sites: ["Blog"] });
  assert.equal(ungated.posts[0].visibility, undefined, "SH1301: no key, no gate op");
});
