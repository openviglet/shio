/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * SH656 — a bound {@code @ConfigurationProperties} class that nothing injects.
 *
 * <h2>The defect is not the dead class</h2>
 *
 * <p>{@code ShGitProperties} bound {@code shio.git} and {@code application.properties} shipped
 * {@code shio.git.url=GIT_URL_HERE} and {@code shio.git.token=DEFINE_TOKEN_HERE}. No bean injected
 * it and no code path read either value. Two lines to delete — except for what the dead class does
 * to the one check that would have caught the page describing it.</p>
 *
 * <p>{@code docs-vocabulary.conformance.mjs} derives the legitimate {@code shio.*} keys from the
 * {@code @ConfigurationProperties} classes and from {@code application.properties}. Both declared
 * these, so the published configuration reference documented {@code shio.git.url} as *"Git
 * repository for the content-versioning integration"* and passed the gate. An operator sets the key,
 * gets no error, and has no way to learn it was never read. That is a documentation defect the
 * documentation check is structurally unable to see, because the vocabulary it checks against is
 * the thing that is wrong.</p>
 *
 * <h2>Why it is a lint and not two deletions (SH322)</h2>
 *
 * <p>Because a grep is what finds the one nobody named. Written for {@code shio.git}, this rule
 * immediately produced a <b>second</b>: {@code ShMgmtProperties} binds {@code shio.mgmt},
 * {@code application.properties} ships {@code shio.mgmt.enabled=true}, nothing injects it either —
 * and the configuration reference documents it as *"Enable the management interface"*. Neither was
 * on anybody's list. Deleting the two the task named would have left that one, under a check that
 * still could not see it.</p>
 *
 * <h2>What "reachable" means here, and why the test is narrow about it</h2>
 *
 * <p>Spring injects by <b>type</b>, so a class that is consumed is one whose simple name appears in
 * some other source file: a constructor parameter, a field, an {@code @EnableConfigurationProperties}
 * argument. That is a weaker claim than "a bean actually calls a getter" and it is the right one to
 * make from source — it has no false negatives (an injected class always names itself at the
 * injection site) and its only false positives are a mention in prose, which
 * {@link ShSourceText#code} removes before the search.</p>
 *
 * <p>Deliberately <b>not</b> reflective. A context-loading test would answer the same question at
 * far greater cost and would also pass for a class injected by something equally dead; this reads
 * the source, like {@code ShStatelessReachabilityLintTest}, whose shape SH656 named as the model.</p>
 *
 * @author Alexandre Oliveira
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShBoundPropertiesReachabilityLintTest {

    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Path PROPERTIES = Path.of("src", "main", "resources", "application.properties");

    /** `@ConfigurationProperties("shio.x")` and `@ConfigurationProperties(prefix = "shio.x")`. */
    private static final Pattern BINDING = Pattern.compile(
            "@ConfigurationProperties\\s*\\(\\s*(?:prefix\\s*=\\s*)?\"([^\"]+)\"");

    /**
     * Bound classes that are deliberately injected by nothing, with the reason.
     *
     * <p>Empty, and that is the finding rather than a state to preserve: every class binding a
     * {@code shio.*} prefix is read by something, so a key an operator can set is a key the product
     * uses. An entry here would be a claim that a bound prefix is documentation-only, which is
     * exactly the shape this test exists to refuse — so adding one needs a sentence somebody
     * reviews, and the sentence has to survive the question *"then why is it bound?"*.</p>
     */
    private static final Map<String, String> UNREAD_BY_DESIGN = Map.of();

    @Test
    @DisplayName("SH656: every class binding a shio.* prefix is injected by something")
    void everyBoundPrefixIsReachable() throws IOException {
        Map<String, Path> bound = boundClasses();

        assertThat(bound)
                .as("""
                        the reader is not vacuous: this repository binds a dozen prefixes, and a \
                        scan finding none would pass while proving nothing""")
                .hasSizeGreaterThan(8);

        List<String> unreachable = new ArrayList<>();
        for (Map.Entry<String, Path> each : bound.entrySet()) {
            String name = each.getKey();
            if (UNREAD_BY_DESIGN.containsKey(name)) {
                continue;
            }
            if (!namedElsewhere(name, each.getValue())) {
                unreachable.add(name);
            }
        }

        assertThat(new TreeSet<>(unreachable))
                .as("""
                        a bound prefix nothing injects is a key an operator can set, a page can \
                        document and no code will ever read — and `docs-vocabulary` derives its \
                        legitimate `shio.*` vocabulary from these very classes, so it passes the \
                        page while the setting does nothing (SH656). Delete the class and its \
                        `application.properties` lines, or inject it somewhere that reads it.""")
                .isEmpty();
    }

    /**
     * And the other half of the same fact: a key in {@code application.properties} under a prefix no
     * class binds.
     *
     * <p>The mirror image, and it fails differently. An unbound key is not documented *wrongly* —
     * it is simply inert, and Spring says nothing about it at startup because unknown properties are
     * legal. Checked here rather than in its own file because the two are one question asked from
     * either end, and a repository that passed one and failed the other would still ship a setting
     * that does nothing.</p>
     */
    @Test
    @DisplayName("SH656: every shio.* key the app ships is under a prefix some class binds")
    void everyShippedKeyHasABinding() throws IOException {
        List<String> prefixes = new ArrayList<>(boundPrefixes());
        assertThat(prefixes).as("the scan found the bound prefixes at all").hasSizeGreaterThan(8);

        Map<String, List<String>> fields = boundFields();
        List<String> orphans = new ArrayList<>();
        for (String line : Files.readAllLines(PROPERTIES, StandardCharsets.UTF_8)) {
            String text = line.strip();
            if (!text.startsWith("shio.") || !text.contains("=")) {
                continue;
            }
            String key = text.substring(0, text.indexOf('=')).strip();
            // The longest prefix wins: `shio` binds everything as a catch-all, so matching it first
            // would let `shio.image-transform.max-width` be checked against ShAppProperties' fields.
            String prefix = prefixes.stream()
                    .filter(each -> key.equals(each) || key.startsWith(each + "."))
                    .max(java.util.Comparator.comparingInt(String::length))
                    .orElse(null);
            if (prefix == null) {
                orphans.add(key);
                continue;
            }
            // And the first segment under it has to be a field the class declares. Prefix coverage
            // alone is too weak for a catch-all: `shio.plugin.blogger` sat under `shio` for years,
            // naming a class this repository does not contain, and read as covered.
            String remainder = key.equals(prefix) ? "" : key.substring(prefix.length() + 1);
            if (remainder.isEmpty()) {
                continue;
            }
            String head = relaxed(remainder.split("\\.")[0]);
            if (!fields.getOrDefault(prefix, List.of()).contains(head)) {
                orphans.add(key);
            }
        }

        assertThat(new TreeSet<>(orphans))
                .as("""
                        a `shio.*` key under no binding is inert: Spring accepts unknown properties \
                        silently, so an operator who sets it gets no error and no effect (SH656)""")
                .isEmpty();
    }

    /** Simple class name → its file, for every class binding a `shio.*` prefix. */
    private static Map<String, Path> boundClasses() throws IOException {
        Map<String, Path> found = new LinkedHashMap<>();
        try (Stream<Path> sources = Files.walk(MAIN)) {
            for (Path file : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String code = ShSourceText.code(Files.readString(file, StandardCharsets.UTF_8));
                Matcher matcher = BINDING.matcher(code);
                if (matcher.find() && matcher.group(1).startsWith("shio")) {
                    String name = file.getFileName().toString().replace(".java", "");
                    found.put(name, file);
                }
            }
        }
        return found;
    }

    /**
     * Prefix → the field names its binding class declares, in relaxed form.
     *
     * <p>Relaxed because that is how Spring binds: {@code shio.multi-tenant} reaches
     * {@code multiTenant} and {@code shio.keycloak-admin-id} reaches {@code keycloakAdminId}, so a
     * literal comparison would report every kebab-cased key in the file as an orphan.</p>
     *
     * <p><b>The first segment only</b>, which is the declared limit of this check: a nested group
     * binds through a field of its own, so the head is where a key stops being guessable from the
     * outside. That is enough for the case this was written for and it does not pretend to be a
     * binder.</p>
     */
    private static Map<String, List<String>> boundFields() throws IOException {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        Pattern field = Pattern.compile("\\bprivate\\s+(?:static\\s+|final\\s+)*[\\w.<>\\[\\], ]+?\\s+(\\w+)\\s*[;=]");
        try (Stream<Path> sources = Files.walk(MAIN)) {
            for (Path file : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String code = ShSourceText.code(Files.readString(file, StandardCharsets.UTF_8));
                Matcher binding = BINDING.matcher(code);
                if (!binding.find() || !binding.group(1).startsWith("shio")) {
                    continue;
                }
                List<String> names = new ArrayList<>();
                Matcher each = field.matcher(code);
                while (each.find()) {
                    names.add(relaxed(each.group(1)));
                }
                fields.put(binding.group(1), names);
            }
        }
        return fields;
    }

    /** `multi-tenant` and `multiTenant` are one name to Spring, and have to be one name here. */
    private static String relaxed(String name) {
        return name.replace("-", "").replace("_", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** Every `shio.*` prefix some class binds. */
    private static List<String> boundPrefixes() throws IOException {
        List<String> prefixes = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(MAIN)) {
            for (Path file : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String code = ShSourceText.code(Files.readString(file, StandardCharsets.UTF_8));
                Matcher matcher = BINDING.matcher(code);
                while (matcher.find()) {
                    if (matcher.group(1).startsWith("shio")) {
                        prefixes.add(matcher.group(1));
                    }
                }
            }
        }
        return prefixes;
    }

    /**
     * Whether any other main source names this type in code.
     *
     * <p>`ShSourceText.code` first, so a class named only in somebody's javadoc does not read as an
     * injection — the SH206 defect, and the one mistake that would make this lint say a dead class
     * is alive.</p>
     */
    private static boolean namedElsewhere(String name, Path own) throws IOException {
        Pattern reference = Pattern.compile("\\b" + Pattern.quote(name) + "\\b");
        try (Stream<Path> sources = Files.walk(MAIN)) {
            for (Path file : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (file.equals(own)) {
                    continue;
                }
                String code = ShSourceText.code(Files.readString(file, StandardCharsets.UTF_8));
                if (reference.matcher(code).find()) {
                    return true;
                }
            }
        }
        return false;
    }
}
