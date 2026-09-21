/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
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

import com.viglet.shio.api.agent.ShAgentReadService;
import com.viglet.shio.api.agent.dto.ShAgentFound;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1060 — "what is still to translate into pt-br", as a filter rather than a scan.
 *
 * <p>Before this, the answer was a full read of the site and a group-by in somebody's head: the
 * vocabulary could say {@code locale=pt-br} (the pages that ARE Portuguese) and had no way to ask
 * for the pages that are not. {@code missingLocale} is that question, as a predicate the database
 * answers.</p>
 *
 * <p>The two decisions this pins. It answers on the group's <b>source</b>, so a page already in
 * three languages and missing a fourth is one row rather than three — a work list with one entry
 * per thing to do. And a group that HAS the locale is silent whichever member you look at, which is
 * what makes the answer a backlog rather than an inventory.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShTranslationCoverageTest {

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ShAgentReadService shAgentReadService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;

    @BeforeAll
    void seed() {
        siteName = "sh1060-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(EPOCH);
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(EPOCH);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType type = new ShPostType();
            type.setName("Sh1060Page" + UUID.randomUUID().toString().substring(0, 6));
            type.setTitle("SH1060 page");
            type.setSystem((byte) 0);
            type.setDate(EPOCH);
            type.setOwner("admin");
            entityManager.persist(type);

            // A page translated into pt-br, and its translation.
            String translated = UUID.randomUUID().toString();
            post(site, root, type, "about", "en", translated, translated);
            post(site, root, type, "sobre", "pt-br", translated, null);
            // A page nobody has translated: the one thing on the pt-br work list.
            String untranslated = UUID.randomUUID().toString();
            post(site, root, type, "careers", "en", untranslated, untranslated);
        });
    }

    @Test
    @DisplayName("SH1060: the pages with no translation in a locale, one row per page to do")
    void missingLocaleAnswersTheWorkList() {
        assertThat(found("pt-br"))
                .as("SH1060: a page whose group has pt-br is done, and a translation is not a "
                        + "second thing to translate — the list is one row per page to write")
                .containsExactly("post:" + siteName + "/careers");
    }

    @Test
    @DisplayName("SH1060: a locale nothing is written in lists every page, once each")
    void aLocaleNobodyHasUsedListsEverySource() {
        assertThat(found("de"))
                .as("SH1060: both sources, and neither the pt-br translation nor a page twice")
                .containsExactlyInAnyOrder("post:" + siteName + "/about",
                        "post:" + siteName + "/careers");
    }

    @Test
    @DisplayName("SH1060: it narrows with the rest of the vocabulary rather than replacing it")
    void itComposesWithTheOtherWords() {
        assertThat(found(ShContentQuery.ALL.withMissingLocale("de").withAuthor("bruno")))
                .as("SH1060: one vocabulary, so a coverage question can be asked of one author's "
                        + "pages the way every other filter composes")
                .containsExactly("post:" + siteName + "/careers");
    }

    private List<String> found(String missingLocale) {
        return found(ShContentQuery.ALL.withMissingLocale(missingLocale));
    }

    private List<String> found(ShContentQuery query) {
        ShAgentFound answer = shAgentReadService.find(query.withSite(siteName), 200, false, null,
                false, null, null);
        return answer.results().stream().map(ShAgentFound.Row::address).sorted().toList();
    }

    private void post(ShSite site, ShFolder folder, ShPostType type, String furl, String locale,
                      String group, String anchorId) {
        ShPostUnified post = new ShPostUnified();
        post.setId(anchorId == null ? UUID.randomUUID().toString() : anchorId);
        post.setState(ShPostState.DRAFT);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(type);
        post.setTitle(furl);
        post.setFurl(furl);
        post.setData("{}");
        post.setDate(EPOCH);
        post.setOwner("careers".equals(furl) ? "bruno" : "ana");
        post.setLocale(locale);
        post.setTranslationGroupId(group);
        entityManager.persist(post);
    }
}
