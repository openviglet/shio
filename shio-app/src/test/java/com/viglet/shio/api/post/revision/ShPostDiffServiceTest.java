/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.revision;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.viglet.shio.api.post.revision.ShPostDiffService.Field;
import com.viglet.shio.api.post.revision.ShPostDiffService.ShPostDiff;
import com.viglet.shio.api.post.revision.ShPostDiffService.UnknownSideException;
import com.viglet.shio.api.post.ShContentPerspective;
import com.viglet.shio.api.post.unified.ShPostStateNotFoundException;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.UpdateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
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
 * SH988 — the diff is answered on the server, and a created page has one.
 *
 * <p>The version dialog and the review queue both hid a create: the dialog compared the draft with
 * a published row that did not exist, and the queue offered no diff without one. These drive a
 * real post through create, publish and edit and read the comparison at each step.</p>
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShPostDiffServiceTest {

    @Autowired
    private ShPostDiffService diffService;
    @Autowired
    private ShPostRevisionService revisionService;
    @Autowired
    private ShPostUnifiedWriteService writeService;
    @Autowired
    private ShPostRevisionRepository revisionRepository;
    @Autowired
    private ShPostUnifiedRepository unifiedRepository;
    @Autowired
    private ShWidgetRepository widgetRepository;
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
            ShSite site = new ShSite();
            site.setName("sh988-" + UUID.randomUUID());
            site.setDescription("Diff test site");
            site.setUrl("http://example.test");
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
            folderId = root.getId();

            ShWidget text = entityManager.find(ShWidget.class,
                    widgetRepository.findFirstByName(ShSystemWidget.TEXT).getId());
            ShPostType postType = new ShPostType();
            postType.setName("PT-SH988-" + UUID.randomUUID());
            postType.setTitle("Diff Type");
            postType.setDescription("Post type for the diff test");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            ShPostTypeAttr title = new ShPostTypeAttr();
            title.setName(ShSystemPostTypeAttr.TITLE);
            title.setLabel("Title");
            title.setDescription("Title field");
            title.setOrdinal(1);
            title.setRequired((byte) 1);
            title.setIsTitle((byte) 1);
            title.setIsSummary((byte) 0);
            title.setShWidget(text);
            title.setShPostType(postType);
            Set<ShPostTypeAttr> attrs = new HashSet<>();
            attrs.add(title);
            postType.setShPostTypeAttrs(attrs);
            entityManager.persist(postType);
            postTypeId = postType.getId();
        });
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> written.forEach(id -> {
            revisionRepository.deleteAll(revisionRepository.findByPostIdOrderByVersionNoDesc(id));
            unifiedRepository.findById(id).forEach(unifiedRepository::delete);
        }));
    }

    private ObjectNode data(String titleValue) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put(ShSystemPostTypeAttr.TITLE, titleValue);
        return node;
    }

    private String create(String titleValue) {
        String id = writeService.create(new CreateRequest(folderId, postTypeId, data(titleValue),
                ShPostState.DRAFT, "admin", null, null, "/p-" + UUID.randomUUID())).getId();
        written.add(id);
        return id;
    }

    @Test
    @DisplayName("SH988: a created page's diff has no before, and every field is an addition")
    void aCreateHasADiff() {
        String id = create("Launch");

        ShPostDiff diff = diffService.diff(id, null, null);

        assertThat(diff.before())
                .as("SH988: a page never published has no published side. That is a create, not an"
                        + " error, and the console draws the other side whole as additions")
                .isNull();
        assertThat(diff.after().get(ShSystemPostTypeAttr.TITLE).asString()).isEqualTo("Launch");
        assertThat(diff.fields()).extracting(Field::name, Field::changed)
                .contains(org.assertj.core.groups.Tuple.tuple(ShSystemPostTypeAttr.TITLE, true));
    }

    @Test
    @DisplayName("SH988: an edit after publishing names the field that moved, and only that one")
    void anEditNamesItsField() {
        String id = create("First");
        writeService.publish(id, "admin");
        writeService.update(id, new UpdateRequest(data("Second"), ShPostState.DRAFT, "admin"));

        ShPostDiff diff = diffService.diff(id, "published", "draft");

        assertThat(diff.before().get(ShSystemPostTypeAttr.TITLE).asString()).isEqualTo("First");
        assertThat(diff.after().get(ShSystemPostTypeAttr.TITLE).asString()).isEqualTo("Second");
        assertThat(diff.fields()).filteredOn(Field::changed).extracting(Field::name)
                .containsExactly(ShSystemPostTypeAttr.TITLE);
        assertThat(diff.fields()).extracting(Field::name).isSorted();
    }

    @Test
    @DisplayName("SH988: any two revisions compare, and a missing one or a bad spelling teaches")
    void revisionsCompareAndBadSidesTeach() {
        String id = create("One");
        writeService.publish(id, "admin");
        writeService.update(id, new UpdateRequest(data("Two"), ShPostState.DRAFT, "admin"));
        writeService.publish(id, "admin");
        List<Integer> versions = revisionService.rows(id).stream().map(ShRevisionRow::version).sorted().toList();
        assertThat(versions).as("SH988: this needs two captured revisions to compare").hasSizeGreaterThanOrEqualTo(2);

        ShPostDiff diff = diffService.diff(id, "rev:" + versions.getFirst(), "rev:" + versions.getLast());
        assertThat(diff.fields()).filteredOn(Field::changed).extracting(Field::name)
                .containsExactly(ShSystemPostTypeAttr.TITLE);

        assertThatThrownBy(() -> diffService.diff(id, "rev:9999", "draft"))
                .isInstanceOf(ShPostStateNotFoundException.class);
        assertThatThrownBy(() -> diffService.diff(id, "publishd", "draft"))
                .as("SH988: a typo is the state= grammar's refusal, so a side is spelled one way (SH990)")
                .isInstanceOf(ShContentPerspective.UnknownStateException.class)
                .satisfies(thrown -> assertThat(((ShContentPerspective.UnknownStateException) thrown)
                        .problem().didYouMean()).isEqualTo("published"));
        assertThatThrownBy(() -> diffService.diff(id, "release:spring", "draft"))
                .isInstanceOf(UnknownSideException.class);
    }
}
