/*!
 * @viglet/shio-editor-cors v2026.2.0
 * Viglet Shio Universal Editor Bridge
 *
 * Include this script in any page to enable Shio Universal Editor support.
 * Requires: <meta name="urn:shio:system:connection" content="shio:http://your-shio-server">
 *
 * Copyright (C) 2016-2026 Viglet Team
 * Licensed under the Apache License, Version 2.0.
 */

// ── Types ─────────────────────────────────────────────────────────────────

interface EditorState {
  mode: "preview" | "edit";
  selectedElement: HTMLElement | null;
  activeEditor: ActiveEditor | null;
  changedContent: Record<string, ContentChange>;
  connectionUrl: string | null;
  siteName: string | null;
  folderPath: string | null;
  /**
   * The editor shell's origin, and the bridge is inert without it (SH350).
   *
   * It has to be *told*: a framed page cannot work out who framed it -
   * `document.referrer` is empty on some navigations and `window.parent.location`
   * throws cross-origin - which is why the server-injected bridge is handed
   * `data-shio-editor-origin` at render time. This package is included by the front
   * end itself, so it comes from the same meta tags the connection does.
   */
  parentOrigin: string | null;
}

interface ActiveEditor {
  element: HTMLElement;
  info: ElementInfo;
  originalContent: string;
  type: "text" | "richtext";
  toolbar?: HTMLElement;
}

interface ElementInfo {
  resource: string | null;
  postId: string | null;
  type: string;
  prop: string | null;
  label: string;
  model: string;
  selector: string;
}

interface DomFieldInfo {
  prop: string;
  type: string;
  label: string;
  value: string;
  selector: string;
}

interface ContentChange {
  postId: string | null;
  prop: string | null;
  type: string;
  value: string;
  label: string;
}

interface EditorMessage {
  type: string;
  data?: Record<string, unknown>;
}

// ── State ─────────────────────────────────────────────────────────────────

const MODE_PREVIEW = "preview" as const;
const MODE_EDIT = "edit" as const;

/**
 * Where the editor shell's origin comes from (SH350).
 *
 * A meta tag rather than a script attribute, because this package is bundled by the front end
 * rather than injected as a tag - and because that is where this bridge already reads its
 * configuration (`urn:shio:system:connection`, `...:site`, `...:path`).
 */
const EDITOR_ORIGIN_META = "urn:shio:system:editor-origin";

const state: EditorState = {
  mode: MODE_PREVIEW,
  selectedElement: null,
  activeEditor: null,
  changedContent: {},
  connectionUrl: null,
  siteName: null,
  folderPath: null,
  parentOrigin: null,
};

// ── Connection ────────────────────────────────────────────────────────────

function readConnectionMeta(): string | null {
  const meta = document.querySelector(
    'meta[name="urn:shio:system:connection"]'
  );
  if (!meta) return null;
  const content = meta.getAttribute("content");
  if (!content) return null;
  return content.replace(/^shio:/, "");
}

function readMeta(name: string): string | null {
  const meta = document.querySelector(`meta[name="${name}"]`);
  if (!meta) return null;
  return meta.getAttribute("content") || null;
}

// ── Utilities ─────────────────────────────────────────────────────────────

function parseResource(urn: string | null): string | null {
  if (!urn) return null;
  return urn.replace("urn:shio:", "") || null;
}

function findEditableParent(el: HTMLElement | null): HTMLElement | null {
  let current = el;
  while (current && current !== document.body) {
    if (current.hasAttribute("data-sh-resource")) {
      return current;
    }
    current = current.parentElement;
  }
  return null;
}

