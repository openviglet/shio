/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH255 — the round trip is a property, asserted as one.
 *
 * SH253 and SH254 were the same defect in two of the three formats this tree writes: a value
 * the format silently changed, so the file and the server could never agree about it. Both
 * survived for the same reason, and it was not a gap in coverage but a gap in *kind* —
 * `parse(serialize(x)) === x` is universally quantified and every existing test is an example,
 * with fixtures that all happen to be values their format represents exactly. The post
 * round-trip test uses LF bodies, where the CRLF normalization is the identity function; the
 * notes tests use notes without trailing spaces.
 *
 * So this file is one adversarial value list run against all three round trips as a table.
 *
 * WHY THE EXEMPTION LIST IS AS IMPORTANT AS THE ASSERTION
 * A format is allowed not to represent something. A body with trailing whitespace stays in
 * `fields:` on purpose (`bodySafe`), which is the format handling the case *correctly* rather
 * than losing it — the value survives, it just does not become the document body. So the
 * property is "exact, or declared", the shape `ShByteStabilityLintTest` and
 * `ShFolderSiteInvariantTest` already use on the Java side: a value that cannot round trip
 * verbatim has to be named here with the reason, which turns "we lost it" into a decision
 * somebody made.
 *
 * It is the complement of SH253's `projectedPost`, not a replacement. That makes a value the
 * format cannot hold read as *unchanged* at runtime instead of eternally in flight; this makes
 * the set of such values **known** instead of discovered one blocked pull at a time.
 */
import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { assetLocationOf, assetPathFor } from "../src/content-assets.mjs";
import {
  filePathFor,
  parseFolderMarker,
  parsePost,
  projectedPost,
  serializeFolder,
  serializePost,
  sidecarFieldsFor,
  sidecarsOf,
} from "../src/content-files.mjs";
import { parseNotes, serializeNotes } from "../src/content-notes.mjs";

/**
 * The values a text format gets wrong, each named for what makes it dangerous.
 *
 * Every entry is a real shape content takes: a Windows editor writes CRLF, a copy-paste leaves a
 * trailing space, a heading is `Notes: draft`, a product code is `0042`, a tag is `#launch`.
 */
const ADVERSARIAL = [
  { name: "plain", value: "hello" },
  { name: "empty", value: "" },
  { name: "trailing space", value: "hello " },
  { name: "leading space", value: " hello" },
  { name: "trailing newline", value: "hello\n" },
  { name: "CRLF", value: "a\r\nb" },
  { name: "lone CR", value: "a\rb" },
  { name: "interior LF", value: "a\nb" },
  { name: "YAML true", value: "true" },
  { name: "YAML null", value: "null" },
  { name: "YAML tilde", value: "~" },
  { name: "numeric", value: "0042" },
  { name: "float", value: "1.5" },
  { name: "key-like", value: "Notes: draft" },
  { name: "colon no space", value: "a:b" },
  { name: "hash comment", value: "tag #launch" },
  { name: "leading hash", value: "#launch" },
  { name: "leading dash", value: "- not a list" },
  { name: "leading bracket", value: "[not, a, list]" },
  { name: "leading brace", value: "{not: a map}" },
  { name: "quote", value: '"quoted"' },
  { name: "backslash", value: "C:\\path\\to" },
  { name: "tab", value: "a\tb" },
  { name: "BOM", value: "\uFEFFhello" },
  { name: "fence", value: "---" },
  { name: "emoji", value: "ship 🚀" },
  // Added by SH257 for the asset-path round trip, where a separator or a traversal segment is
  // the difference between naming a file and navigating to one. They are ordinary strings to
  // every other format here, which is the point of running one list against all of them.
  { name: "slash", value: "a/b" },
  { name: "backslash name", value: "a\\b" },
  { name: "dot dot", value: ".." },
  { name: "single dot", value: "." },
];

