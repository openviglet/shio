/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.exchange.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata for one static binary asset carried in an exchange package (SH68).
 * The bytes live inside the package under {@code files/<id>} (see
 * {@code ShExchangePackageCodec.FILES_DIR} / {@code resolveFile}); this record
 * is only the manifest entry. {@code fileName} preserves the original display
 * name and {@code contentType} the MIME type when known.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShExchangeFile(
        String id,
        String fileName,
        String contentType) {
}
