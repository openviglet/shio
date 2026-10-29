/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.viglet.shio.api.cda;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * SH162 — the mirror-completeness guard: every component of every CDA read
 * record has a matching property in {@code @viglet/shio-client}'s
 * {@code src/types.ts}.
 *
 * <p><b>Why a test and not just the two missing fields.</b> {@code ShCdaPost}
 * shipped {@code locale} + {@code availableLocales} with SH52 — the two fields a
 * headless front end needs for a language switcher — and the TypeScript mirror
 * declared neither for three blocks. A typed consumer therefore could not see
 * the feature at all without casting, which is the opposite of what the type file
 * exists for. The drift was invisible because nothing compared the two shapes,
 * and it will happen again the next time a DTO grows and nobody has both windows
 * open. Adding the fields fixes today; this test fixes the class.</p>
 *
 * <p>It checks three things, and deliberately not a fourth. Names must match
 * one-for-one in both directions (a component with no property is a feature the
 * SDK cannot see; a property with no component is a field the CDA dropped and the
 * SDK still promises). Primitive components must be declared non-optional,
 * because SH127 omits nulls and nothing else — a {@code false} or a {@code 0} is
 * an answer and is always on the wire. Types themselves are <i>not</i> compared:
 * {@code Instant} is delivered as an ISO string and {@code JsonNode} as an
 * arbitrary object, so a mapping table would be a second contract to keep in
 * sync (law P10) for far less than it costs.</p>
 *
 * <p>Per the SH122 lesson (§XIV.4) the guard self-tests: a parser that silently
 * matches nothing passes forever, so the extraction is asserted against known
 * content and against a synthetic drift it must reject.</p>
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShCdaTypeScriptMirrorTest {

    /** The SDK's hand-written mirror of the read contract. */
    private static final Path TYPES_TS = Paths.get("..", "shio-client", "src", "types.ts");

    /** Where the DTO records live, so a *new* one cannot slip past unlisted. */
    private static final Path DTO_DIR = Paths.get("src/main/java/com/viglet/shio/api/cda/dto");

    private static final String DTO_PACKAGE = "com.viglet.shio.api.cda.dto.";

    /** Read-contract record -> the {@code types.ts} interface that mirrors it. */
    private static final Map<String, String> MIRRORED = Map.of(
            "ShCdaSite", "ShioSite",
            "ShCdaSiteLocales", "ShioSiteLocales",
            "ShCdaFolder", "ShioFolder",
            "ShCdaPostSummary", "ShioPostSummary",
            "ShCdaPost", "ShioPost",
            "ShCdaListing", "ShioListing",
            "ShCdaPath", "ShioPath",
            "ShCdaSchemaField", "ShioFieldSchema",
            "ShCdaPostTypeSchema", "ShioPostTypeSchema",
            // SH1133 — the tenth pair, which is Map.of's limit: the next one moves this to ofEntries.
            "ShCdaRedirect", "ShioRedirect");

    /**
     * Records the SDK deliberately does not mirror, each with the reason. A new
     * DTO must land in here or in {@link #MIRRORED} — "I forgot" and "on purpose"
     * have to look different, or the guard degrades into a list of what someone
     * happened to add.
     */
    private static final Map<String, String> NOT_MIRRORED = Map.of(
            "ShCdaEditablePost", "SH10 editor read shape (post + field metadata); ShioClient exposes no editor surface",
            "ShCdaField", "component of ShCdaEditablePost only — see above",
            "ShCdaPatch", "a request body, not a read shape; the SDK is read-only");

    @Test
    void everyCdaDtoIsEitherMirroredOrDeliberatelyExcluded() throws IOException {
        assertThat(Files.isDirectory(DTO_DIR))
                .as("Expected the CDA DTO package at %s", DTO_DIR)
                .isTrue();

        Set<String> declared = new TreeSet<>(MIRRORED.keySet());
        declared.addAll(NOT_MIRRORED.keySet());

        Set<String> onDisk = dtoRecordNames();
        assertThat(onDisk)
                .as("The DTO package scan found nothing — this guard would pass forever")
                .isNotEmpty();

        assertThat(onDisk)
                .as("""
                        A CDA DTO is neither mirrored in @viglet/shio-client's types.ts nor listed as \
                        deliberately unmirrored (SH162). Add it to MIRRORED with its Shio* interface, or \
                        to NOT_MIRRORED with the reason it is not part of the SDK's read contract.""")
                .allSatisfy(name -> assertThat(declared).contains(name));
    }

    @Test
    void everyMirroredRecordComponentHasATypeScriptProperty() throws Exception {
        Map<String, Map<String, Boolean>> interfaces = parseInterfaces(readTypesTs());
        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, String> entry : sorted(MIRRORED).entrySet()) {
            String dto = entry.getKey();
            String tsName = entry.getValue();
            Map<String, Boolean> properties = interfaces.get(tsName);
            if (properties == null) {
                violations.add(tsName + " (mirror of " + dto + ") is not declared in types.ts");
                continue;
            }
            Set<String> components = new LinkedHashSet<>();
            for (RecordComponent component : recordComponents(dto)) {
                components.add(component.getName());
                if (!properties.containsKey(component.getName())) {
                    violations.add(tsName + "." + component.getName()
                            + " is missing — " + dto + " delivers it and a typed consumer cannot see it");
                }
            }
            for (String property : properties.keySet()) {
                if (!components.contains(property)) {
                    violations.add(tsName + "." + property
                            + " has no component on " + dto + " — the CDA no longer delivers it");
                }
            }
        }

        assertThat(violations)
                .as("""
                        @viglet/shio-client's types.ts has drifted from the CDA read records it mirrors \
                        (SH162). Field names are a frozen public contract: fix the mirror, never rename \
                        the DTO component.""")
                .isEmpty();
    }

    @Test
    void primitiveComponentsAreNotOptionalInTheMirror() throws Exception {
        Map<String, Map<String, Boolean>> interfaces = parseInterfaces(readTypesTs());
        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, String> entry : sorted(MIRRORED).entrySet()) {
            Map<String, Boolean> properties = interfaces.getOrDefault(entry.getValue(), Map.of());
            for (RecordComponent component : recordComponents(entry.getKey())) {
                Boolean optional = properties.get(component.getName());
                if (component.getType().isPrimitive() && Boolean.TRUE.equals(optional)) {
                    violations.add(entry.getValue() + "." + component.getName() + " is declared optional but "
                            + entry.getKey() + " delivers a " + component.getType().getName()
                            + ", which SH127 never omits");
                }
            }
        }

        assertThat(violations)
                .as("""
                        SH127 omits nulls and nothing else, so a primitive component is always on the \
                        wire. Declaring it `field?: T` makes a consumer narrow a value that cannot be \
                        absent.""")
                .isEmpty();
    }

    /**
     * The guard's own guard. Both halves of the comparison have to be shown to
     * see something: an interface parser that returns nothing, or a reflection
     * step over a non-record, would make every assertion above vacuous.
     */
    @Test
    void theMirrorParserActuallyReadsTheFileAndRejectsDrift() throws Exception {
        Map<String, Map<String, Boolean>> interfaces = parseInterfaces(readTypesTs());

        assertThat(interfaces.keySet()).containsAll(MIRRORED.values());
        // The two fields SH162 was opened for, and the optionality of each.
        assertThat(interfaces.get("ShioPost")).containsEntry("locale", true);
        assertThat(interfaces.get("ShioPost")).containsEntry("availableLocales", false);
        assertThat(interfaces.get("ShioPost")).containsEntry("published", false);
        assertThat(interfaces.get("ShioPostSummary")).containsEntry("locale", true);
        // JSDoc prose must not be mistaken for a property.
        assertThat(interfaces.get("ShioSite").keySet())
                .containsExactly("id", "name", "description", "url", "rootFolderId", "locales");
        // SH1123 — `declared` is a primitive, so the SDK must not make a consumer narrow it.
        assertThat(interfaces.get("ShioSiteLocales")).containsEntry("declared", false);
        // SH1287 — `labels` is omitted when the site set none, so the SDK must declare it optional.
        assertThat(interfaces.get("ShioSiteLocales")).containsEntry("labels", true);
        assertThat(recordComponents("ShCdaPost")).isNotEmpty();

        // ...and it must notice a field that is gone.
        Map<String, Map<String, Boolean>> drifted = parseInterfaces("""
                export interface ShioPost {
                  id: string;
                  /** locale?: string; -- commented out, so not declared */
                  published: boolean;
                }
                """);
        assertThat(drifted.get("ShioPost").keySet()).containsExactly("id", "published");
    }

    // --- helpers ---

    private static String readTypesTs() throws IOException {
        assertThat(Files.isRegularFile(TYPES_TS))
                .as("Expected the SDK type mirror at %s (run from the shio-app module directory)", TYPES_TS)
                .isTrue();
        // SH613 — TypeScript shares Java's comment syntax, so the shared stripper applies and a
        // commented-out property cannot be read as a declared one.
        return ShSourceText.code(Files.readString(TYPES_TS, StandardCharsets.UTF_8));
    }

    private static Set<String> dtoRecordNames() throws IOException {
        try (Stream<Path> files = Files.list(DTO_DIR)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".java"))
                    .map(name -> name.substring(0, name.length() - ".java".length()))
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        }
    }

    private static RecordComponent[] recordComponents(String simpleName) throws ClassNotFoundException {
        Class<?> type = Class.forName(DTO_PACKAGE + simpleName);
        assertThat(type.isRecord()).as("%s is expected to be a record", simpleName).isTrue();
        return type.getRecordComponents();
    }

    private static Map<String, String> sorted(Map<String, String> map) {
        return new java.util.TreeMap<>(map);
    }

    private static final Pattern INTERFACE_START =
            Pattern.compile("^export interface (\\w+)\\s*\\{\\s*$");

    /** {@code name: T;} or {@code name?: T;} — an identifier, then an optional {@code ?}, then a colon. */
    private static final Pattern PROPERTY = Pattern.compile("^(\\w+)(\\?)?\\s*:");

    /**
     * Extracts {@code interface name -> (property -> isOptional)}. Bodies here are
     * flat (no nested object literals), so the block ends at the first
     * column-zero {@code }}. Comment lines are skipped explicitly rather than
     * relying on prose never starting with an identifier.
     */
    private static Map<String, Map<String, Boolean>> parseInterfaces(String source) {
        Map<String, Map<String, Boolean>> interfaces = new LinkedHashMap<>();
        String[] lines = source.split("\r?\n", -1);
        Map<String, Boolean> current = null;
        for (String raw : lines) {
            String line = raw.strip();
            if (current == null) {
                Matcher start = INTERFACE_START.matcher(line);
                if (start.matches()) {
                    current = new LinkedHashMap<>();
                    interfaces.put(start.group(1), current);
                }
                continue;
            }
            if (line.equals("}")) {
                current = null;
                continue;
            }
            if (line.isEmpty() || line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) {
                continue;
            }
            Matcher property = PROPERTY.matcher(line);
            if (property.find()) {
                current.put(property.group(1), property.group(2) != null);
            }
        }
        return interfaces;
    }
}