/**
 * Values a format may legitimately not hold verbatim, with the reason **and the refusal**.
 *
 * Keyed `<format>/<value name>`. **Adding an entry is a decision**: it says the value survives
 * somewhere else, or that carrying it is not worth what it would cost. It is never "this is
 * broken and we are moving on" — the whole point of the list is that the second kind has to be
 * argued in writing.
 *
 * ## SH258 — a declared refusal is checked for being the *right* refusal
 *
 * Until SH258 an entry was a sentence and the property was "the value comes back exactly, or the
 * loss is declared". A **throw** satisfied the second branch whatever it said, so three outcomes
 * were indistinguishable to the guard: the refusal the format intends, a refusal from three layers
 * down blaming a symptom, and a `TypeError` from a bug in the check itself. That is not
 * hypothetical — the middle one is exactly what happened during SH257 (`siteDir` trimmed, and
 * `parsePost` then complained that a file disagreed with its own path), and it is why that defect
 * had to be found by *reading* rather than by running.
 *
 * So an entry now declares which kind of loss it is:
 *
 * - `{ why, refuses: /pattern/ }` — the format **throws**, and the message must match. The pattern
 *   pins the part that identifies which rule fired, and nothing more: this is not a demand that
 *   every message be asserted verbatim.
 * - `{ why }` — the format **carries** the value lossily and must not throw. Declaring a carry and
 *   getting a refusal is now a failure too, which is the same blind spot facing the other way.
 */
const DECLARED = {
  // Only two, and writing the list is what proved it. The body's framing has to be stripped on
  // read, and the reflex is to assume that costs every whitespace-ish value — but `bodySafe`
  // already keeps those in `fields:`, so they survive the document exactly and only *stop being
  // the body*. The body is presentation, not storage. What is genuinely lost is the pair the
  // format converts on purpose:
  "body/CRLF": {
    why: "normalized to LF by design; SH253 makes the comparison agree so it is not drift",
  },
  "body/lone CR": { why: "normalized to LF by design, same as CRLF" },
  // SH160's "forgetting is an empty note" is an *API* gesture — it deletes the row — so a read
  // never returns one, and in the file a forgotten note is an absent key. `parseNotes` refuses
  // an entry with no instruction rather than storing a note that says nothing.
  "note/empty": {
    why: "refused: a note is a key and an instruction, and a forget is an absent key",
    refuses: /A note is a key and an instruction/,
  },

  // --- asset paths (SH256): every entry here is a *refusal*, which is the point ---
  // An asset path is one of the round trips whose output is used as a filesystem path, so the
  // format's job is to reject a value rather than to carry it cleverly. It read "the only" until
  // SH402, and that premise is why the defect stood: a post's `site` and `url` build a path the
  // same way, `siteDir` refused a traversing site, and nothing asked the URL beside it.
  //
  // SH258's weakest case, and worth naming: `assetPathFor` answers `null` for two different rules
  // (a traversal segment and a missing name), so the pattern can pin *where* the refusal came from
  // and not *which* of the two fired. Pinning the function still separates a real refusal from a
  // `TypeError` in the thunk, which is the third of the three outcomes this mechanism exists for.
  "asset/empty": { why: "refused: a file with no name is not an asset", refuses: /assetPathFor/ },
  "asset/slash": {
    why: "refused: a separator makes the name claim to be a path (SH256)",
    refuses: /assetPathFor/,
  },
  "asset/backslash": {
    why: "refused: a separator makes the name claim to be a path (SH256)",
    refuses: /assetPathFor/,
  },
  "asset/backslash name": {
    why: "refused: a separator makes the name claim to be a path (SH256)",
    refuses: /assetPathFor/,
  },
  "asset/dot dot": {
    why: "refused: navigates instead of naming — under shio/assets it leaves the tree",
    refuses: /assetPathFor/,
  },
  "asset/single dot": {
    why: "refused: two paths for one asset breaks idempotence (P5)",
    refuses: /assetPathFor/,
  },

  // --- a site's own name (SH257) ---
  // The directory name has to *be* the site's name, because the whole format is built on the
  // path and the frontmatter agreeing. A name with no faithful directory is refused, naming the
  // site, rather than silently trimmed into a tree no later command can read.
  //
  // These are the entries SH258 was written for: three distinct rules live here, and before the
  // patterns any of them could have fired for any of these values without the table noticing.
  "site/empty": {
    why: "refused: a post must say which site it belongs to",
    refuses: /must say which site it belongs to/,
  },
  "site/trailing space": {
    why: "refused: its directory name would not be its name (SH257)",
    refuses: /starts or ends with whitespace/,
  },
  "site/leading space": {
    why: "refused: its directory name would not be its name (SH257)",
    refuses: /starts or ends with whitespace/,
  },
  "site/trailing newline": {
    why: "refused: its directory name would not be its name (SH257)",
    refuses: /starts or ends with whitespace/,
  },
  "site/BOM": {
    why: "refused: a leading BOM is trimmed away, so the directory would not be the name",
    refuses: /starts or ends with whitespace/,
  },
  "site/slash": {
    why: "refused: a site name with a slash has no safe directory name",
    refuses: /has no safe directory name/,
  },
  "site/backslash": {
    why: "refused: a site name with a separator has no safe directory name",
    refuses: /has no safe directory name/,
  },
  "site/backslash name": {
    why: "refused: a site name with a separator has no safe directory name",
    refuses: /has no safe directory name/,
  },
  "site/dot dot": {
    why: "refused: `..` is not a directory name",
    refuses: /has no safe directory name/,
  },
  "site/single dot": {
    why: "refused: `.` is not a directory name",
    refuses: /has no safe directory name/,
  },
};

