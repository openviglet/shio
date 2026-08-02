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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.dto.ShAgentContext;

/**
 * SH346 — the write vocabulary is readable without an MCP client, and nobody pays for it twice.
 *
 * <h2>The gap</h2>
 *
 * <p>SH332 published the op set as {@code shio://ops}, an MCP <b>resource</b>. That is the right
 * shape for the client it serves and it left an HTTP-only agent unable to read the one document
 * that says which keys a write takes — the inverse of SH166, and the P1 break this repository
 * refuses. The rendering was always portable; <em>which</em> REST surface should carry it was the
 * decision, and the rule is that it gets measured rather than argued.</p>
 *
 * <h2>The measurement that chose the surface</h2>
 *
 * <p>{@code ShAgentOps.terse()} measures <b>{@value #ESTIMATED_OPS_TOKENS_NOTE}</b> — which is the
 * number that decided this, and it is larger than it reads: the manifest measures 2343 (SH174), so
 * a manifest section is a <b>19% regression on the first read of every session</b>. The three
 * candidates SH346 weighed cost, respectively:</p>
 *
 * <ul>
 *   <li><b>A manifest section</b> — charged to the one document every session reads first, whether
 *       or not that session ever writes. The manifest is already the largest unconditional read on
 *       the surface (2343 measured at SH174, budgeted 2500), so this is the option that spends the
 *       scarcest tokens on the readers who need it least.</li>
 *   <li><b>A context-pack section</b> — <b>zero</b> for a caller that does not ask, because SH157
 *       had already built the shape for exactly this: a non-default section attached <em>after</em>
 *       the digest is taken. {@link #askingForOpsDoesNotMoveTheDigest()} is that claim as an
 *       assertion, and it is what makes the section free twice over — free for a session that never
 *       writes, and free for one holding a cached pack.</li>
 *   <li><b>A pointer from the endpoint index</b> — cheapest of all, and it is not an alternative:
 *       it is what the {@code /agent/batch} entry now does <em>instead of</em> enumerating op names
 *       in prose, which is the guess {@code ShAgentOps} exists to prevent. So the manifest got
 *       smaller in the same change that closed the gap.</li>
 * </ul>
 *
 * <h2>One document, not two</h2>
 *
 * <p>Every surface serves {@link ShAgentOps#terse()} verbatim, so the MCP resource and the pack
 * cannot drift into disagreeing about what a write takes. That is asserted rather than assumed —
 * a re-encoding here would be the second spelling of the one document whose entire purpose is that
 * there is only one.</p>
 *
 * @author Alexandre Oliveira
 */
// Its own database (SH315): the pack describes whatever content exists, and the digest claim below
// is about two reads of the *same* instance — on the shared datasource a concurrent write would
// make it fail for a reason that has nothing to do with SH346.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-ops-reach;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
class ShAgentOpsReachTest {

    /** Recorded by {@link #theVocabularyCostsWhatSH346Measured()}; quoted in the javadoc above. */
    private static final String ESTIMATED_OPS_TOKENS_NOTE = "447 estimated tokens, ten ops";

    @Autowired
    private ShAgentContextService shAgentContextService;
    @Autowired
    private ShAgentManifestService shAgentManifestService;

    @Test
    @DisplayName("SH346: include=ops carries the vocabulary, and a bare pack does not")
    void theVocabularyIsReachableOverHttp() {
        assertThat(pack(null).ops())
                .as("""
                        a session that is only reading must not be charged for the document that \
                        explains writing — that is the whole reason this went on the pack rather \
                        than on the manifest""")
                .isNull();
        assertThat(pack("all").ops())
                .as("`all` means all the sections of the pack, not the pack plus another document "
                        + "(the SH157 rule, applied to the second such section)")
                .isNull();

        assertThat(pack("ops").ops())
                .as("""
                        the P1 break SH346 closes: an agent with HTTP and no MCP client could not \
                        read which keys a write takes""")
                .isEqualTo(ShAgentOps.terse());
    }

