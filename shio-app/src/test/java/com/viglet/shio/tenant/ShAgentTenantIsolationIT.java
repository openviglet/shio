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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAddressResolver;
import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.ShAgentContextService;
import com.viglet.shio.api.agent.ShAgentContextTerse;
import com.viglet.shio.api.agent.ShAgentDesiredStateService;
import com.viglet.shio.api.agent.ShAgentReadService;
import com.viglet.shio.api.agent.ShAgentSiteAccess;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentBatch.Op;
import com.viglet.shio.api.agent.dto.ShAgentDesiredState;
import com.viglet.shio.api.agent.dto.ShAgentFound;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/**
 * SH84 — the tenant sweep over the finished agent surface (Block H).
 *
 * <h2>Why the surface needs its own isolation proof</h2>
 *
 * <p>{@link ShTenantIsolationIT} proves the <em>storage</em> is isolated: rows written
 * under one tenant are invisible to another. That is necessary and not sufficient. The
 * agent surface is the newest and widest read/write path in the system, and it has two
 * properties the older surfaces do not: it resolves content by <b>address</b> rather
 * than by id, and its whole purpose is to hand an agent a complete picture of an
 * instance in one call. Both are exactly the shapes in which a leak would be most
 * damaging and least visible — a context pack that included another tenant's sitemap
 * would look like a working context pack.</p>
 *
 * <p>So this asserts the standing invariant from §XII in the place it is easiest to
 * break: <b>the agent path is never a privilege escalation.</b> Every surface Block H
 * ships is exercised from tenant {@code beta} against content only {@code alpha} wrote
 * — the context pack, {@code find}, {@code read}, the address resolver, the batch
 * engine and desired-state plan/apply — and each one must behave as though that content
 * does not exist. Absent, not forbidden: the SH2 rule that a scoped credential cannot
 * use the difference between 403 and 404 to prove something exists applies to tenants
 * with more force, not less.</p>
 *
 * <p>Same mechanics as its sibling: full context, in-memory H2, Liquibase on,
 * {@code shio.multi-tenant=true}, and one transaction per tenant phase because
 * Hibernate resolves the tenant once per session open.</p>
 *
 * <p><b>Still open after this:</b> the Turing wiring half of SH84 — this covers the
 * SH22 enforcement pass, not the ecosystem integration.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
class ShAgentTenantIsolationIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:shio-sh84-it;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.default-schema", () -> "PUBLIC");
        registry.add("shio.multi-tenant", () -> "true");
    }

    private static final String ALPHA = "alpha";
    private static final String BETA = "beta";

    @Autowired
    private ShAgentSiteAccess siteAccess;
    @Autowired
    private ShAgentContextService contextService;
    @Autowired
    private ShAgentReadService readService;
    @Autowired
    private ShAddressResolver addressResolver;
    @Autowired
    private ShAgentBatchService batchService;
    @Autowired
    private ShAgentDesiredStateService desiredStateService;
    @Autowired
    private PlatformTransactionManager txManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    // --- the sweep ---

    @Test
    @DisplayName("nothing alpha wrote through the agent surface is reachable from beta")
    void theAgentSurfaceIsTenantScopedEndToEnd() {
        Fixture alpha = asTenant(ALPHA, this::seed);
        String postAddress = "post:" + alpha.site + "/blog/hello";
        String folderAddress = "folder:" + alpha.site + "/blog";

        asTenant(ALPHA, () -> {
            batchService.run(new ShAgentBatch(false, List.of(
                    new Op(ShAgentBatchService.FOLDER_UPSERT, folderAddress, null, null, null),
                    new Op(ShAgentBatchService.POST_UPSERT, postAddress, alpha.postType, "/blog",
                            objectMapper.valueToTree(Map.of("headline", "Hello"))))), false, "alpha");
            return null;
        });

        // Alpha can see its own work — otherwise every assertion below would pass
        // vacuously and the test would prove nothing at all.
        asTenant(ALPHA, () -> {
            assertThat(siteAccess.readable().stream().map(ShSite::getName)).contains(alpha.site);
            assertThat(readService.find(alpha.site, null, null, null, 50, false).results())
                    .extracting(ShAgentFound.Row::address)
                    .anyMatch(address -> address.endsWith("/blog/hello"));
            assertThat(addressResolver.resolve(postAddress).id()).isNotBlank();
            return null;
        });

        asTenant(BETA, () -> {
            // 1. The site is not in the readable set, so the context pack cannot mention it.
            assertThat(siteAccess.readable().stream().map(ShSite::getName))
                    .as("beta must not see alpha's site")
                    .doesNotContain(alpha.site);
            assertThat(ShAgentContextTerse.render(
                    contextService.context(null, null, null, null, null)))
                    .as("the context pack is the one call that describes a whole instance; "
                            + "a tenant leak here leaks everything at once")
                    .doesNotContain(alpha.site);
            // 2. find turns up nothing, including through full-text.
            assertThat(readService.find(null, null, null, "Hello", 50, false).results())
                    .as("full-text search must not cross the tenant boundary either")
                    .isEmpty();
            return null;
        });

        // 3. Naming alpha's content explicitly fails, and each attempt gets its own
        //    transaction: a failed op marks the current one rollback-only, so batching
        //    these together would prove only that the first one threw.
        //    Absent, not forbidden — the SH2 rule, which matters more across a tenant
        //    boundary than within one.
        assertThat(failureFor(BETA, () -> siteAccess.require(alpha.site)))
                .as("naming another tenant's site by name")
                .isInstanceOf(ShAgentContextService.UnknownSiteException.class);
        assertThat(failureFor(BETA, () -> readService.read(List.of(postAddress), List.of(), false)))
                .as("reading another tenant's post by address")
                .isNotNull();
        assertThat(failureFor(BETA, () -> addressResolver.resolve(postAddress)))
                .as("resolving another tenant's address")
                .isNotNull();
        assertThat(failureFor(BETA, () -> addressResolver.resolve("id:" + alpha.rootFolderId)))
                .as("the id: escape hatch is not a tenant escape hatch")
                .isNotNull();

        // 4. Writes addressed at alpha's content fail rather than reaching it. A delete
        //    that silently no-op'd would be almost as bad as one that worked: beta would
        //    be told its op succeeded.
        assertThat(failureFor(BETA, () -> batchService.run(new ShAgentBatch(false, List.of(
                new Op(ShAgentBatchService.POST_DELETE, postAddress, null, null, null))),
                false, "beta")))
                .as("deleting another tenant's post")
                .isNotNull();
        assertThat(failureFor(BETA, () -> desiredStateService.plan(new ShAgentDesiredState(
                alpha.site, List.of(new ShAgentDesiredState.Folder("/blog")), List.of(), true),
                false, "beta")))
                .as("a plan that resolved another tenant's site would list its content "
                        + "for deletion")
                .isNotNull();

        // And alpha's content survived every one of beta's attempts.
        asTenant(ALPHA, () -> {
            assertThat(readService.find(alpha.site, null, null, null, 50, false).results())
                    .extracting(ShAgentFound.Row::address)
                    .anyMatch(address -> address.endsWith("/blog/hello"));
            return null;
        });
    }

    // --- fixture ---

    private record Fixture(String site, String postType, String rootFolderId) {
    }

    private Fixture seed() {
        String siteName = "agent-tenant-" + UUID.randomUUID().toString().substring(0, 8);
        ShSite site = new ShSite();
        site.setName(siteName);
        site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        site.setOwner("alpha");
        entityManager.persist(site);

        ShFolder root = new ShFolder();
        root.setName("root");
        root.setRootFolder((byte) 1);
        root.setShSite(site);
        root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        root.setOwner("alpha");
        entityManager.persist(root);

        ShWidget widget = entityManager
                .createQuery("select w from ShWidget w where w.name = 'Text'", ShWidget.class)
                .getResultStream().findFirst().orElseGet(() -> {
                    ShWidget created = new ShWidget();
                    created.setName("Text");
                    created.setDescription("Text");
                    entityManager.persist(created);
                    return created;
                });

        ShPostType postType = new ShPostType();
        String typeName = "TenantArticle" + UUID.randomUUID().toString().substring(0, 8);
        postType.setName(typeName);
        postType.setTitle("Tenant Article");
        postType.setSystem((byte) 0);
        postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        postType.setOwner("alpha");
        entityManager.persist(postType);

        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName("headline");
        attr.setLabel("Headline");
        attr.setOrdinal(1);
        attr.setIsTitle((byte) 1);
        attr.setRequired((byte) 1);
        attr.setShWidget(widget);
        attr.setShPostType(postType);
        entityManager.persist(attr);
        entityManager.flush();

        return new Fixture(siteName, typeName, root.getId());
    }

    /**
     * Runs {@code work} in a fresh transaction with {@code tenant} bound — Hibernate
     * resolves the tenant once per session open, so switching the ThreadLocal inside an
     * open transaction would do nothing and the test would silently pass.
     */
    /**
     * Runs {@code work} under {@code tenant} in a transaction of its own and returns the
     * exception it threw, or {@code null} if it did not. Each expected failure needs its
     * own transaction because a failed {@code @Transactional} call marks the current one
     * rollback-only; asserting several in one would only ever exercise the first.
     */
    private RuntimeException failureFor(String tenant, Runnable work) {
        ShTenantContext.setTenantId(tenant);
        try {
            return new TransactionTemplate(txManager).execute(status -> {
                try {
                    work.run();
                    return null;
                } catch (RuntimeException failure) {
                    status.setRollbackOnly();
                    return failure;
                }
            });
        } finally {
            ShTenantContext.clear();
        }
    }

    private <T> T asTenant(String tenant, Supplier<T> work) {
        ShTenantContext.setTenantId(tenant);
        try {
            return new TransactionTemplate(txManager).execute(status -> work.get());
        } finally {
            ShTenantContext.clear();
        }
    }
}
