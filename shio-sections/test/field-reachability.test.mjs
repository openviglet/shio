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
 * The Java renderer emits `data-shio-field` so the Universal Editor can put a control on the
 * rendered element. A React section rendered by a headless host has to emit the same thing or the
 * field is read-only on the page — which is exactly SH503: rendered, and annotated by nothing.
 */
const isAnnotated = (field) =>
  SOURCE.includes(`data-shio-field="${field}"`) || SOURCE.includes(`shioField="${field}"`);

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

test("SH524: which rendered fields carry no editing annotation, held as a list", () => {
  const unannotated = sectionFields().filter((entry) => {
    const field = entry.split(".")[1];
    return isRendered(field) && !isAnnotated(field);
  });

  // SH503's defect, generalised and counted for the first time: **no** section field is annotated,
  // so every page composed from this package is rendered content a curator cannot edit in place.
  // Asserted as the whole set rather than as a number, so closing it one component at a time is a
  // diff that shrinks a visible list — and so that adding a section without annotations is the
  // same diff, in the other direction.
  assert.deepEqual(
    unannotated,
    sectionFields(),
    "Every section field is currently unannotated. This is the SH503 shape at package scale: " +
      "rendered, stored, and inert on the page. It is held here as the whole list rather than " +
      "fixed in passing, because emitting the annotation is a change to every component and to " +
      "what the host application is expected to do with it — a task, not a line.",
  );
});
