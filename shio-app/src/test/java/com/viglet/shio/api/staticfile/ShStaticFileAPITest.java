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
import com.viglet.shio.post.type.ShSystemPostTypeAttr;

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
        String listing = mockMvc.perform(get("/api/v2/staticfile").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.fileName == 'hello.txt')]").exists())
                .andExpect(jsonPath("$[?(@.fileName == 'hello.txt')].size").exists())
                .andReturn().getResponse().getContentAsString();

        // Stated as an assertion rather than a comment, because `andExpect` carries no message and
        // a jsonPath failure names the path it could not find, never what the path was for.
        assertTrue(listing.contains("hello.txt") && listing.contains("\"size\""),
                "SH61: the row AND the metadata the manager renders. Before this endpoint the "
                        + "`File` widget could upload and nothing could *see* what was stored; a "
                        + "listing that returned names without size or content type would be the "
                        + "same blindness one call further on");
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

    /**
     * SH950 — an uploaded SVG is not a document this origin renders.
     *
     * <p>The route probes the content type from the file name and sent
     * {@code Content-Disposition: inline} for whatever came back, with no
     * {@code X-Content-Type-Options} and no {@code Content-Security-Policy} anywhere in the
     * backend. So {@code evil.svg} was served as a scriptable document on the console's own
     * origin, through an endpoint an agent token may call: stored XSS against the curator's
     * session, needing no other defect.</p>
     *
     * <p>These three live here rather than beside the rest of SH950 in
     * {@code ShAssetSecurityHeadersTest} because this class already owns an isolated context with
     * a temp file-source root, and a second {@code @SpringBootTest} would have spent the last slot
     * SH423's headroom budget had.</p>
     */
    @Test
    void stage02dServeSvgIsNotARenderableDocument() throws Exception {
        String id = uploadFile("evil.svg", "image/svg+xml",
                ("<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(document.domain)</script></svg>")
                        .getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v2/staticfile/" + id).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var response = result.getResponse();
                    assertEquals("nosniff",
                            response.getHeader(ShAssetResponseHeaders.X_CONTENT_TYPE_OPTIONS),
                            "SH950: without nosniff a browser may still make a document of these bytes");
                    assertEquals(ShAssetResponseHeaders.ASSET_CSP,
                            response.getHeader(ShAssetResponseHeaders.CONTENT_SECURITY_POLICY),
                            "SH950: an asset opened directly must land in a sandbox that runs nothing");
                    String disposition = response.getHeader("Content-Disposition");
                    assertNotNull(disposition, "SH950: an asset must say how it is to be handled");
                    assertFalse(disposition.startsWith("inline"),
                            "SH950: an SVG carries script and must not be handed to the browser to "
                                    + "render. Got: " + disposition);
                });
    }

    /**
     * SH950's rule is about everything that is not a raster image, and a PDF is the case that
     * still reaches storage.
     *
     * <p>This asserted an uploaded {@code .html} until SH951, which denies that type at the door —
     * so the more obvious hole is now closed one layer earlier ({@code stage06a}) and cannot be set
     * up here at all. A PDF is the honest subject for the serving rule: the policy stores it, it is
     * a document, it can carry script, and it must not be handed to the browser to render.</p>
     */
    @Test
    void stage02eServeAStoredDocumentIsNotRenderedInline() throws Exception {
        String id = uploadFile("brochure.pdf", "application/pdf",
                "%PDF-1.4 not really a pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v2/staticfile/" + id).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(
                        result.getResponse().getHeader("Content-Disposition").startsWith("attachment"),
                        "SH950: only a raster image renders inline. Got: "
                                + result.getResponse().getHeader("Content-Disposition")));
    }

    /**
     * The half a fix like this loses. If the media library stops showing images it gets reverted,
     * and the hole reopens with it.
     */
    @Test
    void stage02fServePngStillRendersInline() throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", png);
        String id = uploadFile("ok.png", "image/png", png.toByteArray());

        mockMvc.perform(get("/api/v2/staticfile/" + id).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    assertEquals("inline; filename=\"ok.png\"",
                            result.getResponse().getHeader("Content-Disposition"),
                            "SH950: a raster image is not a document and must still render");
                    assertEquals("nosniff",
                            result.getResponse().getHeader(ShAssetResponseHeaders.X_CONTENT_TYPE_OPTIONS),
                            "SH950: nosniff goes on every asset, including the ones served inline");
                });
    }

    /**
     * SH951 — the upload policy, at the boundary it defends.
     *
     * <p>SH950 stopped these bytes being <em>served</em> as documents. They were still stored, still
     * listed and still copied into a content tree by {@code shio pull}, so the hole had moved rather
     * than closed. Here the HTML never lands at all, and the SVG lands with its behaviour taken
     * out.</p>
     */
    @Test
    void stage06aUploadOfAnHtmlFileIsRefusedWithAnInstruction() throws Exception {
        mockMvc.perform(multipart("/api/v2/staticfile/upload")
                        .file(new MockMultipartFile("file", "page.html", "text/html",
                                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)))
                        .param("folderId", folderId)
                        .principal(mockPrincipal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fix").exists())
                .andExpect(jsonPath("$.allowed").exists());
    }

    @Test
    void stage06bUploadOfAScriptedSvgIsStoredWithoutTheScript() throws Exception {
        String hostile = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\">"
                + "<script>alert(document.domain)</script>"
                + "<circle cx=\"5\" cy=\"5\" r=\"4\" fill=\"red\"/></svg>";

        String created = mockMvc
                .perform(multipart("/api/v2/staticfile/upload")
                        .file(new MockMultipartFile("file", "logo.svg", "image/svg+xml",
                                hostile.getBytes(StandardCharsets.UTF_8)))
                        .param("folderId", folderId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");
        assertTrue(created.contains(ShSystemPostTypeAttr.SANITIZED),
                "SH951: the post must record that its stored bytes are not the bytes that arrived — "
                        + "`shio pull` projects this, so a replica's difference reads as a policy "
                        + "rather than as a defect");

        String served = mockMvc.perform(get("/api/v2/staticfile/" + id).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertFalse(served.contains("<script"),
                "SH951: the script must be gone from the STORED bytes, not merely neutralised on "
                        + "the way out — a later change of policy must not resurrect a payload");
        assertFalse(served.contains("onload"), "SH951: the event handler must be gone too");
        assertTrue(served.contains("circle"),
                "SH951: the drawing is the point — a sanitizer that empties the picture is a "
                        + "denial with extra steps, and a replicated logo is usually an SVG");
    }

    /** Upload bytes under a name and return the new File post's id. */
    private String uploadFile(String fileName, String declaredType, byte[] bytes) throws Exception {
        String response = mockMvc
                .perform(multipart("/api/v2/staticfile/upload")
                        .file(new MockMultipartFile("file", fileName, declaredType, bytes))
                        .param("folderId", folderId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(response, "$.id");
        assertNotNull(id, "the upload must return the new File post's id");
        return id;
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
                .andExpect(status().isBadRequest())
                // SH514 — the refusal was right and Spring was dropping the sentence on the floor.
                .andExpect(jsonPath("$.title").value("This replacement carries no bytes"))
                .andExpect(jsonPath("$.fix").exists());

        assertEquals("keep me", Files.readString(storage.resolve(id, "keepme.txt")));
    }

    /**
     * SH514 — a zero-byte file is a file, and refusing it killed a 64-asset conversion.
     *
     * <p>{@code vilt-group.com} serves {@code /css/en.css?v=cf8427e} as <b>200 with 0 bytes</b> — a
     * legitimately empty stylesheet — and the capture wrote it faithfully. This endpoint answered
     * {@code ResponseStatusException(BAD_REQUEST, "Upload is empty")}, which Spring renders with no
     * title, no detail and no fix: the only endpoint in a whole session's traffic to answer that
     * way, while its own sibling refusal for a missing {@code folderId} carries all four.</p>
     *
     * <p>{@code MultipartFile.isEmpty()} was answering two questions at once — no part sent, and a
     * part holding an empty file — and separating them settles it. A replica that 404s where its
     * source answered an empty 200 is less faithful, not safer.</p>
     */
    @Test
    void stage06nAnEmptyFileIsAFileAndIsStored() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "en.css", "text/css", new byte[0]);

        String response = mockMvc.perform(multipart("/api/v2/staticfile/upload")
                        .file(empty)
                        .param("folderId", folderId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("en.css"))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(response, "$.id");
        assertTrue(storage.exists(id, "en.css"), "zero is a count of bytes, and the file is stored");
        assertEquals(0, Files.size(storage.resolve(id, "en.css")));
    }

    /** And the caller mistake the old rule was conflated with, refused the way everything else is. */
    @Test
    void stage06oAPartWithNoNameIsRefusedWithAProblemDocument() throws Exception {
        MockMultipartFile unnamed = new MockMultipartFile("file", "", "text/css", new byte[0]);

        mockMvc.perform(multipart("/api/v2/staticfile/upload")
                        .file(unnamed)
                        .param("folderId", folderId)
                        .principal(mockPrincipal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("This upload has no file name"))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.fix").exists());
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

    /**
     * SH399 — the stamped size is read as a {@code long}. Read as an {@code int} it truncates past
     * two gigabytes, the validator never matches, and the fast path is lost for exactly the files
     * whose hash costs the most: safe, and silently degraded.
     */
    @Test
    void stage06mAStampedSizePastIntegerRangeStillValidates() throws Exception {
        String id = uploadTextPost("huge.txt", "small file, huge claim");
        long huge = 5_000_000_000L;
        // The file cannot really be 5 GB in a test, so the *stamp* is set to one and the file's own
        // size is what disagrees — which proves the comparison happens in long arithmetic rather
        // than after a truncation that would make any two large numbers look equally wrong.
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> entityManager
                .createQuery("update ShPostUnified p set p.data = :data where p.id = :id")
                .setParameter("data", "{\"FILE\":\"huge.txt\",\"SHA256\":\"deadbeef\",\"SIZE\":"
                        + huge + "}")
                .setParameter("id", id)
                .executeUpdate());

        mockMvc.perform(get("/api/v2/staticfile/" + id + "/digest").principal(mockPrincipal))
                .andExpect(jsonPath("$.sha256").value(sha256Hex("small file, huge claim")));

        // And the matching case: a stamp whose size is the file's own is trusted, whatever its
        // magnitude — asserted through the value `intFromData` would have mangled.
        long real = Files.size(storage.resolve(id, "huge.txt"));
        tx.executeWithoutResult(status -> entityManager
                .createQuery("update ShPostUnified p set p.data = :data where p.id = :id")
                .setParameter("data", "{\"FILE\":\"huge.txt\",\"SHA256\":\"deadbeef\",\"SIZE\":"
                        + real + "}")
                .setParameter("id", id)
                .executeUpdate());

        mockMvc.perform(get("/api/v2/staticfile/" + id + "/digest").principal(mockPrincipal))
                .andExpect(jsonPath("$.sha256").value("deadbeef"));
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
