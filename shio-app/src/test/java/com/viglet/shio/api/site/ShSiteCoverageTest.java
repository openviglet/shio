/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
import com.viglet.shio.api.query.ShContentQuery;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.UpdateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1131 — a site's coverage in one read: a row per page, a cell per language.
 *
 * <p>{@code missingLocale} answers one language at a time and this answers every language at once,
 * which is what a board draws and what a person deciding where to spend a week reads. Two things
 * are pinned here beyond the cells themselves: the totals describe the <b>site</b> rather than the
 * page of rows in hand — a summary that counted the page would say "3 missing" on a board showing
 * three of forty — and they agree with what {@code find} answers, because a board and a work list
 * that disagree are two backlogs for one site.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShSiteCoverageTest {

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ShSiteCoverageService coverage;
    @Autowired
    private ShAgentReadService shAgentReadService;
    @Autowired
    private ShPostUnifiedWriteService write;
    @Autowired
    private ShSiteRepository shSiteRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteId;
    private String siteName;
    private String rootId;
    private String typeId;

    @BeforeAll
    void seed() {
        siteName = "sh1060cov-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(EPOCH);
            site.setOwner("admin");
            // Declared, so the board's columns are the site's decision rather than what it happens
            // to hold — a language with nothing in it is exactly the column a board exists to show.
            site.setLocales("{\"default\":\"en\",\"enabled\":[\"en\",\"pt-br\",\"de\"]}");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(EPOCH);
            root.setOwner("admin");
            entityManager.persist(root);
            rootId = root.getId();

            ShPostType type = new ShPostType();
            type.setName("Sh1060Cov" + UUID.randomUUID().toString().substring(0, 6));
            type.setTitle("SH1131 coverage page");
            type.setSystem((byte) 0);
            type.setDate(EPOCH);
            type.setOwner("admin");
            entityManager.persist(type);
            typeId = type.getId();
        });

        // /about is translated into pt-br and then the source moves on: pt-br is stale, de missing.
        ShPostUnified about = create("/about", "en", "{\"body\":\"about\"}", null);
        create("/sobre", "pt-br", "{\"body\":\"sobre\"}", about.getId());
        write.update(about.getId(), new UpdateRequest(json("{\"body\":\"about, revised\"}"),
                ShPostState.DRAFT, "admin"));
        // /careers is English only: both other languages are missing.
        create("/careers", "en", "{\"body\":\"careers\"}", null);
    }

    @Test
    @DisplayName("SH1131: every language of every page, as current, stale or missing")
    void theBoardSaysWhatEachLanguageHolds() {
        ShSiteCoverage board = of(0, 50);

        assertThat(board.locales())
                .as("SH1131: the columns are the site's declared languages, including the one it "
                        + "has written nothing in — that column IS the backlog")
                .containsExactly("en", "pt-br", "de");
        assertThat(board.rows()).extracting(ShSiteCoverage.Group::address)
                .as("SH1131: one row per page to translate, the source's address, in a stable order")
                .containsExactly("post:" + siteName + "/about", "post:" + siteName + "/careers");

        ShSiteCoverage.Group about = board.rows().getFirst();
        assertThat(about.cells().get("en").state()).isEqualTo(ShSiteCoverage.CURRENT);
        assertThat(about.cells().get("pt-br").state())
                .as("SH1131: the source moved on after the translation, which SH1058 stamped")
                .isEqualTo(ShSiteCoverage.STALE);
        assertThat(about.cells().get("pt-br").address())
                .as("SH1131: and a cell carries where to go, or the board is a picture of a problem")
                .isEqualTo("post:" + siteName + "/sobre");
        assertThat(about.cells().get("de").state()).isEqualTo(ShSiteCoverage.MISSING);
        assertThat(about.cells().get("de").address()).isNull();
    }

    @Test
    @DisplayName("SH1131: the totals describe the site, not the page of rows in hand")
    void theTotalsAreOverTheWholeSite() {
        ShSiteCoverage firstOfTwo = of(0, 1);

        assertThat(firstOfTwo.rows()).hasSize(1);
        assertThat(firstOfTwo.total()).isEqualTo(2);
        assertThat(firstOfTwo.totals().get("de"))
                .as("SH1131: a summary counted over the page would say one missing on a board "
                        + "showing one of two")
                .isEqualTo(new ShSiteCoverage.Count(0, 0, 2));
        assertThat(firstOfTwo.totals().get("pt-br"))
                .isEqualTo(new ShSiteCoverage.Count(0, 1, 1));
        assertThat(firstOfTwo.totals().get("en"))
                .isEqualTo(new ShSiteCoverage.Count(2, 0, 0));
    }

    @Test
    @DisplayName("SH1131: the board's missing count is what find answers for the same locale")
    void theBoardAndTheWorkListAgree() {
        ShSiteCoverage board = of(0, 50);

        long fromFind = shAgentReadService.find(
                ShContentQuery.ALL.withSite(siteName).withMissingLocale("de"), 200, false, null,
                false, null, null).results().size();

        assertThat((long) board.totals().get("de").missing())
                .as("SH1131: the console's board and the agent's work list are one question asked "
                        + "twice — two answers would be two backlogs for one site")
                .isEqualTo(fromFind);
    }

    private ShSiteCoverage of(int page, int size) {
        return new TransactionTemplate(transactionManager).execute(status ->
                coverage.of(shSiteRepository.findById(siteId).orElseThrow(), page, size));
    }

    private ShPostUnified create(String furl, String locale, String data, String group) {
        return write.create(new CreateRequest(rootId, typeId, json(data), ShPostState.DRAFT, "admin",
                locale, group, furl));
    }

    private JsonNode json(String text) {
        return objectMapper.readTree(text);
    }
}
