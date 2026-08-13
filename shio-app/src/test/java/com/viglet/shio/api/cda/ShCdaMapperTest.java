/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.viglet.shio.api.cda.dto.ShCdaFolder;
import com.viglet.shio.api.cda.dto.ShCdaPost;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;

import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link ShCdaMapper} — the entity→DTO boundary that freezes the
 * CDA contract. Covers the malformed-{@code data} guard (acceptance criterion 5)
 * and the authoring-field exclusion (criterion 6).
 *
 * @author Alexandre Oliveira
 */
class ShCdaMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ShCdaMapper mapper = new ShCdaMapper(objectMapper);

    private ShPostUnified post(String data) {
        ShPostType postType = new ShPostType();
        postType.setName("PT");
        ShPostUnified post = new ShPostUnified();
        post.setId("p1");
        post.setState(ShPostState.PUBLISHED);
        post.setShPostType(postType);
        post.setTitle("T");
        post.setFurl("/t");
        post.setPublished(true);
        post.setOwner("admin");
        post.setPublisher("admin");
        post.setPublishStatus("PUBLISHED");
        post.setData(data);
        return post;
    }

    /**
     * SH722 — the sort key that is not unique, asserted at the boundary it crossed.
     *
     * <p>P5 promises byte-identical serialization and SH714 measured it failing on the wire: ten
     * identical {@code shio_context} calls returned <b>two</b> distinct packs, differing by two
     * attributes swapping places. This method sorted by {@code ordinal} alone over the
     * {@code Set} the persistence layer hands it in hash order, and two attributes shared
     * {@code ordinal: 2} — so the comparator called them equal and the tie went to whichever way
     * the set iterated this time. <b>A stable sort over an unstable input is an unstable sort.</b></p>
     *
     * <p>The fixture is a {@code HashSet} on purpose, and the assertion runs it repeatedly with a
     * fresh set each time: one pass would pin whatever order this JVM's salt produced today, which
     * is exactly the mistake being fixed. What is asserted is not <em>an</em> order but that the
     * order does not depend on the input's.</p>
     */
    @Test
    void aSharedOrdinalStillSortsTheSameWayEveryTime() {
        java.util.List<java.util.List<String>> orders = new java.util.ArrayList<>();
        for (int run = 0; run < 25; run++) {
            orders.add(mapper.toPostTypeSchema(typeWithCollidingOrdinals()).fields().stream()
                    .map(field -> field.name()).toList());
        }

        assertThat(orders).as("""
                        two identical reads of one post-type must describe it identically, or the                         context pack's own digest flips between calls that changed nothing and                         P9's cheap proof stops being one.""")
                .containsOnly(java.util.List.of("alpha", "description", "page-layout"));
    }

    /** DESCRIPTION and PAGE-LAYOUT both at ordinal 2, which is the shape SH714 met. */
    private ShPostType typeWithCollidingOrdinals() {
        ShPostType postType = new ShPostType();
        postType.setName("PT-FOLDER-INDEX");
        java.util.Set<ShPostTypeAttr> attrs = new java.util.HashSet<>();
        attrs.add(attr("page-layout", 2));
        attrs.add(attr("description", 2));
        attrs.add(attr("alpha", 1));
        postType.setShPostTypeAttrs(attrs);
        return postType;
    }

    private ShPostTypeAttr attr(String name, int ordinal) {
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName(name);
        attr.setOrdinal(ordinal);
        attr.setRequired((byte) 0);
        attr.setIsTitle((byte) 0);
        attr.setIsSummary((byte) 0);
        return attr;
    }

    @Test
    void validDataIsExpandedIntoAttrs() {
        ShCdaPost dto = mapper.toPost(post("{\"title\":\"Hello\",\"n\":3}"));
        assertEquals("Hello", dto.attrs().get("title").asString());
        assertEquals(3, dto.attrs().get("n").asInt());
    }

    @Test
    void malformedDataYieldsEmptyObjectNotException() {
        ShCdaPost dto = mapper.toPost(post("{not valid json"));
        assertTrue(dto.attrs().isObject());
        assertTrue(dto.attrs().isEmpty());
    }

    @Test
    void nullOrBlankDataYieldsEmptyObject() {
        assertTrue(mapper.toPost(post(null)).attrs().isObject());
        assertTrue(mapper.toPost(post("   ")).attrs().isEmpty());
    }

    @Test
    void jsonArrayDataIsRejectedAsEmptyObject() {
        // The contract guarantees attrs is an object, never an array.
        ShCdaPost dto = mapper.toPost(post("[1,2,3]"));
        assertTrue(dto.attrs().isObject());
        assertTrue(dto.attrs().isEmpty());
    }

    @Test
    void serializedPostExcludesAuthoringFields() {
        String json = objectMapper.writeValueAsString(mapper.toPost(post("{\"title\":\"Hello\"}")));
        assertFalse(json.contains("\"owner\""), json);
        assertFalse(json.contains("\"publisher\""), json);
        assertFalse(json.contains("\"publishStatus\""), json);
        assertFalse(json.contains("\"modifier\""), json);
    }

    @Test
    void folderRootFlagDerivedFromRootFolderByte() {
        ShFolder root = new ShFolder();
        root.setName("root");
        root.setRootFolder((byte) 1);
        ShFolder child = new ShFolder();
        child.setName("child");
        child.setRootFolder((byte) 0);

        ShCdaFolder rootDto = mapper.toFolder(root);
        ShCdaFolder childDto = mapper.toFolder(child);
        assertTrue(rootDto.root());
        assertFalse(childDto.root());
    }
}
