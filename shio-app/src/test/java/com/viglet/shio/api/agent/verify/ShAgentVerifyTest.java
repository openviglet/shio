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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAddressResolver;
import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentBatch.Op;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport.Finding;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH99 (content lint) and SH100 (route verification).
 *
 * <p>What is asserted is the <b>defect catalogue</b>, one rule at a time, plus the two
 * properties that decide whether the report is worth running at all: {@code ok} tracks
 * errors and <em>not</em> warnings (a check that failed on an empty folder would be
 * switched off within a week), and a truncated report says so rather than looking
 * shorter.</p>
 *
 * <p>Every fixture defect is deliberate. They are built through the SH81 batch surface
 * rather than by hand-persisting rows, so the lint is proved against content an agent
 * could actually have written — which is the whole population it exists to check.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
@com.viglet.shio.ShFixtureShapeGuard.BuildsDuplicateUrls(
        "as ShAgentVerifyScopeTest: the rule under test is the one that reports a shared URL")
class ShAgentVerifyTest {

    @Autowired
    private ShAgentVerifyService shAgentVerifyService;
    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShAddressResolver shAddressResolver;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String postTypeName;
    private String base;
    private String targetId;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            siteName = "verify-site-" + UUID.randomUUID();
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

            ShPostType postType = new ShPostType();
            postTypeName = "VerifyPage" + UUID.randomUUID().toString().substring(0, 8);
            postType.setName(postTypeName);
            postType.setTitle("Verify Page");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            attr(postType, ShSystemWidget.TEXT, "headline", 1, (byte) 1);
            attr(postType, ShSystemWidget.HTML_EDITOR, "body", 2, (byte) 0);
            attr(postType, ShSystemWidget.CONTENT_SELECT, "related", 3, (byte) 0);
            attr(postType, ShSystemWidget.FILE, "hero", 4, (byte) 0);

