/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
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

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;

/**
 * SH128 — build-time guard for the "site-less object" defect class.
 *
 * <h2>The invariant</h2>
 *
 * <p>Only a site's <b>root</b> folder carries {@code shSite}. Any code that needs the
 * owning site of an arbitrary folder must walk to the root — {@link ShFolderSite#of} —
 * and must never read {@code folder.getShSite()} directly.</p>
 *
 * <h2>Why a lint and not (only) a behavioural test</h2>
 *
 * <p>This is the fourth defect of the shape <em>compiles, reviews clean, wrong only when
 * the data is queried along an axis the author was not thinking about</em> — after
 * SH122's three {@code @TenantId} join hazards. {@code ShPostUnifiedWriteService#create}
 * read the folder's site directly and produced a site-less post for every post created
 * below a root: invisible in the console (which navigates by folder) and fatal to the
 * CDA {@code /query}, the by-url lookup and the SH79 address resolver, all of which key
 * on site.</p>
 *
 * <p>A behavioural test proves the paths it happens to exercise. It cannot prove the
 * <em>next</em> write path, and the next write path is precisely where the fifth
 * instance lands. So the guard is textual and total: any production source that reads
 * {@code getShSite()} off an identifier the same file declares as an {@link ShFolder}
 * fails the build. Writing this lint immediately found four more live instances —
 * {@code ShAddressResolver#addressOf(ShFolder)} (nested folders addressed as
 * {@code folder:null/…}), {@code ShCdaService#siteIdOf} (a scoped CDA token denied on
 * every non-root folder, because {@code allowsSite(null)} is false) and
 * {@code #buildPath}, and {@code ShPreviewTokenAPI}'s folder fallback.</p>
 *
 * <h2>The escape hatch</h2>
 *
 * <p>Two call sites legitimately read the field itself rather than resolving ownership:
 * the exchange export, which records {@code shSite} on the root folder only because
 * that is how the schema stores it, and the console XP endpoint, which touches the
 * association to force a LAZY proxy. Both carry a {@code ShFolderSite-exempt} comment
 * naming the reason. An exemption is a line of prose in a review, which is the right
 * price for stepping outside an invariant.</p>
 *
 * @author Alexandre Oliveira
 */
class ShFolderSiteInvariantTest {

    private static final Path MAIN_ROOT = Path.of("src/main/java/com/viglet/shio");

    /** The one file allowed to implement the walk. */
    private static final String RESOLVER = "ShFolderSite.java";

    /** A comment on (or just above) the line waives the rule and says why. */
    private static final String EXEMPT = "ShFolderSite-exempt";

    /** {@code ShFolder foo}, {@code ShFolder foo =}, {@code (ShFolder foo,} — any declaration. */
    private static final Pattern FOLDER_DECLARATION =
            Pattern.compile("\\bShFolder\\s+(\\w+)\\b");

    /** {@code for (ShFolder f = x; …)} and friends are covered by the same pattern. */
    private static final Pattern GET_SITE = Pattern.compile("\\b(\\w+)\\.getShSite\\(\\)");

    /** {@code post.getShFolder().getShSite()} — the same read, one hop further out. */
    private static final Pattern CHAINED_GET_SITE =
            Pattern.compile("\\.getShFolder\\(\\)\\s*\\.getShSite\\(\\)");

    @Test
    @DisplayName("no production code reads getShSite() off a folder — it walks to the root")
    void noProductionCodeReadsTheSiteOffAFolder() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(MAIN_ROOT)) {
            sources.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals(RESOLVER))
                    .sorted()
                    .forEach(path -> violations.addAll(scan(path)));
        }

        assertThat(violations)
                .withFailMessage("""
                        These read the owning site directly off a folder. Only a site's ROOT \
                        folder carries shSite, so for anything nested this silently yields \
                        null — a site-less post, an address of `folder:null/...`, or a CDA \
                        allow-list check that denies everything:
                        %s
                        Fix: ShFolderSite.of(folder) / ShFolderSite.idOf(folder), which walks \
                        to the root. If the direct read is genuinely what you mean (the \
                        exchange export, a LAZY-proxy touch), add a `%s: <reason>` comment. \
                        See agents.md -> Invariants that fail silently, \
                        docs/agents/troubleshooting.md, and IMPROVEMENTS.md SS VIII.10.""",
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
        Set<String> folderIdentifiers = folderIdentifiers(lines);

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
            if (CHAINED_GET_SITE.matcher(code).find()) {
                violations.add("  %s:%d — getShFolder().getShSite()"
                        .formatted(source.getFileName(), index + 1));
            }
            Matcher matcher = GET_SITE.matcher(code);
            while (matcher.find()) {
                if (folderIdentifiers.contains(matcher.group(1))) {
                    violations.add("  %s:%d — %s.getShSite()"
                            .formatted(source.getFileName(), index + 1, matcher.group(1)));
                }
            }
        }
        return violations;
    }

    /**
     * Identifiers this file declares as an {@link ShFolder}. Textual and deliberately
     * generous: a name declared as a folder anywhere in the file is treated as a folder
     * everywhere in it. Shadowing a folder variable with a post of the same name would
     * be a false positive — and would also be worth renaming.
     */
    private static Set<String> folderIdentifiers(String[] lines) {
        Set<String> names = new LinkedHashSet<>();
        for (String line : lines) {
            String code = stripComment(line);
            Matcher matcher = FOLDER_DECLARATION.matcher(code);
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
    @DisplayName("ShFolderSite walks the parent chain to the site-carrying root")
    void resolvesTheOwningSiteFromAnyDepth() {
        ShSite site = new ShSite();
        site.setId("site-1");
        site.setName("Blog");

        ShFolder root = folder("root", null);
        root.setShSite(site);
        ShFolder blog = folder("blog", root);
        ShFolder year = folder("2026", blog);

        assertThat(ShFolderSite.of(root)).isSameAs(site);
        assertThat(ShFolderSite.of(blog)).isSameAs(site);
        assertThat(ShFolderSite.of(year)).isSameAs(site);
        assertThat(ShFolderSite.idOf(year)).isEqualTo("site-1");

        // The exact reads that produced the shipped defects: nested folders carry nothing.
        assertThat(blog.getShSite()).isNull();
        assertThat(year.getShSite()).isNull();
    }

    @Test
    @DisplayName("a detached tree resolves to no site rather than throwing")
    void toleratesATreeWithNoSite() {
        assertThat(ShFolderSite.of(null)).isNull();
        assertThat(ShFolderSite.idOf(folder("orphan", null))).isNull();
    }

    @Test
    void theLintDetectsTheFormThatBrokeProduction() {
        // Guards the guard: a lint that silently matches nothing is worse than none.
        String[] broken = {
            "    private static ShSite siteOf(ShFolder folder) {",
            "        return folder.getShSite();",
            "    }",
        };
        assertThat(folderIdentifiers(broken)).contains("folder");
        assertThat(GET_SITE.matcher(broken[1]).find()).isTrue();
        assertThat(CHAINED_GET_SITE.matcher("site = post.getShFolder().getShSite();").find())
                .isTrue();

        // And leaves the safe form, and a post's own site, alone.
        assertThat(folderIdentifiers(new String[] {"ShPostUnified row = load();"}))
                .doesNotContain("row");
        assertThat(CHAINED_GET_SITE.matcher("ShFolderSite.of(post.getShFolder())").find())
                .isFalse();
    }

    private static ShFolder folder(String name, ShFolder parent) {
        ShFolder folder = new ShFolder();
        folder.setName(name);
        folder.setParentFolder(parent);
        return folder;
    }
}
