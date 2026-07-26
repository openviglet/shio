/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentBatch.Op;
import com.viglet.shio.api.agent.verify.dto.ShAgentDigest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/**
 * SH101 — the render digest.
 *
 * <p>Two properties carry the feature. First, that the digest reports <b>structure</b>:
 * the heading outline in document order, landmarks, every link and image, and — the one
 * an agent is most often wrong about — the difference between an image with no
 * {@code alt} attribute and one explicitly marked decorative. Second, that the
 * {@code digest} hash moves with the shape and <em>not</em> with the prose, which is what
 * makes "has this page changed?" a string comparison instead of a re-read.</p>
 *
 * <p>Fetching a delivery URL is asserted through its refusals rather than by standing up
 * a server: an endpoint that fetches on the caller's behalf is only safe because of those
 * refusals, so they are the part worth pinning.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentRenderTest {

    @Autowired
    private ShAgentRenderService shAgentRenderService;
    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String postTypeName;
    private String base;

    private static final String PAGE = """
            <header><h1>Pricing</h1></header>
            <main>
              <section>
                <h2>Plans</h2>
                <p>Three plans, all <strong>simple</strong>.</p>
                <a href="/pricing/pro">Pro</a>
                <a href="https://example.com/tos">Terms</a>
                <img src="/file_source/abc/hero.png">
                <img src="/file_source/abc/rule.png" alt="">
              </section>
            </main>
            <footer><h2>Contact</h2></footer>
            """;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            siteName = "render-site-" + UUID.randomUUID();
            site.setName(siteName);
            site.setUrl("https://render.example.com");
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

            ShPostType postType = new ShPostType();
            postTypeName = "RenderPage" + UUID.randomUUID().toString().substring(0, 8);
            postType.setName(postTypeName);
            postType.setTitle("Render Page");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            attr(postType, ShSystemWidget.TEXT, "headline", 1);
            attr(postType, ShSystemWidget.HTML_EDITOR, "body", 2);
        });

        base = "/render-" + UUID.randomUUID().toString().substring(0, 8);
        tx.executeWithoutResult(status -> shAgentBatchService.run(new ShAgentBatch(false, List.of(
                new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + siteName + base,
                        null, null, null),
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + base + "/page",
                        postTypeName, base,
                        objectMapper.valueToTree(Map.of("headline", "Pricing", "body", PAGE))),
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + base + "/blank",
                        postTypeName, base,
                        objectMapper.valueToTree(Map.of("headline", "Blank"))))),
                false, "tester"));
    }

    private void attr(ShPostType postType, String widgetName, String name, int ordinal) {
        ShWidget widget = entityManager
                .createQuery("select w from ShWidget w where w.name = :name", ShWidget.class)
                .setParameter("name", widgetName)
                .getResultStream().findFirst().orElseGet(() -> {
                    ShWidget created = new ShWidget();
                    created.setName(widgetName);
                    created.setDescription(widgetName);
                    entityManager.persist(created);
                    return created;
                });
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName(name);
        attr.setLabel(name);
        attr.setOrdinal(ordinal);
        attr.setIsTitle(ordinal == 1 ? (byte) 1 : (byte) 0);
        attr.setRequired((byte) 0);
        attr.setShWidget(widget);
        attr.setShPostType(postType);
        entityManager.persist(attr);
    }

    private ShAgentDigest digest(String suffix) {
        return shAgentRenderService.digest("post:" + siteName + base + suffix, null);
    }

    @Test
    @DisplayName("the outline is the heading levels and text, in document order")
    void theOutlineIsTheHeadingsInDocumentOrder() {
        ShAgentDigest digest = digest("/page");

        assertThat(digest.source()).isEqualTo("content");
        assertThat(digest.fields()).containsExactly("body");
        assertThat(digest.headings()).extracting(ShAgentDigest.Heading::level)
                .containsExactly(1, 2, 2);
        assertThat(digest.headings()).extracting(ShAgentDigest.Heading::text)
                .containsExactly("Pricing", "Plans", "Contact");
    }

    @Test
    @DisplayName("landmarks are counted, name-sorted")
    void landmarksAreCounted() {
        assertThat(digest("/page").landmarks())
                .containsEntry("header", 1)
                .containsEntry("main", 1)
                .containsEntry("section", 1)
                .containsEntry("footer", 1);
    }

    @Test
    @DisplayName("links are inventoried and internal ones marked")
    void linksAreInventoriedAndMarked() {
        assertThat(digest("/page").links())
                .containsExactly(
                        new ShAgentDigest.Link("/pricing/pro", true, "Pro"),
                        new ShAgentDigest.Link("https://example.com/tos", false, "Terms"));
    }

    @Test
    @DisplayName("a missing alt and an empty alt are different facts")
    void aMissingAltIsNotAnEmptyAlt() {
        // Collapsing these would make the digest unable to report the thing it is most
        // often read for, and would train the agent to "fix" correct decorative markup.
        assertThat(digest("/page").images()).containsExactly(
                new ShAgentDigest.Image("/file_source/abc/hero.png", null),
                new ShAgentDigest.Image("/file_source/abc/rule.png", ""));
    }

    @Test
    @DisplayName("words count visible text only")
    void wordsCountVisibleTextOnly() {
        // "Three plans, all simple." — the <strong> is markup, not a word.
        assertThat(digest("/page").words()).isPositive();
        assertThat(digest("/blank").words()).isZero();
    }

    @Test
    @DisplayName("an empty page is a digest with nothing in it, not an error")
    void anEmptyPageIsStillADigest() {
        ShAgentDigest blank = digest("/blank");

        assertThat(blank.headings()).isEmpty();
        assertThat(blank.fields()).isNull();
        // Distinguishable from "the post has no body field", which is why `fields` exists.
        assertThat(blank.digest()).isNotBlank().isNotEqualTo(digest("/page").digest());
    }

    @Test
    @DisplayName("the digest hash tracks structure, not prose")
    void theDigestTracksStructureAndNotProse() {
        String before = digest("/page").digest();

        try {
            rewriteBody(PAGE.replace("Three plans, all", "Exactly three plans, all"));
            assertThat(digest("/page").digest())
                    .as("a wording change must not move the structural digest")
                    .isEqualTo(before);

            rewriteBody(PAGE.replace("<h2>Contact</h2>", ""));
            assertThat(digest("/page").digest())
                    .as("losing a heading must move it")
                    .isNotEqualTo(before);
        } finally {
            // The fixture is shared across this class, and JUnit does not promise an
            // order — leaving the page one heading short would fail whichever test
            // happened to run next rather than this one.
            rewriteBody(PAGE);
        }
    }

    /** Rewrites {@code /page}'s body in its own transaction. */
    private void rewriteBody(String body) {
        Op op = new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + base + "/page",
                postTypeName, base,
                objectMapper.valueToTree(Map.of("headline", "Pricing", "body", body)));
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                shAgentBatchService.run(new ShAgentBatch(false, List.of(op)), false, "tester"));
    }

    @Test
    @DisplayName("fetching is refused while disabled, and the refusal names the property")
    void fetchingIsRefusedWhileDisabled() {
        assertThatThrownBy(() -> shAgentRenderService.digest(null, "https://render.example.com/x"))
                .isInstanceOf(ShAgentRenderService.FetchDisabledException.class)
                .satisfies(thrown -> assertThat(((ShAgentRenderService.FetchDisabledException) thrown)
                        .problem().toProblemDetail().getProperties())
                        .extractingByKey("fix").asString()
                        .contains("shio.agent.render.allow-fetch"));
    }

    @Test
    @DisplayName("exactly one target: neither, or both, is a teaching 400")
    void exactlyOneTargetIsRequired() {
        assertThatThrownBy(() -> shAgentRenderService.digest(null, null))
                .isInstanceOf(ShAgentRenderService.NoDigestTargetException.class);
        assertThatThrownBy(() -> shAgentRenderService.digest("post:x/y", "https://x/y"))
                .isInstanceOf(ShAgentRenderService.NoDigestTargetException.class);
    }

    @Test
    @DisplayName("a folder address is refused — a digest summarises one page")
    void aFolderIsNotAPage() {
        assertThatThrownBy(() -> shAgentRenderService.digest("folder:" + siteName + base, null))
                .isInstanceOf(ShAgentRenderService.NotAPageException.class);
    }
}