            // SH470 — the system `File` type, whose own `FILE` field is a File widget holding the
            // stored filename. Created here rather than relied on from the seed, because what this
            // fixture has to reproduce is a *capture's* eighteen uploads, and a shape borrowed from
            // whatever the instance happens to have seeded is a fixture that can stop reproducing it.
            filePostType();
        });

        base = "/verify-" + UUID.randomUUID().toString().substring(0, 8);
        // The reference target: a real post, so `related` can point at something that
        // exists (dangling) or does not exist (also dangling) on purpose per case.
        tx.executeWithoutResult(status -> run(
                new Op(ShAgentBatchService.FOLDER_UPSERT, folder(""), null, null, null),
                new Op(ShAgentBatchService.FOLDER_UPSERT, folder("/empty"), null, null, null),
                new Op(ShAgentBatchService.POST_UPSERT, post("/target"), postTypeName, base,
                        data(Map.of("headline", "Target"))),
                // Every rule below gets its own page, so one failing assertion names one rule.
                new Op(ShAgentBatchService.POST_UPSERT, post("/missing-headline"), postTypeName, base,
                        data(Map.of("body", "<p>No headline here</p>"))),
                new Op(ShAgentBatchService.POST_UPSERT, post("/dangling"), postTypeName, base,
                        data(Map.of("headline", "Dangling",
                                "related", "00000000-0000-0000-0000-000000000000"))),
                new Op(ShAgentBatchService.POST_UPSERT, post("/broken-links"), postTypeName, base,
                        data(Map.of("headline", "Broken links",
                                "body", "<h1>Hi</h1><p><a href=\"" + base + "/nowhere\">gone</a>"
                                        + "<a href=\"https://example.com\">out</a>"
                                        + "<img src=\"/file_source/no-such-id/ghost.png\"></p>"))),
                // The clean page: an internal link that resolves and an image with alt.
                // It must produce nothing, which is the only way to know the rules above
                // are firing on the defect rather than on the shape of the fixture.
                new Op(ShAgentBatchService.POST_UPSERT, post("/fine"), postTypeName, base,
                        data(Map.of("headline", "Fine",
                                "body", "<h1>Fine</h1><p><a href=\"" + base + "/broken-links\">ok</a>"
                                        + "<img src=\"https://cdn.example.com/x.png\" alt=\"x\"></p>")))));

        // Published, and pointing at a draft-only post: the "renders a hole for a visitor,
        // looks fine to the editor" case, which is the reason that rule exists.
        tx.executeWithoutResult(status -> run(
                new Op(ShAgentBatchService.POST_UPSERT, post("/needs-target"), postTypeName, base,
                        data(Map.of("headline", "Needs target", "related", targetId()))),
                new Op(ShAgentBatchService.POST_UPSERT, post("/hero-missing"), postTypeName, base,
                        data(Map.of("headline", "Hero missing", "hero", targetId()))),
                // SH470 — what a capture uploads: one `File` post per asset, each storing its own
                // filename in `FILE`. Three rather than eighteen; the rule fires per row, so the
                // count that matters is "more than zero", and eighteen would only make the fixture
                // slower at saying the same thing.
                new Op(ShAgentBatchService.POST_UPSERT, post("/inter.woff2"), ShSystemPostType.FILE,
                        base, data(Map.of("FILE", "inter-greek-wght-normal-CkhJZR-_.woff2"))),
                new Op(ShAgentBatchService.POST_UPSERT, post("/logo.svg"), ShSystemPostType.FILE,
                        base, data(Map.of("FILE", "logo.svg"))),
                new Op(ShAgentBatchService.POST_UPSERT, post("/hero.png"), ShSystemPostType.FILE,
                        base, data(Map.of("FILE", "hero.png"))),
                new Op(ShAgentBatchService.POST_PUBLISH, post("/needs-target"), null, null, null),
                new Op(ShAgentBatchService.POST_PUBLISH, post("/broken-links"), null, null, null),
                new Op(ShAgentBatchService.POST_PUBLISH, post("/fine"), null, null, null)));

        // SH872 — an annotated verbatim page after a curator has edited one of its blocks. The
        // stored key is not on the post-type and never will be: the name comes from a block hash a
        // capture produced. Two keys on one page, so the assertion is about which of them is
        // reported rather than about whether the rule ran at all.
        tx.executeWithoutResult(status -> run(
                new Op(ShAgentBatchService.POST_UPSERT, post("/annotated"), postTypeName, base,
                        data(Map.of("headline", "Annotated",
                                "body", "<h1 sh-field=\"b1a2c3dHeading\">Source text</h1>",
                                "b1a2c3dHeading", "What the curator typed")))));
        tx.executeWithoutResult(status -> entityManager
                .createQuery("update ShPostUnified p set p.data = :data where p.furl = :furl")
                .setParameter("data", "{\"headline\":\"Annotated\","
                        + "\"body\":\"<h1 sh-field=\\\"b1a2c3dHeading\\\">Source text</h1>\","
                        + "\"b1a2c3dHeading\":\"What the curator typed\","
                        + "\"strayKey\":\"nothing annotates this\"}")
                .setParameter("furl", base + "/annotated")
                .executeUpdate());
    }

    private void run(Op... ops) {
        shAgentBatchService.run(new ShAgentBatch(false, List.of(ops)), false, "tester");
    }

    /** The id of {@code /target}, resolved once the batch that created it has committed. */
    private String targetId() {
        if (targetId == null) {
            targetId = entityManager
                    .createQuery("select p.id from ShPostUnified p where p.furl = :furl", String.class)
                    .setParameter("furl", base + "/target")
                    .getResultStream().findFirst().orElseThrow();
        }
        return targetId;
    }

    /**
     * The system {@code File} post-type, found or created (SH470).
     *
     * <p>Found first, because it is instance-wide and this suite shares a JVM with everything else
     * that seeds it — a second row named {@code File} would make the type ambiguous for every other
     * test rather than for this one.</p>
     */
    private void filePostType() {
        ShPostType existing = entityManager
                .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                .setParameter("name", ShSystemPostType.FILE)
                .getResultStream().findFirst().orElse(null);
        if (existing != null) {
            return;
        }
        ShPostType type = new ShPostType();
        type.setName(ShSystemPostType.FILE);
        type.setTitle("File");
        type.setSystem((byte) 1);
        type.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        type.setOwner("admin");
        entityManager.persist(type);
        attr(type, ShSystemWidget.FILE, "FILE", 1, (byte) 1);
    }

    private void attr(ShPostType postType, String widgetName, String name, int ordinal, byte required) {
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
        attr.setIsTitle(ordinal == 1 ? (byte) 1 : (byte) 0);
        attr.setRequired(required);
        attr.setShWidget(widget);
        attr.setShPostType(postType);
        entityManager.persist(attr);
    }

    private JsonNode data(Map<String, String> values) {
        return objectMapper.valueToTree(new HashMap<>(values));
    }

    private String folder(String suffix) {
        return "folder:" + siteName + base + suffix;
    }

    private String post(String suffix) {
        return "post:" + siteName + base + suffix;
    }

    private ShAgentReport verify(String checks) {
        return shAgentVerifyService.verify(siteName, checks, null);
    }

    /** Findings of one rule, so an assertion names a rule rather than a list index. */
    private List<Finding> findings(String checks, String rule) {
        return verify(checks).findings().stream()
                .filter(finding -> rule.equals(finding.rule()))
                .toList();
    }

    // --- SH99: content ---

    @Test
    @DisplayName("a required field left empty is an error, and names the field")
    void anEmptyRequiredFieldIsAnError() {
        List<Finding> found = findings("content", "required-field-empty");

        assertThat(found).singleElement().satisfies(finding -> {
            assertThat(finding.address()).isEqualTo(post("/missing-headline"));
            assertThat(finding.field()).isEqualTo("headline");
            assertThat(finding.severity()).isEqualTo(ShAgentReport.ERROR);
            // P6: a finding an agent cannot act on has spent tokens to produce a feeling.
            // SH1009 changed what the fix says, and the change is the point: the remedy is no
            // longer "write it with an op" but "a publish refuses this page until you do" —
            // the rule is enforced at the writer now, and this report is the reader of it.
            assertThat(finding.fix()).contains("refuses this page");
        });
    }

    @Test
    @DisplayName("SH872: a key the post's own markup annotates is not an unknown field")
    void aMarkupAnnotatedKeyIsNotAnUnknownField() {
        // The assertion that would have caught it, at the boundary it crossed. SH872 gives the
        // annotated values back to the markup that carries them, so `Page` stops gaining a field
        // per captured block — and a curator's first edit through the Universal Editor then stores
        // a key the type does not declare. `unknown-field` is an ERROR, so without this rule an
        // annotated replica goes red on every block anybody touches.
        assertThat(findings("content", "unknown-field")).singleElement().satisfies(finding -> {
            assertThat(finding.field())
                    .as("the stray key is reported and the annotated one is not — the rule is "
                            + "still live, which is what makes the silence about the other key mean "
                            + "something")
                    .isEqualTo("strayKey");
            assertThat(finding.address()).isEqualTo(post("/annotated"));
        });
    }

    @Test
    @DisplayName("a reference to a post that does not exist is an error")
    void aDanglingReferenceIsAnError() {
        assertThat(findings("content", "dangling-reference")).singleElement().satisfies(finding -> {
            assertThat(finding.address()).isEqualTo(post("/dangling"));
            assertThat(finding.field()).isEqualTo("related");
        });
    }

    /**
     * SH470 — the fixture now holds three {@code File} posts, and this is what they are for.
     *
     * <p>{@code verify --site VigletOrg} reported <b>84 errors</b> on a replica whose every asset
     * answered 200: {@code ✗ dangling-reference · FILE — Field 'FILE' points at post
     * inter-greek-wght-normal-CkhJZR-_.woff2, which does not exist.} It does not exist because it is
     * not a post — a {@code File} post stores its own filename there, which is what
     * {@code fileNameOf} has always read it as. A gate that cries wolf is worse than one that stays
     * quiet, and 84 false errors on a run that also carried true ones is past the line where a
     * reader stops looking. On this path it is worse than noise: the replication checks exist so an
     * agent can prove a replica <em>without</em> a human, and an agent that cannot tell 84 false
     * errors from a real one has to escalate every run.</p>
     *
     * <p>The assertion is the count, and it is exact rather than a lower bound: the test above
     * already says the one real finding is right, so this one only has to say nothing joined it.</p>
     */
    @Test
    @DisplayName("SH470: a File post's own name is not an address, so uploaded assets are not findings")
    void aFilePostsOwnNameIsNotADanglingReference() {
        assertThat(findings("content", "dangling-reference"))
                .as("three uploaded assets, and the only finding is still the one post that "
                        + "genuinely points at nothing")
                .extracting(Finding::address)
                .containsExactly(post("/dangling"));
    }

    @Test
    @DisplayName("SH470: and the File widget still resolves a real reference, so missing-asset lives")
    void theFileWidgetIsStillAReferenceWidget() {
        // Why the exemption is the *field* and not the widget. Dropping `File` from
        // REFERENCE_WIDGETS would silence the 84 by deleting the only path to `missing-asset`, which
        // the test below asserts on a user field holding a real file post's id.
        assertThat(findings("content", "missing-asset"))
                .extracting(Finding::field)
                .containsExactly("hero");
    }

    @Test
    @DisplayName("a published post pointing at an unpublished one is an error")
    void aPublishedPostMayNotDependOnAnUnpublishedOne() {
        assertThat(findings("content", "unpublished-dependency")).singleElement()
                .satisfies(finding -> {
                    assertThat(finding.address()).isEqualTo(post("/needs-target"));
                    assertThat(finding.fix()).contains("post.publish");
                });
    }

    @Test
    @DisplayName("a folder with no posts and no children is a warning, not an error")
    void anEmptyFolderIsOnlyAWarning() {
        List<Finding> found = findings("content", "empty-folder");

        assertThat(found).singleElement().satisfies(finding -> {
            assertThat(finding.address()).isEqualTo(folder("/empty"));
            // Warning on purpose: a section under construction is not a broken site, and
            // an exit code that fails on one is an exit code nobody reads.
            assertThat(finding.severity()).isEqualTo(ShAgentReport.WARNING);
        });
    }

    @Test
    @DisplayName("a File field whose bytes are gone is an error")
    void aFileFieldWithNoBytesIsAnError() {
        assertThat(findings("content", "missing-asset")).singleElement().satisfies(finding -> {
            assertThat(finding.address()).isEqualTo(post("/hero-missing"));
            assertThat(finding.field()).isEqualTo("hero");
            assertThat(finding.fix()).contains("staticfile/upload");
        });
    }

    @Test
    @DisplayName("two published posts on one URL is an error, reported on both")
    void twoPostsOnOneUrlIsAnError() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> run(
                new Op(ShAgentBatchService.POST_UPSERT, post("/dup-a"), postTypeName, base,
                        data(Map.of("headline", "Dup A"))),
                new Op(ShAgentBatchService.POST_UPSERT, post("/dup-b"), postTypeName, base,
                        data(Map.of("headline", "Dup B"))),
                new Op(ShAgentBatchService.POST_PUBLISH, post("/dup-a"), null, null, null),
                new Op(ShAgentBatchService.POST_PUBLISH, post("/dup-b"), null, null, null)));
        // The write surface refuses an occupied address (SH134), so the collision is
        // forced at the row level — which is also how it arrives in real data: an import,
        // a restore, or a pre-SH134 move.
        tx.executeWithoutResult(status -> entityManager.createQuery(
                        "update ShPostUnified p set p.furl = :furl "
                                + "where p.furl = :old and p.state = 'PUBLISHED'")
                .setParameter("furl", base + "/dup-a")
                .setParameter("old", base + "/dup-b")
                .executeUpdate());

        assertThat(findings("content", "duplicate-url")).hasSize(2)
                .allSatisfy(finding -> assertThat(finding.severity()).isEqualTo(ShAgentReport.ERROR));
    }

    /**
     * SH720 — the case the instrument could not see, which is the case that produces the refusal.
     *
     * <h2>The promise that was false</h2>
     *
     * <p>{@code duplicate-url}'s refusal ends <i>"GET /api/v2/agent/verify reports this as
     * duplicate-url with both addresses"</i>. This check iterated <b>published</b> rows only, so
     * the pair of drafts SH719's race left behind came back {@code report ok}: the agent is
     * refused, sent to an instrument, and told nothing is wrong. Its next move is to disbelieve
     * one of two answers, and neither choice is safe.</p>
     *
     * <p>Two unpublished rows, so nothing here is visible to a visitor — which was the argument
     * for not looking. It does not survive contact with the agent surface: {@code ShAddressResolver}
     * refuses {@code post:<site>/<furl>} whatever the state, so <b>no write can name either post</b>,
     * and this is the agent's instrument.</p>
     */
    @Test
    void twoDraftsOnOneUrlAreReportedToo() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> run(
                new Op(ShAgentBatchService.POST_UPSERT, post("/draft-dup-a"), postTypeName, base,
                        data(Map.of("headline", "Draft Dup A"))),
                new Op(ShAgentBatchService.POST_UPSERT, post("/draft-dup-b"), postTypeName, base,
                        data(Map.of("headline", "Draft Dup B")))));
        // Neither is published, and the collision is forced at the row level for the reason the
        // test above records — and now for one more: SH719's unique index refuses it through any
        // write, so a bulk update is the only way to reach the state a pre-SH719 instance holds.
        tx.executeWithoutResult(status -> entityManager.createQuery(
                        "update ShPostUnified p set p.furl = :furl "
                                + "where p.furl = :old and p.state = 'DRAFT'")
                .setParameter("furl", base + "/draft-dup-a")
                .setParameter("old", base + "/draft-dup-b")
                .executeUpdate());

        // Filtered to this test's own URL: the class shares one site across methods, so the
        // published pair above is still reported and an unfiltered count would make these two
        // tests order-dependent on each other.
        assertThat(findings("content", "duplicate-url").stream()
                .filter(finding -> finding.address().contains("/draft-dup-a")).toList())
                .as("""
                        before SH720 this was an empty list, and the refusal that sent the agent \
                        here said it would not be.""")
                .hasSize(2)
                .allSatisfy(finding -> {
                    assertThat(finding.severity()).isEqualTo(ShAgentReport.ERROR);
                    assertThat(finding.message())
                            .as("the harm named is the one that applies: no visitor sees this, and "
                                    + "no write can name either post")
                            .contains("not all of them published")
                            .contains("no write can name either post");
                });
    }

    /**
     * SH720 — and an ordinary published page is still not its own duplicate.
     *
     * <p>The old check looked at published rows alone <em>because</em> a post's DRAFT and PUBLISHED
     * rows share a furl (SH81), so counting rows makes every published page a collision with
     * itself. Counting distinct post <b>ids</b> is what makes widening safe, and this is that claim
     * as an assertion rather than as a paragraph: without it, the widening above would fire on
     * every edit in the instance and the check would be switched off within a day.</p>
     */
    @Test
    void aPostsOwnDraftAndPublishedRowsAreNotADuplicate() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> run(
                new Op(ShAgentBatchService.POST_UPSERT, post("/two-states"), postTypeName, base,
                        data(Map.of("headline", "Two States"))),
                new Op(ShAgentBatchService.POST_PUBLISH, post("/two-states"), null, null, null)));

        assertThat(findings("content", "duplicate-url"))
                .as("one post with two rows at one URL is one claimant, not two")
                .noneSatisfy(finding -> assertThat(finding.address()).contains("/two-states"));
    }

    // --- SH100: routes ---

    @Test
    @DisplayName("a link matching no published URL is a warning; an external link is not a finding")
    void anUnresolvedInternalLinkIsAWarning() {
        List<Finding> found = findings("routes", "link-unresolved");

        assertThat(found).singleElement().satisfies(finding -> {
            assertThat(finding.address()).isEqualTo(post("/broken-links"));
            assertThat(finding.message()).contains(base + "/nowhere");
            assertThat(finding.severity()).isEqualTo(ShAgentReport.WARNING);
        });
        // The external href and the resolvable internal one produce nothing at all.
        assertThat(found).noneMatch(finding -> finding.message().contains("example.com"));
    }

    @Test
    @DisplayName("an image whose bytes are gone is an error — a visitor gets a 404")
    void aMissingAssetIsAnError() {
        assertThat(findings("routes", "asset-missing")).singleElement().satisfies(finding -> {
            assertThat(finding.address()).isEqualTo(post("/broken-links"));
            assertThat(finding.severity()).isEqualTo(ShAgentReport.ERROR);
        });
    }

    @Test
    @DisplayName("a missing alt attribute is reported; an explicit alt is not")
    void aMissingAltIsReportedButAnExplicitOneIsNot() {
        List<Finding> found = findings("routes", "image-no-alt");

        assertThat(found).singleElement()
                .satisfies(finding -> assertThat(finding.address()).isEqualTo(post("/broken-links")));
    }

    @Test
    @DisplayName("only published rows are route-checked — a draft's broken link is work in progress")
    void draftsAreNotRouteChecked() {
        // /dangling is draft-only and has no markup; /missing-headline is draft-only with a
        // paragraph. Neither may appear under a routes rule.
        assertThat(verify("routes").findings())
                .noneMatch(finding -> post("/missing-headline").equals(finding.address()));
    }

    // --- the report itself ---

    @Test
    @DisplayName("ok tracks errors, not warnings")
    void okTracksErrorsAndNotWarnings() {
        ShAgentReport report = verify("content,routes");

        assertThat(report.errors()).isPositive();
        assertThat(report.warnings()).isPositive();
        assertThat(report.ok()).isFalse();
        assertThat(report.checked()).isPositive();
        assertThat(report.checks()).containsExactly("content", "routes");
    }

    @Test
    @DisplayName("a shortened report says so — a truncated defect list reads as a shorter one")
    void truncationIsNeverSilent() {
        ShAgentReport report = shAgentVerifyService.verify(siteName, "content,routes", 1);

        assertThat(report.findings()).hasSize(1);
        assertThat(report.truncated()).isTrue();
        // The counts describe the whole run, so the caller can tell how much it is missing.
        assertThat(report.found()).isGreaterThan(1);
        assertThat(ShAgentVerifyTerse.report(report)).contains("truncated");
    }

    @Test
    @DisplayName("the report is byte-stable across runs")
    void theReportIsByteStable() {
        // A report that reordered itself between runs could not be diffed in a PR, which
        // is most of what makes it useful to a human reviewer (P5).
        assertThat(ShAgentVerifyTerse.report(verify("content,routes")))
                .isEqualTo(ShAgentVerifyTerse.report(verify("content,routes")));
    }

    @Test
    @DisplayName("an unknown check group teaches the vocabulary rather than running nothing")
    void anUnknownCheckTeachesTheVocabulary() {
        assertThatThrownBy(() -> shAgentVerifyService.verify(siteName, "contnet", null))
                .isInstanceOf(ShAgentCheck.UnknownCheckException.class);
    }

    @Test
    @DisplayName("narrowing the checks narrows the findings")
    void checksNarrowTheReport() {
        assertThat(verify("content").findings())
                .allSatisfy(finding -> assertThat(finding.check()).isEqualTo("content"));
    }

    // --- SH334: a site's front door ---

    /**
     * This class's own fixture is the case: every page it publishes lives under
     * {@code /verify-xxxx/…} and none is at {@code /}. That was true before SH334 and nothing
     * said so — which is the whole finding, since an agent following SH79 has no reason to
     * create a home page and the convention was never written down anywhere a machine reads.
     */
    @Test
    @DisplayName("SH334: a site that publishes pages and has none at '/' is warned, with its address")
    void aSiteWithNoHomePageIsWarned() {
        List<Finding> found = findings("routes", "no-home-page");

        assertThat(found).singleElement().satisfies(finding -> {
            assertThat(finding.address()).isEqualTo("site:" + siteName);
            assertThat(finding.severity())
                    .as("a site addressed entirely from an external front end's router is a "
                            + "legitimate design, so this cannot fail the gate (SH99)")
                    .isEqualTo(ShAgentReport.WARNING);
            assertThat(finding.fix()).contains("post.move").contains("\"to\":\"post:" + siteName + "/\"");
        });
    }

    @Test
    @DisplayName("SH334: a site with a page at '/' is not warned")
    void aSiteWithAHomePageIsNotWarned() {
        String home = "sh334-" + UUID.randomUUID().toString().substring(0, 8);
        run(new Op(ShAgentBatchService.SITE_UPSERT, "site:" + home, null, null, null),
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + home + "/", postTypeName,
                        "/", data(Map.of("headline", "Home"))),
                new Op(ShAgentBatchService.POST_PUBLISH, "post:" + home + "/", null, null, null));

        assertThat(shAgentVerifyService.verify(home, "routes", null).findings())
                .filteredOn(finding -> "no-home-page".equals(finding.rule()))
                .isEmpty();
    }

    /**
     * The half that keeps the rule from firing at every agent halfway through a build: a site
     * with nothing published has no front door missing, and being told off for the state every
     * build passes through is how a warning becomes one people silence.
     */
    @Test
    @DisplayName("SH334: a site that publishes nothing is not warned")
    void aSiteThatPublishesNothingIsNotWarned() {
        String quiet = "sh334-quiet-" + UUID.randomUUID().toString().substring(0, 8);
        run(new Op(ShAgentBatchService.SITE_UPSERT, "site:" + quiet, null, null, null));

        assertThat(shAgentVerifyService.verify(quiet, "routes", null).findings())
                .filteredOn(finding -> "no-home-page".equals(finding.rule()))
                .isEmpty();
    }

    // --- SH501: a rule for content posts, applied to a File ---

    /**
     * SH501 — three errors on a pixel-identical replica, all of them this rule.
     *
     * <p>A fidelity capture uploads its assets as {@code File} posts, and {@code url-missing} fired
     * on every one: {@code viglet-logo.png}, {@code inter-latin.woff2},
     * {@code plus-jakarta-sans-latin.woff2} — "published but has no friendly URL, so no route reaches
     * it" — on the same run where all three answered <b>200</b>. A {@code File} has no furl by design
     * and is reached at {@code /file_source/<id>/<name>}; the rule was asking a content-post question
     * of a type it does not apply to. Worse than noise: the fix it offered was a {@code post.move} to
     * "give it one", so an agent that trusted the error would have put a friendly URL on the type
     * defined by not having one.</p>
     *
     * <p>Its own site rather than this class's fixture, because <b>the assertion is the count of
     * errors and not the absence of one rule</b>: the state under test is a replica whose every
     * reference answers 200, and the only reading of that which keeps catching this family is that
     * such a run verifies clean. On the shared fixture — eight deliberate defects — that sentence
     * cannot be said.</p>
     */
    @Test
    @DisplayName("SH501: a published File with no friendly URL is not url-missing, and the run is clean")
    void aFileIsNotUrlMissing() {
        String site = "sh501-" + UUID.randomUUID().toString().substring(0, 8);
        run(new Op(ShAgentBatchService.SITE_UPSERT, "site:" + site, null, null, null),
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + site + "/", postTypeName, "/",
                        data(Map.of("headline", "Home"))),
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + site + "/logo.png",
                        ShSystemPostType.FILE, "/", data(Map.of("FILE", "viglet-logo.png"))),
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + site + "/inter.woff2",
                        ShSystemPostType.FILE, "/", data(Map.of("FILE", "inter-latin.woff2"))),
                new Op(ShAgentBatchService.POST_PUBLISH, "post:" + site + "/", null, null, null),
                new Op(ShAgentBatchService.POST_PUBLISH, "post:" + site + "/logo.png", null, null, null),
                new Op(ShAgentBatchService.POST_PUBLISH, "post:" + site + "/inter.woff2", null, null, null));
        // How an upload actually arrives: the write surface addresses a post by furl (SH79), so the
        // furl-less row a `staticfile/upload` produces has to be forced here.
        clearFurl(site, "/logo.png");
        clearFurl(site, "/inter.woff2");

        List<Finding> found = shAgentVerifyService.verify(site, null, null).findings();

        assertThat(found)
                .filteredOn(finding -> "url-missing".equals(finding.rule()))
                .as("a File never has a friendly URL — that is the type's design, not a defect")
                .isEmpty();
        assertThat(found)
                .filteredOn(finding -> ShAgentReport.ERROR.equals(finding.severity()))
                .as("a replica whose every reference answers 200 must verify clean")
                .isEmpty();
    }

    /**
     * The other half, and the reason the exemption is keyed on the post type rather than on the rule:
     * a content post with no furl is genuinely unreachable, and {@code post.move} is genuinely its
     * fix. Silencing {@code url-missing} outright would have cleared three false errors by deleting
     * the check that finds real ones — SH470's mistake-not-made, one rule along.
     */
    @Test
    @DisplayName("SH501: a content post with no friendly URL is still an error, with its post.move fix")
    void aContentPostWithNoUrlIsStillUrlMissing() {
        String site = "sh501-content-" + UUID.randomUUID().toString().substring(0, 8);
        run(new Op(ShAgentBatchService.SITE_UPSERT, "site:" + site, null, null, null),
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + site + "/orphan", postTypeName, "/",
                        data(Map.of("headline", "Orphan"))),
                new Op(ShAgentBatchService.POST_PUBLISH, "post:" + site + "/orphan", null, null, null));
        clearFurl(site, "/orphan");

        assertThat(shAgentVerifyService.verify(site, "routes", null).findings())
                .filteredOn(finding -> "url-missing".equals(finding.rule()))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.severity()).isEqualTo(ShAgentReport.ERROR);
                    assertThat(finding.address()).startsWith("id:");
                    assertThat(finding.fix()).contains("post.move");
                });
    }

    /**
     * Blanks one row's furl — the state an upload produces and the write surface cannot address.
     *
     * <p>Resolved through the site rather than by furl alone: a furl is unique within a site and not
     * across the instance, and this class shares a JVM with every other fixture. Select then update
     * by id, because a bulk update cannot carry an implicit join — and it stops at
     * {@code shSite.id}, which is the JPQL invariant {@code agents.md} names.</p>
     *
     * <p>Every row, not one: an upsert followed by a publish leaves a {@code DRAFT} and a
     * {@code PUBLISHED} row for one address, and an upload has no furl in either state.</p>
     */
    private void clearFurl(String site, String furl) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String siteId = entityManager
                    .createQuery("select s.id from ShSite s where s.name = :name", String.class)
                    .setParameter("name", site)
                    .getSingleResult();
            List<String> ids = entityManager.createQuery(
                            "select p.id from ShPostUnified p "
                                    + "where p.furl = :furl and p.shSite.id = :siteId", String.class)
                    .setParameter("furl", furl)
                    .setParameter("siteId", siteId)
                    .getResultList();
            assertThat(ids).as("the fixture must have written %s", furl).isNotEmpty();
            entityManager.createQuery("update ShPostUnified p set p.furl = null where p.id in :ids")
                    .setParameter("ids", ids)
                    .executeUpdate();
        });
    }

    // --- SH309: an address inside a message has to resolve ---

    /**
     * SH309 — every teaching message in {@code verify} is assembled by string concatenation, and
     * the only assertion any of them had was {@code contains("post:")}.
     *
     * <p>The failure mode is specific and has happened in this repository before: SH172's
     * {@code id:} fallback exists because an address was built in three places and one of them was
     * wrong. A message that names an address which does not resolve is trusted <em>less</em> than a
     * message with no address at all — the reader pastes it, gets a 404, and stops believing the
     * next one.</p>
     *
     * <p>Two things the extraction deliberately skips, and they are the interesting part:</p>
     *
     * <ul>
     *   <li><b>Placeholders.</b> A fix that teaches the <em>shape</em> — {@code post:<site>/<url>}
     *       — is not naming an object, and resolving it would be a category error.</li>
     *   <li><b>Rules that name something absent on purpose.</b> A finding about a missing thing has
     *       to be able to say which thing is missing. Those rules are declared below rather than
     *       excluded by a pattern, so adding one is a decision somebody makes in writing.</li>
     * </ul>
     */
    private static final Set<String> NAMES_SOMETHING_ABSENT = Set.of(
            // SH334's fix ends `"to":"post:<site>/"` — the home page the site has not got, which is
            // the whole finding. A move's destination is absent by construction, and naming the
            // concrete one beats teaching a placeholder the curator then has to fill in.
            //
            // The cost of declaring the *rule* rather than the *position* is real and worth stating:
            // a genuinely malformed address anywhere else in `no-home-page` would now pass. Pinning
            // the `to` instead would mean parsing the fix's JSON out of a sentence, which is a
            // second grammar to keep right — worse than the hole it closes.
            "no-home-page");

    private static final Pattern ADDRESS_IN_TEXT =
            Pattern.compile("\\b(?:site|folder|post|id):[^\\s\"'`,;)\\]}]+");

    @Test
    @DisplayName("SH309: every SH79 address a finding names resolves, or its rule declares it absent")
    void everyAddressInAMessageResolves() {
        List<String> broken = new ArrayList<>();
        for (Finding finding : verify(null).findings()) {
            for (String text : List.of(
                    finding.address() == null ? "" : finding.address(),
                    finding.message() == null ? "" : finding.message(),
                    finding.fix() == null ? "" : finding.fix())) {
                Matcher matcher = ADDRESS_IN_TEXT.matcher(text);
                while (matcher.find()) {
                    String candidate = trimPunctuation(matcher.group());
                    // A shape being taught, not an object being named.
                    if (candidate.contains("<") || candidate.contains(">")) {
                        continue;
                    }
                    if (NAMES_SOMETHING_ABSENT.contains(finding.rule())) {
                        continue;
                    }
                    try {
                        shAddressResolver.resolve(candidate);
                    } catch (RuntimeException unresolved) {
                        broken.add(finding.rule() + " -> " + candidate);
                    }
                }
            }
        }

        assertThat(broken)
                .as("""
                        Each line is a finding whose text names an address that does not resolve.                         Either build the address from ShAddress rather than by concatenation, or                         add the rule to NAMES_SOMETHING_ABSENT with the reason it names a thing                         that is missing on purpose.""")
                .isEmpty();
    }

    /** Trailing punctuation belongs to the sentence, not to the address. */
    private static String trimPunctuation(String candidate) {
        String trimmed = candidate;
        while (!trimmed.isEmpty() && ".,;:!?".indexOf(trimmed.charAt(trimmed.length() - 1)) >= 0) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
