/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { formatDistanceToNow } from "date-fns"
import { ptBR } from "date-fns/locale"

/**
 * SH1203 — "há cerca de 1 mês" in a Portuguese console, not "about 1 month ago".
 *
 * <p>`formatDistanceToNow` speaks English unless it is handed a locale, and the three places the
 * console called it handed it none, so every relative date in the content browser, its table and the
 * review queue was English under Portuguese headings. The language is the i18n instance's, passed in
 * by the caller, so this stays a pure function a test can call.</p>
 */
export function relativeDate(value: string | number | Date | undefined | null, language: string | undefined): string {
  if (value === undefined || value === null || value === "") return ""
  try {
    return formatDistanceToNow(new Date(value), {
      addSuffix: true,
      locale: language?.toLowerCase().startsWith("pt") ? ptBR : undefined,
    })
  } catch {
    return ""
  }
}
