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
    // SH1041 — and what it held before, which only the page can know: the message fires after the
    // change, so without this the console saw the new value and never the old one and had nothing
    // to revert to. Here it is the edited text itself, because the harness dispatched no focus
    // event and the element's own content is the fallback.
    was: "Edited",
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

// --- SH1041: what a field held before the typing, and putting it back ---

test("SH1041: an edit carries what the field held before it, which only the page knows", async () => {
  const { window, document, posted } = await pageWithBridge(PAGE, { bodyAttrs: BODY_ATTRS });
  const title = document.querySelector('[data-shio-post="page-1"] [data-shio-prop="TITLE"]');

  title.dispatchEvent(new window.FocusEvent("focus"));
  title.innerHTML = "Edited";
  title.dispatchEvent(new window.Event("input", { bubbles: true }));

  // `sh:content-changed` fires AFTER the change, so the console sees the new value and never the
  // old one — which is why the pending set could only ever be cleared all at once.
  assert.equal(messagesOfType(posted, "sh:content-changed").at(-1).was, "Hello");
});

test("SH1041: the baseline is what the field held before this round, not a keystroke ago", async () => {
  const { window, document, posted } = await pageWithBridge(PAGE, { bodyAttrs: BODY_ATTRS });
  const title = document.querySelector('[data-shio-post="page-1"] [data-shio-prop="TITLE"]');

  title.dispatchEvent(new window.FocusEvent("focus"));
  for (const text of ["H", "He", "Hey"]) {
    title.innerHTML = text;
    title.dispatchEvent(new window.Event("input", { bubbles: true }));
  }

  // Every message carries the same original. The console keeps the first either way, and the two
  // halves agreeing is what makes an undo mean "before I started on this field".
  assert.deepEqual(
    messagesOfType(posted, "sh:content-changed").map((change) => change.was),
    ["Hello", "Hello", "Hello"]);
});

test("SH1041: an edit with no focus event still reports a baseline", async () => {
  const { window, document, posted } = await pageWithBridge(PAGE, { bodyAttrs: BODY_ATTRS });
  const title = document.querySelector('[data-shio-post="page-1"] [data-shio-prop="TITLE"]');

  // A field reached by keyboard, or a page whose focus event the harness does not reproduce. The
  // element's own content is the honest fallback and is right for the first edit of a session.
  title.innerHTML = "Edited";
  title.dispatchEvent(new window.Event("input", { bubbles: true }));

  assert.equal(messagesOfType(posted, "sh:content-changed").at(-1).was, "Edited",
    "the fallback is the element as it is now: wrong by one edit, and never undefined, which is "
    + "what the console checks before offering a revert at all");
});

test("SH1041: the console can write a TEXT field back, which is what a revert is", async () => {
  const { window, document } = await pageWithBridge(PAGE, { bodyAttrs: BODY_ATTRS });
  const title = document.querySelector('[data-shio-post="page-1"] [data-shio-prop="TITLE"]');
  title.innerHTML = "Edited";

  sendFromParent(window, {
    type: "sh:field-set",
    data: { postId: "page-1", prop: "TITLE", value: "Hello" },
  });

  // Clearing the console's count alone would leave the frame showing text the editor is no longer
  // going to save — a worse lie than having no revert at all.
  assert.equal(title.innerHTML, "Hello");
});

test("SH1041: a plain field is written as text, because that is what it can hold", async () => {
  const { window, document } = await pageWithBridge(TYPED_PAGE, { bodyAttrs: BODY_ATTRS });

  sendFromParent(window, {
    type: "sh:field-set",
    data: { postId: "page-1", prop: "TITLE", value: "<b>bold</b>" },
  });

  // SH1037 made `plaintext-only` refuse markup on the way in from a paste, and nothing refused it
  // on the way in from here. The same decision about what the field can hold applies to both.
  const title = document.querySelector('[data-shio-prop="TITLE"]');
  assert.equal(title.textContent, "<b>bold</b>");
  assert.equal(title.querySelector("b"), null);
});

