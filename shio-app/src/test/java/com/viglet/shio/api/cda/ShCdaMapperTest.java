/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.viglet.shio.api.cda.dto.ShCdaFolder;
import com.viglet.shio.api.cda.dto.ShCdaPost;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
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
