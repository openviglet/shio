/**
 * Shio Universal Editor Bridge
 *
 * This library runs inside site pages loaded in the Shio Universal Editor iframe.
 * It detects data-sh-* attributes on DOM elements, enables inline editing,
 * and communicates with the parent editor shell via postMessage.
 *
 * Data Attributes:
 *   data-sh-resource="urn:shio:<postId>"     - Identifies the backend resource (post)
 *   data-sh-type="text|richtext|media|container|component" - Edit behavior
 *   data-sh-prop="<attrName>"                - Property name on the resource
 *   data-sh-label="<label>"                  - Human-readable label for UI
 *   data-sh-model="<postTypeName>"           - Post type reference
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
(function () {
  "use strict";

  // ── State ──────────────────────────────────────────────────────────────
  var MODE_PREVIEW = "preview";
  var MODE_EDIT = "edit";

  var state = {
    mode: MODE_PREVIEW,
    selectedElement: null,
    activeEditor: null,
    changedContent: {},
  };

  // ── Utilities ──────────────────────────────────────────────────────────

  function parseResource(urn) {
    if (!urn) return null;
    var parts = urn.replace("urn:shio:", "");
    return parts || null;
  }

  function findEditableParent(el) {
    var current = el;
    while (current && current !== document.body) {
      if (current.hasAttribute && current.hasAttribute("data-sh-resource")) {
        return current;
      }
      current = current.parentElement;
    }
    return null;
  }

  function getElementInfo(el) {
    var resource = el.getAttribute("data-sh-resource");
    var type = el.getAttribute("data-sh-type") || "text";
    var prop = el.getAttribute("data-sh-prop");
    var label = el.getAttribute("data-sh-label") || prop || "";
    var model = el.getAttribute("data-sh-model") || "";

    // Walk up for inherited resource
    if (!resource) {
      var parent = findEditableParent(el);
      if (parent) resource = parent.getAttribute("data-sh-resource");
    }

    return {
      resource: resource,
      postId: parseResource(resource),
      type: type,
      prop: prop,
      label: label,
      model: model,
      selector: buildSelector(el),
    };
  }

  function buildSelector(el) {
    if (el.id) return "#" + el.id;
    var path = [];
    while (el && el !== document.body) {
      var tag = el.tagName.toLowerCase();
      if (el.id) {
        path.unshift("#" + el.id);
        break;
      }
      var siblings = el.parentElement
        ? Array.from(el.parentElement.children).filter(function (c) {
            return c.tagName === el.tagName;
          })
        : [];
      if (siblings.length > 1) {
        var idx = siblings.indexOf(el) + 1;
        tag += ":nth-of-type(" + idx + ")";
      }
      path.unshift(tag);
      el = el.parentElement;
    }
    return path.join(" > ");
  }

  // ── Communication with Parent (Editor Shell) ──────────────────────────

  function sendToParent(type, data) {
    if (window.parent && window.parent !== window) {
      window.parent.postMessage({ type: type, data: data }, "*");
    }
  }

  function onParentMessage(event) {
    if (!event.data || !event.data.type) return;

    var msg = event.data;
    switch (msg.type) {
      case "sh:mode-change":
        setMode(msg.data && msg.data.mode ? msg.data.mode : MODE_PREVIEW);
        break;
      case "sh:content-patch":
        handleContentPatch(msg.data);
        break;
      case "sh:request-content":
        handleRequestContent();
        break;
      case "sh:highlight-element":
        highlightElement(msg.data);
        break;
    }
  }

  // ── Mode Management ────────────────────────────────────────────────────

  function setMode(mode) {
    state.mode = mode;
    var html = document.documentElement;

    html.classList.remove("sh-edit-mode", "sh-preview-mode");
    html.classList.add(mode === MODE_EDIT ? "sh-edit-mode" : "sh-preview-mode");

    if (mode === MODE_PREVIEW) {
      closeActiveEditor();
      deselectAll();
    }

    sendToParent("sh:mode-changed", { mode: mode });
  }

  // ── Selection & Click Handling ─────────────────────────────────────────

  function handleClick(e) {
    if (state.mode !== MODE_EDIT) return;

    var target = e.target;
    var editable = findEditableParent(target);

    // Check if the target itself has data-sh-type
    if (
      target.hasAttribute &&
      target.hasAttribute("data-sh-type") &&
      target.hasAttribute("data-sh-prop")
    ) {
      editable = target;
    }

    if (!editable) return;

    e.preventDefault();
    e.stopPropagation();

    selectElement(editable);

    var info = getElementInfo(editable);
    sendToParent("sh:element-selected", info);

    // Start inline editing for text/richtext
    if (
      info.type === "text" ||
      info.type === "richtext"
    ) {
      startInlineEdit(editable, info);
    } else if (info.type === "media") {
      sendToParent("sh:media-edit-request", info);
    }
  }

  function selectElement(el) {
    deselectAll();
    state.selectedElement = el;
    el.classList.add("sh-selected");
  }

  function deselectAll() {
    if (state.selectedElement) {
      state.selectedElement.classList.remove("sh-selected");
      state.selectedElement = null;
    }
    var selected = document.querySelectorAll(".sh-selected");
    for (var i = 0; i < selected.length; i++) {
      selected[i].classList.remove("sh-selected");
    }
  }

  // ── Inline Text Editing ────────────────────────────────────────────────

  function startInlineEdit(el, info) {
    closeActiveEditor();

    if (info.type === "text") {
      startPlainTextEdit(el, info);
    } else if (info.type === "richtext") {
      startRichTextEdit(el, info);
    }
  }

  function startPlainTextEdit(el, info) {
    var originalContent = el.textContent;
    el.setAttribute("contenteditable", "true");
    el.classList.add("sh-editing");
    el.focus();

    // Select all text
    var range = document.createRange();
    range.selectNodeContents(el);
    var sel = window.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);

    state.activeEditor = {
      element: el,
      info: info,
      originalContent: originalContent,
      type: "text",
    };

    function onBlur() {
      commitEdit(el, info, el.textContent, originalContent);
      el.removeEventListener("blur", onBlur);
      el.removeEventListener("keydown", onKeydown);
    }

    function onKeydown(e) {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        el.blur();
      } else if (e.key === "Escape") {
        el.textContent = originalContent;
        el.blur();
      }
    }

    el.addEventListener("blur", onBlur);
    el.addEventListener("keydown", onKeydown);
  }

  function startRichTextEdit(el, info) {
    var originalContent = el.innerHTML;
    el.setAttribute("contenteditable", "true");
    el.classList.add("sh-editing", "sh-richtext-editing");
    el.focus();

    state.activeEditor = {
      element: el,
      info: info,
      originalContent: originalContent,
      type: "richtext",
    };

    // Create floating toolbar
    var toolbar = createRichTextToolbar(el);
    document.body.appendChild(toolbar);
    positionToolbar(toolbar, el);

    state.activeEditor.toolbar = toolbar;

    function onBlurDelayed() {
      setTimeout(function () {
        // Don't close if focus moved to the toolbar
        if (
          toolbar.contains(document.activeElement) ||
          el.contains(document.activeElement)
        ) {
          return;
        }
        commitEdit(el, info, el.innerHTML, originalContent);
        removeToolbar();
        el.removeEventListener("blur", onBlurDelayed);
      }, 200);
    }

    function removeToolbar() {
      if (toolbar.parentNode) toolbar.parentNode.removeChild(toolbar);
    }

    el.addEventListener("blur", onBlurDelayed);
  }

  function createRichTextToolbar(targetEl) {
    var toolbar = document.createElement("div");
    toolbar.className = "sh-rte-toolbar";
    toolbar.setAttribute("role", "toolbar");

    var buttons = [
      { cmd: "bold", icon: "B", title: "Bold" },
      { cmd: "italic", icon: "I", title: "Italic" },
      { cmd: "underline", icon: "U", title: "Underline" },
      { cmd: "insertUnorderedList", icon: "\u2022", title: "Bullet List" },
      { cmd: "insertOrderedList", icon: "1.", title: "Numbered List" },
      { cmd: "createLink", icon: "\uD83D\uDD17", title: "Insert Link" },
      { cmd: "removeFormat", icon: "\u2718", title: "Clear Formatting" },
    ];

    buttons.forEach(function (btn) {
      var button = document.createElement("button");
      button.type = "button";
      button.title = btn.title;
      button.textContent = btn.icon;
      button.className = "sh-rte-btn";
      button.addEventListener("mousedown", function (e) {
        e.preventDefault();
        if (btn.cmd === "createLink") {
          var url = prompt("Enter URL:");
          if (url) document.execCommand(btn.cmd, false, url);
        } else {
          document.execCommand(btn.cmd, false, null);
        }
        targetEl.focus();
      });
      toolbar.appendChild(button);
    });

    return toolbar;
  }

  function positionToolbar(toolbar, el) {
    var rect = el.getBoundingClientRect();
    toolbar.style.position = "fixed";
    toolbar.style.top = Math.max(0, rect.top - 44) + "px";
    toolbar.style.left = rect.left + "px";
    toolbar.style.zIndex = "999999";
  }

  function commitEdit(el, info, newContent, originalContent) {
    el.removeAttribute("contenteditable");
    el.classList.remove("sh-editing", "sh-richtext-editing");

    if (newContent !== originalContent) {
      var changeKey = info.postId + ":" + info.prop;
      state.changedContent[changeKey] = {
        postId: info.postId,
        prop: info.prop,
        type: info.type,
        value: newContent,
        label: info.label,
      };

      sendToParent("sh:content-changed", {
        postId: info.postId,
        prop: info.prop,
        type: info.type,
        value: newContent,
        label: info.label,
        selector: info.selector,
      });
    }

    state.activeEditor = null;
  }

  function closeActiveEditor() {
    if (state.activeEditor) {
      var editor = state.activeEditor;
      editor.element.removeAttribute("contenteditable");
      editor.element.classList.remove("sh-editing", "sh-richtext-editing");
      if (editor.toolbar && editor.toolbar.parentNode) {
        editor.toolbar.parentNode.removeChild(editor.toolbar);
      }
      state.activeEditor = null;
    }
  }

  // ── Content Patch (from parent) ────────────────────────────────────────

  function handleContentPatch(data) {
    if (!data || !data.selector) return;

    var el = document.querySelector(data.selector);
    if (!el) return;

    if (data.type === "media" && el.tagName === "IMG") {
      el.src = data.value;
    } else if (data.type === "richtext") {
      el.innerHTML = data.value;
    } else {
      el.textContent = data.value;
    }
  }

  function handleRequestContent() {
    var editables = document.querySelectorAll("[data-sh-resource]");
    var content = [];

    for (var i = 0; i < editables.length; i++) {
      var el = editables[i];
      content.push(getElementInfo(el));
    }

    sendToParent("sh:content-map", { elements: content });
  }

  function highlightElement(data) {
    if (!data || !data.selector) return;
    deselectAll();
    var el = document.querySelector(data.selector);
    if (el) {
      selectElement(el);
      el.scrollIntoView({ behavior: "smooth", block: "center" });
    }
  }

  // ── CSS Injection ──────────────────────────────────────────────────────

  function injectStyles() {
    var style = document.createElement("style");
    style.id = "sh-universal-editor-styles";
    style.textContent = [
      /* Edit mode: show outlines on all editable elements */
      "html.sh-edit-mode [data-sh-resource] {",
      "  outline: 2px dashed rgba(249, 115, 22, 0.4);",
      "  outline-offset: 4px;",
      "  cursor: pointer;",
      "  position: relative;",
      "  transition: outline 0.2s ease, outline-offset 0.2s ease;",
      "}",
      "html.sh-edit-mode [data-sh-resource]:hover {",
      "  outline: 2px solid #f97316;",
      "  outline-offset: 4px;",
      "}",
      /* Label tooltip on hover */
      "html.sh-edit-mode [data-sh-label]:hover::before {",
      "  content: attr(data-sh-label);",
      "  position: absolute;",
      "  top: -28px;",
      "  left: 0;",
      "  background: #f97316;",
      "  color: white;",
      "  padding: 3px 8px;",
      "  font-size: 11px;",
      "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;",
      "  border-radius: 4px;",
      "  white-space: nowrap;",
      "  z-index: 100000;",
      "  pointer-events: none;",
      "  line-height: 1.4;",
      "  font-weight: 500;",
      "}",
      /* Selected element */
      "html.sh-edit-mode .sh-selected {",
      "  outline: 2px solid #f97316 !important;",
      "  outline-offset: 4px;",
      "  box-shadow: 0 0 0 4px rgba(249, 115, 22, 0.15);",
      "}",
      /* Editing state */
      ".sh-editing {",
      "  outline: 2px solid #2563eb !important;",
      "  outline-offset: 2px;",
      "  box-shadow: 0 0 0 4px rgba(37, 99, 235, 0.15) !important;",
      "  min-height: 1em;",
      "}",
      /* Rich text toolbar */
      ".sh-rte-toolbar {",
      "  display: flex;",
      "  gap: 2px;",
      "  background: #1e293b;",
      "  border-radius: 8px;",
      "  padding: 4px;",
      "  box-shadow: 0 4px 16px rgba(0,0,0,0.2);",
      "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;",
      "}",
      ".sh-rte-btn {",
      "  background: transparent;",
      "  border: none;",
      "  color: #e2e8f0;",
      "  width: 32px;",
      "  height: 32px;",
      "  border-radius: 4px;",
      "  cursor: pointer;",
      "  font-size: 14px;",
      "  font-weight: 600;",
      "  display: flex;",
      "  align-items: center;",
      "  justify-content: center;",
      "  transition: background 0.15s;",
      "}",
      ".sh-rte-btn:hover {",
      "  background: rgba(255,255,255,0.15);",
      "}",
      /* Container type elements */
      "html.sh-edit-mode [data-sh-type='container'] {",
      "  outline-style: dashed;",
      "  outline-color: rgba(139, 92, 246, 0.4);",
      "}",
      "html.sh-edit-mode [data-sh-type='container']:hover {",
      "  outline-color: #8b5cf6;",
      "}",
      /* Component type elements */
      "html.sh-edit-mode [data-sh-type='component'] {",
      "  outline-style: dashed;",
      "  outline-color: rgba(16, 185, 129, 0.4);",
      "}",
      "html.sh-edit-mode [data-sh-type='component']:hover {",
      "  outline-color: #10b981;",
      "}",
      /* Preview mode: remove all outlines */
      "html.sh-preview-mode [data-sh-resource] {",
      "  outline: none !important;",
      "  cursor: default;",
      "}",
    ].join("\n");

    document.head.appendChild(style);
  }

  // ── Initialization ─────────────────────────────────────────────────────

  function init() {
    // Only initialize if we're inside an iframe (loaded by the editor shell)
    if (window === window.parent) {
      // Not in an iframe — could be standalone page, skip editor functionality
      return;
    }

    injectStyles();

    // Listen for messages from the parent editor shell
    window.addEventListener("message", onParentMessage);

    // Listen for clicks in edit mode
    document.addEventListener("click", handleClick, true);

    // Notify the parent that the page is ready
    sendToParent("sh:ready", {
      url: window.location.href,
      title: document.title,
      editableCount: document.querySelectorAll("[data-sh-resource]").length,
    });
  }

  // Run init when DOM is ready
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
