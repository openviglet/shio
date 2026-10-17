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
import java.util.UUID;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;
import com.viglet.shio.api.agent.dto.ShAgentContext;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH77 (context pack) + SH78 (token-lean responses).
 *
 * <p>The properties under test are the ones the design depends on, not the current
 * contents: <b>determinism</b> (an unstable digest destroys the "skip re-reading"
 * saving that justifies the whole endpoint), <b>honest truncation</b> (a cut must be
 * reported and resumable, and must never touch the model), <b>projection</b>, and
 * the <b>token claim</b> — terse really is materially cheaper than JSON, asserted as
 * a number rather than assumed.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentContextTest {

    @Autowired
    private ShAgentContextService shAgentContextService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String siteId;
    private String postTypeName;
    private String firstPostId;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            siteName = "agent-site-" + UUID.randomUUID();
            site.setName(siteName);
            site.setUrl("http://agent.example.test");
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            root.setOwner("admin");
            entityManager.persist(root);

            ShFolder blog = new ShFolder();
            blog.setName("blog");
            blog.setRootFolder((byte) 0);
            blog.setParentFolder(root);
            blog.setShSite(site);
            blog.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            blog.setOwner("admin");
            entityManager.persist(blog);

            ShWidget widget = new ShWidget();
            widget.setName("Text");
            widget.setDescription("Text widget");
            entityManager.persist(widget);

            ShPostType postType = new ShPostType();
            postTypeName = "AgentArticle" + UUID.randomUUID().toString().substring(0, 8);
            postType.setName(postTypeName);
            postType.setTitle("Agent Article");
            postType.setSystem((byte) 0);
            postType.setManaged(true);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setId(UUID.randomUUID().toString());
            attr.setName("headline");
            attr.setLabel("Headline");
            attr.setOrdinal(1);
            attr.setRequired((byte) 1);
            attr.setIsTitle((byte) 1);
            attr.setShWidget(widget);
            attr.setShPostType(postType);
            entityManager.persist(attr);

            // Ten posts under /blog so a small budget has something to truncate.
            for (int i = 0; i < 10; i++) {
                String id = persistPost(site, blog, postType, ShPostState.DRAFT,
                        String.format("Post %02d", i), "/blog/post-" + i);
                if (i == 0) {
                    firstPostId = id;
                    // SH553 — one of the ten is also live, the way `publish()` writes it: a second
                    // row sharing the id, with the draft left in place. Without a published twin in
                    // the fixture the sitemap's own defect is unobservable, which is how it
                    // survived SH545.
                    ShPostUnified live = new ShPostUnified();
                    live.setId(id);
                    live.setState(ShPostState.PUBLISHED);
                    live.setShSite(site);
                    live.setShFolder(blog);
                    live.setShPostType(postType);
                    live.setTitle(String.format("Post %02d", i));
                    live.setFurl("/blog/post-" + i);
                    live.setData("{}");
                    live.setDate(Instant.parse("2026-01-01T00:00:00Z"));
                    live.setOwner("admin");
                    entityManager.persist(live);
                }
            }
        });
    }

    private String persistPost(ShSite site, ShFolder folder, ShPostType postType,
                               ShPostState state, String title, String furl) {
        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(state);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(postType);
        post.setTitle(title);
        post.setFurl(furl);
        post.setOwner("admin");
        post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        post.setData("{\"headline\":\"" + title + "\"}");
        entityManager.persist(post);
        return post.getId();
    }

    private ShAgentContext context() {
        return shAgentContextService.context(siteName, null, 0, null, null);
    }

    @Test
    void packAnswersModelSitemapAndConventionsInOneCall() {
        ShAgentContext pack = context();

        assertThat(pack.protocol()).isEqualTo(ShAgentManifestService.PROTOCOL);
        assertThat(pack.manifest()).isEqualTo("/api/v2/agent/manifest");
        assertThat(pack.instance().product()).isEqualTo("Viglet Shio");
        assertThat(pack.sites()).extracting(ShAgentContext.Site::name).contains(siteName);
        assertThat(pack.model()).extracting(ShAgentContext.Type::name).contains(postTypeName);
        assertThat(pack.sitemap()).isNotEmpty();
        assertThat(pack.conventions()).isNotEmpty();
        assertThat(pack.digest()).startsWith("c:");
    }

    @Test
    void modelCarriesFieldsAndUsageCounts() {
        ShAgentContext.Type type = context().model().stream()
                .filter(candidate -> postTypeName.equals(candidate.name()))
                .findFirst().orElseThrow();

        assertThat(type.title()).isEqualTo("Agent Article");
        assertThat(type.managed()).isTrue();
        assertThat(type.system()).isFalse();
        // Counted on distinct ids: the unified model stores up to two rows per post.
        assertThat(type.posts()).isEqualTo(10);
        assertThat(type.fields()).singleElement().satisfies(field -> {
            assertThat(field.name()).isEqualTo("headline");
            assertThat(field.widget()).isEqualTo("Text");
            assertThat(field.required()).isTrue();
        });
    }

    @Test
    void modelIsSortedByNameSoTheDigestIsStable() {
        List<String> names = context().model().stream().map(ShAgentContext.Type::name).toList();
        assertThat(names).isSorted();
    }

    @Test
    @DisplayName("SH553: the sitemap says which pages are also live, not DRAFT for all of them")
    void theSitemapReportsPublished() {
        List<ShAgentContext.Node> posts = context().sitemap().stream()
                .filter(node -> "post".equals(node.kind()))
                .toList();

        // The defect, and P2 is why it mattered: the pack is the call that replaces a session, so an
        // agent that opens with it learned the structure and was then misinformed about the state of
        // every page in a fully published site.
        assertThat(posts).filteredOn(node -> Boolean.TRUE.equals(node.published()))
                .as("""
                        the published twin is reported, though the row the walk kept is the DRAFT                         one — `folderPosts` prefers the draft, which is exactly what made every                         page read as a draft (SH553)""")
                .hasSize(1)
                .allSatisfy(node -> assertThat(node.url()).isEqualTo("/blog/post-0"));
    }

    @Test
    @DisplayName("SH553: a draft omits the field entirely rather than saying published:false")
    void aDraftSaysNothingAboutBeingPublished() throws Exception {
        // Asserted rather than assumed, which the task asked for by name: `Node` uses NON_DEFAULT
        // and not NON_NULL, so whether a `Boolean` false is omitted does not transfer from SH545's
        // reasoning about `ShAgentDoc` — it has to be checked on this record.
        List<ShAgentContext.Node> drafts = context().sitemap().stream()
                .filter(node -> "post".equals(node.kind()))
                .filter(node -> !"/blog/post-0".equals(node.url()))
                .toList();
        assertThat(drafts).isNotEmpty().allSatisfy(node ->
                assertThat(node.published())
                        .as("null and not FALSE, so the row has nothing to serialize")
                        .isNull());

        String json = new tools.jackson.databind.ObjectMapper().writeValueAsString(drafts.get(0));
        assertThat(json)
                .as("""
                        and the wire agrees: a site with no published page pays nothing per row for                         this field, which is the measurement that let it be added at all — the                         sitemap is one node per post and the token-heaviest of the three surfaces                         SH545 touched (SH553)""")
                .doesNotContain("published");
    }

    @Test
    @DisplayName("SH608: a narrowed pack says which sections it dropped")
    void aNarrowedPackNamesWhatItDropped() {
        // Scoped to this class's own site: a site-less read answers for the whole instance, so on
        // the shared context an assertion about it would be a statement about suite order — which
        // ShBenchmarkIsolationLintTest refuses by name.
        ShAgentContext narrowed = shAgentContextService.context(siteName, null, null, "instance", null);

        // The defect: four scalars, a 200, and no word about the model, the sitemap or the
        // conventions — including the remembered notes an entire SH597 intent is measured against.
        assertThat(narrowed.omitted())
                .as("""
                        a caller narrowing a DISCOVERY surface is by construction one who does not                         yet know what is there, which is why a silent projection misleads worse                         here than a 404 would (SH608)""")
                .containsExactlyInAnyOrder("sites", "model", "sitemap", "conventions");
    }

    @Test
    @DisplayName("SH608: a whole pack pays nothing for the report, and opt-in sections are not omissions")
    void awholePackReportsNothing() {
        ShAgentContext whole = shAgentContextService.context(siteName, null, null, null, null);

        assertThat(whole.omitted())
                .as("""
                        absent entirely rather than an empty list: the full document pays nothing                         for a report it has no content for. `manifest` and `ops` are opt-IN, so                         naming them here would tell every default request that two sections were                         withheld — the opposite of what the field is for (SH608)""")
                .isNull();
    }

    @Test
    @DisplayName("SH608: attaching the report does not move the digest")
    void attachingOmittedDoesNotMoveTheDigest() {
        // The property that keeps SH116's conditional read working. `omitted` describes the CALL,
        // like `budget` — so attaching it must not change the entity tag, or a narrowed pack would
        // be uncacheable for exactly the readers who narrowed to save tokens.
        //
        // Asserted on `withOmitted` rather than by comparing a whole pack with a narrowed one: those
        // two legitimately differ, because narrowing changes the pack's *content*. Comparing them
        // would have passed while proving nothing about this field.
        ShAgentContext whole = shAgentContextService.context(siteName, null, null, null, null);

        assertThat(whole.withOmitted(java.util.List.of("sitemap", "model")).digest())
                .as("the digest covers the pack's content, and what a caller asked for is not it")
                .isEqualTo(whole.digest());
    }

    @Test
    void sitemapCarriesPathsNotJustIds() {
        List<ShAgentContext.Node> sitemap = context().sitemap();

        assertThat(sitemap).anySatisfy(node -> {
            assertThat(node.kind()).isEqualTo("folder");
            assertThat(node.path()).isEqualTo("/");
        });
        assertThat(sitemap).anySatisfy(node -> {
            assertThat(node.kind()).isEqualTo("folder");
            assertThat(node.path()).isEqualTo("/blog");
            assertThat(node.posts()).isEqualTo(10);
        });
        assertThat(sitemap).filteredOn(node -> "post".equals(node.kind())).allSatisfy(node -> {
            assertThat(node.path()).isEqualTo("/blog");
            assertThat(node.type()).isEqualTo(postTypeName);
            assertThat(node.state()).isEqualTo("DRAFT");
            assertThat(node.url()).startsWith("/blog/post-");
            assertThat(node.title()).startsWith("Post ");
        });
    }

    @Test
    void digestIsStableAcrossIdenticalCalls() {
        // This is the load-bearing assertion of the whole endpoint: if the digest
        // churns, an agent can never skip a re-read and SH116's 304 is impossible.
        assertThat(context().digest()).isEqualTo(context().digest());
    }

    @Test
    void digestIgnoresBudgetAndFormatSoItTracksContentOnly() {
        String unbounded = shAgentContextService.context(siteName, null, 0, null, null).digest();
        String generous = shAgentContextService.context(siteName, null, 40000, null, null).digest();

        assertThat(generous).isEqualTo(unbounded);
    }

    @Test
    void digestChangesWhenTheModelChanges() {
        String before = context().digest();
        String extra = "AgentExtra" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShPostType type = new ShPostType();
            type.setName(extra);
            type.setTitle("Extra");
            type.setSystem((byte) 0);
            type.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            type.setOwner("admin");
            entityManager.persist(type);
        });

        assertThat(context().digest()).isNotEqualTo(before);
    }

    /**
     * A budget that fits the bounded sections and some — but not all — of the
     * sitemap. Derived from the actual pack size rather than hard-coded, so the test
     * keeps testing truncation when the fixture or the conventions text changes.
     */
    /**
     * A budget that lands <em>inside</em> the sitemap: everything else, plus half of what
     * the sitemap costs.
     *
     * <p>The obvious version — three quarters of the whole pack — is not a cut point at
     * all. The mandatory sections (instance, sites, model, conventions) are charged first
     * and the <b>model is instance-wide</b>, so any test class that adds a post-type makes
     * them a bigger share of the total; once they pass three quarters, "three quarters of
     * the total" starves the sitemap of every row and the resume assertions fail for a
     * reason that has nothing to do with resuming. Measuring the sitemap's own cost keeps
     * the cut where the test means it, whatever else the shared database happens to
     * hold.</p>
     */
    private int budgetThatCutsTheSitemap() {
        int whole = shAgentContextService.context(siteName, null, 0, null, null)
                .budget().estTokens();
        int withoutSitemap = shAgentContextService
                .context(siteName, null, 0, "instance,sites,model,conventions", null)
                .budget().estTokens();
        return Math.max(withoutSitemap + (whole - withoutSitemap) / 2, 1);
    }

    @Test
    void tinyBudgetTruncatesTheSitemapAndSaysSo() {
        ShAgentContext full = shAgentContextService.context(siteName, null, 0, null, null);
        ShAgentContext pack = shAgentContextService.context(siteName, null,
                budgetThatCutsTheSitemap(), null, null);

        assertThat(pack.budget().truncated()).isTrue();
        assertThat(pack.budget().truncatedAfter()).isNotBlank();
        assertThat(pack.sitemap()).hasSizeLessThan(full.sitemap().size());
        assertThat(pack.budget().estTokens()).isLessThanOrEqualTo(pack.budget().limitTokens());
    }

    @Test
    void aBudgetTooSmallForAnyRowSaysThereIsNothingToResumeAfter() {
        // truncated + no cursor is the honest way to say "nothing fit": inventing a
        // resume point here would send the caller into an infinite loop.
        ShAgentContext pack = shAgentContextService.context(siteName, null, 1, null, null);

        assertThat(pack.sitemap()).isEmpty();
        assertThat(pack.budget().truncated()).isTrue();
        assertThat(pack.budget().truncatedAfter()).isNull();
        assertThat(pack.budget().overBudget()).isTrue();
    }

    @Test
    void truncationNeverDamagesTheModel() {
        // A partial model is not a smaller answer, it is a wrong one — an agent that
        // sees 1 of 3 types will confidently invent the other two.
        int full = shAgentContextService.context(siteName, null, 0, null, null).model().size();
        ShAgentContext squeezed = shAgentContextService.context(siteName, null, 1, null, null);

        assertThat(squeezed.model()).hasSize(full);
        assertThat(squeezed.budget().overBudget()).isTrue();
    }

    @Test
    void truncationIsResumableWithoutDuplicates() {
        int budget = budgetThatCutsTheSitemap();
        ShAgentContext first = shAgentContextService.context(siteName, null, budget, null, null);
        ShAgentContext second = shAgentContextService.context(siteName, null, budget, null,
                first.budget().truncatedAfter());

        // Compared on the rendered row, not on `id`: under SH121 the default response
        // carries no ids, and resume has to keep working precisely because of that —
        // the cursor is one id in the budget block, never a field on every row.
        List<String> firstRows = first.sitemap().stream().map(ShAgentContextTerse::node).toList();
        List<String> secondRows = second.sitemap().stream().map(ShAgentContextTerse::node).toList();

        assertThat(secondRows).isNotEmpty().doesNotContainAnyElementsOf(firstRows);
    }

    @Test
    void includeProjectsSections() {
        ShAgentContext modelOnly = shAgentContextService.context(siteName, null, 0, "model", null);

        assertThat(modelOnly.model()).isNotEmpty();
        assertThat(modelOnly.sitemap()).isEmpty();
        assertThat(modelOnly.sites()).isEmpty();
        assertThat(modelOnly.instance()).isNull();
        assertThat(modelOnly.conventions()).isEmpty();
    }

    @Test
    void unknownIncludeSectionTeachesTheVocabulary() {
        assertThatThrownBy(() -> shAgentContextService.context(siteName, null, 0, "sitmap", null))
                .isInstanceOf(ShAgentContextSection.UnknownSectionException.class)
                .satisfies(thrown -> {
                    var properties = ((ShAgentContextSection.UnknownSectionException) thrown)
                            .toProblemDetail().getProperties();
                    assertThat(properties).containsEntry("didYouMean", "sitemap");
                    // `manifest` (SH157), `ops` (SH346) and `usage` (SH754) are in the allowed set
                    // even though none is in the default one. A caller reading this error is
                    // asking what it may name, and the sections it has to name explicitly are the
                    // ones it most needs told about — for `ops` that is the whole point: it is the
                    // write vocabulary, and a caller that never learns the section exists is back
                    // to the MCP-only state SH346 closed. `usage` is the same argument about
                    // spend: a budget a caller cannot ask about is one it has to be told.
                    // SH993 — `releases` joins the opt-in three for the same reason `ops` did: a
                    // caller that never learns the section exists cannot discover a release by name.
                    // SH1050 — and `status` for the same reason again: an agent that cannot name
                    // the section has to compose "what is going on here" from four other calls,
                    // which is the sequence this section exists to replace.
                    assertThat(properties.get("allowed")).isEqualTo(
                            List.of("instance", "sites", "model", "sitemap", "conventions",
                                    "manifest", "ops", "usage", "releases", "status"));
                });
    }

    @Test
    void unknownSiteListsTheReadableOnes() {
        assertThatThrownBy(() -> shAgentContextService.context("no-such-site", null, 0, null, null))
                .isInstanceOf(ShAgentContextService.UnknownSiteException.class)
                .satisfies(thrown -> {
                    Object allowed = ((ShAgentContextService.UnknownSiteException) thrown)
                            .toProblemDetail().getProperties().get("allowed");
                    assertThat(allowed).asInstanceOf(InstanceOfAssertFactories.list(String.class))
                            .contains(siteName);
                });
    }

    @Test
    void siteResolvesByIdAsWellAsName() {
        // An id is still accepted *as input* — SH121 only stopped emitting it, and the
        // asymmetry is deliberate: a caller that has an id should never be made to
        // translate it first.
        assertThat(shAgentContextService.context(siteId, null, 0, null, null).sites())
                .extracting(ShAgentContext.Site::name).containsExactly(siteName);
    }

    @Test
    void siteAlsoAcceptsAnAddress() {
        // One vocabulary for naming a site, whichever surface the caller came from (SH79).
        assertThat(shAgentContextService.context("site:" + siteName, null, 0, null, null).sites())
                .extracting(ShAgentContext.Site::name).containsExactly(siteName);
    }

    @Test
    void pathAddressingAddsNoPerRowField() {
        // SH79's shape decision, pinned. A row already carries its site, path and url,
        // so a built `address=` field would pay tokens to repeat what the caller can
        // derive — which would have made "the biggest token lever" a token regression.
        // The grammar is stated once, in the conventions, instead.
        // Row-scoped, not whole-document: the conventions legitimately mention
        // `?address=` when they teach the resolve call, and a blunt substring check on
        // the whole pack would fail on its own documentation.
        List<String> rows = ShAgentContextTerse.render(context()).lines()
                .filter(line -> line.startsWith("node ") || line.startsWith("site "))
                .toList();

        assertThat(rows).isNotEmpty().allSatisfy(row -> assertThat(row).doesNotContain("address="));
        assertThat(context().conventions())
                .anySatisfy(line -> assertThat(line).contains("post:<site>/<url>"));
    }

    @Test
    void conventionsStateTheRulesAnAgentWouldOtherwiseLearnByFailing() {
        List<String> conventions = context().conventions();

        assertThat(conventions).anySatisfy(line -> assertThat(line).contains("DRAFT"));
        assertThat(conventions).anySatisfy(line -> assertThat(line).contains("shio push"));
        assertThat(conventions).anySatisfy(line -> assertThat(line).contains("post:<site>/<url>"));
    }

    @Test
    void terseIsCheaperThanJsonForTheWholePack() {
        ShAgentContext pack = context();
        int terse = ShTokenBudget.estimate(ShAgentContextTerse.render(pack));
        int json = ShTokenBudget.estimate(objectMapper.writeValueAsString(pack));

        assertThat(terse).isLessThan(json);
    }

    @Test
    void theTerseSavingIsPerRowNotPerPayload() {
        // Where the saving actually comes from, stated as a number (P3). JSON pays for
        // braces and *every key name* on every row; terse states the shape once in the
        // verb, so the win scales with row count, not payload size — on a small pack
        // the fixed text (conventions) dilutes it. Measured against a JSON that is
        // itself already lean (NON_DEFAULT), because beating a deliberately bloated
        // alternative would prove nothing. The margin is ~25–35% per row; the residual
        // cost is dominated by the 36-char UUID, which is SH79's lever, not encoding's.
        ShAgentContext.Node node = context().sitemap().stream()
                .filter(candidate -> "post".equals(candidate.kind()))
                .findFirst().orElseThrow();

        int terseRow = ShTokenBudget.estimate(ShAgentContextTerse.node(node));
        int jsonRow = ShTokenBudget.estimate(objectMapper.writeValueAsString(node));

        assertThat(terseRow).isLessThan(jsonRow);
    }

    @Test
    void agentJsonDoesNotPayForNullsOrZeroDefaults() {
        ShAgentContext.Node post = context().sitemap().stream()
                .filter(candidate -> "post".equals(candidate.kind()))
                .findFirst().orElseThrow();

        String json = objectMapper.writeValueAsString(post);

        assertThat(json).doesNotContain("null").doesNotContain("\"folders\"").doesNotContain("\"posts\"");
    }

    @Test
    void terseRenderingIsSelfDescribingAndComplete() {
        String terse = ShAgentContextTerse.render(context());

        assertThat(terse).startsWith("shio-context 1 manifest=/api/v2/agent/manifest\n");
        assertThat(terse).contains("node folder /blog ");
        assertThat(terse).contains("state=DRAFT");
        // Widget names are kept verbatim — they are what a write must send.
        assertThat(terse).contains("fields=headline:Text!");
        assertThat(terse).contains("\ndigest c:");
        assertThat(terse).contains("\nbudget est=");
    }

    // --- SH1056: the site's locale policy, where it costs something to say ---

    @Test
    @DisplayName("SH1056: a monolingual site in the instance default says nothing about locales")
    void theOrdinarySiteCostsNoLocaleTokens() {
        ShAgentContext pack = context();
        String terse = ShAgentContextTerse.render(pack);

        assertThat(pack.sites()).isNotEmpty().allSatisfy(site -> {
            assertThat(site.defaultLocale())
                    .as("SH1056: the pack already publishes the instance default once, on "
                            + "`instance` — repeating it on every site row is tokens spent to "
                            + "say the ordinary thing (P3)")
                    .isNull();
            assertThat(site.locales())
                    .as("SH1056: and a list of one naming that same default repeats two facts "
                            + "to add none")
                    .isNull();
            assertThat(site.localesDeclared()).isNull();
        });
        assertThat(terse)
                .as("SH1056: the terse form spends nothing either — this is the assertion that "
                        + "fails if a later row starts emitting a locale unconditionally")
                .doesNotContain("locales=")
                .doesNotContain("locales-declared=");
    }

    @Test
    @DisplayName("SH1056: a declared policy reaches the pack, and says it was a decision")
    void aDeclaredPolicyIsVisibleAndMarkedAsChosen() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = entityManager
                    .createQuery("select s from ShSite s where s.name = :name", ShSite.class)
                    .setParameter("name", siteName)
                    .getSingleResult();
            site.setLocales("{\"default\":\"pt-br\",\"enabled\":[\"pt-br\",\"en\"],"
                    + "\"fallback\":{}}");
            entityManager.merge(site);
        });

        ShAgentContext pack = shAgentContextService.context(siteName, null, 0, null, null);
        ShAgentContext.Site row = pack.sites().stream()
                .filter(each -> siteName.equals(each.name()))
                .findFirst().orElseThrow();

        assertThat(row.defaultLocale()).isEqualTo("pt-br");
        assertThat(row.locales()).containsExactly("pt-br", "en");
        assertThat(row.localesDeclared())
                .as("SH1056: \"offers Portuguese\" means one thing as a decision and another as a "
                        + "description of what has been translated so far, and this is the flag "
                        + "that tells them apart")
                .isTrue();
        assertThat(ShAgentContextTerse.render(pack))
                .contains("locales=pt-br,en")
                .contains("locales-declared=true");
    }

    /**
     * SH1160 — the pack answers exactly what a site marker file needs, and no more.
     *
     * <h2>The blocker SH1122 recorded</h2>
     *
     * <p>SH1122 gave a site's settings a file — `shio/folders/SITE/_site.md`, carrying `url`,
     * `furl`, `description` and SH1056's locale policy — and could only <b>push</b> it. Projecting one
     * <em>from</em> an instance needed a read of those four, and there was none: this row carried
     * `url` and the three locale fields, the console's whole-entity site read answers the 5 MB
     * `postTypeLayout` column along with them, and P3 makes "read 5 MB to write four short strings"
     * a regression rather than an implementation detail.</p>
     *
     * <h2>Why the stored policy rather than the three fields already here</h2>
     *
     * <p>The three are the <em>discovery</em> answer and each is omitted when it says the ordinary
     * thing: a default matching the instance's is absent, a single-locale list is absent, and
     * {@code locales} carries no {@code fallback} at all. A tree reconstructing a policy from them
     * would have to invent the chain and guess the default — so a projection would write four of the
     * marker's five keys and call it a round trip, which is lying by omission. {@code localePolicy}
     * is the stored document, built by {@code ShSiteLocales.toNode()} — the same method
     * {@code toJson} now delegates to, so there is one spelling of that document and not two.</p>
     *
     * <h2>And it costs nothing on a site nobody configured</h2>
     *
     * <p>All three additions are {@code NON_NULL}. Measured: `response.context.json` did not move by
     * a single token, because the benchmark's site has none of them —
     * `ShAgentResponseBudgetTest#measureConfiguredSiteRow` is the second measurement, taken on a site
     * that HAS been configured, because a number that only measures the absent case says nothing
     * about the field.</p>
     */
    @Test
    @DisplayName("SH1160: the pack carries furl, description and the stored policy a marker needs")
    void aSiteRowAnswersWhatAMarkerFileNeeds() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = entityManager
                    .createQuery("select s from ShSite s where s.name = :name", ShSite.class)
                    .setParameter("name", siteName)
                    .getSingleResult();
            site.setFurl("marker-furl");
            site.setDescription("What this site is for");
            site.setLocales("{\"default\":\"pt-br\",\"enabled\":[\"pt-br\",\"en\"],"
                    + "\"fallback\":{\"en\":\"pt-br\"}}");
            entityManager.merge(site);
        });

        ShAgentContext.Site row = shAgentContextService.context(siteName, null, 0, null, null)
                .sites().stream().filter(each -> siteName.equals(each.name()))
                .findFirst().orElseThrow();

        assertThat(row.furl()).isEqualTo("marker-furl");
        assertThat(row.description()).isEqualTo("What this site is for");
        assertThat(row.localePolicy()).isNotNull();
        assertThat(row.localePolicy().get("default").stringValue()).isEqualTo("pt-br");
        assertThat(row.localePolicy().get("fallback").get("en").stringValue())
                .as("SH1160: the fallback chain, which is the half the three discovery fields cannot "
                        + "carry and the half a reconstruction would have to invent")
                .isEqualTo("pt-br");

        // Blank is absent, for the reason the marker treats it so: a column is either unset or holds
        // something, and `""` is the first. Writing the key would make an empty value and an absent
        // one the same thing on the way back in.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = entityManager
                    .createQuery("select s from ShSite s where s.name = :name", ShSite.class)
                    .setParameter("name", siteName).getSingleResult();
            site.setFurl("   ");
            site.setDescription("");
            site.setLocales(null);
            entityManager.merge(site);
        });
        ShAgentContext.Site blank = shAgentContextService.context(siteName, null, 0, null, null)
                .sites().stream().filter(each -> siteName.equals(each.name()))
                .findFirst().orElseThrow();
        assertThat(blank.furl()).isNull();
        assertThat(blank.description()).isNull();
        assertThat(blank.localePolicy())
                .as("SH1160: a DERIVED policy is absent too — a marker carrying one would turn an "
                        + "observation into a declaration on the next push (SH1056)")
                .isNull();
    }

    /**
     * SH1160 — the terse pack deliberately does not carry them, and that is the P3 half.
     *
     * <p>Terse is the cheapest rendering of the call that replaces a session, and the tree reads
     * {@code format=json}. Three more attributes per site on the cheap form would be paid by every
     * caller for one caller's benefit, so they are on the JSON row only — which is also why the
     * budget guard measures the JSON row and not this one.</p>
     */
    @Test
    @DisplayName("SH1160: the terse rendering stays as cheap as it was")
    void theTerseRowDoesNotCarryTheMarkerFields() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = entityManager
                    .createQuery("select s from ShSite s where s.name = :name", ShSite.class)
                    .setParameter("name", siteName).getSingleResult();
            site.setFurl("terse-furl");
            site.setDescription("A description terse must not carry");
            entityManager.merge(site);
        });

        String terse = ShAgentContextTerse.render(
                shAgentContextService.context(siteName, null, 0, null, null));

        assertThat(terse)
                .doesNotContain("terse-furl")
                .doesNotContain("A description terse must not carry")
                .doesNotContain("locale-policy=");
    }

    @Test
    void terseAndJsonDescribeTheSameState() {
        ShAgentContext pack = context();
        String terse = ShAgentContextTerse.render(pack);

        // One digest for both encodings: the terse body *is* the canonical form.
        assertThat(terse).contains("digest " + pack.digest());
    }

    // --- SH121: id-free reads ---

    private ShAgentContext withIds() {
        return shAgentContextService.context(siteName, null, 0, null, null, true);
    }

    @Test
    void rowsCarryNoIdByDefault() {
        ShAgentContext pack = context();

        assertThat(pack.sitemap()).isNotEmpty()
                .allSatisfy(node -> assertThat(node.id()).isNull());
        assertThat(pack.sites()).isNotEmpty().allSatisfy(site -> {
            assertThat(site.id()).isNull();
            assertThat(site.rootFolderId()).isNull();
        });
    }

    @Test
    void idsTrueRestoresThem() {
        ShAgentContext pack = withIds();

        assertThat(pack.sitemap()).isNotEmpty()
                .allSatisfy(node -> assertThat(node.id()).isNotBlank());
        assertThat(pack.sites()).isNotEmpty().allSatisfy(site -> {
            assertThat(site.id()).isEqualTo(siteId);
            assertThat(site.rootFolderId()).isNotBlank();
        });
    }

    @Test
    void droppingIdsIsTheLargestRemainingPerRowSaving() {
        // The measured basis for SH121, asserted rather than assumed (P3): the 36-char
        // UUID is the single most expensive field a row can carry, so the id-free row
        // must be materially — not marginally — cheaper. 25% is well inside the
        // measured margin and leaves room for the row's other fields to change.
        ShAgentContext.Node lean = firstPost(context());
        ShAgentContext.Node fat = firstPost(withIds());

        int leanTokens = ShTokenBudget.estimate(ShAgentContextTerse.node(lean));
        int fatTokens = ShTokenBudget.estimate(ShAgentContextTerse.node(fat));

        assertThat(leanTokens).isLessThan(fatTokens * 3 / 4);
    }

    @Test
    void everyIdFreeRowIsStillAddressable() {
        // The honesty condition for dropping the id (SH79's `addressOf` round-trip is
        // the other half): a row you cannot name is a row you cannot act on.
        assertThat(context().sitemap()).isNotEmpty().allSatisfy(node -> {
            assertThat(node.path()).isNotBlank();
            if ("post".equals(node.kind())) {
                assertThat(node.url()).isNotBlank();
            }
        });
    }

    @Test
    void idFreePacksSayHowToGetIdsBack() {
        assertThat(context().conventions())
                .anySatisfy(convention -> assertThat(convention).contains("?ids=true"));
        assertThat(withIds().conventions())
                .noneSatisfy(convention -> assertThat(convention).contains("?ids=true"));
    }

    @Test
    void theIdsFlagChangesTheDigestBecauseItChangesTheAnswer() {
        // Two representations must not share an entity tag — SH116 hands this value
        // straight to If-None-Match.
        assertThat(withIds().digest()).isNotEqualTo(context().digest());
    }

    @Test
    void resumeWorksWithoutIdsOnTheRows() {
        int budget = budgetThatCutsTheSitemap();
        ShAgentContext first = shAgentContextService.context(siteName, null, budget, null, null);

        assertThat(first.budget().truncated()).isTrue();
        // The cursor is an id even though no row carries one: it is a single value per
        // response, so it costs nothing measurable and stays exact.
        assertThat(first.budget().truncatedAfter()).isNotBlank();
        assertThat(first.sitemap()).allSatisfy(node -> assertThat(node.id()).isNull());
    }

    private static ShAgentContext.Node firstPost(ShAgentContext pack) {
        return pack.sitemap().stream()
                .filter(candidate -> "post".equals(candidate.kind()))
                .findFirst().orElseThrow();
    }
}
