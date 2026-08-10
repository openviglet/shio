import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

import { sectionPostTypes } from "../src/post-types.ts";

/**
 * SH524 — the model's own reachability, as an asserted list.
 *
 * `template-unknown-field` catches a template reading a field the type does not declare. Nothing
 * caught the inverse — **a field no template renders** — and that inverse produced two defects,
 * found by hand, weeks apart:
 *
 *  - `SectionFeatureGrid.features`, `SectionFaq.questions`, `SectionLogoWall.logos` and
 *    `SectionTestimonial.quotes` were declared, captured, stored and drawn nowhere: four of seven
 *    section types rendered as a bare heading (SH465).
 *  - `SectionHero.ctaHref` was captured, stored, rendered into an `href` and annotated by nothing,
 *    so a curator could rename a button and never repoint it (SH503).
 *
 * Both are modelling failures that surfaced as rendering bugs, and both were found by counting
 * annotations against fields **by hand**. This is `ShAgentP1ConformanceTest`'s idea — classify every
 * capability, hold the unreachable ones as a list that cannot drift without a reviewable diff —
 * pointed at the content model instead of the API surface.
 *
 * **The asserted-list shape matters more than the check.** A field nobody renders is sometimes
 * correct, so the answer is a list somebody signed rather than a refusal.
 */

const SOURCE = readFileSync(
  fileURLToPath(new URL("../src/components.tsx", import.meta.url)),
  "utf8",
);

/**
 * Types this package declares but does not draw, with the reason.
 *
 * `components.tsx` renders **sections**. A `Page` is composed by the host application from its
 * `sections` list, and `Menu`/`MenuItem` are drawn by the Java renderer's `{{#menu}}` helper — so
 * their fields being absent here is the design, not a gap. Named rather than filtered by a rule
 * like "starts with Section", because that rule would silently absorb a real section somebody
 * forgot to draw.
 */
const NOT_DRAWN_HERE = new Set(["Menu", "MenuItem", "Page"]);

/** Whether a component reads this field off its `data`, which is the only way it can draw it. */
const isRendered = (field) =>
  SOURCE.includes(`data.${field}`) || SOURCE.includes(`data["${field}"]`);

/**
 * Whether the rendered field carries an editing annotation.
 *
 * <b>This checked `data-shio-field` and that attribute does not exist in this product.</b> SH638
 * found it: the real vocabulary is `data-shio-prop` (the field) and `data-shio-prop-attr` (which
 * attribute carries it when the value is not the element's text), which is what
 * `cli/src/annotations.mjs` counts and what `ShRegionComposer` emits. The list this produced was
 * right by accident — nothing was annotated at all — so the instrument passed its own review while
 * asserting the wrong name, which is the failure mode an asserted list is supposed to prevent.
 *
 * Matched on the `editable(...)` helper rather than on the attribute text, because that is what the
 * components call and a raw attribute in this file would now be the anomaly worth failing on.
 */
const isAnnotated = (field) =>
  SOURCE.includes(`editable("${field}")`) || SOURCE.includes(`editable("${field}",`);

/**
 * Fields no attribute carries verbatim, so there is nothing for the editor to attach to.
 *
 * `SectionHero.align` is rendered as a *class token* (`shio-hero--center`), not as a value: the
 * class attribute also carries three other things, so annotating it would point the editor at a
 * string it must not rewrite. `columns` and `variant` are the contrast that makes this a real
 * distinction rather than an excuse — both are layout switches too, and both land verbatim in a
 * `data-` attribute, so both ARE annotated.
 *
 * The honest fix is a discrete attribute for it, which is a markup change with a class-name
 * contract behind it and does not belong inside this task.
 */
const NOT_CARRIED_VERBATIM = new Set(["SectionHero.align"]);

const sectionFields = () =>
  sectionPostTypes()
    .filter((def) => !NOT_DRAWN_HERE.has(def.name))
    .flatMap((def) => (def.fields ?? []).map((field) => `${def.name}.${field.name}`));

test("SH524: every declared section field is drawn by a component", () => {
  const unrendered = sectionFields().filter((entry) => !isRendered(entry.split(".")[1]));

  assert.deepEqual(
    unrendered,
    [],
    "A field nothing renders is dead content: it costs a curator a form control that changes " +
      "nothing, it costs a capture the work of extracting it, and it makes every count of 'how " +
      "authorable is this page' a lie. This list was four types long when SH465 found it by hand " +
      "(features, questions, logos, quotes) — an empty list is the instrument saying that class " +
      "of defect is closed, and a non-empty one names the field before anybody renders a page.",
  );
});

test("SH524: the types this package does not draw are declared, not inferred", () => {
  // The exemption has to be a signed list rather than a naming rule. "Starts with Section" would
  // have absorbed a real section somebody forgot to draw, which is the defect this file exists for.
  const declared = new Set(sectionPostTypes().map((def) => def.name));
  for (const name of NOT_DRAWN_HERE) {
    assert.ok(
      declared.has(name),
      `${name} is exempted here and no longer declared — an exemption for a type that does not ` +
        `exist is a line nobody will remove, and the next reader will trust it.`,
    );
  }
});

test("SH524/SH638: every rendered field carries an editing annotation", () => {
  const unannotated = sectionFields().filter((entry) => {
    const field = entry.split(".")[1];
    return isRendered(field) && !isAnnotated(field) && !NOT_CARRIED_VERBATIM.has(entry);
  });

  // Was 25 of 25 when SH524 counted it: rendered, stored, and inert on the page — a conversion's
  // authorable mode delivering a model the Universal Editor could not reach, which puts the curator
  // back in the console form the visual editor exists to remove. SH638 closed it.
  assert.deepEqual(
    unannotated,
    [],
    "A field the page renders and does not annotate is one a curator can see and cannot edit — " +
      "SH503's defect, which was found by counting annotations against fields by hand.",
  );
});

test("SH638: the one field no attribute carries is declared, not silently skipped", () => {
  // A signed list of one, for the reason NOT_DRAWN_HERE is a list and not a rule: an exemption
  // nobody can see is an exemption that grows.
  for (const entry of NOT_CARRIED_VERBATIM) {
    assert.ok(
      sectionFields().includes(entry),
      `${entry} is exempted and no longer a declared field — an exemption for something that does `
        + `not exist is a line nobody removes and the next reader trusts.`,
    );
  }
});

test("SH638: a relator's rows are not annotated, and that is declared", () => {
  // The honest limit. A relator row is its own post and the editor needs that post's id to write
  // to it; `rows()` receives plain objects off the wire with no id in them. Annotating them anyway
  // would be worse than leaving them: the editor would attach and have nowhere to save (SH475's
  // shape). So the collection carries the annotation and the rows do not, and this says so rather
  // than leaving a reader to infer it from an absence.
  assert.match(
    SOURCE,
    /A relator row is its own post/,
    "the reason rows are unannotated is written where somebody changing this file will read it",
  );
});
