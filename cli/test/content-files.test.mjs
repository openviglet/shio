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
  SITE_KEYS_ABSENT,
  parseSiteMarker,
  serializeSite,
  siteLocalePolicy,
  siteLocationOf,
  siteMarkerPathFor,
  locationOf,
  parseFolderMarker,
  parsePost,
  pathToUrl,
  serializeFolder,
  serializePost,
  sidecarFieldsFor,
  sidecarNameFor,
  sidecarsOf,
  storedUrl,
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

/**
 * SH585 — the tree can spell what the instance stores.
 *
 * SH467 made an instance able to store `/model-catalog/`, and a filename cannot carry that: a
 * post's path on disk *is* its URL, so `/x` and `/x/` are one file. Block I promises the tree is a
 * complete description of a site, and between SH467 and this it was not — a pull produced a tree
 * that, re-applied to an empty instance, rebuilt every directory-style page one character short
 * and reintroduced the reference defect SH467 closed.
 */

test("SH585: storedUrl keeps the trailing slash and normalizeUrl still drops it", () => {
  // The pair, asserted together, because the whole design is that they are two functions. The
  // count said so: of 33 normalizeUrl call sites, about half normalize a FOLDER chain — a
  // different namespace (SH79) that must never keep a slash — so teaching one function to
  // sometimes keep it would have changed all 33 to fix 3.
  assert.equal(storedUrl("/model-catalog/"), "/model-catalog/");
  assert.equal(storedUrl("/model-catalog"), "/model-catalog");
  assert.equal(storedUrl("model-catalog/"), "/model-catalog/");
  assert.equal(storedUrl("/blog//hello/"), "/blog/hello/");
  assert.equal(storedUrl("/"), "/", "the root has no other spelling");
  assert.equal(storedUrl(""), "/");
  assert.equal(storedUrl(null), "/");
});

test("SH585: a pull writes the declared spelling into the frontmatter, not into the filename", () => {
  const file = serializePost({ ...DOC, url: "/model-catalog/" });

  assert.match(file, /^url: \/model-catalog\/$/m, "the one place the fact can live");
  assert.equal(
    urlToPath("/model-catalog/"), "model-catalog.md",
    "the filename is unchanged and must be: /x and /x/ are one path, which is why the "
      + "frontmatter carries it instead",
  );
});

test("SH585: and the parse gives it back, so the round trip is exact", () => {
  // A pull that wrote the slash and a push that dropped it would rebuild the site one character
  // short — the defect this closes, one step later in the same chain.
  const parsed = parsePost("Blog/model-catalog.md", serializePost({ ...DOC, url: "/model-catalog/" }));

  assert.equal(parsed.url, "/model-catalog/");
});

test("SH585: url: may differ from its path only by the slash — anything else is still refused", () => {
  // Not a new rule: the disagreement check compares both sides collapsed, so it already tolerated
  // exactly this difference and nothing wider. The refusal it guards is a file renamed without its
  // frontmatter, which is a move on one side and a create on the other.
  assert.equal(
    parsePost("Blog/model-catalog.md", "---\nsite: Blog\nurl: /model-catalog/\n---\n").url,
    "/model-catalog/",
  );
  assert.throws(
    () => parsePost("Blog/model-catalog.md", "---\nsite: Blog\nurl: /vendors\n---\n"),
    /disagrees with its path/,
  );
});

test("SH585: a post with no declared slash serializes byte-identically to before", () => {
  // The overwhelming case, and the one that must not move: the whole tree would show a diff.
  assert.equal(serializePost(DOC), serializePost({ ...DOC, url: "/blog/hello" }));
  assert.match(serializePost(DOC), /^url: \/blog\/hello$/m);
});

/*
 * SH1122 — the site marker, and the hole in P4 it fills.
 *
 * A site root is a folder, so it already had a `_folder.md` — carrying `site` and `folder` and
 * nothing about the site. Everything a site *is* lived only in its row: `url`, `furl`, the
 * description, and since SH1056 the locale policy. None of it pulled, pushed or diffed, which is law
 * P4 with a hole in exactly the wrong place: the one object an agent must configure before a page
 * renders at all was the one it could not edit as a file.
 *
 * Found by SH1056, whose design asked for "a locales block in the site marker file" — a sentence that
 * reads as a field on an existing file and is a subsystem, because there was no such file.
 */
