/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.stereotype.Component;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * JSON plumbing for the blueprint format (SH92) — one tolerant mapper, one reason.
 *
 * <h2>Why not the application's {@code ObjectMapper}</h2>
 *
 * <p>A blueprint's post-type files are the canonical JSON {@code shio pull} writes, and
 * that serialization deliberately <em>omits</em> fields that are delivered rather than
 * authored — {@code managed} (SH66) is the standing example. Jackson 3 defaults
 * {@code FAIL_ON_NULL_FOR_PRIMITIVES} to true, so binding such a file to the
 * {@code ShPostTypeDef} record with the default mapper throws on the absent primitive.
 * The desired-state document has the same shape of problem: a blueprint never declares
 * {@code prune}, and {@code prune} is a primitive {@code boolean}.</p>
 *
 * <p>{@code ShExchangePackageCodec} solved this for the exchange format in exactly this
 * way and for exactly this reason (SH68). Sharing that decision here, in one place with
 * the reasoning attached, is cheaper than discovering it a third time — and it keeps the
 * <em>authoring</em> serialization free to stay minimal, which is what makes a
 * hand-written package legal in the first place.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShBlueprintCodec {

    private final ObjectMapper mapper = new ObjectMapper().rebuild()
            // An absent primitive falls back to the type default (false/0) instead of
            // being rejected — see the class note.
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build();

    /** Parses a package file into a tree. */
    public JsonNode readTree(InputStream in) throws IOException {
        return mapper.readTree(in);
    }

    /** Binds a tree to a record, tolerating fields the canonical form omits. */
    public <T> T convert(JsonNode node, Class<T> type) {
        return mapper.treeToValue(node, type);
    }

    /** A fresh, empty object node — for the merged desired-state document. */
    public tools.jackson.databind.node.ObjectNode object() {
        return mapper.createObjectNode();
    }

    /** A fresh, empty array node — SH395's merged `postTypes`, before it is known to be non-empty. */
    public tools.jackson.databind.node.ArrayNode array() {
        return mapper.createArrayNode();
    }
}
