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

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentBatch.Op;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet.Change;
import com.viglet.shio.api.agent.verify.ShAgentVerifyService;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.render.ShRenderPostTypes;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1064 — a rename that does not break every link into the page.
 *
 * <h2>The assertion that was missing</h2>
 *
 * <p>{@code Redirect} shipped with SH385: the type exists, the renderer serves it, and its
 * {@code PERMANENT} flag chooses 301 over 302. Every one of those has a test. What had none — and
 * what a reader of this code could not have discovered without grepping the write path for a type
 * name — is that <b>no writer ever created one</b>. Renaming {@code /pricing} to {@code /plans}
 * answered 200 at the new URL and 404 at the old one, and nothing in the protocol, the report or
 * the console said so. So the assertions here are about the write and the lint rather than about
 * the redirect: that the vocabulary can leave one behind, that the move's own report says how,
 * and that {@code verify} names an address this site used to serve and does not.</p>
 *
 * <p>Plain {@code @SpringBootTest}: it shares the default context rather than taking one of
 * SH423's slots, and {@code ShRenderPostTypes.ensure()} is idempotent — the system types it seeds
 * are the ones {@code render.provision} seeds for every instance.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShRedirectOpTest {

    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShAgentVerifyService shAgentVerifyService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private ShRenderPostTypes shRenderPostTypes;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String postTypeName;

    @BeforeAll
    void setup() {
        shRenderPostTypes.ensure();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            siteName = "sh1064-" + UUID.randomUUID().toString().substring(0, 8);
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

            ShPostType postType = new ShPostType();
            postTypeName = "Sh1064Page" + UUID.randomUUID().toString().substring(0, 8);
            postType.setName(postTypeName);
            postType.setTitle("Page");
            postType.setSystem((byte) 0);
            postType.setDate(when);
            postType.setOwner("admin");
            entityManager.persist(postType);

            attr(postType, "headline", widget("Text"), 1, true, true);
            attr(postType, "body", widget(ShSystemWidget.HTML_EDITOR), 2, false, false);
        });
    }

    private ShWidget widget(String name) {
        return entityManager
                .createQuery("select w from ShWidget w where w.name = :n", ShWidget.class)
                .setParameter("n", name).getResultStream().findFirst().orElseGet(() -> {
                    ShWidget created = new ShWidget();
                    created.setName(name);
                    created.setDescription(name);
                    entityManager.persist(created);
                    return created;
                });
    }

    private void attr(ShPostType postType, String name, ShWidget widget, int ordinal,
                      boolean required, boolean title) {
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName(name);
        attr.setLabel(name);
        attr.setOrdinal(ordinal);
        attr.setIsTitle((byte) (title ? 1 : 0));
        attr.setRequired((byte) (required ? 1 : 0));
        attr.setShWidget(widget);
        attr.setShPostType(postType);
        entityManager.persist(attr);
    }

    // --- helpers ---

    private ShAgentChangeSet run(boolean dryRun, Op... ops) {
        List<Op> list = List.of(ops);
        return new TransactionTemplate(transactionManager).execute(status ->
                shAgentBatchService.run(new ShAgentBatch(dryRun, list), true, "tester"));
    }

    private Op page(String url, String headline, String... hrefs) {
        StringBuilder body = new StringBuilder("<p>");
        for (String href : hrefs) {
            body.append("<a href=\"").append(href).append("\">this</a> ");
        }
        body.append("</p>");
        return new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + url, postTypeName,
                "/", objectMapper.valueToTree(
                        Map.of("headline", headline, "body", body.toString())));
    }

    private Op publish(String url) {
        return new Op(ShAgentBatchService.POST_PUBLISH, "post:" + siteName + url, null, null, null);
    }

    private Op move(String from, String to) {
        return new Op(ShAgentBatchService.POST_MOVE, "post:" + siteName + from, null, null,
                "post:" + siteName + to, null);
    }

    private Op redirect(String from, String to, Boolean permanent) {
        return new Op(ShAgentBatchService.POST_REDIRECT, "post:" + siteName + from, null, null,
                "post:" + siteName + to,
                permanent == null ? null
                        : objectMapper.valueToTree(Map.of("permanent", permanent)));
    }

    /** Inside a read transaction: the site behind a row is lazy, and the filter walks it. */
    private ShPostUnified at(String url, ShPostState state) {
        return new TransactionTemplate(transactionManager).execute(status ->
                shPostUnifiedRepository.findAll().stream()
                        .filter(post -> post.getShSite() != null
                                && siteName.equals(post.getShSite().getName())
                                && url.equals(post.getFurl()) && post.getState() == state)
                        .findFirst().orElse(null));
    }

    /** The post-type behind a row, read in the session that can still load it. */
    private String typeAt(String url, ShPostState state) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            ShPostUnified post = at(url, state);
            return post == null ? null : post.getShPostType().getName();
        });
    }

    private String field(ShPostUnified post, String name) {
        return objectMapper.readTree(post.getData()).path(name).asString();
    }

    private ShAgentReport verifyRoutes() {
        return shAgentVerifyService.verify(siteName, "routes", 200);
    }

    // --- the tests ---

    @Test
    @DisplayName("SH1064: a move plus a redirect keeps the old URL answering, live, as a 301")
    void moveThenRedirect() {
        run(false, page("/pricing", "Pricing"), publish("/pricing"));

        ShAgentChangeSet changes = run(false, move("/pricing", "/plans"),
                redirect("/pricing", "/plans", null));

        assertThat(changes.results()).extracting(Change::action)
                .containsExactly(Change.MOVE, Change.CREATE);
        ShPostUnified redirect = at("/pricing", ShPostState.PUBLISHED);
        assertThat(redirect)
                .describedAs("SH1064: a redirect nobody published routes nobody — the op that "
                        + "exists to keep a live URL working has to leave a live row behind")
                .isNotNull();
        assertThat(typeAt("/pricing", ShPostState.PUBLISHED))
                .isEqualTo(ShSystemPostType.REDIRECT);
        assertThat(field(redirect, ShSystemPostTypeAttr.TO)).isEqualTo("/plans");
        assertThat(field(redirect, ShSystemPostTypeAttr.PERMANENT))
                .describedAs("SH1064: 302 by default, which is SH385's rule and not this op's — a "
                        + "301 is cached effectively for ever, so the opt-in belongs to whoever is "
                        + "making the claim")
                .isEqualTo("false");
        assertThat(at("/plans", ShPostState.PUBLISHED)).isNotNull();
    }

    @Test
    @DisplayName("SH1064: data.permanent:true asks for a 301, and re-running changes nothing")
    void permanentAndIdempotent() {
        run(false, page("/offer", "Offer"), publish("/offer"));
        run(false, move("/offer", "/offer-2026"));

        run(false, redirect("/offer", "/offer-2026", true));
        assertThat(field(at("/offer", ShPostState.PUBLISHED), ShSystemPostTypeAttr.PERMANENT))
                .isEqualTo("true");

        ShAgentChangeSet again = run(false, redirect("/offer", "/offer-2026", true));
        assertThat(again.results()).extracting(Change::action)
                .describedAs("SH1064: every op here is re-runnable, and a plan that cannot be "
                        + "applied twice is the thing SH82 exists to avoid")
                .containsExactly(Change.NOOP);

        ShAgentChangeSet repointed = run(false, redirect("/offer", "/plans", true));
        assertThat(repointed.results()).extracting(Change::action).containsExactly(Change.UPDATE);
        assertThat(field(at("/offer", ShPostState.PUBLISHED), ShSystemPostTypeAttr.TO))
                .isEqualTo("/plans");
    }

    @Test
    @DisplayName("SH1064: a dry run plans the redirect and writes nothing")
    void dryRunWritesNothing() {
        run(false, page("/team", "Team"), publish("/team"));
        run(false, move("/team", "/about/team"));

        ShAgentChangeSet planned = run(true, redirect("/team", "/about/team", null));

        assertThat(planned.results()).extracting(Change::action).containsExactly(Change.CREATE);
        assertThat(planned.results().get(0).detail()).contains("302");
        assertThat(at("/team", ShPostState.PUBLISHED))
                .describedAs("SH1064: dryRun writes nothing, including the row this op publishes")
                .isNull();
    }

    @Test
    @DisplayName("SH1064: a redirect refuses to shadow a page that still answers")
    void refusesToShadowAPage() {
        run(false, page("/careers", "Careers"), publish("/careers"));

        // The batch re-throws a per-op refusal carrying its problem, so the report can say which
        // op failed; the slug and the teaching body are the refusal's own.
        assertThatThrownBy(() -> run(false, redirect("/careers", "/jobs", null)))
                .isInstanceOf(ShTeachingException.class)
                .hasMessageContaining("Two posts on one URL")
                .extracting(failure -> ((ShTeachingException) failure).problem().slug())
                .isEqualTo("redirect-path-occupied");
    }

    @Test
    @DisplayName("SH1064: verify names an address this site used to serve and no longer does")
    void urlOrphanedAndItsRepair() {
        run(false, page("/docs", "Docs"), publish("/docs"),
                page("/index", "Index", "/docs"), publish("/index"));
        run(false, move("/docs", "/handbook"));

        List<ShAgentReport.Finding> orphaned = verifyRoutes().findings().stream()
                .filter(finding -> "url-orphaned".equals(finding.rule())).toList();

        assertThat(orphaned)
                .describedAs("SH1064: the CMS knows this path was a page here and is not one now, "
                        + "so link-unresolved's four possibilities collapse to one fact")
                .hasSize(1);
        assertThat(orphaned.get(0).address()).isEqualTo("post:" + siteName + "/docs");
        assertThat(orphaned.get(0).message()).contains("1 published page(s) still link to it");
        assertThat(orphaned.get(0).fix()).contains("post.redirect");

        run(false, redirect("/docs", "/handbook", null));

        assertThat(verifyRoutes().findings())
                .describedAs("SH1064: the finding's own fix has to clear the finding, or the "
                        + "report is asking for something that does not help")
                .noneMatch(finding -> "url-orphaned".equals(finding.rule()));
    }
}
