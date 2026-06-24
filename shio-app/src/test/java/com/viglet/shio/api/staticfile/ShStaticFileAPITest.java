/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShSystemPostType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestMethodOrder(MethodOrderer.MethodName.class)
@TestInstance(Lifecycle.PER_CLASS)
class ShStaticFileAPITest {

    private static final Path FILE_SOURCE_DIR = createTempRoot();

    private static Path createTempRoot() {
        try {
            return Files.createTempDirectory("sh-file-source-test");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final Path IMAGE_CACHE_DIR = createTempRoot();

    @DynamicPropertySource
    static void fileSourcePath(DynamicPropertyRegistry registry) {
        registry.add("shio.file-source.path", () -> FILE_SOURCE_DIR.toAbsolutePath().toString());
        registry.add("shio.image-cache.path", () -> IMAGE_CACHE_DIR.toAbsolutePath().toString());
    }

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShFileStorageService storage;
    @Autowired
    private ShStaticFileService shStaticFileService;
    @Autowired
    private com.viglet.shio.migration.unified.ShPostUnifiedWriteService writeService;
    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private Principal mockPrincipal;

    private String folderId;
    private String uploadedPostId;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("admin");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("file-site-" + UUID.randomUUID());
            site.setDescription("Static File Test Site");
            site.setUrl("http://example.test");
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder rootFolder = new ShFolder();
            rootFolder.setName("root");
            rootFolder.setRootFolder((byte) 1);
            rootFolder.setShSite(site);
            rootFolder.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            rootFolder.setOwner("admin");
            entityManager.persist(rootFolder);
            folderId = rootFolder.getId();
        });
    }

    @Test
    void stage01UploadCreatesFilePostAndBytes() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "hello.txt", "text/plain", "hello world".getBytes(StandardCharsets.UTF_8));

        String response = mockMvc.perform(multipart("/api/v2/staticfile/upload")
                        .file(file)
                        .param("folderId", folderId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("hello.txt"))
                .andExpect(jsonPath("$.shPostType.name").value(ShSystemPostType.FILE))
                .andReturn().getResponse().getContentAsString();

        uploadedPostId = com.jayway.jsonpath.JsonPath.read(response, "$.id");
        assertNotNull(uploadedPostId);
        assertTrue(storage.exists(uploadedPostId, "hello.txt"), "bytes must be on disk");
    }

    @Test
    void stage01bListReturnsUploadedFile() throws Exception {
        // SH61: the file-manager listing endpoint returns the uploaded File post,
        // carrying its file name, size and content type.
        mockMvc.perform(get("/api/v2/staticfile").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.fileName == 'hello.txt')]").exists())
                .andExpect(jsonPath("$[?(@.fileName == 'hello.txt')].size").exists());
    }

    @Test
    void stage02ServeReturnsBytes() throws Exception {
        mockMvc.perform(get("/api/v2/staticfile/" + uploadedPostId).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("hello world")));
    }

    @Test
    void stage02bServeWithTransformParamsPassesThroughForNonImage() throws Exception {
        // SH39/SH40: ?w=&format= is intercepted, but a text/plain source is not a
        // re-encodable image, so the pipeline declines and the original bytes serve.
        mockMvc.perform(get("/api/v2/staticfile/" + uploadedPostId)
                        .param("w", "100").param("format", "webp")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("hello world")));
    }

    @Test
    void stage02cServeWithMalformedTransformParamIsBadRequest() throws Exception {
        // SH39: a non-positive dimension fails validation before reaching the pipeline.
        mockMvc.perform(get("/api/v2/staticfile/" + uploadedPostId)
                        .param("w", "-5")
                        .principal(mockPrincipal))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stage03PreUploadReportsExisting() throws Exception {
        mockMvc.perform(get("/api/v2/staticfile/pre-upload/" + folderId + "/hello.txt")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));

        mockMvc.perform(get("/api/v2/staticfile/pre-upload/" + folderId + "/absent.txt")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void stage04ServeUnknownReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v2/staticfile/" + UUID.randomUUID()).principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }

    @Test
    void stage05SoftDeleteKeepsBytesPurgeRemovesThem() throws Exception {
        assertTrue(storage.exists(uploadedPostId, "hello.txt"));

        // SH55: deleting a post now moves it to the trash (reversible) — the file
        // bytes are kept so the post can be restored, so NO cleanup fires yet.
        mockMvc.perform(delete("/api/v2/post-unified/" + uploadedPostId).principal(mockPrincipal))
                .andExpect(status().isNoContent());
        assertTrue(storage.exists(uploadedPostId, "hello.txt"),
                "a reversible trash must keep the file bytes");

        // Purging is permanent: it fires the DELETE lifecycle event, so the SH38
        // cleanup listener removes the bytes.
        mockMvc.perform(delete("/api/v2/post-unified/" + uploadedPostId + "/purge").principal(mockPrincipal))
                .andExpect(status().isNoContent());
        assertFalse(storage.exists(uploadedPostId, "hello.txt"),
                "purge must remove bytes via the cleanup listener");
    }

    @Test
    void stage06ImageTransformIsCachedWithEtagAnd304() throws Exception {
        // SH42: a real image transform produces a stable ETag; a follow-up request
        // carrying that ETag in If-None-Match must short-circuit to 304 Not Modified.
        BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, "png", baos), "fixture must encode");
        MockMultipartFile imageFile = new MockMultipartFile(
                "file", "pic.png", "image/png", baos.toByteArray());

        String response = mockMvc.perform(multipart("/api/v2/staticfile/upload")
                        .file(imageFile)
                        .param("folderId", folderId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String imagePostId = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        String etag = mockMvc.perform(get("/api/v2/staticfile/" + imagePostId)
                        .param("w", "100").param("format", "webp")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(result -> assertNotNull(result.getResponse().getHeader("ETag"),
                        "a transformed image is served with an ETag (SH42)"))
                .andReturn().getResponse().getHeader("ETag");

        assertNotNull(etag);
        mockMvc.perform(get("/api/v2/staticfile/" + imagePostId)
                        .param("w", "100").param("format", "webp")
                        .header("If-None-Match", etag)
                        .principal(mockPrincipal))
                .andExpect(status().isNotModified());
    }

    @Test
    void stage07TransformOverMaxDimensionIsBadRequest() throws Exception {
        // SH43: a requested dimension above shio.image-transform.max-width (default 5000)
        // is rejected with 400 before any transform work.
        String imagePostId = uploadImagePost();
        mockMvc.perform(get("/api/v2/staticfile/" + imagePostId)
                        .param("w", "99999")
                        .principal(mockPrincipal))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stage08SignedUrlWithoutSecretIsConflict() throws Exception {
        // SH43: minting a signed URL needs a configured secret; with none set (the test
        // default) the endpoint reports 409 rather than handing out an unusable token.
        String imagePostId = uploadImagePost();
        mockMvc.perform(get("/api/v2/staticfile/" + imagePostId + "/signed-url")
                        .param("w", "100")
                        .principal(mockPrincipal))
                .andExpect(status().isConflict());
    }

    @Test
    void stage09ImageUploadStampsDimensions() throws Exception {
        // SH62: an image upload records its pixel dimensions on the File post, so the
        // Media Library listing exposes width/height without re-reading the bytes.
        BufferedImage img = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, "png", baos), "fixture must encode");
        MockMultipartFile imageFile = new MockMultipartFile(
                "file", "dimensions.png", "image/png", baos.toByteArray());
        mockMvc.perform(multipart("/api/v2/staticfile/upload")
                        .file(imageFile)
                        .param("folderId", folderId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v2/staticfile").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.fileName == 'dimensions.png')].width").value(640))
                .andExpect(jsonPath("$[?(@.fileName == 'dimensions.png')].height").value(480));
    }

    @Test
    void stage10ListFiltersByCategoryAndQuery() throws Exception {
        // SH62: ?category=image keeps only images; ?q is a file-name substring match.
        MockMultipartFile doc = new MockMultipartFile(
                "file", "report.txt", "text/plain", "data".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v2/staticfile/upload")
                        .file(doc).param("folderId", folderId).principal(mockPrincipal))
                .andExpect(status().isOk());

        // category=image excludes the text file
        mockMvc.perform(get("/api/v2/staticfile").param("category", "image").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.fileName == 'report.txt')]").doesNotExist())
                .andExpect(jsonPath("$[?(@.fileName == 'dimensions.png')]").exists());

        // q matches by name (case-insensitive)
        mockMvc.perform(get("/api/v2/staticfile").param("q", "REPORT").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.fileName == 'report.txt')]").exists())
                .andExpect(jsonPath("$[?(@.fileName == 'dimensions.png')]").doesNotExist());
    }

    @Test
    void stage11UpdateMetaPersistsAltCaptionTags() throws Exception {
        // SH62: alt/caption/tags are editable metadata merged into the File post data
        // and surfaced on the listing.
        String id = uploadImagePost();
        mockMvc.perform(put("/api/v2/staticfile/" + id + "/meta")
                        .contentType("application/json")
                        .content("{\"alt\":\"a logo\",\"caption\":\"Our logo\",\"tags\":[\"brand\",\"logo\"]}")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alt").value("a logo"))
                .andExpect(jsonPath("$.caption").value("Our logo"))
                .andExpect(jsonPath("$.tags[0]").value("brand"));

        // and it is still readable through the listing
        mockMvc.perform(get("/api/v2/staticfile").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].alt").value("a logo"));
    }

    @Test
    void stage12UpdateMetaUnknownIsNotFound() throws Exception {
        mockMvc.perform(put("/api/v2/staticfile/" + UUID.randomUUID() + "/meta")
                        .contentType("application/json")
                        .content("{\"alt\":\"x\"}")
                        .principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }

    @Test
    void stage13UsageListsReferencingPosts() throws Exception {
        // SH62 "used in": a post whose data carries the file id is found as a referrer;
        // a file referenced by nobody returns an empty list.
        String fileId = uploadImagePost();

        // unused yet
        mockMvc.perform(get("/api/v2/staticfile/" + fileId + "/usage").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.postId)]").doesNotExist());

        // create a (File-type) post that references the file id in its data, as a
        // Content Select / File widget would
        String fileTypeId = shStaticFileService.resolveOrCreateFilePostType().getId();
        var data = objectMapper.createObjectNode();
        data.put("FILE", "referrer.txt");
        data.put("REF", fileId);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String referrerId = tx.execute(s -> writeService.create(
                new com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest(
                        folderId, fileTypeId, data,
                        com.viglet.shio.persistence.model.post.unified.ShPostState.PUBLISHED,
                        "admin", null, null)).getId());

        mockMvc.perform(get("/api/v2/staticfile/" + fileId + "/usage").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.postId == '" + referrerId + "')]").exists());
    }

    /** Upload a small PNG and return its File-post id. */
    private String uploadImagePost() throws Exception {
        BufferedImage img = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, "png", baos), "fixture must encode");
        MockMultipartFile imageFile = new MockMultipartFile(
                "file", "pic.png", "image/png", baos.toByteArray());
        String response = mockMvc.perform(multipart("/api/v2/staticfile/upload")
                        .file(imageFile)
                        .param("folderId", folderId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }
}
