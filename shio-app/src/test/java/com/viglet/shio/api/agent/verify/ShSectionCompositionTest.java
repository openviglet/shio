/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentBatch.Op;
import com.viglet.shio.api.agent.verify.dto.ShAgentDigest;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/**
 * SH154 and SH169 — verification for a page composed of sections.
 *
 * <p>Two shipped features were each correct alone and blind to the pattern the SH95 vocabulary
 * introduced: the SH99 lint knew a reference held as an id and not one held as a friendly URL, and
 * the SH101 digest read a post's own markup and a section page has none. So a page could name a
 * section that did not exist and {@code verify} said {@code ok}, and the home page, the pricing
 * page and a genuinely blank page all digested to the hash of nothing.</p>
 *
 * <p>The non-regression is as important as the fix and is asserted here too: a {@code Multi Select}
 * that holds <b>tags</b> must stay tags. A rule that read every value as a URL would report a
 * defect for every tag on every post, which is why the field has to declare what it holds.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShSectionCompositionTest {

    /** What the SH95 vocabulary puts on `sections`, and what both readers key on. */
    private static final String REFERENCES_POSTS = "{\"multiselect\":{\"references\":\"post\"}}";

    @Autowired
    private ShAgentVerifyService shAgentVerifyService;
    @Autowired
    private ShAgentRenderService shAgentRenderService;
    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String pageType;
    private String sectionType;
    private String base;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            siteName = "sections-site-" + UUID.randomUUID();
            site.setName(siteName);
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

            // A page: a title, a Multi Select of section URLs, and a Multi Select of plain tags.
            // The second one is the control — it must stay invisible to both readers.
            ShPostType page = postType("SectionPage");
            pageType = page.getName();
            attr(page, ShSystemWidget.TEXT, "title", 1, (byte) 1, null);
            attr(page, ShSystemWidget.MULTI_SELECT, "sections", 2, (byte) 0, REFERENCES_POSTS);
            attr(page, ShSystemWidget.MULTI_SELECT, "tags", 3, (byte) 0, null);

            // A section: a heading in a Text field and a body in an HTML field — the shape the
            // vocabulary's components render, and the reason the digest cannot only read markup.
            ShPostType section = postType("SectionBlock");
            sectionType = section.getName();
            attr(section, ShSystemWidget.TEXT, "heading", 1, (byte) 1, null);
            attr(section, ShSystemWidget.HTML_EDITOR, "body", 2, (byte) 0, null);
        });

        base = "/sec-" + UUID.randomUUID().toString().substring(0, 8);
        tx.executeWithoutResult(status -> shAgentBatchService.run(new ShAgentBatch(false, List.of(
                new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + siteName + base,
                        null, null, null),
                section("/hero", "Build faster", "<p>One <strong>line</strong> of support.</p>"),
                section("/faq", "Questions", "<p>Answers.</p>"),
                // The page every assertion below is about: two real sections, in order.
                page("/home", "Home", List.of(base + "/hero", base + "/faq")),
                // Same shape, one section — so two pages cannot digest the same.
                page("/pricing", "Pricing", List.of(base + "/faq")),
                // And a page naming something that is not there.
                page("/broken", "Broken", List.of(base + "/no-such-section")))),
                false, "tester"));
    }

    // --- helpers ---

    private ShPostType postType(String prefix) {
        ShPostType postType = new ShPostType();
        postType.setName(prefix + UUID.randomUUID().toString().substring(0, 8));
        postType.setTitle(prefix);
        postType.setSystem((byte) 0);
        postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        postType.setOwner("admin");
        entityManager.persist(postType);
        return postType;
    }

    private void attr(ShPostType postType, String widgetName, String name, int ordinal,
                      byte isTitle, String widgetSettings) {
        ShWidget widget = entityManager
                .createQuery("select w from ShWidget w where w.name = :name", ShWidget.class)
                .setParameter("name", widgetName)
                .getResultStream().findFirst().orElseGet(() -> {
                    ShWidget created = new ShWidget();
                    created.setName(widgetName);
                    created.setDescription(widgetName);
                    entityManager.persist(created);
                    return created;
                });
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName(name);
        attr.setLabel(name);
        attr.setOrdinal(ordinal);
        attr.setIsTitle(isTitle);
        attr.setShWidget(widget);
        attr.setWidgetSettings(widgetSettings);
        attr.setShPostType(postType);
        entityManager.persist(attr);
    }

    private Op section(String url, String heading, String body) {
        return new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + base + url,
                sectionType, base, objectMapper.valueToTree(Map.of("heading", heading, "body", body)));
    }

    private Op page(String url, String title, List<String> sections) {
        return new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + base + url,
                pageType, base,
                objectMapper.valueToTree(Map.of("title", title, "sections", sections,
                        "tags", List.of("marketing", "q1"))));
    }

    private List<ShAgentReport.Finding> findings(String rule) {
        ShAgentReport report = shAgentVerifyService.verify(siteName, "content", 200);
        return report.findings().stream().filter(finding -> rule.equals(finding.rule())).toList();
    }

    private ShAgentDigest digest(String url) {
        return shAgentRenderService.digest("post:" + siteName + base + url, null);
    }

    // --- SH154: the lint sees the reference ---

    @Test
    @DisplayName("a page naming a section that does not exist is an error")
    void aDanglingSectionIsReported() {
        assertThat(findings("dangling-reference"))
                .anySatisfy(finding -> {
                    assertThat(finding.address()).endsWith(base + "/broken");
                    assertThat(finding.field()).isEqualTo("sections");
                    assertThat(finding.message()).contains(base + "/no-such-section");
                    assertThat(finding.fix()).isNotBlank();
                });
    }

    @Test
    @DisplayName("a page naming real sections is clean")
    void aResolvedSectionIsNotReported() {
        assertThat(findings("dangling-reference"))
                .noneSatisfy(finding -> assertThat(finding.address()).endsWith(base + "/home"));
    }

    @Test
    @DisplayName("a Multi Select of tags stays tags — the whole reason the field must declare itself")
    void tagsAreNotTreatedAsReferences() {
        // Both pages carry tags "marketing" and "q1". If the rule read every Multi Select value as
        // a URL, every post on every instance would report two defects.
        assertThat(findings("dangling-reference"))
                .noneSatisfy(finding -> assertThat(finding.field()).isEqualTo("tags"));
        assertThat(findings("dangling-reference"))
                .noneSatisfy(finding -> assertThat(finding.message()).contains("marketing"));
    }

    // --- SH169: the digest composes the way the page does ---

    @Test
    @DisplayName("a section-composed page digests its sections' structure, not nothing")
    void theDigestComposesTheSections() {
        ShAgentDigest home = digest("/home");

        assertThat(home.headings()).extracting(ShAgentDigest.Heading::text)
                .as("each section's title becomes a heading, in the order the page names them")
                .containsSubsequence("Build faster", "Questions");
        assertThat(home.words())
                .as("a page that reported 0 words was indistinguishable from a blank one")
                .isPositive();
        assertThat(home.fields()).contains("sections");
    }

    @Test
    @DisplayName("two pages built from different sections do not digest the same")
    void differentCompositionsDigestDifferently() {
        // The defect this replaces: home, pricing and a blank page all hashed e3b0c442… — the
        // hash of nothing — so the digest could answer neither "did it come out right" nor
        // "did anything change".
        assertThat(digest("/home").digest()).isNotEqualTo(digest("/pricing").digest());
    }

    @Test
    @DisplayName("the same page digests the same twice — the hash is over structure, not the read")
    void theDigestIsStable() {
        assertThat(digest("/home").digest()).isEqualTo(digest("/home").digest());
    }

    @Test
    @DisplayName("a missing section is marked in the outline, not hashed around")
    void aMissingSectionIsMarked() {
        ShAgentDigest broken = digest("/broken");

        assertThat(broken.headings()).extracting(ShAgentDigest.Heading::text)
                .as("contributing nothing would be the old failure wearing a different hat: two "
                        + "different mistakes would hash the same")
                .anySatisfy(text -> assertThat(text).contains("missing section"));
        assertThat(broken.digest()).isNotEqualTo(digest("/pricing").digest());
    }

    // --- SH767: the relation that took an address and meant nothing by it ---

    /**
     * SH767 — the write resolves an address in a relation field, the way the op's own
     * {@code folder} has always resolved one.
     *
     * <h2>What was measured</h2>
     *
     * <p>{@code Page.sections} stores site-relative URLs, and SH79 makes {@code post:<site>/<url>}
     * the currency of every other part of this protocol — so writing the relation the way the rest
     * of the surface is written was accepted, reported {@code changed: true}, and stored
     * <b>verbatim</b>. On a replica: the renderer resolved none of them, the page served with no
     * sections, and the only symptom was a curator opening the page and finding the content gone.
     * The post exists, its state is PUBLISHED, the route answers 200. <b>It looks authored.</b></p>
     *
     * <h2>Why resolving and not refusing</h2>
     *
     * <p>Because the op already does exactly this one field over: {@code folder} takes
     * {@code folder:<site>/<path>} or a bare path and normalizes to the path. A relation is the
     * same question about a different field. It is purely syntactic — the address carries the URL —
     * which is what makes it safe inside a batch that creates the target three ops later.</p>
     */
    @Test
    @DisplayName("SH767: a section named by its address is stored as the URL the relation holds")
    void anAddressInARelationIsResolved() {
        String url = base + "/addressed";
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                shAgentBatchService.run(new ShAgentBatch(false, List.of(
                        new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + url,
                                pageType, base, objectMapper.valueToTree(Map.of(
                                        "title", "Addressed",
                                        "sections", List.of("post:" + siteName + base + "/hero"))))
                )), false, "tester"));

        assertThat(findings("dangling-reference"))
                .as("""
                        the address form resolves to the URL the field holds, so the page composes                         the section it names. Stored verbatim this was a page that looked authored                         from every side — post present, state PUBLISHED, route 200 — and served                         with no sections at all (SH767).""")
                .noneSatisfy(finding -> assertThat(finding.address()).endsWith(url));
        assertThat(digest("/addressed").headings()).extracting(ShAgentDigest.Heading::text)
                .as("and it is the section's own structure in the digest, not the hash of nothing")
                .anySatisfy(text -> assertThat(text).contains("Build faster"));
    }

    @Test
    @DisplayName("SH767: an address no URL in this site can mean is refused, naming the form")
    void anAddressThatCannotBeAUrlIsRefused() {
        for (String sent : List.of("id:" + UUID.randomUUID(), "folder:" + siteName + base,
                "post:some-other-site" + base + "/hero")) {
            assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> shAgentBatchService.run(
                            new ShAgentBatch(false, List.of(
                                    new Op(ShAgentBatchService.POST_UPSERT,
                                            "post:" + siteName + base + "/refused",
                                            pageType, base, objectMapper.valueToTree(Map.of(
                                                    "title", "Refused",
                                                    "sections", List.of(sent)))))),
                            false, "tester")))
                    .as("%s names something no URL in this site can mean, and P6 says a refusal "
                            + "carries the form the field wants rather than leaving a page that "
                            + "renders a hole", sent)
                    // The shared half of every one of them, which is the half that teaches: what
                    // the field holds, and the two spellings it accepts. What differs is the
                    // reason, and each carries its own.
                    .hasMessageContaining("friendly URLs of posts in this site");
        }
    }

    @Test
    @DisplayName("SH767: a value with no scheme is left exactly as sent")
    void aBareUrlIsNotRewritten() {
        // The control, and the reason `relationUrl` looks at the scheme rather than at the colon: a
        // Multi Select value is free text. Refusing or rewriting one that does not parse as an
        // address would make a page unwritable until every section exists, which is the opposite of
        // how a conversion builds one — the section arrives three ops later.
        assertThat(findings("dangling-reference"))
                .anySatisfy(finding -> {
                    assertThat(finding.address()).endsWith(base + "/broken");
                    assertThat(finding.message()).contains(base + "/no-such-section");
                });
    }
}