test("SH1041: after a revert the baseline moves with it", async () => {
  const { window, document, posted } = await pageWithBridge(PAGE, { bodyAttrs: BODY_ATTRS });
  const title = document.querySelector('[data-shio-post="page-1"] [data-shio-prop="TITLE"]');

  sendFromParent(window, {
    type: "sh:field-set", data: { postId: "page-1", prop: "TITLE", value: "Reverted" },
  });
  title.innerHTML = "Edited again";
  title.dispatchEvent(new window.Event("input", { bubbles: true }));

  assert.equal(messagesOfType(posted, "sh:content-changed").at(-1).was, "Reverted",
    "what was there is what is there now, so a second edit reports the value the curator just "
    + "went back to rather than the one they had already undone");
});

// --- SH1037: a field's declared widget decides what the browser offers ---

/** The same page, with each field declaring the widget its model gave it. */
const TYPED_PAGE = `
  <div data-shio-post="page-1" data-shio-region="MAIN">
    <h1 data-shio-prop="TITLE" data-shio-prop-type="Text">Hello</h1>
    <div data-shio-prop="BODY" data-shio-prop-type="HTML Editor">Body</div>
    <time data-shio-prop="WHEN" data-shio-prop-type="Date">2026</time>
    <span data-shio-prop="LEGACY">no kind</span>
  </div>
`;

test("SH1175: a plain field reports text, so a trailing space is a space and not an entity", async () => {
  const { window, document, posted } = await pageWithBridge(TYPED_PAGE, { bodyAttrs: BODY_ATTRS });
  const title = document.querySelector('[data-shio-prop="TITLE"]');

  // What a browser leaves behind when a curator types a space at the end of a contenteditable:
  // U+00A0, which `innerHTML` serialises as `&nbsp;`. Read that way, the server stored the entity
  // as text, the template escaped it, and every save added one more — measured on a live instance
  // as `Acme&amp;amp;amp;nbsp;&amp;amp;nbsp;&amp;nbsp;&nbsp;` after four.
  title.focus?.()
  title.dispatchEvent(new window.Event("focus"))
  title.textContent = "Hello "
  title.dispatchEvent(new window.Event("input", { bubbles: true }))

  const change = messagesOfType(posted, "sh:content-changed").at(-1)
  assert.equal(change.value, "Hello ", "SH1175: a plain field's value is its text, with the space back")
  assert.doesNotMatch(change.value, /&/u, "SH1175: no entity reaches the server from a plain field")
  assert.equal(change.was, "Hello", "SH1175: and its baseline is read the same way")
})

test("SH1175: a rich field still reports its markup", async () => {
  const { window, document, posted } = await pageWithBridge(TYPED_PAGE, { bodyAttrs: BODY_ATTRS });
  const body = document.querySelector('[data-shio-prop="BODY"]');
  body.innerHTML = "<b>Body</b>"
  body.dispatchEvent(new window.Event("input", { bubbles: true }))
  assert.equal(messagesOfType(posted, "sh:content-changed").at(-1).value, "<b>Body</b>",
    "SH1175: an HTML Editor field holds markup, so it is read as markup")
})

test("SH1037: a Text field is plaintext-only, so a paste cannot put markup in it", async () => {
  const { document } = await pageWithBridge(TYPED_PAGE, { bodyAttrs: BODY_ATTRS });
  const title = document.querySelector('[data-shio-prop="TITLE"]');

  // `plaintext-only` is the BROWSER refusing markup, not this script stripping it afterwards: a
  // paste handler runs after the DOM the browser already built, and every later read of innerHTML
  // would carry it. jsdom may not implement the value, in which case the script falls back to
  // "true" — so the assertion is that it is one of the two, and the affordance is what records
  // which decision was made.
  assert.equal(title.getAttribute("data-shio-affordance"), "plain",
    "a Text field's affordance is plain, whatever contenteditable the engine supports");
  assert.ok(["plaintext-only", "true"].includes(title.contentEditable),
    `a Text field must be editable; got ${title.contentEditable}`);
});

test("SH1037: an HTML Editor field keeps rich editing", async () => {
  const { document } = await pageWithBridge(TYPED_PAGE, { bodyAttrs: BODY_ATTRS });
  const body = document.querySelector('[data-shio-prop="BODY"]');

  assert.equal(body.getAttribute("data-shio-affordance"), "rich");
  assert.equal(body.contentEditable, "true");
});

