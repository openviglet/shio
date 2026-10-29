/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import java.util.List;

import com.viglet.shio.api.agent.ShAgentReadService;
import com.viglet.shio.api.agent.dto.ShAgentDoc;
import com.viglet.shio.api.agent.dto.ShAgentFound;
import com.viglet.shio.api.agent.mcp.tools.ShAssetsTool;
import com.viglet.shio.api.staticfile.ShAssetLoadService;
import com.viglet.shio.api.staticfile.ShUploadPolicy;
import com.viglet.shio.api.staticfile.ShUploadPolicyProperties;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH727 — <b>an empty listing is not a typo.</b>
 *
 * <h2>The call this is filed on</h2>
 *
 * <p>{@code shio_assets} is a shape over {@code find} with {@code type = File}, and {@code find}
 * refuses a type it cannot resolve. That refusal is right for the caller it was written for —
 * somebody typed {@code Artcle}, and the {@code allowed} list is the whole value of the message.
 * It is wrong here, because nobody typed this name: {@code File} is
 * {@link ShSystemPostType#FILE}, the product's own name for its own row, and an instance that has
 * never held an upload does not have it.</p>
 *
 * <p>So the first {@code shio_assets} of a new install answered <em>"No post-type named 'File' …
 * create a new type with PUT /api/v2/post-type/&#123;name&#125;"</em> — advice naming a system
 * definition whose attributes the message does not list, given to the caller least able to tell a
 * defect from a rule.</p>
 *
 * <h2>Found where the instance was not carrying the test</h2>
 *
 * <p>By SH724's clean-instance conformance run. No Spring test could see it, because a shared test
 * datasource holds whatever every other class created — which is SH724's own shape one tier down.
 * The obvious fix was a {@code @SpringBootTest} with an empty database of its own, and SH423's
 * gauge refused it with <b>zero slots left</b> under the context ceiling.</p>
 *
 * <p>That refusal was right, and not merely a budget: the branch under test is a
 * <em>conditional in the tool</em>, and the only collaborator it needs is a read service that
 * answers whether the type exists. A whole Spring context to arrange one boolean would have bought
 * 1.5 seconds of run time and a slot the next task then could not have. So the collaborator is
 * stubbed, and the assertion is about the branch rather than about Spring.</p>
 *
 * @author Alexandre Oliveira
 */
class ShAssetsToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** The tool with the instance's shipped upload policy, which is what SH951's flags read. */
    private static ShAssetsTool tool(ShAgentReadService reads) {
        // SH1032 — the usage collaborator is a mock rather than a stub with behaviour, because the
        // branches asserted here never reach it: `usage` is off unless asked for, and the no-type
        // branch returns before any row exists. A mock that is never called is the honest fixture.
        return tool(reads,
                // SH1034 — the reference index, mocked for the same reason: the counts are read
                // only when `usage` is asked for, and neither branch here asks.
                mock(com.viglet.shio.api.reference.ShObjectReferenceService.class),
                mock(ShAssetLoadService.class));
    }

    private static ShAssetsTool tool(ShAgentReadService reads,
                                     com.viglet.shio.api.reference.ShObjectReferenceService references,
                                     ShAssetLoadService loads) {
        return new ShAssetsTool(reads, new ShUploadPolicy(new ShUploadPolicyProperties()),
                mock(com.viglet.shio.api.staticfile.ShStaticFileService.class), references, loads);
    }

    /**
     * SH1280 — a theme's font is referenced by no field and loaded by every page. {@code usage}
     * answered {@code used 0} for it, the reading a deletion acts on, while {@code verify} (SH1268)
     * and the media listing's {@code loadedBy} said it was used.
     */
    @Test
    void sh1280UsageSaysHowManyPagesLoadAnAssetNoFieldNames() {
        ShAgentReadService reads = mock(ShAgentReadService.class);
        when(reads.hasType(ShSystemPostType.FILE)).thenReturn(true);
        when(reads.find(any(), eq(ShSystemPostType.FILE), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(new ShAgentFound(1, false, List.of(
                        new ShAgentFound.Row("post:Blog/inter.woff2", "File", "PUBLISHED", "inter.woff2", "f1"))));
        ObjectNode data = objectMapper.createObjectNode();
        data.put(ShSystemPostTypeAttr.FILE, "inter.woff2");
        when(reads.read(any(), any(), anyBoolean())).thenReturn(List.of(
                new ShAgentDoc("post:Blog/inter.woff2", "File", "PUBLISHED", "Blog", List.of(), data,
                        "f1", null, null, null, null, null)));
        var references = mock(com.viglet.shio.api.reference.ShObjectReferenceService.class);
        when(references.referrerCounts(any())).thenReturn(java.util.Map.of());
        ShAssetLoadService loads = mock(ShAssetLoadService.class);
        when(loads.loadedByAssets(any())).thenReturn(
                new ShAssetLoadService.Loads(java.util.Map.of("f1", 48), 48, 48));

        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("usage", true);
        String answer = tool(reads, references, loads).call(arguments);

        assertThat(answer)
                .as("SH1280: the font is referenced by nothing and loaded by all 48 pages, and the "
                        + "line says both, so `used 0` alone no longer reads as deletable")
                .contains("used 0 loaded=48")
                .as("SH1280: a complete walk needs no `rendered` caveat")
                .doesNotContain("rendered=");
    }

    @Test
    void assetsOnAnInstanceWithNoFileTypeAnswersNoneRatherThanRefusing() {
        ShAgentReadService reads = mock(ShAgentReadService.class);
        when(reads.hasType(ShSystemPostType.FILE)).thenReturn(false);

        String answer = tool(reads).call(objectMapper.createObjectNode());

        assertThat(answer)
                .as("SH727: the true answer to \"what files does this site have\" on an instance "
                        + "that has never held one is none. It used to be a refusal.")
                .startsWith("assets 0");
        assertThat(answer)
                .as("SH727: and it must not carry the teaching error either — a caller told to "
                        + "create a post-type it never named would create the wrong `File`, "
                        + "because the system definition's attributes are not in that message.")
                .doesNotContain("No post-type named")
                .doesNotContain("post-type/");
    }

    @Test
    void theListingIsNotEvenAttemptedWhenTheTypeIsAbsent() {
        // The half that says WHY it is not a refusal, and it is the load-bearing one: `find` is the
        // thing that throws, so a fix that still called it and swallowed the exception would pass
        // the assertion above while leaving the refusal one stack frame away — and would swallow a
        // real failure with it. Not asked at all is the property.
        ShAgentReadService reads = mock(ShAgentReadService.class);
        when(reads.hasType(ShSystemPostType.FILE)).thenReturn(false);

        tool(reads).call(objectMapper.createObjectNode());

        verify(reads, never()).find(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void aFileTypeThatExistsIsListedThroughFindAsBefore() {
        // The guard on the narrowing. The branch above must be reachable ONLY when the type is
        // absent; if it ever answered `assets 0` on an instance that has files, this fix would have
        // replaced a bad refusal with a wrong answer, which is the worse of the two (SH263).
        ShAgentReadService reads = mock(ShAgentReadService.class);
        when(reads.hasType(ShSystemPostType.FILE)).thenReturn(true);
        when(reads.find(any(), eq(ShSystemPostType.FILE), any(), any(), any(), anyBoolean(), any()))
                .thenThrow(new IllegalStateException("SH727: find was reached, which is the point"));

        try {
            tool(reads).call(objectMapper.createObjectNode());
        } catch (IllegalStateException expected) {
            assertThat(expected).hasMessageContaining("find was reached");
            return;
        }
        throw new AssertionError(
                "SH727: the tool answered without asking `find` on an instance that HAS the File "
                        + "type. The empty-listing branch is for an absent type only — widened, it "
                        + "would report zero assets to somebody who has some.");
    }

    @Test
    void sh1030ATruncatedListingCarriesTheCursorThatContinuesIt() {
        // The dead end this removes: `truncated` on its own tells a caller it is missing rows and
        // not how to get them. An agent choosing an image from a large library is exactly the
        // caller that meets a cap, and a list it cannot continue reads as "there is no such
        // image" — which is SH146's finding, one surface over.
        ShAgentReadService reads = mock(ShAgentReadService.class);
        when(reads.hasType(ShSystemPostType.FILE)).thenReturn(true);
        when(reads.find(any(), eq(ShSystemPostType.FILE), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(new ShAgentFound(0, true, List.of(), List.of(),
                        "post:Blog/media/last-one", null));

        String answer = tool(reads).call(objectMapper.createObjectNode());

        assertThat(answer)
                .as("""
                        SH1030: the cursor beside the flag, spelled the way `find` spells it.                         Without it the tool says "there is more" and nothing says where.""")
                .contains("truncated")
                .contains("cursor=post:Blog/media/last-one");
    }

    @Test
    void sh1030TheCursorIsPassedBackThroughAsAfter() {
        ShAgentReadService reads = mock(ShAgentReadService.class);
        when(reads.hasType(ShSystemPostType.FILE)).thenReturn(true);
        when(reads.find(any(), eq(ShSystemPostType.FILE), any(), any(), any(), anyBoolean(),
                eq("post:Blog/media/last-one")))
                .thenReturn(new ShAgentFound(0, false, List.of(), List.of(), null, null));

        var arguments = objectMapper.createObjectNode();
        arguments.put("after", "post:Blog/media/last-one");

        assertThat(tool(reads).call(arguments))
                .as("SH1030: a cursor the tool advertises and does not read is worse than none — "
                        + "the caller walks and gets page one for ever")
                .contains("assets");
    }

    /**
     * SH951 — an asset already on disk that this instance would refuse today.
     *
     * <p>The upload policy runs on the way in, which can say nothing about bytes stored before it
     * existed or before it was tightened. An agent choosing between assets is exactly the reader
     * who needs to know, and the row is where it can afford to be told: both are {@code flag}s, so
     * an ordinary asset — every asset on a healthy instance — pays nothing for them (P3).</p>
     */
    @Test
    void sh951AStoredAssetOfADeniedTypeIsFlaggedUnsafe() {
        String answer = listing("post:Blog/payload.html", "payload.html", null);

        assertThat(answer)
                .as("SH951: the policy denies text/html, so bytes stored before it existed are "
                        + "still sitting there and nothing said so")
                .contains("unsafe");
        assertThat(answer).doesNotContain("sanitized");
    }

    @Test
    void sh951ASvgThatWasCleanedSaysSoAndIsNotUnsafe() {
        String answer = listing("post:Blog/logo.svg", "logo.svg", Boolean.TRUE);

        assertThat(answer).contains("sanitized");
        assertThat(answer)
                .as("SH951: a sanitized SVG is the good case — its script is gone, and calling it "
                        + "unsafe too would make the flag mean nothing")
                .doesNotContain("unsafe");
    }

    @Test
    void sh951ASvgNobodyCleanedIsTheOneThisExistsToFind() {
        String answer = listing("post:Blog/old.svg", "old.svg", null);

        assertThat(answer)
                .as("SH951: an SVG stored before the sanitizer shipped still carries whatever it "
                        + "carried, and the upload path can never revisit it")
                .contains("unsafe");
        assertThat(answer).doesNotContain("sanitized");
    }

    @Test
    void sh951AnOrdinaryImageCarriesNeitherFlag() {
        // The control, and the reason both are flags rather than attributes: this is every asset on
        // a healthy instance, and it must cost nothing to say nothing about it.
        String answer = listing("post:Blog/hero.png", "hero.png", null);

        assertThat(answer).doesNotContain("unsafe").doesNotContain("sanitized");
        assertThat(answer).contains("hero.png");
    }

    /** One asset through the tool, with the stored file name and the SANITIZED its post carries. */
    private String listing(String address, String fileName, Boolean sanitized) {
        ShAgentReadService reads = mock(ShAgentReadService.class);
        when(reads.hasType(ShSystemPostType.FILE)).thenReturn(true);
        when(reads.find(any(), eq(ShSystemPostType.FILE), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(new ShAgentFound(1, false,
                        List.of(new ShAgentFound.Row(address, "File", "PUBLISHED", fileName, "id1"))));

        ObjectNode data = objectMapper.createObjectNode();
        data.put(ShSystemPostTypeAttr.FILE, fileName);
        if (sanitized != null) {
            data.put(ShSystemPostTypeAttr.SANITIZED, sanitized.booleanValue());
        }
        when(reads.read(any(), any(), anyBoolean())).thenReturn(List.of(
                new ShAgentDoc(address, "File", "PUBLISHED", "Blog", List.of(), data,
                        "id1", null, null, null, null, null)));

        return tool(reads).call(objectMapper.createObjectNode());
    }
}
