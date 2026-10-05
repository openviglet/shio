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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShControllerScan;
import com.viglet.shio.lint.ShP1Inventory;

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

    /**
     * @param covered for an {@link Reason#AGENT_EQUIVALENT}, the HTTP verbs the named agent form
     *                actually delivers (SH463). Empty means <em>all of them</em>, which is what
     *                {@code CONSOLE_ADMIN} and {@code GAP} mean by construction - a human performs
     *                the whole controller, or the whole controller is the gap
     */
    private record Requirement(Reason reason, String why, Set<String> covered) {

        Requirement(Reason reason, String why) {
            this(reason, why, Set.of());
        }
    }

    /**
     * SH463 - an {@code AGENT_EQUIVALENT} entry now names the verbs its agent form covers.
     *
     * <h2>The blindness this closes was in the instrument, not in the code</h2>
     *
     * <p>This lint classified a whole <b>controller root</b>, so {@code /api/v2/site} was
     * {@code AGENT_EQUIVALENT} on the strength of {@code site.upsert} - and that controller mutates
     * with {@code POST}, {@code PUT} <em>and</em> {@code DELETE}. An agent could create a site and
     * could not remove one, and the inventory built to find exactly that read as covered. SH338's
     * {@code ShAgentP1ConformanceTest} could not see it either, for its own reason: it classifies
     * manifest {@code features} keys, and site deletion had no key, so a capability with no flag is
     * not classified at all. Two instruments, one blind spot each, and the same gap fell between
     * them - found by hand, by somebody clearing sixteen scratch sites through the browser's CSRF
     * dance.</p>
     *
     * <p>The rule now is that the <b>capability is the verb</b> and not the controller: an
     * equivalence claim states which verbs it delivers, and the lint compares that against the verbs
     * the source actually declares. A verb the agent form does not cover fails the build with the
     * verb in the message.</p>
     */
    private static Requirement agentEquivalent(Set<String> covered, String why) {
        return new Requirement(Reason.AGENT_EQUIVALENT, why, covered);
    }

    /**
     * Every mutating controller root that is <b>not</b> stateless-writable, and why.
     *
     * <p>This is the inventory SH339 exists to produce. Sorted by path so a diff reads as an
     * addition or a removal rather than a reshuffle.</p>
     */
    private static Map<String, Requirement> sessionRequired() {
        Map<String, Requirement> byPath = new TreeMap<>();

        byPath.put("/api/v2/folder", agentEquivalent(Set.of("POST", "PUT", "DELETE"),
                "folder.upsert / folder.move / folder.delete on POST /api/v2/agent/batch — SH137's "
                + "whole point is that the agent form is safer than the console pair"));
        // SH463 found this one, and it is the instrument paying for itself on its first run: the
        // entry said POST and the controller's two gestures are both PUT. Neither is a gap, and
        // neither had ever been written down as a decision.
        byPath.put("/api/v2/object", agentEquivalent(Set.of("PUT"),
                "copyto/moveto are the console's gestures. `moveto` is post.move and folder.move, "
                + "which share the same writers (SH139). `copyto` has no op and needs none: a copy "
                + "is a read plus a post.upsert at the new address, which is not merely equivalent "
                + "but better - an op that duplicated an object would have to decide what happens "
                + "to its id, and SH79's whole point is that the agent never holds one"));
        // SH463 found the PATCH here too - SH335's partial merge, which had no classification
        // because the row was written before it existed.
        byPath.put("/api/v2/post-unified", agentEquivalent(Set.of("POST", "PUT", "DELETE", "PATCH"),
                "post.upsert / post.move / post.publish / post.delete, and since SH342 a scheduled "
                + "publish is `when` on post.publish. What is still uncovered is /{id}/translate — "
                + "SH342's other half, and the harder one: a locale is part of a post's identity in "
                + "a way an SH79 address does not express (SH323 settled that an address carries no "
                + "locale). It is a sub-path rather than a verb, so the SH463 check cannot see it "
                + "and this sentence is still the only record of it. PATCH is SH335's partial "
                + "merge and has no op deliberately: post.upsert declares WHOLE state (SH168), "
                + "which is the agent's form, and the merge exists for the editor's concurrency "
                + "problem - several curators on one site - which an agent does not have"));
        // SH463 — this row is why the verb granularity exists. It said `site.upsert` and covered
        // POST and PUT; the controller also declares DELETE, and nothing on the agent surface
        // removed a site. The entry passed anyway, which is an inventory measuring its own table.
        byPath.put("/api/v2/site", agentEquivalent(Set.of("POST", "PUT", "DELETE"),
                "site.upsert (SH170) and site.delete (SH463) — and this path is the reason SH170 "
                + "exists, since a stateless caller here gets a bodiless 403 and the CLI's "
                + "e2e/provision.mjs walked the CSRF dance by hand. /{id}/locales (SH1056) is "
                + "covered as a fill: site.upsert declares a policy a site does not have, and "
                + "replacing or clearing one somebody set is the console's by SH184's rule, the "
                + "same boundary url and description have. A sub-path, so the SH463 verb check "
                + "cannot see it and this sentence is the record"));
        // /api/v2/staticfile was here, classified AGENT_EQUIVALENT on the reasoning that the
        // `shio_assets` tool and the SH145 projection covered it. SH437 measured that and it was
        // false in the direction that matters: an agent can *read* the media library over MCP and
        // cannot put bytes into it, because a multipart upload is not a shape JSON-RPC has — which
        // is an argument for the HTTP path being reachable, not for the gap being acceptable. The
        // narrowed exemption is in ShSecurityConfig; this comment is what stops the row coming back.

        // SH1003 — every operation here has an addressed op: `note.add` says something about a
        // page and `note.resolve` closes it, both on /agent/batch and both taking the same address
        // a caller already holds. This route is the console's door to the same rows.
        byPath.put("/api/v2/note", agentEquivalent(Set.of("POST"),
                "note.add / note.resolve"));
        byPath.put("/api/v2/api-token", new Requirement(Reason.CONSOLE_ADMIN,
                "an agent minting or widening its own credential is the escalation Block L is "
                + "built to prevent — the one entry here that must never move"));
        byPath.put("/api/v2/tenant", new Requirement(Reason.CONSOLE_ADMIN,
                "the SH22 tenant catalogue, ROLE_ADMIN-gated; a tenant is the isolation boundary "
                + "the agent's own scoping is derived from"));
        byPath.put("/api/v2/group", new Requirement(Reason.CONSOLE_ADMIN, "identity administration"));
        byPath.put("/api/v2/role", new Requirement(Reason.CONSOLE_ADMIN, "identity administration"));
        byPath.put("/api/v2/user", new Requirement(Reason.CONSOLE_ADMIN, "identity administration"));
        byPath.put("/api/v2/user/current/dock", new Requirement(Reason.CONSOLE_ADMIN,
                "SH971 — what a person's own dock reports; an agent has no dock, and surfaces.json"
                + " classifies it as a console-only preference"));
        byPath.put("/api/v2/user/current/views", new Requirement(Reason.CONSOLE_ADMIN,
                "SH1024 — a person's own names for filtered lists; an agent states its query every"
                + " time, and surfaces.json classifies it as a console-only preference"));
        byPath.put("/api/v2/site/{id}/thumbnail", new Requirement(Reason.CONSOLE_ADMIN,
                "SH1218 — a picture of a site's published home for the console's grid, captured by"
                + " the browser that renders it; the instance runs none, an agent changes the home"
                + " itself and the console re-captures what it published"));
        byPath.put("/api/v2/provider/auth", new Requirement(Reason.CONSOLE_ADMIN,
                "authentication providers — an operator decision about how humans log in"));
        byPath.put("/api/v2/provider/exchange", new Requirement(Reason.CONSOLE_ADMIN,
                "exchange providers — instance wiring, not content"));
        byPath.put("/api/v2/config/email", new Requirement(Reason.CONSOLE_ADMIN,
                "SMTP credentials; an agent writing these could redirect mail"));
        // SH632 — and this one is worth reading rather than skimming, because P1 says a
        // console-only feature is incomplete and this is deliberately console-only.
        //
        // Every other row here is CONSOLE_ADMIN because the capability is *stronger* than the
        // agent's own. This one is not: an agent can already create a folder and a post-type, so
        // naming a folder and a list of types adds nothing it lacks. What `enabled` adds is
        // different in kind — it converts an authenticated capability into a PUBLIC one, opening a
        // door the internet can write through. That is the single decision on this surface a human
        // has to make, and P7 is what makes it a boundary rather than a gap.
        //
        // So the agent's half is not missing: it builds the form — the post-type, the template, the
        // folder — and asks the operator to open the door. Recorded here rather than argued again,
        // and if that ever reads as too strict, the fix is a scope that may configure but not
        // enable, NOT moving this row.
        // SH677 — /api/v2/marketplace was CONSOLE_ADMIN here for one task, and it left this list
        // the way /api/v2/render/** did: the capability became reachable. SH675's *reasoning* was
        // that installing a stranger's package is the operator's act, and that still holds — what
        // was wrong was the mechanism. "Holds a session" does not identify an operator, it
        // identifies a browser, and it made the capability unreachable from a terminal, which is
        // the console-only failure P1's non-goal names outright. The gate is `hasRole("ADMIN")`
        // now, beside the blueprint reload it is strictly larger than.
        byPath.put("/api/v2/site-form", new Requirement(Reason.CONSOLE_ADMIN,
                "where a site's public submissions land and whether it accepts them at all "
                + "(SH632); enabling one converts an authenticated capability into a public write "
                + "path, which is the operator's decision and not the agent's"));
        // SH327 — /api/v2/render/** is CSRF-narrowed now, so it left this list the only way an
        // entry may: the capability became reachable and the lint refused to let the note outlive
        // the gap. It is classified in ShAgentP1ConformanceTest as an AGENT_ROUTE instead.
        byPath.put("/api/v2/widget", new Requirement(Reason.CONSOLE_ADMIN,
                "the widget catalogue is closed and code-owned, and since SH325 it is seeded per "
                + "widget on every startup — so there is nothing an agent should be adding here"));

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
    @DisplayName("SH463: an agent equivalence covers every verb the controller mutates with")
    void anAgentEquivalentCoversEveryMutatingVerb() {
        Map<String, Set<String>> verbs = mutatingVerbs();
        Map<String, Requirement> classified = sessionRequired();

        List<String> uncovered = new ArrayList<>();
        List<String> overclaimed = new ArrayList<>();
        classified.forEach((root, requirement) -> {
            if (requirement.reason() != Reason.AGENT_EQUIVALENT) {
                return;
            }
            Set<String> declared = verbs.getOrDefault(root, Set.of());
            declared.stream().filter(verb -> !requirement.covered().contains(verb))
                    .forEach(verb -> uncovered.add(root + " " + verb));
            requirement.covered().stream().filter(claimed -> !declared.contains(claimed))
                    .forEach(claimed -> overclaimed.add(root + " " + claimed));
        });

        assertThat(uncovered)
                .as("SH463: the capability is the VERB, not the controller. `/api/v2/site` was "
                        + "AGENT_EQUIVALENT on the strength of `site.upsert` while the same "
                        + "controller also declared DELETE - so an agent could create a site and "
                        + "not remove one, and the inventory built to find exactly that read as "
                        + "covered. Clearing sixteen scratch sites then took the browser's CSRF "
                        + "dance, and the conformance suite had hand-rolled a session for it and "
                        + "called the asymmetry \"not this task's to fix\" in a code comment, filed "
                        + "nowhere. Either ship the op that covers this verb, or reclassify the "
                        + "root as a GAP with a task id.")
                .isEmpty();
        assertThat(overclaimed)
                .as("the same rot from the other side (SH142): a claim to cover a verb the "
                        + "controller no longer declares reads as coverage of something that is "
                        + "gone, and is how a table starts describing a surface that has moved.")
                .isEmpty();
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

        // SH974 — the figures agents.md had gone stale on, written where a doc lint can read them.
        Map<String, Object> counts = new TreeMap<>();
        counts.put("mutatingRoots", mutatingRoots().size());
        counts.put("statelessWritable", writable.size());
        byReason.forEach((reason, list) -> counts.put(reason.name(), list.size()));
        ShP1Inventory.record("ShStatelessReachabilityLintTest", counts);

        assertThat(byReason.getOrDefault(Reason.GAP, List.of()))
                .as("""
                        the gaps are the deliverable: each is a capability the product offers and \
                        its own primary operator cannot invoke over HTTP. Closing one is an edit \
                        here; gaining one must be a reviewable diff with a task id.""")
                .as("""
                        **empty, as of SH343.** Every mutating path is now either stateless-writable,                         an AGENT_EQUIVALENT whose capability has an address-native form, or a                         CONSOLE_ADMIN boundary where a stateless route would be an escalation. That                         is the state this inventory was built to reach, and the assertion stays exact                         so the next gap is a reviewable diff with a task id.""")
                .isEmpty();
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

    /** Does an exempt path pattern cover this controller root? {@link ShControllerScan}'s, since SH973. */
    private static boolean covers(String pattern, String root) {
        return ShControllerScan.covers(pattern, root);
    }

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
        return List.copyOf(mutatingVerbs().keySet());
    }

    /**
     * The same scan, keeping the verbs (SH463).
     *
     * <p>The verb was matched and thrown away, which is precisely why a controller could be
     * classified covered on the strength of one of its three mutating methods. Two controllers may
     * share a root, so the sets are merged rather than replaced.</p>
     *
     * <p>SH973 moved the reading itself into {@link ShControllerScan}, because the read half of this
     * question — which GET-only roots an agent can find — needs the same answer about the same
     * program, and two copies of "what is a controller root" is the cheapest way for two inventories
     * to disagree about one file.</p>
     */
    private static Map<String, Set<String>> mutatingVerbs() {
        ShControllerScan.Scan scan = ShControllerScan.of(Set.of("POST", "PUT", "DELETE", "PATCH"));
        assertThat(scan.unresolved())
                .as("every mutating controller must contribute a root, or this lint reports fewer "
                        + "paths than exist and passes — add the spelling to ShControllerScan")
                .isEmpty();
        return new TreeMap<>(scan.byRoot());
    }

    /**
     * The path patterns a session-less caller may write to: every CSRF exemption in the security
     * config, with its constant resolved to the literal.
     */
    private static List<String> statelessWritablePatterns() {
        // SH975 moved the reading itself into ShControllerScan: the P1 table asks the same question
        // of the same configuration, and two readers of one file is how the two came to disagree
        // about the review queue.
        List<String> patterns = ShControllerScan.csrfExemptions();
        assertThat(patterns)
                .as("an empty exemption list would make every mutating root read as session-only, "
                        + "which passes this lint and means nothing")
                .isNotEmpty();
        return patterns;
    }

}
