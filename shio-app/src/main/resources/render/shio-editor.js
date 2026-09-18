/*
 * shio-editor.js — the page half of the Universal Editor bridge (SH185).
 *
 * The console's visual-editor page has posted `sh:*` messages into an iframe and listened
 * for replies since SH10, and its own comment said the replies came from "shio-editor.js".
 * That file did not exist — not here, not in create-shio-app, shio-react-sdk, shio-sections
 * or shio-client — so the editor could not work against anything, including the first-party
 * starter correctly deployed. This is the counterpart.
 *
 * It is deliberately dependency-free, framework-free and served as a static asset, because
 * the pages it runs in are whatever a template author wrote. It is injected only by the SH183
 * renderer and only when the request asks for it (`?editor=true`), so an ordinary preview
 * ships no editor code at all.
 *
 * ── The protocol ────────────────────────────────────────────────────────────────────────
 *
 * page → console
 *   sh:ready             { postId, url }            the bridge is live -- ON EVERY LOAD,
 *                                                     which is how the console learns the frame
 *                                                     navigated (SH1036): `url` is the page it is
 *                                                     live on, and the console re-keys on it
 *   sh:element-selected  SelectedElement            a curator clicked an editable element
 *   sh:content-changed   ContentChange              an editable element's text changed
 *   sh:navigating        { to, from }               a click in the page is leaving for another one
 *
 * console → page
 *   sh:mode-change       { mode: "edit"|"preview" } toggle affordances on and off
 *   sh:field-set         { postId, prop, value }    write an attribute field the panel edited
 *
 * ── Origins ─────────────────────────────────────────────────────────────────────────────
 *
 * Both directions are origin-checked. The console used to post with `"*"`, which hands the
 * message to whatever happens to be in the frame — a data-leak shape, and the site's own
 * origin was known the whole time. Here, the parent's origin is passed in on the script tag
 * rather than guessed: `document.referrer` is empty on some navigations and
 * `window.parent.location` throws cross-origin, so the only reliable source is the server
 * that rendered the page and knows who asked for it.
 */
