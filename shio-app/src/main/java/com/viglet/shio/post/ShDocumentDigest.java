/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.post;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH1058 — a digest of what a post's document <em>says</em>, not of how its bytes are spelled.
 *
 * <p>A translation records the digest of the source it was translated from, and "the source has
 * changed since" is this value compared with the source's current one. So the one property that
 * matters is that the same content always hashes the same: a document re-serialized with its keys in
 * another order, which an unrelated write does, must not read as an edit, or every translation of a
 * page somebody merely re-saved would be reported stale.</p>
 *
 * <p>So the document is parsed and written back with every object's keys sorted, recursively, before
 * it is hashed. Array order is kept — a list of blocks in a different order is a different page.
 * A document that does not parse is hashed as its raw text, which still changes exactly when the
 * text does.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShDocumentDigest {

    private ShDocumentDigest() {
    }

    /**
     * @param document the post's {@code data} as stored
     * @return lowercase hex SHA-256, 64 characters; the digest of the empty string for null
     */
    public static String of(String document, ObjectMapper mapper) {
        String canonical;
        try {
            canonical = document == null || document.isBlank() ? ""
                    : mapper.writeValueAsString(sorted(mapper.readTree(document), mapper));
        } catch (JacksonException unreadable) {
            canonical = document;
        }
        return sha256(canonical);
    }

    private static JsonNode sorted(JsonNode node, ObjectMapper mapper) {
        if (node.isObject()) {
            List<String> names = new ArrayList<>();
            node.propertyNames().forEach(names::add);
            java.util.Collections.sort(names);
            ObjectNode out = mapper.createObjectNode();
            for (String name : names) {
                out.set(name, sorted(node.get(name), mapper));
            }
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = mapper.createArrayNode();
            node.forEach(item -> out.add(sorted(item, mapper)));
            return out;
        }
        return node;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Every JVM is required to provide SHA-256; this is unreachable.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
