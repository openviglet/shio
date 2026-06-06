/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda.dto;

import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * Content Delivery API (CDA) write payload (SH10). A partial set of post-type
 * attribute changes keyed by attribute name, merged into the post's draft
 * {@code data} document. When {@link #publish()} is true the draft is promoted
 * to {@code PUBLISHED} in the same request (the Universal Editor "save &amp;
 * publish" action).
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShCdaPatch(
        Map<String, JsonNode> attrs,
        boolean publish) {
}
