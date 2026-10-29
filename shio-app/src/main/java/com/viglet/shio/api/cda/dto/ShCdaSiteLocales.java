/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * SH1123 — the languages a site is in, as a front end reads them to build a switcher.
 *
 * <p>Frozen public read contract (SH1): field names are public API. {@code defaultLocale} rather
 * than the stored document's {@code default}, because that is a Java keyword and a record
 * component is what {@code ShCdaTypeScriptMirrorTest} holds the SDK's mirror against — and it is
 * the name the agent's context pack already uses for the same fact.</p>
 *
 * @param defaultLocale the locale the site is written in, always one of {@code enabled}
 * @param enabled       the locales it offers, in the order a switcher lists them
 * @param fallback      locale to show instead when a page is missing in the one asked for; empty
 *                      when a missing page falls straight back to the default
 * @param declared      whether somebody chose this policy, or it was read off the published
 *                      content — a switcher built from a derived one lists what has been
 *                      translated so far, not what the site promises
 * @param labels        SH1287 — what the site's switcher calls each locale (SH1281), keyed by
 *                      enabled locales; null, and so absent from the wire, when the site set none,
 *                      which keeps an unlabelled site's response byte-identical
 */
@JsonInclude(Include.NON_NULL)
public record ShCdaSiteLocales(
        String defaultLocale,
        List<String> enabled,
        Map<String, String> fallback,
        boolean declared,
        Map<String, String> labels) {
}
