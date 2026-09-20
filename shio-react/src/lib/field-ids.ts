/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * The ids a post-editor field's parts use to point at each other.
 *
 * <h2>Why they live in `lib` rather than beside the form</h2>
 *
 * <p>Three readers need them and only one of them is the form: the form mints them, the insights
 * panel scrolls to one (SH1013), and every widget puts one on its own control (SH1052). A scheme
 * written twice is one that drifts, and the drift is silent — a button that scrolls nowhere, a
 * label that focuses nothing. `post-form-fields.tsx` exported them until SH1052 needed a second
 * one, at which point the file was exporting two constants beside a component and Fast Refresh
 * said so; the rule's own advice is this file.</p>
 *
 * <h2>Deterministic, and that is not a nicety</h2>
 *
 * <p>`useId()` is React's answer and it mints a value that differs between renders of different
 * trees, so two renders of the same post would differ in their markup — and a render digest
 * (SH687) compares markup. A field name is already unique within a post type, so the whole scheme
 * is one function of it.</p>
 */

/**
 * SH1013 — the id a finding's "go to field" resolves against.
 *
 * <p>The wrapper, not the control: what a curator wants to see after pressing it is the field's
 * label, its description and its box, and scrolling to the input alone puts the label off-screen.</p>
 */
export function fieldAnchorId(name: string): string {
  return `sh-field-${name}`;
}

/**
 * SH1052 — the three ids a field's label, control and prose share.
 *
 * <p>The label owns the control (`htmlFor` → `control`), the control points back at its prose
 * (`aria-describedby` → `description`), and a widget with no single control names itself by the
 * label instead (`aria-labelledby` → `label`).</p>
 *
 * <p>A field with no description must pass `undefined` rather than an id pointing at nothing:
 * `aria-describedby` naming a missing element is announced as nothing, which is indistinguishable
 * from the defect SH1052 closes.</p>
 */
export function fieldIds(name: string) {
  const base = fieldAnchorId(name);
  return {
    control: `${base}-control`,
    label: `${base}-label`,
    description: `${base}-description`,
  };
}
