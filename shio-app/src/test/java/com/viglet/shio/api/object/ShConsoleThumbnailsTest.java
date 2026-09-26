/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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

import com.viglet.shio.api.object.dto.ShConsolePostRow;
import com.viglet.shio.api.query.ShContentQuery;
import com.viglet.shio.api.reference.ShObjectReferenceService;
import com.viglet.shio.api.staticfile.ShStaticFileService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1026 — a grid card shows an image: its own if it is one, else the first its page references.
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShConsoleThumbnailsTest {

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ShObjectNavigationService navigation;
    @Autowired
    private ShStaticFileService shStaticFileService;
    @Autowired
    private ShObjectReferenceService shObjectReferenceService;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String folderId;
    private final Map<String, String> ids = new HashMap<>();

    @BeforeAll
    void fixture() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("thumbs-" + UUID.randomUUID());
            site.setDate(EPOCH);
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(EPOCH);
            root.setOwner("admin");
            entityManager.persist(root);
            folderId = root.getId();

            ShPostType page = new ShPostType();
            page.setName("Sh1026Page" + UUID.randomUUID().toString().substring(0, 6));
            page.setTitle("SH1026 page");
            page.setDate(EPOCH);
            entityManager.persist(page);
            // SH1034 — the reference index is built from the model, so the type declares its File
            // field; without it the walk finds no edge and every card would read "no image".
            ShPostTypeAttr hero = new ShPostTypeAttr();
            hero.setName("HERO");
            hero.setLabel("HERO");
            hero.setShPostType(page);
            hero.setShWidget(shWidgetRepository.findFirstByName(ShSystemWidget.FILE));
            entityManager.persist(hero);

            ShPostType file = shStaticFileService.resolveOrCreateFilePostType();
            ShPostUnified image = post(root, file, "hero.png", "{\"FILE\":\"hero image.png\"}");
            ShPostUnified pdf = post(root, file, "brief.pdf", "{\"FILE\":\"brief.pdf\"}");
            ShPostUnified article = post(root, page, "Article", "{\"HERO\":\"" + image.getId() + "\"}");
            ShPostUnified bare = post(root, page, "Bare", "{\"HERO\":\"\"}");
            entityManager.flush();
            shObjectReferenceService.reindex(article);
            ids.put("image", image.getId());
            ids.put("pdf", pdf.getId());
            ids.put("article", article.getId());
            ids.put("bare", bare.getId());
        });
    }

    @Test
    @DisplayName("SH1026: an image is its own thumbnail, a page shows the image it references, others none")
    void eachCardShowsTheImageItHas() {
        Map<String, String> byId = new HashMap<>();
        for (ShConsolePostRow row : navigation.list(folderId, ShContentQuery.ALL).orElseThrow().posts()) {
            byId.put(row.id(), row.thumbnail());
        }
        String expected = "/file_source/" + ids.get("image") + "/hero%20image.png";
        assertThat(byId.get(ids.get("image"))).as("SH1026: a File that holds an image is its own card").isEqualTo(expected);
        assertThat(byId.get(ids.get("article")))
                .as("SH1026: a page shows the first image it references, read off the reference index")
                .isEqualTo(expected);
        assertThat(byId.get(ids.get("pdf"))).as("SH1026: a document is not an image, and draws the kind's icon").isNull();
        assertThat(byId.get(ids.get("bare"))).isNull();
    }

    private ShPostUnified post(ShFolder folder, ShPostType type, String title, String data) {
        ShPostUnified post = new ShPostUnified();
        post.setShFolder(folder);
        post.setShPostType(type);
        post.setState(ShPostState.PUBLISHED);
        post.setTitle(title);
        post.setFurl(title.toLowerCase().replace(' ', '-').replace('.', '-'));
        post.setData(data);
        post.setDate(EPOCH);
        post.setOwner("admin");
        entityManager.persist(post);
        return post;
    }
}
