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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;
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
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShFilePostTypeSourceTest {

    /**
     * The one writer, read as text (SH1098).
     *
     * <p>Through {@link ShSourceText#code} — the javadoc above names {@code data.put} to explain
     * the scan, and a raw read would count the explanation as a call site.</p>
     */
    private static final String SERVICE_SOURCE = readService();

    private static String readService() {
        Path path = Path.of("src/main/java/com/viglet/shio/api/staticfile/ShStaticFileService.java");
        try {
            return Files.readString(path);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }


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
     * Every {@code data} key {@code ShStaticFileService} can write, read off the service (SH478).
     *
     * <h2>Why this is derived and the list it replaces was not</h2>
     *
     * <p>SH474 declared four keys and held the other three — {@code ALT}, {@code CAPTION},
     * {@code TAGS} — in a hand-kept {@code STILL_UNDECLARED} map with the reason each waited. The
     * reason was wrong: {@code TAGS} waited on a modelling decision about {@code Multi Select}'s
     * {@code widgetSettings} that the console component does not have, so the field waited a block on
     * a question nobody had to answer. A declared list records a belief; it cannot check one.</p>
     *
     * <p>So the instrument is the constants themselves. SH168's {@code unknown-field} is an
     * <b>error</b>, which makes "a key this writer writes and this type omits" a finding on the
     * <em>content</em> of any instance whose curator opened the Media Library — 66 of them on the
     * replication that found it. Reflecting the {@code DATA_*} fields means the <b>next</b> key
     * somebody adds fails this test on the commit that adds it, rather than on somebody's replica
     * (SH322: the grep is the deliverable).</p>
     *
     * <h2>That promise was false for five blocks (SH1098)</h2>
     *
     * <p>"A key added tomorrow fails on its own commit" held only for a key <b>named</b>
     * {@code DATA_}. {@code SANITIZED} is written by two call sites here as
     * {@code ShSystemPostTypeAttr.SANITIZED} — a constant on another class — so the reflection could
     * not see it, the type never declared it, and {@code shio_assets} (which reads {@code FILE} and
     * {@code SANITIZED} together) refused <b>every listing that returned an asset</b>, on every
     * instance. Four passes over this exact question — SH474's four keys, SH478's three, SH1030's
     * {@code CONTENT_TYPE} — each re-ran a reflection that structurally could not find it.</p>
     *
     * <p>So the scan now reads the <b>call sites</b>: every {@code data.put(X, …)} in the service,
     * resolving {@code X} through whichever class declares it. A naming convention is a belief about
     * the code, and this file's own lesson (SH478, above) is that a belief cannot check one — it took
     * the hand-kept list out and put a convention in, which is a smaller version of the same
     * mistake. What the writer writes is a fact about its statements, and that is what is read.</p>
     */
    private static Set<String> dataKeysTheServiceWrites() {
        Set<String> keys = new TreeSet<>();
        // The constants, still — they are the common case and resolving them by name is exact.
        Map<String, String> byConstant = new java.util.HashMap<>();
        for (Class<?> owner : List.of(ShStaticFileService.class, ShSystemPostTypeAttr.class)) {
            for (java.lang.reflect.Field field : owner.getDeclaredFields()) {
                if (field.getType() != String.class
                        || !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    byConstant.put(owner.getSimpleName() + "." + field.getName(),
                            (String) field.get(null));
                    byConstant.put(field.getName(), (String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("cannot read " + field.getName(), e);
                }
            }
        }
        // And the call sites, which is what makes this a fact rather than a convention. A `put`
        // whose key is a literal counts too: spelling one inline is the next way to slip past this.
        String source = ShSourceText.code(SERVICE_SOURCE);
        java.util.regex.Matcher put = java.util.regex.Pattern
                .compile("data\\.(?:put|putArray|remove)\\(\\s*(?:\"([A-Za-z0-9_]+)\""
                        + "|([A-Za-z]+\\.)?([A-Z][A-Za-z0-9_]*))")
                .matcher(source);
        while (put.find()) {
            if (put.group(1) != null) {
                keys.add(put.group(1));
                continue;
            }
            String named = (put.group(2) == null ? "" : put.group(2)) + put.group(3);
            String value = byConstant.get(named);
            if (value != null) {
                keys.add(value);
            }
        }
        return keys;
    }

    @Test
    @DisplayName("SH478: the type declares every data key the service can write, derived not listed")
    void theTypeDeclaresWhatTheUploaderWrites() {
        ShPostType created = shStaticFileService.resolveOrCreateFilePostType();
        Set<String> declared = created.getShPostTypeAttrs().stream()
                .map(ShPostTypeAttr::getName)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> written = dataKeysTheServiceWrites();

        assertThat(written)
                .as("a reader of this test must be able to tell an empty reflection from a passing one")
                .hasSizeGreaterThanOrEqualTo(7);
        assertThat(written)
                .as("""
                        SH1098: the scan finds the key that named this defect. `SANITIZED` is \
                        written as `ShSystemPostTypeAttr.SANITIZED`, so the `DATA_*` reflection \
                        this replaced could not see it and `shio_assets` refused every listing \
                        that returned an asset. Asserted by name rather than left to the size \
                        check: the widening is the deliverable, and a scan that reads call sites \
                        but resolves no constant off another class would pass the count and miss \
                        exactly this again.""")
                .contains(ShSystemPostTypeAttr.SANITIZED);

        assertThat(declared)
                .as("""
                        every one of these is written into a File post's `data` by \
                        ShStaticFileService, and SH168's unknown-field is an ERROR — a key the \
                        writer writes and the type omits is a finding on the content, not on the \
                        model, which is how 66 of them read as broken assets. Derived from the \
                        DATA_* constants (SH478), so a key added tomorrow fails on its own commit""")
                .contains(ShSystemPostTypeAttr.FILE)
                .containsAll(written);

        assertThat(INVENTED.keySet())
                .as("a declared divergence that no longer diverges is a suppression that rotted")
                .allSatisfy(name -> assertThat(referenceType().get("fields").get(name))
                        .as("%s is in the 2018 model now, so this entry is stale", name)
                        .isNull());
    }

    /**
     * The widget each Media Library key is declared with, and the surface it has to agree with
     * (SH478).
     *
     * <p>Not a transcription of {@code fileFields}: it is the claim that the File form a curator
     * opens renders the same control the Media Library dialog already gives them for the same value.
     * {@code static-file.list.page.tsx} uses an {@code Input} for alt, a two-row {@code Textarea} for
     * caption, and a chip list for tags — which is what {@code multi-select.widget.tsx} is. A
     * declaration that disagreed would put one value behind two different controls, and the free-form
     * half of that claim is held on the console side by {@code widget-option-source.test.mjs}.</p>
     */
    @Test
    @DisplayName("SH478: the three curator keys carry the widget the Media Library already renders")
    void theCuratorKeysCarryTheConsoleWidget() {
        ShPostType created = shStaticFileService.resolveOrCreateFilePostType();
        Map<String, String> widgets = created.getShPostTypeAttrs().stream()
                .collect(Collectors.toMap(ShPostTypeAttr::getName,
                        attr -> attr.getShWidget().getName(), (a, b) -> a, TreeMap::new));

        assertThat(widgets)
                .containsEntry(ShStaticFileService.DATA_ALT, ShSystemWidget.TEXT)
                .containsEntry(ShStaticFileService.DATA_CAPTION, ShSystemWidget.TEXT_AREA)
                .as("""
                        Multi Select, because it is a free-form chip input and not an option list — \
                        the belief that it needed widgetSettings naming a vocabulary is what held \
                        these three back for a block (SH478)""")
                .containsEntry(ShStaticFileService.DATA_TAGS, ShSystemWidget.MULTI_SELECT);

        assertThat(created.getShPostTypeAttrs())
                .as("""
                        all three are optional and none is the title: a File that predates the Media \
                        Library is a file nobody captioned, not a broken one""")
                .filteredOn(attr -> ShStaticFileService.DATA_ALT.equals(attr.getName())
                        || ShStaticFileService.DATA_CAPTION.equals(attr.getName())
                        || ShStaticFileService.DATA_TAGS.equals(attr.getName()))
                .hasSize(3)
                .allSatisfy(attr -> {
                    assertThat(attr.getRequired()).isZero();
                    assertThat(attr.getIsTitle()).isZero();
                });
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
