/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.security.Principal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.api.agent.ShAgentChangesService;
import com.viglet.shio.api.agent.ShAgentChangesTerse;
import com.viglet.shio.api.agent.dto.ShAgentChanges;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.UpdateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.revision.ShPostRevisionRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH989 — an approved run can still be described after the approve.
 *
 * <p>The defect's own sentence is the first test. An agent edits a published page; the curator
 * approves, which publishes; and now the draft and the live row are the same document, so the review
 * queue's comparison shows nothing. Before this the audit trail said {@code Published "Home"} and
 * nothing else, so the one thing a reviewer most wanted to see was gone at the moment of acceptance.
 * The publish row now names the fields it put live, read off the row rather than recomputed — which
 * is the only way it could be answered at all.</p>
 *
 * <p>The P3 half is the byte-identity of the default row: a feed read that does not ask for the names
 * does not carry them, in either encoding.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShAuditChangedFieldsTest {

    private static final String BODY = "body";

    @Autowired
    private ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private ShAgentChangesService shAgentChangesService;
    @Autowired
    private ShHistoryRepository shHistoryRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private ShPostRevisionRepository shPostRevisionRepository;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private WebApplicationContext webApplicationContext;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> written = new HashSet<>();
    private String siteName;
    private String folderId;
    private String postTypeId;

    @BeforeAll
    void setup() {
        siteName = "sh989-site-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDescription("SH989 site");
            site.setUrl("http://example.test");
            site.setDate(when);
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);
            folderId = root.getId();

            ShWidget text = entityManager.find(ShWidget.class,
                    shWidgetRepository.findFirstByName(ShSystemWidget.TEXT).getId());
            ShPostType postType = new ShPostType();
            postType.setName("PT-SH989-" + UUID.randomUUID());
            postType.setTitle("SH989 Type");
            postType.setSystem((byte) 0);
            postType.setDate(when);
            postType.setOwner("admin");
            Set<ShPostTypeAttr> attrs = new HashSet<>();
            attrs.add(attr(postType, text, ShSystemPostTypeAttr.TITLE, 1, true));
            attrs.add(attr(postType, text, BODY, 2, false));
            postType.setShPostTypeAttrs(attrs);
            entityManager.persist(postType);
            postTypeId = postType.getId();
        });
    }

    private static ShPostTypeAttr attr(ShPostType postType, ShWidget widget, String name, int ordinal,
                                       boolean title) {
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName(name);
        attr.setLabel(name);
        attr.setOrdinal(ordinal);
        attr.setRequired((byte) 0);
        attr.setIsTitle((byte) (title ? 1 : 0));
        attr.setIsSummary((byte) 0);
        attr.setShWidget(widget);
        attr.setShPostType(postType);
        return attr;
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> written.forEach(id -> {
            shPostRevisionRepository.deleteAll(shPostRevisionRepository.findByPostIdOrderByVersionNoDesc(id));
            shPostUnifiedRepository.findById(id).forEach(shPostUnifiedRepository::delete);
        }));
    }

    private ObjectNode data(String title, String body) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put(ShSystemPostTypeAttr.TITLE, title);
        node.put(BODY, body);
        return node;
    }

    private String createDraft(String title, String body) {
        ShPostUnified created = shPostUnifiedWriteService.create(new CreateRequest(
                folderId, postTypeId, data(title, body), ShPostState.DRAFT, "admin", null, null, null));
        written.add(created.getId());
        return created.getId();
    }

    /** The post's audit rows, oldest first. */
    private List<ShHistory> trail(String postId) {
        return shHistoryRepository.findByShObjectOrderByDateDesc(postId, PageRequest.of(0, 50))
                .stream()
                .sorted(java.util.Comparator.comparing(ShHistory::getDate))
                .toList();
    }

    private static ShHistory last(List<ShHistory> trail, ShContentAction action) {
        return trail.stream().filter(row -> action.name().equals(row.getAction()))
                .reduce((first, second) -> second).orElseThrow();
    }

    @Test
    @DisplayName("SH989: after an approve, the publish row still names what went live")
    void anApprovedRunCanBeDescribedAfterTheApprove() {
        String postId = createDraft("Home", "first");
        shPostUnifiedWriteService.publish(postId, "admin");

        // The run: one field edited on the draft of a live page.
        shPostUnifiedWriteService.update(postId,
                new UpdateRequest(data("Home", "second"), ShPostState.DRAFT, "agent"));
        // The approve.
        shPostUnifiedWriteService.publish(postId, "curator");

        String draft = shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
                .orElseThrow().getData();
        String live = shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED)
                .orElseThrow().getData();
        assertThat(ShChangedFields.between(draft, live))
                .as("SH989's premise: after the approve the draft and the live row are one document, "
                        + "so any comparison made now finds nothing")
                .isEmpty();

        List<ShHistory> trail = trail(postId);
        assertThat(ShChangedFields.decode(last(trail, ShContentAction.PUBLISH).getChangedFields()))
                .as("SH989: the approve's own row names the field it put live — the description of "
                        + "the accepted run, kept at the one moment both documents existed")
                .containsExactly(BODY);
        assertThat(ShChangedFields.decode(last(trail, ShContentAction.UPDATE).getChangedFields()))
                .as("SH989: and the run's edit names it too, untouched title and all")
                .containsExactly(BODY);
        assertThat(last(trail, ShContentAction.CREATE).getChangedFields())
                .as("SH989: a create is not recorded — against nothing every field is new, which says "
                        + "nothing a reader does not already know")
                .isNull();
    }

    @Test
    @DisplayName("SH989: a first publish names every field, and a no-op save names none")
    void aFirstPublishNamesEverythingAndANoOpSaveNothing() {
        String postId = createDraft("About", "text");
        shPostUnifiedWriteService.publish(postId, "admin");
        shPostUnifiedWriteService.update(postId,
                new UpdateRequest(data("About", "text"), ShPostState.DRAFT, "admin"));

        List<ShHistory> trail = trail(postId);
        assertThat(ShChangedFields.decode(last(trail, ShContentAction.PUBLISH).getChangedFields()))
                .containsExactlyInAnyOrder(BODY, ShSystemPostTypeAttr.TITLE)
                .isSorted();
        assertThat(last(trail, ShContentAction.UPDATE).getChangedFields())
                .as("SH989: recorded, and empty — a save that changed nothing says so, which is a "
                        + "different statement from a row that recorded nothing")
                .isEqualTo("[]");
    }

    @Test
    @DisplayName("SH989: a restore names what it put back")
    void aRestoreNamesWhatItPutBack() {
        String postId = createDraft("Contact", "one");
        shPostUnifiedWriteService.publish(postId, "admin");
        shPostUnifiedWriteService.update(postId,
                new UpdateRequest(data("Contact us", "one"), ShPostState.DRAFT, "admin"));
        shPostUnifiedWriteService.restoreDraftFromPublished(postId, "admin");

        assertThat(ShChangedFields.decode(last(trail(postId), ShContentAction.RESTORE).getChangedFields()))
                .containsExactly(ShSystemPostTypeAttr.TITLE);
    }

    @Test
    @DisplayName("SH989: the default feed row is byte-identical, and include=fields adds the names")
    void theDefaultRowDoesNotCarryTheNames() throws Exception {
        String postId = createDraft("Pricing", "cheap");
        shPostUnifiedWriteService.publish(postId, "admin");
        shPostUnifiedWriteService.update(postId,
                new UpdateRequest(data("Pricing", "cheaper"), ShPostState.DRAFT, "admin"));

        ShAgentChanges plain = shAgentChangesService.changes(siteName, null, 500, false, 0);
        ShAgentChanges named = shAgentChangesService.changes(siteName, null, 500, false, 0, true);

        assertThat(plain.changes()).allSatisfy(change -> assertThat(change.fields()).isNull());
        assertThat(objectMapper.writeValueAsString(plain))
                .as("SH989: P3 — a caller that did not ask pays nothing for the names")
                .doesNotContain("\"fields\"");
        String terse = ShAgentChangesTerse.render(plain);
        assertThat(terse).doesNotContain(" fields=");
        assertThat(ShAgentChangesTerse.render(named).replaceAll(" fields=\\S+", ""))
                .as("SH989: the opt-in adds one attribute to the event lines and moves nothing else")
                .isEqualTo(terse);

        assertThat(named.changes()).anySatisfy(change -> {
            assertThat(change.action()).isEqualTo(ShContentAction.UPDATE.name());
            assertThat(change.fields()).containsExactly(BODY);
        });

        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        Principal principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn("admin");
        String body = mockMvc.perform(get("/api/v2/agent/changes").principal(principal)
                        .param("site", siteName).param("include", "fields").param("format", "terse"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body)
                .as("SH989: include=fields reaches the wire, in the encoding the MCP tool shares")
                .contains("fields=" + BODY + "\n");
    }
}
