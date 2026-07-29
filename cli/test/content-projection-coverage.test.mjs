/**
 * SH348 — what the content projection carries, and what it drops on purpose.
 *
 * ## Why this table exists
 *
 * SH348 was filed as "scheduled publishing's sibling: give `translate` an agent form", and the design
 * note said the file projection would decide the shape rather than the op. Measured, it decides more
 * than that — the op is the **last** of four layers, and the first three are the ones that matter:
 *
 *   1. `/agent/read` never returns a post's `locale`. Not projected, not omitted — *absent*: the string
 *      appears zero times in `ShAgentReadService`. The context pack reports the **instance's** default
 *      locale, which is a different fact.
 *   2. So `projectedPost` cannot carry it, and does not: nine keys, none of them locale (asserted below).
 *   3. So a desired-state document cannot declare it.
 *   4. So `post.translate` would create content that `shio push --content` **silently relocalizes to
 *      `en`** on the next run, because `create` defaults the locale and nothing in the tree says otherwise.
 *
 * That makes `locales: true` a broader half-truth than SH338's inventory captured: not "there is no
 * agent form for translate" but "the agent cannot see which locale a post is in".
 *
 * ## Why it is a test rather than a note
 *
 * The loss is silent and the existing guard cannot see it. `content-round-trip.test.mjs` (SH255) is the
 * instrument for exactly this class — a value the format cannot hold must be declared — and it tests
 * **values of fields the format has**, not **fields the format lacks**. A blind spot worth writing down
 * next to the two the SH338/SH339 instruments turned out to have.
 *
 * So this pins the key set. Adding a field to the projection fails it, which is the point: the fix for
 * SH348 starts here, and it should not be possible to do it without deleting a line from the table.
 */
import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { projectedPost } from "../src/content-files.mjs";

/** Everything `projectedPost` keeps. Sorted, so a diff reads as one addition or one removal. */
const PROJECTED = [
  "bodyField",
  "data",
  "folder",
  "locale",
  "movedFrom",
  "publish",
  "sidecarFiles",
  "site",
  "type",
  "url",
];

/**
 * What a post carries that the projection drops, and why.
 *
 * A `null` reason would be the thing to avoid: every entry here is either a deliberate decision or a
 * known gap with a task, and the difference has to be readable. Remove an entry when the projection
 * starts carrying it — this test fails until you do.
 */
const DROPPED = {
  // `locale` was here and is not any more: SH348's steps 1 and 2 put it on `/agent/read` and then in
  // the frontmatter, and the table failing on the way is what a closing gap is supposed to look like.
  translationGroupId: "SH348 — the last gap, and the one that needs an intent channel rather than a "
    + "field: an agent must not invent a group id, so the tree should say `translationOf: <url>` and "
    + "let the server resolve it, the way SH150's `moves` says `from` rather than an id.",
  title: "derived by the writer from whichever field carries isTitle (SH277), so storing it would be "
    + "a second copy that can disagree with `data`.",
  summary: "derived the same way as `title`.",
  id: "SH79 addresses content by `post:<site>/<url>`; a UUID in a file is the thing Block I removes.",
  state: "only the DRAFT projects (Block I's rule), so there is nothing to record. `publish:` is an "
    + "*instruction* push reads and pull never writes.",
};

describe("SH348: the content projection's field coverage", () => {
  it("carries exactly these keys", () => {
    const projected = projectedPost(
      {
        site: "Blog",
        url: "/sobre",
        type: "Article",
        folder: "/",
        locale: "pt-br",
        translationGroupId: "grp-1",
        title: "Sobre",
        fields: { TITLE: "Sobre" },
      },
      "TITLE",
    );

    assert.deepEqual(
      Object.keys(projected).sort(),
      PROJECTED,
      "the projection's key set changed. If a field was added, remove it from DROPPED below and say "
        + "so in the CHANGELOG — a field arriving silently is how a round trip starts losing "
        + "something new.",
    );
  });

  it("carries a non-default locale and still drops the sibling link", () => {
    const projected = projectedPost(
      { site: "Blog", url: "/sobre", type: "Article", locale: "pt-br", translationGroupId: "grp-1" },
      "TITLE",
    );

    assert.equal(
      projected.locale,
      "pt-br",
      "SH348 step 2: a translated page has to be able to say so, or Block I's promise that the tree "
        + "is a complete description of a site rebuilds it as `en` somewhere else",
    );
    assert.equal(
      projected.translationGroupId,
      undefined,
      "still dropped, and declared: the sibling link needs an intent channel, not a field",
    );
    assert.ok(DROPPED.translationGroupId.includes("SH348"));
  });

  it("a default-locale post is byte-identical to before SH348", () => {
    // The whole reason `/agent/read` omits a default locale (step 1) is that a monolingual tree must
    // not change. If this ever fails, every file in every existing project gains a line.
    const projected = projectedPost({ site: "Blog", url: "/about", type: "Article" }, "TITLE");
    assert.equal(projected.locale, undefined);
  });

  it("declares a reason for everything it drops, and nothing it keeps", () => {
    for (const [field, reason] of Object.entries(DROPPED)) {
      assert.ok(reason && reason.length > 20, `${field}: a dropped field needs a real reason`);
      assert.ok(
        !PROJECTED.includes(field),
        `${field} is declared dropped and is in fact projected — the table is describing something `
          + `that is no longer true, which is worse than no table (SH142's rule).`,
      );
    }
  });
});
