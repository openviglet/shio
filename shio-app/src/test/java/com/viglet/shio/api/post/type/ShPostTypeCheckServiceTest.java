/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;

/**
 * SH1018 — the measurement a model change is argued from.
 *
 * <p>Asserted over real rows rather than over a mock, because the claim is about <b>content that is
 * already stored</b> — the thing constraints were never checked against. A unit test of the counter
 * would prove the arithmetic and leave the question that matters unasked.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShPostTypeCheckServiceTest {

    private String typeName;

    @Autowired
    private ShPostTypeCheckService service;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManager entityManager;

    @BeforeAll
    void setup() {
        typeName = "Sh1018" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShWidget widget = new ShWidget();
            widget.setId(UUID.randomUUID().toString());
            widget.setName(ShSystemWidget.TEXT);
            entityManager.persist(widget);

            ShPostType postType = new ShPostType();
            postType.setId(UUID.randomUUID().toString());
            postType.setName(typeName);
            postType.setDate(Instant.now());
            postType.setSystem((byte) 0);

            // `headline` is required and at least three characters — the shape a model gains and
            // its stored content then does not satisfy.
            ShPostTypeAttr headline = new ShPostTypeAttr();
            headline.setId(UUID.randomUUID().toString());
            headline.setName("headline");
            headline.setLabel("Headline");
            headline.setOrdinal(0);
            headline.setRequired((byte) 1);
            headline.setShWidget(widget);
            headline.setShPostType(postType);
            postType.setShPostTypeAttrs(new HashSet<>(java.util.Set.of(headline)));
            entityManager.persist(postType);

            // One row satisfies it; one leaves it empty; one carries a key nothing declares.
            seed(postType, "{\"headline\":\"A real headline\"}", ShPostState.DRAFT);
            seed(postType, "{\"headline\":\"\"}", ShPostState.DRAFT);
            seed(postType, "{\"headline\":\"Live\",\"subtitle\":\"left by a rename\"}",
                    ShPostState.PUBLISHED);
        });
    }

    private void seed(ShPostType postType, String data, ShPostState state) {
        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(state);
        post.setTitle("SH1018 fixture");
        post.setFurl("/sh1018-" + UUID.randomUUID().toString().substring(0, 8));
        post.setDate(Instant.now());
        post.setOwner("admin");
        post.setShPostType(postType);
        post.setData(data);
        entityManager.persist(post);
    }

    @Test
    @DisplayName("SH1018: the report counts the rows that break each rule, not the rows")
    void violationsAreCountedPerRule() {
        ShPostTypeCheckService.Report report = service.check(typeName);

        assertThat(report.posts())
                .as("SH1018: both states — a draft that violates a constraint is a publish that "
                        + "will be refused, and a published row that violates one is a page that is "
                        + "already wrong, and counting only drafts hides the half that is live")
                .isEqualTo(3);
        assertThat(report.violating()).isEqualTo(1);
        assertThat(report.rules()).hasSize(1);
        assertThat(report.rules().getFirst().field()).isEqualTo("headline");
        assertThat(report.rules().getFirst().posts())
                .as("SH1018: one row per rule with a count, not one row per post — the report is a "
                        + "shape to act on rather than a log to read")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("SH1018: a key the model no longer declares is counted, and named apart")
    void undeclaredKeysAreCountedSeparately() {
        ShPostTypeCheckService.Report report = service.check(typeName);

        assertThat(report.undeclared()).hasSize(1);
        assertThat(report.undeclared().getFirst().field())
                .as("""
                        SH1018: this hole is older than constraints. A post's data is keyed by field \
                        name, so removing or renaming a field leaves values under a key nothing \
                        declares — and `shio verify` reported orphan posts and folders and never an \
                        orphan field. It is listed apart from the violations because the fix is \
                        different: a violation is content to correct, and this is a rename that \
                        happened on one side only.""")
                .isEqualTo("subtitle");
        assertThat(report.undeclared().getFirst().posts()).isEqualTo(1);
    }

    @Test
    @DisplayName("SH1018: an unknown type is the teaching 404 the rest of this surface gives")
    void anUnknownTypeIsRefusedWithTheSameError() {
        assertThatThrownBy(() -> service.check("Sh1018NoSuchType"))
                .isInstanceOf(ShPostTypeAPI.UnknownPostTypeException.class);
    }
}
