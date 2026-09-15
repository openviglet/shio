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

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.dto.ShAgentDoc;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH996 — which URLs a publish would move, before the button writes.
 *
 * <p>{@code include=impact} answered template blast radius, and a curator about to publish is asking
 * a different question: what starts resolving, what moves, and what breaks. The read now answers both,
 * and stays silent for the publish that moves nothing — which is almost every publish, and the reason
 * an ordinary response is byte-identical to what it was.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentPublishRoutesTest {

    private static final String RUN = UUID.randomUUID().toString().substring(0, 8);
    private static final String SITE = "sh996-" + RUN;

    @Autowired
    private ShAgentReadService shAgentReadService;
    @Autowired
    private ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final Set<String> posts = new HashSet<>();

    @BeforeAll
    void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant then = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(SITE);
            site.setDate(then);
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(then);
            root.setOwner("admin");
            entityManager.persist(root);

            ShWidget text = entityManager.find(ShWidget.class,
                    shWidgetRepository.findFirstByName(ShSystemWidget.TEXT).getId());
            ShPostType type = new ShPostType();
            type.setName("PT-SH996-" + RUN);
            type.setTitle("SH996 Page");
            type.setSystem((byte) 0);
            type.setDate(then);
            type.setOwner("admin");
            ShPostTypeAttr title = new ShPostTypeAttr();
            title.setName(ShSystemPostTypeAttr.TITLE);
            title.setLabel("Title");
            title.setOrdinal(1);
            title.setRequired((byte) 0);
            title.setIsTitle((byte) 1);
            title.setIsSummary((byte) 0);
            title.setShWidget(text);
            title.setShPostType(type);
            // A markup field, because the inbound scanner reads the fields whose widget holds markup
            // — a Text field with an href in it is not a link anybody follows.
            ShWidget html = entityManager.find(ShWidget.class,
                    shWidgetRepository.findFirstByName(ShSystemWidget.HTML_EDITOR).getId());
            ShPostTypeAttr body = new ShPostTypeAttr();
            body.setName("body");
            body.setLabel("Body");
            body.setOrdinal(2);
            body.setRequired((byte) 0);
            body.setIsTitle((byte) 0);
            body.setIsSummary((byte) 0);
            body.setShWidget(html);
            body.setShPostType(type);
            type.setShPostTypeAttrs(new HashSet<>(Set.of(title, body)));
            entityManager.persist(type);

            for (String url : List.of("/new-page", "/renamed", "/steady", "/linker")) {
                ShPostUnified draft = new ShPostUnified();
                draft.setId(SITE + url.replace('/', '-'));
                draft.setState(ShPostState.DRAFT);
                draft.setShSite(site);
                draft.setShFolder(root);
                draft.setShPostType(type);
                draft.setTitle(url);
                draft.setFurl(url);
                draft.setLocale("en");
                draft.setOwner("admin");
                draft.setDate(then);
                draft.setData(url.equals("/linker")
                        ? "{\"title\":\"linker\",\"body\":\"<a href=\\\"/renamed\\\">there</a>\"}"
                        : "{\"title\":\"" + url + "\"}");
                entityManager.persist(draft);
                posts.add(draft.getId());
            }
        });
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                posts.forEach(id -> shPostUnifiedRepository.findById(id)
                        .forEach(shPostUnifiedRepository::delete)));
    }

    private ShAgentDoc.Routes routesOf(String url) {
        ShAgentDoc doc = shAgentReadService
                .read(List.of("post:" + SITE + url), null, false, true).get(0);
        return doc.impact() == null ? null : doc.impact().routes();
    }

    private void rename(String url, String to) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShPostUnified draft = shPostUnifiedRepository
                    .findByIdAndState(SITE + url.replace('/', '-'), ShPostState.DRAFT).orElseThrow();
            draft.setFurl(to);
            shPostUnifiedRepository.save(draft);
        });
    }

    @Test
    @DisplayName("SH996: a draft that was never published says which URL it would make resolve")
    void aFirstPublishGoesLive() {
        assertThat(routesOf("/new-page"))
                .as("SH996: nothing told a curator that this press of the button is the one that "
                        + "puts a new URL on the site")
                .isNotNull()
                .satisfies(routes -> {
                    assertThat(routes.goesLive()).isEqualTo("/new-page");
                    assertThat(routes.moves()).isNull();
                    assertThat(routes.stops()).isNull();
                });
    }

    @Test
    @DisplayName("SH996: a rename says what moves, what stops resolving, and who linked to it")
    void aRenamedDraftMovesTheLivePage() {
        String postId = SITE + "-renamed";
        shPostUnifiedWriteService.publish(postId, "admin");
        shPostUnifiedWriteService.publish(SITE + "-linker", "admin");
        rename("/renamed", "/renamed-again");

        ShAgentDoc.Routes routes = routesOf("/renamed-again");

        assertThat(routes).isNotNull();
        assertThat(routes.moves()).isEqualTo("/renamed-again");
        assertThat(routes.stops())
                .as("SH996: the URL that stops resolving is the one nobody thinks about until a "
                        + "link somewhere else breaks")
                .isEqualTo("/renamed");
        assertThat(routes.inbound())
                .as("SH996: counted with SH140's scanner, the one post.move already reports with — "
                        + "two counts of who links here would eventually disagree, and this is the "
                        + "one a curator reads before publishing")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("SH996: a publish that moves no URL says nothing at all")
    void anOrdinaryPublishIsSilent() {
        String postId = SITE + "-steady";
        shPostUnifiedWriteService.publish(postId, "admin");

        assertThat(routesOf("/steady"))
                .as("SH996: an edit to a page already live changes no URL, and a section attached "
                        + "to every read would be the cost this opt-in exists to avoid")
                .isNull();
    }
}
