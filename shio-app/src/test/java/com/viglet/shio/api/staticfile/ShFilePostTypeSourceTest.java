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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

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

    /**
     * A field this system declares that the 2018 model does not, with the reason (SH474).
     *
     * <p>Kept as a declared list rather than added to {@code import/post-types.json}, which is the
     * 2018 artefact itself: editing it so a comparison passes would destroy the only reference this
     * test has. All four are <b>optional</b> and machine-stamped, so a 2018 {@code File} arriving
     * without them is a file with no measurements rather than a broken one.</p>
     */
    private static final Map<String, String> INVENTED = new TreeMap<>(Map.of(
            ShStaticFileService.DATA_SHA256,
            "SH398 — the digest of the stored bytes, which is how `shio pull --content` skips an "
                    + "asset already on disk; there is no 2018 equivalent because there was no "
                    + "content projection to compare against.",
            ShStaticFileService.DATA_SIZE,
            "SH399 — the size the digest was taken over, so a reader can tell a stale stamp from a "
                    + "current one.",
            ShStaticFileService.DATA_WIDTH,
            "SH62 for the Media Library, SH471 for the converter: a section draws an image at the "
                    + "right shape without opening it.",
            ShStaticFileService.DATA_HEIGHT, "as WIDTH."));

    /**
     * Every {@code data} key an upload writes that the type still does not declare (SH474).
     *
     * <p>The instrument, and the reason it is a list rather than a comment: SH168's
     * {@code unknown-field} is an error, so each of these is a red finding on any instance whose
     * curator uses the Media Library. They are held here — declared, with the decision — because
     * {@code TAGS} is an array and the catalogue's {@code Multi Select} needs {@code widgetSettings}
     * naming its options, which is a modelling decision and not a transcription. SH478.</p>
     */
    private static final Map<String, String> STILL_UNDECLARED = new TreeMap<>(Map.of(
            "ALT", "SH62 — curator metadata, written by updateMetadata rather than by an upload.",
            "CAPTION", "as ALT.",
            "TAGS", "as ALT, and the one that needs a decision: an array, so Multi Select and its "
                    + "widgetSettings, or a different widget."));

    @Test
    @DisplayName("SH474: the type declares every key an upload stamps, and names the ones it does not")
    void theTypeDeclaresWhatTheUploaderWrites() {
        ShPostType created = shStaticFileService.resolveOrCreateFilePostType();
        Set<String> declared = created.getShPostTypeAttrs().stream()
                .map(ShPostTypeAttr::getName)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(declared)
                .as("""
                        every one of these is written into a File post's `data` by \
                        ShStaticFileService, and SH168's unknown-field is an ERROR — a key the \
                        writer writes and the type omits is a finding on the content, not on the \
                        model, which is how 66 of them read as broken assets""")
                .contains(ShSystemPostTypeAttr.FILE, ShStaticFileService.DATA_SHA256,
                        ShStaticFileService.DATA_SIZE, ShStaticFileService.DATA_WIDTH,
                        ShStaticFileService.DATA_HEIGHT);

        assertThat(declared)
                .as("""
                        and the gap is stated rather than left to be noticed: a key removed from \
                        STILL_UNDECLARED without being declared fails here, and one declared \
                        without being removed from the list fails too (SH142's rule)""")
                .doesNotContainAnyElementsOf(STILL_UNDECLARED.keySet());

        assertThat(INVENTED.keySet())
                .as("a declared divergence that no longer diverges is a suppression that rotted")
                .allSatisfy(name -> assertThat(referenceType().get("fields").get(name))
                        .as("%s is in the 2018 model now, so this entry is stale", name)
                        .isNull());
    }

    @Test
    @DisplayName("SH474: repairing an existing type adds the missing fields and nothing else")
    void repairIsAdditive() {
        ShPostType created = shStaticFileService.resolveOrCreateFilePostType();
        int before = created.getShPostTypeAttrs().size();

        // The second call is the existing-type branch, which is the one an instance that predates
        // SH474 takes. It must be a no-op on a sound type — the upload path runs it every time.
        ShPostType again = shStaticFileService.resolveOrCreateFilePostType();

        assertThat(again.getShPostTypeAttrs()).hasSize(before);
        assertThat(again.getId()).isEqualTo(created.getId());
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
