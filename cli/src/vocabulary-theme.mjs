/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
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
.shio-stack > * + *{margin-block-start:var(--space-step)}
.shio-grid{display:grid;gap:var(--space-step);grid-template-columns:repeat(auto-fit,minmax(min(100%,16rem),1fr));list-style:none;margin:0;padding:0}
.shio-card{background:var(--color-surface-sunk);border:1px solid var(--color-line);border-radius:var(--size-radius);padding:var(--space-step)}
.shio-card > * + *{margin-block-start:calc(var(--space-step) / 2)}
.shio-card cite{display:block;font-style:normal;color:var(--color-ink-muted)}
.shio-card h3{font-size:var(--text-h3);line-height:1.3;margin:0}
.shio-card p{margin:0}
.shio-media{margin:0}
.shio-media img{display:block;border-radius:var(--size-radius)}
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
