/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH22 — proves the core DB multi-tenant isolation actually isolates: rows
 * written under tenant "alpha" are invisible to tenant "beta", on both the
 * active unified content store ({@code sh_post_unified}, composite-key
 * {@code @TenantId}) and the {@code ShObject} JOINED hierarchy (one
 * {@code @TenantId} on the root, filtering polymorphic <em>and</em> concrete
 * subclass queries).
 *
 * <p>Boots the <strong>full Spring context</strong> on a fresh in-memory H2 (no
 * Docker — runs on every {@code mvn verify}) with the production-like datasource
 * params, Liquibase enabled and {@code shio.multi-tenant=true}. The cross-DB
 * twins ({@link ShMariaDbSchemaIT}/{@link ShPostgresSchemaIT}) already prove the
 * {@code TENANT_ID} column applies on MariaDB/PostgreSQL; this proves the
 * <em>filtering semantics</em>.
 *
 * <p>Hibernate's {@link org.hibernate.context.spi.CurrentTenantIdentifierResolver}
 * resolves the tenant once <em>per session open</em>, so each tenant phase runs
 * in its own transaction (via {@link TransactionTemplate}) — switching the
 * {@link ShTenantContext} ThreadLocal only takes effect on the next session.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
class ShTenantIsolationIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:shio-sh22-it;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.default-schema", () -> "PUBLIC");
        // The whole point: exercise the multi-tenant path, not the single-tenant default.
        registry.add("shio.multi-tenant", () -> "true");
    }

    private static final String ALPHA = "alpha";
    private static final String BETA = "beta";

    @Autowired
    private ShPostUnifiedRepository postRepository;

    @Autowired
    private ShObjectRepository objectRepository;

    @Autowired
    private PlatformTransactionManager txManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void unifiedPostsAreIsolatedPerTenant() {
        String alphaPost1 = asTenant(ALPHA, () -> savePost("Alpha One").getId());
        String alphaPost2 = asTenant(ALPHA, () -> savePost("Alpha Two").getId());
        String betaPost = asTenant(BETA, () -> savePost("Beta One").getId());

        // Beta sees only its own draft — neither of alpha's two, despite sharing the table.
        asTenant(BETA, () -> {
            assertThat(postRepository.findByState(ShPostState.DRAFT))
                    .as("beta must see only its own draft rows")
                    .extracting(ShPostUnified::getId)
                    .containsExactlyInAnyOrder(betaPost);
            assertThat(postRepository.findByIdAndState(alphaPost1, ShPostState.DRAFT))
                    .as("beta must not resolve alpha's post by id")
                    .isEmpty();
            return null;
        });

        // Alpha sees exactly its two — never beta's.
        asTenant(ALPHA, () -> {
            assertThat(postRepository.findByState(ShPostState.DRAFT))
                    .as("alpha must see exactly its own two draft rows")
                    .extracting(ShPostUnified::getId)
                    .containsExactlyInAnyOrder(alphaPost1, alphaPost2);
            assertThat(postRepository.findByIdAndState(alphaPost1, ShPostState.DRAFT)).isPresent();
            assertThat(postRepository.findByIdAndState(betaPost, ShPostState.DRAFT))
                    .as("alpha must not resolve beta's post by id")
                    .isEmpty();
            return null;
        });
    }

    @Test
    void objectTreeIsIsolatedAcrossPolymorphicAndConcreteQueries() {
        String alphaFolderId = asTenant(ALPHA, () -> saveFolder("alpha-folder").getId());
        long alphaObjectCount = asTenant(ALPHA, this::countObjects);
        long alphaFolderCount = asTenant(ALPHA, this::countFolders);

        asTenant(BETA, () -> {
            // Polymorphic query on the JOINED root is tenant-filtered...
            assertThat(countObjects())
                    .as("beta's ShObject count must exclude alpha's rows")
                    .isLessThan(alphaObjectCount);
            // ...and so is a concrete subclass query (the predicate sits on the joined root table).
            assertThat(countFolders())
                    .as("beta's ShFolder count must exclude alpha's folder")
                    .isLessThan(alphaFolderCount);
            assertThat(objectRepository.findById(alphaFolderId))
                    .as("beta must not resolve alpha's folder by id")
                    .isEmpty();
            return null;
        });

        // Alpha still resolves its own folder.
        asTenant(ALPHA, () -> {
            assertThat(objectRepository.findById(alphaFolderId)).isPresent();
            return null;
        });
    }

    private ShPostUnified savePost(String title) {
        ShPostUnified post = new ShPostUnified();
        post.setState(ShPostState.DRAFT);
        post.setTitle(title);
        post.setData("{}"); // data is NOT NULL
        return postRepository.save(post);
    }

    private ShFolder saveFolder(String name) {
        ShFolder folder = new ShFolder();
        folder.setName(name);
        return (ShFolder) objectRepository.save(folder);
    }

    private long countObjects() {
        return entityManager.createQuery("select count(o) from ShObject o", Long.class)
                .getSingleResult();
    }

    private long countFolders() {
        return entityManager.createQuery("select count(f) from ShFolder f", Long.class)
                .getSingleResult();
    }

    /**
     * Runs {@code work} in a brand-new transaction with {@code tenant} bound to the
     * {@link ShTenantContext} — so the opening Hibernate session resolves to that
     * tenant. The ThreadLocal is always cleared afterwards.
     */
    private <T> T asTenant(String tenant, Supplier<T> work) {
        ShTenantContext.setTenantId(tenant);
        try {
            return new TransactionTemplate(txManager).execute(status -> work.get());
        } finally {
            ShTenantContext.clear();
        }
    }
}
