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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.object.ShObjectNavigationService;
import com.viglet.shio.api.object.dto.ShConsoleListing;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1022 — one query vocabulary, asked through each surface, answering the same set.
 *
 * <h2>The assertion the design names</h2>
 *
 * <p>"The assertions ask the same question through all four surfaces and compare the address
 * sets." That is the whole claim, and it is not a claim a unit test of {@link ShContentQuery}
 * could make: the record could parse perfectly while the agent listing and the console listing
 * each did something different with what it parsed. What goes wrong when they drift is invisible
 * — both answer {@code 200} with a plausible list, and the curator and the agent simply see
 * different folders.</p>
 *
 * <p>Three surfaces are reachable from Java: {@code /agent/find}'s service (which is also
 * {@code shio_find}'s — the MCP tool is a shape over it, P10) and the console listing's. The
 * fourth, {@code shio find}, is JavaScript; {@code cli/test/content-query.test.mjs} holds its
 * half by reading this vocabulary out of {@link ShContentQuery} and asserting the CLI spells
 * every word of it, which is the same parity trick {@code blocks-parity.json} plays.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-content-query;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShContentQuerySurfacesTest {

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant RECENT = Instant.parse("2026-06-01T00:00:00Z");

    @Autowired
    private ShAgentReadService shAgentReadService;
    @Autowired
    private ShObjectNavigationService shObjectNavigationService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String sectionId;
    private String typeName;

    /**
     * Eight posts in one folder, differing on every axis the vocabulary names.
     *
     * <p>Deliberately not orthogonal: the same post is the {@code pt_BR} one, one of {@code ana}'s
     * and one of the recently modified. A fixture where each filter picks a disjoint row cannot
     * tell a working predicate from one that is accidentally selecting on something else.</p>
     */
    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("query-" + UUID.randomUUID());
            site.setDate(EPOCH);
            site.setOwner("admin");
            entityManager.persist(site);
            siteName = site.getName();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(EPOCH);
            root.setOwner("admin");
            entityManager.persist(root);

            ShFolder section = new ShFolder();
            section.setName("section");
            section.setRootFolder((byte) 0);
            section.setParentFolder(root);
            section.setDate(EPOCH);
            section.setOwner("admin");
            entityManager.persist(section);
            sectionId = section.getId();

            ShPostType article = new ShPostType();
            article.setName("QueryArticle");
            article.setTitle("Query Article");
            article.setDescription("SH1022 fixture");
            article.setDate(EPOCH);
            entityManager.persist(article);
            typeName = article.getName();

            ShPostType note = new ShPostType();
            note.setName("QueryNote");
            note.setTitle("Query Note");
            note.setDescription("SH1022 fixture");
            note.setDate(EPOCH);
            entityManager.persist(note);

            for (int index = 0; index < 8; index++) {
                String id = null;
                for (ShPostState state : List.of(ShPostState.DRAFT, ShPostState.PUBLISHED)) {
                    ShPostUnified post = new ShPostUnified();
                    if (id != null) {
                        post.setId(id);
                    }
                    post.setState(state);
                    post.setShSite(site);
                    post.setShFolder(section);
                    post.setShPostType(index % 2 == 0 ? article : note);
                    post.setTitle("Row " + index);
                    post.setFurl("row-" + index);
                    post.setData("{}");
                    post.setDate(EPOCH);
                    post.setModifiedDate(index < 3 ? RECENT : EPOCH);
                    post.setOwner(index % 2 == 0 ? "ana" : "bruno");
                    post.setModifier(index < 4 ? "ana" : "bruno");
                    post.setLocale(index % 4 == 0 ? "pt_BR" : "en");
                    id = shPostUnifiedRepository.save(post).getId();
                }
            }
        });
    }

    @Test
    @DisplayName("SH1022: every filter selects the same posts on the agent listing and the console's")
    void thefourSurfacesAnswerOneQuestion() {
        for (ShContentQuery filter : List.of(
                ShContentQuery.ALL,
                ShContentQuery.ALL.withType(typeName),
                ShContentQuery.ALL.withLocale("pt_BR"),
                ShContentQuery.ALL.withAuthor("ana"),
                ShContentQuery.ALL.withAuthor("ANA"),
                ShContentQuery.ALL.withModifier("bruno"),
                ShContentQuery.ALL.withModifiedSince(RECENT.toString()),
                ShContentQuery.ALL.withModifiedBefore(RECENT.toString()),
                ShContentQuery.ALL.withType(typeName).withAuthor("ana").withLocale("pt_BR"),
                ShContentQuery.ALL.withState("published").withModifiedSince("2026-01-01"))) {

            assertThat(fromConsole(filter))
                    .as("""
                            SH1022: %s answers a different set on the console listing than on \
                            /agent/find. Nothing fails when two listings drift — both return 200 \
                            with a plausible list, and the curator and the agent simply see \
                            different folders.""", describe(filter))
                    .isEqualTo(fromAgent(filter));
        }
    }

    @Test
    @DisplayName("SH1022: the date window is inclusive below and exclusive above, on both")
    void theWindowPartitionsTheTimeline() {
        List<String> since = fromAgent(ShContentQuery.ALL.withModifiedSince(RECENT.toString()));
        List<String> before = fromAgent(ShContentQuery.ALL.withModifiedBefore(RECENT.toString()));

        assertThat(since).isNotEmpty();
        assertThat(before).isNotEmpty();
        assertThat(since)
                .as("SH1022: two adjacent windows partition the timeline rather than overlapping "
                        + "on one instant — otherwise a post shows up in both halves of a split")
                .doesNotContainAnyElementsOf(before);
        assertThat(fromConsole(ShContentQuery.ALL.withModifiedSince(RECENT.toString())))
                .isEqualTo(since);
    }

    @Test
    @DisplayName("SH1022: a date is read as midnight UTC, which is what a person types")
    void aBareDateIsAnInstant() {
        assertThat(fromAgent(ShContentQuery.ALL.withModifiedSince("2026-06-01")))
                .isEqualTo(fromAgent(ShContentQuery.ALL.withModifiedSince(RECENT.toString())));
    }

    @Test
    @DisplayName("SH1022: an unknown filter is refused, not ignored")
    void anUnknownKeyIsRefused() {
        assertThatThrownBy(() -> ShContentQuery.of(java.util.Map.of("modifedSince", "2026-01-01"),
                ShContentQuery.FILTERS, List.of(), "elsewhere"))
                .as("""
                        SH1022: Spring drops an unbound parameter silently, so a typo returns \
                        every row with a 200 — the one answer indistinguishable from a filter \
                        that matched everything.""")
                .isInstanceOf(ShUnknownQueryKeyException.class)
                .satisfies(thrown -> assertThat(
                        ((ShTeachingException) thrown).problem().didYouMean())
                        .as("SH1022: and the typo is closed rather than only reported")
                        .isEqualTo("modifiedSince"));
    }

    @Test
    @DisplayName("SH1022: a word this surface cannot serve says where it does work")
    void anUnservedKeySaysWhere() {
        assertThatThrownBy(() -> ShContentQuery.of(java.util.Map.of("sort", "title"),
                ShContentQuery.FILTERS, List.of(), "Use the console listing."))
                .isInstanceOf(ShUnknownQueryKeyException.class)
                .satisfies(thrown -> assertThat(((ShTeachingException) thrown).problem()
                        .toProblemDetail().getProperties().get("fix"))
                        .as("SH1022: `sort` is spelled by the vocabulary and not served here, so "
                                + "what closes it is being told where it is served — not a list "
                                + "of other words")
                        .isEqualTo("Use the console listing."));
    }

    @Test
    @DisplayName("SH1022: a value it cannot read is refused rather than failing open or closed")
    void anUnreadableValueIsRefused() {
        assertThatThrownBy(() -> ShContentQuery.ALL.withModifiedSince("last week"))
                .isInstanceOf(ShInvalidQueryValueException.class);
        assertThatThrownBy(() -> ShContentQuery.of(java.util.Map.of("size", "-1"),
                ShContentQuery.KEYS, List.of(), "elsewhere"))
                .isInstanceOf(ShInvalidQueryValueException.class);
    }

    /** The addresses {@code /agent/find} — and therefore {@code shio_find} — answers with. */
    private List<String> fromAgent(ShContentQuery filter) {
        ShAgentFound found = shAgentReadService.find(filter.withSite(siteName), 200, false, null,
                false, null, null);
        return found.results().stream().map(ShAgentFound.Row::address).sorted().toList();
    }

    /** The same question, asked of the console listing, projected onto the same addresses. */
    private List<String> fromConsole(ShContentQuery filter) {
        ShConsoleListing listing = shObjectNavigationService
                .list(sectionId, filter.withPaging(0, 200)).orElseThrow();
        return listing.posts().stream()
                .map(row -> "post:" + siteName + "/" + row.furl())
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static String describe(ShContentQuery filter) {
        return filter.isEmpty() ? "the unfiltered listing" : filter.toString();
    }
}
