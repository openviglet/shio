/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.exchange.site;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.site.ShSiteWriteService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

/**
 * SH71 — creating a site clones the bundled {@code bootstrap-site} template
 * (post-types + folders + sample pages) instead of leaving it blank, while the new
 * site still carries the user's chosen metadata (name/description/url), not the
 * template's. Exercises {@link ShSiteWriteService#create} end to end through the
 * SH71 provider → SH70 import → SH68 format.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShSiteBootstrapTest {

    private static final List<ShPostState> ALL = List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    @Autowired
    private ShSiteWriteService shSiteWriteService;
    @Autowired
    private ShSiteRepository shSiteRepository;
    @Autowired
    private ShFolderRepository shFolderRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private ShPostTypeRepository shPostTypeRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void createSiteClonesBootstrapStructure() {
        String name = "ut-bootstrap-" + UUID.randomUUID();
        ShSite payload = new ShSite();
        payload.setName(name);
        payload.setDescription("My description");
        payload.setUrl("http://example.test");

        ShSite created = shSiteWriteService.create(payload, "admin");

        // The cloned site keeps the user's identity, not the template's.
        assertThat(created.getName()).isEqualTo(name);
        assertThat(created.getDescription()).isEqualTo("My description");
        assertThat(created.getName()).isNotEqualTo("Bootstrap Site");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = shSiteRepository.findById(created.getId()).orElseThrow();

            // The "Page" post-type from the template was provisioned.
            assertThat(shPostTypeRepository.findByName("Page")).isNotNull();

            // Root folder + the template's "Pages" child folder.
            ShFolder root = shFolderRepository.findByShSiteAndRootFolder(site, (byte) 1)
                    .iterator().next();
            List<ShFolder> children = List.copyOf(shFolderRepository.findByParentFolder(root));
            assertThat(children).extracting(ShFolder::getName).contains("Pages");

            // Sample pages cloned into the tree.
            List<ShPostUnified> rootPosts = shPostUnifiedRepository.findByShFolderAndStateIn(root, ALL);
            assertThat(rootPosts).extracting(ShPostUnified::getTitle).contains("Welcome");

            ShFolder pages = children.stream()
                    .filter(f -> "Pages".equals(f.getName())).findFirst().orElseThrow();
            List<ShPostUnified> pagePosts = shPostUnifiedRepository.findByShFolderAndStateIn(pages, ALL);
            assertThat(pagePosts).extracting(ShPostUnified::getTitle).contains("About");
        });
    }
}
