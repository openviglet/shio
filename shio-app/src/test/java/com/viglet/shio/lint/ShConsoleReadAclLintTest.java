/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.lint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH1027 — which content the console can read without the content ACL being asked.
 *
 * <h2>Why this is a census and not a fix</h2>
 *
 * <p>SH1027 found <em>one</em> such read: console search goes through the CDA, which enforces a
 * token's site allow-list and knows nothing about {@code sh_content_acl}, so on an instance with
 * an ACL set a curator could see titles from folders they cannot open. The task could have fixed
 * that one and moved on. It would then have been the eleventh time this project fixed the
 * instance it was looking at — SH322's rule is that when a task says "and whatever a grep turns
 * up", <b>the grep is the deliverable</b>, and it lands before the fixes because it is what finds
 * the call site nobody read.</p>
 *
 * <h2>What it reads, and why from the console's own source</h2>
 *
 * <p>The population is derived, not listed (SH318's move): every {@code /v2/…} path the React
 * console GETs, taken out of {@code shio-react/src}. A hand-kept list of "reads worth checking"
 * is a list of the ones somebody already thought about, and the read that matters is the one
 * added next year by a page nobody remembers writing.</p>
 *
 * <p>Only <b>content</b> reads are in scope, and the roots that are not content are declared with
 * the reason. A user list is governed by Spring Security roles and there is no object to check an
 * object ACL against; saying so once is cheaper than every future reader working it out again.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
// Code, both sides. The console half is read for `axios.get("/v2/…")` call sites and the Java
// half for mapping bodies, and a sentence in either explaining one of those would be counted
// as one — which is SH206's red build, where prose about a rule was read as the rule.
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShConsoleReadAclLintTest {

    /** The console's source, from the module this test runs in. */
    private static final Path CONSOLE = Path.of("..", "shio-react", "src");

    /** `axios.get<T>("/v2/…")` and its template-literal form, which is most of them. */
    private static final Pattern CONSOLE_GET = Pattern.compile(
            "axios\\.get\\s*(?:<[^>]*>)?\\s*\\(\\s*[`\"](/v2/[^`\"?]*)");

    /** A path segment the console interpolates — every one of them is an id. */
    private static final Pattern INTERPOLATION = Pattern.compile("\\$\\{[^}]*\\}");

    /**
     * The roots that are not content, each with why an object ACL has nothing to say about it.
     *
     * <p>Declared rather than filtered by a heuristic: "does this return a post" is not derivable
     * from a path, and a heuristic that got it wrong would drop a real finding silently.</p>
     */
    private static final Map<String, String> NOT_CONTENT = new TreeMap<>(Map.ofEntries(
            Map.entry("api-token", "a credential, governed by the admin role"),
            Map.entry("config", "instance configuration, governed by the admin role"),
            Map.entry("group", "an identity, governed by the admin role"),
            Map.entry("locale", "the locale catalogue — the same list for everybody"),
            Map.entry("marketplace", "a catalogue of packages, not this instance's content"),
            Map.entry("provider", "auth and exchange providers, governed by the admin role"),
            Map.entry("role", "an identity, governed by the admin role"),
            Map.entry("setup", "the first-access check, which runs before anybody is signed in"),
            Map.entry("site-webhook", "a site's delivery hooks, governed by the admin role"),
            Map.entry("system", "build and instance facts, the same for everybody"),
            Map.entry("tenant", "tenancy, governed by membership rather than by an object ACL"),
            Map.entry("user", "an identity, governed by the admin role"),
            Map.entry("widget", "the widget catalogue, which is the product's own vocabulary")));

    /**
     * Content reads that do <b>not</b> ask the content ACL, each with the reason it need not.
     *
     * <p>This is the list the task exists to shrink, and an entry that stops being true fails the
     * build (SH142's rule) rather than being quietly carried. A bare list would be a wish: the
     * reason is what lets the next reader tell a decision from an oversight.</p>
     */
    private static final Map<String, String> UNCHECKED = new TreeMap<>(Map.ofEntries(
            Map.entry("/api/v2/agent/find",
                    "the agent surface scopes by the credential's site allow-list and is the read "
                            + "SH1027's console search composes over; the per-object ACL is "
                            + "applied by that composition rather than inside a shape (P10)"),
            Map.entry("/api/v2/agent/read",
                    "same surface, same scoping — an address it will not resolve is a 404 there"),
            Map.entry("/api/v2/agent/resolve",
                    "SH970 — an address the stream already handed this reader, turned into the id"
                            + " a console route needs; the stream applied the ACL to the row, and"
                            + " the page the id opens goes through requireRead"),
            Map.entry("/api/v2/agent/memory", "SH1135 — the site editor's provenance card reads the "
                    + "site-scoped notes: decisions and records about a site, never a page's "
                    + "content, scoped by the credential's site allow-list like every agent read"),
            Map.entry("/api/v2/agent/verify",
                    "a report about a site, not a row of content: it names findings, and the site "
                            + "itself is already allow-listed"),
            Map.entry("/api/v2/history",
                    "the audit trail is instance-wide by construction and is an admin read; "
                            + "narrowing it per object is SH1005's question, not this one"),
            Map.entry("/api/v2/history/object/{id}",
                    "one object's trail, reached from a page the reader already opened — and "
                            + "opening it went through requireRead"),
            Map.entry("/api/v2/history/working/{id}",
                    "who else is on a page, for a reader already on it (SH1008)"),
            Map.entry("/api/v2/note/relay",
                    "SH1006 — one boolean about the tenant's agent runs, no content in it"),
            Map.entry("/api/v2/site-form",
                    "SH1066 — each site's form configuration (a folder path, type names, origins),"
                            + " which is operator configuration and not content: SH632 made it"
                            + " console-only under anyRequest().authenticated(), and the submissions"
                            + " the Forms page lists come through /content/search, which asks the ACL"),
            Map.entry("/api/v2/post/xp/{id}",
                    "the Universal Editor bridge, which authenticates with its own short-lived "
                            + "preview token rather than a console session (SH13)"),
            Map.entry("/api/v2/site", "the site list, which the console needs to draw navigation "
                    + "at all; a site the reader may not open still has to be nameable"),
            Map.entry("/api/v2/site/trash", "trashed sites, an admin read for the same reason"),
            Map.entry("/api/v2/site/{id}", "one site's own record — its name, locale and settings"),
            Map.entry("/api/v2/site/{id}/locales", "SH1056 — the languages a site offers, a setting "
                    + "of the site like its record above; the tags it derives from content are the "
                    + "ones every post row already shows, never a post's title or body"),
            Map.entry("/api/v2/agent/patterns", "SH1108 — the patterns pushed to this instance: "
                    + "copies of project files an operator pushed, offered to every curator, and "
                    + "no site's content until one is placed, which is an apply the ACL governs"),
            Map.entry("/api/v2/agent/patterns/{id}", "SH1108 — one of those copies, whole; the same "
                    + "operator file, read the same way"),
            Map.entry("/api/v2/agent/index", "SH1063 — the search-indexing routing table: site "
                    + "names, where each routes and how many pages it has published. Deployment "
                    + "configuration and counts, never a page's content, and the counts are of "
                    + "what is already public"),
            Map.entry("/api/v2/site/{id}/coverage", "SH1060 — which of those languages each page is "
                    + "in. It reports a page's title and address, which is what the folder listing "
                    + "beside it reports, and never a body; a reader who may not open a page still "
                    + "has to see that it is the one missing Portuguese"),
            Map.entry("/api/v2/site/{id}/thumbnail/status", "SH1218 — whether a site's stored "
                    + "home picture is current: two appearance digests and a boolean, never a page's "
                    + "content, and the page they describe is the published home that /sites/<furl>/ "
                    + "already serves to anybody"),
            Map.entry("/api/v2/site/{id}/export",
                    "an SH68 exchange package, guarded by the export role"),
            Map.entry("/api/v2/folder/trash", "the folder trash, which SH222 scopes by site"),
            Map.entry("/api/v2/post-unified/trash", "the post trash, scoped the same way"),
            Map.entry("/api/v2/post-unified/{id}/translations",
                    "the translation group of a post the reader already opened"),
            Map.entry("/api/v2/post-unified/{id}/versions",
                    "the revisions of a post the reader already opened"),
            Map.entry("/api/v2/post-type",
                    "the content model, which is not content: a curator who cannot read a folder "
                            + "may still need to know what an Article is"),
            Map.entry("/api/v2/post-type/{id}",
                    "one type from that same model, keyed by name — the form a post editor loads "
                            + "to know which fields to draw"),
            Map.entry("/api/v2/staticfile/{id}/usage",
                    "which pages reference an asset, for a reader already in the media library"),
            // SH1035 — the same reader, one question further on. It lists sizes and dates of bytes
            // this asset used to have, and it is the ONE read that must stay off /file_source/**:
            // that path is public, so the renditions live outside it and this is how they are seen.
            Map.entry("/api/v2/staticfile/{id}/renditions",
                    "the bytes an asset used to have, for the curator deciding whether to replace "
                            + "it — and the only route to them, since they are deliberately not "
                            + "under the publicly served asset tree")));

    /** One console-reachable read, as this lint sees it. */
    private record Read(String path, boolean checked) {
    }

    @Test
    @DisplayName("SH1027: every content read the console makes is classified, and the list is the debt")
    void everyConsoleContentReadIsClassified() {
        Map<String, Read> reads = consoleContentReads();
        assertThat(reads)
                .as("SH1027: the scan found too few console reads, which is how a census goes "
                        + "green without looking")
                .hasSizeGreaterThanOrEqualTo(15);

        List<String> unchecked = reads.values().stream()
                .filter(read -> !read.checked())
                .map(Read::path)
                .sorted()
                .toList();

        System.out.printf("%n[SH1027] console content reads: %d, of which %d ask the content ACL%n",
                reads.size(), reads.size() - unchecked.size());

        assertThat(unchecked)
                .as("""
                        SH1027: a content read the console can make without the content ACL being \
                        asked. That is not automatically a defect — most of these are reached from \
                        a page whose own open went through requireRead — but every one of them is \
                        a decision, and a decision nobody wrote down is indistinguishable from an \
                        oversight. Add the check, or add the path to UNCHECKED with the reason.""")
                .containsExactlyInAnyOrderElementsOf(new TreeSet<>(UNCHECKED.keySet()));
    }

    @Test
    @DisplayName("SH1027: a declared exception that stopped being needed fails, rather than lingering")
    void noDeclaredExceptionIsStale() {
        Map<String, Read> reads = consoleContentReads();
        List<String> stale = UNCHECKED.keySet().stream()
                .filter(path -> !reads.containsKey(path) || reads.get(path).checked())
                .toList();

        assertThat(stale)
                .as("""
                        SH1027: this path either gained its ACL check or stopped being reachable \
                        from the console. Delete the entry — SH142's rule: an exception nobody \
                        removes is a budget the next task spends without being asked.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH1027: every reason is a sentence, and every non-content root has one")
    void everyClassificationCarriesItsReason() {
        for (Map.Entry<String, String> entry : UNCHECKED.entrySet()) {
            assertThat(entry.getValue())
                    .as("SH1027: %s", entry.getKey())
                    .hasSizeGreaterThan(30);
        }
        for (Map.Entry<String, String> entry : NOT_CONTENT.entrySet()) {
            assertThat(entry.getValue()).as("SH1027: %s", entry.getKey()).hasSizeGreaterThan(20);
        }
        // The guard's own guard: a scan that read nothing would make everything above vacuous.
        assertThat(consolePaths())
                .as("SH1027: no /v2 GET found in the console source — the regex lost its subject")
                .hasSizeGreaterThan(30);
    }

    /** Every {@code /v2/…} path the console GETs, ids normalised, in path order. */
    private static java.util.Set<String> consolePaths() {
        java.util.Set<String> paths = new TreeSet<>();
        try (Stream<Path> files = Files.walk(CONSOLE)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".ts") || path.toString().endsWith(".tsx"))
                    .forEach(path -> {
                        // Through the shared stripper, which is what `@ShSourceScan(CODE)` means
                        // and not a formality: this file's own prose names `axios.get("/v2/…")`
                        // to explain the scan, and a raw read would count the explanation.
                        Matcher found = CONSOLE_GET.matcher(ShSourceText.code(read(path)));
                        while (found.find()) {
                            paths.add(normalise(found.group(1)));
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return paths;
    }

    /**
     * The console's content reads, keyed by path, each said to be ACL-checked or not.
     *
     * <p>Checked means {@code shContentAclService} is named by the mapping's own body <b>or by the
     * one service it delegates to</b>. One hop and no more, which is the honest depth: a
     * controller that hands the whole read to a service is the shape half of this codebase
     * already has (and the shape SH1027's own search takes), so a body-only reader would report
     * those as unchecked and teach the wrong fix. Two hops would be a call graph, and a lint that
     * needs one is a lint nobody can predict the answer of.</p>
     */
    private static Map<String, Read> consoleContentReads() {
        Map<String, String> bodies = contentGetBodies();
        Map<String, String> controllers = contentGetControllers();
        Map<String, Read> reads = new LinkedHashMap<>();
        for (String path : consolePaths()) {
            String root = root(path);
            if (NOT_CONTENT.containsKey(root)) {
                continue;
            }
            String body = bodies.get(path);
            if (body == null) {
                // A path the console spells and no GET mapping declares: a template this lint
                // normalises differently from the controller, or a path that has moved. Reported
                // rather than dropped, because a silent miss is how a census stops checking.
                reads.put(path, new Read(path, false));
                continue;
            }
            reads.put(path, new Read(path, checksAcl(body, controllers.get(path))));
        }
        return reads;
    }

    /** Every GET mapping in every controller, as path → method body, ids normalised. */
    private static Map<String, String> contentGetBodies() {
        Map<String, String> byPath = new LinkedHashMap<>();
        ShControllerScan.sources().forEach((name, body) -> {
            String root = ShControllerScan.root(body);
            for (ShControllerScan.Mapping mapping : ShControllerScan.mappings(body, "Get")) {
                for (String path : mapping.under(root)) {
                    byPath.put(normalise(path), mapping.body());
                }
            }
        });
        return byPath;
    }

    /** The same paths, mapped to the whole controller source that declares them. */
    private static Map<String, String> contentGetControllers() {
        Map<String, String> byPath = new LinkedHashMap<>();
        ShControllerScan.sources().forEach((name, body) -> {
            String root = ShControllerScan.root(body);
            for (ShControllerScan.Mapping mapping : ShControllerScan.mappings(body, "Get")) {
                for (String path : mapping.under(root)) {
                    byPath.put(normalise(path), body);
                }
            }
        });
        return byPath;
    }

    /** Whether this read asks the content ACL, in its own body or in the service it hands off to. */
    private static boolean checksAcl(String mappingBody, String controller) {
        String body = ShSourceText.code(mappingBody);
        if (body.contains(ACL)) {
            return true;
        }
        Matcher call = DELEGATION.matcher(body);
        while (call.find()) {
            String field = call.group(1);
            Matcher declared = Pattern.compile("\\b([A-Z][A-Za-z0-9_]*)\\s+" + field + "\\s*[;,)]")
                    .matcher(controller == null ? "" : ShSourceText.code(controller));
            if (declared.find()
                    && ShSourceText.code(mainSource(declared.group(1))).contains(ACL)) {
                return true;
            }
        }
        return false;
    }

    /** One main class's source by simple name, or {@code ""} when this tree has no such file. */
    private static String mainSource(String simpleName) {
        return MAIN_SOURCES.computeIfAbsent(simpleName, name -> {
            try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
                return files.filter(path -> path.getFileName().toString().equals(name + ".java"))
                        .findFirst().map(ShConsoleReadAclLintTest::read).orElse("");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static final Map<String, String> MAIN_SOURCES = new LinkedHashMap<>();
    private static final String ACL = "shContentAclService";

    /** A call on an injected collaborator: `shSomethingService.read(...)`. */
    private static final Pattern DELEGATION = Pattern.compile("\\b(sh[A-Za-z0-9_]*)\\s*\\.");

    /** A path with every id — interpolated or templated — spelled the same way. */
    private static String normalise(String path) {
        String withoutIds = INTERPOLATION.matcher(path).replaceAll("{id}")
                .replaceAll("\\{[A-Za-z0-9_]+}", "{id}");
        String trimmed = withoutIds.endsWith("/") && withoutIds.length() > 1
                ? withoutIds.substring(0, withoutIds.length() - 1)
                : withoutIds;
        return trimmed.startsWith("/api") ? trimmed : "/api" + trimmed;
    }

    /** The first segment under {@code /api/v2/}, which is the controller family. */
    private static String root(String path) {
        String rest = path.replaceFirst("^/api/v2/", "");
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Kept so a future reader sees the shape this list had when it was written. */
    @SuppressWarnings("unused")
    private static final List<String> HISTORY = new ArrayList<>();
}
