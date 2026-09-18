/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH306 — the page half of the SH185 bridge, tested in a DOM.
 *
 * `shio-app/src/main/resources/render/shio-editor.js` is 182 lines implementing one side of the
 * protocol and had no test at all: `ShEditorBridgeTest` asserts the *injection* (the tag appears for
 * `?editor=true`, with the origin on it) and stops at the file boundary, and SH303 changed three
 * functions inside it with `node --check` — which proves it parses — as the only check.
 *
 * ── Why jsdom, out of the options §XVI.55 listed ─────────────────────────────────────────────
 *
 * Two premises in that section turned out to be wrong, and both pointed at the same answer.
 *
 * 1. "the `shio-react` workspace where a test runner already exists" — there was none. This package
 *    had `dev`, `lint`, `compile:*` and two `e2e/*.mjs` scripts run by hand. The workspace convention
 *    is `node --test "test/*.test.mjs"` (shio-client, shio-model, shio-sections, cli), so that is
 *    what this uses, and the `test` script it needed is part of this task.
 * 2. "restructure the script to expose a testable core" — there is no pure core to expose. Every
 *    function worth testing *is* DOM behaviour: `ownerOf` is `closest`, `fieldsOf` is a
 *    `querySelectorAll` filtered by it, `setMode` writes `contentEditable`. A hand-written DOM stub
 *    would have been a third implementation of the DOM, and passing against it would prove nothing
 *    about a browser.
 *
 * And the option that is not available at all: evaluating the script from the Java suite. There is no
 * script engine on that classpath *by design* — `ShRenderNoScriptEngineTest` asserts the Nashorn
 * exclusion, which is the non-goal the whole renderer was shaped by. So the behavioural test lives
 * here, and the textual guards (no `"*"`, the origin checks, one bridge per producer) stay in
 * `ShEditorBridgeTest` where the build gates. That split is deliberate: this file runs under
 * `pnpm test`, and wiring the JS workspace into CI is SH241/SH284's open work, not something this
 * task can pretend it did.
 */

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

import { JSDOM } from "jsdom";

const HERE = dirname(fileURLToPath(import.meta.url));
const BRIDGE = resolve(HERE, "..", "..", "shio-app", "src", "main", "resources", "render",
  "shio-editor.js");
const SOURCE = readFileSync(BRIDGE, "utf8");

const CONSOLE_ORIGIN = "https://console.example";

/**
 * A page, framed, with everything the parent would have seen.
 *
 * Two things this harness has to get right, and the first cost a red run to learn:
 *
 * - **Wait for the document.** The script's last four lines branch on `document.readyState`: append it
 *   while jsdom is still `"loading"` and it registers a `DOMContentLoaded` listener for an event that
 *   has already fired, so `attach()` never runs — no affordances, no `sh:ready`, nothing editable,
 *   while the `message` listener (registered synchronously) still works. That is exactly the failure
 *   signature the first version of this file produced, and it is a real branch rather than a harness
 *   quirk: {@link !"the bridge injected while the page is still parsing also attaches"} covers it.
 * - **Replace `window.parent` first.** The script exits in its first four lines if it is not framed,
 *   and a top-level jsdom window is its own parent.
 */
async function pageWithBridge(bodyHtml, {
  origin = CONSOLE_ORIGIN, bodyAttrs = "", early = false,
  // SH336 — the page's own address decides whether click interception can run at all, and the
  // path index is what lets it tell a page from a real server path.
  url = "https://site.example/about", previewPaths = null,
} = {}) {
  const dom = new JSDOM(
    `<!doctype html><html><head></head><body ${bodyAttrs}>${bodyHtml}</body></html>`,
    { runScripts: "dangerously", url });

  const posted = [];
  Object.defineProperty(dom.window, "parent", {
    configurable: true,
    value: {
      postMessage(message, targetOrigin) {
        posted.push({ message, targetOrigin });
      },
    },
  });

  const append = () => {
    const script = dom.window.document.createElement("script");
    if (origin !== null) script.setAttribute("data-shio-editor-origin", origin);
    if (previewPaths !== null) {
      script.setAttribute("data-shio-preview-paths", previewPaths.join("\n"));
    }
    script.textContent = SOURCE;
    dom.window.document.body.appendChild(script);
  };

  if (early) {
    // readyState === "loading": the DOMContentLoaded path, which is how the server-rendered tag runs.
    append();
    await loaded(dom);
  } else {
    await loaded(dom);
    append();
  }

  return { dom, window: dom.window, document: dom.window.document, posted };
}

