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
 *   sh:ready             { postId, url }            the bridge is live
 *   sh:element-selected  SelectedElement            a curator clicked an editable element
 *   sh:content-changed   ContentChange              an editable element's text changed
 *
 * console → page
 *   sh:mode-change       { mode: "edit"|"preview" } toggle affordances on and off
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

  function describe(el) {
    var prop = el.getAttribute("data-shio-prop");
    return {
      postId: ownerOf(el),
      model: el.getAttribute("data-shio-region") || "",
      label: labelOf(prop),
      type: "text",
      prop: prop,
      // SH303 — the post-type travels with the selection so the console can resolve every
      // field's declared label in one fetch. It is the page's own type, from <body>, and since
      // SH305 that is a *fallback*: a row of a listing is a post of some other type, and the
      // console reads that post's own declared fields instead of labelling it from this. The row's
      // type deliberately does not travel — ~31 bytes on every listing row of every page, paid by
      // every visitor, for a case only the editor uses (SH121's trade about ids).
      postType: PAGE_TYPE,
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
    document.querySelectorAll(FIELD).forEach(function (el) {
      if (ownerOf(el) !== postId) return;
      var prop = el.getAttribute("data-shio-prop");
      out.push({ prop: prop, type: "text", label: labelOf(prop), value: el.innerHTML });
    });
    return out;
  }

  function onFieldInput(event) {
    var el = event.currentTarget;
    var postId = ownerOf(el);
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

  function setMode(mode) {
    editing = mode === "edit";
    document.documentElement.setAttribute("data-shio-editing", editing ? "true" : "false");
    document.querySelectorAll(FIELD).forEach(function (el) {
      var owned = !!ownerOf(el);
      // Only an owned field becomes editable. An unowned one still gets the outline, so the
      // author can see that the annotation is there and incomplete.
      el.contentEditable = editing && owned ? "true" : "false";
      el.setAttribute("data-shio-editable", owned ? "true" : "false");
    });
  }

  function attach() {
    document.querySelectorAll(FIELD).forEach(function (el) {
      el.addEventListener("input", onFieldInput);
      el.addEventListener("click", onFieldClick);
    });
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
      '[data-shio-editing="true"] [data-shio-prop][data-shio-editable="false"]{outline-color:#9ca3af;cursor:not-allowed}';
    document.head.appendChild(style);
  }

  window.addEventListener("message", function (event) {
    if (event.origin !== PARENT_ORIGIN) return;
    var msg = event.data;
    if (!msg || typeof msg.type !== "string") return;
    if (msg.type === "sh:mode-change") {
      setMode(msg.data && msg.data.mode);
    }
  });

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", attach);
  } else {
    attach();
  }
})();