    @Test
    @DisplayName("SH346: the pack and the MCP resource are byte-identical")
    void bothSurfacesHandOverTheSameBytes() {
        // Deliberately not "contains every op name": that would pass while the two renderings drifted
        // in grammar, spacing or key order — and an agent reading one and calling with the other is
        // the failure SH332 exists to prevent, arriving through the fix for SH346.
        assertThat(ShAgentContextTerse.render(pack("ops")))
                .as("one vocabulary, served verbatim wherever it is asked for")
                .contains(ShAgentOps.terse());
    }

    @Test
    @DisplayName("SH346: asking for ops does not move the pack's digest")
    void askingForOpsDoesNotMoveTheDigest() {
        // The claim the surface choice rests on. The digest answers "has the *content* changed", and
        // the op vocabulary changes with the build, not with the content — so an agent holding a
        // cached pack must not be sent re-reading every sitemap because it also asked for the ops
        // block. It holds because the section is attached after the digest is taken (SH157's shape);
        // if that ordering is ever inverted, this is what says so.
        //
        // `all,ops` and not `ops`: naming a section means *only* that section, so `include=ops`
        // legitimately has a different body — it is the cheap read below, not the same pack.
        assertThat(pack("all,ops").digest())
                .as("SH116's tag is over the body, and ops is not body")
                .isEqualTo(pack(null).digest());
    }

    @Test
    @DisplayName("SH346: include=ops on its own is the vocabulary and almost nothing else")
    void theVocabularyAloneIsACheapRead() {
        ShAgentContext justOps = pack("ops");

        // The shape that makes the pointer in the endpoint index worth following: an agent about to
        // write asks for the vocabulary, not for a sitemap it may already hold. Section projection
        // gives this for free — `ops` names one section, so the rest are absent — but it is asserted
        // because it is the reason a round trip here is cheap, and a future default that quietly
        // reinstated the model would make that argument false without failing anything else.
        assertThat(justOps.ops()).isEqualTo(ShAgentOps.terse());
        assertThat(justOps.sitemap()).isEmpty();
        assertThat(justOps.model()).isEmpty();
        assertThat(justOps.conventions()).isEmpty();
    }

    @Test
    @DisplayName("SH346: the endpoint index points at the vocabulary instead of restating it")
    void theManifestPointsRatherThanEnumerates() {
        String batch = shAgentManifestService.manifest().endpoints().stream()
                .filter(endpoint -> "/agent/batch".equals(endpoint.path()))
                .map(com.viglet.shio.api.agent.dto.ShAgentEndpoint::purpose)
                .findFirst().orElseThrow();

        assertThat(batch)
                .as("""
                        the pointer is the third option SH346 weighed and the only one that costs \
                        nothing to add — it replaced prose that listed op names, which is a second \
                        spelling of the vocabulary on the document every session reads""")
                .contains("include=ops");
        assertThat(batch)
                .as("""
                        an enumeration here is how the vocabulary became prose in the first place: \
                        it goes stale silently, and a caller trusts it because it is in the index""")
                .doesNotContain("folder.upsert")
                .doesNotContain("post.upsert")
                .doesNotContain("post.publish");
    }

    @Test
    @DisplayName("SH346: the vocabulary costs what the surface decision assumed")
    void theVocabularyCostsWhatSH346Measured() {
        int tokens = ShTokenBudget.estimate(ShAgentOps.terse());

        // A ceiling with room for two or three more ops, not an equality: the number is an input to
        // a decision, and what would invalidate the decision is this growing into manifest-sized
        // territory — at which point "a section only a writer asks for" stops being a detail and the
        // trade wants re-running. It is asserted at all because an unmeasured cost is the defect
        // `agents.md` names first, and because 447 is what makes the manifest option a 19% regression
        // rather than a rounding error: without the number that sentence is an opinion.
        assertThat(tokens)
                .as("measured %s; a vocabulary that grows past this is a reason to re-open the "
                        + "surface choice, not to raise the number quietly", ESTIMATED_OPS_TOKENS_NOTE)
                .isLessThan(600);
    }

    private ShAgentContext pack(String include) {
        return shAgentContextService.context(null, null, null, include, null, false);
    }
}
