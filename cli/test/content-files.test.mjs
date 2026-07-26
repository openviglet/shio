/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH86 — the filesystem projection format.
 *
 * The properties under test are the ones a two-way sync stands on: the round trip is
 * exact (a pull followed by a push must not report a change nobody made), the
 * serialization is canonical (so a diff means an edit and not a reordering), a value that
 * would not survive the pretty form is kept in a shape that does, and a file whose path
 * and frontmatter disagree is refused rather than guessed at.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import {
  bodyFieldFor,
  canonicalize,
  filePathFor,
  locationOf,
  parsePost,
  pathToUrl,
  serializePost,
  urlToPath,
} from "../src/content-files.mjs";

const DOC = {
  site: "Blog",
  url: "/blog/hello",
  type: "Article",
  folder: "/blog",
  publish: true,
  data: {
    headline: "Hello",
    tags: ["news", "release"],
    content: "<p>First</p>\n<p>Second</p>",
    seoTitle: "",
  },
};

function roundTrip(doc, bodyField = "content") {
  const text = serializePost(doc, bodyField);
  return { text, parsed: parsePost(filePathFor(doc), text) };
}

test("a post projects to a Markdown file whose path is its URL", () => {
  assert.equal(filePathFor(DOC), "Blog/blog/hello.md");
  assert.equal(urlToPath("/"), "index.md");
  assert.equal(pathToUrl("blog/hello.md"), "/blog/hello");
  assert.equal(pathToUrl("index.md"), "/");
  assert.deepEqual(locationOf("Blog/blog/hello.md"), { site: "Blog", url: "/blog/hello" });
});

test("the body is the rich-text field, verbatim, after the frontmatter", () => {
  const { text } = roundTrip(DOC);

  assert.match(text, /^---\nsite: Blog\nurl: \/blog\/hello\ntype: Article\nfolder: \/blog\n/);
  assert.match(text, /publish: true\nbody: content\n/);
  // The body is raw text, not an escaped string: that is what makes `Edit` cheap.
  assert.match(text, /---\n<p>First<\/p>\n<p>Second<\/p>\n$/);
});

test("the round trip is exact, including the body and the empty field", () => {
  const { parsed } = roundTrip(DOC);

  assert.equal(parsed.site, "Blog");
  assert.equal(parsed.url, "/blog/hello");
  assert.equal(parsed.type, "Article");
  assert.equal(parsed.folder, "/blog");
  assert.equal(parsed.publish, true);
  assert.deepEqual(parsed.data, DOC.data);
});

test("serialization is canonical: field order does not depend on the input's", () => {
  const shuffled = { ...DOC, data: { tags: DOC.data.tags, seoTitle: "", content: DOC.data.content, headline: "Hello" } };

  assert.equal(canonicalize(shuffled, "content"), canonicalize(DOC, "content"));
});

test("values that YAML would misread are quoted and come back unchanged", () => {
  const doc = {
    site: "S",
    url: "/x",
    type: "T",
    folder: "/",
    data: {
      looksBoolean: "true",
      looksNumeric: "0042",
      leadingDash: "- not a list",
      colonInside: "ratio: 1",
      empty: "",
      padded: "  spaced  ",
      multiline: "line one\nline two",
      nothing: null,
      count: 7,
      flag: false,
    },
  };

  const { parsed } = roundTrip(doc, null);

  assert.deepEqual(parsed.data, doc.data);
});

test("a value that is not a scalar stays JSON on one line", () => {
  const doc = {
    site: "S",
    url: "/x",
    type: "T",
    folder: "/",
    data: { items: [{ title: "a", ordinal: 1 }], nested: { a: 1 }, none: [] },
  };

  const { text, parsed } = roundTrip(doc, null);

  assert.match(text, /items: \[\{"title":"a","ordinal":1\}\]/);
  assert.deepEqual(parsed.data, doc.data);
});

test("a body that would not survive the round trip stays a field", () => {
  // A value with trailing whitespace cannot be a body: reading the file back would trim
  // it. Keeping it in `fields:` costs readability and keeps losslessness, which is the
  // property a sync cannot compromise on.
  const doc = { site: "S", url: "/x", type: "T", folder: "/", data: { content: "text\n\n  " } };

  const { text, parsed } = roundTrip(doc, "content");

  assert.doesNotMatch(text, /body: content/);
  assert.deepEqual(parsed.data, doc.data);
});

test("a file with no frontmatter says what a content file looks like", () => {
  assert.throws(
    () => parsePost("Blog/blog/hello.md", "# just markdown\n"),
    /does not start with a `---` frontmatter block/,
  );
});

test("a hand-written file takes its site and URL from its path", () => {
  // Law P4: an agent that writes a file with `Write` should not have to restate what the
  // path already says.
  const parsed = parsePost("Blog/blog/new-page.md", "---\ntype: Article\nfolder: /blog\n---\n");

  assert.equal(parsed.site, "Blog");
  assert.equal(parsed.url, "/blog/new-page");
  assert.equal(parsed.type, "Article");
});

test("a path that disagrees with the frontmatter is refused, not guessed", () => {
  // Renaming the file without the frontmatter is a move on one side and a create on the
  // other; picking either one silently duplicates or deletes a post (SH134's lesson).
  assert.throws(
    () => parsePost("Blog/blog/renamed.md", "---\nurl: /blog/hello\ntype: Article\n---\n"),
    /disagrees with its path/,
  );
});

test("a body with no field to hold it is refused", () => {
  assert.throws(
    () => parsePost("Blog/x.md", "---\ntype: Article\n---\nsome text\n"),
    /no `body:` key/,
  );
});

test("the body field is the first HTML Editor field by ordinal, or nothing", () => {
  assert.equal(
    bodyFieldFor({
      fields: [
        { name: "summary", widget: "Text Area", ordinal: 1 },
        { name: "content", widget: "HTML Editor", ordinal: 2 },
        { name: "extra", widget: "HTML Editor", ordinal: 3 },
      ],
    }),
    "content",
  );
  // A Text Area is deliberately not a body: promoting one would move content between
  // fields the moment someone added a field.
  assert.equal(bodyFieldFor({ fields: [{ name: "summary", widget: "Text Area", ordinal: 1 }] }), null);
});

test("a site name that has no safe directory name is refused", () => {
  assert.throws(() => filePathFor({ site: "a/b", url: "/x" }), /no safe directory name/);
});
