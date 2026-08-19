/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH509 — the theme the section vocabulary never had.
 *
 * SH507 decided a converted page is presented by a **Shio** theme rather than a ruin of the source's,
 * and SH508 gave that theme a tree to lay out. This is the stylesheet: one small one, written against
 * SH508's roles, so a curator who opens the Universal Editor on a converted page sees a page.
 *
 * ## Small on purpose
 *
 * Type scale, measure, vertical rhythm, a card, a button, and two colour roles — **surface** and
 * **ink** — each with the one companion a card and a caption need, plus the accent a button is and
 * the line a card is bounded by. Not a design system competing with a front end's: the answer to "a
 * curator opened the editor and needs to see a page".
 *
 * ## Content, not a file
 *
 * These bytes become a `Theme` **post** in the conversion document, exactly the way the captured
 * theme already does. A stylesheet compiled into the jar would be the one part of a converted site
 * nobody can change, which is the shape SH183 rejected when it made the render model content — so
 * this is editable by the same curator afterwards, and a site that has its own theme overrides it by
 * naming that one on the layout.
 *
 * SH509's rationale names a *blueprint* as the shipping vehicle. It is a post here instead, for a
 * reason worth stating rather than quietly working around: `convert` writes a desired-state document
 * and has no way to apply a blueprint, so a converted site would render themeless until somebody
 * applied one by hand — and a blueprint package **plus** the constant `convert` needs would be two
 * copies of one stylesheet, which is the drift P5 exists to prevent. What the rationale is actually
 * binding on — content rather than a file, editable, overridable — is satisfied by the post.
 *
 * ## Two things it must not do
 *
 * It must not **assume a section type**. SH508's names are roles, and a theme that styled
 * `.shio-section-SectionHero` specially would break the eighth type that ships. The only per-section
 * selectors here read `data-shio-align`, `data-shio-variant` and `data-shio-columns` — which are
 * *fields* of the vocabulary, so a new type that declares one inherits the styling rather than
 * needing a rule.
 *
 * SH556 — and the other half of that, which was missing: it must **define every class the templates
 * draw**. It defined thirteen and they drew sixteen more, so SH521's lint fired on all eleven
 * authorable pages of a converted site and said so accurately. Growing this file by sixteen would
 * have made it a component stylesheet and broken the rule above from the other end; the templates
 * dropped the sixteen instead, because every one of them duplicated a `data-shio-*` handle already
 * on the same element. `convert.test.mjs` now holds both directions — no role unstyled, and no class
 * drawn that is not a role — so the pair can no longer drift.
 *
 * And it must not be inlined **alongside** the captured theme. Two stylesheets on one page make the
 * cascade depend on which came first, which is the accident SH424 spent a task removing. SH510 is
 * the half that stops sending the captured one on an authorable page.
 */

// SH846 — the colour arithmetic a floor needs. It lives beside the CSS reader that already owns
// `isColor`, so there is one place that decides what this project calls a colour.
import { contrastRatio, isTransparent, isTranslucent } from "./theme.mjs";

/**
 * The DTCG tree, which is what makes the theme *editable* rather than merely present.
 *
 * <p>`ShThemeTokens` compiles `color.ink` into `--color-ink` on `:root`, prepended to the CSS below,
 * so every value a curator is likely to want to change is a field edit in the console rather than a
 * stylesheet rewrite. That is the whole argument for the token layer (SH424), and a theme that
 * hard-coded its own colours would ship with the layer switched off.</p>
 */