function loaded(dom) {
  if (dom.window.document.readyState === "complete") return Promise.resolve();
  return new Promise((done) => dom.window.addEventListener("load", () => done()));
}

/**
 * What the console would have received of a given type, as plain data.
 *
 * The JSON round trip is not a workaround for jsdom: a real `postMessage` **structured-clones** its
 * payload, so what the console gets is exactly this shape and never the page's own objects. Comparing
 * the realm's objects directly also fails on `deepStrictEqual`, which checks prototypes - the first
 * run of this file reported `actual: [ 'TITLE' ], expected: [ 'TITLE' ]`, which is the tell.
 */
function messagesOfType(posted, type) {
  return posted
    .filter((entry) => entry.message.type === type)
    .map((entry) => JSON.parse(JSON.stringify(entry.message.data)));
}

function sendFromParent(window, data, origin = CONSOLE_ORIGIN) {
  window.dispatchEvent(new window.MessageEvent("message", { data, origin }));
}

const PAGE = `
  <div data-shio-post="page-1" data-shio-region="MAIN">
    <h1 data-shio-prop="TITLE">Hello</h1>
    <p data-shio-prop="TEXT">Body</p>
  </div>
  <div data-shio-post="row-2">
    <h2 data-shio-prop="TITLE">Another post</h2>
  </div>
`;

const BODY_ATTRS = 'data-shio-post="page-1" data-shio-post-type="Page"';

/**
 * A page whose fields have no owner at all - and the reason it needs its own `<body>`.
 *
 * The first version of this fixture put an "orphan" field beside the annotated divs and expected it to
 * be unowned. It is not: SH185 stamps the page's post on `<body>` precisely so the common case needs no
 * `sh-post`, and `closest` walks all the way up - so a field anywhere on a stamped page is owned by the
 * page. The unowned branch is reachable only when the page post is unknown, which is the runtime
 * counterpart of `ShEditorBridgeTest.noPageIdMeansNoOwner`.
 */
const UNSTAMPED_PAGE = `<p data-shio-prop="ORPHAN">No owner declared</p>`;

/**
 * SH503 — a section whose fields are not all content.
 *
 * The call to action is the case the task was named for: `data-shio-prop` says what an element's
 * *content* is, so one element declared one field, and a link is two — its text and its target. The
 * feature row is the other shape, and the reason the mechanism is not "links": `icon` is a `data-`
 * attribute the theme's CSS reads, on an element with no text of its own.
 */
const ATTR_PAGE = `
  <section data-shio-post="section-1">
    <a data-shio-prop="ctaLabel" data-shio-prop-attr="ctaHref:href" href="/signup">Start</a>
    <li data-shio-prop-attr="features.0.icon:data-shio-icon" data-shio-icon="bolt"></li>
  </section>
`;

test("it announces itself to the origin it was told, and never to \"*\"", async () => {
  const { posted } = await pageWithBridge(PAGE, { bodyAttrs: BODY_ATTRS });

  const ready = messagesOfType(posted, "sh:ready");
  assert.equal(ready.length, 1, "the console waits for exactly one sh:ready");
  assert.equal(ready[0].postId, "page-1", "the page's own post, from <body>");
  assert.equal(ready[0].url, "/about");
  assert.deepEqual([...new Set(posted.map((entry) => entry.targetOrigin))], [CONSOLE_ORIGIN],
    "every message goes to the pinned origin: \"*\" would hand page content to whatever framed it");
});

test("the bridge injected while the page is still parsing also attaches", async () => {
  // The other half of the readyState branch, and the one the server-rendered tag actually takes:
  // ShEditorBridge appends the tag to the HTML it returns, so the script runs before
  // DOMContentLoaded. Getting this wrong in the *harness* produced a bridge that received messages
  // and did nothing else, which is why both paths are covered rather than the convenient one.
  const { posted, document } = await pageWithBridge(PAGE, { bodyAttrs: BODY_ATTRS, early: true });

  assert.equal(messagesOfType(posted, "sh:ready").length, 1);
  assert.equal(document.querySelector('[data-shio-post="page-1"] [data-shio-prop="TITLE"]')
    .contentEditable, "true");
});