/**
 * Asserts exactness, or that the loss is declared — and fails a declaration that is now wrong.
 *
 * Takes a thunk because "the format refuses this value" is one of the outcomes, and a refusal is
 * a legitimate way to not hold something: it is the only one that cannot lose work silently.
 */
function assertRoundTrip(format, entry, read) {
  let actual;
  let refused = null;
  try {
    actual = read();
  } catch (error) {
    refused = error;
  }
  const exact = refused === null && actual === entry.value;
  const declaration = DECLARED[`${format}/${entry.name}`];
  if (declaration) {
    // A declaration the format no longer needs is removed, not left to rot into a suppression
    // that hides the next defect at the same spot — SH142's rule for `shio verify --accept`.
    assert.equal(
      exact,
      false,
      `${format}/${entry.name} now round-trips exactly — delete its DECLARED entry.`,
    );
    if (declaration.refuses) {
      assert.ok(
        refused,
        `${format}/${entry.name} declares a refusal matching ${declaration.refuses}, and the ` +
          `format did not refuse — it returned ${JSON.stringify(actual)}. A declared refusal ` +
          "that stopped happening is a rule that quietly changed.",
      );
      assert.match(
        refused.message,
        declaration.refuses,
        `${format}/${entry.name} refused, but not with the rule it declares. This is SH258's ` +
          "whole point: a refusal from three layers down blaming a symptom satisfies " +
          '"the loss is declared" exactly as well as the right one does.',
      );
    } else {
      assert.equal(
        refused,
        null,
        `${format}/${entry.name} declares a lossy carry, not a refusal, and it threw: ` +
          `${refused?.message}. Either add a refuses pattern, or fix the format.`,
      );
    }
    return;
  }
  if (refused) throw refused;
  assert.equal(
    actual,
    entry.value,
    `${format}/${entry.name}: ${JSON.stringify(entry.value)} came back as ` +
      `${JSON.stringify(actual)}. Either fix the format, or add a DECLARED entry saying why ` +
      "this value cannot be held and where it survives instead.",
  );
}

const doc = (data) => ({ site: "Blog", url: "/x", type: "Article", folder: "/", data });

