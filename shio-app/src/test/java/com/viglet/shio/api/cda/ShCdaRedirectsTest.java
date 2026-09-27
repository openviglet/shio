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

import com.viglet.shio.api.cda.dto.ShCdaRedirect;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1133 — a front end that owns its URLs can read which addresses moved.
 *
 * <p>A {@code Redirect} post is honoured by the instance's renderer, and a headless site never
 * reaches it: the redirect a curator wrote changed nothing a visitor of a Next.js site could see.
 * The table is the read that lets the front end do the routing itself.</p>
 *
 * <p>The fixture carries each way a row could be wrong: a redirect whose draft points somewhere new,
 * one that exists only as a draft, and one with no destination, which the renderer treats as no
 * redirect at all.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShCdaRedirectsTest {

    private static final Instant WHEN = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ShCdaService shCdaService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteId;

    @BeforeAll
    void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("sh1133-" + UUID.randomUUID().toString().substring(0, 8));
            site.setDate(WHEN);
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = new ShFolder();
            root.setName(site.getName());
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(WHEN);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType redirect = redirectType();
            String moved = UUID.randomUUID().toString();
            // Deliberately out of order: the table is ordered by `from`, not by insertion.
            row(root, redirect, moved, "/pricing", ShPostState.PUBLISHED, "/plans", "true");
            row(root, redirect, moved, "/pricing", ShPostState.DRAFT, "/plans-2026", "true");
            row(root, redirect, UUID.randomUUID().toString(), "/about", ShPostState.PUBLISHED,
                    "https://example.org/about", "0");
            row(root, redirect, UUID.randomUUID().toString(), "/drafted", ShPostState.DRAFT,
                    "/elsewhere", null);
            row(root, redirect, UUID.randomUUID().toString(), "/nowhere", ShPostState.PUBLISHED,
                    "", null);
        });
    }

    @AfterEach
    void clearToken() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aProductionTokenReadsThePublishedTableInPathOrder() {
        token(ShApiTokenEnv.PROD, Set.of());

        assertThat(shCdaService.getRedirects(siteId).orElseThrow())
                .as("SH1133: published rows only, ordered by from, and a row with no destination is "
                        + "not a redirect — the renderer ignores it, so the table must too")
                .containsExactly(
                        new ShCdaRedirect("/about", "https://example.org/about", false),
                        new ShCdaRedirect("/pricing", "/plans", true));
    }

    @Test
    void aPreviewTokenReadsDraftsAndPrefersThem() {
        token(ShApiTokenEnv.PREVIEW, Set.of());

        assertThat(shCdaService.getRedirects(siteId).orElseThrow())
                .as("SH1133: a preview token sees the draft's destination and the draft-only row")
                .containsExactly(
                        new ShCdaRedirect("/about", "https://example.org/about", false),
                        new ShCdaRedirect("/drafted", "/elsewhere", false),
                        new ShCdaRedirect("/pricing", "/plans-2026", true));
    }

    @Test
    void aTokenScopedToAnotherSiteGetsTheSame404AsAMissingSite() {
        token(ShApiTokenEnv.PROD, Set.of("some-other-site"));

        assertThat(shCdaService.getRedirects(siteId))
                .as("SH1133: a scoped key cannot tell a site it may not read from one that is missing")
                .isEmpty();
        assertThat(shCdaService.getRedirects("no-such-site")).isEmpty();
    }

    @Test
    void theWireNamesAreTheSdksShioRedirect() {
        token(ShApiTokenEnv.PROD, Set.of());

        JsonNode first = objectMapper.readTree(objectMapper.writeValueAsString(
                shCdaService.getRedirects(siteId).orElseThrow())).get(0);

        assertThat(first.get("from").stringValue()).isEqualTo("/about");
        assertThat(first.get("to").stringValue()).isEqualTo("https://example.org/about");
        assertThat(first.get("permanent").isBoolean())
                .as("SH1133: permanent is always sent — a false that vanished would read as unknown")
                .isTrue();
    }

    private void token(ShApiTokenEnv env, Set<String> sites) {
        var auth = new UsernamePasswordAuthenticationToken("delivery", null, List.of());
        auth.setDetails(new ShApiTokenContext("tok", ShApiTokenScope.READ, sites, env));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void row(ShFolder root, ShPostType type, String id, String furl, ShPostState state,
                     String to, String permanent) {
        ShPostUnified row = new ShPostUnified();
        row.setId(id);
        row.setState(state);
        row.setShSite(root.getShSite());
        row.setShFolder(root);
        row.setShPostType(type);
        row.setFurl(furl);
        row.setTitle(furl);
        row.setLocale("en");
        row.setTranslationGroupId(id);
        row.setData(objectMapper.writeValueAsString(permanent == null
                ? java.util.Map.of("TITLE", furl, "TO", to)
                : java.util.Map.of("TITLE", furl, "TO", to, "PERMANENT", permanent)));
        row.setOwner("admin");
        row.setDate(WHEN);
        entityManager.persist(row);
    }

    private ShPostType redirectType() {
        ShPostType existing = entityManager
                .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                .setParameter("name", ShSystemPostType.REDIRECT).getResultStream().findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }
        ShPostType type = new ShPostType();
        type.setName(ShSystemPostType.REDIRECT);
        type.setTitle(ShSystemPostType.REDIRECT);
        type.setSystem((byte) 1);
        type.setDate(WHEN);
        type.setOwner("admin");
        entityManager.persist(type);
        return type;
    }
}