test("it does nothing at all when nobody says who framed it", async () => {
  const { posted, document } = await pageWithBridge(PAGE, { origin: null, bodyAttrs: BODY_ATTRS });

  assert.deepEqual(posted, [], "no message without an origin to send it to");
  assert.equal(document.querySelector("style[data-shio-editor]"), null,
    "and no affordances: a page making things contenteditable outside the editor corrupts content");
  assert.equal(document.querySelector('[data-shio-prop="TITLE"]').getAttribute("contentEditable"),
    null);
});

test("a field's owner is the nearest ancestor that declares one", async () => {
  const { window, document, posted } = await pageWithBridge(PAGE, { bodyAttrs: BODY_ATTRS });

  document.querySelector('[data-shio-post="row-2"] [data-shio-prop="TITLE"]')
    .dispatchEvent(new window.MouseEvent("click", { bubbles: true }));

  const selected = messagesOfType(posted, "sh:element-selected");
  assert.equal(selected.length, 1);
  assert.equal(selected[0].postId, "row-2",
    "not the <body>'s page-1: SH185 states nearest-ancestor ownership as load-bearing, and this is "
    + "the rule a refactor would most plausibly break");
  assert.equal(selected[0].postType, "Page",
    "the page's type still travels (SH303), which is what the console resolves labels from");
  assert.deepEqual(selected[0].fields.map((field) => field.prop), ["TITLE"],
    "and the field set is that post's, not every field on the page");
});

test("the page's own fields come back as a set, in document order", async () => {
  const { window, document, posted } = await pageWithBridge(PAGE, { bodyAttrs: BODY_ATTRS });

  document.querySelector('[data-shio-post="page-1"] [data-shio-prop="TEXT"]')
    .dispatchEvent(new window.MouseEvent("click", { bubbles: true }));

  const [selected] = messagesOfType(posted, "sh:element-selected");
  assert.equal(selected.prop, "TEXT");
  assert.deepEqual(selected.fields.map((field) => field.prop), ["TITLE", "TEXT"],
    "the whole set, so the properties panel needs no round trip");
  assert.deepEqual(selected.fields.map((field) => field.value), ["Hello", "Body"]);
  assert.equal(selected.label, "TEXT",
    "the label is the raw name: SH303 removed the data-shio-label read rather than adding the write, "
    + "because a label belongs to the model and a copy in markup is a second place to be wrong");
});

test("an unowned field is visible, marked, and not editable", async () => {
  const { document } = await pageWithBridge(UNSTAMPED_PAGE);

  const orphan = document.querySelector('[data-shio-prop="ORPHAN"]');
  assert.equal(orphan.getAttribute("data-shio-editable"), "false",
    "marked rather than hidden: the author can see the annotation is there and incomplete");
  assert.equal(orphan.contentEditable, "false");

  const stamped = await pageWithBridge(PAGE, { bodyAttrs: BODY_ATTRS });
  const owned = stamped.document.querySelector('[data-shio-post="page-1"] [data-shio-prop="TITLE"]');
  assert.equal(owned.getAttribute("data-shio-editable"), "true");
  assert.equal(owned.contentEditable, "true");
});

test("an edit to an unowned field is reported to nobody", async () => {
  const { window, document, posted } = await pageWithBridge(UNSTAMPED_PAGE);

  document.querySelector('[data-shio-prop="ORPHAN"]')
    .dispatchEvent(new window.Event("input", { bubbles: true }));

  assert.deepEqual(messagesOfType(posted, "sh:content-changed"), [],
    "a save that goes nowhere is worse than a field that will not take the edit");
});

test("an edit to an owned field carries the post, the prop and the value", async () => {
  const { window, document, posted } = await pageWithBridge(PAGE, { bodyAttrs: BODY_ATTRS });

  const title = document.querySelector('[data-shio-post="page-1"] [data-shio-prop="TITLE"]');
  title.innerHTML = "Edited";
  title.dispatchEvent(new window.Event("input", { bubbles: true }));

  assert.deepEqual(messagesOfType(posted, "sh:content-changed"), [{
    postId: "page-1",
    prop: "TITLE",
    type: "text",
    value: "Edited",
    label: "TITLE",
  }]);
});