export const VOCABULARY_THEME_TOKENS = Object.freeze({
  color: {
    ink: { $value: "#12151a", $type: "color", $description: "Text, and anything that reads as text." },
    "ink-muted": { $value: "#5a6373", $type: "color", $description: "A subheading, a caption, an attribution." },
    surface: { $value: "#ffffff", $type: "color", $description: "The page." },
    "surface-sunk": { $value: "#f4f6f9", $type: "color", $description: "A card, lifted off the page by being set into it." },
    line: { $value: "#dde3ec", $type: "color", $description: "Every border in the theme." },
    accent: { $value: "#0b5533", $type: "color", $description: "The one colour that means *act*: the button." },
  },
  size: {
    measure: { $value: "68ch", $type: "dimension", $description: "How wide a line of text is allowed to get." },
    radius: { $value: "10px", $type: "dimension" },
  },
  space: {
    step: { $value: "1.5rem", $type: "dimension", $description: "One unit of rhythm. Everything else is a multiple." },
  },
  text: {
    // SH834 — the stack `.shio-page` used to hardcode. A token, because the bridge cannot point at
    // a literal: a replica whose colours are the source's and whose type is the system font reads
    // as *nearly* the source, which is the shape a wrong value has over a missing one.
    family: {
      $value: "system-ui,-apple-system,\"Segoe UI\",Roboto,Helvetica,Arial,sans-serif",
      $type: "fontFamily",
      $description: "The stack the whole page is set in.",
    },
    base: { $value: "1.0625rem", $type: "dimension" },
    lead: { $value: "1.25rem", $type: "dimension" },
    h1: { $value: "2.5rem", $type: "dimension" },
    h2: { $value: "1.875rem", $type: "dimension" },
    h3: { $value: "1.125rem", $type: "dimension" },
  },
});

/**
 * The stylesheet, written against SH508's roles and nothing else.
 *
 * <p>Minified by hand rather than by a build step: it ships as the value of a content field, it is
 * read as a diff when a curator edits it, and adding a CSS toolchain to the CLI to save a few hundred
 * bytes would be the larger cost. One rule per line, so the diff stays readable.</p>
 */
export const VOCABULARY_THEME_CSS = `*,*::before,*::after{box-sizing:border-box}
img{max-width:100%;height:auto}
.shio-page{margin:0;padding-block:calc(var(--space-step) * 2);background:var(--color-surface);color:var(--color-ink);font-family:var(--text-family);font-size:var(--text-base);line-height:1.6;-webkit-font-smoothing:antialiased}
.shio-page > * + *{margin-block-start:calc(var(--space-step) * 2)}
.shio-container{width:100%;max-width:var(--size-measure);margin-inline:auto;padding-inline:var(--space-step)}
.shio-nav{width:100%;max-width:var(--size-measure);margin-inline:auto;padding-inline:var(--space-step)}
.shio-nav ul{display:flex;flex-wrap:wrap;gap:var(--space-step);list-style:none;margin:0;padding:0}
.shio-nav a{color:var(--color-ink-muted);font-weight:600;text-decoration:none}
.shio-nav a:hover,.shio-nav a:focus-visible{color:var(--color-accent);text-decoration:underline}
.shio-stack > * + *{margin-block-start:var(--space-step)}
.shio-grid{display:grid;gap:var(--space-step);grid-template-columns:repeat(auto-fit,minmax(min(100%,16rem),1fr));list-style:none;margin:0;padding:0}
.shio-card{background:var(--color-surface-sunk);border:1px solid var(--color-line);border-radius:var(--size-radius);padding:var(--space-step)}
.shio-card > * + *{margin-block-start:calc(var(--space-step) / 2)}
.shio-card cite{display:block;font-style:normal;color:var(--color-ink-muted)}
.shio-card h3{font-size:var(--text-h3);line-height:1.3;margin:0}
.shio-card p{margin:0}
.shio-section{margin:0}
.shio-media{margin:0}
.shio-media img,img.shio-media{display:block;border-radius:var(--size-radius)}
.shio-actions{margin:0}
.shio-button{display:inline-block;padding:0.7em 1.4em;border-radius:var(--size-radius);background:var(--color-accent);color:var(--color-surface);font-weight:600;text-decoration:none}
.shio-button:hover,.shio-button:focus-visible{filter:brightness(1.15)}
.shio-page-title{font-size:var(--text-h1);line-height:1.15;margin:0}
.shio-heading{font-size:var(--text-h2);line-height:1.2;margin:0}
.shio-subheading{font-size:var(--text-lead);color:var(--color-ink-muted);margin:0}
.shio-intro{margin:0}
.shio-body > * + *{margin-block-start:var(--space-step)}
[data-shio-align="center"] .shio-stack{text-align:center}
[data-shio-align="center"] .shio-grid{text-align:start}
[data-shio-variant="subtle"] .shio-button{background:transparent;border:1px solid var(--color-accent);color:var(--color-accent)}
@media (min-width:48em){
[data-shio-columns="2"] .shio-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
[data-shio-columns="3"] .shio-grid{grid-template-columns:repeat(3,minmax(0,1fr))}
[data-shio-columns="4"] .shio-grid{grid-template-columns:repeat(4,minmax(0,1fr))}
}
`;

