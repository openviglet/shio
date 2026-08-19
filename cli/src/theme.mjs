/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH380 — a captured stylesheet arrives as tokens **plus** CSS, never as CSS.
 *
 * ## The failure this closes
 *
 * SH195 gave a `Theme` a `TOKENS` field in the W3C DTCG shape, because that is what a designer's tool
 * already exports, and the argument was that the commonest change to a site — the accent colour, the
 * type scale, the density — should be a *field edit* rather than a stylesheet rewrite. A capture runs
 * the other direction and the same argument holds: the source's stylesheet **contains** its palette,
 * its type scale and its spacing rhythm, so a conversion that concatenates the file into `CSS` throws
 * all of it away. The replica then looks right and cannot be changed, which is the exact outcome
 * Block Q exists to avoid.
 *
 * ## Two kinds of token, and they are not equally powerful — which the report has to say
 *
 * **Declared** custom properties (`--brand-primary: #0b5`) are the strong case: the source's own CSS
 * already says `var(--brand-primary)`, so lifting one into `TOKENS` makes it genuinely editable, and
 * changing it changes the replica. A modern site hands its whole palette over this way.
 *
 * **Recurring** literals are the weaker case and are still worth having: `#0b5` written out
 * twenty-three times *is* the accent colour, and naming it is how a curator learns the palette exists.
 * But nothing references the name, so editing it changes nothing until somebody rewires the CSS. That
 * asymmetry is printed, because a token list that implied both were editable would be a promise the
 * file cannot keep.
 *
 * ## What is deliberately *not* done: substitution
 *
 * The obvious next step — rewrite `#0b5` to `var(--color-accent)` across the stylesheet — is refused.
 * This module **scans** CSS; it does not parse it into a model it could safely rewrite, and a
 * search-and-replace over a stranger's bytes is the "silently restructured somebody's homepage"
 * hazard from SH379 wearing a different hat. Twenty-three replacements, one of them inside a
 * `content:` string or a data URI, and the replica is broken in a way no digest catches. The CSS is
 * kept verbatim apart from `url()` (see below); the tokens are additive.
 *
 * ## A token the capture is unsure about is reported, not written
 *
 * SH195's own rule makes this load-bearing rather than tidy: a `TOKENS` field that is not JSON
 * degrades the render, and a *name* that cannot be a custom property emits a declaration the browser
 * drops — taking the rest of the `:root` block with it in some parsers. So there is one hard test, and
 * it is a **round trip**: a candidate's DTCG path is compiled back through the mapping
 * `ShThemeTokens` documents (`color.accent` → `--color-accent`), and unless that yields the property
 * name the source actually wrote, the token is reported instead of written. Nothing is guessed into a
 * file nobody reviewed.
 */

/** How many times a literal must appear before it is a palette rather than a coincidence. */
export const DEFAULT_MIN_REFS = 3;

/**
 * The selectors whose custom properties are the site's own tokens.
 *
 * <p>And the reason this is a list rather than "any selector": a custom property declared under
 * `.dark` or inside a media query is a **variant**, so the same name legitimately has two values and
 * neither is the token. Writing one of them would silently pick a theme.
 *
 * <h3>SH845 — atoms, because the list could only ever be a list of the orderings somebody typed</h3>
 *
 * <p>This was a set of whole normalized selectors, and it held `:root,html` and `html,:root` — the
 * two spellings of one selector, enumerated. It did not hold `:root,:host`, which is what a Tailwind
 * v4 sheet emits and therefore what a large share of the modern web declares its palette on. Nothing
 * about that omission is visible from the set: the failure is a token silently reported as a variant.
 * So the rule is stated once — <b>every compound in the list must be a root</b> — and the data is the
 * atoms. A list mixing a root with anything else is still not a root: `:root,.dark` declares a variant
 * in the half that matters.</p>
 */
const ROOT_ATOMS = new Set([":root", "html", ":host", ":where(:root)", ":where(html)"]);

/** Whether every compound in a selector list is a root. See {@link ROOT_ATOMS}. */
function isRootSelector(selector) {
  return everyCompoundIn(selector, ROOT_ATOMS);
}

function everyCompoundIn(selector, atoms) {
  const parts = normalizeSelector(selector).split(",").filter(Boolean);
  return parts.length > 0 && parts.every((part) => atoms.has(part));
}

/**
 * Whether an at-rule makes what it wraps a **variant** (SH845).
 *
 * <p>`@media (prefers-color-scheme: dark)` around a `:root` block is the case {@link ROOT_ATOMS}
 * exists for: the same name has two values, neither is the token, and writing one picks a theme
 * nobody asked for. `@layer` is not that. It orders the cascade and wraps a rule that applies
 * unconditionally, so `@layer theme { :root { --brand: #ea580c } }` is the source **declaring its
 * palette** — the strong case at the top of this file, arriving in the wrapper the commonest CSS
 * toolchain now puts it in.</p>
 *
 * <p>Measured on `www.viglet.org`: 319 custom properties, every one of them refused as
 * `variant-only: declared only under @layer theme`, while `propose` wrote three colour tokens ranked
 * out of literals — one of them `#0000`, which then became the replica's page. Read as an exclusion
 * rule it was doing its job; read as an outcome it threw away the entire named palette and kept a
 * transparent utility class.</p>
 *
 * <p>Stated as *`@layer` and nothing else* rather than as a list of the conditional at-rules, so an
 * at-rule this scanner has never seen keeps today's behaviour. Two values under one name is the
 * failure that cannot be undone downstream; a token left unwritten is reported and can be.</p>
 */
function isVariantAtRule(head) {
  return !/^@layer\b/i.test(String(head ?? ""));
}

/** Properties whose value is a colour, grouped by the role the property implies. */
const COLOR_ROLES = {
  color: "text",
  "background-color": "background",
  background: "background",
  "border-color": "border",
  "border-top-color": "border",
  "border-right-color": "border",
  "border-bottom-color": "border",
  "border-left-color": "border",
  "outline-color": "border",
  fill: "accent",
  stroke: "accent",
  "caret-color": "accent",
  "text-decoration-color": "accent",
};

/**
 * The non-colour groups, in the order they are tested.
 *
 * <p>Each entry is a DTCG group, the properties that feed it, and the DTCG `$type` its values carry.
 * `spacing` is last on purpose: `padding` and `margin` are the commonest length declarations on any
 * page, so a group tested before `font.size` or `radius` would swallow their values.
 */
const LENGTH_GROUPS = [
  { group: "font.size", type: "dimension", properties: ["font-size"] },
  {
    group: "radius",
    type: "dimension",
    properties: ["border-radius", "border-top-left-radius", "border-top-right-radius",
      "border-bottom-left-radius", "border-bottom-right-radius"],
  },
  {
    group: "border.width",
    type: "dimension",
    properties: ["border-width", "border-top-width", "border-right-width", "border-bottom-width",
      "border-left-width"],
  },
  {
    group: "spacing",
    type: "dimension",
    properties: ["margin", "margin-top", "margin-right", "margin-bottom", "margin-left", "padding",
      "padding-top", "padding-right", "padding-bottom", "padding-left", "gap", "row-gap",
      "column-gap"],
  },
];