test("SH503: a field drawn into an attribute comes back with the attribute it lives in", async () => {
  const { window, document, posted } = await pageWithBridge(ATTR_PAGE, { bodyAttrs: BODY_ATTRS });

  document.querySelector('[data-shio-prop="ctaLabel"]')
    .dispatchEvent(new window.MouseEvent("click", { bubbles: true }));

  const [selected] = messagesOfType(posted, "sh:element-selected");
  assert.deepEqual(selected.fields, [
    { prop: "ctaLabel", type: "text", label: "ctaLabel", value: "Start" },
    { prop: "ctaHref", type: "attr", attr: "href", label: "ctaHref", value: "/signup" },
    { prop: "features.0.icon", type: "attr", attr: "data-shio-icon", label: "features.0.icon",
      value: "bolt" },
  ], "the destination was captured, stored and rendered into the href and declared by nothing, so "
    + "the panel had nine of the section's ten fields and not the one that repoints the button");
});

test("SH503: an attribute field is outlined and selectable, and never contenteditable", async () => {
  const { window, document, posted } = await pageWithBridge(ATTR_PAGE, { bodyAttrs: BODY_ATTRS });

  const row = document.querySelector("[data-shio-prop-attr]:not([data-shio-prop])");
  assert.equal(row.getAttribute("data-shio-editable"), "true");
  assert.equal(row.contentEditable, "false",
    "there is nothing to type into: the value is in the markup, not in a text node");

  row.dispatchEvent(new window.MouseEvent("click", { bubbles: true }));
  const [selected] = messagesOfType(posted, "sh:element-selected");
  assert.equal(selected.prop, "features.0.icon",
    "an element declaring only attribute fields still names one when selected, or the panel that "
    + "is its only edit surface can never be opened on it");
  assert.equal(selected.type, "attr");
});

test("SH503: the console writes an attribute field back into the page", async () => {
  const { window, document } = await pageWithBridge(ATTR_PAGE, { bodyAttrs: BODY_ATTRS });

  sendFromParent(window, {
    type: "sh:field-set",
    data: { postId: "section-1", prop: "ctaHref", value: "/pricing" },
  });

  assert.equal(document.querySelector('[data-shio-prop="ctaLabel"]').getAttribute("href"),
    "/pricing",
    "the panel is the edit surface for a field with no content, and without this the page keeps "
    + "showing the old destination until a refresh — a curator changing something and watching "
    + "nothing happen");
});

test("SH503: a write from another origin, or to another post, changes nothing", async () => {
  const { window, document } = await pageWithBridge(ATTR_PAGE, { bodyAttrs: BODY_ATTRS });
  const cta = document.querySelector('[data-shio-prop="ctaLabel"]');

  sendFromParent(window, {
    type: "sh:field-set",
    data: { postId: "section-1", prop: "ctaHref", value: "https://attacker.example" },
  }, "https://attacker.example");
  assert.equal(cta.getAttribute("href"), "/signup", "the origin check guards this listener too");

  sendFromParent(window, {
    type: "sh:field-set",
    data: { postId: "page-1", prop: "ctaHref", value: "/wrong" },
  });
  assert.equal(cta.getAttribute("href"), "/signup",
    "and a field is addressed by post and prop, so the page's own post cannot write a section's");
});

test("a message from another origin cannot change the mode", async () => {
  const { window, document } = await pageWithBridge(PAGE, { bodyAttrs: BODY_ATTRS });

  sendFromParent(window, { type: "sh:mode-change", data: { mode: "preview" } },
    "https://attacker.example");

  assert.equal(document.documentElement.getAttribute("data-shio-editing"), "true",
    "still in edit mode: the origin check is the whole reason this listener can exist safely");
});

