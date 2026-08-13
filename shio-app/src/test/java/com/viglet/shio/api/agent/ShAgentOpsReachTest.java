/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.util.List;
import java.util.stream.Collectors;
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
        // SH222 raised this from 600 to 680, measured at 640, and the raise is the loud kind this
        // message forbids doing quietly — so here is the re-examination it asks for.
        //
        // What SH346 decided was the ops vocabulary versus the MANIFEST as the place a writer
        // learns the keys, and 447 is what made the manifest option a 19% regression rather than a
        // rounding error. Two restore ops do not touch that comparison: they add to both sides
        // equally, because a manifest carrying the vocabulary would carry these too. The trade
        // stands; what has changed is only the vocabulary's size.
        //
        // What the number is now: the room SH346 left — "two or three more ops" — is spent. That
        // was filed as SH647 and answered by the test below, which is why this one keeps a plain
        // total and stops carrying the argument. The measurement it produced: 498 of these 640
        // tokens are per-op PROSE and 88 are the keys, so the growth was never op count; and
        // folding the vocabulary into the manifest would cost 24% of it for every session, writer
        // or not, against the 19% that made SH346 reject that option. Still a regression, so the
        // surface choice stands and the next op is an ordinary act rather than a ceiling move.
        //
        // SH733 raised this from 680 to 720, measured at 696, and here is the re-examination the
        // message below demands rather than a number moved in passing.
        //
        // WHAT BOUGHT THE 16 TOKENS. Two `[data]` keys — `post.delete` and `folder.delete` gained
        // the purge modifier `site.delete` already had — and one grammar line stating it once. It
        // was 30 over before that line: the three ops each explained the same key, and three
        // explanations of one modifier cost more than the modifier. So the trim came first, as it
        // did for SH262, SH342 and SH721, and this is what would not compress further without
        // rewriting contracts SH170 and SH413 own.
        //
        // WHY NOT AN OP. `post.purge` and `folder.purge` were the obvious shape and are what the
        // ceiling exists to argue against: two names on `tools/list` for every caller forever,
        // against two keys read only by a caller who declines the trash. SH413's rule decided it —
        // a modifier of an existing op rides it — and SH648 had already applied that rule to the
        // third delete, so this is the vocabulary staying consistent rather than growing.
        //
        // AND THE SURFACE CHOICE, RE-RUN. 696 against a manifest of 2343 is 30%, worse than the 24%
        // SH647 measured and much worse than the 19% that made SH346 reject the manifest. The
        // comparison has moved further in the direction that already settled it, so the answer is
        // the same and is now better supported than when it was made.
        assertThat(tokens)
                .as("measured %s; a vocabulary that grows past this is a reason to re-open the "
                        + "surface choice, not to raise the number quietly", ESTIMATED_OPS_TOKENS_NOTE)
                .isLessThan(720);
    }

    @Test
    @DisplayName("SH647: the vocabulary's growth is decomposed, so the next raise is not a guess")
    void theGrowthIsProseOrOpsAndTheNumberSaysWhich() throws java.io.IOException {
        String terse = ShAgentOps.terse();
        int total = ShTokenBudget.estimate(terse);
        List<ShAgentOps.OpSpec> ops = ShAgentOps.all();

        // The three parts a single total conflates. SH346's 600 ceiling and SH222's 680 both
        // measured the sum, so "the vocabulary grew" could not be told apart from "the vocabulary
        // gained ops" — and those argue for opposite things. More ops is the surface doing its
        // job; fatter prose per op is drift, and only one of them is a reason to re-open SH346's
        // choice of where a writer learns the keys.
        int header = ShTokenBudget.estimate(terse.lines()
                .filter(line -> line.startsWith("#") || line.startsWith("shio-ops"))
                .collect(Collectors.joining(System.lineSeparator())));
        int purpose = ops.stream()
                .mapToInt(spec -> ShTokenBudget.estimate(spec.purpose()))
                .sum();
        int shape = total - header - purpose;

        // What the manifest alternative costs today — the other half of what SH647 asks for.
        // SH346 rejected it because folding the vocabulary into the manifest makes every session
        // pay for it, writer or not, and 447 against the manifest of the day was a 19% regression.
        // The comparison has to be re-run against today's manifest, not against that ratio.
        int manifest = budget("response.manifest");
        double regression = (100.0 * total) / manifest;

        System.out.printf("%nSH647 — the ops vocabulary decomposed (est. tokens)%n"
                        + "  total %d in %d ops = %.1f per op%n"
                        + "    header/grammar prose  %4d%n"
                        + "    op + keys (the shape) %4d  (%.1f per op)%n"
                        + "    purpose prose         %4d  (%.1f per op)%n"
                        + "  the manifest alternative: +%d on a %d manifest = %.0f%% for every "
                        + "session, writer or not%n"
                        + "  SH346 measured 447 and called %s a 19%% regression%n%n",
                total, ops.size(), (double) total / ops.size(),
                header, shape, (double) shape / ops.size(),
                purpose, (double) purpose / ops.size(),
                total, manifest, regression, "that");

        // Measured 38.3 against a shape of 6.8, which is the answer SH647 asked for: the growth
        // is PROSE, not ops. 498 of 640 tokens are the per-op sentences and 88 are the keys, so
        // "the vocabulary got bigger" and "the vocabulary gained ops" were never the same claim,
        // and the two ceilings that measured only the sum could not tell them apart.
        //
        // 42 rather than 40: an op is a line of keys plus one sentence, and the sentences carry
        // the facts nothing else on this surface carries — which ops need `confirm` from their own
        // dry run, that unknown `data` keys are refused, where an `id:` address comes from. Those
        // are P6, not padding, so this guards the AVERAGE and leaves room for one op that needs
        // two sentences. What it refuses is every op needing two.
        assertThat((double) purpose / ops.size())
                .as("""
                        SH647 — the per-op PROSE, which is the half a total ceiling hides. An op                         is a line of keys plus one sentence saying what it is for; the keys are                         the vocabulary doing its job and the sentence is where drift lives.                         Guarding the average rather than the sum means adding an op costs what an                         op costs, and only writing a paragraph where a clause belongs fails.""")
                .isLessThan(42.0);

        assertThat(regression)
                .as("""
                        SH647 — and the trade itself, re-run rather than inherited. SH346 chose                         the ops vocabulary over the manifest because folding it in makes every                         session pay for it; %d on a %d manifest is what that would cost today.                         Past this, "a section only a writer asks for" stops being a detail and                         the surface choice wants re-opening rather than the ceiling raising."""
                        .formatted(total, manifest))
                .isLessThan(30.0);

        // What this pair replaces: the obligation SH647 left on "the next op", which was that
        // somebody would remember to re-run a comparison by hand. They would not have. The trade
        // is now measured on every build from both sides — how big the vocabulary is per op, and
        // what folding it into the manifest would cost every session — so adding an op is an
        // ordinary act again, and the surface choice re-opens itself when it stops being true.
    }

    private ShAgentContext pack(String include) {
        return shAgentContextService.context(null, null, null, include, null, false);
    }

    private static final String BUDGETS = "/token-budgets.properties";

    /** A declared ceiling, read rather than copied — one place for a number (SH647). */
    private static int budget(String key) throws java.io.IOException {
        java.util.Properties properties = new java.util.Properties();
        try (java.io.InputStream in = ShAgentOpsReachTest.class.getResourceAsStream(BUDGETS)) {
            assertThat(in).as("missing %s", BUDGETS).isNotNull();
            properties.load(in);
        }
        String value = properties.getProperty(key);
        assertThat(value).as("no budget declared for '%s' in %s", key, BUDGETS).isNotNull();
        return Integer.parseInt(value.trim());
    }
}
