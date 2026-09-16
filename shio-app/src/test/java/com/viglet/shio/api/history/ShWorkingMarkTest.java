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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentReadService;
import com.viglet.shio.api.agent.ShAgentReadTerse;
import com.viglet.shio.api.agent.ShAgentRunService;
import com.viglet.shio.api.agent.dto.ShAgentDoc;
import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;
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
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH1008 — awareness, not co-editing.
 *
 * <h2>The defect</h2>
 *
 * <p>SH985 makes a conflicting save fail loudly instead of silently, and it arrives late: the
 * curator finds out after writing the paragraph. Nothing said, before either party typed, that the
 * other was on the page — and on a Shio page the other party is usually a machine.</p>
 *
 * <h2>What is asserted, and the one that matters most</h2>
 *
 * <p>The mark appears, names the run and what it said it was for, and stops being true on its own.
 * But the assertion this task is really about is the last one: <b>a mark is not a lock.</b> Derived
 * presence becomes a lock the moment any write path consults it — and it would be the worst kind,
 * held by whoever wrote last and released by nobody. So a write goes through while a mark is
 * showing, and a scan holds the list of files allowed to read this service, because the functional
 * test can only prove the write path does not consult it <em>today</em>.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShWorkingMarkTest {

    private static final String BODY = "body";
    private static final String RUN = "sh1008-run-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String CONSOLE = "console:ana:2026-09-16";

    @Autowired
    private ShWorkingService shWorkingService;
    @Autowired
    private ShAgentReadService shAgentReadService;
    @Autowired
    private ShAgentRunService shAgentRunService;
    @Autowired
    private ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private ShHistoryRepository shHistoryRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> written = new HashSet<>();
    private final Set<String> marks = new HashSet<>();
    private String siteName;
    private String folderId;
    private String postTypeId;
    private String postId;

    @BeforeAll
    void setup() {
        siteName = "sh1008-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(siteName);
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
            postType.setName("PT-SH1008-" + UUID.randomUUID());
            postType.setTitle("SH1008 Type");
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

        ShPostUnified created = shPostUnifiedWriteService.create(new CreateRequest(
                folderId, postTypeId, data("Home", "first"), ShPostState.DRAFT, "admin",
                null, null, null));
        postId = created.getId();
        written.add(postId);
        shAgentRunService.introduce(RUN, "claude-code", "2.1.0", ShAgentRunService.MCP, "ana");
        shAgentRunService.declare(RUN, "translating the site to pt-BR", "ana");
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            marks.forEach(id -> shHistoryRepository.findById(id)
                    .ifPresent(shHistoryRepository::delete));
            written.forEach(id ->
                    shPostUnifiedRepository.findById(id).forEach(shPostUnifiedRepository::delete));
        });
    }

    private static ShPostTypeAttr attr(ShPostType postType, ShWidget widget, String name,
                                       int ordinal, boolean title) {
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

    private ObjectNode data(String title, String body) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put(ShSystemPostTypeAttr.TITLE, title);
        node.put(BODY, body);
        return node;
    }

    /** One audit row, stamped where the caller says — which is the whole of a mark. */
    private String wroteAt(String session, Instant when) {
        String id = "sh1008-" + UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShHistory row = new ShHistory();
            row.setId(id);
            row.setAction(ShContentAction.UPDATE.name());
            row.setSubjectKind(ShContentSubject.POST.name());
            row.setShObject(postId);
            row.setOwner("ana");
            row.setAgentSessionId(session);
            row.setDate(when);
            row.setDescription("Updated \"Home\"");
            shHistoryRepository.save(row);
        });
        marks.add(id);
        return id;
    }

    private void forget(String id) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                shHistoryRepository.findById(id).ifPresent(shHistoryRepository::delete));
        marks.remove(id);
    }

    @Test
    @DisplayName("SH1008: a run that wrote here a moment ago is on this page, and says what for")
    void aRecentWriteIsAMark() {
        String row = wroteAt(RUN, Instant.now().minusSeconds(5));
        try {
            assertThat(shWorkingService.on(postId)).hasValueSatisfying(mark -> {
                assertThat(mark.session()).isEqualTo(RUN);
                assertThat(mark.agentName()).isEqualTo("claude-code");
                assertThat(mark.intent())
                        .as("SH1008: \"claude-code is here\" is a fact; \"claude-code is here, "
                                + "translating the site\" is a reason to wait or to go elsewhere")
                        .isEqualTo("translating the site to pt-BR");
                assertThat(mark.until())
                        .as("SH1008: the end is carried, so a screen clears the line on its own "
                                + "rather than waiting for news that has no reason to arrive")
                        .isEqualTo(mark.at().plus(ShWorkingService.WINDOW));
            });
        } finally {
            forget(row);
        }
    }

    @Test
    @DisplayName("SH1008: the window passing is the whole of the expiry — nothing releases a mark")
    void itExpiresOnItsOwn() {
        String row = wroteAt(RUN, Instant.now().minus(ShWorkingService.WINDOW).minusSeconds(5));
        try {
            assertThat(shWorkingService.on(postId))
                    .as("SH1008: a declared presence outlives the session that declared it exactly "
                            + "when the session died badly, which is the case it was added for — "
                            + "this one has no heartbeat to miss and no release to forget")
                    .isEmpty();
        } finally {
            forget(row);
        }
    }

    @Test
    @DisplayName("SH1008: a curator's own publish is not an agent on the page")
    void aConsoleSessionIsNotAMark() {
        String row = wroteAt(CONSOLE, Instant.now().minusSeconds(5));
        try {
            assertThat(shWorkingService.on(postId))
                    .as("SH1008: a console session is a curator publishing their own work (SH996), "
                            + "and a line telling them an agent is here because they pressed "
                            + "Publish is the one thing this must not say")
                    .isEmpty();
        } finally {
            forget(row);
        }
    }

    @Test
    @DisplayName("SH1008: the read carries it only when asked, and only while it is true")
    void theReadCarriesIt() {
        String address = "post:" + siteName + "/home";
        List<ShAgentDoc> without = shAgentReadService.read(List.of(address), List.of(), false,
                false, null, false, false, false, false, false);
        assertThat(without.get(0).working())
                .as("SH1008: opt-in like every other rider on this read — a page nobody else is on "
                        + "reads exactly as it always did (P3)")
                .isNull();

        String row = wroteAt(RUN, Instant.now().minusSeconds(5));
        try {
            List<ShAgentDoc> with = shAgentReadService.read(List.of(address), List.of(), false,
                    false, null, false, false, false, false, false, true);
            assertThat(with.get(0).working()).isNotNull()
                    .satisfies(mark -> assertThat(mark.agentName()).isEqualTo("claude-code"));
            assertThat(ShAgentReadTerse.docs(with))
                    .as("SH1008: a session reading terse learns somebody else is here, or it does "
                            + "not learn it at all")
                    .contains("working claude-code")
                    .contains("for=\"translating the site to pt-BR\"");
        } finally {
            forget(row);
        }
    }

    @Test
    @DisplayName("SH1008: a mark is not a lock — the write goes through while it is showing")
    void aMarkRefusesNothing() {
        String row = wroteAt(RUN, Instant.now().minusSeconds(5));
        try {
            assertThat(shWorkingService.on(postId)).isPresent();

            ShPostUnified saved = shPostUnifiedWriteService.update(postId,
                    new UpdateRequest(data("Home", "second"), ShPostState.DRAFT, "ana"));

            assertThat(saved.getTitle())
                    .as("SH1008: presence derived from a timestamp and consulted by a write would "
                            + "be a lock held by whoever wrote last and released by nobody — the "
                            + "concurrency contract stays SH985's expect, and only that")
                    .isEqualTo("Home");
        } finally {
            forget(row);
        }
    }

    @Test
    @DisplayName("SH1008: nothing on a write path may read this, and the list of readers is held")
    void onlyReadersReadIt() throws IOException {
        Path main = Path.of("src", "main", "java", "com", "viglet", "shio");
        List<String> readers;
        try (Stream<Path> files = Files.walk(main)) {
            readers = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            // The shared stripper, so a `{@link ShWorkingService}` in somebody's
                            // javadoc is not read as a call — which is the difference between a
                            // reader and a sentence about one (SH613).
                            return ShSourceText.code(Files.readString(path))
                                    .contains("ShWorkingService");
                        } catch (IOException failure) {
                            throw new IllegalStateException(failure);
                        }
                    })
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }

        assertThat(readers)
                .as("SH1008: the functional test proves the write path does not consult this TODAY; "
                        + "this is what fails the build the day one does. A mark that could refuse "
                        + "a save is a lock derived from a timestamp — add a reader here only after "
                        + "asking whether it decides anything")
                .containsExactly("ShAgentReadService.java", "ShHistoryAPI.java",
                        "ShWorkingService.java");
    }
}
