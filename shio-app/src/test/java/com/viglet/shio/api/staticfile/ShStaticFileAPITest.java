/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /**
     * SH210 — the digest is what lets {@code push --content} tell an edited asset from an
     * untouched one without moving either copy. It reports the name too, so a caller can check
     * it is reconciling what it thinks it is.
     */
    @Test
    void stage06aDigestReportsSizeAndSha256() throws Exception {
        String id = uploadTextPost("digestable.txt", "the original bytes");
        String expected = sha256Hex("the original bytes");

        mockMvc.perform(get("/api/v2/staticfile/" + id + "/digest").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("digestable.txt"))
                .andExpect(jsonPath("$.size").value(18))
                .andExpect(jsonPath("$.sha256").value(expected));
    }

    /**
     * The point of the whole task: the bytes at a path can be edited. Before this, an upload was
     * the only write, so {@code push --content} skipped a path the server already had and editing
     * an asset uploaded nothing while reporting success.
     */
    @Test
    void stage06bReplaceSwapsTheBytesAndKeepsTheIdentity() throws Exception {
        String id = uploadTextPost("swappable.txt", "before");
        MockMultipartFile replacement = new MockMultipartFile(
                // A different name on purpose: the name is half the asset's address, so a replace
                // must not be able to move the asset by accident.
                "file", "ignored-name.txt", "text/plain", "after, and longer".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v2/staticfile/" + id + "/replace")
                        .file(replacement)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.fileName").value("swappable.txt"))
                .andExpect(jsonPath("$.size").value(17));

        assertEquals("after, and longer",
                Files.readString(storage.resolve(id, "swappable.txt")),
                "the bytes on disk are the new ones, under the same id and name");
        mockMvc.perform(get("/api/v2/staticfile/" + id + "/digest").principal(mockPrincipal))
                .andExpect(jsonPath("$.sha256").value(sha256Hex("after, and longer")));
    }

    /** An empty body would truncate the asset and answer 200 (P7: never surprise the human). */
    @Test
    void stage06cReplaceRefusesAnEmptyBody() throws Exception {
        String id = uploadTextPost("keepme.txt", "keep me");
        MockMultipartFile empty = new MockMultipartFile("file", "keepme.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/v2/staticfile/" + id + "/replace")
                        .file(empty)
                        .principal(mockPrincipal))
                .andExpect(status().isBadRequest());

        assertEquals("keep me", Files.readString(storage.resolve(id, "keepme.txt")));
    }

    @Test
    void stage06dReplaceAndDigestRefuseAnIdThatIsNotAFilePost() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v2/staticfile/not-a-file-post/digest").principal(mockPrincipal))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart("/api/v2/staticfile/not-a-file-post/replace")
                        .file(file)
                        .principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }

    /**
     * Stale dimensions are worse than none: the Media Library renders them, so an image replaced
     * by something that is not one would keep the old picture's shape for ever.
     */
    @Test
    void stage06eReplacingAnImageWithATextFileClearsTheDimensions() throws Exception {
        String id = uploadImagePost();
        mockMvc.perform(get("/api/v2/staticfile").principal(mockPrincipal))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].width").value(120));

        MockMultipartFile notAnImage = new MockMultipartFile(
                "file", "pic.png", "text/plain", "not a picture".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v2/staticfile/" + id + "/replace")
                        .file(notAnImage)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.width").doesNotExist())
                .andExpect(jsonPath("$.height").doesNotExist());
    }

    /**
     * SH398 — the digest is stamped at write time and rides the listing, which is what removes one
     * HTTP request per asset from every `shio pull --content` over a settled tree.
     */
    @Test
    void stage06fTheDigestIsStampedAtUploadAndRidesTheListing() throws Exception {
        String id = uploadTextPost("stamped.txt", "stamped bytes");
        String expected = sha256Hex("stamped bytes");

        mockMvc.perform(get("/api/v2/staticfile").principal(mockPrincipal))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].sha256").value(expected));
        // And the endpoint agrees, because it prefers the stamp rather than keeping a second
        // opinion: two answers to one question is how a cache starts disagreeing with its source.
        mockMvc.perform(get("/api/v2/staticfile/" + id + "/digest").principal(mockPrincipal))
                .andExpect(jsonPath("$.sha256").value(expected));
    }

    /** A stamp that outlived its bytes would restore the silent no-op SH210 removed. */
    @Test
    void stage06gReplacingTheBytesRestampsTheDigest() throws Exception {
        String id = uploadTextPost("restamp.txt", "before");
        MockMultipartFile replacement = new MockMultipartFile(
                "file", "restamp.txt", "text/plain", "after".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v2/staticfile/" + id + "/replace")
                        .file(replacement)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sha256").value(sha256Hex("after")));
        mockMvc.perform(get("/api/v2/staticfile/" + id + "/digest").principal(mockPrincipal))
                .andExpect(jsonPath("$.sha256").value(sha256Hex("after")));
    }

    /**
     * A stamp covers only rows written after it ships, so the on-demand hash is not an alternative
     * to it — it is what every asset already in an instance keeps using.
     */
    @Test
    void stage06hARowWithNoStampIsStillHashedOnDemand() throws Exception {
        String id = uploadTextPost("legacy.txt", "legacy bytes");
        // Strip the stamp, which is exactly the state of every File post written before SH398.
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> entityManager
                .createQuery("update ShPostUnified p set p.data = :data where p.id = :id")
                .setParameter("data", "{\"FILE\":\"legacy.txt\"}")
                .setParameter("id", id)
                .executeUpdate());

        mockMvc.perform(get("/api/v2/staticfile/" + id + "/digest").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sha256").value(sha256Hex("legacy bytes")));
    }

    /**
     * SH398 collapsed `isRegularFile` + `size` into one `readAttributes`, and SH62's convention has
     * to survive it: `size: -1` means the post is there and its bytes are not. Stamping the size
     * instead would have made this branch report a number for a file nobody can serve.
     */
    @Test
    void stage06iBytesRemovedUnderneathStillReportMinusOne() throws Exception {
        String id = uploadTextPost("vanishing.txt", "here for now");
        Files.delete(storage.resolve(id, "vanishing.txt"));

        mockMvc.perform(get("/api/v2/staticfile").principal(mockPrincipal))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].size").value(-1));
        mockMvc.perform(get("/api/v2/staticfile/" + id + "/digest").principal(mockPrincipal))
                .andExpect(jsonPath("$.size").value(-1))
                .andExpect(jsonPath("$.sha256").doesNotExist());
    }

    /**
     * SH399 — the digest is a cache, and its only invalidation is a `replace`. Bytes changed under
     * the CMS would otherwise leave every reader confidently holding a hash that is not theirs.
     */
    @Test
    void stage06jAStampIsDroppedWhenTheBytesChangedUnderneath() throws Exception {
        String id = uploadTextPost("meddled.txt", "original");
        Files.writeString(storage.resolve(id, "meddled.txt"), "tampered with, and longer");

        // The listing reports no digest rather than the stale one: a caller that gets none falls
        // back and is right, a caller that gets a stale one is wrong and does not know it.
        mockMvc.perform(get("/api/v2/staticfile").principal(mockPrincipal))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].sha256")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())));
        // And the endpoint recomputes, so the answer is right at the old cost.
        mockMvc.perform(get("/api/v2/staticfile/" + id + "/digest").principal(mockPrincipal))
                .andExpect(jsonPath("$.sha256").value(sha256Hex("tampered with, and longer")));
    }

    /** The fast path is still fast: an untouched file answers from the stamp. */
    @Test
    void stage06kAnUntouchedFileStillAnswersFromTheStamp() throws Exception {
        String id = uploadTextPost("untouched.txt", "steady");

        mockMvc.perform(get("/api/v2/staticfile").principal(mockPrincipal))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].sha256").value(sha256Hex("steady")));
    }

    /**
     * The limit of the guarantee, asserted rather than assumed. A length-preserving edit is
     * invisible to a size check, and nothing short of hashing on every read would see it — which
     * is exactly the cost SH398 exists to remove. Written down here so a reader learns how far it
     * goes from a test rather than from a surprise.
     */
    @Test
    void stage06lALengthPreservingEditIsNotDetected() throws Exception {
        String id = uploadTextPost("samesize.txt", "aaaaaa");
        Files.writeString(storage.resolve(id, "samesize.txt"), "bbbbbb");

        mockMvc.perform(get("/api/v2/staticfile/" + id + "/digest").principal(mockPrincipal))
                .andExpect(jsonPath("$.sha256").value(sha256Hex("aaaaaa")));
    }

    private String uploadTextPost(String name, String body) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", name, "text/plain", body.getBytes(StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart("/api/v2/staticfile/upload")
                        .file(file)
                        .param("folderId", folderId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }

    private static String sha256Hex(String body) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(body.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (byte b : digest) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
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