/**
 * What the theme post is called, and where it lands.
 *
 * <p>`<site> (Shio)` beside `<site> (captured)`: a site converted both ways holds both, and a
 * `PageLayout` names the one it renders with by title. The suffix says whose theme it is, which is
 * the distinction SH507 made the mode's contract.</p>
 */
export const vocabularyThemeTitle = (site) => `${site} (Shio)`;
export const VOCABULARY_THEME_SLUG = "shio";

/**
 * Which extracted token feeds each of this theme's six colours (SH831).
 *
 * <h2>Three palettes on one page, and no bridge between any two</h2>
 *
 * <p>This is why an authorable replica looked nothing like its source, and it was not the theme being
 * generic on purpose. One served page carried three token vocabularies: this theme's — six colours,
 * read through eight `var(--color-*)` references — painting the composed regions; the source's own,
 * inside the captured sheet, painting the chrome; and `Theme.TOKENS`, the palette `propose` extracted
 * at capture time, **read by nothing**. `--color-accent` was a dark green while the site's accent was
 * orange, which is the whole visual mismatch in one line. `propose` said so and shipped it anyway:
 * *"12 recurring token(s) name the palette and change nothing on their own — no rule references them
 * until the CSS is rewired, which this does NOT do."*</p>
 *
 * <p>Nothing needs rewiring. The extractor already ranks each colour role by how many declarations
 * reference it (`recurringTokens`), so `color.text-1` is the colour the source writes as text most
 * often and `color.background-1` is its page. Pointing this theme's six names at those six is the
 * whole bridge: the sections keep their markup and their class names, and the page stops carrying two
 * accents.</p>
 *
 * <h2>The pairs, and the one that is weakest</h2>
 *
 * <p><b>`accent` is the least certain and the most visible.</b> The extractor's `accent` role is fed
 * by `fill`, `stroke`, `caret-color` and `text-decoration-color` — a real site's call-to-action is
 * usually a `background-color`, which lands in `background`. So a source with no SVG palette yields no
 * `color.accent-*` at all and keeps this theme's green. That is why an unmapped name is
 * <b>reported</b> rather than silently defaulted: the default is the right value to ship — a replica
 * with an unreadable button is worse than one with the wrong green — and it is the wrong thing to
 * ship <em>quietly</em>, because the reader cannot tell a colour that was matched from one that was
 * never looked for (P9).</p>
 *
 * <h2>SH834 — the other half of the palette, and the three admission rules</h2>
 *
 * <p>SH831 bridged colour and stopped, so a replica took the source's accent and kept this file's
 * font stack, corner radius and type scale with nothing saying it had. The extractor had already
 * bucketed all of it. What the rest needed was not more mapping but a rule per *kind of group*, and
 * the three are different because the groups are:</p>
 *
 * <ul>
 *   <li><b>`page`</b> — a group with <em>competing roles</em>. The same colour is text here and a
 *       background there, so only `body {{ color }}` separates *the* text colour from *a* text
 *       colour. Colour is the only such group.</li>
 *   <li><b>`rank`</b> — a group with <em>one role</em>. A value in `border-radius` is a corner
 *       radius wherever it appears and a `font-family` is the stack; there is nothing for a
 *       selector to disambiguate, so the most-referenced value is the answer.</li>
 *   <li><b>`page-only`</b> — a group that is a <em>scale</em>. `recurringTokens` ranks `font.size`
 *       by reference count, and the theme's five text steps are ordered by *size*: rank 2 is not
 *       `text.lead`. So the only step that can be taken is the one the page states outright, and
 *       the other four are refused rather than guessed.</li>
 * </ul>
 *
 * <p>`size.measure` and `space.step` are in no group at all, deliberately. A measure is a line
 * length almost no stylesheet declares, and a rhythm step is the *unit other spacing is a multiple
 * of* — which the commonest `padding` value is not. A slot the bridge does not name keeps its
 * default silently; a slot it names and cannot fill is reported. That distinction is the whole
 * difference between "this theme has nowhere to put that" and "the source did not say".</p>
 */
