/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.cda.dto.ShCdaPost;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH53 content i18n: a created post defaults to its own locale group; a
 * translation is a linked sibling; and the CDA resolves a URL per locale,
 * reporting the available translations.
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
@com.viglet.shio.ShFixtureShapeGuard.BuildsDuplicateUrls(
        "SH348 locales — one page in two languages is two posts on one furl by design, and the locale is what tells them apart")
class ShContentLocaleTest {

    @Autowired
    private ShPostUnifiedWriteService writeService;
    @Autowired
    private ShCdaService shCdaService;
    @Autowired
    private com.viglet.shio.api.agent.ShAgentReadService shAgentReadService;
    @Autowired
    private com.viglet.shio.api.agent.ShAgentBatchService shAgentBatchService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String siteId;
    private String siteName;
    private String folderId;
    private String postTypeId;
    private String postTypeName;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(s -> {
            ShSite site = new ShSite();
            siteName = "loc-site-" + UUID.randomUUID();
            site.setName(siteName);
            site.setUrl("http://loc.example.test");
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
            folderId = root.getId();

            ShPostType pt = new ShPostType();
            postTypeName = "PT-LOC-" + UUID.randomUUID();
            pt.setName(postTypeName);
            pt.setTitle("Locale Test Post Type");
            pt.setSystem((byte) 0);
            pt.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            pt.setOwner("admin");
            entityManager.persist(pt);
            postTypeId = pt.getId();
        });
    }

    private ObjectNode data(String title) {
        ObjectNode d = objectMapper.createObjectNode();
        d.put("title", title);
        return d;
    }

    @Test
    void createDefaultsToEnglishAndOwnGroup() {
        ShPostUnified post = writeService.create(new CreateRequest(
                folderId, postTypeId, data("Hello"), ShPostState.DRAFT, "admin", null, null));
        assertThat(post.getLocale()).isEqualTo("en");
        assertThat(post.getTranslationGroupId()).isEqualTo(post.getId());
    }

    @Test
    void translateLinksASiblingAndListsBoth() {
        ShPostUnified en = writeService.create(new CreateRequest(
                folderId, postTypeId, data("About"), ShPostState.DRAFT, "admin", null, null));
        ShPostUnified pt = writeService.translate(en.getId(), "pt-br", null, "admin");

        assertThat(pt.getId()).isNotEqualTo(en.getId());
        assertThat(pt.getLocale()).isEqualTo("pt-br");
        assertThat(pt.getTranslationGroupId()).isEqualTo(en.getTranslationGroupId());

        List<ShPostUnified> siblings = writeService.listTranslations(en.getId());
        assertThat(siblings).extracting(ShPostUnified::getLocale)
                .containsExactlyInAnyOrder("en", "pt-br");
    }

    @Test
    void cdaResolvesByLocaleAndReportsAvailable() {
        // Two PUBLISHED translations sharing one furl + group, differing by locale.
        //
        // SH323 — this shape is *deliverable* and is still a defect the instance reports: an SH79
        // address carries no locale, so `ShAddressResolver` refuses the pair (SH321) and
        // `duplicate-url` is an error over it, with a clause naming the locales so the curator who
        // did this deliberately is told why it does not help. What locale-aware delivery is for is
        // a translation with its own URL. The assertions below are the *delivery* contract, which
        // is unchanged.
        String group = UUID.randomUUID().toString();
        persistPublished("/about-loc", "en", "About", group);
        persistPublished("/about-loc", "pt-br", "Sobre", group);

        ShCdaPost en = shCdaService.getPostByUrl(siteId, "/about-loc", "en").orElseThrow();
        ShCdaPost pt = shCdaService.getPostByUrl(siteId, "/about-loc", "pt-br").orElseThrow();

        assertThat(en.locale()).isEqualTo("en");
        assertThat(en.title()).isEqualTo("About");
        assertThat(pt.locale()).isEqualTo("pt-br");
        assertThat(pt.title()).isEqualTo("Sobre");
        assertThat(en.availableLocales()).containsExactlyInAnyOrder("en", "pt-br");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SH323: two posts on one URL in one locale resolve, not 500")
    void theLocaleScopedReadIsDeterministicOnAContestedUrl() {
        // The actual SH323 defect: `findByShSite_IdAndFurlAndStateAndLocale` was a derived
        // Optional, so Spring Data expected at most one row and threw
        // IncorrectResultSizeDataAccessException — a 500 for a defect `duplicate-url` reports.
        // Two posts, one locale, *separate* translation groups: not translations, just a
        // contested URL, which is what SH321 fixed one parameter along.
        String contested = "/contested-loc";
        persistPublished(contested, "en", "First", UUID.randomUUID().toString());
        // SH719 made a second live post at one address impossible, so the first row's key is
        // removed before the second is written — the state a pre-SH719 instance is in, and the one
        // `ShPostUrlKeyBackfillRunner` leaves rather than refusing to start. What is asserted below
        // is delivery over rows that ALREADY exist, which SH719 cannot undo retroactively.
        unkeyContested(contested);
        persistPublished(contested, "en", "Second", UUID.randomUUID().toString());
        unkeyContested(contested);

        ShCdaPost scoped = shCdaService.getPostByUrl(siteId, contested, "en").orElseThrow();
        ShCdaPost blind = shCdaService.getPostByUrl(siteId, contested).orElseThrow();

        assertThat(scoped.id())
                .as("""
                        the same row every request — SH278's rule on a second identity: \
                        consistently wrong is a defect somebody recognises and `verify` names, \
                        while a coin-flip reads as flakiness in delivery. A *write* refuses \
                        instead (ShAddressResolver.DuplicateUrlException).""")
                .isEqualTo(blind.id());
        assertThat(List.of("First", "Second")).contains(scoped.title());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SH348: the agent read reports a non-default locale, and omits the default")
    void theAgentReadSeesTheLocale() {
        // Before SH348 the string `locale` appeared nowhere in ShAgentReadService, so `locales: true`
        // in the manifest was a capability no read reported the state of — the first of the four
        // layers that made `post.translate` unbuildable (§XVI.64).
        persistPublished("/sh348-pt", "pt-br", "Sobre", UUID.randomUUID().toString());
        persistPublished("/sh348-en", "en", "About", UUID.randomUUID().toString());

        var translated = shAgentReadService.read(
                List.of("post:" + siteName + "/sh348-pt"), List.of(), false, false);
        var ordinary = shAgentReadService.read(
                List.of("post:" + siteName + "/sh348-en"), List.of(), false, false);

        assertThat(translated.getFirst().locale())
                .as("present where it carries information")
                .isEqualTo("pt-br");
        assertThat(ordinary.getFirst().locale())
                .as("""
                        absent for the instance default, which is P3 rather than laziness: a                         monolingual instance pays nothing per row (measured — the response budget did                         not move), and absence is unambiguous because the context pack publishes the                         default on the call SH77 tells a session to make first.""")
                .isNull();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SH348: a document declaring a locale creates the post in it")
    void anUpsertHonoursADeclaredLocale() {
        // Step 2's server half. Without it the file projection could say `locale: pt-br` and a push
        // would still create an `en` post — a tree that claims to be a complete description of a
        // site and rebuilds it in the wrong language (Block I's promise, quietly broken).
        var op = new com.viglet.shio.api.agent.dto.ShAgentBatch.Op(
                com.viglet.shio.api.agent.ShAgentBatchService.POST_UPSERT,
                "post:" + siteName + "/sh348-created", postTypeName, "/", null,
                objectMapper.createObjectNode(), null, "pt-br");
        shAgentBatchService.run(
                new com.viglet.shio.api.agent.dto.ShAgentBatch(false, List.of(op)),
                false, "ut-sh348");

        var created = shAgentReadService.read(
                List.of("post:" + siteName + "/sh348-created"), List.of(), false, false);
        assertThat(created.getFirst().locale())
                .as("created in the declared locale, and the read reports it because it is not the "
                        + "instance default — the two halves of step 1 and step 2 meeting")
                .isEqualTo("pt-br");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SH348: a declared translationOf joins the group, and the read reports the anchor")
    void aDeclaredTranslationJoinsTheGroup() {
        var original = new com.viglet.shio.api.agent.dto.ShAgentBatch.Op(
                com.viglet.shio.api.agent.ShAgentBatchService.POST_UPSERT,
                "post:" + siteName + "/sh348-about", postTypeName, "/", null,
                objectMapper.createObjectNode(), null, null, null);
        var translation = new com.viglet.shio.api.agent.dto.ShAgentBatch.Op(
                com.viglet.shio.api.agent.ShAgentBatchService.POST_UPSERT,
                "post:" + siteName + "/sh348-sobre", postTypeName, "/", null,
                objectMapper.createObjectNode(), null, "pt-br", "/sh348-about");
        shAgentBatchService.run(
                new com.viglet.shio.api.agent.dto.ShAgentBatch(false, List.of(original, translation)),
                false, "ut-sh348");

        var read = shAgentReadService.read(
                List.of("post:" + siteName + "/sh348-sobre", "post:" + siteName + "/sh348-about"),
                List.of(), false, false);

        assertThat(read.get(0).translationOf())
                .as("""
                        the translation points at the group's anchor by address — never a group id,                         which is what makes the tree hold it (SH150's shape).""")
                .isEqualTo("post:" + siteName + "/sh348-about");
        assertThat(read.get(1).translationOf())
                .as("""
                        and the anchor points at nothing, which is how a reader tells the original                         from its translations without a second call. A self-link is the one shape the                         convention cannot express, so it is refused on the way in.""")
                .isNull();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SH348: translationOf naming nothing is a teaching 400")
    void anUnresolvableTranslationTargetIsRefused() {
        var op = new com.viglet.shio.api.agent.dto.ShAgentBatch.Op(
                com.viglet.shio.api.agent.ShAgentBatchService.POST_UPSERT,
                "post:" + siteName + "/sh348-orphan", postTypeName, "/", null,
                objectMapper.createObjectNode(), null, "pt-br", "/no-such-original");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> shAgentBatchService.run(
                new com.viglet.shio.api.agent.dto.ShAgentBatch(false, List.of(op)), false, "ut"))
                .as("creating an unlinked post and reporting success is the silent drop SH331 removed")
                .isInstanceOf(com.viglet.shio.api.agent.ShAgentBatchService.BatchOpException.class)
                .satisfies(failure -> assertThat(
                        ((com.viglet.shio.api.agent.ShAgentBatchService.BatchOpException) failure)
                                .problem().toProblemDetail().getProperties().get("fix").toString())
                        .contains("push the post it translates first"));
    }

    /** SH719 — the pre-guard state, in its own transaction so the native update sees the insert. */
    private void unkeyContested(String furl) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                com.viglet.shio.ShLegacyAddressKeys.unkey(entityManager, furl));
    }

    private void persistPublished(String furl, String locale, String title, String group) {
        persistIn(ShPostState.PUBLISHED, furl, locale, title, group);
    }

    /** SH1128 — the same row in a state the caller names, so a draft-only sibling can exist. */
    private void persistIn(ShPostState state, String furl, String locale, String title, String group) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(s -> {
            ShSite site = entityManager.find(ShSite.class, siteId);
            ShFolder folder = entityManager.find(ShFolder.class, folderId);
            ShPostType pt = entityManager.find(ShPostType.class, postTypeId);
            ShPostUnified post = new ShPostUnified();
            post.setId(UUID.randomUUID().toString());
            post.setState(state);
            post.setShSite(site);
            post.setShFolder(folder);
            post.setShPostType(pt);
            post.setTitle(title);
            post.setFurl(furl);
            post.setLocale(locale);
            post.setTranslationGroupId(group);
            post.setPublished(state == ShPostState.PUBLISHED);
            post.setOwner("admin");
            post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            post.setData("{\"title\":\"" + title + "\"}");
            entityManager.persist(post);
        });
    }
    /**
     * SH1128 — a switcher entry for a page nobody published.
     *
     * <h2>The defect</h2>
     *
     * <p>A post's {@code availableLocales} was read from its whole translation group with nothing in
     * that read asking for a state. {@code translate} creates the sibling as a DRAFT, so the moment a
     * curator started a Portuguese translation, <b>every production token's read of the English page
     * said it exists in {@code pt-br}</b> — and a switcher built from that field linked to a page the
     * same token gets a 404 for, for as long as the translation was being written.</p>
     *
     * <h2>Why it survived SH1123</h2>
     *
     * <p>SH1123 fixed the identical defect one level up: a site's derived languages come from the
     * states the token can see. The post-level field kept the old rule, and SH1129 carried that rule
     * into GraphQL <em>deliberately</em>, so the two surfaces agreed while this was open rather than
     * disagreeing in a way nobody had filed. This is the pair being brought onto SH1123's rule, and
     * onto one query — the single-post path reads through the batch now, so there is one reading of
     * the rule instead of two that agree until somebody changes one.</p>
     *
     * <p>Both surfaces, because the field is the same field: REST's single read and GraphQL's page.</p>
     */
    @Test
    @org.junit.jupiter.api.DisplayName(
            "SH1128: a draft-only translation is absent for production and present for preview")
    void aDraftOnlyTranslationIsNotOfferedToProduction() {
        String group = UUID.randomUUID().toString();
        persistPublished("/sh1128-en", "en", "Pricing", group);
        persistIn(ShPostState.DRAFT, "/sh1128-pt", "pt-br", "Preços", group);

        try {
            token(ShApiTokenEnv.PROD);
            assertThat(shCdaService.getPostByUrl(siteId, "/sh1128-en", "en").orElseThrow()
                    .availableLocales())
                    .as("SH1128: a production token reads PUBLISHED alone, so it must not be told the "
                            + "page exists in a language it cannot fetch a single row of — that entry "
                            + "is a link to a 404")
                    .containsExactly("en");

            token(ShApiTokenEnv.PREVIEW);
            assertThat(shCdaService.getPostByUrl(siteId, "/sh1128-en", "en").orElseThrow()
                    .availableLocales())
                    .as("SH1128: and a preview token sees it, because it can fetch it — the rule is "
                            + "the reader's states, not a blanket exclusion of drafts")
                    .containsExactly("en", "pt-br");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * SH1128 — and on GraphQL, which is the surface SH1129 added the batch for.
     *
     * <p>Asserted separately rather than trusted from the single read: the two paths were two
     * different queries before this, which is exactly how they could have disagreed. They share one
     * now, and this is the check that says so from the outside.</p>
     */
    @Test
    @org.junit.jupiter.api.DisplayName("SH1128: the GraphQL page reports the same locales as the read")
    void theListingAgreesWithTheSingleRead() {
        String group = UUID.randomUUID().toString();
        persistPublished("/sh1128-list-en", "en", "Features", group);
        persistIn(ShPostState.DRAFT, "/sh1128-list-pt", "pt-br", "Recursos", group);

        try {
            token(ShApiTokenEnv.PROD);
            assertThat(localesOfListing("/sh1128-list-en"))
                    .as("SH1128: GraphQL's `posts` carried both states on purpose while this was open, "
                            + "so the two surfaces agreed about the wrong answer")
                    .containsExactly("en");

            token(ShApiTokenEnv.PREVIEW);
            assertThat(localesOfListing("/sh1128-list-en")).containsExactly("en", "pt-br");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** The `availableLocales` GraphQL's page read reports for one row of it. */
    private List<String> localesOfListing(String furl) {
        return shCdaService.queryPosts(siteId, folderId, null, 0, 50, null, null).stream()
                .filter(post -> furl.equals(post.furl()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("SH1128: " + furl + " is not in the listing, so "
                        + "this assertion has nothing to be about"))
                .availableLocales();
    }

    /** A delivery credential of the given kind, which is what decides the states a read considers. */
    private void token(ShApiTokenEnv env) {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "delivery", null, List.of());
        auth.setDetails(new ShApiTokenContext("tok", ShApiTokenScope.READ, Set.of(), env));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

}
