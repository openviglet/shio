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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShSystemPostType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1130 — a language a site promises and never wrote, and content in one it never promised.
 *
 * <p>The route cannot report either: since SH1057 a declared locale with nothing written in it falls
 * back along the chain and answers 200 with another language's page. Every site here is seeded under
 * a unique name and read alone, so the shared context is enough (SH315).</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
class ShVerifySiteLocalesTest {

    private static final Instant WHEN = Instant.parse("2026-01-01T00:00:00Z");
    private static final String UNWRITTEN = "locale-declared-unwritten";
    private static final String UNDECLARED = "locale-undeclared-content";

    @Autowired
    private ShAgentVerifyService verifyService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("SH1130: a declared pt-br with only English content is reported, and is not once a pt-br page is published")
    void aDeclaredLocaleNobodyWroteIsReportedUntilItIsWritten() {
        String name = "sh1130-unwritten-" + UUID.randomUUID().toString().substring(0, 8);
        ShFolder root = inTransaction(() -> {
            ShFolder created = site(name, "{\"default\":\"en\",\"enabled\":[\"pt-br\",\"en\"]}");
            page(created, "/", "en", ShPostState.PUBLISHED);
            // A draft is not what a /pt-br/ reader is served, so it does not make pt-br written.
            page(created, "/rascunho", "pt-br", ShPostState.DRAFT);
            return created;
        });

        List<ShAgentReport.Finding> before = localeFindings(name);
        assertThat(before)
                .as("SH1130: pt-br is declared and nothing published is written in it")
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.rule()).isEqualTo(UNWRITTEN);
                    assertThat(finding.severity()).isEqualTo("warning");
                    assertThat(finding.address()).isEqualTo("site:" + name);
                    assertThat(finding.message()).contains("'pt-br'").contains("serves the en page");
                    assertThat(finding.fix()).contains("locales.enabled");
                });

        inTransaction(() -> page(root, "/inicio", "pt-br", ShPostState.PUBLISHED));

        assertThat(localeFindings(name))
                .as("SH1130: one published pt-br page and the promise is kept")
                .isEmpty();
    }

    @Test
    @DisplayName("SH1130: published content in a locale the policy does not enable is the mirror finding")
    void contentInAnUndeclaredLocaleIsReported() {
        String name = "sh1130-undeclared-" + UUID.randomUUID().toString().substring(0, 8);
        inTransaction(() -> {
            ShFolder root = site(name, "{\"default\":\"pt-br\",\"enabled\":[\"pt-br\"]}");
            page(root, "/", "pt-br", ShPostState.PUBLISHED);
            page(root, "/about", "en", ShPostState.PUBLISHED);
            return root;
        });

        assertThat(localeFindings(name))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.rule()).isEqualTo(UNDECLARED);
                    assertThat(finding.message()).contains("'en'").contains("pt-br");
                });
    }

    @Test
    @DisplayName("SH1130: templates and files carry the writer's default locale and are not content")
    void templatesAndFilesAreNotWrittenInALanguage() {
        String name = "sh1130-templates-" + UUID.randomUUID().toString().substring(0, 8);
        inTransaction(() -> {
            ShFolder root = site(name, "{\"default\":\"pt-br\",\"enabled\":[\"pt-br\"]}");
            page(root, "/", "pt-br", ShPostState.PUBLISHED);
            // Both get `en` on write whatever the site is in, and one has no locale at all — a row
            // written before the column, which every reader takes as the writer's default.
            row(root, type(ShSystemPostType.PAGE_LAYOUT, (byte) 1), "/layout", "en", ShPostState.PUBLISHED);
            row(root, type(ShSystemPostType.FILE, (byte) 1), "/logo.png", null, ShPostState.PUBLISHED);
            return root;
        });

        assertThat(localeFindings(name))
                .as("SH1130: a site declaring only pt-br is not told its layout and its logo are "
                        + "undeclared English content")
                .isEmpty();
    }

    @Test
    @DisplayName("SH1130: an undeclared site is silent — its languages are the ones its content is in")
    void anUndeclaredSiteIsSilent() {
        String name = "sh1130-plain-" + UUID.randomUUID().toString().substring(0, 8);
        inTransaction(() -> {
            ShFolder root = site(name, null);
            page(root, "/", "en", ShPostState.PUBLISHED);
            page(root, "/sobre", "pt-br", ShPostState.PUBLISHED);
            return root;
        });

        assertThat(localeFindings(name)).isEmpty();
    }

    private List<ShAgentReport.Finding> localeFindings(String site) {
        return verifyService.verify(site, "content", 200).findings().stream()
                .filter(finding -> UNWRITTEN.equals(finding.rule()) || UNDECLARED.equals(finding.rule()))
                .toList();
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private ShFolder site(String name, String locales) {
        ShSite site = new ShSite();
        site.setName(name);
        site.setFurl(name);
        site.setDate(WHEN);
        site.setOwner("admin");
        site.setLocales(locales);
        entityManager.persist(site);

        ShFolder root = new ShFolder();
        root.setName(name);
        root.setRootFolder((byte) 1);
        root.setShSite(site);
        root.setDate(WHEN);
        root.setOwner("admin");
        entityManager.persist(root);
        return root;
    }

    private ShPostUnified page(ShFolder root, String furl, String locale, ShPostState state) {
        return row(root, type("Sh1130Page", (byte) 0), furl, locale, state);
    }

    private ShPostUnified row(ShFolder root, ShPostType type, String furl, String locale,
                              ShPostState state) {
        ShPostUnified row = new ShPostUnified();
        row.setId(UUID.randomUUID().toString());
        row.setState(state);
        row.setShSite(root.getShSite());
        row.setShFolder(root);
        row.setShPostType(type);
        row.setFurl(furl);
        row.setTitle(furl);
        row.setLocale(locale);
        row.setTranslationGroupId(row.getId());
        row.setData("{}");
        row.setOwner("admin");
        row.setDate(WHEN);
        entityManager.persist(row);
        return row;
    }

    private ShPostType type(String name, byte system) {
        ShPostType existing = entityManager
                .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                .setParameter("name", name).getResultStream().findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }
        ShPostType type = new ShPostType();
        type.setName(name);
        type.setTitle(name);
        type.setSystem(system);
        type.setDate(WHEN);
        type.setOwner("admin");
        entityManager.persist(type);
        return type;
    }
}
