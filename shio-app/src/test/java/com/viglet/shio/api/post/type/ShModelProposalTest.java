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

import java.time.Instant;
import java.util.List;
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

import com.viglet.shio.api.post.type.dto.ShModelProposal;
import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.post.type.ShSystemPostType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * What a model change would cost, asked before it is made (SH523).
 *
 * <h2>The four questions, one fixture</h2>
 *
 * <p>The fixture is built so each question has a <b>non-trivial</b> answer, because a proposal that
 * reports nothing is indistinguishable from one that cannot see anything: three published posts of
 * which one is missing {@code SUMMARY}; a layout that interpolates {@code TITLE} and
 * {@code SUMMARY} and not {@code NOTES}; and a stored {@code LEGACY} key the type does not
 * declare.</p>
 *
 * <p>That last one is SH474's defect asked as a question rather than found by a lint firing 66
 * times — a {@code File} post stores {@code SHA256}, {@code SIZE}, {@code WIDTH} and
 * {@code HEIGHT} against a type declaring one field.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShModelProposalTest {

    @Autowired
    private ShModelProposalService proposalService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String typeName;

    @BeforeAll
    void setup() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        typeName = "Sh523Article" + tag;
        String siteName = "sh523-" + tag;

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            site.setPostTypeLayout("{\"" + typeName + "\":\"L_sh523_" + tag + "\"}");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setPosition((byte) 1);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType layoutType = systemType(ShSystemPostType.PAGE_LAYOUT, when);
            // TITLE and SUMMARY are read by the layout; NOTES is declared and read by nothing.
            ShPostType article = type(typeName, when, "TITLE", "SUMMARY", "NOTES");

            post(site, root, layoutType, "L_sh523_" + tag, "/l",
                    "{\"HTML\":\"<html><body><h1>{{TITLE}}</h1><p>{{SUMMARY}}</p></body></html>\"}",
                    ShPostState.PUBLISHED, when);

            post(site, root, article, "One", "/one",
                    "{\"TITLE\":\"One\",\"SUMMARY\":\"has one\"}", ShPostState.PUBLISHED, when);
            post(site, root, article, "Two", "/two",
                    "{\"TITLE\":\"Two\",\"SUMMARY\":\"has one\"}", ShPostState.PUBLISHED, when);
            // The violator, and the stored key nothing declares — one post carries both.
            post(site, root, article, "Three", "/three",
                    "{\"TITLE\":\"Three\",\"SUMMARY\":\"\",\"LEGACY\":\"from an import\"}",
                    ShPostState.PUBLISHED, when);
        });
    }

    private ShPostTypeDef proposed(String... required) {
        List<String> tighten = List.of(required);
        return new ShPostTypeDef(typeName, typeName, typeName, null, null, false, true,
                List.of(field("TITLE", tighten.contains("TITLE"), 1),
                        field("SUMMARY", tighten.contains("SUMMARY"), 2),
                        field("NOTES", tighten.contains("NOTES"), 3)));
    }

    private static ShPostTypeAttrDef field(String name, boolean required, int ordinal) {
        return new ShPostTypeAttrDef(name, name, null, "Text", required, ordinal == 1, false,
                ordinal, null, List.of());
    }

    private ShModelProposal.Finding finding(ShModelProposal proposal, String rule) {
        return proposal.findings().stream()
                .filter(item -> rule.equals(item.rule()))
                .findFirst().orElse(null);
    }

    @Test
    @DisplayName("SH523: making a field required counts the posts that would violate it")
    void tighteningIsCounted() {
        ShModelProposal proposal = proposalService.propose(typeName, proposed("SUMMARY"));

        ShModelProposal.Finding violated = finding(proposal, "required-field-violated");
        assertThat(violated)
                .as("""
                        the count is the whole artefact: making a field required is either a data \
                        cleanup or a mistake, and only the number of posts that would violate it \
                        tells them apart. A refusal cannot say that (SH523)""")
                .isNotNull();
        assertThat(violated.field()).isEqualTo("SUMMARY");
        assertThat(violated.count())
                .as("one of the three published posts has an empty SUMMARY")
                .isEqualTo(1);
        assertThat(violated.sample()).hasSize(1);
        assertThat(violated.detail()).contains("Fill them first, or leave the field optional");
    }

    @Test
    @DisplayName("SH523: a field nothing violates costs nothing, so tightening it is safe to accept")
    void aCleanTighteningIsSilent() {
        // The other half of the same question, and the reason the proposal is worth having: TITLE
        // is populated everywhere, so requiring it is free — which is the answer that lets somebody
        // tighten a model instead of leaving every field optional forever.
        ShModelProposal proposal = proposalService.propose(typeName, proposed("TITLE"));

        assertThat(finding(proposal, "required-field-violated")).isNull();
    }

    @Test
    @DisplayName("SH523: removing a field a template reads is reported before the page renders empty")
    void aRemovedFieldThatIsStillReadIsReported() {
        ShPostTypeDef withoutSummary = new ShPostTypeDef(typeName, typeName, typeName, null, null,
                false, true, List.of(field("TITLE", false, 1), field("NOTES", false, 2)));

        ShModelProposal proposal = proposalService.propose(typeName, withoutSummary);

        ShModelProposal.Finding removed = finding(proposal, "removed-field-still-read");
        assertThat(removed).isNotNull();
        assertThat(removed.field())
                .as("the layout interpolates SUMMARY, so dropping it renders nothing there (SH523)")
                .isEqualTo("SUMMARY");
        assertThat(removed.detail()).contains("Remove the interpolation first, or keep the field");
    }

    @Test
    @DisplayName("SH523: a declared field no template reads is listed, and is not called a defect")
    void anUnreadFieldIsListedNotCondemned() {
        ShModelProposal proposal = proposalService.propose(typeName, null);

        ShModelProposal.Finding unread = finding(proposal, "declared-field-unread");
        assertThat(unread).isNotNull();
        assertThat(unread.sample()).containsExactly("NOTES");
        assertThat(unread.detail())
                .as("""
                        a field can be delivered over the CDA without any template reading it, so \
                        this is a list to check and not a violation — a proposal that cried defect \
                        here is one nobody would read twice (SH523)""")
                .contains("Not a defect");
    }

    @Test
    @DisplayName("SH523: a stored key no field declares is reported — SH474's defect, asked")
    void anUndeclaredStoredKeyIsReported() {
        ShModelProposal proposal = proposalService.propose(typeName, null);

        ShModelProposal.Finding stored = finding(proposal, "stored-key-undeclared");
        assertThat(stored)
                .as("""
                        a File post stores SHA256, SIZE, WIDTH and HEIGHT against a type declaring \
                        one field, and it took a lint firing 66 times to surface that (SH474). \
                        Here it is a line in a proposal (SH523)""")
                .isNotNull();
        assertThat(stored.sample()).containsExactly("LEGACY");
        assertThat(stored.detail()).contains("nothing validates them");
    }

    @Test
    @DisplayName("SH523: the two model-as-it-stands questions need no proposed change at all")
    void theStandingQuestionsNeedNoProposal() {
        // Passing null asks only what is true today, which is what makes this usable as a review of
        // a model nobody is currently changing.
        ShModelProposal proposal = proposalService.propose(typeName, null);

        assertThat(proposal.exists()).isTrue();
        assertThat(proposal.posts()).isEqualTo(3);
        assertThat(proposal.findings()).extracting(ShModelProposal.Finding::rule)
                .containsExactlyInAnyOrder("declared-field-unread", "stored-key-undeclared");
    }

    // --- fixture ---

    private ShPostType systemType(String name, Instant when) {
        ShPostType existing = entityManager
                .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                .setParameter("name", name)
                .getResultStream().findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }
        ShPostType type = new ShPostType();
        type.setName(name);
        type.setTitle(name);
        type.setSystem((byte) 1);
        type.setDate(when);
        type.setOwner("admin");
        entityManager.persist(type);
        attr(type, "HTML", 1);
        return type;
    }

    private ShPostType type(String name, Instant when, String... fields) {
        ShPostType type = new ShPostType();
        type.setName(name);
        type.setTitle(name);
        type.setSystem((byte) 0);
        type.setDate(when);
        type.setOwner("admin");
        entityManager.persist(type);
        int ordinal = 1;
        for (String field : fields) {
            attr(type, field, ordinal++);
        }
        return type;
    }

    private void attr(ShPostType postType, String name, int ordinal) {
        ShWidget widget = entityManager
                .createQuery("select w from ShWidget w where w.name = :name", ShWidget.class)
                .setParameter("name", "Text")
                .getResultStream().findFirst().orElseGet(() -> {
                    ShWidget created = new ShWidget();
                    created.setName("Text");
                    created.setDescription("Text");
                    entityManager.persist(created);
                    return created;
                });
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName(name);
        attr.setLabel(name);
        attr.setOrdinal(ordinal);
        attr.setIsTitle(ordinal == 1 ? (byte) 1 : (byte) 0);
        attr.setRequired((byte) 0);
        attr.setShWidget(widget);
        attr.setShPostType(postType);
        entityManager.persist(attr);
    }

    private void post(ShSite site, ShFolder folder, ShPostType type, String title, String furl,
                      String data, ShPostState state, Instant when) {
        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(state);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(type);
        post.setTitle(title);
        post.setFurl(furl);
        post.setData(data);
        post.setDate(when);
        post.setOwner("admin");
        entityManager.persist(post);
    }
}