const OTHER_GROUPS = [
  { group: "font.family", type: "fontFamily", properties: ["font-family"] },
  { group: "font.weight", type: "fontWeight", properties: ["font-weight"] },
  { group: "shadow", type: "shadow", properties: ["box-shadow", "text-shadow"] },
];

// ---------------------------------------------------------------------------------------------
// Reading the stylesheet
// ---------------------------------------------------------------------------------------------

/**
 * Removes comments, which is the whole of the preprocessing.
 *
 * <p>Deliberately not a `/\*[\s\S]*?\*\/` over the raw file and then trust: a `/*` inside a quoted
 * value or a data URI would swallow real declarations. The scan below is a single pass that knows
 * about quotes, so comment handling lives there and this only exists to say why it does not live
 * here.
 */

/**
 * One linear pass over a stylesheet: the declarations, in source order, and where each one sits.
 *
 * <p>A **scanner, not a parser** — SH377's rule, and this file is the second module in the repository
 * reading a stranger's bytes. It tracks quotes, comments, brace depth and the current selector text,
 * and nothing else. No tree, no specificity, no cascade: every question below is answered from a flat
 * list of `(selector, property, value)`, and a question that needed the cascade would be a question
 * this module should refuse rather than approximate.
 *
 * <p>Single-pass and character-driven rather than regex-driven, so there is no backtracking to be
 * pathological about: the cost is linear in the file's length, full stop.
 *
 * <p>SH845 — a declaration carries **two** facts about the at-rules it sits under, because one string
 * cannot answer both questions. `atRule` is the outermost, which is what a report names when it says
 * where a variant was found; `conditional` is the innermost one that makes what it wraps a variant
 * ({@link isVariantAtRule}), which is what every rule below actually asks. They were one field, and
 * `stack.find` returning the outermost meant a `@media` nested inside a `@layer` was invisible the
 * moment `@layer` stopped counting.</p>
 *
 * @returns {{declarations: Array<{selector: string, property: string, value: string, atRule: string|null,
 *            conditional: string|null}>, rules: number, bytes: number}}
 */
export function scanCss(css) {
  const text = String(css ?? "");
  const declarations = [];
  const stack = [];
  let buffer = "";
  let selector = "";
  let rules = 0;
  let index = 0;

  const flush = () => {
    const raw = buffer.trim();
    buffer = "";
    if (!raw) return;
    const colon = splitDeclaration(raw);
    if (!colon) return;
    const ats = stack.filter((each) => each.startsWith("@"));
    const atRule = ats[0] ?? null;
    const conditional = ats.findLast(isVariantAtRule) ?? null;
    declarations.push({ selector, property: colon.property, value: colon.value, atRule, conditional });
  };

  while (index < text.length) {
    const char = text[index];
    if (char === "/" && text[index + 1] === "*") {
      const end = text.indexOf("*/", index + 2);
      index = end === -1 ? text.length : end + 2;
      continue;
    }
    if (char === '"' || char === "'") {
      const end = closingQuote(text, index);
      buffer += text.slice(index, end);
      index = end;
      continue;
    }
    if (char === "{") {
      const head = buffer.trim();
      buffer = "";
      stack.push(head);
      selector = head.startsWith("@") ? selector : head;
      if (!head.startsWith("@")) rules += 1;
      index += 1;
      continue;
    }
    if (char === "}") {
      flush();
      stack.pop();
      // The enclosing selector, which is what a declaration inside `@media` belongs to once the
      // media block closes. Without this, everything after a media query is attributed to its last
      // rule — the defect that would put a dark-mode override's properties on `:root`.
      selector = [...stack].reverse().find((each) => each && !each.startsWith("@")) ?? "";
      index += 1;
      continue;
    }
    if (char === ";") {
      flush();
      index += 1;
      continue;
    }
    buffer += char;
    index += 1;
  }
  flush();
  return { declarations, rules, bytes: text.length };
}

/** The end of a quoted run, quote included. An unterminated quote ends at the file, not in a loop. */
function closingQuote(text, start) {
  const quote = text[start];
  let index = start + 1;
  while (index < text.length) {
    if (text[index] === "\\") {
      index += 2;
      continue;
    }
    if (text[index] === quote) return index + 1;
    index += 1;
  }
  return text.length;
}

/**
 * `prop: value` → its two halves, or null when the text is not a declaration.
 *
 * <p>Split on the **first** colon only: `background: url(http://x/y.png)` has three, and splitting on
 * the last would produce a property called `background: url(http` — which is not a property, is
 * silently dropped by every group test below, and takes the site's whole background with it.
 */
function splitDeclaration(raw) {
  const at = raw.indexOf(":");
  if (at <= 0) return null;
  const name = raw.slice(0, at).trim();
  // A **custom property keeps its case**, and an ordinary one does not. CSS folds `COLOR` to `color`
  // and treats `--Foo` and `--foo` as two different properties — so lowercasing everything both merges
  // two distinct declarations and, much worse, defeats the round trip that is this module's only hard
  // rule: `--brandPrimary` lowercased is `--brandprimary`, which *does* survive the round trip, so the
  // token would be written under a name the source's CSS never references. Silently, and only in the
  // replica. The smoke run against a real stylesheet is what found this.
  const property = name.startsWith("--") ? name : name.toLowerCase();
  const value = raw.slice(at + 1).trim();
  if (!property || !value || /[{}]/.test(property)) return null;
  return { property, value };
}

// ---------------------------------------------------------------------------------------------
// The name round trip, which is the only hard rule here
// ---------------------------------------------------------------------------------------------

/**
 * A DTCG path → the custom property `ShThemeTokens` will emit for it.
 *
 * <p>A deliberate mirror of `ShThemeTokens.customProperty`, and `theme.test.mjs` asserts the two stay
 * the same by reading the Java. Mirroring is the lesser evil: the alternative is for this module to
 * write a name whose *runtime* spelling it cannot predict, and the failure mode there is a `:root`
 * block the browser drops — silently, taking its siblings with it in some parsers.
 */
export function customPropertyFor(path) {
  return `--${String(path).replace(/[^A-Za-z0-9-]+/g, "-")}`;
}

/**
 * The DTCG path a source's own custom property becomes, or null when nothing safe round-trips.
 *
 * <p>`--brand-primary` → `brand.primary`, which compiles back to `--brand-primary`. That is not a
 * guess about the source's hierarchy, it is the **inverse of a documented mapping**, and it is what
 * keeps the captured CSS working: the stylesheet says `var(--brand-primary)`, so a token that
 * compiled to any other name would leave every rule referencing it unstyled.
 *
 * <p>Anything that does not survive the round trip returns null and is reported. `--Brand_Primary` is
 * the ordinary case: the underscore is legal in a custom property and is not legal in the mapping, so
 * writing it would rename the property out from under the CSS that uses it.
 */
