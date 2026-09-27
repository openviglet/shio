/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import type { ShFieldConstraints } from "@/models/post-type/post-type"

// SH1136 — out of field-constraints.tsx, which may export only components for fast refresh to work:
// this is a pure function of a field and a translator, and the post form is its only caller.
/**
 * SH1010 — the constraints as one sentence, for the form a curator types in.
 *
 * <p>The point of showing it is that the rule stops being a discovery on save. Advisory is included
 * and named as advice, because a recommendation the form does not mention is a number only the
 * report knows about.</p>
 */
export function constraintHint(field: { constraints?: ShFieldConstraints },
                               t: (key: string, values?: Record<string, unknown>) => string):
                               string | undefined {
  const rules = field.constraints
  if (!rules) return undefined
  const parts: string[] = []
  if (rules.minLength != null) parts.push(t("content.hintMinLength", { n: rules.minLength }))
  if (rules.maxLength != null) parts.push(t("content.hintMaxLength", { n: rules.maxLength }))
  if (rules.min != null || rules.max != null) {
    parts.push(t("content.hintRange", { min: rules.min ?? "–", max: rules.max ?? "–" }))
  }
  if (rules.values?.length) parts.push(t("content.hintValues", { values: rules.values.join(", ") }))
  if (rules.minItems != null) parts.push(t("content.hintMinItems", { n: rules.minItems }))
  if (rules.maxItems != null) parts.push(t("content.hintMaxItems", { n: rules.maxItems }))
  if (rules.unique) parts.push(t("content.hintUnique"))
  // The pattern is shown as its own sentence, never as the expression: the message is the part a
  // person can act on, and a field with a pattern and no message has nothing to say here.
  if (rules.patternMessage) parts.push(rules.patternMessage)
  if (rules.recommendedLength != null) {
    parts.push(t("content.hintRecommended", { n: rules.recommendedLength }))
  }
  return parts.length > 0 ? parts.join(" · ") : undefined
}