/**
 * The pairs the theme actually paints one on top of the other, and therefore the ones with a floor
 * (SH846).
 *
 * <h2>A mapping that assembled a pair neither side chose</h2>
 *
 * <p>The bridge fills each slot from the best row for *that* slot and never looked at two together,
 * so it could write a source surface under a default text colour — or the reverse. Measured: a source
 * declaring `--vg-background: #0000` and `--vg-foreground: #f9fafb` produced `--color-surface: #0000`
 * with near-white body copy on it, reported as *3 of 9 token(s) from the captured stylesheet*. Two
 * failures presented as two successes, and the page looked white only because the document behind it
 * was — the accident that survives review and breaks on a dark layout.</p>
 *
 * <p>The pair is what has a floor, not the row: `#f9fafb` is a perfectly good text colour and is only
 * wrong against *this* surface. And the failure does not need a bad source at all — a source stating a
 * dark page and no page-level text colour leaves this file's near-black default on it, which is the
 * likelier half and one no rule about the source's values could catch.</p>
 *
 * <h2>Which half gives way</h2>
 *
 * <p>Whichever came from the **source**, because the defaults are a pair chosen together and legible
 * by construction — so reverting always terminates, at worst at the default pair. Where both came from
 * the source the text gives way first: the surface is the larger area and the more certain reading
 * (`background#1` is what `body` states), and reverting it would throw away more on weaker evidence.
 * `color.ink` is settled before `color.ink-muted`, since both are painted on one surface and the
 * page's own text is the load-bearing half.</p>
 *
 * <h2>The bound, said rather than left to be discovered</h2>
 *
 * <p>Two pairs, not four. The button (`color.surface` on `color.accent`) and the card
 * (`color.ink` on `color.surface-sunk`) are the same relation and are deliberately not held here:
 * `color.accent` is the slot the bridge can least often fill at all (SH847), so a floor on it would
 * fire on the default almost every time and say nothing anybody can act on. They come back with the
 * task that makes accent reachable.</p>
 *
 * <p>SH848 is that task, and they do not come back with it: `color.surface` sits in three of the four
 * pairs, so a button judged after the page could revert a surface the page pair has already settled.
 * The order in which a shared slot may be reverted is a decision, not an array entry — <b>SH852</b>.</p>
 */
export const CONTRAST_PAIRS = Object.freeze([
  Object.freeze({ text: "color.ink", on: "color.surface" }),
  Object.freeze({ text: "color.ink-muted", on: "color.surface" }),
]);

/**
 * The ratio below which two colours are one colour (SH846).
 *
 * <p><b>Not a legibility grade.</b> WCAG's own numbers — 4.5 for body text, 3 for large text and UI —
 * grade a *design*, and this mapping is not entitled to grade the source's: `#999` on `#ffffff` is
 * 2.85, a caption colour a great many sites ship on purpose, and rejecting it would replace the
 * source's own muted grey with this file's. So the floor sits below every published grade and asks
 * only the question the rationale asks — is there any contrast at all. Near-black on near-black is
 * 1.1 and white on white is 1.0; both are refused, and every real design decision is admitted.</p>
 */
export const CONTRAST_FLOOR = 1.5;

export const PALETTE_BRIDGE = Object.freeze({
  "color.ink": { group: "color", role: "text", rank: 1, needs: "page" },
  "color.ink-muted": { group: "color", role: "text", rank: 2, needs: "page" },
  "color.surface": { group: "color", role: "background", rank: 1, needs: "page" },
  "color.surface-sunk": { group: "color", role: "background", rank: 2, needs: "page" },
  "color.line": { group: "color", role: "border", rank: 1, needs: "page" },
  "color.accent": { group: "color", role: "accent", rank: 1, needs: "page" },
  "size.radius": { group: "radius", role: null, rank: 1, needs: "rank" },
  "text.family": { group: "font.family", role: null, rank: 1, needs: "rank" },
  "text.base": { group: "font.size", role: null, rank: 1, needs: "page-only" },
});

