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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.dto.ShAgentEndpoint;
import com.viglet.shio.api.agent.dto.ShAgentManifest;

/**
 * SH76 — the agent manifest. What is asserted is the manifest's *contract*, not
 * its current contents: it must be deterministic (so it caches and diffs), it must
 * state unshipped capabilities as {@code false} rather than omitting them (an
 * absent flag sends a caller probing — the cost the manifest exists to remove),
 * and every indexed endpoint must carry a runnable example.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentManifestTest {

    @Autowired
    private ShAgentManifestService shAgentManifestService;

    @Test
    void manifestIdentifiesTheInstanceAndTheProtocol() {
        ShAgentManifest manifest = shAgentManifestService.manifest();

        assertThat(manifest.product()).isEqualTo("Viglet Shio");
        assertThat(manifest.version()).isNotBlank();
        assertThat(manifest.protocol()).isEqualTo(ShAgentManifestService.PROTOCOL);
        assertThat(manifest.apiBase()).isEqualTo("/api/v2");
    }

    @Test
    void manifestIsByteStableAcrossCalls() {
        // Determinism (P5) is what makes the manifest cacheable and diffable; an
        // unstable key order would defeat the SH116 ETag before it ships.
        ShAgentManifest first = shAgentManifestService.manifest();
        ShAgentManifest second = shAgentManifestService.manifest();

        assertThat(second).isEqualTo(first);
        assertThat(List.copyOf(second.features().keySet()))
                .isEqualTo(List.copyOf(first.features().keySet()))
                .isSorted();
    }

    @Test
    void unshippedCapabilitiesAreReportedFalseNotOmitted() {
        ShAgentManifest manifest = shAgentManifestService.manifest();

        assertThat(manifest.features())
                .as("SH76: an unshipped capability is reported `false`, never omitted. That single "
                        + "rule is what the whole document is for — an absent flag cannot be told "
                        + "from a flag nobody wrote, so the caller probes, which is the cost P2 "
                        + "says one cached call replaces. A manifest that dropped its false "
                        + "entries would be smaller, would validate, and would be useless")
                // SH102's PNG: a screenshot is for the human handoff, and advertising it
                // here would invite an agent to spend three orders of magnitude more
                // tokens than the SH101 digest costs to learn less.
                .containsEntry("visualSnapshot", false);
    }

    @Test
    void searchIndexingIsReportedAndIsOffOnAnInstanceNobodyConfigured() {
        // SH84. Two claims, and the second is the one worth a test: the flag is *present*
        // (an absent one sends the agent probing, which is what this document exists to
        // stop) and it is `false` here, because the test instance has no Turing. Publishing
        // and expecting search to work is the mistake it prevents — `search: true` next to
        // it is the SH52 in-DB predicate and answers a different question.
        assertThat(shAgentManifestService.manifest().features())
                .containsEntry("searchIndexing", false);
    }

    @Test
    void aFalseFlagSaysWhereTheCapabilityDoesLive() {
        // SH393 — the pair `features` alone could not express. `visualSnapshot: false` is the only
        // honest answer this surface can give about a PNG, and on its own it reads as "not
        // possible" when the truth is "possible, from the CLI". The flag stays false; the pointer
        // is what stops the agent concluding the wrong thing from it.
        ShAgentManifest manifest = shAgentManifestService.manifest();

        assertThat(manifest.features()).containsEntry("visualSnapshot", false);
        assertThat(manifest.elsewhere())
                .containsKey("visualSnapshot")
                .extractingByKey("visualSnapshot", org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("shio snapshot");
    }

    @Test
    void blueprintsAreReportedShipped() {
        // False through Blocks H, K and I, so the flag flipping means a real package format
        // with a real registry behind it rather than a documented intention (SH92/SH93).
        assertThat(shAgentManifestService.manifest().features())
                .containsEntry("blueprints", true);
        assertThat(shAgentManifestService.manifest().endpoints())
                .anySatisfy(endpoint -> assertThat(endpoint.path()).isEqualTo("/agent/blueprints"));
    }

    @Test
    void theFileProjectionAndItsCursorAreReportedShipped() {
        // Both were false through Blocks H and K on purpose. changeCursor in particular:
        // faking a cursor over a timestamp would have looked resumable while dropping
        // same-timestamp entries, so the flag stayed false until SH88 made the position a
        // (date, id) pair. Flipping it is a claim that catching up cannot lose an edit.
        assertThat(shAgentManifestService.manifest().features())
                .containsEntry("changeCursor", true)
                .containsEntry("contentAsFiles", true);
    }

    @Test
    void theVerificationLoopIsReportedShipped() {
        // contentLint was false for the whole of Block H precisely so that flipping it
        // would mean something: it is now backed by the SH99/SH100 rules rather than by
        // a stub that always passes, and an agent that believes its work was verified
        // when it was not is worse off than one that knows it was not.
        assertThat(shAgentManifestService.manifest().features())
                .containsEntry("contentLint", true)
                .containsEntry("routeCheck", true)
                .containsEntry("renderDigest", true)
                .containsEntry("diagnostics", true);
    }

    @Test
    void shippedCapabilitiesAreReportedTrue() {
        ShAgentManifest manifest = shAgentManifestService.manifest();

        assertThat(manifest.features())
                .containsEntry("graphql", true)
                .containsEntry("search", true)
                .containsEntry("locales", true)
                .containsEntry("exchange", true)
                .containsEntry("postTypesAsCode", true)
                .containsEntry("teachingErrors", true)
                .containsEntry("agentContext", true)
                .containsEntry("terseResponses", true)
                .containsEntry("pathAddressing", true)
                // SH81 flipped `batchWrites` from the false SH76 pre-declared, exactly
                // as intended: a reader that saw the key yesterday needs no new key to
                // notice the capability arrived. `desiredState` (SH82) is new — SH76
                // could not pre-declare a capability nobody had designed yet.
                .containsEntry("batchWrites", true)
                .containsEntry("desiredState", true)
                .containsEntry("mcp", true)
                .containsEntry("fieldProjection", true);
    }

    @Test
    void configuredCapabilitiesTrackTheLiveInstance() {
        ShAgentManifest manifest = shAgentManifestService.manifest();

        // The test profile is single-tenant with no OIDC — the manifest must say so
        // rather than advertising a compiled-in capability that isn't configured.
        assertThat(manifest.features())
                .containsEntry("multiTenant", false)
                .containsEntry("oidc", false);
    }

    @Test
    void limitsPublishTheRateBudgetSoAClientCanPaceItself() {
        var limits = shAgentManifestService.manifest().limits();

        assertThat(limits.rateLimitCapacity()).isPositive();
        assertThat(limits.rateLimitRefillTokens()).isPositive();
        assertThat(limits.rateLimitPeriodSeconds()).isPositive();
        assertThat(limits.defaultPageSize()).isEqualTo(50);
    }

    @Test
    void everyIndexedEndpointIsSelfSufficient() {
        List<ShAgentEndpoint> endpoints = shAgentManifestService.manifest().endpoints();

        assertThat(endpoints).isNotEmpty();
        assertThat(endpoints).allSatisfy(endpoint -> {
            assertThat(endpoint.method()).isNotBlank();
            assertThat(endpoint.path()).startsWith("/");
            assertThat(endpoint.purpose()).isNotBlank();
            // The credential must be stated, or the caller learns it from a 401.
            assertThat(endpoint.auth()).isIn("console", "token:READ", "token:WRITE");
            // The example must be runnable as-is: verb + absolute path.
            assertThat(endpoint.example()).startsWith(endpoint.method() + " /api/v2");
        });
    }

    @Test
    void indexIsCuratedNotTheWholeApi() {
        // P3: the manifest is read every session, so it must stay small on purpose.
        // If this trips, the index is drifting into a full API dump — prune it.
        //
        // SH327 raised this to 31 and **SH344 put it back**: that task needed a row of its own and
        // answered the question the SH327 note left — which of the seven /cda/** rows earns its place
        // — by pruning the two the agent surface fully supersedes. 29 endpoints now, and the token
        // budget went back to 2500 as well. If this trips again, ask the same question before
        // reaching for the number: the count is a proxy and `response.manifest` is the cost.
        //
        // SH1050 raises it to 31, and asked that question first. Nothing here is superseded by
        // `GET /console/summary`; what it supersedes is a SEQUENCE nobody had written down — the
        // review surface, the scheduled listing, the note list and the change feed, four calls an
        // agent makes to find out what is going on before it does anything. A row that replaces
        // four calls is the opposite of an API dump, which is what this count guards against, and
        // the cost of it went through `response.manifest` in the same commit with its own argument.
        assertThat(shAgentManifestService.manifest().endpoints()).hasSizeLessThan(31);
    }

    @Test
    void startHerePointsAtTheOneCallThatReplacesASession() {
        List<String> startHere = shAgentManifestService.manifest().startHere();

        // Until SH601 this asserted `hasSizeLessThan(6)`, on the premise that a long list is a plan
        // the agent has to evaluate and a short one an instruction it can follow. The premise was
        // half right and SH601 is the correction: five ordered lines were still a plan, because a
        // reader arrives with an INTENT and had to map it onto them. So the bound moves rather than
        // being dropped — what must stay short is the part read every time.
        assertThat(startHere.subList(0, startHere.indexOf("then, whichever you need:")))
                .as("""
                        the unconditional preamble is what every reader pays for whatever they came                         to do, so it stays two lines: the one call that replaces a session, and how                         to address content (SH601)""")
                .hasSize(2);
        assertThat(startHere.get(0)).contains("/agent/context");

        // The branch is bounded by the intent table, which is closed and small by construction —
        // and that is the thing to keep watching, because "add a row because the capability exists"
        // is exactly how this becomes the inventory it replaces.
        assertThat(startHere)
                .as("preamble, the branch marker, one line per intent, and the MCP tool names")
                .hasSize(2 + 1 + ShAgentIntents.INTENTS.size() + 1);
    }
}
