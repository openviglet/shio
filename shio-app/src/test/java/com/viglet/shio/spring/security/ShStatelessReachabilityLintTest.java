/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
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
 * SH339 — <b>every mutating path is usable without a cookie, or says why not.</b>
 *
 * <h2>Why this is not {@code ShCsrfExemptionLintTest}</h2>
 *
 * <p>That lint asks "is this blanket exemption a <em>decision</em>?" — the right question from the
 * security side, and it leaves the operator side entirely unasked. The cost of that gap is a
 * recurring class: SH170 (creating a site needs session + CSRF, worked around by hand in
 * {@code e2e/provision.mjs}), then SH327 ({@code /api/v2/render/**}) and SH335 (the editor demanding a
 * WRITE token) — the same defect three times, each found by a human doing a build. So this is the
 * <b>inverse</b> assertion over the same configuration: not "which paths waive CSRF" but "which paths
 * an agent can write to at all".</p>
 *
 * <p>It is deliberately distinct from SH338's {@code ShAgentP1ConformanceTest} too. That one asks
 * whether a capability has an agent route <em>at all</em>; this one asks whether the route it has can
 * be used by a stateless credential. A capability can pass either and fail the other.</p>
 *
 * <h2>The rule</h2>
 *
 * <p>Every controller with a mutating mapping is either <b>stateless-writable</b> — its path is
 * CSRF-exempt for session-less callers, so HTTP Basic or a token is enough — or classified below with
 * the reason a browser session is required. A path that becomes stateless-writable while still listed
 * as an exception <b>fails the build</b>, which is SH142's rule: an inventory that cannot become
 * stale is the only kind worth asserting.</p>
 *
 * @author Alexandre Oliveira
 */
class ShStatelessReachabilityLintTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "viglet", "shio");
    private static final Path CONFIG = MAIN.resolve(
            Path.of("spring", "security", "ShSecurityConfig.java"));

    /** Why a mutating path may require a browser session. */
    private enum Reason {

        /**
         * The capability has an address-native form on {@code /api/v2/agent/**}, so the id-keyed
         * console path may stay session-only. This is SH170's own resolution: creating a site did
         * not open {@code POST /api/v2/site} to a token, it grew {@code site.upsert}.
         */
        AGENT_EQUIVALENT,
        /**
         * Instance administration a human performs, where a stateless agent route would be a
         * privilege escalation rather than a convenience — an agent minting its own API token is
         * the clearest case (§XII forbids exactly that shape).
         */
        CONSOLE_ADMIN,
        /** A real gap: no agent form, session required, and a task filed for it. */
        GAP
    }

    private record Requirement(Reason reason, String why) {
    }

    /**
     * Every mutating controller root that is <b>not</b> stateless-writable, and why.
     *
     * <p>This is the inventory SH339 exists to produce. Sorted by path so a diff reads as an
     * addition or a removal rather than a reshuffle.</p>
     */
    private static Map<String, Requirement> sessionRequired() {
        Map<String, Requirement> byPath = new TreeMap<>();

        byPath.put("/api/v2/folder", new Requirement(Reason.AGENT_EQUIVALENT,
                "folder.upsert / folder.move / folder.delete on POST /api/v2/agent/batch — SH137's "
                + "whole point is that the agent form is safer than the console pair"));
        byPath.put("/api/v2/object", new Requirement(Reason.AGENT_EQUIVALENT,
                "copyto/moveto are the console's gestures; the agent moves with post.move and "
                + "folder.move, which share the same writers (SH139)"));
        byPath.put("/api/v2/post-unified", new Requirement(Reason.AGENT_EQUIVALENT,
                "post.upsert / post.move / post.publish, and since SH342 a scheduled publish is "
                + "`when` on post.publish. What is still uncovered is /{id}/translate — SH342's "
                + "other half, and the harder one: a locale is part of a post's identity in a way an "
                + "SH79 address does not express (SH323 settled that an address carries no locale)"));
        byPath.put("/api/v2/site", new Requirement(Reason.AGENT_EQUIVALENT,
                "site.upsert (SH170) — and this path is the reason that task exists, since a "
                + "stateless caller here gets a bodiless 403 and the CLI's e2e/provision.mjs walks "
                + "the CSRF dance by hand"));
        byPath.put("/api/v2/staticfile", new Requirement(Reason.AGENT_EQUIVALENT,
                "the shio_assets tool and the SH145 asset projection; a multipart upload from a "
                + "JSON-RPC client is not a shape MCP has"));

        byPath.put("/api/v2/api-token", new Requirement(Reason.CONSOLE_ADMIN,
                "an agent minting or widening its own credential is the escalation Block L is "
                + "built to prevent — the one entry here that must never move"));
        byPath.put("/api/v2/tenant", new Requirement(Reason.CONSOLE_ADMIN,
                "the SH22 tenant catalogue, ROLE_ADMIN-gated; a tenant is the isolation boundary "
                + "the agent's own scoping is derived from"));
        byPath.put("/api/v2/group", new Requirement(Reason.CONSOLE_ADMIN, "identity administration"));
        byPath.put("/api/v2/role", new Requirement(Reason.CONSOLE_ADMIN, "identity administration"));
        byPath.put("/api/v2/user", new Requirement(Reason.CONSOLE_ADMIN, "identity administration"));
        byPath.put("/api/v2/provider/auth", new Requirement(Reason.CONSOLE_ADMIN,
                "authentication providers — an operator decision about how humans log in"));
        byPath.put("/api/v2/provider/exchange", new Requirement(Reason.CONSOLE_ADMIN,
                "exchange providers — instance wiring, not content"));
        byPath.put("/api/v2/config/email", new Requirement(Reason.CONSOLE_ADMIN,
                "SMTP credentials; an agent writing these could redirect mail"));
        // SH327 — /api/v2/render/** is CSRF-narrowed now, so it left this list the only way an
        // entry may: the capability became reachable and the lint refused to let the note outlive
        // the gap. It is classified in ShAgentP1ConformanceTest as an AGENT_ROUTE instead.
        byPath.put("/api/v2/widget", new Requirement(Reason.CONSOLE_ADMIN,
                "the widget catalogue is closed and code-owned, and since SH325 it is seeded per "
                + "widget on every startup — so there is nothing an agent should be adding here"));

        byPath.put("/api/v2/site-webhook", new Requirement(Reason.GAP,
                "SH343 — the agent reads its own webhook delivery failures in /agent/diagnostics "
                + "and cannot touch the subscription that produced them"));
        byPath.put("/api/v2/preview-token", new Requirement(Reason.GAP,
                "SH344 — SH184's answer for an external front end ends in a token the agent has no "
                + "route to mint"));
        return byPath;
    }

    @Test
    @DisplayName("SH339: every mutating path is stateless-writable or classified")
    void everyMutatingPathIsReachableOrClassified() {
        List<String> mutating = mutatingRoots();
        List<String> statelessWritable = statelessWritablePatterns();
        Map<String, Requirement> classified = sessionRequired();

        List<String> unclassified = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (String root : mutating) {
            boolean writable = statelessWritable.stream().anyMatch(pattern -> covers(pattern, root));
            Requirement requirement = classified.get(root);
            if (writable && requirement != null) {
                stale.add(root + " (" + requirement.reason() + ")");
            }
            if (!writable && requirement == null) {
                unclassified.add(root);
            }
        }

        assertThat(unclassified)
                .as("""
                        SH339: a mutating path an agent cannot call without a browser session is \
                        either a deliberate boundary or a P1 break, and the difference has to be \
                        written down — the alternative is what happened three times already (SH170, \
                        SH327, SH335: found by a human doing a build, not by a build failing). \
                        Classify it in sessionRequired(): AGENT_EQUIVALENT if the capability has an \
                        address-native form, CONSOLE_ADMIN if a stateless route would be an \
                        escalation, GAP with a task id if it is neither.""")
                .isEmpty();
        assertThat(stale)
                .as("""
                        this path is now CSRF-exempt for session-less callers, so it is reachable \
                        and the entry must go (SH142's rule — an inventory that can rot is worse \
                        than none, because it reads as a known gap after the gap closed).""")
                .isEmpty();

        assertThat(classified.keySet())
                .as("an entry for a controller root that no longer has a mutating mapping is the "
                        + "same rot from the other side")
                .isSubsetOf(mutating);
    }

    @Test
    @DisplayName("SH339: the inventory is exactly this, and the run prints it")
    void theInventoryIsTheDeliverable() {
        Map<String, Requirement> classified = sessionRequired();
        Map<Reason, List<String>> byReason = new LinkedHashMap<>();
        classified.forEach((path, requirement) -> byReason
                .computeIfAbsent(requirement.reason(), ignored -> new ArrayList<>())
                .add(path));

        List<String> writable = mutatingRoots().stream()
                .filter(root -> statelessWritablePatterns().stream().anyMatch(p -> covers(p, root)))
                .toList();

        System.out.printf("%n[SH339] stateless reachability — %d mutating controller roots%n",
                mutatingRoots().size());
        System.out.printf("  %-16s %2d  %s%n", "WRITABLE", writable.size(), new TreeSet<>(writable));
        new TreeMap<>(byReason).forEach((reason, list) ->
                System.out.printf("  %-16s %2d  %s%n", reason, list.size(), new TreeSet<>(list)));
        System.out.println();

        assertThat(byReason.getOrDefault(Reason.GAP, List.of()))
                .as("""
                        the gaps are the deliverable: each is a capability the product offers and \
                        its own primary operator cannot invoke over HTTP. Closing one is an edit \
                        here; gaining one must be a reviewable diff with a task id.""")
                .containsExactlyInAnyOrder(
                        "/api/v2/preview-token",
                        "/api/v2/site-webhook");
    }

    @Test
    @DisplayName("the guard is not vacuous: the scanner finds mappings and the matcher matches")
    void theGuardCanFail() {
        assertThat(mutatingRoots())
                .as("a scanner that silently found nothing would pass forever, which is the "
                        + "failure mode every lint here is written against")
                .contains("/api/v2/agent", "/api/v2/folder", "/mcp")
                .hasSizeGreaterThanOrEqualTo(20);
        assertThat(statelessWritablePatterns())
                .as("read from the security config, so a reverted narrowing shows up here")
                .contains("/api/v2/agent/**", "/mcp");

        assertThat(covers("/api/v2/agent/**", "/api/v2/agent")).isTrue();
        assertThat(covers("/api/v2/post-type/**", "/api/v2/post-type")).isTrue();
        assertThat(covers("/mcp", "/mcp")).isTrue();
        assertThat(covers("/api/v2/agent/**", "/api/v2/agentx"))
                .as("a prefix match must not treat a longer sibling as covered — /api/v2/agentx "
                        + "would be a different controller")
                .isFalse();
        assertThat(covers("/api/v2/site", "/api/v2/site-webhook"))
                .as("the case that matters most here: /api/v2/site is not a prefix of "
                        + "/api/v2/site-webhook, and treating it as one would hide SH343")
                .isFalse();
    }

    /** Does an exempt path pattern cover this controller root? */
    private static boolean covers(String pattern, String root) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return root.equals(prefix) || root.startsWith(prefix + "/");
        }
        return pattern.equals(root);
    }

    /** {@code @RequestMapping} on a class, in each of the three spellings this codebase uses. */
    private static final Pattern CLASS_MAPPING = Pattern.compile(
            "@RequestMapping\\(\\s*(?:value\\s*=\\s*)?(\"([^\"]*)\"|[A-Za-z_][\\w.]*)");
    private static final Pattern MUTATING_MAPPING = Pattern.compile(
            "@(?:Post|Put|Delete|Patch)Mapping\\b");
    /** {@code public static final String X = "/literal";} — for a mapping given as a constant. */
    private static final Pattern CONSTANT = Pattern.compile(
            "String\\s+(\\w+)\\s*=\\s*\"([^\"]*)\"");

    /**
     * Every controller root that carries at least one mutating mapping.
     *
     * <p>Read from source, like {@code ShCsrfExemptionLintTest}: a booted {@code
     * RequestMappingHandlerMapping} would work and would also make this test require a Spring
     * context for a question that is answered by the code as written.</p>
     *
     * <p>A file with a mutating mapping and <b>no resolvable root</b> is a failure rather than a
     * skip — silent under-reporting is the one way a lint like this goes wrong.</p>
     */
    private static List<String> mutatingRoots() {
        List<String> roots = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        sources().forEach((name, body) -> {
            if (!MUTATING_MAPPING.matcher(body).find()) {
                return;
            }
            Matcher mapping = CLASS_MAPPING.matcher(body);
            if (!mapping.find()) {
                unresolved.add(name + " (no @RequestMapping on the class)");
                return;
            }
            String literal = mapping.group(2);
            if (literal == null) {
                // A constant: `@RequestMapping(ShMcpAPI.MCP_PATH)`. Resolve it in the same file.
                String reference = mapping.group(1);
                String simple = reference.substring(reference.lastIndexOf('.') + 1);
                Matcher constant = CONSTANT.matcher(body);
                while (constant.find()) {
                    if (constant.group(1).equals(simple)) {
                        literal = constant.group(2);
                        break;
                    }
                }
            }
            if (literal == null || literal.isBlank()) {
                unresolved.add(name + " (" + mapping.group(1) + " could not be resolved)");
                return;
            }
            roots.add(literal);
        });

        assertThat(unresolved)
                .as("every mutating controller must contribute a root, or this lint reports fewer "
                        + "paths than exist and passes — add the spelling to CLASS_MAPPING")
                .isEmpty();
        return roots.stream().distinct().sorted().toList();
    }

    /**
     * The path patterns a session-less caller may write to: every CSRF exemption in the security
     * config, with its constant resolved to the literal.
     */
    private static List<String> statelessWritablePatterns() {
        String config = sources().get("ShSecurityConfig.java");
        assertThat(config)
                .as("ShSecurityConfig.java is gone — move this lint with the configuration rather "
                        + "than deleting it")
                .isNotNull();
        int start = config.indexOf(".ignoringRequestMatchers(");
        assertThat(start).as("the CSRF configuration moved; see above").isGreaterThanOrEqualTo(0);
        int end = config.indexOf("))\n", start);
        String block = config.substring(start, end < 0 ? config.length() : end)
                .replaceAll("(?m)//.*$", "");

        Map<String, String> constants = new LinkedHashMap<>();
        Matcher constant = CONSTANT.matcher(config);
        while (constant.find()) {
            constants.put(constant.group(1), constant.group(2));
        }

        List<String> patterns = new ArrayList<>();
        Matcher matcher = Pattern.compile("mvc\\.matcher\\((\\w+)\\)").matcher(block);
        while (matcher.find()) {
            String literal = constants.get(matcher.group(1));
            assertThat(literal)
                    .as("%s is exempted but its literal could not be resolved in the config",
                            matcher.group(1))
                    .isNotNull();
            patterns.add(literal);
        }
        return List.copyOf(patterns);
    }

    private static Map<String, String> sources() {
        try (Stream<Path> files = Files.walk(MAIN)) {
            Map<String, String> byName = new TreeMap<>();
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> byName.put(path.getFileName().toString(), read(path)));
            assertThat(byName).containsKey(CONFIG.getFileName().toString());
            return byName;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path.toAbsolutePath(), e);
        }
    }
}
