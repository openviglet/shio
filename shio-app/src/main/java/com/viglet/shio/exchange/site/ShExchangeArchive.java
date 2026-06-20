/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.exchange.site;

import java.util.Arrays;
import java.util.Objects;

/**
 * A built exchange archive ready to stream (SH69): the suggested download
 * {@code fileName} (e.g. {@code my-site.zip}) and the zipped package bytes.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShExchangeArchive(String fileName, byte[] content) {

    // Records compare/hash array components by reference by default (S6218); the zipped
    // bytes are the payload, so compare them by content.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ShExchangeArchive(String otherFileName, byte[] otherContent))) {
            return false;
        }
        return Objects.equals(fileName, otherFileName) && Arrays.equals(content, otherContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, Arrays.hashCode(content));
    }

    @Override
    public String toString() {
        return "ShExchangeArchive[fileName=" + fileName
                + ", content=" + (content == null ? "null" : content.length + " bytes") + "]";
    }
}
