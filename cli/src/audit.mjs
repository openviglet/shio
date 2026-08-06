/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH391 — the browser as a measuring instrument, as the parts that do not need one.
 *
 * ## Why a third browser command, when two already exist
 *
 * Playwright is a dependency here twice already, and both uses are right. `shio-react/e2e`
 * drives the console; `shio snapshot` (SH102) hands a **human** a PNG. Neither is agent-shaped,
 * so the gap SH265 named honestly — the digest covers the failure an *agent* creates, not the one
 * a *reader* sees — was still closed by asking somebody to look at a picture, which is the round
 * trip P9 exists to remove.
 *
 * So this command renders the page in the browser that is already installed and returns **text**.
 * The difference is not the medium, it is what the artifact asks of its reader: a full-page
 * screenshot costs roughly 1.6k tokens after downscaling, cannot be diffed across turns, and asks
 * the reader to *judge*; twenty findings cost a few hundred, grep and diff, and ask the reader to
 * *fix*. `audit-cost.test.mjs` measures both numbers and asserts the ceiling, because a cost that
 * is argued rather than measured fails quietly in somebody else's environment.
 *
 * ## The split, and why the probe is in this file
 *
 * Everything here is pure but {@link probe}, which runs **inside the page** — Playwright
 * serializes it and evaluates it in the browser, so it may close over nothing but its own
 * argument. It lives beside the pure half anyway, because the alternative is a template literal
 * in the command file that no editor checks. What the unit suite pins is the half that decides
 * *what a finding means*: the WCAG ratio, the merge across widths, the severity split, the
 * ordering, and the exit code.
 *
 * ## Two decisions that are the whole design
 *
 * **A selector, not a coordinate.** Every finding names the element, so the next edit is targeted
 * rather than exploratory. A finding that says "something overflows at 375px" costs the same
 * tokens as one that says which element and buys nothing.
 *
 * **Errors are what a machine observed; warnings are what a person decides.** A 404 and a thrown
 * exception are facts, and they exit non-zero. An overflow, a contrast ratio, a collapsed box and
 * a fallback font are *defects in almost every case* and design intent in the rest — a 0×0
 * tracking pixel is somebody's analytics, and a 3.9:1 heading may be a signed-off brand colour.
 * Failing the build on those teaches the same lesson `--fail-on-change` was kept opt-in to avoid:
 * that this command's exit code is the one you wrap in `|| true`. `--strict` is there for a
 * caller who has decided otherwise for their own project.
 */

/** Every rule this audit can report, and the severity each carries. Error ⇒ exit 1. */
export const AUDIT_RULES = {
  "request-failed": "error",
  "console-error": "error",
  overflow: "warn",
  contrast: "warn",
  "zero-size": "warn",
  collapsed: "warn",
  "font-fallback": "warn",
};

/**
 * Desktop and phone.
 *
 * <p>Two loads rather than three: a width is a page load, and the two that disagree about a
 * layout are the widest and the narrowest. `--width 1280,768,375` adds the tablet when a caller
 * wants it.
 */
export const DEFAULT_WIDTHS = [1280, 375];

/** WCAG 2.1 AA for body text, and for large text (≥24px, or ≥18.66px bold). */
export const CONTRAST_FLOOR = 4.5;
export const LARGE_TEXT_FLOOR = 3;
export const LARGE_TEXT_PX = 24;
export const LARGE_BOLD_PX = 18.66;

/** Findings kept per rule per width. Beyond this the probe counts what it dropped and says so. */
export const PER_RULE_CAP = 25;

/**
 * Chromium logs its own console error for every response it could not load, so a single 404
 * arrives twice: once from the response listener, which knows the URL and the status, and once as
 * a console message that names neither. Measured on a fixture with two broken references, that was
 * five error findings for three defects.
 *
 * <p>Dropping the echo is deduplication rather than suppression: the two listeners are on the same
 * page object, so every failed request the browser logged is a request the response handler saw.
 */
export const NETWORK_ECHO = /^Failed to load resource:/;