test("the console's own message toggles the mode, and preview restores the page", async () => {
  const { window, document } = await pageWithBridge(PAGE, { bodyAttrs: BODY_ATTRS });
  const title = document.querySelector('[data-shio-post="page-1"] [data-shio-prop="TITLE"]');

  sendFromParent(window, { type: "sh:mode-change", data: { mode: "preview" } });

  assert.equal(document.documentElement.getAttribute("data-shio-editing"), "false");
  assert.equal(title.contentEditable, "false",
    "a curator toggling to preview is asking to see what a visitor sees");

  sendFromParent(window, { type: "sh:mode-change", data: { mode: "edit" } });

  assert.equal(document.documentElement.getAttribute("data-shio-editing"), "true");
  assert.equal(title.contentEditable, "true");
});

test("a malformed message is ignored rather than throwing", async () => {
  const { window, document } = await pageWithBridge(PAGE, { bodyAttrs: BODY_ATTRS });

  sendFromParent(window, null);
  sendFromParent(window, { type: 42 });
  sendFromParent(window, { type: "sh:unknown" });

  assert.equal(document.documentElement.getAttribute("data-shio-editing"), "true",
    "an exception in a message listener is invisible to the console, which would wait forever");
});

test("the affordances are scoped so preview leaves no trace", async () => {
  const { document } = await pageWithBridge(PAGE, { bodyAttrs: BODY_ATTRS });

  const style = document.querySelector("style[data-shio-editor]");
  assert.ok(style, "the page cannot have anticipated these, so they are injected");
  for (const rule of style.textContent.split("}").filter(Boolean)) {
    assert.ok(rule.includes('[data-shio-editing="true"]'),
      `every rule is scoped under the editing flag, or preview would not restore the page: ${rule}`);
  }
});

// --- SH336: the links the server pass never saw ---

const PREVIEW_URL = "https://shio.example/preview/Demo/about";
const PREVIEW_PATHS = ["/", "/contato", "/blog/post-1"];

/** Clicks an element the way a reader does, and reports whether the default was prevented. */
function click(window, element, init = {}) {
  const event = new window.MouseEvent("click", {
    bubbles: true, cancelable: true, button: 0, ...init,
  });
  element.dispatchEvent(event);
  return event.defaultPrevented;
}

/**
 * Where the bridge said it was going, from the `sh:navigating` it posts before leaving.
 *
 * Through `messagesOfType`, which crosses the jsdom realm boundary properly — a raw `.data` read
 * gives an object from the other realm and `deepEqual` against it reports two identical-looking
 * values as different (the note on that helper has the tell).
 */
function destination(posted) {
  const leaving = messagesOfType(posted, "sh:navigating");
  return leaving.length ? leaving[leaving.length - 1].to : null;
}

test("SH336: a JS-built link to a page of this site is redirected into the preview", async () => {
  const { window, document, posted } = await pageWithBridge(PAGE, {
    bodyAttrs: BODY_ATTRS, url: PREVIEW_URL, previewPaths: PREVIEW_PATHS,
  });
  // Built after attach(), which is the whole reason this mechanism exists: a region's own
  // JAVASCRIPT (SH248) can write a nav the server never had a chance to rewrite.
  const nav = document.createElement("a");
  nav.setAttribute("href", "/contato");
  document.body.appendChild(nav);

  assert.equal(click(window, nav), true,
    "the reader's click has to be taken over, or it leaves the preview and 404s");
  // SH1036 — WHERE it went. This test asserted only that the default was prevented, which is how a
  // rewrite that dropped the editor parameters survived a green suite for as long as it did. jsdom
  // implements no navigation, so `location` cannot answer; the bridge announces the departure and
  // that is the fact to read.
  assert.equal(destination(posted), "/preview/Demo/contato",
    "the click has to land inside this site's preview, at the page the link named");
});

test("SH1036: the editor and state parameters survive a click, or edit mode ends on the first one",
  async () => {
    const { window, document, posted } = await pageWithBridge(PAGE, {
      bodyAttrs: BODY_ATTRS,
      url: "https://shio.example/preview/Demo/about?editor=true&state=rev:3",
      previewPaths: PREVIEW_PATHS,
    });
    const nav = document.createElement("a");
    nav.setAttribute("href", "/contato");
    document.body.appendChild(nav);

    assert.equal(click(window, nav), true);
    // `?editor=true` is what injects this script at all, so a destination without it is a page with
    // no bridge -- and the console, which keyed its handshake on an iframe `src` that in-frame
    // navigation cannot change, went on reporting `connected` over it.
    // Verbatim, not re-encoded: the script copies the pair as the URL already holds it, so whatever
    // encoding the server sent survives a click unchanged. The Java side encodes because it builds
    // from a DECODED `request.getParameter` value; the two produce the same parameter either way,
    // and a script that re-encoded would double-escape a `%` the server had already written.
    assert.equal(destination(posted), "/preview/Demo/contato?editor=true&state=rev:3");
  });

