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
 * @returns `{tokens, mapped, unmapped}` — `mapped` is `{name, from, role, value}` per slot that took
 *          a captured value, `unmapped` the slots that kept this file's default and what each looked
 *          for. Both sorted by name, so two runs over one capture report identically (P5). A slot
 *          the bridge does not name is in neither list: it has no source to take from, which is not
 *          the same fact as a source that did not state one.
 */
export function vocabularyTokensFrom(rows) {
  const palette = paletteByGroup(rows);
  const tokens = structuredClone(VOCABULARY_THEME_TOKENS);
  const mapped = [];
  const unmapped = [];
  for (const [name, want] of Object.entries(PALETTE_BRIDGE)) {
    const [family, leaf] = name.split(".");
    const definition = tokens[family]?.[leaf];
    if (!definition) continue;
    const candidates = (palette.get(want.role ? `${want.group} ${want.role}` : want.group) ?? [])
      .filter((row) => admits(want.needs, row));
    const row = candidates[want.rank - 1];
    const wanted = `${want.role ?? want.group}#${want.rank}`;
    if (row) {
      tokens[family][leaf] = { ...definition, $value: row.value };
      mapped.push({ name, from: row.path, role: wanted, value: row.value });
    } else {
      unmapped.push({ name, role: wanted, value: definition.$value });
    }
  }
  const byName = (a, b) => (a.name < b.name ? -1 : 1);
  return { tokens, mapped: mapped.sort(byName), unmapped: unmapped.sort(byName) };
}

/** Whether a row clears the bar its slot's group sets — the three rules the bridge's doc states. */
function admits(needs, row) {
  if (needs === "page-only") return row.page === true;
  // A recurring row's role is the extractor's own contract, so it is admitted at its own rank
  // whatever the rule; only the *declared* half, whose role is inferred, has to clear `page`.
  if (needs === "page") return row.source !== "declared" || row.page === true;
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
      if ((a.page === true) !== (b.page === true)) return a.page === true ? -1 : 1;
      if ((a.refs ?? 0) !== (b.refs ?? 0)) return (b.refs ?? 0) - (a.refs ?? 0);
      return a.value < b.value ? -1 : 1;
    });
  }
  return byKey;
}
