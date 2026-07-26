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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.api.agent.memory.ShAgentMemoryService;
import com.viglet.shio.api.agent.memory.dto.ShAgentNoteRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH116 — conditional agent reads, and SH115's third rendering.
 *
 * <p>The claim being tested is a token claim, so the assertions are about the number the
 * instance itself reports: a {@code 304} must measure as <b>zero</b> estimated tokens on
 * {@code X-Shio-Est-Tokens} (SH124), because "re-reading the context is free" is either
 * visible in that header or it is marketing.</p>
 *
 * <p>The other half is the trap this feature makes easy to fall into: an entity tag names
 * a <em>representation</em>, not a state. The SH77 digest deliberately covers content
 * only, so JSON and terse share one — handing both the same tag would let a client that
 * cached terse keep it while asking for JSON. Two tests exist purely to keep that
 * distinction.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentConditionalReadTest {

    private static final String CONTEXT = "/api/v2/agent/context";
    private static final String EST_TOKENS = "X-Shio-Est-Tokens";

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShAgentMemoryService shAgentMemoryService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private Principal principal;
    private String siteName;

    @BeforeAll
    void setup() {
        // The cost header is registered as a servlet FilterRegistrationBean, which
        // webAppContextSetup does not pick up. It is added by hand because the "a 304
        // costs zero tokens" assertion is measured through it — without the filter the
        // test would pass by measuring nothing.
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(new ShEstTokensHeaderFilter())
                .build();
        principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn("admin");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            siteName = "etag-site-" + UUID.randomUUID().toString().substring(0, 8);
            site.setName(siteName);
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            root.setOwner("admin");
            entityManager.persist(root);

            // The model section only renders when there is a model. Seeding is off in
            // tests, so a type is created here rather than relying on whichever other
            // class happened to run first — the AGENTS.md assertion has to be about the
            // renderer, not about test order.
            ShWidget widget = new ShWidget();
            widget.setName("Text");
            entityManager.persist(widget);

            ShPostType postType = new ShPostType();
            postType.setName("EtagType" + UUID.randomUUID().toString().substring(0, 8));
            postType.setTitle("Etag type");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setId(UUID.randomUUID().toString());
            attr.setName("headline");
            attr.setLabel("Headline");
            attr.setOrdinal(1);
            attr.setRequired((byte) 1);
            attr.setShWidget(widget);
            attr.setShPostType(postType);
            entityManager.persist(attr);
        });
    }

    private MvcResult getContext(String query, String ifNoneMatch) throws Exception {
        var request = get(CONTEXT + query).principal(principal);
        if (ifNoneMatch != null) {
            request = request.header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
        }
        return mockMvc.perform(request).andReturn();
    }

    @Test
    void anUnchangedContextCostsAThreeOhFourAndNoTokens() throws Exception {
        MvcResult first = getContext("?site=" + siteName, null);
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotBlank();

        MvcResult second = getContext("?site=" + siteName, etag);

        assertThat(second.getResponse().getStatus()).isEqualTo(304);
        assertThat(second.getResponse().getContentAsString()).isEmpty();
        // The number is the feature. An est-tokens header that still reported the body
        // would mean the cost instrumentation and the caching disagree, and the SH112
        // benchmark reads that header.
        assertThat(second.getResponse().getHeader(EST_TOKENS)).isEqualTo("0");
        // The tag travels on the 304 too, or the *next* request goes out unconditional.
        assertThat(second.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo(etag);
    }

    @Test
    void aChangedInstanceInvalidatesTheTag() throws Exception {
        String before = getContext("?site=" + siteName, null)
                .getResponse().getHeader(HttpHeaders.ETAG);

        shAgentMemoryService.remember(
                new ShAgentNoteRequest(siteName, "etag.probe", "Something changed."), "human");

        MvcResult after = getContext("?site=" + siteName, before);

        assertThat(after.getResponse().getStatus()).isEqualTo(200);
        assertThat(after.getResponse().getHeader(HttpHeaders.ETAG)).isNotEqualTo(before);

        shAgentMemoryService.forget(siteName, "etag.probe");
    }

    @Test
    void jsonAndTerseDoNotShareATag() throws Exception {
        String json = getContext("?site=" + siteName, null)
                .getResponse().getHeader(HttpHeaders.ETAG);
        String terse = getContext("?site=" + siteName + "&format=terse", null)
                .getResponse().getHeader(HttpHeaders.ETAG);

        assertThat(json).isNotEqualTo(terse);
    }

    @Test
    void aTagEarnedInOneRepresentationDoesNotSatisfyAnother() throws Exception {
        String terse = getContext("?site=" + siteName + "&format=terse", null)
                .getResponse().getHeader(HttpHeaders.ETAG);

        // Same state, different bytes. A 304 here would leave the caller holding the
        // terse rendering while believing it had the JSON one.
        assertThat(getContext("?site=" + siteName, terse).getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void theIdsFlagIsPartOfTheRepresentation() throws Exception {
        String without = getContext("?site=" + siteName, null)
                .getResponse().getHeader(HttpHeaders.ETAG);

        assertThat(getContext("?site=" + siteName + "&ids=true", without)
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void theManifestIsConditionalToo() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/v2/agent/manifest").principal(principal)).andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotBlank();

        MvcResult second = mockMvc.perform(get("/api/v2/agent/manifest").principal(principal)
                .header(HttpHeaders.IF_NONE_MATCH, etag)).andReturn();

        assertThat(second.getResponse().getStatus()).isEqualTo(304);
    }

    @Test
    void theBlueprintCatalogueIsConditionalToo() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/v2/agent/blueprints").principal(principal)).andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotBlank();

        MvcResult second = mockMvc.perform(get("/api/v2/agent/blueprints").principal(principal)
                .header(HttpHeaders.IF_NONE_MATCH, etag)).andReturn();

        assertThat(second.getResponse().getStatus()).isEqualTo(304);
    }

    @Test
    void conditionalReadsAdvertiseRevalidationRatherThanFreshness() throws Exception {
        // no-cache means "store it, but ask me first" — not "do not store". A max-age
        // here would serve stale content, which is the one thing a content API must not
        // do to a caller that asked whether anything changed.
        assertThat(getContext("?site=" + siteName, null)
                .getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                .contains("no-cache");
    }

    @Test
    void theAgentsMdRenderingIsMarkdownAndCarriesTheModel() throws Exception {
        MvcResult result = getContext("?site=" + siteName + "&format=agents-md", null);
        String body = result.getResponse().getContentAsString();

        assertThat(result.getResponse().getContentType()).contains("text/markdown");
        assertThat(body)
                .startsWith("# Working with this Shio instance")
                .contains("## Start here")
                .contains("## Content model")
                // Generated files that do not say so get hand-edited, and then the next
                // regeneration silently discards somebody's work.
                .contains("Do not edit by hand")
                // The snapshot must point at the authority rather than pretend to be it.
                .contains("/api/v2/agent/context");
    }

    @Test
    void theAgentsMdRenderingCarriesTheDigestSoAReviewerCanSpotDrift() throws Exception {
        String body = getContext("?site=" + siteName + "&format=agents-md", null)
                .getResponse().getContentAsString();

        assertThat(body).contains("Context digest: `c:");
    }

    @Test
    void anUnknownFormatIsATeachingErrorNotASilentFallbackToJson() throws Exception {
        MvcResult result = getContext("?site=" + siteName + "&format=agent-md", null);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString())
                .contains("agents-md")
                .contains("fix");
    }
}
