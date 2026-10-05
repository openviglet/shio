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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1217 — the sites listing says whether each site is alive, and what that costs.
 *
 * <p>The redrawn list shows each site's pages by state and its last change, with who made it and
 * whether an agent did. Both facts existed; the cost question was whether they would arrive in the
 * listing's one read or as a read per row, which is what SH1050 removed from home. So this counts
 * the statements {@code GET /api/v2/site} prepares at 15 sites and at 400, through the controller
 * and the serializer rather than the service alone, because a lazy association touched while
 * writing JSON is a statement per row too. Counts are asserted and milliseconds are printed, for
 * SH264's reason: a timing ceiling on a shared machine is a flake.</p>
 *
 * <p>It shares the plain context rather than spending a slot of its own (SH423): statistics are
 * switched on for the measurement and back off after it, and every site it made is removed, so the
 * other tests in that context never see four hundred sites they did not seed.</p>
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShSiteListingCostTest {

    private static final int[] SIZES = { 15, 400 };

    /** The sites, one grouped count of pages, one newest-change read. */
    private static final long STATEMENTS = 3;

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ShSiteThumbnailStore thumbnails;
    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private Principal principal;
    private int sites;
    private String liveSite;
    private String emptySite;
    private String rootFolder;
    private String postType;
    private final List<String> made = new ArrayList<>();

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn("admin");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite live = site("sh1217-live-" + UUID.randomUUID());
            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(live);
            root.setDate(EPOCH);
            root.setOwner("admin");
            entityManager.persist(root);
            ShPostType type = new ShPostType();
            type.setName("Sh1217Page-" + UUID.randomUUID());
            type.setTitle("SH1217 Page");
            type.setDescription("SH1217 listing fixture");
            type.setDate(EPOCH);
            entityManager.persist(type);
            // One page live with a pending draft, one only drafted: 1 published, 2 drafts.
            String both = UUID.randomUUID().toString();
            post(both, ShPostState.PUBLISHED, live, root, type);
            post(both, ShPostState.DRAFT, live, root, type);
            post(UUID.randomUUID().toString(), ShPostState.DRAFT, live, root, type);
            // A curator's console publish, then a later agent write: the agent's is the newest.
            history(live.getId(), "curator", "console:abc", EPOCH.plusSeconds(60));
            history(live.getId(), "admin", "run-sh1217", EPOCH.plusSeconds(120));
            liveSite = live.getId();
            rootFolder = root.getId();
            postType = type.getId();
            emptySite = site("sh1217-empty-" + UUID.randomUUID()).getId();
        });
        thumbnails.write(liveSite, "a:sh1218", "image/webp", new byte[] { 1, 2, 3 });
        sites = 2;
    }

    private ShSite site(String name) {
        ShSite site = new ShSite();
        site.setName(name);
        site.setDate(EPOCH);
        site.setOwner("admin");
        entityManager.persist(site);
        made.add(site.getId());
        return site;
    }

    @AfterAll
    void tearDown() {
        thumbnails.forget(liveSite);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createQuery("delete from ShHistory h where h.shSite in :ids")
                    .setParameter("ids", made).executeUpdate();
            entityManager.createQuery("delete from ShPostUnified p where p.shSite.id = :id")
                    .setParameter("id", liveSite).executeUpdate();
            entityManager.remove(entityManager.find(ShFolder.class, rootFolder));
            entityManager.remove(entityManager.find(ShPostType.class, postType));
            for (String id : made) {
                entityManager.remove(entityManager.find(ShSite.class, id));
            }
        });
    }

    private void post(String id, ShPostState state, ShSite site, ShFolder folder, ShPostType type) {
        ShPostUnified post = new ShPostUnified();
        post.setId(id);
        post.setState(state);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(type);
        post.setTitle("page " + id);
        post.setFurl(id + "-" + state.name().toLowerCase(java.util.Locale.ROOT));
        post.setLocale("en");
        post.setOwner("admin");
        post.setDate(EPOCH);
        post.setData("{}");
        entityManager.persist(post);
    }

    private void history(String siteId, String owner, String session, Instant at) {
        ShHistory row = new ShHistory();
        row.setShSite(siteId);
        row.setShObject(siteId);
        row.setOwner(owner);
        row.setAgentSessionId(session);
        row.setAction("UPDATE");
        row.setDate(at);
        entityManager.persist(row);
    }

    /** Grows to {@code target} sites, each with one change, so every row has something to say. */
    private void growTo(int target) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (int index = sites; index < target; index++) {
                ShSite site = site("sh1217-noise-" + index + "-" + UUID.randomUUID());
                history(site.getId(), "admin", null, EPOCH.plusSeconds(index));
            }
        });
        sites = Math.max(sites, target);
    }

    private JsonNode listing() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v2/site").principal(principal))
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode row(JsonNode listing, String id) {
        for (JsonNode each : listing) {
            if (id.equals(each.path("id").asString())) {
                return each;
            }
        }
        throw new AssertionError("SH1217: site " + id + " is missing from GET /api/v2/site");
    }

    @Test
    @DisplayName("SH1217: each row carries pages by state and the last change, and its old fields")
    void eachRowSaysWhetherTheSiteIsAlive() throws Exception {
        JsonNode listing = listing();
        JsonNode live = row(listing, liveSite);

        assertThat(live.path("name").asString())
                .as("SH1217: the site's own fields stay where every caller already reads them")
                .startsWith("sh1217-live-");
        assertThat(live.path("pages").path("published").asLong())
                .as("SH1217: a page live with a pending draft is published").isEqualTo(1);
        assertThat(live.path("pages").path("draft").asLong())
                .as("SH1217: and it is also waiting, beside the page that was only drafted").isEqualTo(2);
        assertThat(live.path("lastChange").path("actor").asString()).isEqualTo("admin");
        assertThat(live.path("lastChange").path("agent").asBoolean())
                .as("SH1217: the newest change was an agent run's, and the row has to say so")
                .isTrue();

        assertThat(live.path("thumbnail").path("url").asString())
                .as("SH1218: a stored home picture is named in the listing's one read")
                .isEqualTo("/api/v2/site/" + liveSite + "/thumbnail");
        assertThat(live.path("thumbnail").path("digest").asString()).isEqualTo("a:sh1218");

        JsonNode empty = row(listing, emptySite);
        assertThat(empty.path("thumbnail").isMissingNode() || empty.path("thumbnail").isNull())
                .as("SH1218: no picture is absent, which the grid shows as initials")
                .isTrue();
        assertThat(empty.path("pages").path("published").asLong()).isZero();
        assertThat(empty.path("pages").path("draft").asLong()).isZero();
        assertThat(empty.path("lastChange").isMissingNode() || empty.path("lastChange").isNull())
                .as("SH1217: a site nothing was recorded against has no last change, not an invented one")
                .isTrue();
    }

    @Test
    @DisplayName("SH1217: a console session's change is the curator's, not an agent's")
    void aConsoleSessionIsNotAnAgent() {
        assertThat(ShSiteListingService.isAgent("console:abc")).isFalse();
        assertThat(ShSiteListingService.isAgent(null)).isFalse();
        assertThat(ShSiteListingService.isAgent("run-1")).isTrue();
    }

    @Test
    @DisplayName("SH1217: the listing costs the same statements at 15 sites and at 400")
    void theListingIsBoundedInStatements() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasOn = statistics.isStatisticsEnabled();
        List<String> report = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        // Without this the counter reads zero and the bound passes for the wrong reason.
        statistics.setStatisticsEnabled(true);
        try {
            measure(statistics, report, counts);
        } finally {
            statistics.setStatisticsEnabled(wasOn);
        }
        System.out.println("\nSH1217 — GET /api/v2/site, with pages and last change per site:\n"
                + String.join("\n", report));

        assertThat(counts)
                .as("""
                        SH1217: the listing has to cost the same at 400 sites as at 15 — a statement \
                        per row is the cost SH1050 removed from home. Measured %s.""", counts)
                .containsOnly(counts.get(0));
        assertThat(counts.get(0))
                .as("SH1217: the sites, one grouped count and one newest-change read")
                .isPositive()
                .isLessThanOrEqualTo(STATEMENTS);
    }

    private void measure(Statistics statistics, List<String> report, List<Long> counts)
            throws Exception {
        for (int size : SIZES) {
            growTo(size);
            listing(); // warm: the first read of a fresh context pays for metadata, not for rows
            long before = statistics.getPrepareStatementCount();
            long started = System.nanoTime();
            JsonNode listing = listing();
            long millis = (System.nanoTime() - started) / 1_000_000;
            long statements = statistics.getPrepareStatementCount() - before;
            counts.add(statements);
            report.add(String.format("  %4d sites: %d statement(s), %d ms", listing.size(),
                    statements, millis));
            assertThat(listing.size()).isGreaterThanOrEqualTo(size);
        }
    }
}
