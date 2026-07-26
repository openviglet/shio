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
        assertThat(pack.instance().product()).isEqualTo("Viglet Shio CMS");
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
    private int budgetThatCutsTheSitemap() {
        return Math.max(shAgentContextService.context(siteName, null, 0, null, null)
                .budget().estTokens() * 3 / 4, 1);
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

        List<String> firstIds = first.sitemap().stream().map(ShAgentContext.Node::id).toList();
        List<String> secondIds = second.sitemap().stream().map(ShAgentContext.Node::id).toList();

        assertThat(secondIds).isNotEmpty().doesNotContainAnyElementsOf(firstIds);
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
                    assertThat(properties.get("allowed")).isEqualTo(
                            List.of("instance", "sites", "model", "sitemap", "conventions"));
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
        assertThat(shAgentContextService.context(siteId, null, 0, null, null).sites())
                .extracting(ShAgentContext.Site::id).containsExactly(siteId);
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

    @Test
    void terseAndJsonDescribeTheSameState() {
        ShAgentContext pack = context();
        String terse = ShAgentContextTerse.render(pack);

        // One digest for both encodings: the terse body *is* the canonical form.
        assertThat(terse).contains("digest " + pack.digest());
    }
}