/**
 * This theme's tokens with the captured palette's values in them, and what could not be filled.
 *
 * @param rows the extracted token rows (`proposal.theme.rows`), each carrying the `group` and — for
 *             colour — the `role` the source uses it in, the `refs` that argued for it, and whether
 *             any of those uses was at page level
 * @returns `{tokens, mapped, unmapped, rejected, unchecked}` — `mapped` is `{name, from, role, value}`
 *          per slot that took a captured value, `unmapped` the slots that kept this file's default and
 *          what each looked for, `rejected` (SH846) the slots that were **offered** a value and refused
 *          it, which is a different fact from being offered none. All sorted by name, so two runs over
 *          one capture report identically (P5). A slot the bridge does not name is in none of them: it
 *          has no source to take from, which is not the same fact as a source that did not state one —
 *          it is `unbridged` (SH849), counted rather than listed, because a reader given "0 of 9" over
 *          a theme with fifteen slots cannot tell whether six are missing from the report or from the
 *          theme, and that arithmetic is the same on every run.
 */
export function vocabularyTokensFrom(rows) {
  const palette = paletteByGroup(rows);
  const tokens = structuredClone(VOCABULARY_THEME_TOKENS);
  const mapped = [];
  const unmapped = [];
  const rejected = [];
  for (const [name, want] of Object.entries(PALETTE_BRIDGE)) {
    const [family, leaf] = name.split(".");
    const definition = tokens[family]?.[leaf];
    if (!definition) continue;
    const offered = (palette.get(want.role ? `${want.group} ${want.role}` : want.group) ?? [])
      .filter((row) => admits(want.needs, row));
    // SH846 — a value floor is a property of the row, so it filters the candidates the way `admits`
    // does rather than failing the slot: `#0000` is not a background this source states, it is the
    // absence of one, and the next row down is a better answer than the default.
    const candidates = offered.filter((row) => !refusedValue(want, row));
    const row = candidates[want.rank - 1];
    const wanted = `${want.role ?? want.group}#${want.rank}`;
    if (row) {
      tokens[family][leaf] = { ...definition, $value: row.value };
      mapped.push({ name, from: row.path, role: wanted, value: row.value });
    } else if (offered[want.rank - 1]) {
      // Refused, and not merely unfilled: **this slot's own rank** held a row and the floor took it.
      // Asking whether the filter removed anything at all would report `background#2` as refused on
      // a source that states one background — the slot was never offered a second, which is the
      // other fact entirely and the one this list exists to separate.
      // SH848 — which floor fired, because the two are different facts about the source and a
      // reader who cannot tell them apart cannot act on either: "it declared no colour there" and
      // "it declared one you can see through" call for different fixes.
      const refused = offered[want.rank - 1].value;
      const translucent = !isTransparent(refused);
      rejected.push({
        name, role: wanted, value: definition.$value,
        reason: translucent ? "translucent" : "transparent",
        detail: translucent
          ? `the source's ${wanted} is ${refused}, which lets the page behind it through`
          : `the source's ${wanted} is ${refused}, which paints nothing`,
      });
    } else {
      unmapped.push({ name, role: wanted, value: definition.$value });
    }
  }
  const unchecked = enforceContrast(tokens, mapped, rejected);
  const byName = (a, b) => (a.name < b.name ? -1 : 1);
  return {
    tokens,
    mapped: mapped.sort(byName),
    unmapped: unmapped.sort(byName),
    rejected: rejected.sort(byName),
    unchecked,
    unbridged: unbridgedSlots(),
  };
}

/**
 * The theme's slots {@link PALETTE_BRIDGE} names no source role for, sorted (SH849).
 *
 * <p>Derived from the two constants rather than typed, for the reason every count in this project is:
 * a number a reader can check against the artefact beneath it has to come from it. A measure is a line
 * length almost no stylesheet declares and a rhythm step is the unit other spacing is a multiple of;
 * four of the five text steps are a scale `recurringTokens` cannot order. Each keeps its default
 * **silently** and on purpose — the run states how many, never which, because the answer is the same
 * on every run and a constant list is the line everybody stops reading (P3).</p>
 */
function unbridgedSlots() {
  const named = new Set(Object.keys(PALETTE_BRIDGE));
  return Object.entries(VOCABULARY_THEME_TOKENS)
    .flatMap(([family, leaves]) => Object.keys(leaves).map((leaf) => `${family}.${leaf}`))
    .filter((slot) => !named.has(slot))
    .sort();
}

