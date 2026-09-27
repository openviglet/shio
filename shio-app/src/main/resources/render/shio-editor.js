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
 *   sh:ready             ReadyMessage               the bridge is live -- ON EVERY LOAD,
 *                                                     which is how the console learns the frame
 *                                                     navigated (SH1036): `url` is the page it is
 *                                                     live on, and the console re-keys on it
 *   sh:element-selected  SelectedElement            a curator clicked an editable element
 *   sh:content-changed   ContentChange              an editable element's text changed; `was` is
 *                                                     what it held before this round of typing,
 *                                                     which only the page can know (SH1041)
 *   sh:navigating        { to, from }               a click in the page is leaving for another one
 *   sh:component-selected SelectedSection            a curator clicked a section, not a field of it
 *   sh:section-geometry  { rect }                   where the selected section is NOW, so the
 *                                                     console can keep its overlay on it -- and
 *                                                     `rect: null` when there is no longer one
 *
 * console → page
 *   sh:mode-change       { mode: "edit"|"preview" } toggle affordances on and off
 *   sh:field-set         { postId, prop, value }    write a field the console decided: an
 *                                                     attribute one the panel edited (SH503), or
 *                                                     a text one being reverted or replayed into
 *                                                     a reloaded frame (SH1041)
 *   sh:section-op        { op, index, to }          move, remove or duplicate the selected
 *                                                     section, applied optimistically here while
 *                                                     the console writes the Page's array
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

  /**
   * SH1038 — a section, which is an OBJECT on the page rather than a field of one.
   *
   * The bridge bound two attributes and reported an item type of `text` or `attr`, so the editor
   * could change a hero's headline and never the hero: a section could not be selected, moved,
   * duplicated or removed, although `post.upsert` can do all four by rewriting the owning Page's
   * ordered reference array. Everything needed was already in the DOM — the generated layout writes
   * `data-shio-section` naming the type, the server's region walk reads it, and SH1038's composer
   * pass adds the position beside it — and only this half was missing.
   */
  var SECTION = "[data-shio-section]";
  var CONTAINER = "[data-shio-sections]";
  var editing = false;

  /**
   * The section element a curator selected, which is what an op acts on (SH1038).
   *
   * Held here rather than addressed by the incoming message, and that is the point: with two
   * composed lists on one page there are two sections at index 0, so `{op, index}` names two
   * elements and a move would rewrite whichever the selector reached first. What a curator
   * selected is one element and the console is answering about that one.
   */
  var selectedSection = null;

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

  /**
   * The section an element is inside, as the object a curator selects (SH1038).
   *
   * `index` and `of` are what make a MOVE expressible: a position is only meaningful against a
   * length, and the position after the last section is a real destination with no element on it.
   * Both are read off the DOM rather than counted here — the server derived them from document
   * order, which is the order the Page's field names its sections in, and a second count in the
   * browser would be a second opinion about that.
   */
  function describeSection(el) {
    var section = el && el.closest ? el.closest(SECTION) : null;
    if (!section) return null;
    var container = section.parentElement;
    return {
      postId: ownerOf(section),
      section: section.getAttribute("data-shio-section") || "",
      index: Number(section.getAttribute("data-shio-section-index") || "0"),
      of: Number((container && container.getAttribute("data-shio-sections")) || "0"),
      // The owning Page, which is what a reorder writes. It is the page's own post and not the
      // section's: moving a hero rewrites the Page's array, and the hero itself does not change.
      pagePostId: PAGE_POST,
      // SH1038 — where it is on screen, because the overlay is DRAWN BY THE CONSOLE. A stylesheet
      // injected here can outline a section and cannot draw a toolbar over it: the buttons belong
      // to the console's own tree, which is where the writes are, and the console cannot measure
      // the framed document because it is another origin.
      rect: rectOf(section),
      // SH1105 — every section of the same container, in document order, so the console can draw a
      // drop caret in any gap and not only beside the selection. `sections[i]` is position `i`.
      sections: sectionsIn(container).map(rectOf),
      // SH1039 — the post-type names this container says fit in it, so the console knows what may
      // be placed beside the selection. An empty list is "nothing is known to fit here", which is
      // what an absent declaration means; it is never read as "anything". The catalogue itself —
      // each type's title, description and fields — is the server's, on
      // `GET /agent/editable?include=insertable`, because only it knows what this instance has
      // provisioned and a copy of that in the markup would be a second place for it to be wrong.
      accepts: acceptedBy(container)
    };
  }

  /** The types a container declares, split and de-duplicated in the order written (SH1039). */
  function acceptedBy(container) {
    var raw = container ? container.getAttribute("data-shio-accepts") : null;
    if (!raw) return [];
    var names = [];
    raw.trim().split(/\s+/).forEach(function (name) {
      if (name && names.indexOf(name) < 0) names.push(name);
    });
    return names;
  }

  /**
   * A box in the FRAMED viewport's coordinates (SH1038).
   *
   * Viewport and not document, because that is the space the console can map: the iframe occupies
   * exactly one box in the console's layout and the framed viewport <em>is</em> that box, so
   * {@code rect.top} is an offset from the iframe's own top edge and needs no knowledge of how far
   * the page inside has been scrolled. The price is that a scroll moves it, which is what
   * `sh:section-geometry` exists to say.
   */
  function rectOf(el) {
    var box = el.getBoundingClientRect();
    return { top: box.top, left: box.left, width: box.width, height: box.height };
  }

  /**
   * The sections of one container, in document order (SH1038).
   *
   * Direct children only, which is the same rule the server's pass follows — it takes a section's
   * `parentElement` as its container — so the two agree about what an ordered list is without
   * either having to describe it to the other.
   */
  function sectionsIn(container) {
    var found = [];
    if (!container) return found;
    for (var i = 0; i < container.children.length; i++) {
      if (container.children[i].hasAttribute("data-shio-section")) {
        found.push(container.children[i]);
      }
    }
    return found;
  }

  /**
   * Re-derives every position after an optimistic edit — the browser half of the server's pass.
   *
   * Two counts of one thing, and that is not a duplicate implementation for the same reason the
   * server's own is not a copy of the template's: the fact is *document order*, and after a move
   * the document has a new one. Not re-deriving it would leave the next selection reporting the
   * position the section had before the curator moved it, and the write after that would be
   * against an array index that is no longer the one on screen.
   */
  function reindexSections() {
    document.querySelectorAll(CONTAINER).forEach(function (container) {
      var sections = sectionsIn(container);
      sections.forEach(function (section, index) {
        section.setAttribute("data-shio-section-index", String(index));
      });
      container.setAttribute("data-shio-sections", String(sections.length));
    });
  }

  /**
   * Move, remove or duplicate, applied HERE while the console writes the Page's array (SH1038).
   *
   * Optimistic, and the reason is what the alternative costs: the write is a draft merge on the
   * console's session, and the page in the frame is a rendered preview of the published row — so
   * nothing about the write makes this document change, and a curator would press Move and watch
   * the page sit still until they reloaded it. The console reloads the frame when its write is
   * refused, which is the only case where these two can disagree.
   *
   * **Duplicate places the same section twice.** The array holds references, so a duplicated entry
   * is a second placement of one post and not a copy of it: editing either heading edits both.
   * That is what a single replacement of the array can mean, and the console says so rather than
   * letting a curator find out by editing one of them.
   */
  function applySectionOp(data) {
    if (!editing || !data || !selectedSection || !selectedSection.parentElement) return;
    var element = selectedSection;
    var container = element.parentElement;
    var op = data.op;
    if (op === "remove") {
      element.remove();
      selectedSection = null;
    } else if (op === "duplicate") {
      var clone = element.cloneNode(true);
      element.after(clone);
    } else if (op === "move") {
      var others = sectionsIn(container).filter(function (section) {
        return section !== element;
      });
      var to = Math.max(0, Math.min(Number(data.to), others.length));
      element.remove();
      if (to >= others.length) {
        if (others.length) others[others.length - 1].after(element);
        else container.appendChild(element);
      } else {
        container.insertBefore(element, others[to]);
      }
    } else {
      return;
    }
    reindexSections();
    // A clone carries no listeners — `cloneNode` copies attributes and children and nothing that
    // was added with `addEventListener` — so its fields would be outlined and inert.
    attachFields();
    setMode(editing ? "edit" : "preview");
    if (selectedSection) {
      send("sh:component-selected", describeSection(selectedSection));
    } else {
      send("sh:section-geometry", { rect: null });
    }
  }

  /**
   * Where the selected section is now, so the console's overlay follows it (SH1038).
   *
   * Sent on scroll and resize, coalesced onto a frame: a message per scroll event is a message per
   * pixel, and the console can only draw once per frame anyway. `rect: null` says the selection is
   * gone — after a remove, or after the element left the document — and the console takes the
   * overlay down rather than leaving it over whatever moved into that box.
   */
  function sendGeometry() {
    if (!selectedSection || !selectedSection.isConnected) {
      selectedSection = null;
      send("sh:section-geometry", { rect: null });
      return;
    }
    send("sh:section-geometry", {
      rect: rectOf(selectedSection),
      sections: sectionsIn(selectedSection.parentElement).map(rectOf)
    });
  }

  var geometryPending = false;

  function scheduleGeometry() {
    if (!selectedSection || geometryPending) return;
    // No rAF — an engine without it, or a test harness that does not pretend to paint — means the
    // message goes out immediately. Coalescing is an economy, not a correctness rule, and silently
    // dropping the update would leave the overlay behind the page it is drawn over.
    if (typeof window.requestAnimationFrame !== "function") {
      sendGeometry();
      return;
    }
    geometryPending = true;
    window.requestAnimationFrame(function () {
      geometryPending = false;
      sendGeometry();
    });
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
   * SH1175 — what a field holds, read the way `setField` writes it.
   *
   * A plain field is written with `textContent` (SH1037) and was read with `innerHTML`, so it
   * reported markup: a browser serialises a trailing space in a contenteditable as `&nbsp;`, the
   * server stored that as text, the template escaped it, and the next read returned
   * `&amp;nbsp;` — one more entity on every save. U+00A0 is the browser's stand-in for the space
   * the curator typed, so a plain field gives it back as one.
   */
  function valueOf(el) {
    if (el.getAttribute("data-shio-affordance") === "plain") {
      return (el.textContent || "").replace(/ /g, " ");
    }
    return el.innerHTML;
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
        out.push({ prop: prop, type: "text", label: labelOf(prop), value: valueOf(el) });
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
    // SH1041 — and a TEXT field, which this message could not reach. Reverting an edit and
    // replaying one into a reloaded frame are the same act as SH503's write-back — put this value
    // where that field is drawn — and there was no way to say it for the kind of field a curator
    // edits most. Without it a revert clears the console's count and leaves the page showing the
    // text it is no longer going to save, which is worse than having no revert at all.
    document.querySelectorAll(FIELD).forEach(function (el) {
      if (ownerOf(el) !== data.postId || el.getAttribute("data-shio-prop") !== data.prop) return;
      // `plaintext-only` refuses markup on the way IN from a paste, and nothing refuses it on the
      // way in from here — so a plain field is written as text, which is the same decision SH1037
      // made about what that field can hold.
      if (el.getAttribute("data-shio-affordance") === "plain") el.textContent = value;
      else el.innerHTML = value;
      // The baseline moves with it: after a revert, what was there is what is there now, so a
      // second edit of the same field reports the value the curator just went back to.
      el.shioWas = value;
    });
  }

  /**
   * What a field held before this round of typing (SH1041).
   *
   * **Only the page can answer it.** `sh:content-changed` fires after the change, so the console
   * sees the new value and never the old one — it had no way to offer a revert, which is why the
   * pending set could only be cleared all at once. The baseline is taken when a field gains focus
   * and at the moment edit mode is turned on, so the first edit of a session reports the value the
   * page was serving.
   */
  function baselineOf(el) {
    return el.shioWas === undefined ? valueOf(el) : el.shioWas;
  }

  function onFieldFocus(event) {
    var el = event.currentTarget;
    if (el.getAttribute("data-shio-prop")) el.shioWas = valueOf(el);
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
      value: valueOf(el),
      // SH1041 — what to put back. It travels on every message and the console keeps the first,
      // which is what makes an undo mean "before I started" rather than "before the last keystroke".
      was: baselineOf(el),
      label: labelOf(el.getAttribute("data-shio-prop"))
    });
  }

  function onFieldClick(event) {
    if (!editing) return;
    event.preventDefault();
    event.stopPropagation();
    // SH1038 — one selection at a time. A field and the section holding it are two objects, and a
    // section left selected here would keep the console's overlay and its move controls over a
    // section the curator has stopped talking about.
    selectedSection = null;
    send("sh:element-selected", describe(event.currentTarget));
  }

  /**
   * A click on a section but not on one of its fields (SH1038).
   *
   * Delegated on the document and running AFTER the field handler, which stops propagation: so a
   * click on a headline selects the headline and a click on the space around it selects the hero.
   * That ordering is the whole interaction — a curator reaches for the section by clicking the part
   * of it that is not a field, which is how every visual editor behaves and needs no extra chrome.
   */
  function onSectionClick(event) {
    if (!editing || event.defaultPrevented) return;
    // A link the click interception owns is a navigation, not a selection (SH336/SH1036).
    var anchor = event.target && event.target.closest ? event.target.closest("a[href]") : null;
    if (anchor) return;
    var section = event.target.closest(SECTION);
    if (!section) return;
    event.preventDefault();
    selectedSection = section;
    send("sh:component-selected", describeSection(section));
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

  /**
   * Protocol version two: what this bridge IS, and what it can do (SH1037).
   *
   * v1's ready was a post id and a URL, so the console's only question was whether anything
   * answered. That is the wrong question for a protocol with more than one implementation: this
   * script and `@viglet/shio-editor-cors` are two producers (one for the markup the renderer emits,
   * one for a front end writing its own `data-sh-*`), and the published one implements less. The
   * console was therefore offering affordances the page on the other side might not honour, and
   * finding out meant a click that did nothing.
   *
   * So the capabilities are ANNOUNCED, and derived from what is actually true of this page rather
   * than hard-coded: `navigate` only when the server handed over a URL index, because without one
   * the click interception is off (SH336). A console that reads no `capabilities` at all — a v1
   * bridge, or an external one — must assume ALL of them, for the same reason a missing field kind
   * means rich: taking away what works today is worse than offering something that turns out to be
   * a no-op.
   */
  var PROTOCOL = 2;

  function capabilities() {
    var caps = ["select", "edit-inline", "field-set", "field-kinds"];
    // SH1038 — claimed only when the page HAS sections. A layout that composes none is not a page
    // where a curator can select one, and a console told otherwise would offer a component panel
    // over a page that has no component to put in it.
    if (document.querySelector(SECTION)) caps.push("sections");
    // SH336's interception is off without the index, so claiming it would be a lie the console acts
    // on: it would stop offering its own navigation on the belief that the page handles clicks.
    if (PREVIEW_PATHS) caps.push("navigate");
    return caps;
  }

  function ready() {
    return {
      protocol: PROTOCOL,
      capabilities: capabilities(),
      postId: PAGE_POST,
      // SH1037 — the page's type on ready as well as on every selection. The console needs it to
      // fetch a field schema, and waiting for a click to learn it meant the panel could not draw a
      // declared widget until the curator had already selected something.
      postType: PAGE_TYPE,
      url: window.location.pathname
    };
  }

  /**
   * Binds every annotated element, and binds each one once (SH1038).
   *
   * Re-run after a duplicate, because `cloneNode` copies attributes and children and nothing that
   * was added with `addEventListener` — so the copy's fields would be outlined, editable-looking
   * and inert. The mark is a property rather than an attribute on purpose: an attribute would be a
   * term of the SH300 rendered-DOM contract, and this is not something a reader of the page has
   * any business knowing.
   */
  function attachFields() {
    document.querySelectorAll(FIELD + "," + ATTR_FIELD).forEach(function (el) {
      if (el.shioBound) return;
      el.shioBound = true;
      el.addEventListener("input", onFieldInput);
      el.addEventListener("click", onFieldClick);
      // SH1041 — the baseline a revert goes back to, taken before the typing rather than after it.
      el.addEventListener("focus", onFieldFocus);
    });
  }

  function attach() {
    attachFields();
    // Delegated, and on the document: the whole point is links that do not exist yet.
    document.addEventListener("click", onDocumentClick);
    // SH1038 — after the link handler, so a navigation is never turned into a selection, and after
    // the per-field one, which stops propagation: clicking a headline selects the headline and
    // clicking the space around it selects the section that holds it.
    document.addEventListener("click", onSectionClick);
    // SH1038 — the overlay is the console's, so it has to be told when the box it is drawn over
    // moves. `capture` on scroll, because a scroll inside an element does not bubble.
    window.addEventListener("scroll", scheduleGeometry, true);
    window.addEventListener("resize", scheduleGeometry);
    injectStyles();
    injectToolbar();
    setMode("edit");
    send("sh:ready", ready());
  }

  /**
   * The inline toolbar for a rich-text field, and only for one (SH1037).
   *
   * A `plaintext-only` field must not have it — the commands it offers produce exactly the markup
   * that field cannot store, which is the defect this task is about, wearing a button. A `panel`
   * field has no caret at all. So it appears for `affordance="rich"` and nothing else.
   *
   * RESTRICTED, and the restriction is the point rather than a first cut: bold, italic, link and
   * "remove formatting" are what a curator reaches for in a page's prose, and they are also what
   * SH956's allow-list keeps. A toolbar offering headings, colours and fonts would be offering
   * writes the write boundary then strips, which is worse than not offering them — the curator sees
   * the change, saves, and the change is gone with no explanation.
   *
   * `document.execCommand` is deprecated and is what a `contenteditable` still has: the modern
   * replacement for styling a selection does not exist. It is used for exactly the four commands
   * above and no more, which is the scope where its behaviour is consistent across engines.
   */
  var TOOLBAR_ID = "shio-editor-toolbar";

  function injectToolbar() {
    if (document.getElementById(TOOLBAR_ID)) return;
    var bar = document.createElement("div");
    bar.id = TOOLBAR_ID;
    bar.setAttribute("data-shio-editor", "true");
    bar.setAttribute("role", "toolbar");
    bar.setAttribute("aria-label", "Formatting");
    bar.setAttribute("hidden", "hidden");
    [
      ["bold", "B"],
      ["italic", "I"],
      ["createLink", "link"],
      ["removeFormat", "clear"]
    ].forEach(function (pair) {
      var button = document.createElement("button");
      button.type = "button";
      button.setAttribute("data-shio-command", pair[0]);
      button.textContent = pair[1];
      // `mousedown` and not `click`: a click moves focus out of the contenteditable first, and the
      // selection the command acts on is gone by the time the handler runs.
      button.addEventListener("mousedown", function (event) {
        event.preventDefault();
        runCommand(pair[0]);
      });
      bar.appendChild(button);
    });
    document.body.appendChild(bar);
    // Shown against the field being edited, so it is never over a plain or panel one.
    document.addEventListener("selectionchange", positionToolbar);
  }

  function richFieldOfSelection() {
    var selection = window.getSelection ? window.getSelection() : null;
    if (!selection || !selection.rangeCount) return null;
    var node = selection.getRangeAt(0).startContainer;
    var element = node && node.nodeType === 1 ? node : node && node.parentElement;
    if (!element || !element.closest) return null;
    var field = element.closest("[data-shio-prop]");
    return field && field.getAttribute("data-shio-affordance") === "rich"
      && field.isContentEditable ? field : null;
  }

  function positionToolbar() {
    var bar = document.getElementById(TOOLBAR_ID);
    if (!bar) return;
    var field = editing ? richFieldOfSelection() : null;
    if (!field) {
      bar.setAttribute("hidden", "hidden");
      return;
    }
    var box = field.getBoundingClientRect();
    bar.removeAttribute("hidden");
    bar.style.top = (box.top + (window.scrollY || 0) - 34) + "px";
    bar.style.left = (box.left + (window.scrollX || 0)) + "px";
  }

  function runCommand(command) {
    var field = richFieldOfSelection();
    if (!field) return;
    if (command === "createLink") {
      var href = window.prompt("Link URL");
      if (!href) return;
      document.execCommand("createLink", false, href);
    } else {
      document.execCommand(command, false, null);
    }
    // The command edited the DOM without an `input` event on every engine, so the change is
    // reported here too — a formatting change nobody sent is one the curator saves and loses.
    onFieldInput({ currentTarget: field });
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
      // SH1037 — a field the caret cannot express: outlined like the others so the curator can see
      // it is a field, with a pointer rather than a caret, because it is edited in the panel.
      '[data-shio-editing="true"] [data-shio-prop][data-shio-affordance="panel"]{cursor:pointer}' +
      // SH1037 — the rich-text toolbar. Absolutely positioned against the document so it cannot be
      // clipped by a field's own overflow, and above the page's own stacking: a formatting bar
      // behind a sticky header is one nobody can press.
      // Scoped like everything else, which is what keeps preview byte-for-byte the page a visitor
      // sees: in preview mode none of these apply and the toolbar falls back to the UA's own
      // `[hidden]` rule, so it contributes nothing rather than being hidden by a rule of ours.
      '[data-shio-editing="true"] #shio-editor-toolbar{position:absolute;z-index:2147483000;' +
      'display:flex;gap:2px;background:#111827;border-radius:4px;padding:2px;' +
      'font:12px/1 system-ui,sans-serif}' +
      '[data-shio-editing="true"] #shio-editor-toolbar[hidden]{display:none}' +
      '[data-shio-editing="true"] #shio-editor-toolbar button{background:transparent;border:0;' +
      'color:#f9fafb;cursor:pointer;padding:4px 7px;border-radius:3px;font:inherit}' +
      '[data-shio-editing="true"] #shio-editor-toolbar button:hover{background:#374151}' +
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
    // SH1038 — the only inbound message that changes the SHAPE of the page rather than a value in
    // it, which is why it acts on the curator's own selection and on nothing the sender names.
    if (msg.type === "sh:section-op") {
      applySectionOp(msg.data);
    }
  });

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", attach);
  } else {
    attach();
  }
})();
