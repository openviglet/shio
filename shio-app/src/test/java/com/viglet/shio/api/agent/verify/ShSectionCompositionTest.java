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
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.post.type.ShSystemPostType;
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
    private com.viglet.shio.render.ShRenderPostTypes shRenderPostTypes;
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

    // --- SH1106: the allow-list read by something that is not a screen ---

    /**
     * SH1106 — a page composing a type its own layout's {@code sh-accepts} does not name.
     *
     * <p>A second page type, bound to a layout whose {@code <main>} accepts two types this fixture
     * never writes, and a page of it naming two {@code SectionBlock}s. The console offered only the
     * declared types and nothing else consulted the list, so a write past that screen composed the
     * page against a declaration no instrument read.</p>
     */
    @Test
    @DisplayName("SH1106: a section of a type the layout does not accept is reported once, with the list")
    void anUndeclaredSectionTypeIsReported() {
        shRenderPostTypes.ensure();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String layoutName = "SH1106_LAYOUT_" + suffix;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String acceptingType = tx.execute(status -> {
            ShPostType accepting = postType("AcceptingPage");
            attr(accepting, ShSystemWidget.TEXT, "title", 1, (byte) 1, null);
            attr(accepting, ShSystemWidget.MULTI_SELECT, "sections", 2, (byte) 0, REFERENCES_POSTS);
            ShSite site = entityManager
                    .createQuery("select s from ShSite s where s.name = :name", ShSite.class)
                    .setParameter("name", siteName).getSingleResult();
            site.setPostTypeLayout("{\"" + accepting.getName() + "\":\"" + layoutName + "\"}");
            ShFolder root = entityManager
                    .createQuery("select f from ShFolder f where f.shSite.id = :site and f.rootFolder = 1",
                            ShFolder.class)
                    .setParameter("site", site.getId()).getSingleResult();
            ShPostUnified layout = new ShPostUnified();
            layout.setId(UUID.randomUUID().toString());
            layout.setState(ShPostState.DRAFT);
            layout.setShSite(site);
            layout.setShFolder(root);
            layout.setShPostType(entityManager
                    .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                    .setParameter("name", ShSystemPostType.PAGE_LAYOUT).getSingleResult());
            layout.setTitle(layoutName);
            layout.setFurl("/sh1106-layout-" + suffix);
            layout.setData(objectMapper.writeValueAsString(Map.of("HTML",
                    "<html><body><h1>{{post.title}}</h1>"
                            + "<main sh-accepts=\"Sh1106Hero Sh1106Cta\"></main></body></html>")));
            layout.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            layout.setOwner("admin");
            entityManager.persist(layout);
            return accepting.getName();
        });
        String url = base + "/sh1106-" + suffix;
        tx.executeWithoutResult(status -> shAgentBatchService.run(new ShAgentBatch(false, List.of(
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + url, acceptingType, base,
                        objectMapper.valueToTree(Map.of("title", "Composed",
                                "sections", List.of(base + "/hero", base + "/faq")))))),
                false, "tester"));

        List<ShAgentReport.Finding> reported = findings("section-not-accepted");
        assertThat(reported.stream().filter(finding -> finding.address().endsWith(url)).toList())
                .as("""
                        two sections of one undeclared type are one decision, so one finding — and \
                        it names the type and the list the layout does accept, which is the whole \
                        of what the author needs to fix either side (SH1106)""")
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.field()).isEqualTo("sections");
                    assertThat(finding.message()).contains(sectionType).contains("Sh1106Hero, Sh1106Cta");
                    assertThat(finding.fix()).contains("sh-accepts");
                });
        assertThat(reported)
                .as("a page type bound to no layout declares nothing, which is 'nobody said' and "
                        + "never 'nothing fits' — the home page composes the same sections unreported")
                .noneSatisfy(finding -> assertThat(finding.address()).endsWith(base + "/home"));
    }

    // --- SH773: the model that went blind, and the reader that now says so ---

    /**
     * SH773 — a vocabulary field whose live copy lost the key three readers need.
     *
     * <h2>What the probe found</h2>
     *
     * <p>SH767 was filed on a replica where {@code shio verify} answered {@code ok} over a page
     * whose sections resolved to nothing, and measuring it before fixing it found the report
     * already right. The quiet run had another cause, and the same probe found it: written against
     * a {@code sections} field carrying <b>no</b> {@code widgetSettings}, verify says nothing —
     * not about an address stored verbatim, and not about a section that genuinely does not
     * exist.</p>
     *
     * <p>This fixture reproduces exactly that, and both assertions matter only together: the
     * silence is the defect, and the new rule is what breaks it.</p>
     */
    @Test
    @DisplayName("SH773: a vocabulary field that lost `references: post` is reported, not silent")
    void aBlindRelationFieldIsReported() {
        // `Page` because the catalogue is what the rule compares against: the finding exists only
        // for a type the shipped vocabulary declares, and `Page.sections` is one of the three
        // fields in the whole vocabulary that carry the key.
        //
        // The state is FORCED rather than assumed. `ShBlueprintApplyTest` and
        // `ShVocabularyPostTypesTest` both provision a correct `Page` into this JVM's database, so
        // whether this fixture can create a blind one depends on class order — and a test that is
        // right because it sorts first is the shape SH770 was about. Blanking one String column is
        // safe where removing the attribute would not be: SH289's cascade reaches the shared widget
        // catalogue through `ShPostTypeAttr.shWidget`, and nothing here touches that.
        String blindPage = "Page";
        String before = blankSectionsSettings(blindPage);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    shAgentBatchService.run(new ShAgentBatch(false, List.of(
                            new Op(ShAgentBatchService.POST_UPSERT,
                                    "post:" + siteName + base + "/blind",
                                    blindPage, base, objectMapper.valueToTree(Map.of(
                                            "title", "Blind",
                                            "sections", List.of(base + "/not-there-at-all"))))
                    )), false, "tester"));

            assertThat(findings("dangling-reference"))
                    .as("""
                            the silence this task is about: the page names a section that does not                             exist, and the dangling rule cannot see it because the field never said                             its values were addresses. Every dev box that ran the replication chain                             early is in this state (SH773).""")
                    .noneSatisfy(finding -> assertThat(finding.address()).endsWith(base + "/blind"));
            assertThat(findings("vocabulary-drift"))
                    .as("""
                            so the drift itself is the finding. Not broken content — content that                             cannot be CHECKED, which is worse, and is the only reason the rule                             exists. It names the type, the field and a fix that works:                             render.provision creates a type and never edits one (SH152).""")
                    .anySatisfy(finding -> {
                        assertThat(finding.field()).isEqualTo("sections");
                        assertThat(finding.message()).contains("references: post");
                        assertThat(finding.fix()).contains("PUT /api/v2/post-type/" + blindPage);
                    });
        } finally {
            restoreSectionsSettings(blindPage, before);
        }
    }

    /**
     * Blanks {@code <type>.sections}'s widgetSettings, creating the type and the field if this JVM
     * has neither yet, and answers what was there before.
     */
    private String blankSectionsSettings(String typeName) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            ShPostType live = entityManager
                    .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                    .setParameter("name", typeName)
                    .getResultStream().findFirst().orElse(null);
            if (live == null) {
                ShPostType created = new ShPostType();
                created.setName(typeName);
                created.setTitle(typeName);
                created.setSystem((byte) 0);
                created.setDate(Instant.parse("2026-01-01T00:00:00Z"));
                created.setOwner("admin");
                entityManager.persist(created);
                attr(created, ShSystemWidget.TEXT, "title", 1, (byte) 1, null);
                attr(created, ShSystemWidget.MULTI_SELECT, "sections", 2, (byte) 0, null);
                return null;
            }
            ShPostTypeAttr sections = live.getShPostTypeAttrs() == null ? null
                    : live.getShPostTypeAttrs().stream()
                            .filter(each -> "sections".equals(each.getName()))
                            .findFirst().orElse(null);
            if (sections == null) {
                attr(live, ShSystemWidget.MULTI_SELECT, "sections", 2, (byte) 0, null);
                return null;
            }
            String had = sections.getWidgetSettings();
            // The whole defect, in one null: SH154's rule is that a Multi Select is tags unless the
            // model says otherwise, so this field IS tags to every reader that asks.
            sections.setWidgetSettings(null);
            entityManager.merge(sections);
            return had;
        });
    }

    private void restoreSectionsSettings(String typeName, String settings) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShPostType live = entityManager
                    .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                    .setParameter("name", typeName)
                    .getResultStream().findFirst().orElse(null);
            if (live == null || live.getShPostTypeAttrs() == null) {
                return;
            }
            live.getShPostTypeAttrs().stream()
                    .filter(each -> "sections".equals(each.getName()))
                    .findFirst()
                    .ifPresent(each -> {
                        each.setWidgetSettings(settings);
                        entityManager.merge(each);
                    });
        });
    }

    @Test
    @DisplayName("SH773: a type the shipped vocabulary does not carry is nobody's drift")
    void aTypeOutsideTheVocabularyIsNotJudged() {
        // The noise this rule must not make. `pageType` is this test's own invention with a
        // perfectly good `references: post`, and half of any instance's types are somebody's own —
        // comparing those against a catalogue that never described them would report a defect for
        // every model anybody ever wrote.
        assertThat(findings("vocabulary-drift"))
                .noneSatisfy(finding -> assertThat(finding.address()).contains(pageType));
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
