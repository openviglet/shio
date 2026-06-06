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
 * Content Delivery API (CDA) editable post (SH10): the draft-preferred post
 * (frozen {@link ShCdaPost} contract) plus its post-type field schema. Backs the
 * Universal Editor properties panel, which needs both the current attribute
 * values and the per-field labels/widgets to render the full form.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShCdaEditablePost(
        ShCdaPost post,
        List<ShCdaField> fields) {
}