test("SH1122: a site marker round-trips, and every key is one a push can send", () => {
  const marker = {
    site: "Blog",
    url: "https://blog.example",
    furl: "blog",
    description: "The company blog",
    locales: { default: "en", enabled: ["en", "pt-br"], fallback: { "pt-br": "en" } },
  };
  const text = serializeSite(marker);
  assert.equal(text, [
    "---",
    "site: Blog",
    "url: https://blog.example",
    "furl: blog",
    "description: The company blog",
    'locales: {"default":"en","enabled":["en","pt-br"],"fallback":{"pt-br":"en"}}',
    "---",
    "",
  ].join("\n"), "SH1122: the key order is fixed, so a diff never shows a reordering (P5)");
  assert.deepEqual(parseSiteMarker("Blog/_site.md", text), marker,
    "SH1122: what the tree writes is what the tree reads back — the property every sync rests on");
  assert.equal(serializeSite(parseSiteMarker("Blog/_site.md", text)), text,
    "SH1122: and it is byte-stable over a round trip");
});

test("SH1283: a site's page type is projected, read back and in its fixed place", () => {
  const marker = { site: "Quilhara", description: "Trails", pageType: "InfoPage" };
  const text = serializeSite(marker);
  assert.equal(text, "---\nsite: Quilhara\ndescription: Trails\npageType: InfoPage\n---\n",
    "SH1283: after the plain settings and before the policy, so a diff never reorders (P5)");
  assert.deepEqual(parseSiteMarker("Quilhara/_site.md", text), marker,
    "SH1283: a push sends what a pull wrote, or the tree carries a key nothing reads");
});

test("SH1288: a site's language labels survive a pull and a push, and their absence costs no bytes", () => {
  const stored = '{"default":"pt-br","enabled":["pt-br","pt"],'
    + '"labels":{"pt-br":"Português (Brasil)","pt":"Português (Portugal)"}}';
  const text = serializeSite({ site: "Quilhara", locales: stored });
  assert.equal(text, "---\nsite: Quilhara\n"
    + 'locales: {"default":"pt-br","enabled":["pt-br","pt"],'
    + '"labels":{"pt":"Português (Portugal)","pt-br":"Português (Brasil)"}}\n---\n',
    "SH1288: the labels are dropped from _site.md, or not last and sorted the way the server's "
    + "toNode writes them");
  assert.equal(serializeSite(parseSiteMarker("Quilhara/_site.md", text)), text,
    "SH1288: a label typed into the marker must reach the push, byte-identically");
  assert.equal(
    JSON.stringify(siteLocalePolicy('{"default":"en","enabled":["en"],"labels":{}}')),
    '{"default":"en","enabled":["en"]}',
    "SH1288: a policy without labels serializes exactly as it did before labels existed");
});

test("SH1122: a site with nothing but a name projects two lines, and gains one per field", () => {
  assert.equal(serializeSite({ site: "Blog" }), "---\nsite: Blog\n---\n",
    "SH1122: a key is written only when the site has a value for it, so an unconfigured site is not "
    + "a file full of nulls that a push would read as a request to blank four fields");
  // A column is either unset or holds something, and `""` is the first: the op reads a blank as
  // "nothing requested", so writing the key would make an empty value indistinguishable from an
  // absent one on the way back in.
  assert.equal(serializeSite({ site: "Blog", url: "", description: "   " }),
    "---\nsite: Blog\n---\n");
});

test("SH1122: an undeclared locale policy is absent, not an empty object", () => {
  // SH1056's distinction, carried into the tree. A site that has declared nothing reports the
  // locales its content happens to be written in, and writing that derived answer into the file
  // would turn an observation into a declaration on the next push — the agent would be declaring a
  // policy nobody chose.
  assert.equal(siteLocalePolicy(null), undefined);
  assert.equal(siteLocalePolicy("{}"), undefined);
  assert.equal(siteLocalePolicy("not json"), undefined);
  assert.equal(siteLocalePolicy('{"enabled":[]}'), undefined,
    "SH1122: a policy naming nothing is not a policy");
  // `fallback` sorted, because the server's stored document is byte-stable (ShStableMap) and a
  // projection that re-encoded it in hash order would show a diff after a restart with nothing
  // changed. `enabled` keeps the author's order — it is a list, not a set.
  assert.equal(
    JSON.stringify(siteLocalePolicy(
      '{"fallback":{"z":"en","a":"en"},"enabled":["pt-br","en"],"default":"en"}')),
    '{"default":"en","enabled":["pt-br","en"],"fallback":{"a":"en","z":"en"}}');
});

