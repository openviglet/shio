/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
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
];

/**
 * Values a format may legitimately not hold verbatim, with the reason.
 *
 * Keyed `<format>/<value name>`. **Adding an entry is a decision**: it says the value survives
 * somewhere else, or that carrying it is not worth what it would cost. It is never "this is
 * broken and we are moving on" — the whole point of the list is that the second kind has to be
 * argued in writing.
 */
const DECLARED = {
  // Only two, and writing the list is what proved it. The body's framing has to be stripped on
  // read, and the reflex is to assume that costs every whitespace-ish value — but `bodySafe`
  // already keeps those in `fields:`, so they survive the document exactly and only *stop being
  // the body*. The body is presentation, not storage. What is genuinely lost is the pair the
  // format converts on purpose:
  "body/CRLF": "normalized to LF by design; SH253 makes the comparison agree so it is not drift",
  "body/lone CR": "normalized to LF by design, same as CRLF",
  // SH160's "forgetting is an empty note" is an *API* gesture — it deletes the row — so a read
  // never returns one, and in the file a forgotten note is an absent key. `parseNotes` refuses
  // an entry with no instruction rather than storing a note that says nothing.
  "note/empty": "refused: a note is a key and an instruction, and a forget is an absent key",
};

const declared = (format, name) => Object.hasOwn(DECLARED, `${format}/${name}`);

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
  if (declared(format, entry.name)) {
    // A declaration the format no longer needs is removed, not left to rot into a suppression
    // that hides the next defect at the same spot — SH142's rule for `shio verify --accept`.
    assert.equal(
      exact,
      false,
      `${format}/${entry.name} now round-trips exactly — delete its DECLARED entry.`,
    );
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
