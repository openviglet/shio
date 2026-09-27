/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * SH1133 — one row of a site's redirect table, as a front end that owns its URLs consults it.
 *
 * <p>Frozen public read contract (SH1): field names are public API. A {@code Redirect} post's source
 * is its own friendly URL (SH385), so {@code from} is that path and not a field of the post.</p>
 *
 * @param from      the site-relative path that moved, normalised the way the renderer matches it
 * @param to        where it moved: a site-relative path, or an absolute URL passed through as written
 * @param permanent 301 when true, 302 otherwise — SH385's default, because a wrong permanent
 *                  redirect is cached by browsers effectively for ever
 */
@JsonInclude(Include.NON_NULL)
public record ShCdaRedirect(
        String from,
        String to,
        boolean permanent) {
}