test("SH1122: a marker and its directory have to agree, and a body has nowhere to go", () => {
  assert.throws(() => parseSiteMarker("Blog/_site.md", "---\nsite: Other\n---\n"),
    /disagrees with its directory/,
    "SH1122: a directory renamed without its frontmatter is a rename on one side and a create on "
    + "the other — the same refusal `parseFolderMarker` makes, and for the same reason");
  assert.throws(() => parseSiteMarker("Blog/_site.md", "---\nsite: Blog\n---\nsome prose\n"),
    /has no body/,
    "SH1122: text in a marker is content somebody wrote expecting it to land somewhere, and there "
    + "is nowhere for it to go");
  assert.deepEqual(parseSiteMarker("Blog/_site.md", "---\nurl: https://x\n---\n"),
    { site: "Blog", url: "https://x" },
    "SH1122: the path names the site, so a hand-written file needs no ceremony (P4)");
});

test("SH1122: the four keys a marker may not carry are refused by name", () => {
  // The projection's rule: every key in a marker is a key the tree can author AND a key
  // `site.upsert` can send. A file carrying a field no write accepts is a file that lies about being
  // authorable — an agent edits it, pushes, is told nothing, and the setting is whatever it was.
  for (const [key, value] of [
    ["bindings", "{}"],
    ["scratch", "true"],
    ["searchablePostTypes", "Page"],
    ["formSuccess", "/thanks"],
  ]) {
    assert.throws(
      () => parseSiteMarker("Blog/_site.md", `---\nsite: Blog\n${key}: ${value}\n---\n`),
      new RegExp(`carries \`${key}:\``),
      `SH1122: ${key} is not projected and a marker carrying it must say so rather than be ignored`);
  }
  assert.deepEqual(SITE_KEYS_ABSENT,
    ["bindings", "scratch", "searchablePostTypes", "formSuccess"],
    "SH1122: the list is the decision — `bindings` name a layout by title, which is a reference the "
    + "tree cannot check; `scratch` is settable on a create and never afterwards, which is what "
    + "`shio site rm --scratch` rests on; the other two have no write op at all");
});

test("SH1122: the marker lives beside the site root's _folder.md, never under content", () => {
  assert.equal(siteMarkerPathFor({ site: "Blog" }), "Blog/_site.md");
  assert.deepEqual(siteLocationOf("Blog/_site.md"), { site: "Blog" });
  // Not a deeper site: under `shio/content` the directories are URL segments (SH149), and the
  // folders root is where a marker can mean a structural thing. A `_site.md` below the site root is
  // a mistake, and it reads as no site rather than as one named after a folder.
  assert.deepEqual(siteLocationOf("Blog/news/_site.md"), {});
  assert.throws(() => siteMarkerPathFor({ site: ".." }), /site name/,
    "SH1122: the only value in the path is the site name, and `siteDir` is what stops a "
    + "server-reported `..` leaving the root");
});

test("SH1301: a folder marker carries its members-only gate, and only when it has one", () => {
  const gated = { site: "Blog", folder: "/members", visibility: "members", groups: ["Members"] };
  const text = serializeFolder(gated);
  assert.match(text, /\nvisibility: members\ngroups: \["Members"\]\n/);
  assert.deepEqual(parseFolderMarker("Blog/members/_folder.md", text), gated,
    "SH1301: the gate round-trips through the file");

  const plain = serializeFolder({ site: "Blog", folder: "/news" });
  assert.doesNotMatch(plain, /visibility/, "SH1301: a public folder's marker is what it was before");
  assert.deepEqual(parseFolderMarker("Blog/news/_folder.md", plain), { site: "Blog", folder: "/news" });

  assert.deepEqual(parseFolderMarker("Blog/open/_folder.md", "---\nsite: Blog\nfolder: /open\nvisibility: public\n---\n"),
    { site: "Blog", folder: "/open", visibility: "public" }, "SH1301: `public` is how a file removes a gate");
  assert.throws(() => parseFolderMarker("Blog/x/_folder.md", "---\nsite: Blog\nfolder: /x\nvisibility: private\n---\n"),
    /visibility: private/, "SH1301: a word that is not a visibility is refused, not dropped");
  assert.throws(() => parseFolderMarker("Blog/x/_folder.md", "---\nsite: Blog\nfolder: /x\ngroups: [\"A\"]\n---\n"),
    /no `visibility:`/, "SH1301: groups without a visibility is refused");
});

test("SH1301: a post's own members-only gate round-trips through its frontmatter", () => {
  const doc = { site: "Blog", url: "/vip", type: "Page", folder: "/", visibility: "members", groups: ["Members"], data: { title: "Vip" } };
  const text = serializePost(doc);
  assert.match(text, /\nvisibility: members\ngroups: \["Members"\]\n/);
  const back = parsePost("Blog/vip.md", text);
  assert.equal(back.visibility, "members");
  assert.deepEqual(back.groups, ["Members"]);
  assert.doesNotMatch(serializePost({ ...doc, visibility: undefined, groups: undefined }), /visibility/,
    "SH1301: a post with no gate of its own writes exactly what it did before");
});
