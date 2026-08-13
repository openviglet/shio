/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.acl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * SH650 — an authorization decision may not depend on an entity mapping's fetch type.
 *
 * <h2>The finding, which is about a property no test can hold</h2>
 *
 * <p>SH225 set out to make a permission decision independent of {@code spring.jpa.open-in-view} and
 * found its own premise false: {@code ShFolder.parentFolder} is a plain {@code @ManyToOne} and JPA's
 * default there is EAGER, so the ancestor chain arrived with the {@code findById}. The test written
 * to catch the predicted {@code LazyInitializationException} — {@code open-in-view} off, three levels
 * deep — <b>passed against the unfixed code</b>, and was withdrawn rather than kept as reassurance.</p>
 *
 * <p>The dependency was real one level down: the check worked <em>because of a default fetch type</em>.
 * Marking that association {@code LAZY} is ordinary JPA hygiene, somebody will do it for reasons that
 * have nothing to do with permissions, and before SH225 it would have broken <b>authorization</b> — as
 * a 500 rather than a denial, which is neither an allow nor a deny and cannot be handled as either.</p>
 *
 * <p>SH225 removed the dependency by walking the chain by id, and could not stop the next edit putting
 * it back, <b>because the property is a shape rather than a behaviour</b>: the walk must not navigate
 * an association. Any behavioural test is green on both sides of that line. So the deliverable is the
 * instrument (SH338), and this is it.</p>
 *
 * <h2>Two questions, and the second is the one worth having</h2>
 *
 * <p>The narrow form scans the one class. {@link #theInventoryOfSecurityReadsThatWalkTheGraph()} asks
 * the wider one the design named — <em>which security-relevant reads navigate associations at all</em>
 * — and holds the answer as a declared list rather than a ceiling, the shape
 * {@code ShAgentP1ConformanceTest} uses: a ceiling lets one task pay the debt and the next spend it.
 * Both counts print on every run, so the number is never a comment that has started rotting.</p>
 *
 * <p>The banned getters are <b>derived from the entity model</b>, not written down. A list in this
 * file would be a second copy of the mapping, and the failure it would miss is precisely the one this
 * exists for: an association added later, navigated by an edit nobody connected to authorization.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShAclAssociationWalkLintTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "viglet", "shio");
    private static final Path MODEL = MAIN.resolve("persistence").resolve("model");

    /**
     * The class the rule is enforced on: the ancestor walk itself.
     *
     * <p>Named rather than derived, because "the class that decides permission" is exactly the
     * judgement a list should carry and a regex should not. The wider population below is derived.</p>
     */
    private static final String ENFORCED = "ShContentAclService.java";

    /**
     * Security-relevant reads that navigate an association today, and the reason each is allowed to.
     *
     * <p><b>Empty, and measured rather than assumed.</b> The first draft of this list guessed two
     * entries — the principal walk in {@code ShAuthorityResolver} and {@code ShCustomUserDetails} —
     * and the run said zero: those read {@code role.getName()} and {@code group.getName()}, which
     * are properties of an already-loaded row and not navigations. The only two textual hits in the
     * whole population are the <em>prose</em> in {@link ShContentAclService} explaining what SH225
     * removed, which {@code ShSourceText.codeLine} strips — SH206's defect, avoided by construction
     * rather than by luck.</p>
     *
     * <p>So the standing state is that no security decision in this instance depends on a fetch
     * type, and this list is what holds it there. Declared rather than a ceiling: an entry is an
     * argument somebody made, and a class arriving without one fails whether or not the total moved.</p>
     */
    private static final Set<String> DECLARED_GRAPH_WALKERS = Set.of();

    /** Where a security decision is made, derived rather than listed — see the class javadoc. */
    private static final Pattern DECIDES_ACCESS =
            Pattern.compile("AccessDeniedException|ShAclPermission|GrantedAuthority");

    @Test
    @DisplayName("SH650: the ACL chain walk never navigates an entity association")
    void theChainWalkClimbsByIdAndNotByGraph() {
        Map<String, String> getters = associationGetters();
        List<String> violations = navigations(MAIN.resolve("api").resolve("acl"), getters).stream()
                .filter(hit -> hit.file().equals(ENFORCED))
                .map(Hit::toString)
                .toList();

        assertThat(violations)
                .withFailMessage("""
                        %s navigates an entity association, so a permission decision depends on \
                        that association's FETCH TYPE:
                        %s
                        Today it happens to work — JPA defaults @ManyToOne to EAGER — and the day \
                        somebody marks it LAZY for ordinary reasons the check answers a \
                        LazyInitializationException instead of yes or no. A 500 is neither an allow \
                        nor a deny, and the code that has to work is the code that DENIES (SH225).
                        Fix: climb by id. `ShFolderRepository.findParentIdOf` and \
                        `ShPostUnifiedRepository.findFolderIdOf` are the projections that exist for \
                        this, and they cost the round trips the lazy walk was already paying.""",
                        ENFORCED, String.join("\n", violations))
                .isEmpty();
    }

    @Test
    @DisplayName("SH650: every security-relevant read that walks the entity graph is a declared one")
    void theInventoryOfSecurityReadsThatWalkTheGraph() {
        Map<String, String> getters = associationGetters();
        Map<String, List<Hit>> byFile = new TreeMap<>();
        for (Path source : securityRelevantSources()) {
            List<Hit> hits = navigations(source, getters);
            if (!hits.isEmpty()) {
                byFile.computeIfAbsent(hits.getFirst().file(), key -> new ArrayList<>()).addAll(hits);
            }
        }

        System.out.printf("%n[SH650] %d association getters derived from the model; "
                        + "%d security-relevant sources, %d of which walk the graph: %s%n%n",
                getters.size(), securityRelevantSources().size(), byFile.size(), byFile.keySet());

        assertThat(byFile.keySet())
                .as("""
                        A security-relevant read that navigates an association is a decision whose \
                        correctness depends on a fetch type somebody else owns. That is allowed \
                        where it is argued and never where it merely happened, so this is a \
                        declared list and not a ceiling — a ceiling lets one task pay the debt and \
                        the next spend it. Add the file to DECLARED_GRAPH_WALKERS with the reason, \
                        or climb by id (SH650).""")
                .isSubsetOf(DECLARED_GRAPH_WALKERS);
    }

    @Test
    @DisplayName("SH650: the lint finds the form SH225 removed, and does not flag the one it left")
    void theLintDetectsTheShapeThatWasRemoved() {
        // Guards the guard, for ShSourceScanLintTest's reason: a scan that silently matches nothing
        // reports the same green as a codebase that is clean, and this one derives its own needles.
        Map<String, String> getters = associationGetters();
        assertThat(getters)
                .as("derived from the model, so an association added later is covered without an edit")
                .containsKeys("getParentFolder", "getShFolder", "getShSite");

        // The other half of not being vacuous, and the failure mode every source lint has: a moved
        // package or a tightened pattern turns the instrument off and reports the same green. Named
        // files rather than a count, because a count is a number somebody relaxes instead of
        // investigating.
        assertThat(securityRelevantSources().stream().map(path -> path.getFileName().toString()).toList())
                .as("the population still contains the class the rule is actually about")
                .contains(ENFORCED);

        assertThat(navigationsIn("ShExample.java", """
                for (ShFolder f = folder; f != null; f = f.getParentFolder()) { chain.add(f.getId()); }
                """, getters))
                .as("the exact walk SH225 replaced")
                .isNotEmpty();
        assertThat(navigationsIn("ShExample.java", """
                for (String id = startFolderId(objectId); id != null;
                        id = shFolderRepository.findParentIdOf(id).orElse(null)) { chain.add(id); }
                """, getters))
                .as("and the id projection it replaced it with is not a finding")
                .isEmpty();
        assertThat(navigationsIn("ShExample.java", """
                /** Used to walk folder.getParentFolder(), which is lazy (SH225). */
                """, getters))
                .as("""
                        the prose ABOUT the rule is not a breach of it — SH206's defect, and the \
                        reason ShSourceText.codeLine exists""")
                .isEmpty();
    }

    /**
     * Getter name → the association it reads, derived from the entity model's own annotations.
     *
     * <p>Every {@code @ManyToOne}/{@code @OneToMany}/{@code @OneToOne}/{@code @ManyToMany} field, as
     * the accessor Lombok's {@code @Getter} generates for it. Read from source rather than by
     * reflection so the lint needs no application context — it is a build-time shape check, and
     * standing an instance up to ask what shape the code has would be the slowest possible way to
     * find out.</p>
     */
    private static Map<String, String> associationGetters() {
        Map<String, String> getters = new TreeMap<>();
        Pattern association = Pattern.compile("@(ManyToOne|OneToMany|OneToOne|ManyToMany)\\b");
        for (Path source : javaFilesUnder(MODEL)) {
            boolean pending = false;
            for (String line : read(source).split("\n", -1)) {
                String code = ShSourceText.codeLine(line);
                if (code.isBlank()) {
                    continue;
                }
                if (association.matcher(code).find()) {
                    pending = true;
                    continue;
                }
                String name = pending ? fieldNameIn(code) : null;
                if (name != null) {
                    getters.put("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1), name);
                    pending = false;
                }
            }
        }
        return getters;
    }

    /**
     * The field a declaration line declares, or {@code null} if the line is not one.
     *
     * <p>Written out rather than as one regex: the pattern that reads
     * {@code private Set<ShFolder> shFolders = new HashSet<>();} in one match needs a lazy run over
     * a character class holding whitespace, and that is the super-linear backtracking a scanner
     * running on every build should not be carrying. The field name is simply the last token before
     * the {@code =} or the {@code ;}.</p>
     */
    private static String fieldNameIn(String code) {
        String line = code.strip();
        if (!line.startsWith("private ") && !line.startsWith("protected ")
                && !line.startsWith("public ")) {
            return null;
        }
        int end = line.indexOf('=');
        if (end < 0) {
            end = line.indexOf(';');
        }
        if (end < 0) {
            return null;
        }
        String[] tokens = line.substring(0, end).strip().split("[\\s>]+");
        String last = tokens[tokens.length - 1];
        return tokens.length >= 2 && last.matches("[a-zA-Z_]\\w*") ? last : null;
    }

    /**
     * Main sources that make an access decision — the population the wider question is asked of.
     *
     * <p>Derived from what the file <em>does</em> rather than from where it lives: a check that moved
     * package would leave a path-keyed list quietly scanning nothing, which is the failure mode every
     * source lint has. The model's own classes are excluded because they <b>are</b> the associations;
     * {@code ShContentAcl} declaring one is the mapping, not a walk of it.</p>
     */
    private static List<Path> securityRelevantSources() {
        List<Path> sources = new ArrayList<>();
        for (Path source : javaFilesUnder(MAIN)) {
            if (source.startsWith(MODEL)) {
                continue;
            }
            if (DECIDES_ACCESS.matcher(ShSourceText.code(read(source))).find()) {
                sources.add(source);
            }
        }
        return sources;
    }

    private static List<Hit> navigations(Path root, Map<String, String> getters) {
        List<Hit> hits = new ArrayList<>();
        for (Path source : javaFilesUnder(root)) {
            hits.addAll(navigationsIn(source.getFileName().toString(), read(source), getters));
        }
        return hits;
    }

    /** One file's findings: a call to an association getter, on a line that is code. */
    private static List<Hit> navigationsIn(String file, String text, Map<String, String> getters) {
        List<Hit> hits = new ArrayList<>();
        int lineNumber = 0;
        for (String line : text.split("\n", -1)) {
            lineNumber++;
            String code = ShSourceText.codeLine(line);
            if (code.isBlank()) {
                continue;
            }
            for (String getter : new LinkedHashSet<>(getters.keySet())) {
                if (code.contains(getter + "()")) {
                    hits.add(new Hit(file, lineNumber, getter, code.strip()));
                }
            }
        }
        return hits;
    }

    private record Hit(String file, int line, String getter, String code) {

        @Override
        public String toString() {
            return "  %s:%d — %s()  [%s]".formatted(file, line, getter, code);
        }
    }

    private static List<Path> javaFilesUnder(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of(root);
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
