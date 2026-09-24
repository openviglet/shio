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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.exchange.model.ShExchangeFile;
import com.viglet.shio.exchange.model.ShExchangeFolder;
import com.viglet.shio.exchange.model.ShExchangePackage;
import com.viglet.shio.exchange.model.ShExchangePost;
import com.viglet.shio.exchange.model.ShExchangeSite;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH70 site import / clone, end to end with the SH69 export: a site is exported to
 * a package zip, then imported back into the <em>same</em> install — proving the
 * clone reassigns fresh ids (no PK collision), rebuilds the folder tree, recreates
 * posts with their verbatim data, and rewrites embedded id references to the cloned
 * posts. Also covers the {@code POST /api/v2/import} multipart endpoint.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShSiteImportServiceTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShSiteExportService shSiteExportService;
    @Autowired
    private ShSiteImportService shSiteImportService;
    @Autowired
    private ShSiteRepository shSiteRepository;
    @Autowired
    private ShFolderRepository shFolderRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
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

    private String sourceSiteId;
    private String sourceChildFolderId;
    private String sourcePostId;
    private String sourceRefPostId;
    private byte[] exportedZip;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("admin");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("ut-import-src-" + UUID.randomUUID());
            site.setUrl("http://example.test");
            site.setFurl("import-src-" + UUID.randomUUID());
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);
            sourceSiteId = site.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            root.setOwner("admin");
            entityManager.persist(root);

            ShFolder child = new ShFolder();
            child.setName("Articles");
            child.setRootFolder((byte) 0);
            child.setParentFolder(root);
            child.setFurl("articles");
            child.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            child.setOwner("admin");
            entityManager.persist(child);
            sourceChildFolderId = child.getId();

            ShWidget textWidget = entityManager.find(ShWidget.class,
                    shWidgetRepository.findFirstByName(ShSystemWidget.TEXT).getId());

            ShPostType postType = new ShPostType();
            postType.setName("PT-IMP-" + UUID.randomUUID());
            postType.setTitle("Import Test Post Type");
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

            String postTypeId = postType.getId();

            ObjectNode targetData = objectMapper.createObjectNode();
            targetData.put(ShSystemPostTypeAttr.TITLE, "Target Post");
            sourceRefPostId = shPostUnifiedWriteService.create(
                    new CreateRequest(sourceChildFolderId, postTypeId, targetData, ShPostState.DRAFT, "admin", null, null))
                    .getId();

            // A second post whose data embeds a reference to the first post's id
            // (as a Content Select / File widget would store it) — to assert remap.
            ObjectNode data = objectMapper.createObjectNode();
            data.put(ShSystemPostTypeAttr.TITLE, "Source Post");
            data.put("related", sourceRefPostId);
            sourcePostId = shPostUnifiedWriteService.create(
                    new CreateRequest(sourceChildFolderId, postTypeId, data, ShPostState.DRAFT, "admin", null, null))
                    .getId();
        });

        exportedZip = shSiteExportService.exportSite(sourceSiteId).orElseThrow().content();
    }

    @Test
    void importClonesSiteWithFreshIdsAndRemappedReferences() {
        ShSite imported = shSiteImportService.importPackage(
                new ByteArrayInputStream(exportedZip), "importer");

        // A brand-new site row, distinct from the source.
        assertThat(imported.getId()).isNotEqualTo(sourceSiteId);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite newSite = shSiteRepository.findById(imported.getId()).orElseThrow();

            ShFolder newRoot = shFolderRepository.findByShSiteAndRootFolder(newSite, (byte) 1)
                    .iterator().next();
            assertThat(newRoot.getId()).isNotEqualTo(sourceChildFolderId);

            // Cloned child folder under the new root.
            List<ShFolder> children = List.copyOf(shFolderRepository.findByParentFolder(newRoot));
            assertThat(children).hasSize(1);
            ShFolder newChild = children.get(0);
            assertThat(newChild.getName()).isEqualTo("Articles");
            assertThat(newChild.getId()).isNotEqualTo(sourceChildFolderId);

            // Both posts cloned into the new child folder, with fresh ids.
            List<ShPostUnified> posts = shPostUnifiedRepository
                    .findByShFolderAndStateIn(newChild, List.of(ShPostState.DRAFT, ShPostState.PUBLISHED));
            assertThat(posts).hasSize(2);
            assertThat(posts).noneMatch(p -> p.getId().equals(sourcePostId)
                    || p.getId().equals(sourceRefPostId));

            // The "Source Post" must point its `related` ref at the CLONED target id,
            // not the original — proving the data reference remap.
            ShPostUnified source = posts.stream()
                    .filter(p -> "Source Post".equals(p.getTitle())).findFirst().orElseThrow();
            ShPostUnified target = posts.stream()
                    .filter(p -> "Target Post".equals(p.getTitle())).findFirst().orElseThrow();
            assertDataField(source.getData(), "related", target.getId());
            assertThat(source.getData()).doesNotContain(sourceRefPostId);
        });
    }

    @Test
    void importEndpointAcceptsMultipartZip() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "site.zip",
                "application/zip", exportedZip);

        // SH1079 — the answer is the site and what arrived in it, not the site row alone.
        mockMvc.perform(multipart("/api/v2/import").file(file).principal(mockPrincipal))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.site.id").exists())
                .andExpect(jsonPath("$.arrived.sites").value(1))
                .andExpect(jsonPath("$.arrived.posts").value(2))
                .andExpect(jsonPath("$.problems").isArray());
    }

    @Test
    void sh1079AnImportNamesWhatItCouldNotDo() {
        // A package built to trip each thing the import already knew and used to drop: a post in a
        // folder the package does not carry, a post whose type exists nowhere, and a listed file.
        String siteId = "sh1079-site";
        String rootId = "sh1079-root";
        ShExchangePackage pkg = new ShExchangePackage(ShExchangePackage.CURRENT_VERSION,
                "2026-09-24T00:00:00Z", "test",
                List.of(new ShExchangeSite(siteId, "SH1079 " + UUID.randomUUID(), null, null,
                        null, null, null, null, "admin", List.of(rootId))),
                List.of(),
                List.of(new ShExchangeFolder(rootId, "root", null, true, siteId, null, 0, "admin")),
                List.of(new ShExchangePost("sh1079-kept", rootId, "SH1079-NO-SUCH-TYPE", "DRAFT",
                                "Kept Without A Type", null, "/kept", "admin", false, null, 0, null),
                        new ShExchangePost("sh1079-stray", "sh1079-no-such-folder", null, "DRAFT",
                                "Stray Post", null, "/stray", "admin", false, null, 0, null)),
                List.of(new ShExchangeFile("sh1079-file", "logo.png", "image/png")));

        ShImportOutcome outcome = new TransactionTemplate(transactionManager)
                .execute(status -> shSiteImportService.importWithOutcome(pkg, "admin"));

        assertThat(outcome.arrived())
                .as("SH1079: the tally counts what landed, and the stray post did not")
                .isEqualTo(new ShImportOutcome.Arrived(1, 1, 1, 0));
        assertThat(outcome.problems()).extracting(ShImportOutcome.Problem::code)
                .as("SH1079: each thing the import could not do is named, in the order it met them")
                .containsExactly("post-type-unresolved", "post-skipped", "files-not-imported");
        assertThat(outcome.problems().get(0).subject()).isEqualTo("SH1079-NO-SUCH-TYPE");
        assertThat(outcome.problems().get(1).subject()).isEqualTo("Stray Post");
    }

    @Test
    void importEndpointRejectsGarbage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bad.zip",
                "application/zip", "not a zip".getBytes());

        mockMvc.perform(multipart("/api/v2/import").file(file).principal(mockPrincipal))
                .andExpect(status().isBadRequest());
    }

    private static void assertDataField(String dataJson, String field, String expected) {
        assertThat(dataJson).contains("\"" + field + "\":\"" + expected + "\"");
    }
}
