/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
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
  folderLocationOf,
  folderMarkerPathFor,
  locationOf,
  parseFolderMarker,
  parsePost,
  pathToUrl,
  serializeFolder,
  serializePost,
  sidecarFieldsFor,
  sidecarNameFor,
  sidecarsOf,
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

// --- movedFrom, the rename instruction (SH150) ---

test("movedFrom round-trips and is an instruction, not a compared field", () => {
  const text = serializePost({ ...DOC, movedFrom: "blog/previous" }, "content");

  // Normalized like every other path in the format, so `blog/previous` and `/blog/previous`
  // cannot produce two different documents (P5).
  assert.match(text, /^movedFrom: \/blog\/previous$/m);
  assert.equal(parsePost(filePathFor(DOC), text).movedFrom, "/blog/previous");
  // Absent unless set: a pull has nothing to report here, so it never writes the key.
  assert.doesNotMatch(serializePost(DOC, "content"), /movedFrom/);
});

test("a movedFrom that is the post's own URL is refused", () => {
  // The likely mistake: the URL was edited and `movedFrom` was set to the *new* value. Left
  // alone it compiles to a move onto itself, which the server refuses for a reason that says
  // nothing about the actual error.
  assert.throws(
    () => parsePost("Blog/blog/hello.md", "---\nsite: Blog\nurl: /blog/hello\nmovedFrom: /blog/hello\n---\n"),
    /is its own URL/,
  );
});

// --- code fields project as sidecar files (SH191) ---

/** The render model's `Region`, as `shio pull` serializes a post-type def. */
const REGION = {
  name: "Region",
  fields: [
    { name: "HTML", widget: "Ace Editor - HTML", ordinal: 1 },
    { name: "JAVASCRIPT", widget: "Ace Editor - Javascript", ordinal: 2 },
  ],
};

const TEMPLATE = '<div class="cards">\n  {{#each this.posts}}\n    <b>{{title}}</b>\n  {{/each}}\n</div>\n';
const SCRIPT = "document.querySelectorAll('.cards').forEach((n) => n.classList.add('ready'));\n";

const REGION_DOC = {
  site: "Bootstrap",
  url: "/region/content-list",
  type: "Region",
  folder: "/Regions",
  data: { HTML: TEMPLATE, JAVASCRIPT: SCRIPT },
};

test("the widget decides which fields earn a sidecar, and the extension", () => {
  assert.deepEqual(sidecarFieldsFor(REGION), [
    { name: "HTML", ext: "html" },
    { name: "JAVASCRIPT", ext: "js" },
  ]);
  // Not the post-type: a layout somebody models themselves has to work the same way, and
  // prose fields must never become files.
  assert.deepEqual(
    sidecarFieldsFor({ fields: [{ name: "summary", widget: "Text Area", ordinal: 1 }] }),
    [],
  );
  // The body is already a file; it must not become two.
  assert.deepEqual(
    sidecarFieldsFor({
      fields: [
        { name: "content", widget: "HTML Editor", ordinal: 1 },
        { name: "HTML", widget: "Ace Editor - HTML", ordinal: 2 },
      ],
    }),
    [{ name: "HTML", ext: "html" }],
  );
});

test("a sidecar's name carries the field, so two same-widget fields cannot collide", () => {
  assert.equal(sidecarNameFor("content-list.md", { name: "HTML", ext: "html" }), "content-list.HTML.html");
  const two = sidecarFieldsFor({
    fields: [
      { name: "HEADER", widget: "Ace Editor - HTML", ordinal: 1 },
      { name: "FOOTER", widget: "Ace Editor - HTML", ordinal: 2 },
    ],
  });
  const names = sidecarsOf({ ...REGION_DOC, data: { HEADER: "<h1/>", FOOTER: "<hr/>" } }, two)
    .map((sidecar) => sidecar.name);
  assert.deepEqual(new Set(names).size, 2);
});

test("a code field leaves the frontmatter and becomes a file the frontmatter names", () => {
  const sidecars = sidecarFieldsFor(REGION);
  const text = serializePost(REGION_DOC, null, sidecars);

  assert.match(text, /^files:\n {2}HTML: content-list\.HTML\.html\n {2}JAVASCRIPT: content-list\.JAVASCRIPT\.js$/m);
  // The whole point: neither program is in the document any more, escaped or otherwise.
  assert.doesNotMatch(text, /fields:/);
  assert.doesNotMatch(text, /card/);

  const written = sidecarsOf(REGION_DOC, sidecars);
  assert.deepEqual(
    written.map((sidecar) => sidecar.path),
    ["Bootstrap/region/content-list.HTML.html", "Bootstrap/region/content-list.JAVASCRIPT.js"],
  );
  // Verbatim — an editor opens a real HTML document, and the round trip is byte-for-byte.
  assert.equal(written[0].text, TEMPLATE);
});

