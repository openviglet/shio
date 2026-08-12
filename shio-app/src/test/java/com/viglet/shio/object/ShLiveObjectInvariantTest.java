/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.object;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.site.ShSite;

/**
 * SH226 — build-time guard for the "browsable trash" defect class.
 *
 * <h2>The invariant</h2>
 *
 * <p>{@code ShFolder} carries no {@code @SQLRestriction} — unlike {@code ShPostUnified} — so the
 * folder trash is a rule the <em>repository</em> keeps, not the mapping. SH200 put that rule in
 * every {@code ShFolderRepository} finder, which covers navigation, the CDA, the sitemap, the
 * content lint and the exchange export at once. It cannot cover {@link
 * com.viglet.shio.persistence.repository.object.ShObjectRepository}, which resolves ids
 * polymorphically and knows nothing about {@code deleted_date}. Any production code resolving an
 * id there must come through {@link ShLiveObject}.</p>
 *
 * <h2>Why a lint, and what writing it found</h2>
 *
 * <p>SH200 guarded the two call sites it knew about with a private {@code live} helper each —
 * identical predicate, two copies — and the roadmap line said in as many words that hand-guarded
 * is not enforced. It was right: this lint's first run found <b>two</b> unguarded reads, and one
 * of them was a live defect. {@code ShFolderWriteService#create} resolved its parent through the
 * polymorphic repository, so a section could be created <em>inside</em> a trashed one and then
 * never appear in any listing — because what is excluded is the ancestor, and nothing asks
 * whether a child's ancestor is gone. It answers {@code 200}, it is invisible to every
 * behavioural test that does not think to trash a parent first, and it is character-for-character
 * the SH128 shape that earned {@code ShFolderSiteInvariantTest}.</p>
 *
 * <p>A behavioural test proves the paths it happens to exercise. It cannot prove the <em>next</em>
 * read, and the next read is where the third instance lands. So the guard is textual and total.</p>
 *
 * <h2>The escape hatch</h2>
 *
 * <p>One call site legitimately resolves a trashed folder: {@code ShContentAclService}'s ancestor
 * walk, which decides <b>whose grants apply</b> rather than what a curator may see — a trashed
 * folder still has an ACL, and the restore that brings it back is authorized by it. It carries a
 * {@code ShLiveObject-exempt} comment naming the reason, the same form SH128 established. An
 * exemption is a line of prose in a review, which is the right price for stepping outside an
 * invariant.</p>
 *
 * @author Alexandre Oliveira
 */
@ShSourceScan(value = ShSourceScan.Reads.BOTH,
        reason = "The breach is a `findById` on the polymorphic repository and the waiver is the"
                + " `ShLiveObject-exempt` comment on or above the line naming the reason, which is"
                + " the exemption form ShFolderSiteInvariantTest established.")
class ShLiveObjectInvariantTest {

    private static final Path MAIN_ROOT = Path.of("src/main/java/com/viglet/shio");

    /** The one file allowed to resolve an id without going through the resolver. */
    private static final String RESOLVER = "ShLiveObject.java";

    /** A comment on (or just above) the line waives the rule and says why. */
    private static final String EXEMPT = "ShLiveObject-exempt";

    /** {@code ShObjectRepository shObjectRepository}, {@code (ShObjectRepository repo,} — any. */
    private static final Pattern REPOSITORY_DECLARATION =
            Pattern.compile("\\bShObjectRepository\\s+(\\w+)\\b");

    /** {@code shObjectRepository.findById(...)} — the read that skips the trash. */
    private static final Pattern FIND_BY_ID = Pattern.compile("\\b(\\w+)\\.findById\\s*\\(");

