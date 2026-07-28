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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH157 — <b>the round trips, not the payloads.</b>
 *
 * <h2>What the benchmark said</h2>
 *
 * <p>Block M's measurement split cleanly: tokens cleared the concept spec's ≈10× target on
 * three of four canonical tasks, and tool calls cleared nothing — best 3.3× against ≈6×. The
 * traces showed why, and it was not a payload problem. Every task opens with discovery
 * (manifest, then context) and closes with proof (verify); the actual work in between is one
 * or two calls. <em>The framing is the remaining round trips.</em></p>
 *
 * <p>So both ends fold in, and both are opt-in — a caller that does not want them should not
 * pay for them, which is also what keeps every existing response budget intact.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentRoundTripTest {

    private static final String CONTEXT = "/api/v2/agent/context";

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private Principal principal;
    private String siteName;
    private String typeName;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(new ShEstTokensHeaderFilter())
                .build();
        principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn("admin");

        siteName = "sh157-" + UUID.randomUUID().toString().substring(0, 8);
        typeName = "Sh157Type" + UUID.randomUUID().toString().substring(0, 8);

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

            ShWidget widget = entityManager
                    .createQuery("select w from ShWidget w where w.name = 'Text'", ShWidget.class)
                    .getResultStream().findFirst().orElseGet(() -> {
                        ShWidget created = new ShWidget();
                        created.setName("Text");
                        entityManager.persist(created);
                        return created;
                    });

            ShPostType postType = new ShPostType();
            postType.setName(typeName);
            postType.setTitle("SH157 type");
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

            // A reference field, so a write can be made to produce a finding of its own. Without
            // one, every proof this class takes is clean, and the half of the report that
            // attributes findings to the run has nothing to assert against.
            ShWidget reference = widgetNamed("Content Select");
            ShPostTypeAttr related = new ShPostTypeAttr();
            related.setId(UUID.randomUUID().toString());
            related.setName("related");
            related.setLabel("Related");
            related.setOrdinal(2);
            related.setRequired((byte) 0);
            related.setShWidget(reference);
            related.setShPostType(postType);
            entityManager.persist(related);
        });
    }

    /** The catalogue widget by name, created if this test datasource has none. */
    private ShWidget widgetNamed(String name) {
        return entityManager
                .createQuery("select w from ShWidget w where w.name = :name", ShWidget.class)
                .setParameter("name", name)
                .getResultStream().findFirst().orElseGet(() -> {
                    ShWidget created = new ShWidget();
                    created.setName(name);
                    entityManager.persist(created);
                    return created;
                });
    }

    // ── half one: discovery in one call ───────────────────────────────────────────────

    @Test
    @DisplayName("SH157: include=manifest folds a session's two opening reads into one")
    void contextCanCarryCapabilities() throws Exception {
        String body = json(CONTEXT + "?site=" + siteName + "&include=all,manifest");

        assertThat(body)
                .as("features and limits are what a session needs before it acts")
                .contains("\"capabilities\"", "\"features\"", "\"limits\"", "\"startHere\"");
        assertThat(body)
                .as("the endpoint index deliberately stays on /agent/manifest: it is ~87% of "
                        + "that document, it changes only when the build does, and folding it in "
                        + "here would re-send it every time a post changed")
                .doesNotContain("\"endpoints\"");
    }

    /**
     * The section that has to be named. Every other section of the pack is about this
     * instance's content; this one is about the protocol, and a session that already knows it
     * must not be charged for it again — which is the entire cost this block exists to remove.
     */
    @Test
    @DisplayName("SH157: capabilities are absent unless asked for, including from include=all")
    void capabilitiesAreOptIn() throws Exception {
        assertThat(json(CONTEXT + "?site=" + siteName)).doesNotContain("\"capabilities\"");
        assertThat(json(CONTEXT + "?site=" + siteName + "&include=all"))
                .as("`all` means all the sections of the context pack, not the pack plus a "
                        + "different document")
                .doesNotContain("\"capabilities\"");
    }

    /**
     * The digest answers "has the content changed". A build number is not content, and letting
     * one move the digest would send every agent re-reading every site's sitemap the first
     * time the instance was upgraded.
     */
    @Test
    @DisplayName("SH157: capabilities do not move the content digest")
    void capabilitiesAreOutsideTheDigest() throws Exception {
        String plain = digestOf(json(CONTEXT + "?site=" + siteName));
        String withCaps = digestOf(json(CONTEXT + "?site=" + siteName + "&include=all,manifest"));
        assertThat(withCaps).isEqualTo(plain);
    }

    /**
     * A latent SH116 defect this uncovered, worth its own test now that it is fixed: two
     * projections of one unchanged pack were handed the same tag off the same digest, so a
     * caller asking for {@code include=model} could revalidate against a full pack's cache and
     * keep a pack with no sitemap in it.
     */
    @Test
    @DisplayName("SH157: the context tag distinguishes projections, which it did not before")
    void theContextTagCoversInclude() throws Exception {
        String full = etag(perform(CONTEXT + "?site=" + siteName));
        String modelOnly = etag(perform(CONTEXT + "?site=" + siteName + "&include=model"));
        String withCaps = etag(perform(CONTEXT + "?site=" + siteName + "&include=all,manifest"));

        assertThat(full).isNotEqualTo(modelOnly).isNotEqualTo(withCaps);
        assertThat(modelOnly).isNotEqualTo(withCaps);
    }

    @Test
    @DisplayName("SH157: the terse pack carries capabilities too")
    void terseCarriesCapabilities() throws Exception {
        MvcResult result = perform(CONTEXT + "?site=" + siteName
                + "&include=all,manifest&format=terse");
        assertThat(result.getResponse().getContentAsString())
                .contains("\nfeatures ")
                .contains("\nlimits ");
    }

    // ── half two: a write that returns its own proof ──────────────────────────────────

    @Test
    @DisplayName("SH157: verify=true returns the lint over what the write touched")
    void aWriteCanReturnItsOwnProof() throws Exception {
        String body = batch("/api/v2/agent/batch?verify=true", """
                {"ops":[{"op":"post.upsert","address":"post:%s/sh157-proof","type":"%s",\
                "folder":"/","data":{"headline":"Proof"}}]}""".formatted(siteName, typeName));

        assertThat(body).contains("\"proof\"");
        assertThat(body)
                .as("the report must say what it is scoped to — `ok` over a subset is not a "
                        + "statement about the instance (SH143)")
                .contains("\"partial\":true", "\"scope\":\"1 address(es)\"");
        assertThat(body)
                .as("""
                        ...and a clean proof does not pay for the attribution prose. This \
                        assertion used to demand it unconditionally, which asserted a contract \
                        the service deliberately does not keep: two sentences explaining that \
                        none of the zero findings were caused by this run is exactly what P3 \
                        says to stop paying for. The prose is asserted below, where there is \
                        something to attribute.""")
                .doesNotContain("address(es) this run touched");
    }

    /**
     * The other half of the proof: when the lint <em>does</em> find something, the report says
     * the findings belong to the addresses this run touched rather than to the run.
     */
    @Test
    @DisplayName("SH157: a proof with findings attributes them to what the run touched")
    void aProofAttributesItsFindings() throws Exception {
        String body = batch("/api/v2/agent/batch?verify=true", """
                {"ops":[{"op":"post.upsert","address":"post:%s/sh157-dangling","type":"%s",\
                "folder":"/","data":{"headline":"Dangling","related":"no-such-post-id"}}]}"""
                .formatted(siteName, typeName));

        assertThat(body).contains("\"dangling-reference\"", "\"ok\":false");
        assertThat(body)
                .as("a finding attached to a write reads as an accusation unless it says "
                        + "otherwise")
                .contains("address(es) this run touched");
    }

    @Test
    @DisplayName("SH157: without verify=true a write is byte-for-byte what it always was")
    void proofIsOptIn() throws Exception {
        String body = batch("/api/v2/agent/batch", """
                {"ops":[{"op":"post.upsert","address":"post:%s/sh157-quiet","type":"%s",\
                "folder":"/","data":{"headline":"Quiet"}}]}""".formatted(siteName, typeName));

        assertThat(body).doesNotContain("\"proof\"");
    }

    /**
     * Nothing was written, so there is nothing this run could have proved. Linting the current
     * state on a plan would read as "the plan is clean", which is a claim about a future the
     * plan has not created yet.
     */
    @Test
    @DisplayName("SH157: a dry run gets no proof, because it proved nothing")
    void aDryRunIsNotProven() throws Exception {
        String body = batch("/api/v2/agent/batch?verify=true", """
                {"dryRun":true,"ops":[{"op":"post.upsert","address":"post:%s/sh157-dry",\
                "type":"%s","folder":"/","data":{"headline":"Dry"}}]}"""
                .formatted(siteName, typeName));

        assertThat(body).doesNotContain("\"proof\"");
    }

    private String batch(String url, String body) throws Exception {
        return mockMvc.perform(post(url).principal(principal)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
    }

    private MvcResult perform(String url) throws Exception {
        return mockMvc.perform(get(url).principal(principal)).andReturn();
    }

    private String json(String url) throws Exception {
        return perform(url).getResponse().getContentAsString();
    }

    private static String etag(MvcResult result) {
        return result.getResponse().getHeader(HttpHeaders.ETAG);
    }

    private static String digestOf(String body) {
        int at = body.indexOf("\"digest\":\"");
        assertThat(at).as("every pack carries a digest").isNotNegative();
        return body.substring(at + 10, body.indexOf('"', at + 10));
    }
}