/**
 * Whether a row is a value the slot's kind may never take. See {@link CONTRAST_FLOOR}'s neighbour.
 *
 * <p>SH848 — <b>the floor belongs on the role, not on the value.</b> SH846 refused a colour that
 * paints nothing, which is a property of the value and therefore the same answer on every slot; what
 * it let through was `color-mix(in oklab, var(--vg-accent) 50%, transparent)` landing on
 * `--color-surface`, because 50% is not 0%. A surface at half alpha is not a surface — the page's
 * background is the one colour a replica cannot have the document showing through — while the same
 * value is a perfectly ordinary border, overlay or accent, and refusing it everywhere would throw
 * away decisions the source made on purpose.</p>
 */
function refusedValue(want, row) {
  if (want.group !== "color") return false;
  if (isTransparent(row.value)) return true;
  return want.role === "background" && isTranslucent(row.value);
}

/**
 * Holds {@link CONTRAST_PAIRS} to {@link CONTRAST_FLOOR}, reverting the source's half where they fail.
 *
 * <p>Mutates `tokens`, `mapped` and `rejected` in place, and returns the pairs it could not judge —
 * a value in a colour space this scanner does not parse ({@link parseColor}), which is reported
 * rather than passed. Runs after every slot is filled, because a pair is not a property of either
 * half and the two are decided independently by design.</p>
 */
function enforceContrast(tokens, mapped, rejected) {
  const unchecked = [];
  const valueOf = (name) => {
    const [family, leaf] = name.split(".");
    return tokens[family]?.[leaf]?.$value;
  };
  const revert = (name, against, ratio) => {
    const [family, leaf] = name.split(".");
    const at = mapped.findIndex((row) => row.name === name);
    if (at < 0) return false;
    const [row] = mapped.splice(at, 1);
    tokens[family][leaf] = { ...tokens[family][leaf], $value: defaultValue(name) };
    rejected.push({
      name, role: row.role, value: defaultValue(name), reason: "contrast",
      detail: `${row.value} from ${row.from} reads at ${ratio.toFixed(2)}:1 against `
        + `--${against.replace(".", "-")} ${valueOf(against)}, under the ${CONTRAST_FLOOR}:1 floor`,
    });
    return true;
  };
  for (const pair of CONTRAST_PAIRS) {
    let ratio = contrastRatio(valueOf(pair.text), valueOf(pair.on));
    if (ratio === null) {
      // Only where a captured value is in play: a default-on-default pair is known good, and a note
      // about one would be noise in the only report a curator reads about appearance.
      if (mapped.some((row) => row.name === pair.text || row.name === pair.on)) {
        unchecked.push({ text: pair.text, on: pair.on, value: valueOf(pair.text), against: valueOf(pair.on) });
      }
      continue;
    }
    if (ratio >= CONTRAST_FLOOR) continue;
    // The text first, then the surface — and never a default, which is why this terminates.
    if (!revert(pair.text, pair.on, ratio)) revert(pair.on, pair.text, ratio);
    ratio = contrastRatio(valueOf(pair.text), valueOf(pair.on));
    if (ratio !== null && ratio < CONTRAST_FLOOR) revert(pair.on, pair.text, ratio);
  }
  return unchecked.sort((a, b) => (a.text < b.text ? -1 : 1));
}

const defaultValue = (name) => {
  const [family, leaf] = name.split(".");
  return VOCABULARY_THEME_TOKENS[family][leaf].$value;
};

/** Whether a row clears the bar its slot's group sets — the three rules the bridge's doc states. */
function admits(needs, row) {
  if (needs === "page-only") return row.page === true;
  // A recurring row's role is the extractor's own contract, so it is admitted at its own rank
  // whatever the rule; only the *declared* half, whose role is inferred, has to clear `page`.
  // SH847 — or a reversal, which is the same *kind* of evidence the page bar asks for. The bar
  // exists because colour has competing roles and only a statement about the page settles which one
  // a token has; a rule that paints the page's own surface as its text is such a statement, made
  // about the element rather than about the page. A declared accent is otherwise refused twice over
  // — roled `background` and then failed for not being page-level — which is why it never arrived.
  //
  // SH848 — and a saturated fill is a third, made by the value. What the bar is really asking is
  // *has the competition between this token's possible roles been settled by something other than a
  // count*; a colour with real chroma in it, filling an element that is not the page, settles it
  // without needing a selector to. That is the half a reversal cannot reach: a shadcn button's own
  // foreground is a token of its own, not the page's surface, and the pair is in `oklch()`, which
  // this project's reader does not compare at all.
  if (needs === "page") {
    return row.source !== "declared" || row.page === true
      || row.reversal === true || row.saturated === true;
  }
  return true;
}