function getElementInfo(el: HTMLElement): ElementInfo {
  let resource = el.getAttribute("data-sh-resource");
  const type = el.getAttribute("data-sh-type") || "text";
  const prop = el.getAttribute("data-sh-prop");
  const label = el.getAttribute("data-sh-label") || prop || "";
  let model = el.getAttribute("data-sh-model") || "";

  if (!resource || !model) {
    const parent = findEditableParent(el);
    if (parent) {
      if (!resource) resource = parent.getAttribute("data-sh-resource");
      if (!model) model = parent.getAttribute("data-sh-model") || "";
    }
  }

  return {
    resource,
    postId: parseResource(resource),
    type,
    prop,
    label,
    model,
    selector: buildSelector(el),
  };
}

function buildSelector(el: HTMLElement): string {
  if (el.id) return "#" + el.id;
  const path: string[] = [];
  let current: HTMLElement | null = el;
  while (current && current !== document.body) {
    let tag = current.tagName.toLowerCase();
    if (current.id) {
      path.unshift("#" + current.id);
      break;
    }
    const siblings = current.parentElement
      ? Array.from(current.parentElement.children).filter(
          (c) => c.tagName === current!.tagName
        )
      : [];
    if (siblings.length > 1) {
      const idx = siblings.indexOf(current) + 1;
      tag += ":nth-of-type(" + idx + ")";
    }
    path.unshift(tag);
    current = current.parentElement;
  }
  return path.join(" > ");
}

// ── Component Field Collection ────────────────────────────────────────────

function getComponentFields(el: HTMLElement): DomFieldInfo[] {
  const fields: DomFieldInfo[] = [];
  el.querySelectorAll("[data-sh-prop]").forEach((child) => {
    const htmlChild = child as HTMLElement;
    const type = htmlChild.getAttribute("data-sh-type") || "text";
    let value: string;
    if (type === "media" && htmlChild.tagName === "IMG") {
      value = (htmlChild as HTMLImageElement).src;
    } else if (type === "richtext") {
      value = htmlChild.innerHTML;
    } else {
      value = htmlChild.textContent || "";
    }
    fields.push({
      prop: htmlChild.getAttribute("data-sh-prop") || "",
      type,
      label: htmlChild.getAttribute("data-sh-label") || htmlChild.getAttribute("data-sh-prop") || "",
      value,
      selector: buildSelector(htmlChild),
    });
  });
  return fields;
}

// ── Communication with Parent (Editor Shell) ──────────────────────────────

function sendToParent(type: string, data: unknown): void {
  if (window.parent && window.parent !== window) {
    // SH350 - never "*": this message carries page content, and "*" hands it to whatever
    // origin framed the page. No origin means no conversation (see init).
    if (!state.parentOrigin) return;
    window.parent.postMessage({ type, data }, state.parentOrigin);
  }
}

function onParentMessage(event: MessageEvent): void {
  // SH350 - the sender's origin, checked before anything is read. Without this, any page that
  // frames this one could ask for the content map (`sh:request-content`) and rewrite the DOM
  // (`sh:content-patch`); `event.data.type` was the only thing this looked at.
  if (!state.parentOrigin || event.origin !== state.parentOrigin) return;
  if (!event.data || !event.data.type) return;

  const msg = event.data as EditorMessage;
  switch (msg.type) {
    case "sh:mode-change":
      setMode(
        msg.data && msg.data.mode
          ? (msg.data.mode as "edit" | "preview")
          : MODE_PREVIEW
      );
      break;
    case "sh:content-patch":
      handleContentPatch(msg.data as Record<string, string>);
      break;
    case "sh:request-content":
      handleRequestContent();
      break;
    case "sh:highlight-element":
      highlightElement(msg.data as Record<string, string>);
      break;
  }
}

// ── Mode Management ──────────────────────────────────────────────────────

function setMode(mode: "edit" | "preview"): void {
  state.mode = mode;
  const html = document.documentElement;

  html.classList.remove("sh-edit-mode", "sh-preview-mode");
  html.classList.add(mode === MODE_EDIT ? "sh-edit-mode" : "sh-preview-mode");

  if (mode === MODE_PREVIEW) {
    closeActiveEditor();
    deselectAll();
  }

  sendToParent("sh:mode-changed", { mode });
}

