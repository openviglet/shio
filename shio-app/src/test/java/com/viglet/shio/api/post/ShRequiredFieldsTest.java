/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.post.unified.ShConsolePublishPlan;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.UpdateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
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
 * SH1009 — required means required, on every writer.
 *
 * <h2>The defect</h2>
 *
 * <p>The console's form painted the asterisk and posted the data unconditionally. REST's create and
 * update validated the post-type name and nothing else. The agent's batch refused an <em>undeclared</em>
 * key and never an empty required one. The only enforcement in the product was
 * {@code required-field-empty} in {@code shio verify} — after the fact, and only if somebody ran it —
 * so a page missing its title published, served, and reached a reviewer looking complete.</p>
 *
 * <h2>Why a draft is not refused</h2>
 *
 * <p>Drafts are where work happens. A rule that refused an incomplete draft would make the product
 * worse than the defect: a curator could not save a page they had half written, and an agent could
 * not build one in two ops. The line is the publish — the moment the page stops being somebody's
 * work in progress and becomes what a visitor is served.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShRequiredFieldsTest {

    private static final String BODY = "body";
    private static final String CAPTION = "caption";

    @Autowired
    private ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShConsolePublishPlan shConsolePublishPlan;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    /** The instance's own mapper, because a batch body is parsed the way the surface parses it. */
    @Autowired
    private ObjectMapper batchMapper;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> written = new HashSet<>();
    private String siteName;
    private String folderId;
    private String postTypeId;

    @BeforeAll
    void setup() {
        siteName = "sh1009-" + UUID.randomUUID().toString().substring(0, 8);
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
            ShWidget tab = entityManager.find(ShWidget.class,
                    shWidgetRepository.findFirstByName(ShSystemWidget.TAB).getId());
            ShPostType postType = new ShPostType();
            postType.setName("PT-SH1009-" + UUID.randomUUID());
            postType.setTitle("SH1009 Type");
            postType.setSystem((byte) 0);
            postType.setDate(when);
            postType.setOwner("admin");
            Set<ShPostTypeAttr> attrs = new HashSet<>();
            attrs.add(attr(postType, text, ShSystemPostTypeAttr.TITLE, 1, true, true));
            // Required and empty on every post this test writes — the second name in the refusal.
            attrs.add(attr(postType, text, BODY, 2, false, true));
            attrs.add(attr(postType, text, CAPTION, 3, false, false));
            // SH1009 — a container declared required must not refuse anything: it holds no value of
            // its own, so requiring it would refuse every post of a type that declares one.
            attrs.add(attr(postType, tab, "layout", 4, false, true));
            postType.setShPostTypeAttrs(attrs);
            entityManager.persist(postType);
            postTypeId = postType.getId();
        });
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                written.forEach(id ->
                        shPostUnifiedRepository.findById(id).forEach(shPostUnifiedRepository::delete)));
    }

    private static ShPostTypeAttr attr(ShPostType postType, ShWidget widget, String name,
                                       int ordinal, boolean title, boolean required) {
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName(name);
        attr.setLabel(name);
        attr.setOrdinal(ordinal);
        attr.setRequired((byte) (required ? 1 : 0));
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

    /** A draft with whatever this test wants in it. Never refused — that is the point. */
    private String draft(String title, String body) {
        ShPostUnified created = shPostUnifiedWriteService.create(new CreateRequest(
                folderId, postTypeId, data(title, body), ShPostState.DRAFT, "admin",
                null, null, null));
        written.add(created.getId());
        return created.getId();
    }

    @Test
    @DisplayName("SH1009: a half-written draft saves, because a draft is where work happens")
    void anIncompleteDraftIsSaved() {
        String postId = draft("Half a page", "  ");

        assertThat(shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT))
                .as("SH1009: refusing an incomplete draft would be worse than the defect — a "
                        + "curator could not save a page they had half written, and an agent could "
                        + "not build one in two ops")
                .isPresent();
        assertThatCode(() -> shPostUnifiedWriteService.update(postId,
                new UpdateRequest(data("Half a page", ""), ShPostState.DRAFT, "admin")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SH1009: publishing it is refused, at the field, with a fix that names it")
    void publishingAnIncompletePageIsRefused() {
        String postId = draft("Half a page", "   ");

        assertThatThrownBy(() -> shPostUnifiedWriteService.publish(postId, "admin"))
                .isInstanceOf(ShRequiredFields.RequiredFieldEmptyException.class)
                .satisfies(failure -> {
                    var problem = ((ShRequiredFields.RequiredFieldEmptyException) failure)
                            .problem().toProblemDetail();
                    assertThat(problem.getProperties().get("at"))
                            .as("SH1009: `at` is data.<field>, which is what the console's problem "
                                    + "reader maps onto the input — the curator lands in the box to "
                                    + "type in rather than reading a sentence about a page with "
                                    + "sixteen of them")
                            .isEqualTo("data." + BODY);
                    assertThat(problem.getProperties().get("fix").toString()).contains(BODY);
                });

        assertThat(shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED))
                .as("SH1009: refused means nothing went live")
                .isEmpty();
    }

    @Test
    @DisplayName("SH1009: whitespace is empty, which is what a converted page leaves behind")
    void whitespaceIsEmpty() {
        assertThat(ShRequiredFields.isEmpty(objectMapper.getNodeFactory().stringNode("   ")))
                .as("SH1009: a whitespace-only title satisfies 'a value is present' and is not a "
                        + "title")
                .isTrue();
        assertThat(ShRequiredFields.isEmpty(objectMapper.getNodeFactory().arrayNode())).isTrue();
        assertThat(ShRequiredFields.isEmpty(objectMapper.getNodeFactory().stringNode("x"))).isFalse();
    }

    @Test
    @DisplayName("SH1009: filling it in publishes, so the refusal is about the page and not the type")
    void completingItPublishes() {
        String postId = draft("A whole page", "the body");

        assertThatCode(() -> shPostUnifiedWriteService.publish(postId, "admin"))
                .as("SH1009: a container widget declared required is not a value, so the `layout` "
                        + "tab on this type refuses nothing — a rule that counted it would refuse "
                        + "every post of a type that declares a tab")
                .doesNotThrowAnyException();
        assertThat(shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED))
                .isPresent();
    }

    @Test
    @DisplayName("SH1009: the agent's batch refuses it too, and its plan says so without writing")
    void theBatchRefusesAndItsPlanSaysSo() {
        String postId = draft("Batch page", "");
        String address = "post:" + siteName + "/" + shPostUnifiedRepository
                .findByIdAndState(postId, ShPostState.DRAFT).orElseThrow().getFurl();

        ShAgentChangeSet plan = shAgentBatchService.run(batchMapper.readValue("""
                {"dryRun":true,"ops":[{"op":"post.publish","address":"%s"}]}
                """.formatted(address), ShAgentBatch.class), false, "ut-sh1009");

        assertThat(plan.results()).singleElement()
                .satisfies(change -> assertThat(change.detail())
                        .as("SH1009: a plan says what the apply would refuse rather than refusing, "
                                + "in SH997's shape — a plan that stopped at the first problem "
                                + "would report one op of ten")
                        .contains("refused")
                        .contains(BODY));
        assertThat(shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED))
                .as("SH1009: and a plan writes nothing")
                .isEmpty();

        assertThatThrownBy(() -> shAgentBatchService.run(batchMapper.readValue("""
                {"ops":[{"op":"post.publish","address":"%s"}]}
                """.formatted(address), ShAgentBatch.class), false, "ut-sh1009"))
                .as("SH1009: the batch reports an op's failure as its own exception carrying the "
                        + "refusal's sentence, which is why that sentence has to NAME the fields — "
                        + "'missing a field it declares it must have' sends whoever reads it to go "
                        + "and find out which")
                .hasMessageContaining(BODY)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("SH1009: the console's publish preview says it before the curator presses Publish")
    void theConsolePlanSaysSo() {
        String postId = draft("Console page", "");

        assertThat(shConsolePublishPlan.of(postId).results()).singleElement()
                .satisfies(change -> assertThat(change.detail())
                        .as("SH1009: the preview is where a curator finds out, which is the whole "
                                + "reason it exists — the alternative is pressing Publish and "
                                + "reading the refusal")
                        .contains("refused")
                        .contains(BODY));
    }

    @Test
    @DisplayName("SH1009: a person's waiver gets past it, because a gate may declare this check")
    void aRecordedWaiverPublishesAnyway() {
        String postId = draft("Waived page", "");

        assertThatCode(() -> shPostUnifiedWriteService.publish(postId, "ana",
                "gate 'launch' waived by ana: the copy lands tomorrow"))
                .as("SH1009: `required-field-empty` is a check a site's gate can declare (SH998), "
                        + "so a refusal that ignored the waiver would put a second lock on the one "
                        + "door SH998 built — and this one is refused to an agent outright and "
                        + "recorded twice, so the page goes live with a person's reason on it")
                .doesNotThrowAnyException();
        assertThat(shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED))
                .isPresent();
    }

    @Test
    @DisplayName("SH1009: a create straight to PUBLISHED is a publish, and is refused like one")
    void creatingLiveIsAPublish() {
        assertThatThrownBy(() -> shPostUnifiedWriteService.create(new CreateRequest(
                folderId, postTypeId, data("Straight to live", ""), ShPostState.PUBLISHED,
                "admin", null, null, null)))
                .as("SH1009: the one publish that does not pass through `publish` — a rule written "
                        + "only there would have a door in it")
                .isInstanceOf(ShRequiredFields.RequiredFieldEmptyException.class);
    }

    @Test
    @DisplayName("SH1009: the fields are named in declaration order, so two refusals can be diffed")
    void theOrderIsTheTypesOwn() {
        ShPostType type = new TransactionTemplate(transactionManager).execute(status -> {
            ShPostType row = entityManager.find(ShPostType.class, postTypeId);
            // Touched inside the transaction: the attributes are lazy, and a Set read after the
            // session closed is the LazyInitializationException this test would report as a
            // missing field.
            row.getShPostTypeAttrs().size();
            return row;
        });
        ObjectNode empty = objectMapper.createObjectNode();

        assertThat(ShRequiredFields.missing(type, empty))
                .as("SH1009: the attributes arrive in a Set, so without an order a refusal reorders "
                        + "itself between two identical requests")
                .containsExactly(ShSystemPostTypeAttr.TITLE, BODY);
    }
}
