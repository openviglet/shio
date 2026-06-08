/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile.transform;

/**
 * The output of a {@link ShImageTransformPipeline}: the transformed bytes plus the
 * content type they should be served as (which differs from the source when a
 * {@code format} conversion happened — SH41, e.g. jpeg → webp).
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShImageTransformResult(byte[] bytes, String contentType) {
}