    @Test
    @DisplayName("no production code resolves an object id without dropping a trashed folder")
    void noProductionCodeResolvesAnIdPastTheTrash() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(MAIN_ROOT)) {
            sources.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals(RESOLVER))
                    .sorted()
                    .forEach(path -> violations.addAll(scan(path)));
        }

        assertThat(violations)
                .withFailMessage("""
                        SH226: these resolve an id through the polymorphic repository, which knows \
                        nothing about the folder trash. A trashed section is absent from every \
                        listing and still reachable by its own id, so this reads as content that \
                        is supposed to be deleted, or writes live content into it:
                        %s
                        Fix: ShLiveObject.of(repository, id), which drops a trashed folder, or \
                        ShLiveObject.isLive(object) when the entity is already loaded. If \
                        resolving the trash is genuinely what you mean (an ACL walk, the trash's \
                        own screens), add a `%s: <reason>` comment. See agents.md -> Invariants \
                        that fail silently, and SH200 for where the rule lives for folders.""",
                        String.join("\n", violations), EXEMPT)
                .isEmpty();
    }

    private static List<String> scan(Path source) {
        String text;
        try {
            text = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + source, e);
        }
        String[] lines = text.split("\n", -1);
        Set<String> repositories = repositoryIdentifiers(lines);
        if (repositories.isEmpty()) {
            return List.of();
        }

        List<String> violations = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            String raw = lines[index];
            if (raw.contains(EXEMPT) || (index > 0 && exemptAbove(lines, index))) {
                continue;
            }
            String code = stripComment(raw);
            if (code.isBlank()) {
                continue;
            }
            Matcher matcher = FIND_BY_ID.matcher(code);
            while (matcher.find()) {
                if (repositories.contains(matcher.group(1))) {
                    violations.add("  %s:%d — %s.findById(...)"
                            .formatted(source.getFileName(), index + 1, matcher.group(1)));
                }
            }
        }
        return violations;
    }

    /**
     * Identifiers this file declares as an {@link
     * com.viglet.shio.persistence.repository.object.ShObjectRepository}.
     *
     * <p>Textual and deliberately generous, for {@code ShFolderSiteInvariantTest}'s reason: a name
     * declared as the polymorphic repository anywhere in the file is treated as one everywhere in
     * it. The narrow alternative — flagging every {@code findById} — would fire on
     * {@code ShFolderRepository}, whose finders already exclude the trash, and a rule with false
     * positives is one authors learn to ignore.</p>
     */
    private static Set<String> repositoryIdentifiers(String[] lines) {
        Set<String> names = new LinkedHashSet<>();
        for (String line : lines) {
            String code = stripComment(line);
            Matcher matcher = REPOSITORY_DECLARATION.matcher(code);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    /** An exemption may sit on the comment line(s) directly above the code it waives. */
    private static boolean exemptAbove(String[] lines, int index) {
        for (int probe = index - 1; probe >= 0; probe--) {
            String trimmed = lines[probe].trim();
            if (!trimmed.startsWith("//")) {
                return false;
            }
            if (trimmed.contains(EXEMPT)) {
                return true;
            }
        }
        return false;
    }

    private static String stripComment(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) {
            return "";
        }
        int comment = line.indexOf("//");
        return comment >= 0 ? line.substring(0, comment) : line;
    }

    // --- the invariant itself, so the lint is not the only thing asserting it ---

    @Test
    @DisplayName("a trashed folder is not live; everything else is")
    void aTrashedFolderIsNotLive() {
        ShFolder live = new ShFolder();
        live.setName("Blog");
        ShFolder trashed = new ShFolder();
        trashed.setName("Old");
        trashed.setDeletedDate(java.time.Instant.now());

        assertThat(ShLiveObject.isLive(live)).isTrue();
        assertThat(ShLiveObject.isLive(trashed)).isFalse();
        assertThat(ShLiveObject.isLive(null)).isFalse();

        // ShObject has exactly three subclasses and only one of them has a trash. A site has none
        // at all until SH648, and a post type has none by design. A post is not an ShObject —
        // ShPostUnified is its own root — so it never reaches this resolver, and its trash is
        // already enforced by @SQLRestriction on the mapping. Filtering one here would be a second
        // implementation of a rule that already holds, which is what this class exists to remove.
        assertThat(ShLiveObject.isLive(new ShSite())).isTrue();
        assertThat(ShLiveObject.isLive(new ShPostType())).isTrue();
    }

    @Test
    void theLintDetectsTheFormThatShippedTheDefect() {
        // Guards the guard: a lint that silently matches nothing is worse than none. These are the
        // literal bytes of ShFolderWriteService#create before SH226.
        String[] broken = {
            "    private final ShObjectRepository shObjectRepository;",
            "        return shObjectRepository.findById(parentObjectId).map(parent -> {",
        };
        assertThat(repositoryIdentifiers(broken)).contains("shObjectRepository");
        Matcher matcher = FIND_BY_ID.matcher(broken[1]);
        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(1)).isEqualTo("shObjectRepository");

        // And leaves the two shapes that are not the defect alone: another repository's findById —
        // ShFolderRepository's finders already exclude the trash (SH200) — and the resolved form.
        assertThat(repositoryIdentifiers(new String[] {
            "    private final ShFolderRepository shFolderRepository;",
        })).isEmpty();
        assertThat(FIND_BY_ID.matcher("ShLiveObject.of(shObjectRepository, id)").find()).isFalse();
    }

    @Test
    @DisplayName("the exemption marker waives the line, above it and on it")
    void theExemptionMarkerWaivesTheLine() {
        String[] onTheLine = {
            "    ShObjectRepository repo;",
            "    repo.findById(id); // ShLiveObject-exempt: the trash's own screen",
        };
        assertThat(scanLines(onTheLine)).isEmpty();

        String[] above = {
            "    ShObjectRepository repo;",
            "    // ShLiveObject-exempt: whose grants apply, not what is visible",
            "    repo.findById(id);",
        };
        assertThat(scanLines(above)).isEmpty();

        String[] unwaived = {
            "    ShObjectRepository repo;",
            "    repo.findById(id);",
        };
        assertThat(scanLines(unwaived)).hasSize(1);
    }

    /** The scan body, over lines rather than a file, so the waiver rules can be exercised. */
    private static List<String> scanLines(String[] lines) {
        Set<String> repositories = repositoryIdentifiers(lines);
        List<String> violations = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].contains(EXEMPT) || (index > 0 && exemptAbove(lines, index))) {
                continue;
            }
            Matcher matcher = FIND_BY_ID.matcher(stripComment(lines[index]));
            while (matcher.find()) {
                if (repositories.contains(matcher.group(1))) {
                    violations.add("line " + (index + 1));
                }
            }
        }
        return violations;
    }

    /** Kept so the import of {@link ShObject} is load-bearing rather than decorative. */
    @Test
    void theResolverAnswersAboutObjectsRatherThanFolders() {
        ShObject site = new ShSite();
        assertThat(ShLiveObject.isLive(site)).isTrue();
    }
}
