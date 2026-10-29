// The one piece of real logic on this site, so it is the one piece with a test.
//
// Each case is a shape the site actually emits, and the two that matter most are
// the ones that are silent when wrong: chrome leaking into every twin (which is
// the token waste the file exists to avoid), and content vanishing because a tag
// was not recognised.

import { test } from "node:test";
import assert from "node:assert/strict";
import { decodeEntities, pageToMarkdown, parseHtml, findTag } from "./markdown.mjs";

const wrap = (main) =>
  `<div><header><nav><a href="/">Viglet Shio</a></nav></header>` +
  `<main>${main}</main>` +
  `<footer><p>© 2026 Viglet Shio</p></footer></div>`;

const md = (main) =>
  pageToMarkdown(wrap(main), {
    title: "T",
    description: "D",
    canonical: "https://openviglet.github.io/shio/x",
  });

test("the twin carries the route's own metadata", () => {
  const out = md("<p>Body.</p>");
  assert.match(out, /^# T\n/);
  assert.match(out, /^> D$/m);
  assert.match(out, /^Canonical: https:\/\/openviglet\.github\.io\/shio\/x$/m);
});

test("the nav and the footer are dropped: they are chrome on all ten routes", () => {
  const out = md("<p>Body.</p>");
  assert.doesNotMatch(out, /Viglet Shio\]/);
  assert.doesNotMatch(out, /© 2026/);
  assert.match(out, /Body\./);
});

test("headings keep their level", () => {
  assert.match(md("<h2>Ten laws</h2>"), /^## Ten laws$/m);
  assert.match(md("<h3>A candidate</h3>"), /^### A candidate$/m);
});

test("JSX indentation collapses instead of reaching the file", () => {
  const out = md("<p>\n      One call\n      replaces a session.\n    </p>");
  assert.match(out, /^One call replaces a session\.$/m);
});

test("a code block survives verbatim, newlines and all", () => {
  const out = md("<pre><code>shio pull --content\nshio push --content</code></pre>");
  assert.match(out, /```\nshio pull --content\nshio push --content\n```/);
});

test("a code block whose lines are separate spans keeps one line per span", () => {
  // The session panels colour each line on its own, so they emit one
  // `<span class="block">` per line and no newline characters at all. Joining
  // the children ran a whole artefact (a file tree, a diff) into a single
  // line in the twin, which reads as mangled rather than as missing.
  const out = md(
    '<pre><code><span class="block">acme</span><span class="block whitespace-pre">  └─ Home</span></code></pre>'
  );
  assert.match(out, /```\nacme\n {2}└─ Home\n```/);
});

test("a link keeps its href, and an in-page anchor keeps only its text", () => {
  assert.match(md('<p><a href="/curator">For the curator</a></p>'), /\[For the curator\]\(\/curator\)/);
  assert.match(md('<p><a href="#laws">The ten laws</a></p>'), /^The ten laws$/m);
});

test("emphasis becomes Markdown emphasis", () => {
  assert.match(md("<p><strong>Because</strong> it is a <em>signature</em>.</p>"), /\*\*Because\*\* it is a \*signature\*\./);
});

test("a definition list reads as a term and its gloss", () => {
  const out = md("<dl><dt>shio_context</dt><dd>model and sitemap</dd></dl>");
  assert.match(out, /\*\*shio_context\*\*/);
  assert.match(out, /model and sitemap/);
});

test("a table becomes a Markdown table with a header rule", () => {
  const out = md(
    "<table><thead><tr><th>Scope</th><th>Writes</th></tr></thead>" +
      "<tbody><tr><td>AGENT</td><td>the agent surface</td></tr></tbody></table>"
  );
  assert.match(out, /\| Scope \| Writes \|/);
  assert.match(out, /\| --- \| --- \|/);
  assert.match(out, /\| AGENT \| the agent surface \|/);
});

test("an icon and a decorative element leave nothing behind", () => {
  const out = md('<p><svg viewBox="0 0 1 1"><path d="M0 0"/></svg>Only this</p>');
  assert.match(out, /^Only this$/m);
  assert.doesNotMatch(out, /path|viewBox/);
  assert.doesNotMatch(md('<div aria-hidden="true"><p>Decoration</p></div><p>Kept</p>'), /Decoration/);
});

test("an empty overlay button gives a step a click target and costs the twin nothing", () => {
  // SH571: the transcript's steps became clickable. A <button> is dropped whole,
  // children included, which is right for the CTA and would have deleted all six
  // calls from every twin had the text moved inside it. The affordance therefore
  // sits beside the text, not around it, and this is the assertion that says so:
  // label in the button, content still in the list.
  const out = md(
    '<ol><li><button aria-label="Show call 1: shio_context"></button>' +
      "<div><p>&gt; shio_context</p><p>P2</p></div></li></ol>"
  );
  assert.match(out, /shio_context/);
  assert.doesNotMatch(out, /Show call 1/);
});

test("an unknown tag degrades to its text rather than losing it", () => {
  // The failure this refuses: a converter that drops what it does not know is a
  // converter that loses content nobody notices.
  assert.match(md("<p>Before <mark>kept</mark> after</p>"), /Before kept after/);
});

test("a list keeps its items, and a nested list is indented rather than swallowed", () => {
  const out = md("<ul><li>One<ul><li>Nested</li></ul></li><li>Two</li></ul>");
  assert.match(out, /^- One$/m);
  assert.match(out, /^ {2}- Nested$/m);
  assert.match(out, /^- Two$/m);
});

test("the scanner survives an unbalanced close tag instead of throwing", () => {
  const tree = parseHtml("<main><p>Text</p></span></main>");
  assert.equal(findTag(tree, "p").children[0].text, "Text");
});

test("entities decode, including the ones this site actually writes", () => {
  assert.equal(decodeEntities("a &mdash; b &middot; c &rarr; d"), "a \u2014 b · c → d");
  assert.equal(decodeEntities("&#8212;"), "\u2014");
  assert.equal(decodeEntities("&unknownentity;"), "&unknownentity;");
});

test("a quoted '>' inside an attribute does not end the tag early", () => {
  const tree = parseHtml('<main><p title="a > b">Text</p></main>');
  const p = findTag(tree, "p");
  assert.equal(p.attrs.title, "a > b");
  assert.equal(p.children[0].text, "Text");
});

test("a law chip beside a heading gains the space the markup implied", () => {
  const out = md('<h2>One vocabulary<span>P8</span></h2>');
  assert.match(out, /^## One vocabulary P8$/m);
});

test("but a span before punctuation does not", () => {
  const out = md('<p>The response carries a <span>digest</span>.</p>');
  assert.match(out, /^The response carries a digest\.$/m);
});

test("a data-md-skip region is chrome and never reaches the twin", () => {
  const out = md('<section data-md-skip><p>Convert now</p></section><p>Kept</p>');
  assert.doesNotMatch(out, /Convert now/);
  assert.match(out, /^Kept$/m);
});

test("list items stay one list, and table rows stay one table", () => {
  const list = md("<ul><li>One</li><li>Two</li></ul>");
  assert.match(list, /- One\n- Two/);
  const table = md("<table><tbody><tr><td>a</td></tr><tr><td>b</td></tr></tbody></table>");
  assert.match(table, /\| a \|\n\| b \|/);
});

test("a long run of newlines in a code block does not hang the trim", () => {
  const started = Date.now();
  const out = md(`<pre><code>${"\n".repeat(50000)}x</code></pre>`);
  assert.match(out, /```\nx\n```/);
  assert.ok(Date.now() - started < 2000, "trimming newlines must be linear");
});