test("SH1037: a Date field is not contenteditable, and is still selectable", async () => {
  const { document } = await pageWithBridge(TYPED_PAGE, { bodyAttrs: BODY_ATTRS });
  const when = document.querySelector('[data-shio-prop="WHEN"]');

  // Typing into a Date produced a string the widget would refuse, so the caret is the wrong tool.
  // It keeps `data-shio-editable="true"` because it IS editable — in the panel, which is what the
  // click still opens. An element the curator cannot click is a field with no way to reach it.
  assert.equal(when.getAttribute("data-shio-affordance"), "panel");
  assert.equal(when.contentEditable, "false");
  assert.equal(when.getAttribute("data-shio-editable"), "true");
});

test("SH1037: a field with no declared kind stays richly editable", async () => {
  const { document } = await pageWithBridge(TYPED_PAGE, { bodyAttrs: BODY_ATTRS });
  const legacy = document.querySelector('[data-shio-prop="LEGACY"]');

  // Compatibility, not a default anybody chose: a page rendered before this shipped, a listing row
  // (whose type is not knowable at compose time) and an external front end writing its own
  // `data-shio-*` all carry no kind. Reading that as "panel" would take away editing that works.
  assert.equal(legacy.getAttribute("data-shio-affordance"), "rich");
  assert.equal(legacy.contentEditable, "true");
});

test("SH1037: the selection carries the widget and the affordance", async () => {
  const { window, document, posted } = await pageWithBridge(TYPED_PAGE, { bodyAttrs: BODY_ATTRS });
  const title = document.querySelector('[data-shio-prop="TITLE"]');

  title.dispatchEvent(new window.MouseEvent("click", { bubbles: true, cancelable: true }));

  // The console cannot read the framed DOM — it is another origin — so the panel can only render
  // the declared widget if the selection says what it is.
  const [selected] = messagesOfType(posted, "sh:element-selected");
  assert.equal(selected.widget, "Text");
  assert.equal(selected.affordance, "plain");
});

test("SH1037: ready announces the protocol version and what this bridge can do", async () => {
  const { posted } = await pageWithBridge(TYPED_PAGE, { bodyAttrs: BODY_ATTRS });

  const [ready] = messagesOfType(posted, "sh:ready");
  assert.equal(ready.protocol, 2);
  // Derived from what is true of this page, not hard-coded: `navigate` is absent because no URL
  // index was shipped, so the click interception is off and claiming it would be a lie the console
  // acts on — it would stop offering its own navigation.
  assert.deepEqual(ready.capabilities.sort(),
    ["edit-inline", "field-kinds", "field-set", "select"]);
  assert.equal(ready.postType, "Page", "the page's type, so the panel need not wait for a click");
  assert.equal(ready.postId, "page-1");
});

test("SH1037: navigate is claimed only when the index that enables it was shipped", async () => {
  const { posted } = await pageWithBridge(TYPED_PAGE, {
    bodyAttrs: BODY_ATTRS, url: PREVIEW_URL, previewPaths: ["/", "/contato"],
  });

  const [ready] = messagesOfType(posted, "sh:ready");
  assert.ok(ready.capabilities.includes("navigate"),
    "with an index in hand the bridge handles clicks, and says so");
});

test("SH1037: the rich-text toolbar exists, is hidden, and offers only what the allow-list keeps",
  async () => {
    const { document } = await pageWithBridge(TYPED_PAGE, { bodyAttrs: BODY_ATTRS });
    const bar = document.getElementById("shio-editor-toolbar");

    assert.ok(bar, "a rich field needs somewhere to format from");
    assert.ok(bar.hasAttribute("hidden"),
      "hidden until a rich field has the selection — it must never appear over a plaintext one");
    // Restricted on purpose: a toolbar offering headings, colours and fonts would be offering
    // writes SH956's allow-list then strips, so the curator sees the change, saves, and loses it.
    assert.deepEqual(
      Array.from(bar.querySelectorAll("button")).map((b) => b.getAttribute("data-shio-command")),
      ["bold", "italic", "createLink", "removeFormat"]);
  });

// --- SH1038: a section is an object, not a field ---

/** A page of two sections, annotated the way the composer leaves them. */
const SECTION_PAGE = `
  <main data-shio-sections="2">
    <section data-shio-section="SectionHero" data-shio-post="hero-1" data-shio-section-index="0">
      <h1 data-shio-prop="TITLE" data-shio-prop-type="Text">Hello</h1>
      <p class="filler">around the field</p>
    </section>
    <section data-shio-section="SectionFaq" data-shio-post="faq-1" data-shio-section-index="1">
      <p class="filler">second</p>
    </section>
  </main>
`;

