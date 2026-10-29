/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * One post in a console listing (SH120) — {@code ShCdaPostSummary}'s eight components,
 * name for name, plus the draft axis.
 *
 * <p><b>What this replaced, and why it mattered.</b> The listing used to serialize the
 * {@code ShPostUnified} entity: every attribute, the folder, the folder's site, the
 * post-type with its whole field tree. A console browsing a folder of five posts was
 * reading kilobytes to render five rows, and — worse for law P5 — it was reading them under
 * a third set of names, so {@code title} was {@code title} but {@code postType} was
 * {@code shPostType.name} and {@code date} was next to eleven fields nobody rendered.</p>
 *
 * <p><b>Why it is a superset rather than the CDA record itself.</b> The CDA delivers
 * published content, so it has no way to say "draft". A console's entire job includes
 * saying that: {@code published}, {@code publishStatus} and {@code state} are what the
 * draft/published badges render from, and dropping them to reuse the record verbatim would
 * be alignment bought by breaking the console. {@code postTypeTitle} is the same kind of
 * addition — the CDA carries the post-type's API name because that is what a front end
 * routes on, and the browser shows the human label next to it.</p>
 *
 * <p>{@code thumbnail} (SH1026) is the byte path of the image a grid card shows: the post itself
 * when it is a {@code File} holding one, or else the first image it references. Absent when there
 * is none, and the card draws the kind's icon instead.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShConsolePostRow(String id, String title, String summary, String furl,
                               String postType, String locale, Instant date, Instant modifiedDate,
                               String postTypeTitle, boolean published, String publishStatus,
                               String state, String thumbnail, Boolean membersOnly) {

    /** The thirteen-field form, before SH1302 marked a members-only post: true or absent. */
    public ShConsolePostRow(String id, String title, String summary, String furl,
                            String postType, String locale, Instant date, Instant modifiedDate,
                            String postTypeTitle, boolean published, String publishStatus,
                            String state, String thumbnail) {
        this(id, title, summary, furl, postType, locale, date, modifiedDate, postTypeTitle, published,
                publishStatus, state, thumbnail, null);
    }
}
