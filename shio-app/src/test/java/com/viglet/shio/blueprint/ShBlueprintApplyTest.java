/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.agent.ShAddressResolver;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.blueprint.dto.ShBlueprintApplied;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH93/SH94 — applying a blueprint end to end.
 *
 * <h2>The three properties worth an integration test</h2>
 *
 * <ol>
 *   <li><b>The plan is complete and writes nothing.</b> A blueprint brings its own
 *       post-types, so the content planner has to resolve types that do not exist yet;
 *       the plan provisions them inside its transaction and rolls back. If the rollback
 *       ever stopped working, a dry run would leave a model behind — so the test asserts
 *       the post-type is <em>absent</em> afterwards, which is the only way to see it.</li>
 *   <li><b>Applying converges.</b> The second apply changes nothing. This is inherited
 *       from SH82 rather than implemented here, and that is exactly why it is worth
 *       asserting: it is the property that makes a blueprint safe to re-run after a
 *       context compaction.</li>
 *   <li><b>The package keeps its promise.</b> Every URL in {@code verify.posts} resolves
 *       afterwards, and the SH99 lint over the site reports no error. A blueprint that
 *       cannot prove it worked is a liability (SH94), and this is the proof.</li>
 * </ol>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class ShBlueprintApplyTest {

    /** Widgets the first-party packages reference. Seeded here if the catalogue lacks them. */
    private static final List<String> WIDGETS = List.of("Text", "Text Area", "HTML Editor",
            "Date", "Multi Select", "Combo Box", "File", "Relator");

    @Autowired
    private ShBlueprintService shBlueprintService;
    @Autowired
    private ShAddressResolver shAddressResolver;
    @Autowired
    private ShPostTypeRepository shPostTypeRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;

    private void withTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private void createSite() {
        withTransaction(() -> {
            ShSite site = new ShSite();
            siteName = "bp-site-" + UUID.randomUUID();
            site.setName(siteName);
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            root.setOwner("admin");
            entityManager.persist(root);

            for (String name : WIDGETS) {
                boolean present = entityManager
                        .createQuery("select w from ShWidget w where w.name = :name", ShWidget.class)
                        .setParameter("name", name)
                        .getResultStream().findAny().isPresent();
                if (!present) {
                    ShWidget widget = new ShWidget();
                    widget.setName(name);
                    widget.setDescription(name);
                    entityManager.persist(widget);
                }
            }
        });
    }

    private JsonNode params(String json) {
        return objectMapper.readTree(json);
    }

    @Test
    @Order(1)
    @DisplayName("the plan names the post-types it would create — and creates none of them")
    void planIsCompleteAndWritesNothing() {
        createSite();

        ShBlueprintApplied plan = shBlueprintService.plan("blog", siteName,
                params("{\"blogTitle\":\"The Acme Blog\"}"), "admin");

        assertThat(plan.dryRun()).isTrue();
        assertThat(plan.postTypes())
                .extracting(ShBlueprintApplied.PostType::name,
                        ShBlueprintApplied.PostType::action)
                .containsExactly(org.assertj.core.api.Assertions.tuple("Article", "create"));
        // The whole point of provisioning inside the plan's transaction: without it every
        // post row below would have failed with "no post-type named Article".
        assertThat(plan.content().changed()).isEqualTo(7);
        assertThat(plan.content().results())
                .extracting(change -> change.address())
                .contains("post:" + siteName + "/blog/hello-world");
        assertThat(plan.verify()).as("a plan has nothing to prove").isNull();
        // Parameters are echoed with the defaults applied, so the record of what ran is
        // complete even for values the caller never sent.
        assertThat(plan.params())
                .containsEntry("blogTitle", "The Acme Blog")
                .containsEntry("authorName", "Editorial");

        withTransaction(() -> assertThat(shPostTypeRepository.findByName("Article"))
                .as("a plan that left a post-type behind is not a plan")
                .isNull());
        withTransaction(() -> assertThat(shAddressResolver
                .find(ShAddress.post(siteName, "/blog/hello-world")))
                .as("and it wrote no content either")
                .isEmpty());
    }

    @Test
    @Order(2)
    @DisplayName("applying creates the model and the content, and proves its own promise")
    void applyKeepsThePackagesPromise() {
        ShBlueprintApplied applied = shBlueprintService.apply("blog", siteName,
                params("{\"blogTitle\":\"The Acme Blog\",\"authorName\":\"Acme Editorial\"}"),
                "admin");

        assertThat(applied.dryRun()).isFalse();
        assertThat(applied.postTypes()).singleElement()
                .satisfies(postType -> {
                    assertThat(postType.name()).isEqualTo("Article");
                    assertThat(postType.action()).isEqualTo("create");
                    assertThat(postType.missing()).isNull();
                });
        assertThat(applied.content().changed()).isEqualTo(7);
        assertThat(applied.verify()).isNotNull();
        assertThat(applied.verify().missing()).isEmpty();
        assertThat(applied.verify().errors()).isZero();
        assertThat(applied.verify().ok()).isTrue();
        // No front-end files in this package: reported as absent rather than as an empty
        // list, so an MCP-only caller is not told it has files it does not have.
        assertThat(applied.files()).isNull();

        withTransaction(() -> {
            assertThat(shPostTypeRepository.findByName("Article")).isNotNull();
            assertThat(shAddressResolver.find(ShAddress.post(siteName, "/blog/hello-world")))
                    .isPresent();
        });
    }

    @Test
    @Order(3)
    @DisplayName("substitution reached the content, and the parameter's value is what is stored")
    void substitutionReachedTheContent() {
        withTransaction(() -> assertThat(shAddressResolver
                .find(ShAddress.post(siteName, "/blog/hello-world"))
                .orElseThrow().title())
                .isEqualTo("Welcome to The Acme Blog"));
    }

    @Test
    @Order(4)
    @DisplayName("a second apply changes nothing — a blueprint survives a compaction")
    void applyingTwiceConverges() {
        ShBlueprintApplied again = shBlueprintService.apply("blog", siteName,
                params("{\"blogTitle\":\"The Acme Blog\",\"authorName\":\"Acme Editorial\"}"),
                "admin");

        assertThat(again.postTypes()).singleElement()
                .satisfies(postType -> assertThat(postType.action()).isEqualTo("exists"));
        assertThat(again.content().changed())
                .as("convergence is the property that makes re-running safe")
                .isZero();
        assertThat(again.verify().ok()).isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("an existing post-type missing a field the package writes is named, not hidden")
    void existingPostTypeMismatchIsReported() {
        // The collision that actually happens: a console-created site already carries the
        // SH71 bootstrap `Page` (title + content), and the section vocabulary's `Page` has
        // `sections`. The older type wins — and the caller is told which fields it lacks.
        createSite();
        withTransaction(() -> {
            // Reused when it is already there: the SH71 bootstrap template provisions a
            // `Page` too, so a second one would make findByName ambiguous for every test in
            // the run — and either origin produces the same collision, which is the point.
            if (shPostTypeRepository.findByName("Page") != null) {
                return;
            }
            com.viglet.shio.persistence.model.post.type.ShPostType page =
                    new com.viglet.shio.persistence.model.post.type.ShPostType();
            page.setName("Page");
            page.setTitle("Page");
            page.setSystem((byte) 0);
            page.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            page.setOwner("admin");
            entityManager.persist(page);

            com.viglet.shio.persistence.model.post.type.ShPostTypeAttr title =
                    new com.viglet.shio.persistence.model.post.type.ShPostTypeAttr();
            title.setName("title");
            title.setLabel("Title");
            title.setOrdinal(1);
            title.setIsTitle((byte) 1);
            title.setShPostType(page);
            title.setShWidget(entityManager
                    .createQuery("select w from ShWidget w where w.name = 'Text'", ShWidget.class)
                    .getResultStream().findFirst().orElseThrow());
            entityManager.persist(title);
        });

        ShBlueprintApplied plan = shBlueprintService.plan("landing", siteName,
                params("{\"brand\":\"Acme\"}"), "admin");

        assertThat(plan.postTypes())
                .filteredOn(postType -> "Page".equals(postType.name()))
                .singleElement()
                .satisfies(postType -> {
                    assertThat(postType.action()).isEqualTo("exists");
                    assertThat(postType.missing()).containsExactly("description", "sections");
                });
        // SH168: and the content half of the same answer — the plan says which writes the
        // missing fields will break, rather than reporting `update` and failing on apply.
        // The plan must still be a *whole* report: dying on the first bad field would have
        // hidden the post-type list above, which is the more actionable half.
        assertThat(plan.content().results())
                .anySatisfy(change -> assertThat(change.detail())
                        .as("a plan warns where a real run refuses")
                        .contains("undeclared field(s)", "sections"));
    }

    @Test
    @DisplayName("an unknown site and an unknown blueprint both teach instead of failing blankly")
    void refusalsTeach() {
        createSite();

        assertThatThrownBy(() -> shBlueprintService.apply("blog", null, null, "admin"))
                .isInstanceOf(ShBlueprintService.MissingSiteException.class)
                .satisfies(failure -> assertThat(((ShTeachingException) failure).problem()
                        .toProblemDetail().getProperties()).containsKey("fix"));

        assertThatThrownBy(() -> shBlueprintService.apply("no-such-package", siteName, null, "admin"))
                .isInstanceOf(ShBlueprintRegistry.UnknownBlueprintException.class);
    }
}