test("SH1038: clicking the space around a field selects the section that holds it", async () => {
  const { window, document, posted } = await pageWithBridge(SECTION_PAGE, { bodyAttrs: BODY_ATTRS });

  document.querySelector("section[data-shio-section='SectionFaq'] .filler")
    .dispatchEvent(new window.MouseEvent("click", { bubbles: true, cancelable: true }));

  const [section] = messagesOfType(posted, "sh:component-selected");
  assert.equal(section.section, "SectionFaq");
  assert.equal(section.postId, "faq-1");
  // A position is only meaningful against a length, and the position AFTER the last section is a
  // real destination with no element on it — which is what makes a move expressible at all.
  assert.equal(section.index, 1);
  assert.equal(section.of, 2);
  // The Page is what a reorder rewrites: moving a hero replaces the Page's array and the hero
  // itself does not change. One deterministic write, so no new write path is needed.
  assert.equal(section.pagePostId, "page-1");
});

test("SH1038: clicking a field selects the field, not the section around it", async () => {
  const { window, document, posted } = await pageWithBridge(SECTION_PAGE, { bodyAttrs: BODY_ATTRS });

  document.querySelector('[data-shio-prop="TITLE"]')
    .dispatchEvent(new window.MouseEvent("click", { bubbles: true, cancelable: true }));

  // The field handler stops propagation and the section handler runs after it, which is the whole
  // interaction: the headline is the headline and the space around it is the hero.
  assert.equal(messagesOfType(posted, "sh:element-selected").length, 1);
  assert.equal(messagesOfType(posted, "sh:component-selected").length, 0);
});

test("SH1038: a link inside a section navigates rather than selecting it", async () => {
  const { window, document, posted } = await pageWithBridge(SECTION_PAGE, {
    bodyAttrs: BODY_ATTRS, url: PREVIEW_URL, previewPaths: PREVIEW_PATHS,
  });
  const link = document.createElement("a");
  link.setAttribute("href", "/contato");
  document.querySelector("section[data-shio-section='SectionFaq']").appendChild(link);

  link.dispatchEvent(new window.MouseEvent("click", { bubbles: true, cancelable: true, button: 0 }));

  assert.equal(messagesOfType(posted, "sh:component-selected").length, 0,
    "a navigation is not a selection, whatever it happens to be inside");
  assert.equal(destination(posted), "/preview/Demo/contato");
});

/** Three sections, which is the fewest that can tell "moved up" from "moved to the front". */
const THREE_SECTIONS = `
  <main data-shio-sections="3">
    <section data-shio-section="SectionHero" data-shio-post="hero-1" data-shio-section-index="0">
      <h1 data-shio-prop="TITLE" data-shio-prop-type="Text">Hello</h1>
    </section>
    <section data-shio-section="SectionFeatures" data-shio-post="feat-1" data-shio-section-index="1">
      <p class="filler">second</p>
    </section>
    <section data-shio-section="SectionFaq" data-shio-post="faq-1" data-shio-section-index="2">
      <p class="filler">third</p>
    </section>
  </main>
`;

/** The section types on the page, in document order — what a move is supposed to change. */
function sectionOrder(document) {
  return Array.from(document.querySelectorAll("[data-shio-section]"))
    .map((section) => section.getAttribute("data-shio-section"));
}

/** Every section's recorded index, which has to agree with document order after an op. */
function sectionIndexes(document) {
  return Array.from(document.querySelectorAll("[data-shio-section]"))
    .map((section) => Number(section.getAttribute("data-shio-section-index")));
}

/** Selects a section the way a curator does: a click on the part of it that is not a field. */
function selectSection(window, document, type) {
  document.querySelector(`section[data-shio-section='${type}']`)
    .dispatchEvent(new window.MouseEvent("click", { bubbles: true, cancelable: true }));
}

test("SH1038: the selection carries the box the console draws its overlay over", async () => {
  const { window, document, posted } = await pageWithBridge(SECTION_PAGE, { bodyAttrs: BODY_ATTRS });

  selectSection(window, document, "SectionFaq");

  // The overlay is the CONSOLE's — a stylesheet the page injects can outline a section and cannot
  // draw a toolbar over it, and the buttons have to live where the writes are. The console cannot
  // measure this document (another origin), so the box travels with the selection.
  const [section] = messagesOfType(posted, "sh:component-selected");
  assert.deepEqual(Object.keys(section.rect).sort(), ["height", "left", "top", "width"]);
});

