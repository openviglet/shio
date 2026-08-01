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
                new Op(ShAgentBatchService.POST_PUBLISH, post("/needs-target"), null, null, null),
                new Op(ShAgentBatchService.POST_PUBLISH, post("/broken-links"), null, null, null),
                new Op(ShAgentBatchService.POST_PUBLISH, post("/fine"), null, null, null)));
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
            assertThat(finding.fix()).contains("post.upsert");
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