test("the sidecar round trip is exact, so a pull never reports a change nobody made", () => {
  const sidecars = sidecarFieldsFor(REGION);
  const text = serializePost(REGION_DOC, null, sidecars);
  const parsed = parsePost(filePathFor(REGION_DOC), text);

  // The values are the store's to read; the format's job is to say where they went.
  assert.deepEqual(parsed.sidecarFiles, {
    HTML: "content-list.HTML.html",
    JAVASCRIPT: "content-list.JAVASCRIPT.js",
  });
  assert.deepEqual(parsed.data, {});
  // Reassembled the way `readLocal` does it, the document is the one that went in — including
  // a value whose trailing newline a normalizing writer would have eaten.
  for (const sidecar of sidecarsOf(REGION_DOC, sidecars)) parsed.data[sidecar.field] = sidecar.text;
  assert.deepEqual(parsed.data, REGION_DOC.data);
});

test("an empty code field stays out of files: rather than writing an empty program", () => {
  const sidecars = sidecarFieldsFor(REGION);
  const text = serializePost({ ...REGION_DOC, data: { HTML: TEMPLATE, JAVASCRIPT: "" } }, null, sidecars);

  assert.match(text, /^ {2}HTML: content-list\.HTML\.html$/m);
  assert.doesNotMatch(text, /JAVASCRIPT: content-list/);
  // It is still a field the post holds, so it stays declared — just not as a file, because an
  // empty file cannot be told apart from one somebody emptied.
  assert.match(text, /^fields:\n {2}JAVASCRIPT: ""$/m);
});

test("a post-type with no code fields serializes exactly as it did before", () => {
  assert.equal(serializePost(DOC, "content", []), serializePost(DOC, "content"));
});

test("one field cannot have two values: files: versus fields: is refused", () => {
  assert.throws(
    () =>
      parsePost(
        "Bootstrap/region/x.md",
        "---\nsite: Bootstrap\nurl: /region/x\nfiles:\n  HTML: x.HTML.html\nfields:\n  HTML: <p/>\n---\n",
      ),
    /is in both/,
  );
  assert.throws(
    () =>
      parsePost(
        "Bootstrap/region/x.md",
        "---\nsite: Bootstrap\nurl: /region/x\nbody: HTML\nfiles:\n  HTML: x.HTML.html\n---\n",
      ),
    /both the document body/,
  );
});

test("a sidecar that is a path, not a file name, is refused", () => {
  assert.throws(
    () =>
      parsePost(
        "Bootstrap/region/x.md",
        "---\nsite: Bootstrap\nurl: /region/x\nfiles:\n  HTML: ../../../etc/passwd\n---\n",
      ),
    /is a path, not a file name/,
  );
});

test("a Theme's CSS field lands in a .css, because the widget decides (SH252)", () => {
  // The whole of SH252 as seen from the projection: nothing here changed. The field's *widget*
  // changed from `Ace Editor - HTML` to `Ace Editor - CSS` on the server, and the extension
  // followed — which is the rule SH191 chose ("the widget decides, never the post-type") paying
  // off rather than being asserted.
  const theme = {
    name: "Theme",
    fields: [
      { name: "CSS", widget: "Ace Editor - CSS", ordinal: 1 },
      { name: "JAVASCRIPT", widget: "Ace Editor - Javascript", ordinal: 2 },
    ],
  };

  assert.deepEqual(sidecarFieldsFor(theme), [
    { name: "CSS", ext: "css" },
    { name: "JAVASCRIPT", ext: "js" },
  ]);

  const doc = {
    site: "Bootstrap",
    url: "/theme",
    type: "Theme",
    folder: "/",
    data: { CSS: "body{font-family:sans-serif}\n", JAVASCRIPT: "console.log('x')\n" },
  };
  assert.deepEqual(
    sidecarsOf(doc, sidecarFieldsFor(theme)).map((sidecar) => sidecar.path),
    ["Bootstrap/theme.CSS.css", "Bootstrap/theme.JAVASCRIPT.js"],
  );
});

// --- folder markers (SH149) ---

/** A marker file's contents, written the way the format writes them. */
const marker = (site, folder) => `---\nsite: ${site}\nfolder: ${folder}\n---\n`;

test("a folder marker's path is its name chain, in a root of its own", () => {
  // Not under `shio/content`: there the directories are URL segments, so a marker inside it
  // would be claiming a URL prefix is a folder chain. They agree often enough to look right,
  // which is exactly why the one time they do not would create the wrong folder silently.
  assert.equal(folderMarkerPathFor({ site: "Blog", folder: "/news" }), "Blog/news/_folder.md");
  assert.equal(folderMarkerPathFor({ site: "Blog", folder: "/a/b" }), "Blog/a/b/_folder.md");
  // A site root is a folder too, and it is the one every other chain hangs off.
  assert.equal(folderMarkerPathFor({ site: "Blog", folder: "/" }), "Blog/_folder.md");
  assert.deepEqual(folderLocationOf("Blog/a/b/_folder.md"), { site: "Blog", folder: "/a/b" });
  assert.deepEqual(folderLocationOf("Blog/_folder.md"), { site: "Blog", folder: "/" });
});