test("SH1038: a move from the console reorders the page and renumbers what is left", async () => {
  const { window, document, posted } = await pageWithBridge(THREE_SECTIONS, { bodyAttrs: BODY_ATTRS });
  selectSection(window, document, "SectionFaq");

  sendFromParent(window, { type: "sh:section-op", data: { op: "move", index: 2, to: 0 } });

  assert.deepEqual(sectionOrder(document),
    ["SectionFaq", "SectionHero", "SectionFeatures"],
    "optimistic: the write is a draft merge and the frame renders the published row, so nothing "
    + "about the write would make this document change — the curator would watch the page sit still");
  assert.deepEqual(sectionIndexes(document), [0, 1, 2],
    "and every position is re-derived, or the next selection reports where the section used to be "
    + "and the write after that is against the wrong entry");
  const echoed = messagesOfType(posted, "sh:component-selected").at(-1);
  assert.equal(echoed.section, "SectionFaq");
  assert.equal(echoed.index, 0, "the page confirms the new position rather than the console "
    + "assuming the op landed");
});

test("SH1038: a move past the last position lands at the end", async () => {
  const { window, document } = await pageWithBridge(THREE_SECTIONS, { bodyAttrs: BODY_ATTRS });
  selectSection(window, document, "SectionHero");

  // The position AFTER the last section is a real destination with no element on it, which is the
  // reason `of` travels beside `index` at all.
  sendFromParent(window, { type: "sh:section-op", data: { op: "move", index: 0, to: 2 } });

  assert.deepEqual(sectionOrder(document),
    ["SectionFeatures", "SectionFaq", "SectionHero"]);
});

test("SH1038: remove takes the section out and reports that the selection is gone", async () => {
  const { window, document, posted } = await pageWithBridge(THREE_SECTIONS, { bodyAttrs: BODY_ATTRS });
  selectSection(window, document, "SectionFeatures");

  sendFromParent(window, { type: "sh:section-op", data: { op: "remove", index: 1 } });

  assert.deepEqual(sectionOrder(document), ["SectionHero", "SectionFaq"]);
  assert.equal(document.querySelector("main").getAttribute("data-shio-sections"), "2",
    "the container's count is what says where a section may move TO, so it moves with the list");
  assert.deepEqual(messagesOfType(posted, "sh:section-geometry").at(-1), { rect: null },
    "a null box takes the overlay down rather than leaving it over whatever moved into that space");
});

test("SH1038: duplicate places the section twice, and the copy's fields are live", async () => {
  const { window, document, posted } = await pageWithBridge(THREE_SECTIONS, { bodyAttrs: BODY_ATTRS });
  selectSection(window, document, "SectionHero");

  sendFromParent(window, { type: "sh:section-op", data: { op: "duplicate", index: 0 } });

  assert.deepEqual(sectionOrder(document),
    ["SectionHero", "SectionHero", "SectionFeatures", "SectionFaq"],
    "the array holds references, so a duplicated entry is a second PLACEMENT of one post and not a "
    + "copy of it — which the console says out loud rather than letting a curator find out");
  const copy = document.querySelectorAll("[data-shio-section='SectionHero']")[1];
  copy.querySelector('[data-shio-prop="TITLE"]').innerHTML = "Edited";
  copy.querySelector('[data-shio-prop="TITLE"]')
    .dispatchEvent(new window.Event("input", { bubbles: true }));

  // `cloneNode` copies attributes and children and nothing that was added with `addEventListener`,
  // so without rebinding the copy would be outlined, editable-looking and inert.
  assert.equal(messagesOfType(posted, "sh:content-changed").at(-1).value, "Edited");
});

test("SH1038: a section op from another origin changes nothing", async () => {
  const { window, document } = await pageWithBridge(THREE_SECTIONS, { bodyAttrs: BODY_ATTRS });
  selectSection(window, document, "SectionHero");

  sendFromParent(window, { type: "sh:section-op", data: { op: "remove", index: 0 } },
    "https://attacker.example");

  assert.deepEqual(sectionOrder(document),
    ["SectionHero", "SectionFeatures", "SectionFaq"],
    "this is the one inbound message that changes the SHAPE of the page, so the origin check on "
    + "that listener is the whole reason it can exist");
});