/**
 * Viewport widths from `--width`.
 *
 * <p>De-duplicated and sorted widest-first, so two runs of the same command produce the same
 * report in the same order (P5) and the desktop finding is read before the phone one.
 */
export function parseWidths(flag) {
  if (flag === undefined || flag === true || flag === "") {
    return { widths: [...DEFAULT_WIDTHS] };
  }
  const raw = String(flag)
    .split(/[,\s]+/)
    .filter(Boolean);
  const widths = [];
  for (const each of raw) {
    const value = Number(each);
    if (!Number.isInteger(value) || value < 200 || value > 4096) {
      return {
        error:
          `"${each}" is not a viewport width. Pass whole pixels between 200 and 4096, ` +
          `comma-separated.\nExample: --width 1280,768,375`,
      };
    }
    widths.push(value);
  }
  return { widths: [...new Set(widths)].sort((a, b) => b - a) };
}

/**
 * Parses the colour forms a computed style actually produces.
 *
 * <p>Chromium serializes to `rgb()`/`rgba()`, so those two are the cases that matter; the hex
 * forms are here because a test fixture is written by a person. Anything else — `color(display-p3
 * …)`, a named system colour — returns null, and a null colour is a **skipped** contrast check
 * rather than a guessed one: a fabricated ratio is the number somebody would put a threshold on.
 */
export function parseColor(value) {
  const raw = String(value ?? "").trim().toLowerCase();
  if (!raw || raw === "transparent") return { r: 0, g: 0, b: 0, a: 0 };
  const fn = /^rgba?\(([^)]+)\)$/.exec(raw);
  if (fn) {
    const parts = fn[1].split(/[,/\s]+/).filter(Boolean).map(Number);
    if (parts.length < 3 || parts.some((n) => !Number.isFinite(n))) return null;
    const [r, g, b, a = 1] = parts;
    return { r, g, b, a };
  }
  const hex = /^#([0-9a-f]{3,8})$/.exec(raw);
  if (hex) {
    const digits = hex[1];
    const wide = digits.length > 4;
    const size = wide ? 2 : 1;
    const at = (index) => {
      const slice = digits.slice(index * size, index * size + size);
      if (!slice) return undefined;
      return Number.parseInt(wide ? slice : slice + slice, 16);
    };
    if (digits.length !== 3 && digits.length !== 4 && digits.length !== 6 && digits.length !== 8) {
      return null;
    }
    const alpha = at(3);
    return { r: at(0), g: at(1), b: at(2), a: alpha === undefined ? 1 : alpha / 255 };
  }
  return null;
}