test("a folder marker round-trips through frontmatter alone", () => {
  const doc = { site: "Blog", folder: "/news" };
  const text = serializeFolder(doc);

  assert.equal(text, marker("Blog", "/news"));
  assert.deepEqual(parseFolderMarker(folderMarkerPathFor(doc), text), doc);
  // Byte-identical for the same folder, whatever produced it (P5).
  assert.equal(serializeFolder({ site: "Blog", folder: "news" }), text);
});

test("a folder marker takes its identity from its path when the frontmatter omits it", () => {
  assert.deepEqual(parseFolderMarker("Docs/guide/_folder.md", "---\n---\n"), {
    site: "Docs",
    folder: "/guide",
  });
});

test("a folder marker whose path disagrees with its frontmatter is refused", () => {
  // Half a rename: a move on one side and a create on the other. SH134's lesson, and the
  // reason the marker writes the two keys its path already implies.
  assert.throws(
    () => parseFolderMarker("Blog/news/_folder.md", marker("Blog", "/archive")),
    /disagrees with its path/,
  );
  assert.throws(
    () => parseFolderMarker("Blog/news/_folder.md", marker("Other", "/news")),
    /disagrees with its directory/,
  );
});

test("a folder marker can declare the folder it moved from, and round-trips with it (SH211)", () => {
  const doc = { site: "Blog", folder: "/updates", movedFrom: "/news" };
  const text = serializeFolder(doc);

  assert.match(text, /movedFrom: \/news/);
  assert.deepEqual(parseFolderMarker(folderMarkerPathFor(doc), text), doc);
  // Absent, not empty, when there is no rename — so a marker without one is byte-identical to
  // what SH149 wrote and the key never appears in a tree that is not renaming anything.
  assert.equal(serializeFolder({ site: "Blog", folder: "/news" }), marker("Blog", "/news"));
  assert.deepEqual(parseFolderMarker("Blog/news/_folder.md", marker("Blog", "/news")), {
    site: "Blog",
    folder: "/news",
  });
});

test("a folder marker that says it moved from itself is refused", () => {
  // The likeliest way to get here is renaming the directory and then setting `movedFrom` to the
  // *new* path, which asks for nothing and reads exactly like a rename that already happened.
  assert.throws(
    () => parseFolderMarker(
      "Blog/updates/_folder.md",
      "---\nsite: Blog\nfolder: /updates\nmovedFrom: /updates\n---\n",
    ),
    /is its own folder/,
  );
});

test("text in a folder marker is refused rather than dropped", () => {
  // A folder holds posts, not prose. Silently discarding it loses something somebody wrote
  // expecting it to appear on the site.
  assert.throws(
    () => parseFolderMarker("Blog/news/_folder.md", `${marker("Blog", "/news")}Hello\n`),
    /has no body/,
  );
});

// --- SH402: a URL that navigates instead of naming ---

test("SH402: a post URL with a traversal segment is refused, not projected", () => {
  // The measurement: `path.join` normalizes the result, so `Blog/../outside.md` under
  // `shio/content` is `shio/outside.md`, and more segments leave the repository. The value is
  // the server's furl, which makes this remote data choosing where a local command writes.
  for (const url of ["/../outside", "/blog/../../etc/passwd", "/blog/./here"]) {
    assert.throws(
      () => filePathFor({ site: "Blog", url }),
      /navigates the filesystem instead of naming a post/,
      `filePathFor should refuse ${url}`,
    );
  }
});

test("SH402: the site half was already guarded, and still is", () => {
  // Why this defect survived: a path is built from two values, `siteDir` has refused `.` and `..`
  // since SH257, and the URL beside it was never asked the same question. Asserted here so the
  // pair stays covered by one rule each rather than one rule and a gap.
  assert.throws(() => filePathFor({ site: "..", url: "/hello" }), /has no safe directory name/);
});

test("SH402: an ordinary URL still projects, including dots inside a segment", () => {
  // The refusal is about a segment that *is* `.` or `..`, not about the character — a post at
  // `/blog/v1.2` is an ordinary post and a file name may hold dots.
  assert.equal(filePathFor({ site: "Blog", url: "/blog/v1.2" }), "Blog/blog/v1.2.md");
  assert.equal(filePathFor({ site: "Blog", url: "/blog/hello" }), "Blog/blog/hello.md");
  assert.equal(filePathFor({ site: "Blog", url: "/" }), "Blog/index.md");
});
