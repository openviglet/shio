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
import com.viglet.shio.api.agent.ShAgentDigestPages;
import com.viglet.shio.api.agent.ShAgentProofService;
import com.viglet.shio.api.agent.ShAgentReadService;
import com.viglet.shio.api.agent.ShAgentReadTerse;
import com.viglet.shio.api.agent.dto.ShAgentDoc;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
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
    private static final String USED_THEME = "SH286_THEME";
    private static final String LONELY_THEME = "SH286_LONELY_THEME";
    /** SH558 — reached by `{{#getRelation}}` and by no THEME field, the shape SH549 generates. */
    private static final String RELATED_THEME = "SH558_RELATED_THEME";

    @Autowired
    private ShTemplateGraph graph;
    @Autowired
    private ShAgentVerifyService verifyService;
    @Autowired
    private ShAgentChangesService shAgentChangesService;
    @Autowired
    private ShHistoryRepository shHistoryRepository;
    @Autowired
    private ShAgentProofService proofService;
    @Autowired
    private ShAgentReadService readService;
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
                    json("HTML", "<body><div sh-region=\"" + SHARED_REGION + "\"></div>{{TEXT}}</body>",
                            "THEME", USED_THEME),
                    when, ShPostState.DRAFT);
            // SH558 — a bound layout that names no theme in THEME and reaches one by relation,
            // which is the shape SH549's generated layout has and the shape the rule could not read.
            post(created, root, layoutType, PAGE_LAYOUT, "/l-page",
                    json("HTML", "<body><div sh-region=\"" + SHARED_REGION + "\"></div>"
                            + "{{#getRelation \"/t-related\"}}<style>{{CSS}}</style>{{/getRelation}}"
                            + "</body>"),
                    when, ShPostState.DRAFT);
            post(created, root, layoutType, ORPHAN_LAYOUT, "/l-orphan",
                    json("HTML", "<body>nothing binds me</body>"), when, ShPostState.DRAFT);

            post(created, root, regionType, SHARED_REGION, "/r-nav",
                    json("HTML", "<nav>shared</nav>"), when, ShPostState.DRAFT);
            post(created, root, regionType, LONELY_REGION, "/r-lonely",
                    json("HTML", "<aside>nobody slots me</aside>"), when, ShPostState.DRAFT);

            // SH286 — the third edge: one theme a bound layout names, one nothing names.
            ShPostType themeType = postType(ShSystemPostType.THEME, (byte) 1, when);
            post(created, root, themeType, USED_THEME, "/t-used",
                    json("CSS", "body{color:red}"), when, ShPostState.DRAFT);
            post(created, root, themeType, LONELY_THEME, "/t-lonely",
                    json("CSS", "body{color:blue}"), when, ShPostState.DRAFT);
            // SH558 — reached only by PAGE_LAYOUT's relation, so it is used and no field says so.
            post(created, root, themeType, RELATED_THEME, "/t-related",
                    json("CSS", "body{color:green}"), when, ShPostState.DRAFT);

            // Two article drafts, one of them also published; one page, published.
            post(created, root, article, "A1", "/a1", json("TEXT", "one"), when, ShPostState.DRAFT);
            // SH321 found this fixture modelling a published post the way the product never
            // produces one: two `post(...)` calls mint two ids, so A2's draft and published rows
            // were *different posts sharing a friendly URL* — which is the duplicate-url defect,
            // not a published page. The pair shares an id, as `publishedAndDraft` does in
            // ShPageRenderTest and as ShPostUnifiedWriteService does in production.
            publishedAndDraft(created, root, article, "A2", "/a2", json("TEXT", "two"), when);
            publishedAndDraft(created, root, page, "P1", "/p1", json("TEXT", "p"), when);
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

    // ── SH263: a title is an identity ───────────────────────────────────────────────

    /**
     * A site of its own for the duplicate-title cases.
     *
     * <p>Not the shared fixture, deliberately: these need a *second* Region called
     * {@code SH193_NAV}, and adding one to the class fixture would make every later assertion in
     * this class see a duplicate — `unusedIsNotAGateFailure` asserts `ok()` is true and started
     * failing the moment this was written against the shared site. That is SH239's confound
     * (the batching pair writing into the folder the addressing pair measured), one class along:
     * a {@code PER_CLASS} fixture makes every mutating test an input to the others.</p>
     *
     * @return the new site's name
     */
    private String siteWithDuplicateTitles(String... regionTitles) {
        String name = "sh263-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite owner = new ShSite();
            owner.setName(name);
            owner.setDate(when);
            owner.setOwner("admin");
            entityManager.persist(owner);
            ShFolder root = folder(owner, when);
            ShPostType regionType = postType(ShSystemPostType.REGION, (byte) 1, when);
            int index = 0;
            for (String title : regionTitles) {
                post(owner, root, regionType, title, "/r-" + index++,
                        json("HTML", "<nav>" + title + "</nav>"), when, ShPostState.DRAFT);
            }
        });
        return name;
    }

    @Test
    @DisplayName("two templates claiming one title is an error, not a warning")
    void aDuplicateTitleIsAnError() {
        // The renderer resolves `sh-region="NAV"` by title and takes the first match, so which of
        // the two renders is undefined — and before SH263 nothing said so anywhere.
        String name = siteWithDuplicateTitles("NAV", "NAV", "FOOTER");

        ShAgentReport report = verifyService.verify(name, "content", 200);

        assertThat(report.findings())
                .filteredOn(finding -> "duplicate-template-title".equals(finding.rule()))
                .as("""
                        one finding per template involved, so each is addressed at a subject the                         reader can act on — the shape duplicate-url already uses. FOOTER is unique                         and must not appear.""")
                .hasSize(2)
                .allSatisfy(finding -> {
                    assertThat(finding.severity())
                            .as("""
                                    an error, unlike the unused-template warnings beside it: an                                     unused region may be work in progress, while two templates                                     competing for one name is provably wrong whatever anybody meant.""")
                            .isEqualTo("error");
                    assertThat(finding.message()).contains("NAV").contains("undefined");
                    assertThat(finding.fix()).contains("identity");
                });
        assertThat(report.findings())
                .filteredOn(finding -> "duplicate-template-title".equals(finding.rule()))
                .noneMatch(finding -> finding.message().contains("FOOTER"));
        assertThat(report.ok())
                .as("and it fails the gate, which the unused-template warnings deliberately do not")
                .isFalse();
    }

    @Test
    @DisplayName("a published copy of one template is not its own duplicate")
    void aPublishedRowIsNotADuplicate() {
        // The false positive that would make this rule useless: a post has a DRAFT row and a
        // PUBLISHED row sharing both id and title, so counting rows rather than ids would report
        // every published template in every instance as a duplicate of itself.
        String name = "sh263-pub-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite owner = new ShSite();
            owner.setName(name);
            owner.setDate(when);
            owner.setOwner("admin");
            entityManager.persist(owner);
            ShFolder root = folder(owner, when);
            ShPostType regionType = postType(ShSystemPostType.REGION, (byte) 1, when);
            String id = UUID.randomUUID().toString();
            for (ShPostState state : List.of(ShPostState.DRAFT, ShPostState.PUBLISHED)) {
                ShPostUnified row = new ShPostUnified();
                row.setId(id);
                row.setState(state);
                row.setShSite(owner);
                row.setShFolder(root);
                row.setShPostType(regionType);
                row.setTitle("ONLY_ONE");
                row.setFurl("/r-only");
                row.setData(json("HTML", "<nav>one</nav>"));
                row.setDate(when);
                row.setOwner("admin");
                entityManager.persist(row);
            }
        });

        assertThat(verifyService.verify(name, "content", 200).findings())
                .as("the two rows share an id, so they are one template")
                .noneMatch(finding -> "duplicate-template-title".equals(finding.rule()));
    }

    @Test
    @DisplayName("SH280: verify warns when published pages depend on an unpublished layout")
    void verifyWarnsAboutAnUnpublishedLayout() {
        String name = "sh280v-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh280vPage" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite owner = new ShSite();
            owner.setName(name);
            owner.setDate(when);
            owner.setOwner("admin");
            owner.setPostTypeLayout("{\"" + type + "\":\"SH280V_LAYOUT\"}");
            entityManager.persist(owner);
            ShFolder root = folder(owner, when);
            ShPostType pageType = postType(type, (byte) 0, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            post(owner, root, layoutType, "SH280V_LAYOUT", "/l",
                    json("HTML", "<html><body>x</body></html>"), when, ShPostState.DRAFT);
            post(owner, root, pageType, "Home", "/", json("TEXT", "b"), when, ShPostState.PUBLISHED);
        });

        assertThat(verifyService.verify(name, "content", 200).findings())
                .filteredOn(finding -> "unpublished-layout".equals(finding.rule()))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.severity())
                            .as("a warning: the remedy is one publish away, and SH99's line is that "
                                    + "ok is false only for what the CMS can prove is wrong")
                            .isEqualTo("warning");
                    assertThat(finding.message()).contains("SH280V_LAYOUT").contains(type);
                    assertThat(finding.fix()).contains("post.publish");
                });
    }

    @Test
    @DisplayName("SH280: a draft layout with no published pages behind it is work in progress")
    void aDraftLayoutWithNoPublishedPagesIsSilent() {
        // The second condition is what keeps this from firing on every site mid-build — the same
        // shape SH193's unused-template warnings use. Work in progress and something wrong are
        // almost always separated by another condition rather than by a stricter first one.
        String name = "sh280q-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh280qPage" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite owner = new ShSite();
            owner.setName(name);
            owner.setDate(when);
            owner.setOwner("admin");
            owner.setPostTypeLayout("{\"" + type + "\":\"SH280Q_LAYOUT\"}");
            entityManager.persist(owner);
            ShFolder root = folder(owner, when);
            ShPostType pageType = postType(type, (byte) 0, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            post(owner, root, layoutType, "SH280Q_LAYOUT", "/l",
                    json("HTML", "<html><body>x</body></html>"), when, ShPostState.DRAFT);
            post(owner, root, pageType, "Home", "/", json("TEXT", "b"), when, ShPostState.DRAFT);
        });

        assertThat(verifyService.verify(name, "content", 200).findings())
                .noneMatch(finding -> "unpublished-layout".equals(finding.rule()));
    }

    @Test
    @DisplayName("SH281: verify reports an unpublished region and an unpublished theme")
    void verifyReportsUnpublishedDependencies() {
        // These two levels degrade rather than refusing — a slot is skipped and a theme yields no
        // CSS — so a report is the only way anybody learns. The layout level has a 409 for the same
        // situation (SH280); this is what the other two get instead.
        String name = "sh281v-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh281vPage" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite owner = new ShSite();
            owner.setName(name);
            owner.setDate(when);
            owner.setOwner("admin");
            owner.setPostTypeLayout("{\"" + type + "\":\"SH281V_LAYOUT\"}");
            entityManager.persist(owner);
            ShFolder root = folder(owner, when);
            ShPostType pageType = postType(type, (byte) 0, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            ShPostType regionType = postType(ShSystemPostType.REGION, (byte) 1, when);
            ShPostType themeType = postType(ShSystemPostType.THEME, (byte) 1, when);

            // The layout is published; its region and its theme are not.
            String layoutData = json("HTML",
                    "<html><body><div sh-region=\"SH281V_NAV\"></div></body></html>",
                    "THEME", "SH281V_THEME");
            // SH324 — one id across both rows: `post` mints one per call, so a two-state loop
            // through it is two posts on one URL, which publishing cannot produce. Found by
            // ShFixtureShapeGuard, in the class SH321's refusal first fired on.
            bothStates(owner, root, layoutType, "SH281V_LAYOUT", "/l", layoutData, when);
            post(owner, root, regionType, "SH281V_NAV", "/r",
                    json("HTML", "<nav>x</nav>"), when, ShPostState.DRAFT);
            post(owner, root, themeType, "SH281V_THEME", "/t",
                    json("CSS", "body{color:red}"), when, ShPostState.DRAFT);
            post(owner, root, pageType, "Home", "/", json("TEXT", "b"), when, ShPostState.PUBLISHED);
        });

        assertThat(verifyService.verify(name, "content", 200).findings())
                .filteredOn(finding -> "unpublished-template".equals(finding.rule()))
                .as("both levels, each naming the layout that needs it")
                .hasSize(2)
                .allSatisfy(finding -> {
                    assertThat(finding.severity()).isEqualTo("warning");
                    assertThat(finding.message()).contains("SH281V_LAYOUT")
                            .contains("never been published");
                    assertThat(finding.fix())
                            .as("""
                                    and the fix says why this is a report and not a refusal: the                                     page is served incomplete or unstyled rather than failing.""")
                            .contains("post.publish")
                            .contains("refuses the render");
                })
                .extracting(com.viglet.shio.api.agent.verify.dto.ShAgentReport.Finding::message)
                .anySatisfy(message -> assertThat(message).contains("Region 'SH281V_NAV'"))
                .anySatisfy(message -> assertThat(message).contains("Theme 'SH281V_THEME'"));
    }

    // ── SH194: the one-call appearance loop ─────────────────────────────────────────

    @Test
    @DisplayName("a write to a shared region returns the digests of the pages it affected")
    void aWriteReturnsTheAffectedPageDigests() {
        // The loop this closes: upsert the region, render a page, compare. SH157 folded the lint
        // into the write; this folds the look. What is asserted is the difference between
        // "touched" and "affected" — the rows come back for the *pages*, not for the region that
        // was written, and only SH193's graph can make that step.
        ShAgentChangeSet written = proofService.attach(upsertOf("/r-nav"), false, true);

        assertThat(written.digests()).isNotNull();
        assertThat(written.digests()).extracting(ShAgentChangeSet.PageDigest::address)
                .as("""
                        the three pages of the two post-types whose layouts slot this region — not                         the region's own address, which would answer nothing, because a template                         digested as a page is not a page.""")
                .containsExactlyInAnyOrder(
                        "post:" + siteName + "/a1",
                        "post:" + siteName + "/a2",
                        "post:" + siteName + "/p1");
        assertThat(written.digests()).allSatisfy(row -> {
            assertThat(row.digest()).startsWith("d:");
            assertThat(row.source()).isEqualTo("rendered");
        });
    }

    @Test
    @DisplayName("SH266: the caller names how many pages to digest, and the cut is a row")
    void theCallerNamesTheDigestCount() {
        // SH194 capped this at ten because each row is a real render and ten read well. SH264 has
        // since measured a swept page at 6 queries, so the ceiling can be a decision: the default
        // stays, and a caller that wants fewer — an agent iterating on one layout — says so.
        ShAgentChangeSet written = proofService.attach(upsertOf("/r-nav"), false, true, 2);

        assertThat(written.digests()).hasSize(3);
        assertThat(written.digests()).element(2)
                .satisfies(row -> assertThat(row.address())
                        .as("""
                                the cut is a row, not a silence: an unreported cut reads as a \
                                complete answer (SH138), and it names the ceiling that applied so \
                                the caller learns the real bound from the answer rather than from \
                                the manifest it did not read.""")
                        .contains("2-page ceiling"));
        assertThat(written.digests()).filteredOn(row -> row.digest() != null)
                .as("two real digests and one cut row")
                .hasSize(2);
    }

    /**
     * SH319 — the narrowing the caller could not see.
     *
     * <p>The cut row above fires when the <em>pages</em> run out. Nothing fired when the
     * <em>request</em> was reduced: asking for 200 on an instance whose ceiling is 100 and getting
     * three rows was indistinguishable from three being all there was. The caller asked for two
     * hundred, got three, and could not tell whether the instance had disagreed with it.</p>
     */
    @Test
    @DisplayName("SH319: a request above the ceiling says so, even when nothing was cut")
    void aRequestAboveTheCeilingIsReported() {
        ShAgentChangeSet written = proofService.attach(upsertOf("/r-nav"), false, true, 200);

        assertThat(written.digests()).last()
                .satisfies(row -> assertThat(row.address())
                        .as("""
                                reported once rather than per row (SH146's shape), and it names                                 both numbers: what was asked for and what the instance allows""")
                        .contains("asked for 200")
                        .contains("ceiling is 100"));
        assertThat(written.digests()).filteredOn(row -> row.digest() != null)
                .as("every affected page is still here — this row is a note, not a cut")
                .isNotEmpty();
    }

    @Test
    @DisplayName("SH319: a request within the ceiling stays silent")
    void aRequestWithinTheCeilingSaysNothing() {
        ShAgentChangeSet written = proofService.attach(upsertOf("/r-nav"), false, true, 50);

        assertThat(written.digests()).allSatisfy(row -> assertThat(row.address())
                .as("nothing was narrowed, so there is nothing to report — a note on every "
                        + "response is the prose P3 says to stop paying for")
                .doesNotContain("asked for"));
    }

    @Test
    @DisplayName("SH266: `digest=yes` teaches instead of quietly meaning false")
    void aBadDigestValueTeaches() {
        assertThat(catchThrowable(() -> ShAgentDigestPages.count("yes")))
                .as("""
                        a caller writing `digest=yes` believes it asked for something. Reading it \
                        as false would answer a different question and look successful — the same \
                        false green that made an unknown `format` a 400 rather than a fallback.""")
                .isInstanceOf(ShAgentDigestPages.BadDigestValueException.class);
        assertThat(catchThrowable(() -> ShAgentDigestPages.count("0")))
                .as("zero is not 'no digests' — that is `false`, and a caller that computed a "
                        + "count and got zero has a bug it should hear about")
                .isInstanceOf(ShAgentDigestPages.BadDigestValueException.class);
        assertThat(ShAgentDigestPages.wanted("true")).isTrue();
        assertThat(ShAgentDigestPages.count("true"))
                .as("`true` still means the instance's default, which is what every pre-SH266 "
                        + "caller sends")
                .isNull();
        assertThat(ShAgentDigestPages.count("40")).isEqualTo(40);
    }

    private static Throwable catchThrowable(Runnable work) {
        try {
            work.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    @Test
    @DisplayName("a write to an ordinary page returns that page and nothing else")
    void aPageWriteReturnsItself() {
        ShAgentChangeSet written = proofService.attach(upsertOf("/a1"), false, true);

        assertThat(written.digests()).extracting(ShAgentChangeSet.PageDigest::address)
                .as("for a page, touched and affected coincide — the graph step is a no-op here")
                .containsExactly("post:" + siteName + "/a1");
    }

    @Test
    @DisplayName("SH301: a clean page's row is byte-identical, and a broken one says how many markers")
    void markersRideAlongOnlyWhenThereAreSome() {
        // Clean first, because "absent when zero" is the half that keeps SH266's `?digest=<n>` from
        // multiplying: a field on every row would cost the caller whatever count it asked for.
        assertThat(proofService.attach(upsertOf("/r-nav"), false, true).digests())
                .as("nothing here is broken, so the row carries the two hashes and nothing else")
                .allSatisfy(row -> assertThat(row.markers()).isNull());

        // Its own site, following the SH281 precedent in this class: the shared fixture is PER_CLASS
        // and JUnit's method order is deterministic but arbitrary, so a test that broke a layout in it
        // would make some other test's failure depend on which ran first.
        String name = "sh301m-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh301mPage" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite owner = new ShSite();
            owner.setName(name);
            owner.setDate(when);
            owner.setOwner("admin");
            owner.setPostTypeLayout("{\"" + type + "\":\"SH301M_LAYOUT\"}");
            entityManager.persist(owner);
            ShFolder root = folder(owner, when);
            ShPostType pageType = postType(type, (byte) 0, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            // The write SH301 is about: a layout slotting a region that does not exist, so every page
            // it reaches renders a marker where the slot should be.
            post(owner, root, layoutType, "SH301M_LAYOUT", "/l",
                    json("HTML", "<html><body><div sh-region=\"SH301M_GONE\"></div></body></html>"),
                    when, ShPostState.DRAFT);
            post(owner, root, pageType, "Home", "/home", json("TEXT", "b"), when, ShPostState.DRAFT);
        });

        ShAgentChangeSet written = proofService.attach(
                new ShAgentChangeSet(false, 1, 1,
                        List.of(new ShAgentChangeSet.Change("post.upsert", "post:" + name + "/l",
                                ShAgentChangeSet.Change.UPDATE, null, null))),
                false, true);

        assertThat(written.digests())
                .as("""
                        a marker count is not a comparison - it is an assertion about the page                         *now*, which is why it is the one non-hash field SH194's rule admits. And                         the caller that most wants it is this one: somebody who has just rewritten a                         shared template and is being told which pages it reached.""")
                .anySatisfy(row -> {
                    assertThat(row.address()).isEqualTo("post:" + name + "/home");
                    assertThat(row.markers()).isNotNull();
                    assertThat(row.markers().missingRegions()).isEqualTo(1);
                    assertThat(row.markers().any()).isTrue();
                });
    }

    @Test
    @DisplayName("digests are absent unless asked for, so nobody pays for a render they did not want")
    void digestsAreOptIn() {
        assertThat(proofService.attach(upsertOf("/r-nav"), false, false).digests()).isNull();
    }

    /** One committed `post.upsert` row against an address — the shape a real batch produces. */
    private ShAgentChangeSet upsertOf(String url) {
        return new ShAgentChangeSet(false, 1, 1,
                List.of(new ShAgentChangeSet.Change("post.upsert", "post:" + siteName + url,
                        ShAgentChangeSet.Change.UPDATE, null, null)));
    }

    @Test
    @DisplayName("a dry run returns no digests, because it changed nothing to compare")
    void aDryRunHasNoDigests() {
        ShAgentChangeSet planned = proofService.attach(
                new ShAgentChangeSet(true, 1, 1,
                        List.of(new ShAgentChangeSet.Change("post.upsert",
                                "post:" + siteName + "/r-nav",
                                ShAgentChangeSet.Change.UPDATE, null, null))),
                false, true);

        assertThat(planned.digests())
                .as("""
                        the same rule the lint half follows: a plan created no state, so a digest                         of the current one would be a claim about a future that has not happened.""")
                .isNull();
    }

    // ── SH286: a theme is the third edge ────────────────────────────────────────────

    @Test
    @DisplayName("SH286: a theme's blast radius is every page its layouts render")
    void aThemeReachesEveryPageOfItsLayouts() {
        ShTemplateImpact impact = impactOf(USED_THEME);

        assertThat(impact.kind()).isEqualTo("theme");
        assertThat(impact.layouts())
                .as("""
                        the third edge, and the one that is not a slot: a layout names its theme \
                        in a THEME *field* rather than in its markup, so the graph reads it from \
                        the layout post through the renderer's own accessor.""")
                .containsExactly(ARTICLE_LAYOUT);
        assertThat(impact.postTypes()).containsExactly(articleType);
        assertThat(impact.published())
                .as("a theme edit restyles every page of every type its layouts serve, which is "
                        + "the largest blast radius in the model and was the one that answered "
                        + "nothing at all before this")
                .isEqualTo(1);
        assertThat(impact.isUsed()).isTrue();
    }

    @Test
    @DisplayName("SH286: a read of a theme answers include=impact, like the other two")
    void aReadOfAThemeAnswersImpact() {
        assertThat(readService.read(List.of("post:" + siteName + "/t-used"), null, false, true))
                .singleElement()
                .satisfies(doc -> {
                    assertThat(doc.impact()).isNotNull();
                    assertThat(doc.impact().kind()).isEqualTo("theme");
                    assertThat(doc.impact().layouts()).containsExactly(ARTICLE_LAYOUT);
                });
        assertThat(readService.read(List.of("post:" + siteName + "/t-lonely"), null, false, true)
                .getFirst().impact())
                .as("""
                        and an unused theme still answers — with empty layouts, which says \
                        "nothing renders through this" rather than "this is not a template". \
                        Before SH286 a Theme got no impact section at all, which was \
                        indistinguishable from the same answer for an Article.""")
                .isNotNull()
                .satisfies(impact -> assertThat(impact.layouts()).isEmpty());
    }

    @Test
    @DisplayName("SH286: verify warns about a theme no bound layout names")
    void verifyReportsAnUnusedTheme() {
        ShAgentReport report = verifyService.verify(siteName, "content", 200);

        assertThat(report.findings())
                .filteredOn(finding -> "unused-theme".equals(finding.rule()))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.severity())
                            .as("a warning, like its two siblings: a theme written before the "
                                    + "layout that will name it is work in progress")
                            .isEqualTo("warning");
                    assertThat(finding.message()).contains(LONELY_THEME).contains("THEME");
                });
        assertThat(report.findings())
                .filteredOn(finding -> "unused-theme".equals(finding.rule()))
                .as("and the theme a bound layout names is not reported")
                .noneMatch(finding -> finding.message().contains(USED_THEME));
    }

    /**
     * SH558 — a theme a layout renders through {@code getRelation} is not unused.
     *
     * <h2>A binding the lint did not know how to read</h2>
     *
     * <p>{@code unused-theme} answered by reading the {@code THEME} field. Since SH549 a generated
     * layout names one theme there and reaches the other through
     * {@code {{#getRelation "/theme/captured"}}}, so the captured theme had no binding and was
     * reported unused — on a site where it renders, measurably, at a megabyte a page. The rule was
     * not wrong about what it read; it was wrong about <b>what a reference is</b>, the way SH516
     * was. But no page is needed to see this one: the argument is a literal, so the reference is
     * static in the template source.</p>
     *
     * <p>The immediate cost was a warning that cannot be cleared, on the file every conversion
     * writes, which is how a warning list stops being read. The wider one is that <em>is this theme
     * reachable</em> had a wrong answer, so a cleanup trusting it would delete a theme eleven pages
     * were using — which is why this asserts both directions of the same fixture: the related theme
     * is used, and the theme nothing reaches is still reported.</p>
     */
    @Test
    @DisplayName("SH558: a theme reached only by getRelation is used, and the lonely one still is not")
    void aThemeReachedByRelationIsNotUnused() {
        ShAgentReport report = verifyService.verify(siteName, "content", 200);

        assertThat(report.findings())
                .filteredOn(finding -> "unused-theme".equals(finding.rule()))
                .as("""
                        the whole finding: a layout that names one theme in THEME and relates \
                        another leaves neither unused. Before this, `%s` was reported while its \
                        CSS was on every page the layout renders.""".formatted(RELATED_THEME))
                .noneMatch(finding -> finding.message().contains(RELATED_THEME));
        assertThat(report.findings())
                .filteredOn(finding -> "unused-theme".equals(finding.rule()))
                .as("and the rule still answers the question it was for — widening what counts as "
                        + "a reference must not turn it into a rule that reports nothing")
                .anyMatch(finding -> finding.message().contains(LONELY_THEME));
    }

    @Test
    @DisplayName("SH558: the graph carries the relation edge, so a theme's blast radius includes it")
    void aRelatedThemeCarriesItsLayoutInTheGraph() {
        ShTemplateImpact impact = impactOf(RELATED_THEME);

        assertThat(impact.kind()).isEqualTo("theme");
        assertThat(impact.layouts())
                .as("the edge is real and not a special case inside the lint: `layoutsThemed` "
                        + "answers it, so `include=impact` and the reverse question read the same "
                        + "graph the renderer resolves (SH281's rule)")
                .containsExactly(PAGE_LAYOUT);
        assertThat(impact.isUsed())
                .as("a cleanup that trusted the old answer would have deleted a theme that "
                        + "eleven pages were using")
                .isTrue();
    }

    @Test
    @DisplayName("SH297: a layout naming a theme the site has not got is an error")
    void verifyReportsAMissingTheme() {
        String name = "sh297v-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh297vPage" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite owner = new ShSite();
            owner.setName(name);
            owner.setDate(when);
            owner.setOwner("admin");
            owner.setPostTypeLayout("{\"" + type + "\":\"SH297V_LAYOUT\"}");
            entityManager.persist(owner);
            ShFolder root = folder(owner, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            postType(type, (byte) 0, when);
            post(owner, root, layoutType, "SH297V_LAYOUT", "/l",
                    json("HTML", "<body>{{TEXT}}</body>", "THEME", "SH297V_ABSENT"),
                    when, ShPostState.DRAFT);
        });

        ShAgentReport report = verifyService.verify(name, "content", 200);

        assertThat(report.findings())
                .filteredOn(finding -> "missing-theme".equals(finding.rule()))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.severity())
                            .as("""
                                    an error, unlike the unused-* warnings beside it: the layout \
                                    names a theme and no post of that title exists in either \
                                    state, which is a claim that turned out false rather than a \
                                    guess about whether somebody is still working.""")
                            .isEqualTo("error");
                    assertThat(finding.address())
                            .as("reported against the layout, because that is the post to edit — "
                                    + "the theme does not exist, so it has no address")
                            .contains("/l");
                    assertThat(finding.message()).contains("SH297V_ABSENT")
                            .contains("looks like a CSS fault");
                });
        assertThat(report.ok()).isFalse();
    }

    @Test
    @DisplayName("SH298: an unused template says where that name *is* used")
    void anUnusedTemplateNamesTheSiteThatUsesIt() {
        // The case the fix line used to get wrong: "add sh-region to the layout that should show
        // it" names a layout in another site, which the reader will not find - so the message
        // reported that something was wrong and never what had happened.
        String other = "sh298-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite owner = new ShSite();
            owner.setName(other);
            owner.setDate(when);
            owner.setOwner("admin");
            entityManager.persist(owner);
            ShFolder root = folder(owner, when);
            ShPostType regionType = postType(ShSystemPostType.REGION, (byte) 1, when);
            // The same title the shared fixture's layouts slot - but this site binds no layout, so
            // here it is unused while the name is plainly in use next door.
            post(owner, root, regionType, SHARED_REGION, "/r-stray",
                    json("HTML", "<nav>stray</nav>"), when, ShPostState.DRAFT);
        });

        ShAgentReport report = verifyService.verify(null, "content", 500);

        assertThat(report.findings())
                .filteredOn(finding -> "unused-region".equals(finding.rule()))
                .filteredOn(finding -> finding.address() != null
                        && finding.address().contains("/r-stray"))
                .singleElement()
                .satisfies(finding -> assertThat(finding.message())
                        .as("the root cause, said once: this site binds nothing, so every template "
                                + "in it is unused and the defect is one missing postTypeLayout "
                                + "entry rather than N faulty templates")
                        .contains("binds no layouts at all")
                        .as("and where the name is used, so a stray template is a move rather than "
                                + "a re-creation")
                        .contains(siteName)
                        .contains("post.move")
                        .as("SH308 — the layout's own address. A site name is not an address and a "
                                + "layout title is not one either, so \"used in Blog\" told the "
                                + "reader what happened and then left them to find it; this names "
                                + "the post to open")
                        .contains("post:" + siteName + "/l-article")
                        .contains(ARTICLE_LAYOUT));
    }

    // ── SH262: the blast radius before the edit, on the read ────────────────────────

    @Test
    @DisplayName("SH262: a read of a region reports what renders through it, when asked")
    void aReadAnswersTheBlastRadius() {
        // The question SH193 and SH194 could only answer *afterwards*: this is the one an agent
        // asks before rewriting a shared region, and until now it had no surface at all.
        List<ShAgentDoc> docs = readService.read(
                List.of("post:" + siteName + "/r-nav"), null, false, true);

        assertThat(docs).singleElement().satisfies(doc -> {
            assertThat(doc.impact()).isNotNull();
            assertThat(doc.impact().kind()).isEqualTo("region");
            assertThat(doc.impact().layouts()).containsExactly(ARTICLE_LAYOUT, PAGE_LAYOUT);
            assertThat(doc.impact().postTypes()).containsExactly(articleType, pageType);
            assertThat(doc.impact().published())
                    .as("the number that decides whether to edit now, and the same number the "
                            + "change feed reports afterwards — one graph, so the answer cannot "
                            + "differ between before and after (P10)")
                    .isEqualTo(2);
            assertThat(doc.data()).as("and the fields are still there: this is a projection on a "
                    + "read, not a different call").isNotNull();
        });
    }

    @Test
    @DisplayName("SH262: it is absent unless the caller asked, and absent for an ordinary post")
    void impactIsOptInAndTemplateOnly() {
        assertThat(readService.read(List.of("post:" + siteName + "/r-nav"), null, false).getFirst()
                .impact())
                .as("""
                        opt-in: most reads want the fields, and a caller that has not asked about \
                        the blast radius should pay neither the graph walk nor the tokens. This is \
                        the half a test has to hold, because the cost of the default being wrong \
                        is invisible — every read would simply be bigger.""")
                .isNull();

        assertThat(readService.read(List.of("post:" + siteName + "/a1"), null, false, true)
                .getFirst().impact())
                .as("an ordinary post asked with impact=true gets no section rather than an "
                        + "error: a caller reading a mixed list of addresses must not have to know "
                        + "which of them are templates before it asks")
                .isNull();
    }

    @Test
    @DisplayName("SH262: a failed row in a mixed read keeps its error and gains no impact")
    void aFailedRowIsUntouched() {
        List<ShAgentDoc> docs = readService.read(
                List.of("post:" + siteName + "/r-nav", "post:" + siteName + "/nope"),
                null, false, true);

        assertThat(docs).hasSize(2);
        assertThat(docs.get(0).impact()).isNotNull();
        assertThat(docs.get(1).error())
                .as("SH171's row survives the projection — there is no post to ask about, and "
                        + "attaching an empty impact would read as a template that reaches nothing")
                .isNotNull();
        assertThat(docs.get(1).impact()).isNull();
    }

    @Test
    @DisplayName("SH262: the terse form carries it on its own line")
    void terseCarriesImpact() {
        String terse = ShAgentReadTerse.docs(
                readService.read(List.of("post:" + siteName + "/r-nav"), List.of("HTML"),
                        false, true));

        assertThat(terse).contains("impact region")
                .as("""
                        its own line and not attributes on `doc`, because the header describes the \
                        post while this describes everything else the post reaches. A reader that \
                        does not know the line skips it.""")
                .contains("layouts=" + ARTICLE_LAYOUT + "," + PAGE_LAYOUT)
                .contains("published=2");
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

    /** A post with both state rows under one id — the shape a published post actually has. */
    private void publishedAndDraft(ShSite owner, ShFolder folder, ShPostType type, String title,
                                   String furl, String data, Instant when) {
        String id = UUID.randomUUID().toString();
        for (ShPostState state : List.of(ShPostState.PUBLISHED, ShPostState.DRAFT)) {
            ShPostUnified row = new ShPostUnified();
            row.setId(id);
            row.setState(state);
            row.setShSite(owner);
            row.setShFolder(folder);
            row.setShPostType(type);
            row.setTitle(title);
            row.setFurl(furl);
            row.setData(data);
            row.setDate(when);
            row.setOwner("admin");
            entityManager.persist(row);
        }
    }

    /**
     * A published page: two rows, <b>one id</b> — the shape publishing actually produces (SH324).
     *
     * <p>Two {@code post(...)} calls give two ids, which is a duplicate URL rather than a published
     * page, and is the fixture defect SH321's refusal exposed and {@code ShFixtureShapeGuard} now
     * fails the build on.</p>
     */
    private void bothStates(ShSite owner, ShFolder folder, ShPostType type, String title,
                            String furl, String data, Instant when) {
        String id = UUID.randomUUID().toString();
        for (ShPostState state : List.of(ShPostState.PUBLISHED, ShPostState.DRAFT)) {
            ShPostUnified post = new ShPostUnified();
            post.setId(id);
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