/** WCAG relative luminance. */
function luminance({ r, g, b }) {
  const channel = (value) => {
    const scaled = Math.min(Math.max(value / 255, 0), 1);
    return scaled <= 0.03928 ? scaled / 12.92 : ((scaled + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

/** Composites a partially transparent foreground over its background. */
function blend(fg, bg) {
  return {
    r: fg.r * fg.a + bg.r * (1 - fg.a),
    g: fg.g * fg.a + bg.g * (1 - fg.a),
    b: fg.b * fg.a + bg.b * (1 - fg.a),
    a: 1,
  };
}

/**
 * The WCAG contrast ratio between two colours, or null when either cannot be read.
 *
 * <p>Text drawn at less than full opacity is composited over its background before anybody reads
 * it, so the declared colour is not the colour on screen — judging it would report a ratio that
 * is not the one the page has.
 */
export function contrastRatio(foreground, background) {
  const fg = parseColor(foreground);
  const bg = parseColor(background);
  if (!fg || !bg || bg.a === 0) return null;
  const over = fg.a >= 1 ? fg : blend(fg, bg);
  const a = luminance(over) + 0.05;
  const b = luminance(bg) + 0.05;
  return Math.round((Math.max(a, b) / Math.min(a, b)) * 100) / 100;
}

/** The floor this text has to clear: large text is allowed 3:1 by WCAG, and this respects that. */
export function contrastFloor({ fontSize, bold } = {}, floor = CONTRAST_FLOOR) {
  const size = Number(fontSize);
  if (!Number.isFinite(size)) return floor;
  const large = size >= LARGE_TEXT_PX || (bold && size >= LARGE_BOLD_PX);
  // A caller who lowered the floor asked for a lower floor, so the large-text allowance never
  // raises it above what they set.
  return large ? Math.min(LARGE_TEXT_FLOOR, floor) : floor;
}

/**
 * Turns what the browser observed into findings.
 *
 * <p>One finding per (rule, element) across every width, because the same box overflowing at
 * three widths is one thing to fix. `widths` says where it was seen, and the numbers kept are the
 * **worst** ones — the widest overflow, the lowest ratio — since that is the one that decides
 * whether the fix worked.
 *
 * @param observed `{ probes: [{ width, overflow, zeroSize, collapsed, text, fonts, capped }],
 *                    requests: [], consoleErrors: [] }`
 * @param options  `contrast` — override the AA floor
 */
export function classify(observed, options = {}) {
  const floor = Number(options.contrast ?? CONTRAST_FLOOR);
  const found = new Map();
  const capped = {};

  const merge = (rule, selector, width, payload, worse) => {
    const key = `${rule}\u0000${selector}`;
    const existing = found.get(key);
    if (!existing) {
      found.set(key, {
        rule,
        severity: AUDIT_RULES[rule] ?? "warn",
        selector,
        widths: width === undefined ? [] : [width],
        ...payload,
      });
      return;
    }
    if (width !== undefined && !existing.widths.includes(width)) existing.widths.push(width);
    if (worse && worse(payload, existing)) Object.assign(existing, payload);
  };

  for (const probe of observed?.probes ?? []) {
    const { width } = probe;
    for (const [rule, dropped] of Object.entries(probe.capped ?? {})) {
      capped[rule] = Math.max(capped[rule] ?? 0, Number(dropped) || 0);
    }
    for (const item of probe.overflow ?? []) {
      merge(
        "overflow",
        item.selector,
        width,
        { px: item.px, side: item.side, at: width },
        (next, current) => next.px > current.px,
      );
    }
    for (const item of probe.zeroSize ?? []) {
      merge("zero-size", item.selector, width, { boxWidth: item.width, boxHeight: item.height, kind: item.kind });
    }
    for (const item of probe.collapsed ?? []) {
      merge("collapsed", item.selector, width, { children: item.children });
    }
    for (const item of probe.text ?? []) {
      const ratio = contrastRatio(item.color, item.background);
      if (ratio === null) continue;
      const needs = contrastFloor(item, floor);
      if (ratio >= needs) continue;
      merge(
        "contrast",
        item.selector,
        width,
        { ratio, needs, color: item.color, background: item.background, sample: item.sample },
        (next, current) => next.ratio < current.ratio,
      );
    }
    for (const item of probe.fonts ?? []) {
      if (item.loaded) continue;
      merge("font-fallback", item.family, width, { source: item.source ?? "computed" });
    }
  }

  // A URL, not a selector: the element that referenced it may not be the one a reader fixes, and
  // a stylesheet's background image has no element at all.
  for (const item of observed?.requests ?? []) {
    merge("request-failed", item.url, item.width, {
      status: item.status ?? 0,
      failure: item.failure ?? null,
    });
  }
  for (const item of observed?.consoleErrors ?? []) {
    if (NETWORK_ECHO.test(String(item.text ?? ""))) continue;
    merge("console-error", item.text, item.width, {});
  }

  const findings = [...found.values()];
  for (const finding of findings) finding.widths.sort((a, b) => b - a);
  findings.sort(compare);
  return { findings, capped, counts: count(findings) };
}

/** Deterministic order: errors first, then by rule, then by the element named (P5). */
function compare(a, b) {
  if (a.severity !== b.severity) return a.severity === "error" ? -1 : 1;
  if (a.rule !== b.rule) return a.rule < b.rule ? -1 : 1;
  if (a.selector === b.selector) return 0;
  return a.selector < b.selector ? -1 : 1;
}

function count(findings) {
  const counts = { total: findings.length, errors: 0, warnings: 0 };
  for (const finding of findings) {
    if (finding.severity === "error") counts.errors += 1;
    else counts.warnings += 1;
  }
  return counts;
}

/**
 * One line per finding: severity, rule, the element, and what is wrong with it.
 *
 * <p>A `console-error`'s message *is* the finding, so it has no detail — and the dash is dropped
 * with it rather than left dangling at the end of the line, which is what the first real run
 * printed.
 */
export function line(finding) {
  const where = finding.widths.length ? ` [${finding.widths.join(",")}]` : "";
  const said = detail(finding);
  const severity = finding.severity === "error" ? "error" : "warn ";
  return `${severity} ${finding.rule.padEnd(15)} ${finding.selector}${said ? ` — ${said}` : ""}${where}`;
}

function detail(finding) {
  switch (finding.rule) {
    case "overflow":
      return `${finding.px}px past the ${finding.side} edge`;
    case "contrast":
      return `${finding.ratio}:1 (needs ${finding.needs}:1) ${finding.color} on ${finding.background}${
        finding.sample ? ` — "${finding.sample}"` : ""
      }`;
    case "zero-size":
      return `${finding.boxWidth}×${finding.boxHeight} box${finding.kind ? ` (${finding.kind})` : ""}`;
    case "collapsed":
      return `height 0 around ${finding.children} laid-out child${finding.children === 1 ? "" : "ren"}`;
    case "font-fallback":
      return `did not load, so the page fell back${finding.source === "font-face" ? " (@font-face errored)" : ""}`;
    case "request-failed":
      return finding.status ? `HTTP ${finding.status}` : `request failed: ${finding.failure ?? "unknown"}`;
    default:
      return "";
  }
}

/**
 * The whole report.
 *
 * <p>`--limit` bounds what is printed, never what was counted: a cut this report did not declare
 * would read as a clean pass, which is the failure `shio verify`'s `notes` exist to prevent.
 */
export function describe({ target, url, widths, findings, counts, capped }, options = {}) {
  const limit = Number.isFinite(Number(options.limit)) ? Number(options.limit) : Infinity;
  const shown = findings.slice(0, Math.max(limit, 0));
  const head =
    `shio audit: ${target} — ${counts.total} finding${counts.total === 1 ? "" : "s"} ` +
    `(${counts.errors} error${counts.errors === 1 ? "" : "s"}, ${counts.warnings} warning${
      counts.warnings === 1 ? "" : "s"
    }) at ${widths.join(",")}px`;
  const lines = [head, `  page: ${url}`];
  for (const finding of shown) lines.push(line(finding));
  if (shown.length < findings.length) {
    lines.push(`  (${findings.length - shown.length} more — raise --limit to see them)`);
  }
  for (const [rule, dropped] of Object.entries(capped ?? {}).sort(([a], [b]) => (a < b ? -1 : 1))) {
    if (dropped > 0) lines.push(`  note: ${rule} stopped at ${PER_RULE_CAP} per load, ${dropped} not examined`);
  }
  if (!findings.length) {
    lines.push("  nothing to fix: no overflow, no failed request, no console error, contrast clears AA");
  }
  return `${lines.join("\n")}\n`;
}

/**
 * Runs **inside the page** — this function is serialized by Playwright, so it may reference
 * nothing outside its own body.
 *
 * <p>What it returns are *observations*, not findings: rectangles, computed colours, font
 * availability. Every judgment (is this ratio too low, is this box the one to report) is made by
 * {@link classify}, in a process where it can be unit-tested.
 *
 * <p>It is one long function on purpose, and a complexity warning here is a false positive: a
 * serialized function may not call a helper it does not contain, so splitting it out would be a
 * ReferenceError inside a Chromium process — several files from the cause, and only for a caller
 * who actually has a browser installed. `audit.test.mjs` lints the same constraint from the other
 * side by checking the source closes over nothing.
 *
 * <h2>The definitions, because each one is a decision</h2>
 *
 * <p><b>Overflow is horizontal, and it is reported where it is introduced.</b> A page scrolls
 * down by design, so vertical overflow is not a finding. And an element that sticks out drags
 * every ancestor's box with it — reporting all of them would name `html` and bury the cause, so
 * what is reported is the element that exceeds **both** its own parent's box and the viewport.
 * That is the point where the overflow enters the layout. An absolutely positioned or fixed box
 * has a different containing block, so for those only the viewport comparison applies.
 *
 * <p><b>The preview banner is excluded.</b> `shio snapshot` deliberately keeps it, because a
 * baseline and a capture have to agree. A finding is an instruction to fix something, and nobody
 * can fix Shio's own injected banner from their content.
 *
 * <p><b>A hidden element is not a defect.</b> `display:none`, `visibility:hidden` and a
 * display-none ancestor are how a page hides things on purpose. What *is* a defect is an element
 * the CSS says is visible whose box has no size — which is why the two are separated here rather
 * than collapsed into one rectangle test.
 */
export function probe(options) {
  const cap = options?.cap ?? 25;
  const contrastSampleChars = 40;
  const SKIP = new Set([
    "SCRIPT", "STYLE", "LINK", "META", "TITLE", "HEAD", "TEMPLATE", "NOSCRIPT", "BR", "OPTION",
    "OPTGROUP", "SOURCE", "TRACK", "PARAM", "AREA", "MAP", "BASE", "COL", "COLGROUP", "WBR",
    "DIALOG", "HR",
  ]);
  const GENERIC = new Set([
    "serif", "sans-serif", "monospace", "cursive", "fantasy", "system-ui", "ui-sans-serif",
    "ui-serif", "ui-monospace", "ui-rounded", "math", "emoji", "fangsong", "-apple-system",
    "blinkmacsystemfont", "inherit", "initial", "unset",
  ]);
  const REPLACED = new Set(["IMG", "VIDEO", "IFRAME", "CANVAS", "SVG", "OBJECT", "EMBED", "PICTURE"]);

  const overflow = [];
  const zeroSize = [];
  const collapsed = [];
  const text = [];
  const fonts = [];
  const capped = {};
  const push = (list, rule, item) => {
    if (list.length >= cap) {
      capped[rule] = (capped[rule] ?? 0) + 1;
      return;
    }
    list.push(item);
  };

  const selectorFor = (element) => {
    if (!element || element.nodeType !== 1) return "?";
    const parts = [];
    let node = element;
    while (node && node.nodeType === 1 && parts.length < 4) {
      if (node.id) {
        parts.unshift(`#${node.id}`);
        break;
      }
      let part = node.tagName.toLowerCase();
      const classes = (node.getAttribute("class") || "").trim().split(/\s+/).filter(Boolean);
      if (classes.length) part += `.${classes[0]}`;
      const parent = node.parentElement;
      if (parent) {
        const twins = Array.prototype.filter.call(parent.children, (each) => each.tagName === node.tagName);
        if (twins.length > 1) part += `:nth-of-type(${twins.indexOf(node) + 1})`;
      }
      parts.unshift(part);
      if (!parent || parent.tagName === "BODY" || parent.tagName === "HTML") break;
      node = parent;
    }
    return parts.join(" > ");
  };

  const rendered = (element, style) => {
    if (style.display === "none" || style.visibility === "hidden" || style.visibility === "collapse") {
      return false;
    }
    // An ancestor with display:none leaves this element's own computed style untouched, and a
    // null offsetParent is the cheapest way to notice — with the two documented cases where it is
    // null for an element that *is* laid out.
    if (element.offsetParent === null && style.position !== "fixed" && element !== document.body) {
      return element.getClientRects().length > 0;
    }
    return true;
  };

  const ownText = (element) => {
    let out = "";
    for (const node of element.childNodes) {
      if (node.nodeType === 3) out += node.nodeValue;
    }
    return out.replace(/\s+/g, " ").trim();
  };

  const effectiveBackground = (element) => {
    let node = element;
    while (node && node.nodeType === 1) {
      const style = window.getComputedStyle(node);
      // An image behind the text makes the ratio unknowable rather than low, and an unknowable
      // ratio is a skipped check — see parseColor's note on fabricated numbers.
      if (style.backgroundImage && style.backgroundImage !== "none") return null;
      const colour = style.backgroundColor;
      if (colour && colour !== "transparent" && !/rgba\([^)]*,\s*0\s*\)$/.test(colour)) return colour;
      node = node.parentElement;
    }
    return "rgb(255, 255, 255)";
  };

  const viewport = { width: window.innerWidth, height: window.innerHeight };
  const past = (rect) => {
    const right = Math.round(rect.right - viewport.width);
    const left = Math.round(-rect.left);
    if (right > 1) return { side: "right", px: right };
    if (left > 1) return { side: "left", px: left };
    return null;
  };

  const banner = document.querySelector("[data-shio-preview]");
  const families = new Set();

  for (const element of document.body ? document.body.querySelectorAll("*") : []) {
    if (SKIP.has(element.tagName)) continue;
    if (banner && (element === banner || banner.contains(element))) continue;
    const style = window.getComputedStyle(element);
    if (!rendered(element, style)) continue;
    const rect = element.getBoundingClientRect();

    const out = past(rect);
    if (out && rect.width > 0 && rect.height > 0) {
      const parent = element.parentElement;
      const absolute = style.position === "absolute" || style.position === "fixed";
      const parentRect = parent && !absolute ? parent.getBoundingClientRect() : null;
      const insideParent =
        parentRect &&
        (out.side === "right"
          ? rect.right <= parentRect.right + 1
          : rect.left >= parentRect.left - 1);
      // The parent already sticks out this far, so it is the element to report and this one is
      // only along for the ride.
      if (!insideParent) push(overflow, "overflow", { selector: selectorFor(element), ...out });
    }

    const own = ownText(element);
    const replaced = REPLACED.has(element.tagName);
    const width = Math.round(rect.width);
    const height = Math.round(rect.height);
    const kids = Array.prototype.filter.call(element.children, (each) => !SKIP.has(each.tagName));

    if ((own || (replaced && (element.currentSrc || element.src || kids.length))) && (width === 0 || height === 0)) {
      push(zeroSize, "zero-size", {
        selector: selectorFor(element),
        width,
        height,
        kind: replaced ? element.tagName.toLowerCase() : "text",
      });
    } else if (height === 0 && kids.length) {
      const laidOut = kids.filter((child) => child.getBoundingClientRect().height > 0).length;
      // Height 0 *around content that has height* is the float/absolute collapse. Height 0 around
      // nothing is an empty box, which is not a defect on its own.
      if (laidOut) {
        push(collapsed, "collapsed", { selector: selectorFor(element), children: laidOut });
      }
    }

    if (own) {
      const first = (style.fontFamily || "").split(",")[0].trim().replace(/^["']|["']$/g, "");
      if (first) families.add(first);
      const visible =
        typeof element.checkVisibility === "function"
          ? element.checkVisibility({ checkOpacity: true, checkVisibilityCSS: true })
          : true;
      if (visible) {
        const background = effectiveBackground(element);
        if (background) {
          push(text, "contrast", {
            selector: selectorFor(element),
            color: style.color,
            background,
            fontSize: Number.parseFloat(style.fontSize),
            bold: Number(style.fontWeight) >= 700 || style.fontWeight === "bold",
            sample: own.slice(0, contrastSampleChars),
          });
        }
      }
    }
  }

  for (const family of families) {
    if (GENERIC.has(family.toLowerCase())) continue;
    let available = true;
    try {
      available = document.fonts.check(`16px "${family}"`);
    } catch {
      continue;
    }
    if (!available) fonts.push({ family, loaded: false, source: "computed" });
  }
  try {
    document.fonts.forEach((face) => {
      if (face.status !== "error") return;
      const family = String(face.family || "").replace(/^["']|["']$/g, "");
      if (fonts.some((each) => each.family === family)) return;
      fonts.push({ family, loaded: false, source: "font-face" });
    });
  } catch {
    // A browser without a FontFaceSet iterator still reported the computed-family half above.
  }

  return { overflow, zeroSize, collapsed, text, fonts, capped, viewport };
}
