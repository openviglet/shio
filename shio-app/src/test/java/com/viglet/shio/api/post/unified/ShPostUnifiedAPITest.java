/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
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

@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestMethodOrder(MethodOrderer.MethodName.class)
@TestInstance(Lifecycle.PER_CLASS)
class ShPostUnifiedAPITest {

    /**
     * A fixed reference instant: both the stored schedule payloads and the due-check
     * query derive from this same instant, so the schedule assertions stay
     * deterministic with no dependency on the system clock.
     */
    private static final Instant SCHED_REF = Instant.parse("2026-06-01T00:00:00Z");

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private com.viglet.shio.migration.unified.ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private Principal mockPrincipal;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String folderId;
    private String postTypeId;
    private String postTypeName;
    private String newPostId;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("admin");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("ut-site-" + UUID.randomUUID());
            site.setDescription("Unified API Test Site");
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

            ShWidget textWidget = entityManager.find(ShWidget.class,
                    shWidgetRepository.findFirstByName(ShSystemWidget.TEXT).getId());

            ShPostType postType = new ShPostType();
            postType.setName("PT-UT-" + UUID.randomUUID());
            postType.setTitle("Unified Test Post Type");
            postType.setDescription("Post type for unified API tests");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");

            ShPostTypeAttr titleAttr = new ShPostTypeAttr();
            titleAttr.setName(ShSystemPostTypeAttr.TITLE);
            titleAttr.setLabel("Title");
            titleAttr.setDescription("Title field");
            titleAttr.setOrdinal(1);
            titleAttr.setRequired((byte) 1);
            titleAttr.setIsTitle((byte) 1);
            titleAttr.setIsSummary((byte) 0);
            titleAttr.setShWidget(textWidget);
            titleAttr.setShPostType(postType);

            Set<ShPostTypeAttr> attrs = new HashSet<>();
            attrs.add(titleAttr);
            postType.setShPostTypeAttrs(attrs);

