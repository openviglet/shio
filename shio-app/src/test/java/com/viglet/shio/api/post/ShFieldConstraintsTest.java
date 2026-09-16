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

import com.viglet.shio.api.post.type.ShPostTypeAdminMapper;
import com.viglet.shio.api.post.type.dto.ShFieldConstraints;
import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.api.post.unified.ShConsolePublishPlan;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
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
 * SH1010 — a constraint vocabulary, evaluated where {@code required} already is.
 *
 * <h2>The defect</h2>
 *
 * <p>A field could declare one rule and nothing else. So a slug field accepted spaces, a rating
 * accepted eleven, a locale code accepted {@code "english"}, and every rule past presence lived in
 * somebody's head — enforced by whoever remembered it, on the surface they happened to be writing,
 * and nowhere on the other three.</p>
 *
 * <h2>The three properties worth asserting</h2>
 *
 * <p><b>An absent value is not a violation</b>, or a constraint vocabulary would make every
 * constrained field required by the back door. <b>Advice never refuses</b>, or the difference
 * between a broken document and a judgement about search results is one refactor wide. And
 * <b>nothing existing moves</b>: a post-type that declares no constraints has to round-trip byte
 * for byte, which is the property {@code shio pull}/{@code push} is built on.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShFieldConstraintsTest {

    private static final String SLUG = "slug";
    private static final String RATING = "rating";
    private static final String STAGE = "stage";
    private static final String TAGS = "tags";
    private static final String HEADLINE = "headline";

    @Autowired
    private ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private ShConsolePublishPlan shConsolePublishPlan;
    @Autowired
    private ShPostTypeAdminMapper shPostTypeAdminMapper;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private ObjectMapper serverMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> written = new HashSet<>();
    private String folderId;
    private String postTypeId;

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName("sh1010-" + UUID.randomUUID().toString().substring(0, 8));
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
            postType.setName("PT-SH1010-" + UUID.randomUUID());
            postType.setTitle("SH1010 Type");
            postType.setSystem((byte) 0);
            postType.setDate(when);
            postType.setOwner("admin");
            Set<ShPostTypeAttr> attrs = new HashSet<>();
            attrs.add(attr(postType, text, ShSystemPostTypeAttr.TITLE, 1, true, true, null));
            attrs.add(attr(postType, text, SLUG, 2, false, false,
                    "{\"pattern\":\"[a-z0-9-]+\",\"patternMessage\":\"lower-case letters, digits "
                            + "and hyphens only\",\"maxLength\":40}"));
            attrs.add(attr(postType, text, RATING, 3, false, false,
                    "{\"min\":1,\"max\":10}"));
            attrs.add(attr(postType, text, STAGE, 4, false, false,
                    "{\"values\":[\"draft\",\"review\",\"live\"]}"));
            attrs.add(attr(postType, text, TAGS, 5, false, false,
                    "{\"minItems\":1,\"maxItems\":3}"));
            attrs.add(attr(postType, text, HEADLINE, 6, false, false,
                    "{\"recommendedLength\":10}"));
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
                                       int ordinal, boolean title, boolean required,
                                       String constraints) {
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName(name);
        attr.setLabel(name);
        attr.setOrdinal(ordinal);
        attr.setRequired((byte) (required ? 1 : 0));
        attr.setIsTitle((byte) (title ? 1 : 0));
        attr.setIsSummary((byte) 0);
        attr.setShWidget(widget);
        attr.setShPostType(postType);
        attr.setConstraints(constraints);
        return attr;
    }

    /** A page with a title and whatever else this test is about. */
    private String draft(String field, Object value) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put(ShSystemPostTypeAttr.TITLE, "A page");
        if (field != null) {
            if (value instanceof Integer number) {
                data.put(field, number);
            } else if (value instanceof List<?> items) {
                var array = data.putArray(field);
                items.forEach(item -> array.add(String.valueOf(item)));
            } else {
                data.put(field, String.valueOf(value));
            }
        }
        ShPostUnified created = shPostUnifiedWriteService.create(new CreateRequest(
                folderId, postTypeId, data, ShPostState.DRAFT, "admin", null, null, null));
        written.add(created.getId());
        return created.getId();
    }

    private ShPostType postType() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            ShPostType row = entityManager.find(ShPostType.class, postTypeId);
            row.getShPostTypeAttrs().size();
            return row;
        });
    }

    private List<ShFieldRules.Violation> violations(ObjectNode data) {
        return ShFieldRules.violations(postType(), data, serverMapper);
    }

    /**
     * One field's def, mapped inside the transaction.
     *
     * <p>The mapper nests a field's children, so it touches a lazy collection: mapping outside the
     * session is the LazyInitializationException this test would otherwise report as a missing
     * constraint block.</p>
     */
    private ShPostTypeAttrDef defOf(String field) {
        return new TransactionTemplate(transactionManager).execute(status ->
                shPostTypeAdminMapper.toAttrDef(
                        entityManager.find(ShPostType.class, postTypeId).getShPostTypeAttrs()
                                .stream()
                                .filter(attr -> field.equals(attr.getName()))
                                .findFirst().orElseThrow()));
    }

    private ObjectNode document(String field, String value) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put(field, value);
        return data;
    }

    @Test
    @DisplayName("SH1010: a pattern refuses the shape, and says the model's own sentence")
    void aPatternRefusesTheShape() {
        assertThat(violations(document(SLUG, "Not A Slug"))).singleElement()
                .satisfies(violation -> {
                    assertThat(violation.rule()).isEqualTo("pattern-mismatch");
                    assertThat(violation.says())
                            .as("SH1010: a regular expression quoted back at a curator is a refusal "
                                    + "only its author can act on, which is what P6 is about")
                            .contains("lower-case letters, digits and hyphens only");
                });
        assertThat(violations(document(SLUG, "a-real-slug"))).isEmpty();
    }

    @Test
    @DisplayName("SH1010: a length, a range, a vocabulary and an item count, each with its bound")
    void everyKindReportsItsBound() {
        assertThat(violations(document(SLUG, "x".repeat(41)))).extracting(
                ShFieldRules.Violation::rule).contains("too-long");
        assertThat(violations(document(RATING, "11"))).singleElement()
                .satisfies(violation -> {
                    assertThat(violation.rule()).isEqualTo("above-maximum");
                    assertThat(violation.fix())
                            .as("SH1010: 10 rather than 10.0 — the bound as somebody typed it into "
                                    + "the model")
                            .contains("10 or less");
                });
        assertThat(violations(document(RATING, "not a number"))).extracting(
                ShFieldRules.Violation::rule).contains("not-a-number");
        assertThat(violations(document(STAGE, "shipped"))).singleElement()
                .satisfies(violation -> assertThat(violation.fix())
                        .contains("draft, review, live"));

        ObjectNode tooMany = objectMapper.createObjectNode();
        tooMany.putArray(TAGS).add("a").add("b").add("c").add("d");
        assertThat(violations(tooMany)).extracting(ShFieldRules.Violation::rule)
                .contains("too-many-items");
    }

    @Test
    @DisplayName("SH1010: an absent value breaks nothing — presence is required's question alone")
    void absenceIsNotAViolation() {
        assertThat(violations(objectMapper.createObjectNode()))
                .as("SH1010: applying minLength to an empty optional field would make every "
                        + "constrained field required by the back door, which is the one way a "
                        + "constraint vocabulary quietly changes the model it was added to")
                .isEmpty();
        assertThat(violations(document(SLUG, "   ")))
                .as("SH1010: and blank is absent, which is the same answer `required` gives")
                .isEmpty();
    }

    @Test
    @DisplayName("SH1010: advice is reported and refuses nothing")
    void adviceNeverRefuses() {
        ObjectNode longHeadline = document(HEADLINE, "a headline well past the recommendation");

        assertThat(violations(longHeadline))
                .as("SH1010: a title over the recommendation is a judgement about how it reads "
                        + "where it is shown, not a broken document")
                .isEmpty();
        assertThat(ShFieldRules.advice(postType(), longHeadline, serverMapper)).singleElement()
                .satisfies(advice -> assertThat(advice.rule()).isEqualTo("longer-than-recommended"));
    }

    @Test
    @DisplayName("SH1010: publishing a violating page is refused, at the field")
    void publishingIsRefused() {
        String postId = draft(SLUG, "Not A Slug");

        assertThatThrownBy(() -> shPostUnifiedWriteService.publish(postId, "admin"))
                .isInstanceOf(ShFieldRules.ConstraintViolatedException.class)
                .satisfies(failure -> assertThat(((ShFieldRules.ConstraintViolatedException) failure)
                        .problem().toProblemDetail().getProperties().get("at"))
                        .isEqualTo("data." + SLUG));

        assertThat(shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED))
                .isEmpty();
    }

    @Test
    @DisplayName("SH1010: a draft is never refused, and the plan says what the publish would be")
    void theDraftIsSavedAndThePlanSaysWhy() {
        String postId = draft(SLUG, "Not A Slug");

        assertThat(shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT))
                .as("SH1010: drafts are where work happens — SH1009's line, and this vocabulary "
                        + "does not move it")
                .isPresent();
        assertThat(shConsolePublishPlan.of(postId).results()).singleElement()
                .satisfies(change -> assertThat(change.detail())
                        .contains("refused")
                        .contains(SLUG));
    }

    @Test
    @DisplayName("SH1010: a recommendation alone never blocks a publish, on any surface")
    void adviceDoesNotBlockAPublish() {
        String postId = draft(HEADLINE, "a headline well past the recommendation");

        assertThatCode(() -> shPostUnifiedWriteService.publish(postId, "admin"))
                .doesNotThrowAnyException();
        assertThat(shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED))
                .isPresent();
    }

    @Test
    @DisplayName("SH1010: a field that declares nothing carries no block, so nothing existing moves")
    void anUndeclaredFieldRoundTripsUnchanged() {
        ShPostTypeAttrDef title = defOf(ShSystemPostTypeAttr.TITLE);

        assertThat(title.constraints())
                .as("SH1010: a block materialized as nulls would make every post-type pulled "
                        + "before this read as drift on the next pull (P5)")
                .isNull();

        ShPostTypeAttrDef slug = defOf(SLUG);
        assertThat(slug.constraints()).isNotNull()
                .satisfies(declared -> {
                    assertThat(declared.pattern()).isEqualTo("[a-z0-9-]+");
                    assertThat(declared.maxLength()).isEqualTo(40);
                    assertThat(declared.min())
                            .as("SH1010: what it does not declare stays undeclared, which is what "
                                    + "makes the block additive")
                            .isNull();
                });
    }

    @Test
    @DisplayName("SH1010: an unreadable block is no constraints, not a refusal on every page")
    void anUnreadableBlockIsNoRule() {
        ShPostTypeAttr broken = new ShPostTypeAttr();
        broken.setName("x");
        broken.setConstraints("{not json");

        assertThat(ShFieldRules.of(broken, serverMapper))
                .as("SH1010: a constraint block the server wrote and cannot read back is a defect "
                        + "in the post-type; refusing every publish of every post of that type "
                        + "puts the fix furthest from whoever can make it")
                .isEqualTo(ShFieldConstraints.NONE);
    }
}