/**
 * The captured rows keyed by group — and by group plus role where the group has several.
 *
 * <h3>Why a declared colour needs a page-level statement and a declared radius does not</h3>
 *
 * <p>A recurring row's role is the extractor's published contract — it saw the literal land in
 * `color:` or `border-radius:` and ranked it by how often. A **declared** token's role is inferred
 * from the declarations that read it, and for colour inference by count was measured wrong: on
 * `theme-heavy`, `--brand` is read by four `color:` rules and `--ink` by three, so the accent would
 * have become the replica's body copy. `body {{ color: var(--ink) }}` is the one thing a stylesheet
 * says without ambiguity.</p>
 *
 * <p>That argument is about **competing roles**, so it does not carry to a group that has one: a
 * `--radius` read by three `border-radius` rules is the site's corner radius, and there is no rival
 * reading for a page-level selector to settle — `body {{ border-radius }}` would mean nothing. So
 * the bar is set per slot ({@link PALETTE_BRIDGE}'s `needs`) rather than here, and this only orders
 * the candidates.</p>
 *
 * <p>Within a key, declared rows come first — a name the *source* wrote, which SH424 lifts out of
 * the kept CSS so editing it in the console changes the replica — then the recurring ones in the
 * order `recurringTokens` already ranks by: most-referenced, ties on the value. So `text#1` means
 * the same thing on both sides of the join and two runs over one capture agree (P5).</p>
 *
 * <h3>SH840 — and a page-level row first within each half</h3>
 *
 * <p>Measured on a stylesheet built to ask exactly one question:</p>
 *
 * <pre>  body{{color:#111111}}    .a…​.e{{color:#333333}}   (five in all)
 *
 *   color.text-1  #333333  refs=5  page=false
 *   color.text-2  #111111  refs=1  page=true
 *   color.ink  &lt;-  #333333</pre>
 *
 * <p>The replica's body copy took the **card's** colour. That is SH831's own finding word for word
 * — *ranking a role by reference count picked the accent as the archetype's text colour, and
 * shipping it would have painted the replica's body copy teal* — reached on the half SH831 did not
 * touch. Its reason for leaving recurring rows to the extractor's ranking was sound at the time: the
 * ranking *is* the published contract, and a recurring row carried no better evidence. SH834 gave it
 * some, for the `text.base` rule, and nothing here sorted by it — so the flag was recorded on every
 * row and read for one slot.</p>
 *
 * <p>The **sort** is where it belongs and `admits` is not: a bar decides what may fill a slot at all,
 * and this decides which of several eligible rows fills it first. Declared-first stays the outer key,
 * so within the declared half — already filtered to page-level for colour — nothing moves.</p>
 */
function paletteByGroup(rows) {
  const byKey = new Map();
  for (const row of rows ?? []) {
    if (!row?.group || typeof row.value !== "string") continue;
    const key = row.role ? `${row.group} ${row.role}` : row.group;
    const group = byKey.get(key) ?? [];
    group.push(row);
    byKey.set(key, group);
  }
  for (const group of byKey.values()) {
    group.sort((a, b) => {
      if ((a.source === "declared") !== (b.source === "declared")) {
        return a.source === "declared" ? -1 : 1;
      }
      // SH847 — a reversal ranks with a page-level use, for the reason `admits` states: both are
      // statements about the page, and the accent role now takes rows from `fill` and from a
      // reversal alike. A button is what a site's accent is; an SVG fill is one of four properties
      // that happen to bucket there.
      // SH848 — a saturated fill ranks with them, and for the same reason: it is the third way the
      // competition between a colour's roles is settled by something other than a count.
      const stated = (row) => row.page === true || row.reversal === true || row.saturated === true;
      if (stated(a) !== stated(b)) return stated(a) ? -1 : 1;
      if ((a.refs ?? 0) !== (b.refs ?? 0)) return (b.refs ?? 0) - (a.refs ?? 0);
      return a.value < b.value ? -1 : 1;
    });
  }
  return byKey;
}