            entityManager.persist(postType);
            postTypeId = postType.getId();
            postTypeName = postType.getName();
        });
    }

    @AfterAll
    void tearDown() {
        if (newPostId == null) return;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status ->
                shPostUnifiedRepository.findById(newPostId).forEach(shPostUnifiedRepository::delete));
    }

    private ObjectNode createPayload(String title, String state) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("folderId", folderId);
        payload.put("postTypeId", postTypeId);
        payload.put("state", state);
        ObjectNode data = payload.putObject("data");
        data.put(ShSystemPostTypeAttr.TITLE, title);
        return payload;
    }

    @Test
    void stage01PostUnifiedListByFolderEmpty() throws Exception {
        mockMvc.perform(get("/api/v2/post-unified/folder/" + folderId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void stage02PostUnifiedCreateDraft() throws Exception {
        String body = objectMapper.writeValueAsString(createPayload("Hello", "DRAFT"));

        mockMvc.perform(post("/api/v2/post-unified")
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        newPostId = shPostUnifiedRepository.findByState(ShPostState.DRAFT).stream()
                .filter(p -> p.getShFolder() != null && folderId.equals(p.getShFolder().getId()))
                .findFirst()
                .map(ShPostUnified::getId)
                .orElseThrow(() -> new AssertionError("Created post not found in repository"));
    }

    @Test
    void stage03PostUnifiedGetDraft() throws Exception {
        mockMvc.perform(get("/api/v2/post-unified/" + newPostId)
                        .param("state", "DRAFT")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.id").value(newPostId))
                .andExpect(jsonPath("$.state").value("DRAFT"));
    }

    /**
     * SH176 — the read the console builds its form from. Both halves of the defect are
     * pinned here because either one alone empties every post edit page: the LAZY
     * {@code shPostType} arriving as {@code null}, and {@code data} arriving as the stored
     * JSON <em>string</em> instead of an object.
     */
    @Test
    void stage03bPostUnifiedGetCarriesPostTypeAndParsedData() throws Exception {
        mockMvc.perform(get("/api/v2/post-unified/" + newPostId)
                        .param("state", "DRAFT")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                // The post-type travels as its SH16 def — name, title and the fields the form projects.
                .andExpect(jsonPath("$.shPostType.name").value(postTypeName))
                .andExpect(jsonPath("$.shPostType.fields.length()").value(1))
                .andExpect(jsonPath("$.shPostType.fields[0].name").value(ShSystemPostTypeAttr.TITLE))
                .andExpect(jsonPath("$.shPostType.fields[0].widget").value(ShSystemWidget.TEXT))
                // `data` is an object: a string here reads as `undefined` in every widget.
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data." + ShSystemPostTypeAttr.TITLE).value("Hello"))
                // The breadcrumb's folder and site, the latter resolved through ShFolderSite (SH128).
                .andExpect(jsonPath("$.shFolder.id").value(folderId))
                .andExpect(jsonPath("$.shFolder.shSite.id").isNotEmpty())
                .andExpect(jsonPath("$.shSite.id").isNotEmpty())
                .andExpect(jsonPath("$.undeclaredFields.length()").value(0));
    }

    /**
     * A field the post-type does not declare is <em>named</em>, not dropped. Until SH168
     * refuses such a write, a post that already carries one must not look empty — hiding a
     * stored value is how content gets lost.
     */
    @Test
    void stage99PostUnifiedGetNamesUndeclaredFields() throws Exception {
        ObjectNode payload = createPayload("Undeclared", "DRAFT");
        ((ObjectNode) payload.get("data")).put("nosuchfield", "kept");

        String created = mockMvc.perform(post("/api/v2/post-unified")
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nosuchfield").value("kept"))
                .andExpect(jsonPath("$.undeclaredFields.length()").value(1))
                .andExpect(jsonPath("$.undeclaredFields[0]").value("nosuchfield"))
                .andReturn().getResponse().getContentAsString();

        // Purged, not just trashed: a soft-deleted row still answers `listByFolder`.
        String id = objectMapper.readTree(created).get("id").asString();
        mockMvc.perform(delete("/api/v2/post-unified/" + id).principal(mockPrincipal))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v2/post-unified/" + id + "/purge").principal(mockPrincipal))
                .andExpect(status().isNoContent());
    }

    @Test
    void stage04PostUnifiedListByFolder() throws Exception {
        mockMvc.perform(get("/api/v2/post-unified/folder/" + folderId)
                        .param("state", "DRAFT")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(newPostId));
    }

    @Test
    void stage05PostUnifiedUpdate() throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("state", "DRAFT");
        ObjectNode data = payload.putObject("data");
        data.put(ShSystemPostTypeAttr.TITLE, "Hello Updated");

        mockMvc.perform(put("/api/v2/post-unified/" + newPostId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Hello Updated"));
    }

    /**
     * SH985 — two writes to one post, the second composed against a read the first invalidated.
     *
     * <p>This is the assertion the defect earns rather than a unit test of the guard's internals:
     * it goes through the same {@code PUT} a curator's save makes, with the tag the console holds
     * from its load. Before SH985 every line here answered {@code 200} and the first writer's work
     * was gone with no signal to either party — the failure SH721 fixed for the agent and left
     * standing for the console, in the direction that costs the reviewed writer.</p>
     */
    @Test
    void stage05aWriteWithAStaleVersionIsRefused() throws Exception {
        String held = mockMvc.perform(get("/api/v2/post-unified/" + newPostId)
                        .param("state", "DRAFT")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        org.assertj.core.api.Assertions.assertThat(held)
                .as("SH985: the read hands over the tag a conditional write compares")
                .isNotBlank();

        // Somebody else writes — an agent's post.upsert, another curator's save, the same thing.
        mockMvc.perform(put("/api/v2/post-unified/" + newPostId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.IF_MATCH, held)
                        .content(objectMapper.writeValueAsString(draftTitled("Written By Somebody"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Written By Somebody"));

        // ...and the write composed against the earlier read is refused, with the instruction.
        mockMvc.perform(put("/api/v2/post-unified/" + newPostId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.IF_MATCH, held)
                        .content(objectMapper.writeValueAsString(draftTitled("Composed Before That"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fix").value(org.hamcrest.Matchers.containsString("Read it again")));

        // A publish is conditional on the draft too (SH730's subject, and the agent's).
        mockMvc.perform(post("/api/v2/post-unified/" + newPostId + "/publish")
                        .principal(mockPrincipal)
                        .header(HttpHeaders.IF_MATCH, held))
                .andExpect(status().isConflict());

        // SH1026 — and the merge a table cell's inline title edit sends, under the version its own
        // read answered with: a cell edit composed before somebody else's write is refused too.
        mockMvc.perform(patch("/api/v2/post-unified/" + newPostId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.IF_MATCH, held)
                        .content("{\"attrs\":{\"" + ShSystemPostTypeAttr.TITLE + "\":\"Edited In A Cell\"}}"))
                .andExpect(status().isConflict());

        // The unconditional write still works, which is what keeps every caller written before
        // today working: the guarantee is the console sending the header, not the server demanding it.
        mockMvc.perform(put("/api/v2/post-unified/" + newPostId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftTitled("Hello Updated"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Hello Updated"));
    }

    private ObjectNode draftTitled(String title) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("state", "DRAFT");
        payload.putObject("data").put(ShSystemPostTypeAttr.TITLE, title);
        return payload;
    }

    @Test
    void stage06PostUnifiedPublish() throws Exception {
        mockMvc.perform(post("/api/v2/post-unified/" + newPostId + "/publish")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PUBLISHED"))
                .andExpect(jsonPath("$.published").value(true));
    }

    @Test
    void stage07PostUnifiedVersions() throws Exception {
        mockMvc.perform(get("/api/v2/post-unified/" + newPostId + "/versions")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void stage07bPostUnifiedRestore() throws Exception {
        // Diverge the draft from the published snapshot...
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("state", "DRAFT");
        ObjectNode data = payload.putObject("data");
        data.put(ShSystemPostTypeAttr.TITLE, "Draft Pending Change");

        mockMvc.perform(put("/api/v2/post-unified/" + newPostId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Draft Pending Change"));

        // ...then restore the draft to the last published version.
        mockMvc.perform(post("/api/v2/post-unified/" + newPostId + "/restore")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.title").value("Hello Updated"));

        mockMvc.perform(get("/api/v2/post-unified/" + newPostId)
                        .param("state", "DRAFT")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Hello Updated"));
    }

    @Test
    void stage07cPostUnifiedRestoreUnknownReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v2/post-unified/" + UUID.randomUUID() + "/restore")
                        .principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }

    @Test
    void stage07dScheduleFuturePublishNotDue() throws Exception {
        // A future publishAt must be accepted but NOT picked up by the sweep yet.
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("publishAt", SCHED_REF.plus(1, ChronoUnit.DAYS).toEpochMilli()); // +1 day

        mockMvc.perform(post("/api/v2/post-unified/" + newPostId + "/schedule")
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishScheduledDate").exists());

        org.junit.jupiter.api.Assertions.assertFalse(
                shPostUnifiedWriteService.findScheduledPublishDue(SCHED_REF).stream()
                        .anyMatch(due -> newPostId.equals(due.id())),
                "A future schedule must not be due");
    }

    @Test
    void stage07dbScheduleAcceptsTheIsoInstantTheConsoleSends() throws Exception {
        // SH1179 — the console's schedule dialog (and SH1082's calendar) send ISO-8601 strings, the
        // shape /agent/find returns; the endpoint read only epoch millis, so every console schedule
        // was a 400 the dialog reported as a generic failure.
        Instant at = SCHED_REF.plus(2, ChronoUnit.DAYS);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("publishAt", at.toString());
        payload.putNull("unpublishAt");

        mockMvc.perform(post("/api/v2/post-unified/" + newPostId + "/schedule")
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishScheduledDate").exists());

        // Due a second after the instant and not a second before: it was stored as that instant.
        org.junit.jupiter.api.Assertions.assertTrue(
                shPostUnifiedWriteService.findScheduledPublishDue(at.plusSeconds(1)).stream()
                        .anyMatch(due -> newPostId.equals(due.id())),
                "SH1179: an ISO instant was accepted but is not due just after it");
        org.junit.jupiter.api.Assertions.assertFalse(
                shPostUnifiedWriteService.findScheduledPublishDue(at.minusSeconds(1)).stream()
                        .anyMatch(due -> newPostId.equals(due.id())),
                "SH1179: an ISO instant was stored earlier than the instant sent");
    }

    @Test
    void stage07dcScheduleRefusesAnInstantInNeitherShapeWithTheFix() throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("publishAt", "next tuesday");

        mockMvc.perform(post("/api/v2/post-unified/" + newPostId + "/schedule")
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("schedule-instant")))
                .andExpect(jsonPath("$.at").value("publishAt"))
                .andExpect(jsonPath("$.fix").exists());
    }

    @Test
    void stage07eScheduledPublishFires() throws Exception {
        // Diverge the draft, schedule a publish in the past, then run the sweep step.
        ObjectNode update = objectMapper.createObjectNode();
        update.put("state", "DRAFT");
        update.putObject("data").put(ShSystemPostTypeAttr.TITLE, "Scheduled Title");
        mockMvc.perform(put("/api/v2/post-unified/" + newPostId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("publishAt", SCHED_REF.minusSeconds(1).toEpochMilli());
        mockMvc.perform(post("/api/v2/post-unified/" + newPostId + "/schedule")
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        // SH204 — the finder returns the post's site beside its id now, so the sweep's failure
        // handler can attribute an incident without a query inside a catch.
        org.junit.jupiter.api.Assertions.assertTrue(
                shPostUnifiedWriteService.findScheduledPublishDue(SCHED_REF).stream()
                        .anyMatch(due -> newPostId.equals(due.id())),
                "A past schedule must be due");

        // Fire the due publish (what ShPostScheduler.sweep() delegates to).
        shPostUnifiedWriteService.publishScheduled(newPostId);

        mockMvc.perform(get("/api/v2/post-unified/" + newPostId)
                        .param("state", "PUBLISHED")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.title").value("Scheduled Title"));

        // Schedule field cleared, so it cannot re-fire on the next sweep.
        org.junit.jupiter.api.Assertions.assertFalse(
                shPostUnifiedWriteService.findScheduledPublishDue(SCHED_REF).stream()
                        .anyMatch(due -> newPostId.equals(due.id())),
                "publishScheduledDate must be cleared after firing");
    }

    @Test
    void stage07fScheduledUnpublishFires() throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("unpublishAt", SCHED_REF.minusSeconds(1).toEpochMilli());
        mockMvc.perform(post("/api/v2/post-unified/" + newPostId + "/schedule")
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        shPostUnifiedWriteService.unpublishScheduled(newPostId);

        // The published row is gone after a scheduled unpublish.
        mockMvc.perform(get("/api/v2/post-unified/" + newPostId)
                        .param("state", "PUBLISHED")
                        .principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }

    @Test
    void stage07gScheduleUnknownReturnsNotFound() throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("publishAt", System.currentTimeMillis());
        mockMvc.perform(post("/api/v2/post-unified/" + UUID.randomUUID() + "/schedule")
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound());
    }

    @Test
    void stage08PostUnifiedUnpublish() throws Exception {
        mockMvc.perform(post("/api/v2/post-unified/" + newPostId + "/unpublish")
                        .principal(mockPrincipal))
                .andExpect(status().isNoContent());
    }

    @Test
    void stage09PostUnifiedDelete() throws Exception {
        mockMvc.perform(delete("/api/v2/post-unified/" + newPostId)
                        .principal(mockPrincipal))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v2/post-unified/" + newPostId)
                        .param("state", "DRAFT")
                        .principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }
}
