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

/**
 * Content Delivery API (CDA) post-type schema (SH11). The public, delivery-shaped
 * description of one content model — its API {@code name} plus the ordered list
 * of {@link ShCdaSchemaField}s a post of this type exposes under
 * {@code ShCdaPost.attrs}.
 *
 * <p>Exposing the content model on the CDA closes the loop between the dynamic,
 * runtime-configured post-types and the statically-typed JavaScript front: a
 * {@code shio-types} code-generation step turns each of these into a TypeScript
 * interface, so page authors get autocomplete on {@code post.attrs.*}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShCdaPostTypeSchema(
        String name,
        String title,
        String namePlural,
        String description,
        List<ShCdaSchemaField> fields) {
}