export function pathForCustomProperty(name) {
  const raw = String(name ?? "").trim();
  if (!raw.startsWith("--") || raw.length < 3) return null;
  const segments = raw.slice(2).split("-");
  if (segments.some((segment) => !segment)) return null;
  const path = segments.join(".");
  return customPropertyFor(path) === raw ? path : null;
}

// ---------------------------------------------------------------------------------------------
// Values
// ---------------------------------------------------------------------------------------------

const HEX = /^#(?:[0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})$/i;
const COLOR_FUNCTION = /^(?:rgba?|hsla?|hwb|lab|lch|oklab|oklch|color|color-mix)\(/i;
/**
 * A single length.
 *
 * <p>`\d+(?:\.\d+)?` and **not** `\d+\.?\d*`, which is the same class of defect SH377's tag regex was
 * written to avoid and the reason it is worth a comment on a five-token pattern: two quantifiers over
 * the same class with an optional separator between them means the engine explores every split of a
 * digit run before failing, which is quadratic. This is called once per declaration value on bytes
 * fetched from a stranger's server, so a value of 20k digits would make the scan hang rather than
 * return — and a hang is the failure mode nobody can diagnose from a report.
 */
const LENGTH = /^-?(?:\d+(?:\.\d+)?|\.\d+)(px|rem|em|%|vh|vw|vmin|vmax|ch|ex|pt|cm|mm|in|q)$/i;

/** Whether a value is a colour this reader is willing to call one. A keyword is not enough. */
export function isColor(value) {
  const raw = String(value).trim();
  return HEX.test(raw) || COLOR_FUNCTION.test(raw);
}

/** A single length. A shorthand (`8px 16px`) is deliberately not one — it is two tokens or none. */
export function isLength(value) {
  return LENGTH.test(String(value).trim());
}

/** DTCG's `$type` for a value, or null when this reader will not claim one. */
export function typeOf(value) {
  const raw = String(value).trim();
  if (isColor(raw)) return "color";
  if (isLength(raw)) return "dimension";
  return null;
}

// ---------------------------------------------------------------------------------------------
// Declared tokens
// ---------------------------------------------------------------------------------------------

/**
 * The custom properties the source declares, split into the ones that can be written and the ones
 * that cannot.
 *
 * <p>Three ways to be unsure, and each one is a real stylesheet rather than a hypothetical.
 * **Variant**: the same name declared under two selectors — a light and a dark palette — where
 * writing either value picks a theme nobody asked for. **Conflict**: declared twice under the *same*
 * root with different values, which is a cascade question and this is a scanner. **Name**: a name
 * that does not survive the round trip above.
 */
export function declaredTokens(declarations) {
  const roles = rolesByUse(declarations);
  const roots = new Map();
  const variants = new Map();
  for (const declaration of declarations) {
    if (!declaration.property.startsWith("--")) continue;
    const inRoot = isRootSelector(declaration.selector) && !declaration.conditional;
    const into = inRoot ? roots : variants;
    const seen = into.get(declaration.property);
    if (seen) seen.push(declaration);
    else into.set(declaration.property, [declaration]);
  }

  const tokens = [];
  const unsure = [];
  for (const [name, group] of [...roots.entries()].sort(byKey)) {
    const values = new Set(group.map((each) => each.value));
    if (values.size > 1) {
      unsure.push({ name, reason: "conflict", detail: `declared ${values.size} times with different values` });
      continue;
    }
    if (variants.has(name)) {
      unsure.push({
        name,
        reason: "variant",
        // SH845 — the *conditional* at-rule, not the outermost: under `@layer base { @media … }` the
        // outermost is the layer, which is now the half that is not the reason.
        detail: `also declared under ${variants.get(name).map(variantOrigin).join(" ")}`,
      });
      continue;
    }
    const path = pathForCustomProperty(name);
    if (!path) {
      unsure.push({ name, reason: "name", detail: `${name} does not survive the DTCG round trip` });
      continue;
    }
    const use = roles.get(name);
    // SH834 — and only where the value is of the group's own kind: a `--icon: url(…)` read by
    // `background-image` is roled by nothing rather than counted as a length.
    const roled = use && use.test(group[0].value);
    tokens.push({
      path, name, value: group[0].value, type: typeOf(group[0].value), source: "declared",
      // SH831 — what the source uses this token *for*, where its own name does not say. Present
      // only when something references it in a property some group claims; a token nothing uses
      // has no role, and neither has one used only where no group looks.
      ...(roled
        ? { group: use.group, role: use.role, refs: use.refs, page: use.page > 0 }
        : {}),
    });
  }
  // A variant with no root declaration at all: the palette lives entirely under `.dark`, and there is
  // no default to lift. Reported, because silence here reads as "this site declares no tokens".
  for (const [name, group] of [...variants.entries()].sort(byKey)) {
    if (roots.has(name)) continue;
    unsure.push({
      name,
      reason: "variant-only",
      detail: `declared only under ${group.map(variantOrigin).join(" ")}`,
    });
  }
  return { tokens, unsure };
}

/** What made one declaration a variant: the conditional at-rule if any, else its selector (SH845). */
function variantOrigin(declaration) {
  return declaration.conditional ?? declaration.selector ?? declaration.atRule;
}

function normalizeSelector(selector) {
  return String(selector ?? "").replace(/\s+/g, "").toLowerCase();
}

/** `var(--brand)` and `var(--brand, #fff)` — every custom property a value reads. */
const VAR_REF = /var\(\s*(--[\w-]+)/g;

/**
 * Which group and role each property implies, as one table (SH834).
 *
 * <p>The same three tables `bucketFor` walks, flattened once so a *declared* token can be roled by
 * the properties that read it — the question SH831 asked of colour and SH834 asks of everything
 * else. Colour is the only group with competing roles; a length or a font stack has exactly one,
 * which is what makes the admission rules downstream differ by group rather than by taste.</p>
 */
const GROUP_FOR_PROPERTY = new Map([
  ...Object.entries(COLOR_ROLES).map(([property, role]) =>
    [property, { group: "color", role, type: "color", test: isColor }]),
  ...LENGTH_GROUPS.flatMap((entry) => entry.properties.map((property) =>
    [property, { group: entry.group, role: null, type: entry.type, test: isLength }])),
  ...OTHER_GROUPS.flatMap((entry) => entry.properties.map((property) =>
    [property, { group: entry.group, role: null, type: entry.type, test: () => true }])),
]);

/**
 * The selectors that make a colour the **page's**, rather than one element's (SH831).
 *
 * <p>`body { color: var(--ink) }` is a stylesheet saying *this is what this site's text looks like*.
 * `.card h3 { color: var(--brand) }` is not, whatever the count. `*` is here because a reset that
 * sets a colour on everything is making the same statement in the other direction.</p>
 *
 * <p>SH845 — atoms rather than orderings, for the reason {@link ROOT_ATOMS} states: this held
 * `html,body` and `body,html` and would have needed a third entry for `html,body,#root`.</p>
 */
const PAGE_ATOMS = new Set([":root", "html", "body", "*", ":host"]);

/** Whether every compound in a selector list makes a declaration the page's. See {@link PAGE_ATOMS}. */
function isPageSelector(selector) {
  return everyCompoundIn(selector, PAGE_ATOMS);
}

/**
 * What a declared token is *used for*, read off the declarations that reference it (SH831).
 *
 * <h2>A name is not a role, and the source picked the name</h2>
 *
 * <p>{@link recurringTokens} knows what a literal is for, because it saw the property the literal
 * landed in: `color: #10243a` is text, `background: #eef3f8` is a surface. A **declared** token
 * carried none of that — `declaredTokens` never looked at where `--brand` is read — so the extracted
 * palette was a list of the source's own words (`brand`, `ink`, `wash`, `vg-primary`) and nothing
 * downstream could tell which one was the accent. That is why SH831's bridge could not be a name
 * table: a synonym list guessing that `brand` means accent is exactly the unverifiable label SH689
 * spent a task removing.</p>
 *
 * <p>The evidence is already in the file and is the same evidence the recurring side uses: which
 * properties read the variable, bucketed through `COLOR_ROLES`. A declaration whose own property is a
 * custom property is skipped — `--button-bg: var(--brand)` is an alias, not a use — for the reason
 * `recurringTokens` skips them too.</p>
 *
 * <p>Most-referenced role wins, ties broken on the role's name so two runs over one stylesheet agree
 * (P5). A token read in one colour property once is still roled: one use is what the source had, and
 * the rank ordering downstream is what decides whether it is reached.</p>
 *
 * <h2>`page` is the part that had to be measured, and the count alone was wrong</h2>
 *
 * <p>Ranking a role by reference count picked the **accent** as the archetype's text colour: in
 * `theme-heavy`, `--brand` is read by four `color:` declarations (a nav link, a card heading, two
 * `::before` marks) and `--ink` by three — one of which is `body`. Shipping that would have painted
 * the replica's body copy teal, which is a worse artefact than the generic default it replaced.</p>
 *
 * <p>So a use is recorded with *where* it was, and a page-level use decides the role on its own:
 * counting says which colour a site reaches for often, and only the selector says which colour is
 * the page's. Downstream (`vocabularyTokensFrom`) takes page-level uses and nothing else from the
 * declared half, because a count is evidence about a name and this is evidence about a role.</p>
 *
 * <h2>SH834 — the same question, asked of every group</h2>
 *
 * <p>This read `COLOR_ROLES` alone, so a `--radius` read by three `border-radius` rules and a
 * `--font-body` read by `body { font-family }` left the extractor as bare names with nothing saying
 * what they were for. Every group is bucketed now, through {@link GROUP_FOR_PROPERTY}. What differs
 * downstream is only *which* evidence admits a row: colour is the one group with competing roles,
 * so it needs the page-level statement; a corner radius or a font stack has one role wherever it
 * appears, and there is nothing for a selector to disambiguate.</p>
 *
 * <p>The value is tested against its group the way `bucketFor` tests a literal, so
 * `--icon: url("/img/sprite.svg")` read by `background-image` is roled by nothing rather than
 * counted as a length.</p>
 */
function rolesByUse(declarations) {
  const counts = new Map();
  for (const declaration of declarations) {
    if (declaration.property.startsWith("--")) continue;
    const slot = GROUP_FOR_PROPERTY.get(declaration.property);
    if (!slot) continue;
    const page = isPageSelector(declaration.selector) && !declaration.conditional;
    const key = slot.role ? `${slot.group} ${slot.role}` : slot.group;
    for (const match of String(declaration.value ?? "").matchAll(VAR_REF)) {
      const seen = counts.get(match[1]) ?? new Map();
      const tally = seen.get(key)
        ?? { group: slot.group, role: slot.role, test: slot.test, refs: 0, page: 0 };
      tally.refs += 1;
      tally.page += page ? 1 : 0;
      seen.set(key, tally);
      counts.set(match[1], seen);
    }
  }
  const roles = new Map();
  for (const [name, seen] of counts) {
    const ranked = [...seen.entries()].sort((a, b) => {
      // A page-level use outranks any number of element-level ones, in both directions: it decides
      // which role this token has, not only how highly it ranks within one.
      if ((a[1].page > 0) !== (b[1].page > 0)) return a[1].page > 0 ? -1 : 1;
      if (a[1].refs !== b[1].refs) return b[1].refs - a[1].refs;
      return a[0] < b[0] ? -1 : 1;
    });
    roles.set(name, ranked[0][1]);
  }
  return roles;
}

const byKey = (a, b) => (a[0] === b[0] ? 0 : a[0] < b[0] ? -1 : 1);

// ---------------------------------------------------------------------------------------------
// Recurring tokens
// ---------------------------------------------------------------------------------------------

/**
 * The literals a stylesheet repeats often enough to be its palette.
 *
 * <p>Named by **group plus role plus rank**, and ranked by how often the value occurs — because the
 * rank is the one part of the name that traces back to the source, and every row carries the count
 * that argued for it. A name invented from nothing (`color.brand`, `spacing.comfortable`) reads
 * better and is a guess; SH380's rule is that a token nobody can trace back to the source is exactly
 * that.
 *
 * <p>Ties break on the value, never on encounter order, or two runs over one file disagree about
 * which colour is `color.text-1` (P5).
 */
export function recurringTokens(declarations, { minRefs = DEFAULT_MIN_REFS } = {}) {
  const buckets = new Map();
  for (const declaration of declarations) {
    if (declaration.property.startsWith("--")) continue;
    const slot = bucketFor(declaration);
    if (!slot) continue;
    const key = `${slot.group} ${slot.role ?? ""} ${declaration.value}`;
    // SH834 — where the literal was, not only how often. `body { font-size: 17px }` is the page
    // saying what its own text is; the same value on a card is one step of a scale, and only the
    // selector separates them. The declared half has carried this since SH831.
    const page = isPageSelector(declaration.selector) && !declaration.conditional;
    const existing = buckets.get(key);
    if (existing) {
      existing.refs += 1;
      existing.page ||= page;
      existing.properties.add(declaration.property);
    } else {
      buckets.set(key, {
        group: slot.group,
        role: slot.role ?? null,
        type: slot.type,
        value: declaration.value,
        refs: 1,
        page,
        properties: new Set([declaration.property]),
      });
    }
  }

  const kept = [...buckets.values()]
    // SH834 — `minRefs` asks *is this value a palette*, and a page-level declaration does not have
    // to answer it: `body { font-size: 17px }` is the page stating its own type, once, by nature.
    // Repetition is evidence for a value nobody labelled; a selector that means "everything" is
    // evidence of a different kind, and the bar for one was never the bar for the other.
    //
    // SH839 — this comment used to end *"across the whole source corpus this admits nothing … the
    // rule is right and its blast radius here is zero"*, and a run refuted it on the very commit
    // that wrote it. The measurement behind that sentence filtered for values holding no `var(`,
    // which is the right filter for a length group and the wrong one for `font.family`; the
    // exemption was already admitting `font.family.1 = var(--font-body)` from `theme-heavy`'s
    // `body` rule. `bucketFor` refuses a pointer now, so what this admits on that corpus is one
    // row — `font.size.1 = 17px`, the case the rule exists for. A claim of zero cost that a run
    // refutes is worse than no claim, which is why the correction is here and not only in a ledger.
    .filter((row) => row.refs >= minRefs || row.page)
    .sort((a, b) => {
      if (a.group !== b.group) return a.group < b.group ? -1 : 1;
      if ((a.role ?? "") !== (b.role ?? "")) return (a.role ?? "") < (b.role ?? "") ? -1 : 1;
      if (a.refs !== b.refs) return b.refs - a.refs;
      return a.value < b.value ? -1 : 1;
    });

  const ranks = new Map();
  const tokens = [];
  const unsure = [];
  for (const row of kept) {
    const stem = row.role ? `${row.group}.${row.role}` : row.group;
    const rank = (ranks.get(stem) ?? 0) + 1;
    ranks.set(stem, rank);
    // A rank joins its stem with `.` where the stem is a group (`radius` → `radius.1`) and with `-`
    // where it is a role (`color.text` → `color.text-1`). Both compile to the same custom property —
    // `--radius-1`, `--color-text-1` — so this is purely about the DTCG file being coherent: with `-`
    // throughout, `radius-1` sat at the top level as a *sibling* of the `radius` group holding the
    // source's own `--radius-md`, which reads as two unrelated things.
    const path = row.role ? `${stem}-${rank}` : `${stem}.${rank}`;
    // The round trip again, and it can genuinely fail here: a group is this module's own string, but a
    // role is not, and a future entry with a dot or a space in it would rename itself at compile time.
    if (customPropertyFor(path) !== `--${path.replace(/\./g, "-")}`) {
      unsure.push({ name: path, reason: "name", detail: "the generated path does not round-trip" });
      continue;
    }
    tokens.push({
      path,
      value: row.value,
      type: row.type ?? typeOf(row.value),
      source: "recurring",
      // SH831 — carried rather than left to be re-parsed out of `path`. The declared rows now carry
      // one too, so a reader that wants "the colour this site uses as text" asks both the same way.
      // SH834 — and the group beside it, for the same reason one group along.
      group: row.group,
      ...(row.role ? { role: row.role } : {}),
      page: row.page === true,
      refs: row.refs,
      properties: [...row.properties].sort(),
    });
  }
  return { tokens, unsure };
}

function bucketFor(declaration) {
  const { property, value } = declaration;
  // SH845 — and not when it is fully transparent. See `isTransparent`.
  if (COLOR_ROLES[property] && isColor(value) && !isTransparent(value)) {
    return { group: "color", role: COLOR_ROLES[property], type: "color" };
  }
  for (const entry of LENGTH_GROUPS) {
    if (entry.properties.includes(property) && isLength(value)) {
      return { group: entry.group, role: null, type: entry.type };
    }
  }
  for (const entry of OTHER_GROUPS) {
    // No value test: a font stack and a shadow have no single canonical form worth asserting, and
    // recurrence is the whole evidence anyway — a `box-shadow` written eight times identically is a
    // decision somebody made once.
    //
    // SH839 — one exception, and it is the only value a *recurring* token can never be. See
    // `isPointer`: the colour and length groups already refuse it through their own tests, so this
    // is where the rule has to be spelled and the only group it changes.
    if (entry.properties.includes(property) && !isPointer(value)) {
      return { group: entry.group, role: null, type: entry.type };
    }
  }
  return null;
}

/**
 * A value that is <b>nothing but</b> a reference to another custom property (SH839).
 *
 * <h3>Why a pointer cannot be a recurring token</h3>
 *
 * <p>A recurring token's whole premise is *a literal this stylesheet repeats*, extracted so a curator
 * can change it in one place. A `var(--font-body)` is already somebody's token: writing it into
 * `Theme.TOKENS` produces `--font-family-1: var(--font-body)`, a field whose value is a pointer and
 * which edits into nothing — the source's own token is the one that moves the page.</p>
 *
 * <p>Measured on `theme-heavy`, where SH834's page-level exemption admitted exactly this and the
 * comment on that exemption claimed a blast radius of zero. It said so because the measurement behind
 * it filtered for values holding no `var(` — the right filter for a length group, whose `isLength`
 * already refuses one, and the wrong one for `font.family`, which has no value test and says so two
 * lines up.</p>
 *
 * <h3>Nothing but, and not merely containing</h3>
 *
 * <p>`0 1px 2px var(--shadow-color)` stays a token. It is a value the source composed, most of it
 * literal, and refusing it would lose a real shadow to a rule about a pointer. The distinction is
 * exact rather than cautious: a pointer resolves to one other token and carries no decision of its
 * own, while a composed value carries several and happens to interpolate one.</p>
 */
function isPointer(value) {
  return /^\s*var\(\s*--[\w-]+\s*(,[\s\S]*)?\)\s*$/.test(String(value ?? ""));
}

/** `#0000`, `#00000000`, and the zero-alpha spellings of the colour functions. */
const TRANSPARENT = /^(?:#(?:[0-9a-f]{3}0|[0-9a-f]{6}00)|(?:rgba?|hsla?|hwb|lab|lch|oklab|oklch)\([^)]*[/,]\s*(?:0|0?\.0+|0%)\s*\))$/i;

/**
 * A colour that paints nothing (SH845).
 *
 * <h3>Why a recurring token can never be one</h3>
 *
 * <p>A recurring token's premise is *a literal this stylesheet repeats, therefore this is the site's
 * palette*. `#0000` repeats more than any real colour on a utility sheet — it is what
 * `.bg-transparent` and every reset are written with — and it is the one value where repetition
 * argues the opposite of the premise: a colour declared to be absent is not a decision about what
 * the site looks like.</p>
 *
 * <p>Measured on `www.viglet.org`, where it was ranked `color.background-1` — the site's *page*
 * background, the first thing the bridge assigns and the largest area on the replica. That is the
 * defect at its worst: not a wrong colour, an invisible one, on the slot that decides whether the
 * page reads at all. The same argument as {@link isPointer} one value along, and refused in the same
 * place for the same reason.</p>
 *
 * <p>The source corpus holds <b>no</b> transparent colour at all, in any of its eight archetypes, so
 * what this refuses there is nothing — measured with a grep rather than asserted, which is the whole
 * of SH839's correction: a claim of zero cost that a run refutes is worse than no claim.</p>
 *
 * <p><b>Only the recurring half.</b> A source that declares `--overlay: transparent` has *said* so,
 * the token is editable, and refusing it would drop a name the source's own CSS references — which
 * is the loss SH424 exists to prevent. The rule is about what may be inferred from a count, not
 * about what a stylesheet is allowed to contain.</p>
 */
function isTransparent(value) {
  const raw = String(value ?? "").trim();
  return raw.toLowerCase() === "transparent" || TRANSPARENT.test(raw);
}

// ---------------------------------------------------------------------------------------------
// The DTCG tree
// ---------------------------------------------------------------------------------------------

/**
 * The token rows → the `TOKENS` field's JSON.
 *
 * <p>Nested by the dotted path, `$type` omitted where this reader would not claim one, and every
 * object key sorted so two runs over one stylesheet serialize identically (P5). A path that would
 * have to overwrite a *group* with a token, or a token with a group, is dropped and named: DTCG has
 * no representation for both, and picking one would make the other silently disappear.
 *
 * <h2>SH428 — the collision was self-inflicted, and the flat name resolves it</h2>
 *
 * <p>Over `openviglet.github.io`'s palette this wrote nine of sixteen declared tokens and dropped
 * seven: `bg.alt`, `border.strong`, `brand.2`, `brand.3`, `brand.ink`, `brand.wash`, `brand.font`.
 * Detecting the collision was right — DTCG forbids it and writing anyway loses a token silently —
 * but *creating* it was the defect: `pathForCustomProperty` splits on every hyphen, so `--bg` becomes
 * `bg` and `--bg-alt` becomes `bg.alt`, and the first then has to be both a token and a group.
 *
 * <p>The round trip is the only hard rule here, and a **single-segment** path passes it:
 * `customPropertyFor("bg-alt")` is `--bg-alt`, the very property the source wrote. So a colliding row
 * falls back to its whole name as one segment — nothing guessed, nothing lost, and the fallback is
 * accepted only when it compiles to the *same* custom property the nested path would have. `--x` plus
 * `--x-y` is the most common naming scheme in hand-written CSS; here it cost the accent ramp
 * (`--brand-2` and `--brand-3` are the hero gradient and the primary button), which is SH210's hazard:
 * a curator replacing "the brand colour" replaces a quarter of it.
 *
 * <p>Rows are taken **shortest path first**, which is what makes the fallback always available: `bg`
 * is written as a token before `bg.alt` asks to nest under it, so the collision is discovered while a
 * flat name is still free. In the other order `bg` would find a group already there and have nowhere
 * to go. The emitted JSON is unaffected by the reordering — every key is sorted on the way out.
 */
export function dtcgFor(rows) {
  const root = {};
  const dropped = [];
  const kept = [];
  const shortestFirst = [...rows]
    .map((row, index) => ({ row, index, depth: String(row.path).split(".").length }))
    .sort((a, b) => (a.depth === b.depth ? a.index - b.index : a.depth - b.depth))
    .map((each) => each.row);
  for (const row of shortestFirst) {
    const segments = String(row.path).split(".");
    let node = root;
    let ok = true;
    for (const segment of segments.slice(0, -1)) {
      const next = node[segment];
      if (next && typeof next === "object" && !("$value" in next)) {
        node = next;
        continue;
      }
      if (next) {
        ok = false;
        break;
      }
      node[segment] = {};
      node = node[segment];
    }
    const leaf = segments.at(-1);
    if (!ok || (node[leaf] && !("$value" in node[leaf]))) {
      const flat = flatName(row.path);
      if (flat && !root[flat]) {
        root[flat] = { $value: row.value, ...(row.type ? { $type: row.type } : {}) };
        kept.push({ ...row, path: flat, nested: row.path });
        continue;
      }
      dropped.push({ name: row.path, reason: "collision", detail: "a group and a token cannot share one path" });
      continue;
    }
    node[leaf] = { $value: row.value, ...(row.type ? { $type: row.type } : {}) };
    kept.push(row);
  }
  return { tokens: sortKeys(root), dropped, rows: kept };
}

/**
 * Removes the declarations that became tokens from the kept CSS (SH424).
 *
 * <h2>The defect, measured on a real replica rather than argued</h2>
 *
 * <p>`shio propose` wrote nine declared tokens into `Theme.TOKENS`, and the rendered page carried
 * **two** `:root` blocks — the token-compiled one at char 1794 and the source's own at 2735, still
 * inside the `CSS` field SH380 deliberately keeps verbatim. Equal specificity, later wins, so all nine
 * were inert: a curator editing `brand` saw no change. Both halves are individually right, which is
 * why nothing caught it — SH380 refuses substitution because search-and-replacing `#ea580c` across a
 * stranger's stylesheet breaks a replica in a way no digest catches, and `ShThemeTokens` prepends so a
 * theme's own CSS can override the token block. Composed, the artefact's editability claim inverted
 * while the report stated it out loud.
 *
 * <h2>Why this is narrower than what SH380 forbids</h2>
 *
 * <p>A lifted property is re-emitted **byte-identically** by the compiled `:root`, so dropping that
 * one declaration preserves the computed value — unlike rewriting a literal into a `var()`, which
 * changes what the cascade computes. Only lifted declarations, never the block: the source's `:root`
 * also holds things like `color-scheme: light`, which is no custom property and must stay. A recurring
 * token is untouched, since nothing references it yet.
 *
 * <p>Removing every occurrence of a lifted name is safe because of what `declaredTokens` already
 * guarantees about one: it is declared **only** under a root selector (a variant occurrence
 * disqualifies it) and with **one** value (two values make it a conflict). So there is no occurrence
 * elsewhere that could carry a different value, and rule boundaries do not have to be reconstructed.
 *
 * <p>The exception is a value holding a `url()`. The kept CSS has been rewritten to the replica's own
 * path (SH461) and the token has not, so dropping the declaration would send the replica back to the
 * source's origin
 * for those bytes. Those stay, and stay **named** in a note: a token that cannot be edited is exactly
 * the claim this task exists to stop making silently.
 */
function dropLifted(css, rows) {
  let out = css;
  const removed = [];
  const shadowed = [];
  for (const row of rows) {
    const property = row.name ?? customPropertyFor(row.path);
    if (/url\(/i.test(String(row.value ?? ""))) {
      shadowed.push(property);
      continue;
    }
    // `[^;{}]*` cannot run past its own rule, and the terminator is consumed only when it is a `;` —
    // a declaration that ends at the closing brace leaves the brace where it is.
    const pattern = new RegExp(`\\s*${escapeForPattern(property)}\\s*:[^;{}]*(?:;|(?=\\}))`, "g");
    const before = out;
    out = out.replace(pattern, "");
    if (out !== before) removed.push(property);
  }
  return { css: out, removed, shadowed };
}

/** A property name as a literal in a pattern. Custom property names may hold regex metacharacters. */
function escapeForPattern(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/**
 * A colliding path as one flat segment, or null when that would rename the property (SH428).
 *
 * <p>The equality is the whole safety of the fallback: `bg.alt` and `bg-alt` both compile to
 * `--bg-alt`, so writing the flat one keeps every `var(--bg-alt)` in the captured CSS working. A path
 * where the two spellings differ is not a name this reader may invent, and it is dropped and reported
 * exactly as before.
 */
function flatName(path) {
  const flat = String(path).replace(/\./g, "-");
  return customPropertyFor(flat) === customPropertyFor(path) ? flat : null;
}

function sortKeys(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return value;
  const out = {};
  // Code-unit order, deliberately. A linter will suggest `localeCompare` here and it is the one
  // change to refuse: locale-aware collation makes the byte order depend on the machine's locale, so
  // the same capture would serialize differently on two developers' laptops — the exact property P5
  // asks for, undone by a suggestion that reads like a fix.
  for (const key of Object.keys(value).sort()) out[key] = sortKeys(value[key]);
  return out;
}

// ---------------------------------------------------------------------------------------------
// url() — what makes the theme portable rather than a set of links to somebody else's origin
// ---------------------------------------------------------------------------------------------

/**
 * A `url()` call.
 *
 * <p>The bare branch is `+` rather than `*` for the same reason as `LENGTH` above: an alternative that
 * can match **empty** sits between two `\s*`, so on a `url(` with no closing paren the engine tries
 * every position in the whitespace run. `url()` with nothing in it then does not match, which is the
 * correct outcome anyway — there is no reference to rewrite.
 */
const URL_CALL = /url\(\s*(?:"([^"]*)"|'([^']*)'|([^)'"\s]+))\s*\)/gi;

/**
 * Rewrites every `url()` in a stylesheet to **the path the source used**, once SH378 has the bytes.
 *
 * <p>Which is the difference between a portable theme and one that renders by fetching from the site
 * it was copied from — including the case where that site is taken down, and the case where it starts
 * serving something else. A reference the asset map does not hold is **left exactly as it was and
 * reported**: an off-origin webfont is the common one, and SH378's rule is that mirroring somebody
 * else's bytes is a licensing decision this tool does not make. Rewriting it to a path with no bytes
 * behind it would turn that into a broken replica instead of a named gap.
 *
 * <h3>Why the source's path and not the tree path (SH461)</h3>
 *
 * <p>This emitted `/<site>/<chain>/<name>` — the asset tree's own layout, site segment and all —
 * until a replica's fonts 404'd on a route that serves them. That path is addressable in `shio/assets/`
 * and nowhere else: the theme's CSS is served by the **instance**, and what the instance resolves is
 * a site-relative path (`ShSiteAssets` walks the folder chain from the site root). The source's own
 * path is exactly that, which is why the markup — kept verbatim, never rewritten here — has worked
 * since SH437 while this did not. So one page carried two rules for one job; this is the second one
 * deleted, not a third one added. The asset map stays the gate (a reference nothing downloaded is
 * still left alone and named); only what gets written changes.</p>
 *
 * <p>`data:` is skipped rather than looked up — it is already portable, and a 40 KB base64 sprite in
 * the "unresolved" list would bury the three references somebody has to act on.
 */
export function rewriteUrls(css, { base, assets = new Map() } = {}) {
  const unresolved = [];
  let rewritten = 0;
  const out = String(css ?? "").replace(URL_CALL, (match, dq, sq, bare) => {
    const raw = (dq ?? sq ?? bare ?? "").trim();
    if (!raw || /^(?:data|about):/i.test(raw) || raw.startsWith("#")) return match;
    const key = resolveAgainst(raw, base);
    const file = key === null ? undefined : (assets.get(key) ?? assets.get(raw));
    if (!file) {
      unresolved.push(raw);
      return match;
    }
    rewritten += 1;
    // `key` when the map answered on it, `raw` when it answered on the raw value — the same two
    // lookups the line above makes, so the emitted path is always the one the bytes were found by.
    return `url("${assets.has(key) ? key : raw}")`;
  });
  return { css: out, rewritten, unresolved: [...new Set(unresolved)].sort() };
}

/**
 * A `url()`'s key in the asset map: the path the inventory recorded, resolved against the stylesheet.
 *
 * <p>Relative to the **stylesheet**, not to the page — `url(../fonts/x.woff2)` in
 * `/assets/css/site.css` is `/assets/fonts/x.woff2`, and resolving it against the page that linked
 * the stylesheet would name a file that does not exist. The most common single defect in anything
 * that touches CSS urls, and the reason `base` is required rather than optional.
 */
/**
 * Whether an unresolved `url()` will 404 on the replica (SH452).
 *
 * <p>The question is what the reference resolves *against*. An absolute URL names a host and keeps
 * working; a protocol-relative `//host/x` names one too. Everything else — `/fonts/x.woff2`,
 * `../fonts/x.woff2`, `x.woff2` — resolves against the replica's own origin, which has no bytes at
 * that path, so it is broken rather than left alone. A document-relative reference is included for
 * the same reason as a root-relative one: in a theme the CSS has been concatenated out of its
 * original file, so "relative to what" no longer has the answer the source gave it.</p>
 */
function isRootRelative(raw) {
  const value = String(raw ?? "").trim();
  if (!value || value.startsWith("//")) return false;
  return !/^[a-z][a-z0-9+.-]*:/i.test(value);
}

function resolveAgainst(raw, base) {
  if (!base) return raw;
  try {
    const url = new URL(raw, base);
    const origin = new URL(base);
    if (url.host.toLowerCase() !== origin.host.toLowerCase()) return url.href;
    return `${url.pathname}${url.search}`;
  } catch {
    return raw;
  }
}

// ---------------------------------------------------------------------------------------------
// The theme proposal
// ---------------------------------------------------------------------------------------------

/**
 * Folds the captured stylesheets into one theme proposal.
 *
 * <p>Concatenated in inventory order — which is sorted by path, so two runs produce the same bytes —
 * with each file's own name in a comment, because the first question anybody asks of a 200 KB blob is
 * which file a rule came from. The declarations are pooled across files before the recurrence count
 * is taken: a palette split over `reset.css` and `theme.css` is one palette, and counting per file
 * would put every value below the threshold.
 *
 * @param {Array<{path: string, file: string, url: string, css: string}>} styles the downloaded CSS
 * @param {Map<string, string>} assets source path → tree path, from the SH378 download. It is the
 *   *gate* rather than the value — SH461 emits the source path, and this map says whether the bytes
 *   for it were actually captured
 */
export function buildTheme({ styles = [], assets = new Map(), minRefs = DEFAULT_MIN_REFS } = {}) {
  const declarations = [];
  const sheets = [];
  const unresolved = new Map();
  let css = "";
  let rewritten = 0;

  for (const style of styles) {
    const scan = scanCss(style.css);
    declarations.push(...scan.declarations);
    const rewrite = rewriteUrls(style.css, { base: style.url, assets });
    rewritten += rewrite.rewritten;
    for (const each of rewrite.unresolved) {
      unresolved.set(each, (unresolved.get(each) ?? 0) + 1);
    }
    css += `${css ? "\n" : ""}/* ${style.path} */\n${rewrite.css.trim()}\n`;
    sheets.push({
      path: style.path,
      file: style.file,
      // SH427 — where the bytes were, which is the one thing a reviewer cannot see from a token row.
      inline: style.inline === true,
      rules: scan.rules,
      bytes: scan.bytes,
    });
  }

  const declared = declaredTokens(declarations);
  const recurring = recurringTokens(declarations, { minRefs });
  const rows = [...declared.tokens, ...recurring.tokens];
  // SH428 — `written` is what the file actually holds, and the counts below are taken from it. The
  // note used to say "16 declared token(s) are the editable half" while its own list showed nine
  // written and seven not: the count was taken before the drop, so the note contradicted the artefact
  // it introduces. A number a reader can check against the rows beneath it has to come from them.
  const { tokens, dropped, rows: written } = dtcgFor(rows);
  const declaredWritten = written.filter((row) => row.source === "declared");
  const recurringWritten = written.filter((row) => row.source === "recurring");
  // SH424 — the declarations that were lifted come OUT of the kept CSS, or the tokens are inert.
  const lifted = dropLifted(css, declaredWritten);
  css = lifted.css;

  const notes = [];
  if (lifted.removed.length) {
    notes.push(
      `${lifted.removed.length} lifted declaration(s) were removed from the kept CSS, so the token `
        + "block is what sets them: it is prepended, and the source re-declaring the same property "
        + "later at equal specificity is what made every token inert",
    );
  }
  if (lifted.shadowed.length) {
    notes.push(
      `${lifted.shadowed.length} token(s) are still set by the kept CSS and editing them changes `
        + `nothing (${lifted.shadowed.join(" ")}): their value carries a url(), which the kept CSS has `
        + "rewritten to the replica's own path and the token has not — dropping the declaration would send the "
        + "replica back to the source's origin for those bytes",
    );
  }
  if (declaredWritten.length) {
    notes.push(
      `${declaredWritten.length} declared token(s) are the editable half: the source's own CSS says ` +
        `var() on each, so changing one changes the replica`,
    );
  }
  if (recurringWritten.length) {
    notes.push(
      `${recurringWritten.length} recurring token(s) name the palette and change nothing on their ` +
        `own — no rule references them until the CSS is rewired, which this does NOT do: a ` +
        `search-and-replace over a stranger's stylesheet breaks a replica in a way no digest ` +
        `catches. SH831: \`shio convert\` reads them, so on an authorable conversion they paint ` +
        `the theme the composed sections render with, and that run says which of its colours ` +
        `each one filled`,
    );
  }
  // SH427 — said out loud, because "16 declared tokens and no stylesheet in the tree" is a state a
  // reader will otherwise try to explain. It is also the note that says a diff against the source's
  // CSS is not available here: there is no file, only a page.
  if (sheets.length && sheets.every((sheet) => sheet.inline)) {
    notes.push(
      `every stylesheet read here is an inline <style> block (${sheets.length} page(s)) — critical `
        + "CSS, which is what a single-page site does. The tokens are the page's own custom "
        + "properties; there is no stylesheet file in the tree to diff a change against",
    );
  }
  // SH452 — two different outcomes, and the note used to report both as the harmless one.
  //
  // "left as it is — the theme still fetches them from the source's origin" is true of an
  // **absolute** URL and false of a **root-relative** one, which resolves against the *replica's*
  // origin, where nothing serves it. Measured: two webfonts, 200 at the source and 404 at the
  // replica, while this note told the reader they still worked. That is SH443's defect one surface
  // along — a message asserting an outcome nobody measured — so the two are counted apart and only
  // the one that keeps working is described as keeping working.
  const broken = [...unresolved.keys()].filter(isRootRelative).sort();
  const offOrigin = [...unresolved.keys()].filter((raw) => !isRootRelative(raw)).sort();
  if (broken.length) {
    notes.push(
      `${broken.length} url() reference(s) in the theme point at a path this replica does not ` +
        `serve, so they 404 — they are root-relative, so they resolve against the REPLICA's origin ` +
        `and not the source's. Re-run the capture to fetch them: shio clone <url> --assets --site ` +
        `<name> (first: ${broken.slice(0, 3).join(" ")})`,
    );
  }
  if (offOrigin.length) {
    notes.push(
      `${offOrigin.length} url() reference(s) are not in the asset map and were left as they are — ` +
        `these are absolute, so the theme still fetches them from the host they name ` +
        `(first: ${offOrigin.slice(0, 3).join(" ")})`,
    );
  }

  return {
    sheets,
    css,
    tokens,
    // The rows the file holds, with the path each was actually written under — a row whose nested
    // path collided carries the flat one it took, plus `nested` saying what it would have been. A
    // report keyed to a path the artefact does not contain is the same defect as a count taken
    // before a drop.
    rows: written,
    unsure: [...declared.unsure, ...recurring.unsure, ...dropped].sort((a, b) =>
      (a.name === b.name ? 0 : a.name < b.name ? -1 : 1)),
    urls: { rewritten, unresolved: [...unresolved.keys()].sort() },
    notes,
  };
}

/** The DTCG tree → dotted path to value, which is what `ShThemeTokens.parse` will produce. */
export function flatten(node, prefix = "", into = {}) {
  if (!node || typeof node !== "object") return into;
  if ("$value" in node) {
    into[prefix] = node.$value;
    return into;
  }
  for (const [key, child] of Object.entries(node)) {
    if (key.startsWith("$")) continue;
    flatten(child, prefix ? `${prefix}.${key}` : key, into);
  }
  return into;
}

/**
 * What the caller reads about the theme.
 *
 * <p>Bounded like every other report here, and the counts always cover the whole run. The two token
 * kinds are printed **separately** rather than in one list, because their power is different and a
 * merged list would imply otherwise — which is the only way this artefact can mislead somebody.
 */
export function describeTheme(theme, options = {}) {
  const limit = Number.isFinite(Number(options.limit)) ? Math.max(Number(options.limit), 0) : 20;
  const lines = [];
  const declared = theme.rows.filter((row) => row.source === "declared");
  const recurring = theme.rows.filter((row) => row.source === "recurring");
  lines.push(
    `theme: ${theme.sheets.length} stylesheet(s), ${theme.css.length} bytes of CSS kept — ` +
      `${declared.length} declared token(s), ${recurring.length} recurring, ` +
      `${theme.unsure.length} reported and not written`,
  );
  const section = (title, rows, render) => {
    if (!rows.length) return;
    lines.push(`  ${title} (${rows.length})`);
    for (const row of rows.slice(0, limit)) lines.push(`    ${render(row)}`);
    if (rows.length > limit) lines.push(`    … ${rows.length - limit} more in the proposal`);
  };
  section("declared", declared, (row) => `${row.path} = ${row.value}${row.type ? `  [${row.type}]` : ""}  (${row.name})`);
  section("recurring", recurring, (row) =>
    `${row.path} = ${row.value}${row.type ? `  [${row.type}]` : ""}  ${row.refs}×  on ${row.properties.slice(0, 3).join(", ")}`);
  section("not written", theme.unsure, (row) => `${row.name}  ${row.reason}: ${row.detail}`);
  if (theme.urls.rewritten || theme.urls.unresolved.length) {
    lines.push(
      `  url(): ${theme.urls.rewritten} pointed at this replica, ${theme.urls.unresolved.length} left ` +
        `pointing at the source`,
    );
  }
  for (const note of theme.notes) lines.push(`  note: ${note}`);
  return `${lines.join("\n")}\n`;
}