test("SH1038: an op with nothing selected changes nothing", async () => {
  const { window, document } = await pageWithBridge(THREE_SECTIONS, { bodyAttrs: BODY_ATTRS });

  // The op acts on the curator's own selection and on nothing the sender names: with two composed
  // lists there are two sections at index 0, so `{op, index}` names two elements and a move would
  // rewrite whichever the selector reached first.
  sendFromParent(window, { type: "sh:section-op", data: { op: "remove", index: 0 } });

  assert.deepEqual(sectionOrder(document), ["SectionHero", "SectionFeatures", "SectionFaq"]);
});

test("SH1038: selecting a field drops the section, so the overlay does not outlive it", async () => {
  const { window, document } = await pageWithBridge(THREE_SECTIONS, { bodyAttrs: BODY_ATTRS });
  selectSection(window, document, "SectionHero");

  document.querySelector('[data-shio-prop="TITLE"]')
    .dispatchEvent(new window.MouseEvent("click", { bubbles: true, cancelable: true }));
  sendFromParent(window, { type: "sh:section-op", data: { op: "remove", index: 0 } });

  assert.deepEqual(sectionOrder(document), ["SectionHero", "SectionFeatures", "SectionFaq"],
    "a field and the section holding it are two objects, and a section left selected here would "
    + "keep the move controls over something the curator has stopped talking about");
});

test("SH1038: a scroll re-reports where the selected section now is", async () => {
  const { window, document, posted } = await pageWithBridge(THREE_SECTIONS, { bodyAttrs: BODY_ATTRS });
  selectSection(window, document, "SectionFaq");

  window.dispatchEvent(new window.Event("scroll"));

  // The box is in the framed VIEWPORT's coordinates, which is the space the console can map: the
  // iframe occupies one box in its layout and the framed viewport is that box. The price of that
  // choice is that a scroll moves it, which is what this message is for.
  const geometry = messagesOfType(posted, "sh:section-geometry").at(-1);
  assert.deepEqual(Object.keys(geometry.rect).sort(), ["height", "left", "top", "width"]);
});

test("SH1038: a scroll with nothing selected says nothing at all", async () => {
  const { window, posted } = await pageWithBridge(THREE_SECTIONS, { bodyAttrs: BODY_ATTRS });

  window.dispatchEvent(new window.Event("scroll"));

  assert.deepEqual(messagesOfType(posted, "sh:section-geometry"), [],
    "a message per scroll event is a message per pixel, and there is nothing to draw");
});

// --- SH1039: what may be inserted, and where ---

test("SH1039: the selection carries the types this container says fit in it", async () => {
  const { window, document, posted } = await pageWithBridge(`
    <main data-shio-sections="1" data-shio-accepts="SectionHero  SectionHero SectionCta">
      <section data-shio-section="SectionFaq" data-shio-post="faq-1" data-shio-section-index="0">
        <p class="filler">x</p>
      </section>
    </main>
  `, { bodyAttrs: BODY_ATTRS });

  selectSection(window, document, "SectionFaq");

  // De-duplicated in the order written. The catalogue itself — each type's title, description and
  // fields — is the server's, because only it knows what this instance has provisioned; a copy of
  // that in the markup would be a second place for it to be wrong.
  const [section] = messagesOfType(posted, "sh:component-selected");
  assert.deepEqual(section.accepts, ["SectionHero", "SectionCta"]);
});

test("SH1039: a container that declares nothing accepts nothing, not everything", async () => {
  const { window, document, posted } = await pageWithBridge(SECTION_PAGE, { bodyAttrs: BODY_ATTRS });

  selectSection(window, document, "SectionHero");

  // Never read as "anything": that would need a list of which post-types are sections, and the
  // server bans a third copy of the vocabulary. An empty list is "nothing is known to fit here",
  // and the server's catalogue carries the declaration that would change it.
  assert.deepEqual(messagesOfType(posted, "sh:component-selected")[0].accepts, []);
});

test("SH1038: sections is claimed only when the page has one", async () => {
  const withSections = await pageWithBridge(SECTION_PAGE, { bodyAttrs: BODY_ATTRS });
  const without = await pageWithBridge(PAGE, { bodyAttrs: BODY_ATTRS });

  assert.ok(messagesOfType(withSections.posted, "sh:ready")[0].capabilities.includes("sections"));
  // A console told otherwise would offer a component panel over a page with no component in it.
  assert.ok(!messagesOfType(without.posted, "sh:ready")[0].capabilities.includes("sections"));
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
