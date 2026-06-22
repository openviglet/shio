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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.security.Principal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.exchange.ShExchangePackageCodec;
import com.viglet.shio.exchange.model.ShExchangeFolder;
import com.viglet.shio.exchange.model.ShExchangePackage;
import com.viglet.shio.exchange.model.ShExchangePost;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH69 site export → zip, end to end with the SH68 format codec: a site is
 * walked into a package, packed as a zip by {@link ShSiteExportService}, and the
 * same {@link ShExchangePackageCodec} unzips + parses it back, proving folders,
 * posts (with verbatim {@code data}) and the referenced post-type def all survive
 * the round-trip. Also covers the {@code GET /api/v2/site/{id}/export} endpoint
 * (200 + {@code application/zip}) and the unknown-site 404.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShSiteExportServiceTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShSiteExportService shSiteExportService;
    @Autowired
    private ShExchangePackageCodec codec;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private Principal mockPrincipal;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String siteId;
    private String rootFolderId;
    private String childFolderId;
    private String postTypeName;
    private String postId;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("admin");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("ut-export-site-" + UUID.randomUUID());
            site.setDescription("Export API Test Site");
            site.setUrl("http://example.test");
            site.setFurl("export-test");
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
            rootFolderId = root.getId();

            ShFolder child = new ShFolder();
            child.setName("News");
            child.setRootFolder((byte) 0);
            child.setParentFolder(root);
            child.setFurl("news");
            child.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            child.setOwner("admin");
            entityManager.persist(child);
            childFolderId = child.getId();

            ShWidget textWidget = entityManager.find(ShWidget.class,
                    shWidgetRepository.findFirstByName(ShSystemWidget.TEXT).getId());

            ShPostType postType = new ShPostType();
            postType.setName("PT-EXP-" + UUID.randomUUID());
            postType.setTitle("Export Test Post Type");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");

            ShPostTypeAttr titleAttr = new ShPostTypeAttr();
            titleAttr.setName(ShSystemPostTypeAttr.TITLE);
            titleAttr.setLabel("Title");
            titleAttr.setOrdinal(1);
            titleAttr.setRequired((byte) 1);
            titleAttr.setIsTitle((byte) 1);
            titleAttr.setIsSummary((byte) 0);
            titleAttr.setShWidget(textWidget);
            titleAttr.setShPostType(postType);
            postType.setShPostTypeAttrs(Set.of(titleAttr));
            entityManager.persist(postType);
            postTypeName = postType.getName();

            ObjectNode data = objectMapper.createObjectNode();
            data.put(ShSystemPostTypeAttr.TITLE, "Exported Post");
            postId = shPostUnifiedWriteService.create(
                    new CreateRequest(childFolderId, postType.getId(), data, ShPostState.DRAFT, "admin", null, null))
                    .getId();
        });
    }

    @Test
    void exportSiteRoundTripsThroughTheZip(@TempDir Path tmp) {
        ShExchangeArchive archive = shSiteExportService.exportSite(siteId).orElseThrow();
        assertThat(archive.fileName()).isEqualTo("export-test.zip");
        assertThat(archive.content()).isNotEmpty();

        Path root = codec.unzip(new ByteArrayInputStream(archive.content()), tmp.resolve("dest"));
        ShExchangePackage pkg = codec.readPackage(root);

        // Site + its root-folder anchor.
        assertThat(pkg.sites()).hasSize(1);
        assertThat(pkg.sites().get(0).id()).isEqualTo(siteId);
        assertThat(pkg.sites().get(0).rootFolderIds()).contains(rootFolderId);

        // Folder tree (root + recursive child) preserved with parent/site anchors.
        assertThat(pkg.folders()).extracting(ShExchangeFolder::id)
                .contains(rootFolderId, childFolderId);
        ShExchangeFolder child = pkg.folders().stream()
                .filter(f -> f.id().equals(childFolderId)).findFirst().orElseThrow();
        assertThat(child.rootFolder()).isFalse();
        assertThat(child.parentFolderId()).isEqualTo(rootFolderId);

        // Post carried with its verbatim data document + post-type-by-name reference.
        ShExchangePost post = pkg.posts().stream()
                .filter(p -> p.id().equals(postId)).findFirst().orElseThrow();
        assertThat(post.state()).isEqualTo("DRAFT");
        assertThat(post.folderId()).isEqualTo(childFolderId);
        assertThat(post.postTypeName()).isEqualTo(postTypeName);
        assertThat(post.data().get(ShSystemPostTypeAttr.TITLE).asString()).isEqualTo("Exported Post");

        // Referenced post-type emitted once, as the lossless SH16 def.
        assertThat(pkg.postTypes()).extracting(ShPostTypeDef::name).containsExactly(postTypeName);
        assertThat(pkg.postTypes().get(0).fields()).extracting(ShPostTypeAttrDef::name)
                .contains(ShSystemPostTypeAttr.TITLE);
    }

    @Test
    void exportUnknownSiteIsEmpty() {
        assertThat(shSiteExportService.exportSite("missing-" + UUID.randomUUID())).isEmpty();
    }

    @Test
    void exportEndpointStreamsZip() throws Exception {
        mockMvc.perform(get("/api/v2/site/" + siteId + "/export").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType())
                        .isEqualTo("application/zip"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());
    }

    @Test
    void exportEndpointUnknownSiteReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v2/site/missing-" + UUID.randomUUID() + "/export").principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }
}
