/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.folder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Covers the folder CRUD and object-navigation surface rebuilt on the unified
 * post model: {@code /api/v2/folder} CRUD + {@code /path}, and
 * {@code /api/v2/object/{id}/list}, {@code /copyto}, {@code /moveto}.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestMethodOrder(MethodOrderer.MethodName.class)
@TestInstance(Lifecycle.PER_CLASS)
class ShFolderAPITest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
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
    private String siteName;
    private String rootFolderId;
    private String postTypeId;
    private String childFolderId;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("admin");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("ut-folder-site-" + UUID.randomUUID());
            site.setDescription("Folder API Test Site");
            site.setUrl("http://example.test");
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();
            siteName = site.getName();

            ShFolder rootFolder = new ShFolder();
            rootFolder.setName("root");
            rootFolder.setRootFolder((byte) 1);
            rootFolder.setShSite(site);
            rootFolder.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            rootFolder.setOwner("admin");
            entityManager.persist(rootFolder);
            rootFolderId = rootFolder.getId();

            ShWidget textWidget = entityManager.find(ShWidget.class,
                    shWidgetRepository.findFirstByName(ShSystemWidget.TEXT).getId());

            ShPostType postType = new ShPostType();
            postType.setName("PT-FUT-" + UUID.randomUUID());
            postType.setTitle("Folder Test Post Type");
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
            postType.setShPostTypeAttrs(java.util.Set.of(titleAttr));

            entityManager.persist(postType);
            postTypeId = postType.getId();
        });
    }

    @Test
    void stage01ListSiteShowsRootFolder() throws Exception {
        mockMvc.perform(get("/api/v2/object/" + siteId + "/list").principal(mockPrincipal))
                .andExpect(status().isOk())
                // SH120: the listing is the CDA envelope — folderId/folders/posts, no entity names.
                .andExpect(jsonPath("$.folderId").value(siteId))
                .andExpect(jsonPath("$.folders[?(@.id == '" + rootFolderId + "')]").exists());
    }

    @Test
    void stage02CreateFolderUnderRoot() throws Exception {
        ShFolder payload = new ShFolder();
        payload.setName("Docs");

        String response = mockMvc.perform(post("/api/v2/folder/" + rootFolderId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Docs"))
                .andExpect(jsonPath("$.rootFolder").value(0))
                .andReturn().getResponse().getContentAsString();

        childFolderId = objectMapper.readTree(response).get("id").asString();
    }

    @Test
    void stage03CreateFolderUnknownParentReturnsNotFound() throws Exception {
        ShFolder payload = new ShFolder();
        payload.setName("Orphan");
        mockMvc.perform(post("/api/v2/folder/" + UUID.randomUUID())
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound());
    }

    @Test
    void stage04GetFolder() throws Exception {
        mockMvc.perform(get("/api/v2/folder/" + childFolderId).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(childFolderId))
                .andExpect(jsonPath("$.name").value("Docs"));
    }

    @Test
    void stage05FolderPathHasBreadcrumbAndSite() throws Exception {
        mockMvc.perform(get("/api/v2/folder/" + childFolderId + "/path").principal(mockPrincipal))
                .andExpect(status().isOk())
                // SH120: /path returns the CDA's own ShCdaPath, so the site is `site`, not `shSite`.
                .andExpect(jsonPath("$.site.id").value(siteId))
                .andExpect(jsonPath("$.currentFolder.id").value(childFolderId))
                // breadcrumb is root-first and includes the current folder.
                .andExpect(jsonPath("$.breadcrumb[0].id").value(rootFolderId))
                .andExpect(jsonPath("$.breadcrumb[1].id").value(childFolderId));
    }

    @Test
    void stage06UpdateFolderRename() throws Exception {
        ShFolder payload = new ShFolder();
        payload.setName("Documents");
        mockMvc.perform(put("/api/v2/folder/" + childFolderId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Documents"))
                .andExpect(jsonPath("$.furl").value("documents"));
    }

    @Test
    void stage07ListFolderShowsPostsAndClone() throws Exception {
        String postId = createPost(childFolderId, "First Post");

        // List shows the post.
        mockMvc.perform(get("/api/v2/object/" + childFolderId + "/list").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[?(@.id == '" + postId + "')]").exists());

        // Clone (copy to same folder) yields a second post in the folder.
        mockMvc.perform(put("/api/v2/object/copyto/" + childFolderId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + postId + "\"]"))
                .andExpect(status().isOk())
                // SH1101 — the copy answers with what it made, so the caller can address it next.
                // It used to answer `[]` for a post, which is why the visual editor's Duplicate
                // could only place the same section twice.
                .andExpect(jsonPath("$.posts.length()").value(1))
                .andExpect(jsonPath("$.posts[0].id").isNotEmpty())
                .andExpect(jsonPath("$.posts[0].id").value(org.hamcrest.Matchers.not(postId)))
                .andExpect(jsonPath("$.posts[0].address").isNotEmpty())
                .andExpect(jsonPath("$.folders").isArray());

        mockMvc.perform(get("/api/v2/object/" + childFolderId + "/list").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts.length()").value(2));
    }

    @Test
    void stage08MovePostToRoot() throws Exception {
        String postId = createPost(childFolderId, "Movable Post");

        mockMvc.perform(put("/api/v2/object/moveto/" + rootFolderId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + postId + "\"]"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v2/object/" + rootFolderId + "/list").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[?(@.id == '" + postId + "')]").exists());
    }

    /**
     * SH139: a move relocates, and changes nothing else. The console path used to
     * re-slug {@code furl} from the title on every move, so dragging a page between
     * folders silently changed its live URL and discarded a hand-set one.
     */
    @Test
    void stage08bMovePostKeepsHandSetUrl() throws Exception {
        String postId = createPost(childFolderId, "Renamed Later");
        setFurl(postId, "the-url-a-human-chose");

        mockMvc.perform(put("/api/v2/object/moveto/" + rootFolderId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + postId + "\"]"))
                .andExpect(status().isOk());

        // Every state row moved together and every one kept the URL (SH134's rule,
        // now shared by the console because it delegates to the same writer).
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> shPostUnifiedRepository.findById(postId)
                .forEach(row -> {
                    assertThat(row.getFurl())
                            .isEqualTo("the-url-a-human-chose");
                    assertThat(row.getShFolder().getId())
                            .isEqualTo(rootFolderId);
                }));
    }

    /** SH139: a folder move keeps its own {@code furl} for the same reason. */
    @Test
    void stage08cMoveFolderKeepsHandSetUrl() throws Exception {
        String folderId = createFolderWithFurl("Guides", "hand-set-guides");

        mockMvc.perform(put("/api/v2/object/moveto/" + rootFolderId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + folderId + "\"]"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v2/folder/" + folderId).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.furl").value("hand-set-guides"));
    }

    /**
     * SH139: keeping the URL makes a collision possible on the one move that crosses
     * a site boundary. It is refused, not quietly uniquified — mirroring the agent
     * path's {@code MoveTargetOccupiedException}.
     */
    @Test
    void stage08dMoveIntoOccupiedUrlIsRefused() throws Exception {
        String occupied = "contested-url";
        String sitting = createPost(rootFolderId, "Sitting Post");
        setFurl(sitting, occupied);

        String otherSiteRootId = createSiteWithRoot("ut-folder-site-b-");
        String incoming = createPost(otherSiteRootId, "Incoming Post");
        setFurl(incoming, occupied);

        mockMvc.perform(put("/api/v2/object/moveto/" + rootFolderId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + incoming + "\"]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fix").exists());

        // Refused means nothing moved — the post is still in its own site.
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> shPostUnifiedRepository.findById(incoming)
                .forEach(row -> assertThat(row.getShFolder().getId())
                        .isEqualTo(otherSiteRootId)));
    }

    /**
     * SH137: the console's move dialog navigates <b>down</b> from the site root, so a curator
     * can reach a child of the folder they are dragging in three clicks. Before the shared
     * writer, that succeeded and produced a cycle — the subtree detached from the root and
     * the recursive delete walked the loop until the stack ran out.
     */
    @Test
    void stage08eMoveFolderIntoItsOwnChildIsRefused() throws Exception {
        String outerId = createFolderWithFurl("Outer", "outer");
        String innerId = createChildFolder(outerId, "Inner");

        mockMvc.perform(put("/api/v2/object/moveto/" + innerId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + outerId + "\"]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fix").exists());

        // Still reachable from where it was, which is what a cycle destroys.
        mockMvc.perform(get("/api/v2/folder/" + outerId).principal(mockPrincipal))
                .andExpect(status().isOk());
    }

    /**
     * SH1078: a move answers with the published addresses it retired and created. Inside one site
     * a post keeps its friendly URL (SH139), so nothing a visitor uses moves and the list is empty.
     */
    @Test
    void stage08fMoveWithinASiteReportsNoRouteChange() throws Exception {
        String postId = createPublishedPost(childFolderId, "Stays Reachable");
        setFurl(postId, "/sh1078-stays");

        mockMvc.perform(put("/api/v2/object/moveto/" + rootFolderId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + postId + "\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moved").isArray())
                .andExpect(jsonPath("$.routes.length()").value(0));
    }

    /** SH1078: crossing into another site is the move that retires an address and creates one. */
    @Test
    void stage08gMoveAcrossSitesReportsTheAddressItRetiredAndCreated() throws Exception {
        String otherRootId = createSiteWithRoot("ut-sh1078-");
        String postId = createPublishedPost(otherRootId, "Crossing Over");
        setFurl(postId, "/sh1078-crossing");

        mockMvc.perform(put("/api/v2/object/moveto/" + rootFolderId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + postId + "\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes.length()").value(1))
                .andExpect(jsonPath("$.routes[0].retired")
                        .value(org.hamcrest.Matchers.startsWith("post:ut-sh1078-")))
                .andExpect(jsonPath("$.routes[0].retired")
                        .value(org.hamcrest.Matchers.endsWith("/sh1078-crossing")))
                .andExpect(jsonPath("$.routes[0].created")
                        .value("post:" + siteName + "/sh1078-crossing"));

        // And delivery agrees with the answer: the page now lives in this site.
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> shPostUnifiedRepository.findById(postId)
                .forEach(row -> assertThat(row.getShSite().getId())
                        .as("SH1078: the created address names the site delivery now resolves")
                        .isEqualTo(siteId)));
    }

    /**
     * SH1272 — the listing answers "is this published" the way delivery does. Every publish writes a
     * PUBLISHED row beside the draft and leaves the draft's own columns at DRAFT, and the listing
     * prefers the draft, so a site an agent had just applied and published read as all drafts.
     */
    @Test
    @org.junit.jupiter.api.DisplayName("SH1272: after any publish the listing says published, and says when edits wait")
    void stage08hListingSaysPublishedAfterAPublish() throws Exception {
        String folderId = createFolderWithFurl("Sh1272", "sh1272");
        String never = createPost(folderId, "Never Published");
        String live = createPost(folderId, "Published As Is");
        String edited = createPost(folderId, "Published Then Edited");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        // The one method every publish passes through: the console's, REST's, the scheduler's and
        // the agent batch's `post.publish`.
        tx.executeWithoutResult(status -> {
            shPostUnifiedWriteService.publish(live, "admin");
            shPostUnifiedWriteService.publish(edited, "admin");
        });
        tx.executeWithoutResult(status -> shPostUnifiedRepository
                .findByIdAndState(edited, ShPostState.DRAFT).ifPresent(draft -> {
                    draft.setData("{\"" + ShSystemPostTypeAttr.TITLE + "\":\"Edited since\"}");
                    shPostUnifiedRepository.save(draft);
                }));

        mockMvc.perform(get("/api/v2/object/" + folderId + "/list").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[?(@.id == '" + never + "')].publishStatus").value("DRAFT"))
                .andExpect(jsonPath("$.posts[?(@.id == '" + never + "')].published").value(false))
                .andExpect(jsonPath("$.posts[?(@.id == '" + live + "')].publishStatus")
                        .value("PUBLISHED"))
                .andExpect(jsonPath("$.posts[?(@.id == '" + live + "')].published").value(true))
                .andExpect(jsonPath("$.posts[?(@.id == '" + edited + "')].publishStatus")
                        .value("CHANGED"))
                .andExpect(jsonPath("$.posts[?(@.id == '" + edited + "')].published").value(true));
    }

    // SH954 — `stage09ClearCacheIsOk` was here, asserting that `/clear-cache` answered 200. It did,
    // and that was the defect: the endpoint's whole body was `ResponseEntity.ok().build()` long
    // after the page cache it cleared had been removed, and the console's Clear Cache button
    // reported success on every press. A test whose subject is that a no-op returns 200 is a test
    // holding a lie in place — it goes with the endpoint rather than being retargeted.

    @Test
    void stage10DeleteFolderCascadesPosts() throws Exception {
        // childFolder still has the original post + its clone; delete must remove them too.
        mockMvc.perform(delete("/api/v2/folder/" + childFolderId).principal(mockPrincipal))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v2/folder/" + childFolderId).principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }

    /** Stands in for a hand-set URL: what a curator typed, or an import brought in. */
    private void setFurl(String postId, String furl) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> shPostUnifiedRepository.findById(postId)
                .forEach(row -> {
                    row.setFurl(furl);
                    shPostUnifiedRepository.save(row);
                }));
    }

    private String createFolderWithFurl(String name, String furl) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            ShFolder parent = entityManager.find(ShFolder.class, rootFolderId);
            ShFolder folder = new ShFolder();
            folder.setName(name);
            folder.setFurl(furl);
            folder.setRootFolder((byte) 0);
            folder.setParentFolder(parent);
            folder.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            folder.setOwner("admin");
            entityManager.persist(folder);
            return folder.getId();
        });
    }

    private String createChildFolder(String parentId, String name) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            ShFolder folder = new ShFolder();
            folder.setName(name);
            folder.setFurl(name.toLowerCase(java.util.Locale.ROOT));
            folder.setRootFolder((byte) 0);
            folder.setParentFolder(entityManager.find(ShFolder.class, parentId));
            folder.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            folder.setOwner("admin");
            entityManager.persist(folder);
            return folder.getId();
        });
    }

    /** A second site, so the one move that can genuinely collide is reachable. */
    private String createSiteWithRoot(String namePrefix) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            ShSite site = new ShSite();
            site.setName(namePrefix + UUID.randomUUID());
            site.setDescription("Second site");
            site.setUrl("http://other.test");
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
            return root.getId();
        });
    }

    private String createPost(String folderId, String title) {
        return createPost(folderId, title, ShPostState.DRAFT);
    }

    /** SH1078 — a page a visitor can reach, which is the only kind a route delta is about. */
    private String createPublishedPost(String folderId, String title) {
        return createPost(folderId, title, ShPostState.PUBLISHED);
    }

    private String createPost(String folderId, String title, ShPostState state) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put(ShSystemPostTypeAttr.TITLE, title);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> shPostUnifiedWriteService.create(
                new CreateRequest(folderId, postTypeId, data, state, "admin", null, null)).getId());
    }
}
