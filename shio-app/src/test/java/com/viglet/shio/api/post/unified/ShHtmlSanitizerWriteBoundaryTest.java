/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.UpdateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH956 — the sanitizer runs at the boundary, so every writer is covered by it.
 *
 * <p>{@code ShHtmlSanitizerTest} asserts the rule. This asserts <b>where</b> it runs, which is the
 * half that decides whether the defence is complete: the console, REST, the agent batch and the
 * Universal Editor's draft merge all write through {@code ShPostUnifiedWriteService}, so a payload
 * that cannot survive {@code create} or {@code update} cannot survive any of them. A sanitizer in
 * the rich-text widget would have defended the one writer easiest to go around — and the analysis
 * found the editor's draft merge about to grow a second one of its own.</p>
 *
 * <p>The last test is the one that would have been an outage. {@code PageLayout} and
 * {@code Region} declare {@code Ace Editor - HTML} for their {@code HTML} field, and those values
 * are Handlebars templates carrying {@code <script>} and a whole document <em>on purpose</em>.
 * Sanitising them would strip every site's own layout on its next save.</p>
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShHtmlSanitizerWriteBoundaryTest {

    /** The rich-text field this fixture declares, with the widget the defect is about. */
    private static final String RICH_TEXT_FIELD = "body";

    private static final String HOSTILE =
            "<p>Real content</p><img src=x onerror=\"alert(document.domain)\">"
                    + "<script>alert(1)</script>";

    @Autowired
    private ShPostUnifiedWriteService writeService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String folderId;
    /** A content type with a rich-text field, and one with a code-editor field. */
    private String richTextTypeId;
    private String templateTypeId;

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("sanitizer-site-" + UUID.randomUUID());
            site.setDescription("SH956");
            site.setUrl("http://example.test");
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
            folderId = root.getId();

            // Built here rather than taken from the seed: the test datasource carries whatever
            // other classes created, and a fixture that depends on a type somebody else seeded is
            // one that passes or fails on run order (SH724's shape).
            richTextTypeId = postType("SanitizerRichText", ShSystemWidget.HTML_EDITOR);
            templateTypeId = postType("SanitizerTemplate", ShSystemWidget.ACE_HTML);
        });
    }

    /** A one-field type whose field uses the widget named. */
    private String postType(String name, String widgetName) {
        ShPostType type = new ShPostType();
        type.setName(name + "-" + UUID.randomUUID());
        type.setTitle(name);
        type.setSystem((byte) 0);
        type.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        type.setOwner("admin");
        entityManager.persist(type);

        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setId(UUID.randomUUID().toString());
        attr.setName(RICH_TEXT_FIELD);
        attr.setLabel(RICH_TEXT_FIELD);
        attr.setOrdinal(1);
        attr.setRequired((byte) 0);
        attr.setIsTitle((byte) 0);
        // Looked up, never created and never detached: removing an attribute cascades a REMOVE
        // into the shared widget catalogue for the whole JVM (SH289), and this only ever reads.
        attr.setShWidget(widget(widgetName));
        attr.setShPostType(type);
        entityManager.persist(attr);
        return type.getId();
    }

    private ShWidget widget(String name) {
        return entityManager
                .createQuery("select w from ShWidget w where w.name = :name", ShWidget.class)
                .setParameter("name", name)
                .getResultStream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "SH956: widget '" + name + "' is not seeded, so this fixture cannot say "
                                + "anything about which widgets are sanitized"));
    }

    private ObjectNode data(String field, String value) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("TITLE", "SH956 " + UUID.randomUUID());
        data.put(field, value);
        return data;
    }

    private String storedValue(ShPostUnified post, String field) {
        return objectMapper.readTree(post.getData()).get(field).asString();
    }

    @Test
    void sh956ACreateCannotStoreScriptInARichTextField() {
        ShPostUnified created = writeService.create(new CreateRequest(
                folderId, richTextTypeId, data(RICH_TEXT_FIELD, HOSTILE),
                ShPostState.DRAFT, "admin", null, null));

        String stored = storedValue(created, RICH_TEXT_FIELD);
        assertThat(stored)
                .as("SH956: the payload must not reach the database. A sanitizer at the render "
                        + "would leave it there for the next reader that does not have one")
                .doesNotContain("onerror", "<script", "alert(");
        assertThat(stored)
                .as("SH956: and the curator's actual content survives")
                .contains("Real content");
    }

    @Test
    void sh956AnUpdateCannotStoreScriptEither() {
        // The case a create-only guard misses entirely: an edit is how a payload arrives at a post
        // that was clean when it was made.
        ShPostUnified created = writeService.create(new CreateRequest(
                folderId, richTextTypeId, data(RICH_TEXT_FIELD, "<p>Clean</p>"),
                ShPostState.DRAFT, "admin", null, null));

        ShPostUnified updated = writeService.update(created.getId(),
                new UpdateRequest(data(RICH_TEXT_FIELD, HOSTILE), ShPostState.DRAFT, "admin"));

        assertThat(storedValue(updated, RICH_TEXT_FIELD))
                .doesNotContain("onerror", "<script", "alert(")
                .contains("Real content");
    }

    /**
     * The exclusion that would have been an outage, asserted rather than trusted to a comment.
     *
     * <p>A {@code PageLayout}'s {@code HTML} is a Handlebars template. It carries {@code <script>}
     * because a site needs scripts, and {@code {{#region}}} because that is what a layout is. An
     * allow-list over it strips the site.</p>
     */
    @Test
    void sh956APageLayoutTemplateIsNotSanitized() {
        String template = "<html><head><script src=\"/site.js\"></script></head>"
                + "<body>{{#region \"main\"}}{{/region}}</body></html>";

        ShPostUnified created = writeService.create(new CreateRequest(
                folderId, templateTypeId, data(RICH_TEXT_FIELD, template),
                ShPostState.DRAFT, "admin", null, null));

        assertThat(storedValue(created, RICH_TEXT_FIELD))
                .as("SH956: `Ace Editor - HTML` is a code editor — writing markup IS the point "
                        + "there. Sanitising it would strip every site's own layout on its next "
                        + "save, which is a larger outage than the defect this task closes.")
                .isEqualTo(template);
    }
}