test("SH1036: a link's own query wins, and the reader's is added beside it", async () => {
  const { window, document, posted } = await pageWithBridge(PAGE, {
    bodyAttrs: BODY_ATTRS,
    url: "https://shio.example/preview/Demo/about?editor=true&state=rev:3",
    previewPaths: PREVIEW_PATHS,
  });
  const nav = document.createElement("a");
  // The author wrote `state=published` and means it; a carried parameter that overrode it would
  // make the rewrite change what the page says rather than where it goes.
  nav.setAttribute("href", "/contato?state=published&page=2");
  document.body.appendChild(nav);

  assert.equal(click(window, nav), true);
  assert.equal(destination(posted),
    "/preview/Demo/contato?state=published&page=2&editor=true");
});

test("SH1036: a fragment stays a fragment, and the query goes before it", async () => {
  const { window, document, posted } = await pageWithBridge(PAGE, {
    bodyAttrs: BODY_ATTRS,
    url: "https://shio.example/preview/Demo/about?editor=true",
    previewPaths: PREVIEW_PATHS,
  });
  const nav = document.createElement("a");
  // `/a#b?c` is a fragment containing a question mark and not a query at all, so the split has to
  // take the fragment off first or the parameters land inside it and reach no server.
  nav.setAttribute("href", "/contato#section");
  document.body.appendChild(nav);

  assert.equal(click(window, nav), true);
  assert.equal(destination(posted), "/preview/Demo/contato?editor=true#section");
});

test("SH336: a real server path is left alone, so a file link still works", async () => {
  const { window, document } = await pageWithBridge(PAGE, {
    bodyAttrs: BODY_ATTRS, url: PREVIEW_URL, previewPaths: PREVIEW_PATHS,
  });
  const file = document.createElement("a");
  file.setAttribute("href", "/file_source/abc/logo.png");
  document.body.appendChild(file);

  assert.equal(click(window, file), false,
    "prefixing this would break a working file link to fix a broken page link - which is why "
    + "the browser half is handed the same index the server used");
});

test("SH336: a modified click is the reader asking for a new tab, and is never hijacked", async () => {
  const { window, document } = await pageWithBridge(PAGE, {
    bodyAttrs: BODY_ATTRS, url: PREVIEW_URL, previewPaths: PREVIEW_PATHS,
  });
  const nav = document.createElement("a");
  nav.setAttribute("href", "/contato");
  document.body.appendChild(nav);

  for (const modifier of ["metaKey", "ctrlKey", "shiftKey", "altKey"]) {
    assert.equal(click(window, nav, { [modifier]: true }), false,
      `${modifier}+click is open-in-new-tab, and taking it over is what the server pass was `
      + "chosen to avoid doing");
  }
});

test("SH336: with no index shipped, nothing is intercepted at all", async () => {
  const { window, document } = await pageWithBridge(PAGE, {
    bodyAttrs: BODY_ATTRS, url: PREVIEW_URL,
  });
  const nav = document.createElement("a");
  nav.setAttribute("href", "/contato");
  document.body.appendChild(nav);

  assert.equal(click(window, nav), false,
    "a site too large to ship its index loses the JS-built links and keeps everything else");
});

test("SH336: outside a preview - an external front end - nothing is intercepted", async () => {
  const { window, document } = await pageWithBridge(PAGE, {
    bodyAttrs: BODY_ATTRS, url: "https://site.example/about", previewPaths: PREVIEW_PATHS,
  });
  const nav = document.createElement("a");
  nav.setAttribute("href", "/contato");
  document.body.appendChild(nav);

  assert.equal(click(window, nav), false,
    "the editor works against a front end Shio does not serve, where /preview means nothing");
});
