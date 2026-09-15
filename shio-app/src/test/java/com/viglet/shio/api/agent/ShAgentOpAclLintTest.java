/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH696 — which batch ops ask the ACL before they write, held as a list rather than read.
 *
 * <h2>The defect this generalises</h2>
 *
 * <p>{@code folder.restore} was the one mutating op in {@link ShAgentBatchService} with no
 * {@code shContentAclService.requireWrite} call. Every other one has had it for blocks —
 * {@code folderDelete} since SH200, {@code postRestore} since SH222 — so a credential scoped to
 * part of a tree could bring back a section it could not have deleted, and the restore carries the
 * whole subtree's posts with it (SH200). SH690 rewrote that method's plan branch four commits
 * earlier and did not notice, which is the point: <b>a missing call is invisible to review</b>,
 * because there is nothing on the screen to look at.</p>
 *
 * <h2>Why a source lint, and what it is honestly worth</h2>
 *
 * <p>SH338's rule — when a review finds a law being broken, the instrument that finds every *other*
 * break lands before the fixes. Here the question is the presence of a call in a method body, which
 * a scan answers faithfully; contrast {@code ShAgentOps}, where a scan was tried for *which keys an
 * op reads* and rejected as unfaithful because handlers reach arguments through helpers.</p>
 *
 * <p>What it cannot do is judge whether the guard is the *right* one, so it is deliberately paired
 * with {@code ShAgentTrashTest}'s behavioural assertion for the op that was actually wrong. This
 * one's job is narrower and is the one nobody was doing: <b>a fourteenth op arriving unguarded
 * fails the build instead of waiting to be read.</b> Every op is classified — the guarded ones and
 * the exempt ones both — and the run prints both counts, because a lint that reports only what it
 * dislikes cannot be told from one that found nothing.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShAgentOpAclLintTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/com/viglet/shio/api/agent/ShAgentBatchService.java");

    /**
     * Ops that write without asking the content ACL, each with the reason it is not a defect.
     *
     * <p>An asserted list, not a ceiling: a ceiling lets one task pay the debt and the next spend
     * it (SH527's rule for the assertion ledger, applied one surface over). Adding an entry here is
     * a decision somebody writes down; adding an op and forgetting the guard is a red build.</p>
     */
    private static final Map<String, String> EXEMPT = new LinkedHashMap<>();

    static {
        EXEMPT.put("render.provision",
                "SH186's addressless op: it installs post-types on the instance, so it acts on no "
                        + "content object and there is no id an ACL could hang on");
        EXEMPT.put("release.upsert",
                "SH993 — it writes a release row and touches no post, so there is no content id to "
                        + "ask about; the site it names is checked by requireSite, and release.add "
                        + "asks the ACL for every post it puts in");
    }

    /**
     * What counts as asking, and the second name is not a loophole.
     *
     * <p>The first draft looked only for {@code shContentAclService.require} and reported
     * <b>three</b> unguarded ops rather than the one this task was filed for. Two of them —
     * {@code site.upsert}'s create branch and {@code site.delete} — carry
     * {@code requireUnscopedCredential} instead, which is a <em>stronger</em> check chosen on
     * purpose: a credential scoped to a list of sites must not be able to create or destroy a
     * member of it, and scoping is an allow-list of what may be read and written rather than a
     * grant over the container. Listing those as exempt would have recorded a guard as an absence,
     * which is the mistake this file exists to prevent, pointed the other way.</p>
     */
    private static final List<String> GUARDS =
            List.of("shContentAclService.require", "requireUnscopedCredential");

    @Test
    @DisplayName("SH696: every mutating batch op asks the content ACL, or is a declared exemption")
    void everyMutatingOpAsksTheAcl() throws IOException {
        String source = Files.readString(SOURCE);
        Map<String, String> handlers = dispatch(source);

        assertThat(handlers)
                .as("""
                        the lint reads the dispatch switch to learn which method serves each op. \
                        An empty map means the switch was reshaped and this test is measuring \
                        nothing — which is the failure mode SH696 exists to name (SH674).""")
                .hasSameSizeAs(ShAgentBatchService.OPS);

        List<String> unguarded = new ArrayList<>();
        List<String> guarded = new ArrayList<>();
        for (String op : ShAgentBatchService.OPS) {
            String body = bodyOf(source, handlers.get(op));
            if (GUARDS.stream().anyMatch(body::contains)) {
                guarded.add(op);
            } else {
                unguarded.add(op);
            }
        }

        System.out.printf("SH696: %d of %d batch ops check authorisation; %d exempt: %s%n",
                guarded.size(), ShAgentBatchService.OPS.size(), unguarded.size(), unguarded);

        assertThat(unguarded)
                .as("""
                        SH696: a batch op writes without checking authorisation and is not on this \
                        test's declared exemption list. `folder.restore` was exactly this for as \
                        long as it existed, and nothing failed — a missing call is invisible to \
                        review because there is nothing on the screen to look at. Either add the \
                        guard, or add the op to EXEMPT with the reason it does not need one.""")
                .containsExactlyInAnyOrderElementsOf(EXEMPT.keySet());
    }

    @Test
    @DisplayName("SH696: the reader is not vacuous — it finds the guard it is looking for")
    void theReaderActuallyReadsBodies() throws IOException {
        // Four green assertions above are consistent with a reader that finds nothing and calls
        // everything exempt, or one that finds everything and calls nothing unguarded. So it is
        // asked about a body it must read correctly in both directions.
        String source = Files.readString(SOURCE);
        Map<String, String> handlers = dispatch(source);

        assertThat(bodyOf(source, handlers.get(ShAgentBatchService.FOLDER_DELETE)))
                .as("folder.delete has guarded since SH200; a reader that misses this is broken")
                .contains("shContentAclService.requireWrite");
        assertThat(bodyOf(source, handlers.get(ShAgentBatchService.RENDER_PROVISION)))
                .as("""
                        and render.provision genuinely has no such call — without this the reader \
                        could be returning the whole file for every op and still look right""")
                .doesNotContain("shContentAclService.require");
    }

    /** Op name → the method the dispatch switch sends it to. */
    private static Map<String, String> dispatch(String source) {
        Map<String, String> byOp = new LinkedHashMap<>();
        Matcher arm = Pattern.compile("case ([A-Z_]+) -> (\\w+)\\(op, session\\);").matcher(source);
        while (arm.find()) {
            constantValue(source, arm.group(1)).ifPresent(op -> byOp.put(op, arm.group(2)));
        }
        return byOp;
    }

    /** The literal a {@code String} constant holds, so the switch's labels resolve to op names. */
    private static java.util.Optional<String> constantValue(String source, String name) {
        Matcher declared = Pattern
                .compile("String " + Pattern.quote(name) + "\\s*=\\s*\"([^\"]+)\"")
                .matcher(source);
        return declared.find() ? java.util.Optional.of(declared.group(1)) : java.util.Optional.empty();
    }

    /**
     * A handler whose whole body hands the op to another method of this class (SH993).
     *
     * <p>{@code release.add} and {@code release.remove} are one implementation with a boolean, the
     * way {@code post.publish} and {@code post.unpublish} share {@code publishById} — except that
     * those two do their own ACL call as well, so the reader never had to follow one. Read without
     * following, a delegate looks exactly like an op with no guard, which would have put two
     * guarded ops on the exemption list: a guard recorded as an absence, the mistake the GUARDS
     * comment above says this file exists to prevent, pointed the other way.</p>
     */
    private static final Pattern DELEGATE =
            Pattern.compile("^\\{\\s*return (\\w+)\\(op, session[^;]*\\);\\s*}$");

    /** One method's body, by brace counting from its signature, through a delegate. */
    private static String bodyOf(String source, String method) {
        return bodyOf(source, method, 2);
    }

    private static String bodyOf(String source, String method, int depth) {
        if (method == null || depth <= 0) {
            return "";
        }
        Matcher signature = Pattern
                .compile("(?:private|public) [\\w<>, .]+ " + Pattern.quote(method) + "\\(")
                .matcher(source);
        if (!signature.find()) {
            return "";
        }
        int open = source.indexOf('{', signature.end());
        int braces = 0;
        for (int at = open; at < source.length(); at += 1) {
            if (source.charAt(at) == '{') {
                braces += 1;
            } else if (source.charAt(at) == '}') {
                braces -= 1;
                if (braces == 0) {
                    String body = source.substring(open, at + 1);
                    Matcher delegate = DELEGATE.matcher(body.replaceAll("\\s+", " ").trim());
                    return delegate.matches() ? bodyOf(source, delegate.group(1), depth - 1) : body;
                }
            }
        }
        return "";
    }
}
