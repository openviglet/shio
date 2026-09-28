/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/** The translator this needs: a key, and the values it interpolates. */
type Translate = (key: string, options?: Record<string, unknown>) => string

/**
 * The shape `ShContentAuditService.describe` writes: a verb, the subject in quotes, and optionally
 * a detail in parentheses — `Updated "Home" (renamed from "VigR3")`, `Created "menu" (in Home)`.
 * The title is matched greedily up to its last quote so a title that contains quotes survives.
 */
const SERVER_SENTENCE = /^([A-Z][a-z]+) "([\s\S]*)"(?: \(([\s\S]*)\))?$/

const RENAMED = /^renamed from "([\s\S]*)"$/
const IN_PARENT = /^in ([\s\S]+)$/

/** A title as a reader should see it: replicated content can carry a literal `&nbsp;`. */
const readable = (value: string) => value.replace(/&nbsp;/g, " ").trim()

/**
 * SH1202 — an audit row's sentence in the console's language.
 *
 * <p>The server stores each row's description as an English sentence at write time, and the Activity
 * page and the home page's recent activity printed it as it was: "Updated "About — Viglet"" under
 * Portuguese headings. Block AH swept the console's own strings, and these were never its own, so no
 * i18n scan could see them. The row carries its action, and the sentence's grammar is fixed, so the
 * verb, the subject and the two details the server writes are rebuilt from locale keys here.</p>
 *
 * <p>A sentence of any other shape is returned as it came, because a wrong translation is worse than
 * an English one: the reader can still read it.</p>
 */
export function activitySentence(description: string, action: string, t: Translate): string {
  const match = SERVER_SENTENCE.exec(description)
  if (!match) return readable(description)
  const [, , title, detail] = match
  const verb = t(`activity.verb.${action}`, { defaultValue: "" })
  if (!verb) return readable(description)
  const subject = readable(title)
  let tail = ""
  if (detail) {
    const renamed = RENAMED.exec(detail)
    const inParent = IN_PARENT.exec(detail)
    tail = renamed
      ? ` ${t("activity.detail.renamed", { name: readable(renamed[1]) })}`
      : inParent
        ? ` ${t("activity.detail.in", { name: readable(inParent[1]) })}`
        : ` (${detail})`
  }
  return `${verb} “${subject}”${tail}`
}
