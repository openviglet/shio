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

import java.time.Instant;
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

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.render.ShPageRenderService;

import tools.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * A blueprint-built site can be looked at (SH518).
 *
 * <h2>What was backwards</h2>
 *
 * <p>{@code shio apply --blueprint marketing-site --site X} wrote everything it promised — eight
 * post-types, four folders, thirteen posts, three pages composed of ten sections, every field
 * authorable — and then {@code /preview/X/} answered <b>400, binds no layout for post-type
 * 'Page'</b>. The generator that writes a {@code Theme} and a {@code PageLayout} existed and lived
 * on the <em>replication</em> path, so a site that started from a blueprint could not be rendered
 * inside Shio. Getting one to render meant harvesting those two posts from a throwaway conversion,
 * retitling them and binding the site by hand.</p>
 *
 * <p>That is backwards for the product: replication is the unusual case, and a blueprint is how
 * something new starts. SH395 made a package able to carry its design; this is the section packages
 * using it.</p>
 *
 * <h2>Its own database, and the reason is a sibling</h2>
 *
 * <p>Post-types are instance-wide. {@code ShBlueprintApplyTest} deliberately creates a mismatched
 * one to prove the mismatch is reported, so every later apply in that class meets it — this
 * assertion was written there first and failed on a {@code Page} type another test had bent on
 * purpose. SH315's rule: a test measuring what a whole apply produces may not share.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh518;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShBlueprintRendersTest {

    @Autowired
    private ShBlueprintService shBlueprintService;
    @Autowired
    private ShPageRenderService shPageRenderService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;

    @BeforeAll
    void setup() {
        siteName = "sh518-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
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
        });
    }

    @Test
    @DisplayName("SH518: applying a section package leaves a page that renders, annotated")
    void aSectionPackageIsVisible() {
        shBlueprintService.apply("marketing-site", siteName,
                new ObjectMapper().readTree("{\"brand\":\"Acme\"}"), "admin");

        String html = shPageRenderService.render(siteName, "/", ShPostState.PUBLISHED).html();

        assertThat(html)
                .as("""
                        the composed page rendered through the package's own layout: the sections \
                        it declares are on it, not a labelled fallback saying nothing is bound. \
                        This is the assertion SH518 names — apply a blueprint to a fresh site and \
                        /preview answers 200 (SH518).""")
                .contains("data-shio-section")
                .doesNotContain("data-shio-fallback");
        assertThat(html)
                .as("""
                        and it is EDITABLE: `sh-field` becomes `data-shio-prop`, which is what the \
                        Universal Editor attaches to. A page that renders and cannot be edited \
                        puts the curator back in the console form the visual editor exists to \
                        remove (SH518).""")
                .contains("data-shio-prop");
        assertThat(html)
                .as("the theme the layout names resolved, so `theme.css` is a stylesheet rather "
                        + "than an empty <style> — the trap SH280 records for a THEME that names "
                        + "a post the site has not got")
                .contains("--accent");
    }
}
