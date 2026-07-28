/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

import com.viglet.shio.api.agent.ShAgentChangesService;
import com.viglet.shio.api.agent.dto.ShAgentChanges;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.api.agent.verify.ShAgentVerifyService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.render.ShTemplateGraph.ShSiteTemplateGraph;
import com.viglet.shio.render.ShTemplateGraph.ShTemplateImpact;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH193 — the blast radius of a shared-presentation edit.
 *
 * <p>Before this, "which layouts slot this region, which pages use those layouts, how many are
 * published" was unanswerable, because composition is an attribute inside an HTML string. Editing
 * a shared region was an unbounded change and {@code verify} could not prove afterwards that
 * nothing broke.</p>
 *
 * <p>The fixture is the shape that makes the inversion worth having: <b>two</b> layouts, one shared
 * region slotted by both, one region slotted by neither, and one layout nothing binds. A graph that
 * only handled the one-layout case would pass a simpler fixture and be useless on a real site.</p>
 *
 * <p>Its own database, for {@link ShPageRenderTest}'s reason (SH246).</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh193;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShTemplateGraphTest {

    private static final String ARTICLE_LAYOUT = "SH193_ARTICLE_LAYOUT";
    private static final String PAGE_LAYOUT = "SH193_PAGE_LAYOUT";
    private static final String ORPHAN_LAYOUT = "SH193_ORPHAN_LAYOUT";
    private static final String SHARED_REGION = "SH193_NAV";
    private static final String LONELY_REGION = "SH193_LONELY";

    @Autowired
    private ShTemplateGraph graph;
    @Autowired
    private ShAgentVerifyService verifyService;
    @Autowired
    private ShAgentChangesService shAgentChangesService;
    @Autowired
    private ShHistoryRepository shHistoryRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String articleType;
    private String pageType;
    private ShSite site;

    @BeforeAll
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        siteName = "sh193-" + suffix;
        articleType = "Sh193Article" + suffix;
        pageType = "Sh193Page" + suffix;

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");

            ShSite created = new ShSite();
            created.setName(siteName);
            created.setDate(when);
            created.setOwner("admin");
            created.setPostTypeLayout("{\"" + articleType + "\":\"" + ARTICLE_LAYOUT + "\",\""
                    + pageType + "\":\"" + PAGE_LAYOUT + "\"}");
            entityManager.persist(created);
            site = created;

            ShFolder root = folder(created, when);
            ShPostType article = postType(articleType, (byte) 0, when);
            ShPostType page = postType(pageType, (byte) 0, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            ShPostType regionType = postType(ShSystemPostType.REGION, (byte) 1, when);

            // Both layouts slot the shared region; only the article layout slots nothing else.
            post(created, root, layoutType, ARTICLE_LAYOUT, "/l-article",
                    json("HTML", "<body><div sh-region=\"" + SHARED_REGION + "\"></div>{{TEXT}}</body>"),
                    when, ShPostState.DRAFT);
            post(created, root, layoutType, PAGE_LAYOUT, "/l-page",
                    json("HTML", "<body><div sh-region=\"" + SHARED_REGION + "\"></div></body>"),
                    when, ShPostState.DRAFT);
            post(created, root, layoutType, ORPHAN_LAYOUT, "/l-orphan",
                    json("HTML", "<body>nothing binds me</body>"), when, ShPostState.DRAFT);

            post(created, root, regionType, SHARED_REGION, "/r-nav",
                    json("HTML", "<nav>shared</nav>"), when, ShPostState.DRAFT);
            post(created, root, regionType, LONELY_REGION, "/r-lonely",
                    json("HTML", "<aside>nobody slots me</aside>"), when, ShPostState.DRAFT);

            // Two article drafts, one of them also published; one page, published.
            post(created, root, article, "A1", "/a1", json("TEXT", "one"), when, ShPostState.DRAFT);
            post(created, root, article, "A2", "/a2", json("TEXT", "two"), when, ShPostState.DRAFT);
            post(created, root, article, "A2", "/a2", json("TEXT", "two"), when, ShPostState.PUBLISHED);
            post(created, root, page, "P1", "/p1", json("TEXT", "p"), when, ShPostState.DRAFT);
            post(created, root, page, "P1", "/p1", json("TEXT", "p"), when, ShPostState.PUBLISHED);
        });
    }

    @Test
    @DisplayName("the graph inverts both edges: layout → types, region → layouts")
    void bothDirections() {
        ShSiteTemplateGraph resolved = inTransaction(() -> graph.of(reload()));

        assertThat(resolved.typesUsing(ARTICLE_LAYOUT)).containsExactly(articleType);
        assertThat(resolved.typesUsing(PAGE_LAYOUT)).containsExactly(pageType);
        assertThat(resolved.layoutsSlotting(SHARED_REGION))
                .as("""
                        the case the whole task is about: one region, two layouts. A graph that \
                        answered with the first layout it found would understate every shared \
                        region, which is precisely the edit nobody could bound.""")
                .containsExactly(ARTICLE_LAYOUT, PAGE_LAYOUT);
        assertThat(resolved.layoutsSlotting(LONELY_REGION)).isEmpty();
        assertThat(resolved.typesUsing(ORPHAN_LAYOUT)).isEmpty();
    }

    @Test
    @DisplayName("a region's blast radius reaches through every layout that slots it")
    void aRegionReachesEveryType() {
        ShTemplateImpact impact = impactOf(SHARED_REGION);

        assertThat(impact.kind()).isEqualTo("region");
        assertThat(impact.layouts()).containsExactly(ARTICLE_LAYOUT, PAGE_LAYOUT);
        assertThat(impact.postTypes())
                .as("both types, because both layouts slot it — the transitive step")
                .containsExactly(articleType, pageType);
        assertThat(impact.pages()).as("A1, A2 and P1 as drafts").isEqualTo(3);
        assertThat(impact.published())
                .as("""
                        counted separately and not as a total: "how many are published" is the \
                        number that decides whether this edit is safe to make now, and a \
                        DRAFT-preferred count would answer a different question.""")
                .isEqualTo(2);
        assertThat(impact.isUsed()).isTrue();
    }

    @Test
    @DisplayName("a layout's blast radius is its own bound types")
    void aLayoutReachesItsTypes() {
        ShTemplateImpact impact = impactOf(ARTICLE_LAYOUT);

        assertThat(impact.kind()).isEqualTo("layout");
        assertThat(impact.layouts()).containsExactly(ARTICLE_LAYOUT);
        assertThat(impact.postTypes()).containsExactly(articleType);
        assertThat(impact.pages()).isEqualTo(2);
        assertThat(impact.published()).isEqualTo(1);
    }

    @Test
    @DisplayName("a template nothing renders through says so rather than looking used")
    void unusedTemplatesAreVisible() {
        assertThat(impactOf(LONELY_REGION).isUsed()).isFalse();
        assertThat(impactOf(ORPHAN_LAYOUT).isUsed()).isFalse();
    }

    @Test
    @DisplayName("an ordinary post has no blast radius — the field is absent, not empty")
    void ordinaryPostsHaveNoImpact() {
        // Only two post-types can raise the question, and a field that is always present is a
        // field every row of the change feed pays for (SH121's argument about ids).
        assertThat(maybeImpactOf("A1")).isEmpty();
    }

    @Test
    @DisplayName("verify warns about an unreachable region and an unbound layout")
    void verifyReportsUnusedTemplates() {
        ShAgentReport report = verifyService.verify(siteName, "content", 200);

        assertThat(report.findings())
                .filteredOn(finding -> "unused-region".equals(finding.rule()))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.severity())
                            .as("""
                                    a warning, not an error: a region waiting for the layout that \
                                    will slot it is work in progress, and SH99's rule is that ok \
                                    is false only for what the CMS can prove is wrong.""")
                            .isEqualTo("warning");
                    assertThat(finding.message()).contains(LONELY_REGION).contains("sh-region");
                    assertThat(finding.fix())
                            .as("P6 — a title differing in case or spacing is the usual cause")
                            .contains("differs in case");
                });

        assertThat(report.findings())
                .filteredOn(finding -> "unused-layout".equals(finding.rule()))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.message()).contains(ORPHAN_LAYOUT)
                            .as("the bound layouts are named, so the fix is a comparison")
                            .contains(ARTICLE_LAYOUT);
                    assertThat(finding.fix()).contains("postTypeLayout");
                    assertThat(finding.severity()).isEqualTo("warning");
                });
    }

    @Test
    @DisplayName("the change feed carries the blast radius for a region, and nothing else")
    void theFeedCarriesAffects() {
        // SH193's answer to "what did I just touch" - on the feed rather than on a read of its
        // own, which is XV.11's own suggestion and needs no twelfth MCP tool, so shio_changes
        // carries it for free.
        String regionId = inTransaction(() -> postNamed(SHARED_REGION).getId());
        String articleId = inTransaction(() -> postNamed("A1").getId());
        recordHistory(regionId, "the nav was rewritten");
        recordHistory(articleId, "a headline was fixed");

        List<ShAgentChanges.Change> rows =
                shAgentChangesService.changes(siteName, null, 50, false).changes();

        assertThat(rows).filteredOn(row -> "the nav was rewritten".equals(row.what()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.affects()).isNotNull();
                    assertThat(row.affects().kind()).isEqualTo("region");
                    assertThat(row.affects().layouts())
                            .containsExactly(ARTICLE_LAYOUT, PAGE_LAYOUT);
                    assertThat(row.affects().postTypes()).containsExactly(articleType, pageType);
                    assertThat(row.affects().published()).isEqualTo(2);
                });
        assertThat(rows).filteredOn(row -> "a headline was fixed".equals(row.what()))
                .singleElement()
                .satisfies(row -> assertThat(row.affects())
                        .as("absent, not empty: only two post-types can raise this question, and "
                                + "a field that is always present is one every row of the feed "
                                + "pays for (SH121's argument about ids)")
                        .isNull());
    }

    private void recordHistory(String objectId, String description) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShHistory entry = new ShHistory();
            entry.setId(UUID.randomUUID().toString());
            entry.setAction("UPDATE");
            entry.setShObject(objectId);
            entry.setShSite(site.getId());
            entry.setOwner("tester");
            // Old enough to clear the SH88 lag window, which holds back a just-written row.
            entry.setDate(Instant.parse("2026-03-01T10:00:00Z"));
            entry.setDescription(description);
            shHistoryRepository.save(entry);
        });
    }

    @Test
    @DisplayName("a warning does not fail the gate on its own")
    void unusedIsNotAGateFailure() {
        ShAgentReport report = verifyService.verify(siteName, "content", 200);

        assertThat(report.findings()).anyMatch(finding -> "unused-region".equals(finding.rule()));
        assertThat(report.ok())
                .as("this fixture has no content errors, so an unused template must not fail it — "
                        + "a gate that failed on work in progress is a gate people bypass")
                .isTrue();
    }

    // ── fixture ─────────────────────────────────────────────────────────────────────

    /**
     * Resolves and inspects inside one transaction.
     *
     * <p>{@code shPostType} and {@code shSite} are LAZY, so a post read in one transaction and
     * handed to the graph outside it throws — the same hazard SH176 and {@code ShRenderPostTypes}
     * both hit. The test therefore does what a caller does: it asks while the entity is
     * managed.</p>
     */
    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private ShTemplateImpact impactOf(String title) {
        return inTransaction(() -> graph.impactOf(postNamed(title))
                .orElseThrow(() -> new AssertionError("no impact for " + title)));
    }

    private Optional<ShTemplateImpact> maybeImpactOf(String title) {
        return inTransaction(() -> graph.impactOf(postNamed(title)));
    }

    private ShPostUnified postNamed(String title) {
        return entityManager.createQuery(
                        "select p from ShPostUnified p where p.title = :t and p.shSite.id = :s "
                                + "and p.state = com.viglet.shio.persistence.model.post.unified."
                                + "ShPostState.DRAFT", ShPostUnified.class)
                .setParameter("t", title)
                .setParameter("s", site.getId())
                .getResultList().stream().findFirst().orElseThrow();
    }

    private ShSite reload() {
        return inTransaction(() -> entityManager.find(ShSite.class, site.getId()));
    }

    private ShFolder folder(ShSite owner, Instant when) {
        ShFolder folder = new ShFolder();
        folder.setName("root");
        folder.setRootFolder((byte) 1);
        folder.setShSite(owner);
        folder.setDate(when);
        folder.setOwner("admin");
        entityManager.persist(folder);
        return folder;
    }

    private ShPostType postType(String name, byte system, Instant when) {
        ShPostType existing = entityManager
                .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                .setParameter("name", name)
                .getResultStream().findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }
        ShPostType type = new ShPostType();
        type.setName(name);
        type.setTitle(name);
        type.setSystem(system);
        type.setDate(when);
        type.setOwner("admin");
        entityManager.persist(type);
        return type;
    }

    private void post(ShSite owner, ShFolder folder, ShPostType type, String title, String furl,
                      String data, Instant when, ShPostState state) {
        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(state);
        post.setShSite(owner);
        post.setShFolder(folder);
        post.setShPostType(type);
        post.setTitle(title);
        post.setFurl(furl);
        post.setData(data);
        post.setDate(when);
        post.setOwner("admin");
        entityManager.persist(post);
    }

    private String json(String... pairs) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(pairs[i]).append("\":\"")
                    .append(pairs[i + 1].replace("\\", "\\\\").replace("\"", "\\\"")
                            .replace("\n", "\\n"))
                    .append('"');
        }
        return sb.append('}').toString();
    }
}