describe("SH255: every format holds an adversarial value, or declares that it does not", () => {
  for (const entry of ADVERSARIAL) {
    it(`a post field holds ${entry.name}`, () => {
      assertRoundTrip("field", entry, () => {
        const input = doc({ value: entry.value });
        return parsePost(filePathFor(input), serializePost(input, null)).data.value;
      });
    });

    it(`a post body holds ${entry.name}`, () => {
      assertRoundTrip("body", entry, () => {
        const input = doc({ text: entry.value });
        // A value the body declines keeps living in `fields:`, so the assertion is about the
        // *document*, not about which half of it the value landed in. That distinction is what
        // took this list from nine declared losses down to two.
        return parsePost(filePathFor(input), serializePost(input, "text")).data.text;
      });
    });

    it(`a memory note holds ${entry.name}`, () => {
      assertRoundTrip("note", entry, () => {
        const notes = [{ key: "k", note: entry.value, author: "ana" }];
        const text = serializeNotes({ site: "Blog", notes });
        return parseNotes("Blog/.memory.yaml", text).notes[0]?.note;
      });
    });

    it(`a sidecar holds ${entry.name}`, () => {
      assertRoundTrip("sidecar", entry, () => {
        const region = {
          name: "Region",
          fields: [{ name: "HTML", widget: "Ace Editor - HTML", ordinal: 1 }],
        };
        const input = { ...doc({ HTML: entry.value }), type: "Region" };
        const fields = sidecarFieldsFor(region);
        const written = sidecarsOf(input, fields);
        // SH191 writes the bytes verbatim, so the file *is* the value — an empty one gets no
        // file, which is the `fields:` fallback and exact there.
        return written.length
          ? written[0].text
          : parsePost(filePathFor(input), serializePost(input, null, fields)).data.HTML;
      });
    });

    it(`an asset path holds ${entry.name} as a file name`, () => {
      assertRoundTrip("asset", entry, () => {
        const path = assetPathFor({ siteName: "Blog", fileName: entry.value }, "/media");
        // SH256 — null is the refusal, and a refusal is a legitimate way not to hold a value.
        // Surfaced as a throw so the DECLARED entry has to say so.
        // SH258 — name the function, so a real refusal is distinguishable from a TypeError
        // in this thunk. `assetPathFor` collapses two rules into one null, which is as far
        // as the pattern can pin this format; the DECLARED entries say so.
        if (path === null) {
          throw new Error(`assetPathFor refused ${JSON.stringify(entry.value)}`);
        }
        return assetLocationOf(path).fileName;
      });
    });

    it(`a post's type holds ${entry.name}`, () => {
      assertRoundTrip("type", entry, () => {
        const input = { ...doc({ v: "1" }), type: entry.value };
        return parsePost(filePathFor(input), serializePost(input, null)).type;
      });
    });

    it(`a post's site holds ${entry.name}`, () => {
      assertRoundTrip("site", entry, () => {
        const input = { ...doc({ v: "1" }), site: entry.value };
        // `filePathFor` is inside the thunk on purpose: `siteDir` refusing a name is one of the
        // outcomes, and the refusal is the *fix* for the site names that have no faithful
        // directory. Before SH257 the whitespace ones produced a file whose frontmatter
        // disagreed with its own path — a tree the pull wrote and no later command could read.
        return parsePost(filePathFor(input), serializePost(input, null)).site;
      });
    });
  }

  it("a folder marker holds every site and chain the tree can name", () => {
    // Not driven by the value table: a marker's two fields are a site name and a name chain,
    // both of which `siteDir`/`normalizeUrl` constrain long before this. What is worth asserting
    // is that the pair survives, including the characters a name is allowed to contain.
    for (const [site, folder] of [
      ["Blog", "/"],
      ["Blog", "/news"],
      ["My Site", "/a b/c-d"],
      ["Site.2", "/über/ação"],
      ["true", "/null"],
      ["0042", "/1.5"],
    ]) {
      const parsed = parseFolderMarker(null, serializeFolder({ site, folder }));
      assert.deepEqual(parsed, { site, folder }, `folder marker lost ${site}${folder}`);
    }
  });
});

describe("SH255: projection is idempotent, so a second sync is not a third state", () => {
  for (const entry of ADVERSARIAL) {
    it(`projecting ${entry.name} twice changes nothing the second time`, () => {
      // SH253's guarantee, stated as a fixed point: whatever the format does to a value, it must
      // do it *once*. A projection that kept changing the value would make the base chase it,
      // which is the permanent `remote-changed` that the half-fix produced.
      const once = projectedPost(doc({ text: entry.value }), "text");
      const twice = projectedPost(once, "text");
      assert.deepEqual(twice.data, once.data, `projecting ${entry.name} is not a fixed point`);
    });
  }
});
