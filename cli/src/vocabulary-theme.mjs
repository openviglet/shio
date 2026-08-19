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
.shio-page{margin:0;padding-block:calc(var(--space-step) * 2);background:var(--color-surface);color:var(--color-ink);font-family:system-ui,-apple-system,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;font-size:var(--text-base);line-height:1.6;-webkit-font-smoothing:antialiased}
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
 */
export const PALETTE_BRIDGE = Object.freeze({
  ink: { role: "text", rank: 1 },
  "ink-muted": { role: "text", rank: 2 },
  surface: { role: "background", rank: 1 },
  "surface-sunk": { role: "background", rank: 2 },
  line: { role: "border", rank: 1 },
  accent: { role: "accent", rank: 1 },
});

/**
 * This theme's tokens with the captured palette's values in them, and what could not be filled.
 *
 * @param rows the extracted token rows (`proposal.theme.rows`), each carrying the `role` the source
 *             uses it in and the `refs` that argued for it
 * @returns `{tokens, mapped, unmapped}` — `mapped` is `{name, from, value}` per colour that took a
 *          captured value, `unmapped` the names that kept this file's default and the role each
 *          looked for. Both sorted by name, so two runs over one capture report identically (P5).
 */
export function vocabularyTokensFrom(rows) {
  const palette = paletteByRole(rows);
  const colors = {};
  const mapped = [];
  const unmapped = [];
  for (const [name, definition] of Object.entries(VOCABULARY_THEME_TOKENS.color)) {
    const want = PALETTE_BRIDGE[name];
    const row = want ? (palette.get(want.role) ?? [])[want.rank - 1] : undefined;
    const wanted = want ? `${want.role}#${want.rank}` : null;
    if (row) {
      colors[name] = { ...definition, $value: row.value };
      mapped.push({ name, from: row.path, role: wanted, value: row.value });
    } else {
      colors[name] = definition;
      unmapped.push({ name, role: wanted, value: definition.$value });
    }
  }
  const byName = (a, b) => (a.name < b.name ? -1 : 1);
  return {
    tokens: { ...VOCABULARY_THEME_TOKENS, color: colors },
    mapped: mapped.sort(byName),
    unmapped: unmapped.sort(byName),
  };
}

/**
 * The captured colour rows grouped by role, each group in the order the ranks name.
 *
 * <h3>A declared token is taken only where the source states its role at page level</h3>
 *
 * <p>A recurring row's role is the extractor's published contract — it saw the literal land in
 * `color:` or `background:` and ranked it by how often. A **declared** token's role is inferred from
 * the declarations that read it, and inference by count was measured wrong: on `theme-heavy`,
 * `--brand` is read by four `color:` rules and `--ink` by three, so the accent would have become the
 * replica's body copy. `body { color: var(--ink) }` is the one thing a stylesheet says without
 * ambiguity, so a declared row is admitted when, and only when, that is where it was used
 * (`row.page`). Everything else keeps this theme's default and is reported — mapping less is the
 * direction that cannot make a replica worse than the generic theme it replaced.</p>
 *
 * <p>Within a role, page-level declared rows come first, then the recurring ones in the order
 * `recurringTokens` already ranks by — most-referenced, ties on the value — so `text#1` means the
 * same thing on both sides of the join and two runs over one capture agree (P5).</p>
 */
function paletteByRole(rows) {
  const byRole = new Map();
  for (const row of rows ?? []) {
    if (row?.type !== "color" || !row.role || typeof row.value !== "string") continue;
    if (row.source === "declared" && !row.page) continue;
    const group = byRole.get(row.role) ?? [];
    group.push(row);
    byRole.set(row.role, group);
  }
  for (const group of byRole.values()) {
    group.sort((a, b) => {
      if ((a.source === "declared") !== (b.source === "declared")) {
        return a.source === "declared" ? -1 : 1;
      }
      if ((a.refs ?? 0) !== (b.refs ?? 0)) return (b.refs ?? 0) - (a.refs ?? 0);
      return a.value < b.value ? -1 : 1;
    });
  }
  return byRole;
}