// ── Selection & Click Handling ──────────────────────────────────────────

function handleClick(e: MouseEvent): void {
  if (state.mode !== MODE_EDIT) return;

  const target = e.target as HTMLElement;
  let editable = findEditableParent(target);

  if (
    target.hasAttribute("data-sh-type") &&
    target.hasAttribute("data-sh-prop")
  ) {
    editable = target;
  }

  if (!editable) return;

  e.preventDefault();
  e.stopPropagation();

  selectElement(editable);

  const info = getElementInfo(editable);

  // Collect all fields from the resource root element
  const resourceRoot = editable.hasAttribute("data-sh-resource")
    ? editable
    : findEditableParent(editable);
  const fields = resourceRoot ? getComponentFields(resourceRoot) : [];

  sendToParent("sh:element-selected", {
    ...info,
    fields,
    siteName: state.siteName,
    folderPath: state.folderPath,
  } as unknown as Record<string, unknown>);

  if (info.type === "text" || info.type === "richtext") {
    startInlineEdit(editable, info);
  } else if (info.type === "media") {
    sendToParent("sh:media-edit-request", info as unknown as Record<string, unknown>);
  }
}

function selectElement(el: HTMLElement): void {
  deselectAll();
  state.selectedElement = el;
  el.classList.add("sh-selected");
}

function deselectAll(): void {
  if (state.selectedElement) {
    state.selectedElement.classList.remove("sh-selected");
    state.selectedElement = null;
  }
  document.querySelectorAll(".sh-selected").forEach((el) => {
    el.classList.remove("sh-selected");
  });
}

// ── Inline Text Editing ─────────────────────────────────────────────────

function startInlineEdit(el: HTMLElement, info: ElementInfo): void {
  closeActiveEditor();

  if (info.type === "text") {
    startPlainTextEdit(el, info);
  } else if (info.type === "richtext") {
    startRichTextEdit(el, info);
  }
}