(function () {
  "use strict";

  var script = document.currentScript;
  var PARENT_ORIGIN = (script && script.getAttribute("data-shio-editor-origin")) || "";
  var PAGE_POST = document.body ? document.body.getAttribute("data-shio-post") : null;
  var PAGE_TYPE = document.body ? document.body.getAttribute("data-shio-post-type") : null;

  if (!PARENT_ORIGIN || window.parent === window) {
    // Not framed, or nobody told us who framed us. Do nothing at all: a page that starts
    // making things contenteditable outside the editor is a page that corrupts content.
    return;
  }

  var FIELD = "[data-shio-prop]";
  /**
   * SH503 — the fields an element draws into its own attributes, `FIELD:ATTRIBUTE` per pair.
   *
   * `data-shio-prop` says what the element's *content* is, so one element declared one field and a
   * link is two of them: `<a data-shio-prop="ctaLabel" href="…">` let a curator rename the button
   * and not repoint it. The renderer copies the pairs verbatim — it has never parsed a prop — so
   * splitting them is this side's job, which is the right side for it: reading the value means
   * reading the attribute off the element anyway.
   */
  var ATTR_FIELD = "[data-shio-prop-attr]";
  var editing = false;

  function send(type, data) {
    window.parent.postMessage({ type: type, data: data || {} }, PARENT_ORIGIN);
  }

  /** The post an element's content belongs to: nearest ancestor that declares one. */
  function ownerOf(el) {
    var owner = el.closest("[data-shio-post]");
    return owner ? owner.getAttribute("data-shio-post") : null;
  }

  /**
   * A field's label is the *model's*, and the page does not carry one (SH303).
   *
   * This used to read `data-shio-label`, which nothing has ever emitted — the composer
   * translates `sh-field` and `sh-post` and there is no `sh-label` case — so the fallback was
   * the only branch that ever ran and every field in the editor was labelled `TEXT` or `HTML`.
   * The fix is not to start emitting it: a field's label is declared once, by the post-type,
   * where the console shows it and `shio pull` round-trips it, and a copy in the markup is a
   * second place for it to be wrong. So the page sends the post-type instead (below) and the
   * console labels from the schema — which is what `data-shio-post-type` was put on <body> for
   * and what nothing had used.
   */
  function labelOf(prop) {
    return prop;
  }

  /**
   * The `FIELD:ATTRIBUTE` pairs an element declares (SH503), split — `[]` when it declares none.
   *
   * A pair missing its colon names no attribute, so there is nothing to read and nothing to write.
   * Skipped rather than guessed: defaulting it to `href` would edit an attribute the template never
   * declared, and a link is only the commonest case of this, not the rule.
   */
  function attrPairsOf(el) {
    var raw = el.getAttribute("data-shio-prop-attr");
    if (!raw) return [];
    var pairs = [];
    raw.split(/\s+/).forEach(function (pair) {
      var cut = pair.indexOf(":");
      if (cut > 0 && cut < pair.length - 1) {
        pairs.push({ prop: pair.slice(0, cut), attr: pair.slice(cut + 1) });
      }
    });
    return pairs;
  }

  function describe(el) {
    var prop = el.getAttribute("data-shio-prop");
    var pairs = attrPairsOf(el);
    // An element may declare only attribute fields — a feature row's `icon` lives in a `data-`
    // attribute and the row has no text of its own — and it is still selectable: the panel is the
    // edit surface for those, so a selection that named nothing would leave them unreachable.
    var type = prop || !pairs.length ? "text" : "attr";
    if (!prop && pairs.length) prop = pairs[0].prop;
    return {
      postId: ownerOf(el),
      model: el.getAttribute("data-shio-region") || "",
      label: labelOf(prop),
      type: type,
      prop: prop,
      // SH303 — the post-type travels with the selection so the console can resolve every
      // field's declared label in one fetch. It is the page's own type, from <body>, and since
      // SH305 that is a *fallback*: a row of a listing is a post of some other type, and the
      // console reads that post's own declared fields instead of labelling it from this. The row's
      // type deliberately does not travel — ~31 bytes on every listing row of every page, paid by
      // every visitor, for a case only the editor uses (SH121's trade about ids).
      postType: PAGE_TYPE,
      // SH1037 — the declared widget, so the panel renders the field the model describes rather
      // than a bare input, and `affordance` says what this page decided to allow inline. Both
      // travel because the console cannot read the framed DOM: it is another origin.
      widget: el.getAttribute("data-shio-prop-type") || "",
      affordance: el.getAttribute("data-shio-affordance") || "",
      fields: fieldsOf(ownerOf(el))
    };
  }

  /**
   * Every editable field of the same post, so the properties panel can show the whole set
   * rather than only the one that was clicked. Cheap here and a round trip otherwise.
   */
  function fieldsOf(postId) {
    if (!postId) return [];
    var out = [];
    document.querySelectorAll(FIELD + "," + ATTR_FIELD).forEach(function (el) {
      if (ownerOf(el) !== postId) return;
      var prop = el.getAttribute("data-shio-prop");
      if (prop) {
        out.push({ prop: prop, type: "text", label: labelOf(prop), value: el.innerHTML });
      }
      // SH503 — and the fields this element draws into its attributes, in declaration order. The
      // attribute name travels with the field because the panel is the only place one of these can
      // be edited, and what comes back is `sh:field-set` naming the prop rather than the element.
      attrPairsOf(el).forEach(function (pair) {
        out.push({
          prop: pair.prop,
          type: "attr",
          attr: pair.attr,
          label: labelOf(pair.prop),
          value: el.getAttribute(pair.attr) || ""
        });
      });
    });
    return out;
  }

  /**
   * SH503 — the console writing back a field it edited in the properties panel.
   *
   * A text field is edited in place and reports itself. An attribute field has no content to type
   * into, so the panel is its edit surface — and without this the page would keep showing the old
   * destination until a refresh, which is a curator saving a change and watching nothing happen.
   *
   * Addressed by post and prop, never by a selector: the console has no selector for the element,
   * and the same field may be drawn on more than one of them.
   */
  function setField(data) {
    if (!data || !data.postId || !data.prop) return;
    var value = data.value == null ? "" : String(data.value);
    document.querySelectorAll(ATTR_FIELD).forEach(function (el) {
      if (ownerOf(el) !== data.postId) return;
      attrPairsOf(el).forEach(function (pair) {
        if (pair.prop === data.prop) el.setAttribute(pair.attr, value);
      });
    });
  }

  function onFieldInput(event) {
    var el = event.currentTarget;
    var postId = ownerOf(el);
    // An attribute-only element has no `data-shio-prop` and cannot be typed into; a stray input
    // event on one would report a change to a field that is not the one it names.
    if (!el.getAttribute("data-shio-prop")) return;
    // A field whose owner cannot be resolved is left visible and read-only rather than
    // silently swallowing edits — the template is missing an `sh-post`, and reporting that
    // is more useful than a save that goes nowhere.
    if (!postId) return;
    send("sh:content-changed", {
      postId: postId,
      prop: el.getAttribute("data-shio-prop"),
      type: "text",
      value: el.innerHTML,
      label: labelOf(el.getAttribute("data-shio-prop"))
    });
  }

  function onFieldClick(event) {
    if (!editing) return;
    event.preventDefault();
    event.stopPropagation();
    send("sh:element-selected", describe(event.currentTarget));
  }

  /**
   * What a field's declared widget lets the browser offer (SH1037).
   *
   * Edit mode used to be granted by the presence of `data-shio-prop` alone: every annotated element
   * became `contenteditable` and reported `innerHTML`. So a plain `Text` field absorbed the markup
   * of a paste, stored it, and then showed the tags as visible text — because the template escapes
   * a text field, which is correct. The defect was an editor offering rich editing over a field that
   * cannot hold it.
   *
   * Three outcomes, and the third is the one that matters most:
   *
   *   "plain" — `contenteditable="plaintext-only"`, so the browser itself refuses markup. Pasting
   *             rich content yields its text, which is what the field can store.
   *   "rich"  — `contenteditable="true"`, today's behaviour, for the widget that means HTML.
   *   "panel" — NOT contenteditable. A `Date`, a `Check Box`, a `Content Select` has a shape a
   *             caret cannot express, and typing into it produced a string the widget would refuse.
   *             It stays selectable, so clicking still opens the properties panel.
   *
   * An ABSENT attribute is "rich", not "panel", and that is a compatibility decision rather than a
   * default: a page rendered before this shipped, a listing row (whose type is not knowable at
   * compose time) and an external front end writing its own `data-shio-*` all carry no kind, and
   * treating them as uneditable would take away editing that works today.
   */
  var PLAIN_WIDGETS = ["Text", "Text Area"];
  var RICH_WIDGETS = ["HTML Editor"];

  function affordanceOf(el) {
    var declared = el.getAttribute("data-shio-prop-type");
    if (!declared) return "rich";
    if (PLAIN_WIDGETS.indexOf(declared) >= 0) return "plain";
    if (RICH_WIDGETS.indexOf(declared) >= 0) return "rich";
    return "panel";
  }

  function setMode(mode) {
    editing = mode === "edit";
    document.documentElement.setAttribute("data-shio-editing", editing ? "true" : "false");
    document.querySelectorAll(FIELD + "," + ATTR_FIELD).forEach(function (el) {
      var owned = !!ownerOf(el);
      // Only an owned field becomes editable. An unowned one still gets the outline, so the
      // author can see that the annotation is there and incomplete. And only a *text* field: an
      // attribute field's value is in the markup, so there is nothing to type into (SH503).
      var text = !!el.getAttribute("data-shio-prop");
      var affordance = text ? affordanceOf(el) : "panel";
      var inline = editing && owned && text && affordance !== "panel";
      // `plaintext-only` is the browser refusing markup rather than this script stripping it after
      // the fact — a paste handler runs too late to stop the DOM the browser already built, and
      // every subsequent read of innerHTML would carry it.
      // `plaintext-only` is not universal (Firefox got it in 136), and an unsupported value throws
      // on assignment rather than being ignored — so the fallback is explicit. `true` is the old
      // behaviour, which is worse for this field and not broken: the console's panel still renders
      // the declared widget, and SH956 strips at the write boundary either way.
      if (inline && affordance === "plain") {
        try {
          el.contentEditable = "plaintext-only";
        } catch (unsupported) {
          el.contentEditable = "true";
        }
      } else {
        el.contentEditable = inline ? "true" : "false";
      }
      el.setAttribute("data-shio-editable", owned ? "true" : "false");
      // The kind the browser acted on, so the console can render the same decision in its panel and
      // the e2e probe can read it. `data-shio-editable` says WHETHER, this says HOW.
      el.setAttribute("data-shio-affordance", text ? affordance : "attr");
    });
  }

  /**
   * SH336's second mechanism — the links the server pass never saw.
   *
   * The server rewrites every internal href in the HTML it renders. What it cannot rewrite is a
   * nav a region's own JAVASCRIPT builds at runtime (SH248), because that markup does not exist
   * when the page leaves the server. So a click is intercepted instead.
   *
   * It consults the same URL index the server used, handed over on the script tag: without it the
   * browser cannot tell a page from `/file_source/<id>/<name>`, and prefixing the second would
   * break a working file link to fix a broken page link. No index — a site too large to ship one —
   * means no interception, which loses only the JS-built links.
   */
  var PREVIEW_PATHS = (function () {
    var raw = script && script.getAttribute("data-shio-preview-paths");
    var known = {};
    if (!raw) return null;
    raw.split("\n").forEach(function (path) {
      if (path) known[path] = true;
    });
    return known;
  })();

  /** `/preview/<site>`, or "" when this page is not being served under one (external mode). */
  function previewBase() {
    var parts = window.location.pathname.split("/");
    return parts.length > 2 && parts[1] === "preview" ? "/preview/" + parts[2] : "";
  }

  /**
   * The renderer's own parameters of the page this script is running in (SH1036).
   *
   * A preview's MODE lives in its query -- `?editor=true` is what injects this script at all, and
   * `?state=rev:3` says which version is being looked at. A click that dropped them landed the
   * frame on a page with no bridge, and because the console keys its handshake on an iframe `src`
   * that in-frame navigation cannot change, the badge stayed green over a page that had no editor
   * on it. The server threads the same two onto every href it rewrites; this is the same rule for
   * the links that pass never saw.
   *
   * Only those two. Copying the whole query would carry a reader's own `?page=2` onto a link that
   * means something else by it.
   */
  var CARRIED = ["editor", "state"];

  function carriedParams() {
    var search = window.location.search;
    if (!search || search.length < 2) return "";
    var kept = [];
    search.slice(1).split("&").forEach(function (pair) {
      if (!pair) return;
      var name = pair.split("=")[0];
      if (CARRIED.indexOf(name) >= 0) kept.push(pair);
    });
    return kept.join("&");
  }

  /**
   * `href` with the carried parameters added, the link's own values winning.
   *
   * An author who wrote `?state=published` means that, so a carried parameter is only added when
   * the link does not already name it -- the same rule the server pass follows. The fragment is
   * split off first, because `/a#b?c` is a fragment containing a question mark and not a query.
   */
  function withCarried(href, carry) {
    if (!carry) return href;
    var hash = href.indexOf("#");
    var fragment = hash < 0 ? "" : href.slice(hash);
    var rest = hash < 0 ? href : href.slice(0, hash);
    var question = rest.indexOf("?");
    var existing = question < 0 ? "" : rest.slice(question + 1);
    var declared = existing ? existing.split("&").map(function (pair) {
      return pair.split("=")[0];
    }) : [];
    var added = carry.split("&").filter(function (pair) {
      return pair && declared.indexOf(pair.split("=")[0]) < 0;
    });
    if (!added.length) return href;
    var base = existing ? rest : rest.slice(0, question < 0 ? rest.length : question);
    return base + (existing ? "&" : "?") + added.join("&") + fragment;
  }

  function onDocumentClick(event) {
    // A field click is the editor's own and is handled (and prevented) elsewhere; a modified or
    // non-primary click is the reader asking for a new tab, a download or a menu, and hijacking
    // those is exactly what the server pass was chosen to avoid doing.
    if (!PREVIEW_PATHS || event.defaultPrevented || event.button !== 0) return;
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    var anchor = event.target && event.target.closest ? event.target.closest("a[href]") : null;
    if (!anchor || anchor.getAttribute("target")) return;
    var href = anchor.getAttribute("href");
    if (!href || href.charAt(0) !== "/" || href.charAt(1) === "/") return;
    if (href.indexOf("/preview/") === 0) return;
    var cut = href.search(/[?#]/);
    var path = cut < 0 ? href : href.slice(0, cut);
    var base = previewBase();
    if (!base || !PREVIEW_PATHS[path]) return;
    event.preventDefault();
    // SH1036 — the mode goes with it. Without this the next page ships no bridge script, so edit
    // mode ends on the curator's first click and nothing says so.
    var to = withCarried(base + href, carriedParams());
    // SH1036 — say where, BEFORE going. Two things fall out of announcing the departure rather than
    // only the arrival. The console can stop describing this page the moment the curator clicks,
    // instead of showing a selection and a Save button for a page that is unloading; and the
    // destination becomes a fact a test can read, which is what the postMessage suite could not do
    // — it asserted the click was PREVENTED and never where it went, so the dropped parameters this
    // task fixes survived a green suite.
    send("sh:navigating", { to: to, from: window.location.pathname });
    window.location.href = to;
  }

  function attach() {
    document.querySelectorAll(FIELD + "," + ATTR_FIELD).forEach(function (el) {
      el.addEventListener("input", onFieldInput);
      el.addEventListener("click", onFieldClick);
    });
    // Delegated, and on the document: the whole point is links that do not exist yet.
    document.addEventListener("click", onDocumentClick);
    injectStyles();
    setMode("edit");
    send("sh:ready", { postId: PAGE_POST, url: window.location.pathname });
  }

  /**
   * The affordances. Inline in a <style> the page cannot have anticipated, scoped under the
   * `data-shio-editing` flag so turning edit mode off restores the page exactly — a curator
   * toggling to preview is asking to see what a visitor sees.
   */
  function injectStyles() {
    var style = document.createElement("style");
    style.setAttribute("data-shio-editor", "true");
    style.textContent =
      '[data-shio-editing="true"] [data-shio-prop]{outline:1px dashed #ea580c;outline-offset:2px;cursor:text}' +
      '[data-shio-editing="true"] [data-shio-prop]:hover{outline-style:solid;background:rgba(234,88,12,.06)}' +
      '[data-shio-editing="true"] [data-shio-prop][data-shio-editable="false"]{outline-color:#9ca3af;cursor:not-allowed}' +
      // SH503 — an element that declares only attribute fields is outlined too, and with a
      // pointer rather than a caret: it is selectable and it is not typed into.
      '[data-shio-editing="true"] [data-shio-prop-attr]:not([data-shio-prop])' +
      '{outline:1px dashed #ea580c;outline-offset:2px;cursor:pointer}' +
      '[data-shio-editing="true"] [data-shio-prop-attr]:not([data-shio-prop]):hover' +
      '{outline-style:solid;background:rgba(234,88,12,.06)}';
    document.head.appendChild(style);
  }

  window.addEventListener("message", function (event) {
    if (event.origin !== PARENT_ORIGIN) return;
    var msg = event.data;
    if (!msg || typeof msg.type !== "string") return;
    if (msg.type === "sh:mode-change") {
      setMode(msg.data && msg.data.mode);
    }
    if (msg.type === "sh:field-set") {
      setField(msg.data);
    }
  });

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", attach);
  } else {
    attach();
  }
})();
