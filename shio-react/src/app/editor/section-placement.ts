/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1105 — where a new section goes in the Page's array, for every way of saying where.
 *
 * Above, Below, At end and a drop on the caret are four ways of naming one number, and this is the
 * one arithmetic behind all of them: a drop must write the array the Below button writes, so the two
 * cannot each carry a splice of their own. Pure, so `section-placement.test.mjs` holds it.
 */

/** A box in the framed viewport's coordinates, as the bridge sends it. */
export type PlacementRect = { top: number; left: number; width: number; height: number }

/** The index a button names: before or after the selected section, or last with no selection. */
export function buttonIndex(selected: number | null, after: boolean, length: number): number {
  return selected === null ? length : selected + (after ? 1 : 0)
}

/**
 * The gap a pointer at `y` is nearest: `0` is before the first section and `boxes.length` after the
 * last. A section's midpoint is the boundary, so the upper half of section `i` is gap `i` and its
 * lower half is gap `i + 1` — the same number Above and Below name with `i` selected.
 */
export function gapIndex(boxes: PlacementRect[], y: number): number {
  const index = boxes.findIndex((box) => y < box.top + box.height / 2)
  return index === -1 ? boxes.length : index
}

/** Where the caret for gap `at` is drawn: between the two boxes that border it, or at an outer edge. */
export function caretY(boxes: PlacementRect[], at: number): number | null {
  if (!boxes.length) return null
  if (at <= 0) return boxes[0].top
  if (at >= boxes.length) return boxes[boxes.length - 1].top + boxes[boxes.length - 1].height
  const above = boxes[at - 1]
  return (above.top + above.height + boxes[at].top) / 2
}

/** The array with `reference` placed at `at`, clamped into range; the input is never mutated. */
export function placeAt(entries: unknown[], reference: unknown, at: number): unknown[] {
  const next = [...entries]
  next.splice(Math.max(0, Math.min(at, next.length)), 0, reference)
  return next
}