function startPlainTextEdit(el: HTMLElement, info: ElementInfo): void {
  const originalContent = el.textContent || "";
  el.setAttribute("contenteditable", "true");
  el.classList.add("sh-editing");
  el.focus();

  const range = document.createRange();
  range.selectNodeContents(el);
  const sel = window.getSelection();
  if (sel) {
    sel.removeAllRanges();
    sel.addRange(range);
  }

  state.activeEditor = {
    element: el,
    info,
    originalContent,
    type: "text",
  };

  function onBlur(): void {
    commitEdit(el, info, el.textContent || "", originalContent);
    el.removeEventListener("blur", onBlur);
    el.removeEventListener("keydown", onKeydown);
  }

  function onKeydown(e: KeyboardEvent): void {
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

function startRichTextEdit(el: HTMLElement, info: ElementInfo): void {
  const originalContent = el.innerHTML;
  el.setAttribute("contenteditable", "true");
  el.classList.add("sh-editing", "sh-richtext-editing");
  el.focus();

  state.activeEditor = {
    element: el,
    info,
    originalContent,
    type: "richtext",
  };

  const toolbar = createRichTextToolbar(el);
  document.body.appendChild(toolbar);
  positionToolbar(toolbar, el);

  state.activeEditor.toolbar = toolbar;

  function onBlurDelayed(): void {
    setTimeout(() => {
      if (
        toolbar.contains(document.activeElement) ||
        el.contains(document.activeElement)
      ) {
        return;
      }
      commitEdit(el, info, el.innerHTML, originalContent);
      if (toolbar.parentNode) toolbar.parentNode.removeChild(toolbar);
      el.removeEventListener("blur", onBlurDelayed);
    }, 200);
  }

  el.addEventListener("blur", onBlurDelayed);
}

function createRichTextToolbar(targetEl: HTMLElement): HTMLElement {
  const toolbar = document.createElement("div");
  toolbar.className = "sh-rte-toolbar";
  toolbar.setAttribute("role", "toolbar");

  const buttons = [
    { cmd: "bold", icon: "B", title: "Bold" },
    { cmd: "italic", icon: "I", title: "Italic" },
    { cmd: "underline", icon: "U", title: "Underline" },
    { cmd: "insertUnorderedList", icon: "\u2022", title: "Bullet List" },
    { cmd: "insertOrderedList", icon: "1.", title: "Numbered List" },
    { cmd: "createLink", icon: "\uD83D\uDD17", title: "Insert Link" },
    { cmd: "removeFormat", icon: "\u2718", title: "Clear Formatting" },
  ];

  buttons.forEach((btn) => {
    const button = document.createElement("button");
    button.type = "button";
    button.title = btn.title;
    button.textContent = btn.icon;
    button.className = "sh-rte-btn";
    button.addEventListener("mousedown", (e) => {
      e.preventDefault();
      if (btn.cmd === "createLink") {
        const url = prompt("Enter URL:");
        if (url) document.execCommand(btn.cmd, false, url);
      } else {
        document.execCommand(btn.cmd, false, undefined);
      }
      targetEl.focus();
    });
    toolbar.appendChild(button);
  });

  return toolbar;
}

function positionToolbar(toolbar: HTMLElement, el: HTMLElement): void {
  const rect = el.getBoundingClientRect();
  toolbar.style.position = "fixed";
  toolbar.style.top = Math.max(0, rect.top - 44) + "px";
  toolbar.style.left = rect.left + "px";
  toolbar.style.zIndex = "999999";
}

function commitEdit(
  el: HTMLElement,
  info: ElementInfo,
  newContent: string,
  originalContent: string
): void {
  el.removeAttribute("contenteditable");
  el.classList.remove("sh-editing", "sh-richtext-editing");

  if (newContent !== originalContent) {
    const changeKey = info.postId + ":" + info.prop;
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

function closeActiveEditor(): void {
  if (state.activeEditor) {
    const editor = state.activeEditor;
    editor.element.removeAttribute("contenteditable");
    editor.element.classList.remove("sh-editing", "sh-richtext-editing");
    if (editor.toolbar && editor.toolbar.parentNode) {
      editor.toolbar.parentNode.removeChild(editor.toolbar);
    }
    state.activeEditor = null;
  }
}

// ── Content Patch (from parent) ─────────────────────────────────────────

function handleContentPatch(data: Record<string, string> | undefined): void {
  if (!data || !data.selector) return;

  const el = document.querySelector(data.selector) as HTMLElement | null;
  if (!el) return;

  if (data.type === "media" && el.tagName === "IMG") {
    (el as HTMLImageElement).src = data.value;
  } else if (data.type === "richtext") {
    el.innerHTML = data.value;
  } else {
    el.textContent = data.value;
  }
}

function handleRequestContent(): void {
  const editables = document.querySelectorAll("[data-sh-resource]");
  const content: ElementInfo[] = [];

  editables.forEach((el) => {
    content.push(getElementInfo(el as HTMLElement));
  });

  sendToParent("sh:content-map", { elements: content });
}

function highlightElement(data: Record<string, string> | undefined): void {
  if (!data || !data.selector) return;
  deselectAll();
  const el = document.querySelector(data.selector) as HTMLElement | null;
  if (el) {
    selectElement(el);
    el.scrollIntoView({ behavior: "smooth", block: "center" });
  }
}

// ── CSS Injection ───────────────────────────────────────────────────────

function injectStyles(): void {
  if (document.getElementById("sh-universal-editor-styles")) return;

  const style = document.createElement("style");
  style.id = "sh-universal-editor-styles";
  style.textContent = `
    /* Edit mode: show outlines on all editable elements */
    html.sh-edit-mode [data-sh-resource] {
      outline: 2px dashed rgba(249, 115, 22, 0.4);
      outline-offset: 4px;
      cursor: pointer;
      position: relative;
      transition: outline 0.2s ease, outline-offset 0.2s ease;
    }
    html.sh-edit-mode [data-sh-resource]:hover {
      outline: 2px solid #f97316;
      outline-offset: 4px;
    }
    /* Label tooltip on hover — uses ::after to avoid conflicts with page ::before */
    html.sh-edit-mode [data-sh-label]:hover::after {
      content: attr(data-sh-label);
      position: absolute;
      top: -28px;
      left: 0;
      right: auto;
      bottom: auto;
      width: auto;
      height: auto;
      background: #f97316;
      color: white;
      padding: 3px 8px;
      font-size: 11px;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
      border-radius: 4px;
      white-space: nowrap;
      z-index: 100000;
      pointer-events: none;
      line-height: 1.4;
      font-weight: 500;
      transform: none;
      opacity: 1;
      display: block;
      border: none;
      box-shadow: none;
      margin: 0;
    }
    /* Selected element */
    html.sh-edit-mode .sh-selected {
      outline: 2px solid #f97316 !important;
      outline-offset: 4px;
      box-shadow: 0 0 0 4px rgba(249, 115, 22, 0.15);
    }
    /* Editing state */
    .sh-editing {
      outline: 2px solid #2563eb !important;
      outline-offset: 2px;
      box-shadow: 0 0 0 4px rgba(37, 99, 235, 0.15) !important;
      min-height: 1em;
    }
    /* Rich text toolbar */
    .sh-rte-toolbar {
      display: flex;
      gap: 2px;
      background: #1e293b;
      border-radius: 8px;
      padding: 4px;
      box-shadow: 0 4px 16px rgba(0,0,0,0.2);
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    }
    .sh-rte-btn {
      background: transparent;
      border: none;
      color: #e2e8f0;
      width: 32px;
      height: 32px;
      border-radius: 4px;
      cursor: pointer;
      font-size: 14px;
      font-weight: 600;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background 0.15s;
    }
    .sh-rte-btn:hover {
      background: rgba(255,255,255,0.15);
    }
    /* Container type elements */
    html.sh-edit-mode [data-sh-type='container'] {
      outline-style: dashed;
      outline-color: rgba(139, 92, 246, 0.4);
    }
    html.sh-edit-mode [data-sh-type='container']:hover {
      outline-color: #8b5cf6;
    }
    /* Component type elements */
    html.sh-edit-mode [data-sh-type='component'] {
      outline-style: dashed;
      outline-color: rgba(16, 185, 129, 0.4);
    }
    html.sh-edit-mode [data-sh-type='component']:hover {
      outline-color: #10b981;
    }
    /* Preview mode: remove all outlines */
    html.sh-preview-mode [data-sh-resource] {
      outline: none !important;
      cursor: default;
    }
  `;

  document.head.appendChild(style);
}

// ── Initialization ──────────────────────────────────────────────────────

function init(): void {
  // Only initialize if we're inside an iframe (loaded by the editor shell)
  if (window === window.parent) return;

  // SH350 - and only if the page says which origin is allowed to drive it. A page that starts
  // making things contenteditable, and answering questions about its content, for an unnamed
  // parent is the defect this fixes; the server-injected bridge has had this rule since SH185.
  state.parentOrigin = readMeta(EDITOR_ORIGIN_META);
  if (!state.parentOrigin) {
    // Deliberately loud rather than silent: a front end that upgraded this package and did not
    // add the meta tag would otherwise see editing "just stop working" with nothing to read.
    console.warn(
      '[shio-editor] inert: add <meta name="' + EDITOR_ORIGIN_META +
        '" content="https://your-console-origin"> so this page knows which origin may edit it.'
    );
    return;
  }

  // Read connection metadata
  state.connectionUrl = readConnectionMeta();
  state.siteName = readMeta("urn:shio:system:site");
  state.folderPath = readMeta("urn:shio:system:path");

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
    connectionUrl: state.connectionUrl,
    siteName: state.siteName,
    folderPath: state.folderPath,
  });
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", init);
} else {
  init();
}
