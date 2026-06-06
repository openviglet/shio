/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type.dto;

import java.util.List;

/**
 * Lossless, round-trippable description of a post-type's content model (SH16) —
 * the unit the {@code shio} CLI {@code pull}s into version control and
 * {@code push}es back. It is the authoring counterpart to the delivery-only
 * {@code ShCdaPostTypeSchema} (SH11).
 *
 * <p>The {@code system} flag is delivered on {@code pull} (so operators can see
 * which types are read-only) but is honoured, not trusted, on {@code push}: the
 * service refuses to create or overwrite a system post-type regardless of the
 * value sent.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShPostTypeDef(
        String name,
        String title,
        String namePlural,
        String description,
        String workflowPublishEntity,
        boolean system,
        List<ShPostTypeAttrDef> fields) {
}
