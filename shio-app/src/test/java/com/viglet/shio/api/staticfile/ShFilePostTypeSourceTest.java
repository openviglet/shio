/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH293 — the {@code File} post-type this system creates, compared with the 2018 model.
 *
 * <h2>Why this type and not the other nine</h2>
 *
 * <p>SH290 compared {@code PageLayout}, {@code Region} and {@code Theme} against
 * {@code import/post-types.json} because SH183 transcribed them and SH277 had found the
 * transcription wrong. The same file declares ten more, and SH293's audit asked of each: does this
 * system still provision it? For eight the answer was no and the constants were deleted. For
 * {@code File} it is <b>yes</b> — {@code ShStaticFileService.resolveOrCreateFilePostType} builds it
 * on the first upload (SH38), field by field, in Java.</p>
 *
 * <p>That makes it exactly the shape SH277's defect had: a model transcribed by hand into code,
 * with a reference copy in the repository that nothing compared it to. And the blast radius is
 * different from the render three's — {@code FILE} is the field the static-file subsystem writes the
 * stored path into and {@code /file_source/**} serves from, so a name drift here breaks
 * <b>uploads</b>, not rendering, and would surface as a 404 on bytes that exist.</p>
 *
 * <h2>Its own database</h2>
 *
 * <p>This test creates a system post-type. On the shared context that is a mutation every later test
 * inherits — SH289's lesson, where a shared catalogue turned one test's cleanup into another's
 * missing widget several steps away.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-sh293;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShFilePostTypeSourceTest {

    private static final String REFERENCE = "/import/post-types.json";

    @Autowired
    private ShStaticFileService shStaticFileService;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("SH293: the File post-type this system creates is the 2018 model's File")
    void theCreatedTypeMatchesTheReference() {
        ShPostType created = shStaticFileService.resolveOrCreateFilePostType();
        JsonNode reference = referenceType();

        assertThat(created.getName())
                .as("the name uploads resolve by")
                .isEqualTo(reference.get("name").asString());
        assertThat(created.getNamePlural()).isEqualTo(reference.get("namePlural").asString());
        // `label` there, `title` here: the 2018 export's word for the same thing, which is a mapping
        // rather than a divergence — stated because an unexplained mismatch is what SH290 exists to
        // stop being invisible.
        assertThat(created.getTitle()).isEqualTo(reference.get("label").asString());
        assertThat(created.getDescription()).isEqualTo(reference.get("description").asString());
        assertThat(created.getSystem())
                .as("a system type: the console must not offer it for editing like a user's")
                .isEqualTo((byte) 1);
    }

    @Test
    @DisplayName("SH293: and its FILE field, which is what an upload actually writes into")
    void theFieldMatchesTheReference() {
        ShPostType created = shStaticFileService.resolveOrCreateFilePostType();
        JsonNode field = referenceType().get("fields").get(ShSystemPostTypeAttr.FILE);

        assertThat(field)
                .as("""
                        the 2018 model has no FILE field on File at all, which makes this whole \
                        comparison meaningless rather than merely failing""")
                .isNotNull();

        ShPostTypeAttr attr = created.getShPostTypeAttrs().stream()
                .filter(candidate -> ShSystemPostTypeAttr.FILE.equals(candidate.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(attr.getLabel()).isEqualTo(field.get("label").asString());
        assertThat(attr.getDescription()).isEqualTo(field.get("description").asString());
        assertThat(attr.getOrdinal()).isEqualTo(field.get("ordinal").asInt());
        assertThat(attr.getRequired() == 1)
                .as("required=%s in the 2018 model", field.get("required").asBoolean())
                .isEqualTo(field.get("required").asBoolean());
        assertThat(attr.getIsTitle() == 1)
                .as("""
                        isTitle is the flag SH277 was filed for: dropped from all three render \
                        types, it left every template written through the documented flow with an \
                        empty title and unaddressable. On File it is what names the uploaded \
                        object.""")
                .isEqualTo(field.get("title").asBoolean());
        assertThat(attr.getIsSummary() == 1).isEqualTo(field.get("summary").asBoolean());
        assertThat(attr.getShWidget().getName())
                .as("the File widget, which is what the console renders and SH38 stores through")
                .isEqualTo(field.get("widget").asString())
                .isEqualTo(ShSystemWidget.FILE);
    }

    private JsonNode referenceType() {
        try (InputStream in = getClass().getResourceAsStream(REFERENCE)) {
            for (JsonNode type : objectMapper.readTree(in).get("postTypes")) {
                if (ShSystemPostType.FILE.equals(type.get("name").asString())) {
                    return type;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + REFERENCE, e);
        }
        throw new IllegalStateException(REFERENCE + " no longer declares a File post-type");
    }
}
