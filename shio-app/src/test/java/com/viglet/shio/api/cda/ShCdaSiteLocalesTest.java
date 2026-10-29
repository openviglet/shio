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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.cda.dto.ShCdaSite;
import com.viglet.shio.api.cda.dto.ShCdaSiteLocales;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1123 — a front end can build a language switcher from the CDA alone.
 *
 * <p>Two sites. One declared its policy, and every reader gets that decision verbatim. The other
 * declared nothing and is written in English, with a Portuguese translation that is still a draft:
 * a production token must hear "English" and nothing else, because a switcher entry for a language
 * it cannot fetch one page in is a link to a 404. A preview token reads drafts and hears both.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShCdaSiteLocalesTest {

    private static final String DECLARED =
            "{\"default\":\"pt-br\",\"enabled\":[\"pt-br\",\"pt\",\"en\"],\"fallback\":{\"pt-br\":\"pt\"},"
                    + "\"labels\":{\"pt-br\":\"Português (Brasil)\",\"pt\":\"Português (Portugal)\"}}";

    @Autowired
    private ShCdaService shCdaService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String declaredSiteId;
    private String derivedSiteId;
    private String derivedRootId;
    private final List<String> postIds = new ArrayList<>();

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShPostType postType = new ShPostType();
            postType.setName("PT-LOCALES-" + UUID.randomUUID());
            postType.setTitle("Locale Test Post Type");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            ShSite declared = persistSite(DECLARED);
            declaredSiteId = declared.getId();
            persistPost(declared, persistRoot(declared), postType, "en", ShPostState.PUBLISHED);

            ShSite derived = persistSite(null);
            derivedSiteId = derived.getId();
            ShFolder root = persistRoot(derived);
            derivedRootId = root.getId();
            persistPost(derived, root, postType, "en", ShPostState.PUBLISHED);
            persistPost(derived, root, postType, "pt-br", ShPostState.DRAFT);
        });
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (String id : postIds) {
                shPostUnifiedRepository.findById(id).forEach(shPostUnifiedRepository::delete);
            }
        });
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aDeclaredPolicyIsDeliveredAsItWasDeclared() {
        ShCdaSiteLocales locales = shCdaService.getSite(declaredSiteId).orElseThrow().locales();

        assertThat(locales).as("SH1123: the CDA site read carries no locale policy").isNotNull();
        assertThat(locales.declared()).isTrue();
        assertThat(locales.defaultLocale()).isEqualTo("pt-br");
        assertThat(locales.enabled())
                .as("SH1123: a switcher's order is a decision, and the read must keep it")
                .containsExactly("pt-br", "pt", "en");
        assertThat(locales.fallback()).isEqualTo(Map.of("pt-br", "pt"));
        assertThat(locales.labels())
                .as("SH1287: the site's labels reach the renderer but not a headless switcher")
                .isEqualTo(Map.of("pt-br", "Português (Brasil)", "pt", "Português (Portugal)"));
    }

    @Test
    void aSiteWithNoLabelsAnswersNoLabelsKey() {
        String site = objectMapper.writeValueAsString(shCdaService.getSite(derivedSiteId).orElseThrow());

        assertThat(shCdaService.getSite(derivedSiteId).orElseThrow().locales().labels()).isNull();
        assertThat(site)
                .as("SH1287: an unlabelled site's response must keep its bytes, so no `labels` key")
                .doesNotContain("labels");
    }

    @Test
    void aProductionTokenIsNotOfferedALanguageOnlyADraftIsWrittenIn() {
        token(ShApiTokenEnv.PROD);

        ShCdaSiteLocales locales = shCdaService.getSite(derivedSiteId).orElseThrow().locales();

        assertThat(locales.declared()).isFalse();
        assertThat(locales.enabled())
                .as("SH1123: pt-br exists only as a draft, so a production switcher offering it "
                        + "links to a page this token cannot fetch")
                .containsExactly("en");
        assertThat(locales.defaultLocale()).isEqualTo("en");
    }

    @Test
    void aPreviewTokenReadsDraftsAndSoHearsTheDraftLanguageToo() {
        token(ShApiTokenEnv.PREVIEW);

        assertThat(shCdaService.getSite(derivedSiteId).orElseThrow().locales().enabled())
                .as("SH1123: a preview token reads drafts, so the draft's language is one it can fetch")
                .containsExactly("en", "pt-br");
    }

    @Test
    void theListingCarriesEverySitesPolicyNotOnlyTheFirst() {
        Map<String, ShCdaSiteLocales> bySite = new java.util.HashMap<>();
        for (ShCdaSite site : shCdaService.listSites()) {
            bySite.put(site.id(), site.locales());
        }

        assertThat(bySite.get(declaredSiteId).enabled()).containsExactly("pt-br", "pt", "en");
        assertThat(bySite.get(derivedSiteId).enabled())
                .as("SH1123: the listing resolves undeclared sites in one batch, and must not "
                        + "drop them from it")
                .containsExactly("en");
    }

    @Test
    void theBreadcrumbsSiteCarriesNoPolicyAndTheWireSaysNothingRatherThanNull() {
        JsonNode site = objectMapper.readTree(
                objectMapper.writeValueAsString(shCdaService.getSite(declaredSiteId).orElseThrow()));
        String path = objectMapper.writeValueAsString(shCdaService.path(derivedRootId).orElseThrow());

        // The wire names are the SDK's (ShioSiteLocales), which is what a front end reads.
        JsonNode locales = site.get("locales");
        assertThat(locales).as("SH1123: the delivered site has no `locales`").isNotNull();
        assertThat(locales.get("defaultLocale").stringValue()).isEqualTo("pt-br");
        assertThat(locales.get("enabled").size()).isEqualTo(3);
        assertThat(locales.get("fallback").get("pt-br").stringValue()).isEqualTo("pt");
        assertThat(locales.get("declared").booleanValue()).isTrue();
        assertThat(locales.get("labels").get("pt-br").stringValue())
                .as("SH1287: the wire carries the label under the SDK's name")
                .isEqualTo("Português (Brasil)");
        assertThat(path)
                .as("SH1123: a breadcrumb is read per page, and resolving the policy there is a "
                        + "content query per navigation")
                .doesNotContain("locales")
                .doesNotContain(":null");
    }

    private void token(ShApiTokenEnv env) {
        var auth = new UsernamePasswordAuthenticationToken("delivery", null, List.of());
        auth.setDetails(new ShApiTokenContext("tok", ShApiTokenScope.READ, Set.of(), env));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private ShSite persistSite(String locales) {
        ShSite site = new ShSite();
        site.setName("locale-site-" + UUID.randomUUID());
        site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        site.setOwner("admin");
        site.setLocales(locales);
        entityManager.persist(site);
        return site;
    }

    private ShFolder persistRoot(ShSite site) {
        ShFolder root = new ShFolder();
        root.setName("root");
        root.setRootFolder((byte) 1);
        root.setShSite(site);
        root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        root.setOwner("admin");
        entityManager.persist(root);
        return root;
    }

    private void persistPost(ShSite site, ShFolder folder, ShPostType postType, String locale,
                             ShPostState state) {
        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(state);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(postType);
        post.setTitle("Localized " + locale);
        post.setFurl("/localized-" + locale);
        post.setLocale(locale);
        post.setPublished(state == ShPostState.PUBLISHED);
        post.setOwner("admin");
        post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        post.setData("{\"title\":\"Localized\"}");
        entityManager.persist(post);
        postIds.add(post.getId());
    }
}
