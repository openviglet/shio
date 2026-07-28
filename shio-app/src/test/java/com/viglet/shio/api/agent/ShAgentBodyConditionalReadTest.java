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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH159 — conditional reads for the content half of the surface.
 *
 * <h2>What SH116 left unconditional, and why it matters most here</h2>
 *
 * <p>SH116 reached the three discovery reads. It did not reach {@code find}, {@code read},
 * {@code changes} or the SH16 post-type listing — and those are the reads an agent makes
 * <em>repeatedly</em>. {@code find} is the read at the top of a loop; {@code changes} is the
 * poll whose honest answer is usually "nothing"; the post-type list is what every
 * {@code shio push --check} and every console editor load fetches. Each of them left
 * unconditional is a full payload the caller pays to learn nothing has changed.</p>
 *
 * <h2>The rule that must not be relaxed</h2>
 *
 * <p>SH116's judgement, restated: an entity tag names a <b>representation</b>. A
 * {@code read} projected to two fields must never answer {@code 304} to a caller that asked
 * for the whole document, and a terse rendering must never satisfy a JSON one. Tagging the
 * rendered bytes rather than a content digest makes that true by construction — which is the
 * point of doing it that way, since the four endpoints here have no digest of their own and
 * four hand-derived ones would be four things that can quietly disagree with what was sent.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentBodyConditionalReadTest {

    private static final String EST_TOKENS = "X-Shio-Est-Tokens";

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private Principal principal;
    private String siteName;
    private String postUrl;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(new ShEstTokensHeaderFilter())
                .build();
        principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn("admin");

        siteName = "sh159-site-" + UUID.randomUUID().toString().substring(0, 8);
        postUrl = "sh159-page";

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");

            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);

            ShWidget widget = entityManager.createQuery(
                            "select w from ShWidget w where w.name = 'Text'", ShWidget.class)
                    .getResultStream().findFirst().orElseGet(() -> {
                        ShWidget created = new ShWidget();
                        created.setName("Text");
                        entityManager.persist(created);
                        return created;
                    });

            ShPostType postType = new ShPostType();
            postType.setName("Sh159Type" + UUID.randomUUID().toString().substring(0, 8));
            postType.setTitle("SH159 type");
            postType.setSystem((byte) 0);
            postType.setDate(when);
            postType.setOwner("admin");
            entityManager.persist(postType);

            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setId(UUID.randomUUID().toString());
            attr.setName("headline");
            attr.setLabel("Headline");
            attr.setOrdinal(1);
            attr.setRequired((byte) 0);
            attr.setIsTitle((byte) 1);
            attr.setShWidget(widget);
            attr.setShPostType(postType);
            entityManager.persist(attr);

            ShPostUnified post = new ShPostUnified();
            post.setId(UUID.randomUUID().toString());
            post.setState(ShPostState.DRAFT);
            post.setTitle("SH159 page");
            post.setFurl("/" + postUrl);
            post.setShSite(site);
            post.setShFolder(root);
            post.setShPostType(postType);
            post.setData("{\"headline\":\"SH159 page\"}");
            post.setDate(when);
            post.setOwner("admin");
            entityManager.persist(post);
        });
    }

    @Test
    @DisplayName("SH159: find revalidates for free — the read at the top of every loop")
    void findRevalidatesToZeroTokens() throws Exception {
        assertRevalidatesFree(get("/api/v2/agent/find").param("site", siteName));
    }

    @Test
    @DisplayName("SH159: find in the terse encoding revalidates too")
    void findTerseRevalidates() throws Exception {
        assertRevalidatesFree(get("/api/v2/agent/find")
                .param("site", siteName).param("format", "terse"));
    }

    @Test
    @DisplayName("SH159: read revalidates for free")
    void readRevalidatesToZeroTokens() throws Exception {
        assertRevalidatesFree(get("/api/v2/agent/read")
                .param("address", "post:" + siteName + "/" + postUrl));
    }

    /**
     * The poll whose honest answer is usually "nothing", and the one this had to be careful
     * about. It revalidates in <b>cursor</b> mode — the way SH88 intends the feed to be walked,
     * carrying the previous response's cursor back as {@code since}. It deliberately does not
     * in window mode, because without {@code since} the response's own cursor <em>is</em> the
     * moving edge of the feed, so two calls a second apart really are different answers.
     *
     * <p>Getting this far turned up a defect worth naming: the lag note used to embed the
     * computed ceiling instant ({@code now - lagMillis}), which made every {@code changes}
     * response different bytes from the last one in <em>both</em> modes — on the endpoint whose
     * entire job is to be polled. It also told the caller nothing actionable, since what a
     * caller can reason about is the lag, and that is a constant.</p>
     */
    @Test
    @DisplayName("SH159: changes revalidates when walked by cursor, as SH88 intends")
    void changesRevalidatesToZeroTokens() throws Exception {
        MvcResult window = perform(get("/api/v2/agent/changes").param("site", siteName), null);
        String cursor = com.jayway.jsonpath.JsonPath.read(
                window.getResponse().getContentAsString(), "$.cursor");

        assertRevalidatesFree(get("/api/v2/agent/changes")
                .param("site", siteName).param("since", cursor));
    }

    @Test
    @DisplayName("SH159: the post-type listing revalidates — every push --check reads it")
    void postTypeListRevalidatesToZeroTokens() throws Exception {
        assertRevalidatesFree(get("/api/v2/post-type"));
    }

    /**
     * The half that would be a data-loss bug rather than a missed saving. A projected read is a
     * different representation of the same state, so it must never satisfy a request for the
     * whole document — a caller that got a {@code 304} here would keep two fields and believe
     * it had them all.
     */
    @Test
    @DisplayName("SH159: a projection never satisfies a request for the whole document")
    void projectionIsItsOwnRepresentation() throws Exception {
        String address = "post:" + siteName + "/" + postUrl;
        String wholeTag = etag(perform(get("/api/v2/agent/read").param("address", address), null));

        MvcResult projected = perform(get("/api/v2/agent/read")
                .param("address", address).param("fields", "headline"), wholeTag);

        assertThat(projected.getResponse().getStatus())
                .as("the tag for the whole document must not match a two-field projection")
                .isEqualTo(200);
        assertThat(etag(projected)).isNotEqualTo(wholeTag);
    }

    /**
     * SH116 wrote two tests purely to keep this distinction; SH159 inherits it. Terse and JSON
     * are one state and two representations.
     */
    @Test
    @DisplayName("SH159: the terse encoding never satisfies a JSON request")
    void encodingIsPartOfTheRepresentation() throws Exception {
        MockHttpServletRequestBuilder json = get("/api/v2/agent/find").param("site", siteName);
        String jsonTag = etag(perform(json, null));

        MvcResult terse = perform(get("/api/v2/agent/find")
                .param("site", siteName).param("format", "terse"), jsonTag);

        assertThat(terse.getResponse().getStatus()).isEqualTo(200);
        assertThat(etag(terse)).isNotEqualTo(jsonTag);
    }

    /**
     * The saving has to survive the content changing, or the tag is measuring the wrong thing.
     */
    @Test
    @DisplayName("SH159: a changed result does not match the old tag")
    void aChangedResultInvalidatesTheTag() throws Exception {
        MockHttpServletRequestBuilder find = get("/api/v2/agent/find").param("site", siteName);
        String before = etag(perform(find, null));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShPostUnified post = entityManager.createQuery(
                            "select p from ShPostUnified p where p.furl = :furl", ShPostUnified.class)
                    .setParameter("furl", "/" + postUrl)
                    .getResultStream().findFirst().orElseThrow();
            post.setTitle("SH159 page, retitled");
            entityManager.merge(post);
        });

        MvcResult after = perform(find, before);
        assertThat(after.getResponse().getStatus())
                .as("a retitled post must not come back as 'nothing changed'")
                .isEqualTo(200);
        assertThat(etag(after)).isNotEqualTo(before);
    }

    private void assertRevalidatesFree(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult first = perform(request, null);
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        String tag = etag(first);
        assertThat(tag).as("a read with no tag cannot be revalidated at all").isNotBlank();
        assertThat(first.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                .as("no-cache means 'store it, ask me first' — a freshness lifetime would "
                        + "serve stale content")
                .contains("no-cache");
        assertThat(tokens(first)).isPositive();

        MvcResult second = perform(request, tag);
        assertThat(second.getResponse().getStatus()).isEqualTo(304);
        assertThat(etag(second))
                .as("a 304 that dropped the tag would make the next request unconditional")
                .isEqualTo(tag);
        assertThat(tokens(second))
                .as("SH159 is a token claim; if a 304 does not measure zero it saved nothing")
                .isZero();
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, String ifNoneMatch)
            throws Exception {
        MockHttpServletRequestBuilder built = request.principal(principal);
        if (ifNoneMatch != null) {
            built = built.header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
        }
        return mockMvc.perform(built).andReturn();
    }

    private static String etag(MvcResult result) {
        return result.getResponse().getHeader(HttpHeaders.ETAG);
    }

    private static int tokens(MvcResult result) {
        String header = result.getResponse().getHeader(EST_TOKENS);
        return header == null ? -1 : Integer.parseInt(header);
    }
}
