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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentProofService;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.ShAgentChangesService;
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
import tools.jackson.databind.ObjectMapper;

/**
 * SH143 — a verify run scoped to what changed.
 *
 * <p>The full pass reads every post in every readable site. That is exactly right for CI and
 * roughly a hundred times more work than the call an agent makes after writing three pages,
 * so {@code folder}, {@code address} and {@code since} narrow the subjects.</p>
 *
 * <p><b>Three of these tests are about what scoping must not break</b>, and they are the
 * reason this is a refactor rather than a filter. Two lint rules ask cross-cutting questions
 * — is another published post claiming this URL, does this link land anywhere in the site —
 * and one asks whether a folder holds content. A scoped run that answered any of them from
 * its own subjects would invent defects that are not there ({@code link-unresolved} for every
 * link out of the scope, {@code empty-folder} for every folder it did not look inside) and
 * miss the one that is (a duplicate whose twin is outside). So the subjects narrow and the
 * indexes stay whole.</p>
 *
 * <p>The fourth property is honesty: a subset that reads as a full pass is worse than no
 * report, so a scoped run is {@code partial} and says what it covered.</p>
 *
 * <p>{@code shio.agent.changes.lag-millis=0} because the {@code since} case would otherwise
 * have to sleep past SH88's hold-back window to see its own write.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.agent.changes.lag-millis=0")
@TestInstance(Lifecycle.PER_CLASS)
// The last two cases add content to the shared fixture, so the order is declared rather than
// left to chance: a test whose result depends on which sibling ran first is not a test.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@com.viglet.shio.ShFixtureShapeGuard.BuildsDuplicateUrls(
        "the duplicate-url finding cannot be scoped without a duplicate to find (SH99/SH202)")
class ShAgentVerifyScopeTest {

    @Autowired
    private ShAgentVerifyService shAgentVerifyService;
    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShAgentProofService shAgentProofService;
    @Autowired
    private ShAgentChangesService shAgentChangesService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String postTypeName;
    private String base;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            siteName = "scope-site-" + UUID.randomUUID();
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

            ShPostType page = new ShPostType();
            postTypeName = "ScopedPage" + UUID.randomUUID().toString().substring(0, 8);
            page.setName(postTypeName);
            page.setTitle(postTypeName);
            page.setSystem((byte) 0);
            page.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            page.setOwner("admin");
            entityManager.persist(page);
            attr(page, ShSystemWidget.TEXT, "title", 1, (byte) 1);
            attr(page, ShSystemWidget.HTML_EDITOR, "body", 2, (byte) 0);
            attr(page, ShSystemWidget.TEXT, "needed", 3, (byte) 0, (byte) 1);
        });

        base = "/scope-" + UUID.randomUUID().toString().substring(0, 8);
        tx.executeWithoutResult(status -> shAgentBatchService.run(new ShAgentBatch(false, List.of(
                folder("/inside"),
                folder("/inside/deeper"),
                folder("/outside"),
                // In scope: a clean page, and one whose link points *outside* the scope. The
                // link must resolve — the site index is whole even when the subjects are not.
                page("/inside/home", "Home", "<p>See <a href=\"" + base + "/outside/far\">far</a>.</p>"),
                // In a *sub*folder of the scope, so `folder=` has to descend.
                page("/inside/deeper/nested", "Nested", "<p>Deep.</p>"),
                // Out of scope, and the target of the link above.
                page("/outside/far", "Far", "<p>Far away.</p>"),
                // Out of scope and broken, so a scoped run that reported it would be wrong.
                brokenPage("/outside/broken", "Broken"))),
                false, "tester"));
        tx.executeWithoutResult(status -> shAgentBatchService.run(new ShAgentBatch(false, List.of(
                new Op(ShAgentBatchService.POST_PUBLISH, "post:" + siteName + base + "/inside/home",
                        null, null, null),
                new Op(ShAgentBatchService.POST_PUBLISH, "post:" + siteName + base + "/outside/far",
                        null, null, null))),
                false, "tester"));
    }

    // --- helpers ---

    private void attr(ShPostType postType, String widgetName, String name, int ordinal, byte isTitle) {
        attr(postType, widgetName, name, ordinal, isTitle, (byte) 0);
    }

    private void attr(ShPostType postType, String widgetName, String name, int ordinal,
                      byte isTitle, byte required) {
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
        attr.setRequired(required);
        attr.setShWidget(widget);
        attr.setShPostType(postType);
        entityManager.persist(attr);
    }

    private Op folder(String chain) {
        return new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + siteName + base + chain,
                null, null, null);
    }

    private Op page(String url, String title, String body) {
        return new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + base + url,
                postTypeName, folderOf(url),
                objectMapper.valueToTree(Map.of("title", title, "body", body, "needed", "yes")));
    }

    /** A page with the required field left empty — one guaranteed error. */
    private Op brokenPage(String url, String title) {
        return new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + base + url,
                postTypeName, folderOf(url),
                objectMapper.valueToTree(Map.of("title", title, "needed", "")));
    }

    private String folderOf(String url) {
        return base + url.substring(0, url.lastIndexOf('/'));
    }

    private ShAgentReport full() {
        return shAgentVerifyService.verify(siteName, "content,routes", 500);
    }

    private ShAgentReport scoped(String folder, String address, String since) {
        return shAgentVerifyService.verify(siteName, "content,routes", 500, folder, address, since);
    }

    private static List<String> addresses(ShAgentReport report, String rule) {
        return report.findings().stream().filter(finding -> rule.equals(finding.rule()))
                .map(Finding::address).toList();
    }

    // --- the narrowing ---

    @Test
    @Order(10)
    @DisplayName("a folder scope covers the subtree and nothing outside it")
    void aFolderScopeCoversItsSubtree() {
        assertThat(addresses(full(), "required-field-empty"))
                .as("the full pass is the comparand: the defect is there to be found")
                .anySatisfy(address -> assertThat(address).endsWith("/outside/broken"));

        ShAgentReport scoped = scoped("folder:" + siteName + base + "/inside", null, null);

        assertThat(addresses(scoped, "required-field-empty")).isEmpty();
        assertThat(scoped.checked())
                .as("fewer objects examined is the entire point")
                .isLessThan(full().checked());
    }

    @Test
    @Order(11)
    @DisplayName("a folder scope descends into subfolders")
    void aFolderScopeDescends() {
        // A scope that stopped at the named folder would silently skip the page an agent had
        // just written one level down, which is the exact call this exists for.
        ShAgentReport scoped = scoped("folder:" + siteName + base + "/inside", null, null);

        assertThat(scoped.scope()).contains("subfolder");
        assertThat(scoped.checked()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(12)
    @DisplayName("an address scope checks exactly the posts named")
    void anAddressScopeChecksWhatItNames() {
        ShAgentReport scoped = scoped(null,
                "post:" + siteName + base + "/outside/broken,post:" + siteName + base + "/inside/home",
                null);

        assertThat(addresses(scoped, "required-field-empty"))
                .anySatisfy(address -> assertThat(address).endsWith("/outside/broken"));
        assertThat(scoped.scope()).contains("2 address(es)");
    }

    @Test
    @Order(90)
    @DisplayName("a since scope covers what changed after the cursor and nothing before it")
    void aSinceScopeCoversWhatChanged() {
        String cursor = shAgentChangesService.changes(siteName, null, 1, false).cursor();

        String url = base + "/inside/late-" + UUID.randomUUID().toString().substring(0, 6);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                shAgentBatchService.run(new ShAgentBatch(false, List.of(
                        brokenPage(url.substring(base.length()), "Late"))), false, "tester"));

        ShAgentReport scoped = scoped(null, null, cursor);

        assertThat(addresses(scoped, "required-field-empty"))
                .as("the write after the cursor is what the run is for")
                .anySatisfy(address -> assertThat(address).endsWith(url));
        assertThat(addresses(scoped, "required-field-empty"))
                .as("the defect that predates the cursor is somebody else's run")
                .noneSatisfy(address -> assertThat(address).endsWith("/outside/broken"));
        assertThat(scoped.scope()).contains("changed since");
    }

    // --- what scoping must not break ---

    @Test
    @Order(20)
    @DisplayName("a link into content outside the scope still resolves")
    void aLinkOutOfScopeStillResolves() {
        // If the published-URL index narrowed with the subjects, every link that pointed out
        // of the scope would report link-unresolved — a run that reported a defect per page
        // for content that is perfectly fine.
        ShAgentReport scoped = scoped("folder:" + siteName + base + "/inside", null, null);

        assertThat(addresses(scoped, "link-unresolved")).isEmpty();
    }

    @Test
    @Order(21)
    @DisplayName("a folder holding a post outside the scope is not called empty")
    void aFolderWithOutOfScopeContentIsNotEmpty() {
        // `deeper` holds a page. Scope to its *parent* only: `deeper` is a subject (it is in
        // the subtree) but its post may not be loaded — and "empty" must still be false.
        ShAgentReport scoped = scoped("folder:" + siteName + base + "/inside/deeper", null, null);

        assertThat(addresses(scoped, "empty-folder")).isEmpty();
    }

    @Test
    @Order(99)
    @DisplayName("a duplicate URL whose twin is outside the scope is still reported")
    void aDuplicateWithAnOutOfScopeTwinIsFound() {
        String clash = base + "/inside/clash";
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        // Two published posts on one URL: one inside the scope, one outside it. A run that
        // grouped its own loaded rows would see one of each and call both unique.
        tx.executeWithoutResult(status -> shAgentBatchService.run(new ShAgentBatch(false, List.of(
                page("/inside/clash", "Inside", "<p>a</p>"),
                new Op(ShAgentBatchService.POST_PUBLISH, "post:" + siteName + clash,
                        null, null, null))), false, "tester"));
        tx.executeWithoutResult(status -> shAgentBatchService.run(new ShAgentBatch(false, List.of(
                page("/outside/twin", "Twin", "<p>b</p>"),
                new Op(ShAgentBatchService.POST_PUBLISH,
                        "post:" + siteName + base + "/outside/twin", null, null, null))),
                false, "tester"));
        // Give it the clashing URL by hand, on both rows — only the PUBLISHED one counts, and a
        // DRAFT left behind on the old URL would be a second fixture nobody asked for.
        // post.move refuses an occupied target, which is itself the guard; what this reproduces
        // is the state that exists once something bypassed it.
        tx.executeWithoutResult(status -> assertThat(entityManager
                .createQuery("update ShPostUnified p set p.furl = :furl where p.furl = :old")
                .setParameter("furl", clash)
                .setParameter("old", base + "/outside/twin")
                .executeUpdate())
                .as("the fixture has to actually land, or this test passes by not looking")
                .isPositive());

        ShAgentReport scoped = scoped("folder:" + siteName + base + "/inside", null, null);

        assertThat(addresses(scoped, "duplicate-url")).isNotEmpty();
    }

    // --- honesty ---

    @Test
    @Order(30)
    @DisplayName("a scoped run says it is partial; a full one does not")
    void aScopedRunSaysSo() {
        ShAgentReport scoped = scoped("folder:" + siteName + base + "/inside", null, null);

        assertThat(scoped.partial()).isTrue();
        assertThat(scoped.scope()).isNotBlank();
        assertThat(ShAgentVerifyTerse.report(scoped)).contains("partial");

        ShAgentReport whole = full();
        assertThat(whole.partial())
                .as("absent, not false: a flag that is always there is a flag nobody reads")
                .isNull();
        assertThat(whole.scope()).isNull();
    }

    @Test
    @Order(31)
    @DisplayName("a folder scope that is not a folder is a teaching 400, not an empty green run")
    void aBadFolderScopeTeaches() {
        assertThatThrownBy(() -> scoped("post:" + siteName + base + "/inside/home", null, null))
                .isInstanceOf(ShAgentVerifyService.UnknownFolderScopeException.class)
                .hasMessageContaining("not to a folder");
    }

    @Test
    @Order(40)
    @DisplayName("SH228: a folder address is that folder, while `folder=` is that folder's subtree")
    void aFolderAddressIsASubjectNotASubtree() {
        // The distinction that makes both params worth having. A write proof names the folders a
        // run created; scoping those to their subtrees would make the proof's reach depend on
        // whatever happens to sit underneath, which is not what "prove what I just wrote" asks.
        //
        // The pair is built so the two answers differ: the parent holds a subfolder (so it is not
        // empty), and the child holds nothing (so it is). `empty-folder` therefore fires for the
        // child and never for the parent, which is what lets each assertion below mean one thing.
        String tree = base + "-sh228-tree";
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                shAgentBatchService.run(new ShAgentBatch(false, List.of(
                        new Op(ShAgentBatchService.FOLDER_UPSERT,
                                "folder:" + siteName + tree, null, null, null),
                        new Op(ShAgentBatchService.FOLDER_UPSERT,
                                "folder:" + siteName + tree + "/child", null, null, null))),
                        false, "tester"));
        String parent = "folder:" + siteName + tree;
        String child = parent + "/child";

        assertThat(findings(scoped(null, child, null)))
                .as("""
                        a folder handed to `address=` is a subject the lint reports on. Before                         SH228 it resolved and was then looked up as a post, found nothing, and                         was dropped without a word — the scope still said "1 address(es)".""")
                .contains("empty-folder");
        assertThat(findings(scoped(null, parent, null)))
                .as("and only that folder: the child is not in scope, so its warning is not this "
                        + "run's to report")
                .doesNotContain("empty-folder");
        assertThat(findings(scoped(parent, null, null)))
                .as("`folder=` is unchanged and still descends, which is why the two are not the "
                        + "same parameter wearing two names")
                .contains("empty-folder");
    }

    private static List<String> findings(ShAgentReport report) {
        return report.findings().stream().map(Finding::rule).toList();
    }

    @Test
    @Order(41)
    @DisplayName("SH228: a batch of folder upserts comes back with a proof")
    void aFolderOnlyWriteIsProven() {
        // The defect. The proof collected `post:` addresses and dropped everything else, so a
        // scaffolding batch — "create the structure, then fill it", a real and common first step
        // — returned NO proof key at all, which a caller cannot tell apart from "asked for, and
        // nothing was wrong". The lint has had folder rules the whole time; they were simply
        // never asked for.
        String scaffold = base + "-sh228";
        ShAgentChangeSet written = new TransactionTemplate(transactionManager).execute(status ->
                shAgentBatchService.run(new ShAgentBatch(false, List.of(
                        new Op(ShAgentBatchService.FOLDER_UPSERT,
                                "folder:" + siteName + scaffold, null, null, null))),
                        false, "tester"));

        ShAgentChangeSet proven = shAgentProofService.attach(written, true);

        assertThat(proven.proof())
                .as("""
                        a write that asked to be proved gets a report; an absent one is not a                         weaker answer than a clean one, it is a different answer and the caller                         cannot tell which it got (SH228).""")
                .isNotNull();
        assertThat(proven.proof().findings())
                .as("""
                        and it is the folder's own rule that fires: a folder created with nothing                         in it yet is exactly the "looks done but isn't" the lint warns about,                         which is the finding a scaffolding run most wants back.""")
                .anySatisfy(finding -> assertThat(finding.rule()).isEqualTo("empty-folder"));
    }

    @Test
    @Order(42)
    @DisplayName("SH228: a run with nothing lintable is told so, rather than told nothing")
    void anEmptyProofIsExplicit() {
        // The other half of the same ambiguity, and the trap in fixing it: the empty case must
        // not fall through to a blank scope, because blank means the FULL pass — a run that
        // wrote nothing lintable would have linted the whole instance and reported findings
        // about objects it never touched.
        ShAgentChangeSet written = new TransactionTemplate(transactionManager).execute(status ->
                shAgentBatchService.run(new ShAgentBatch(false, List.of(
                        new Op(ShAgentBatchService.SITE_UPSERT,
                                "site:sh228-" + UUID.randomUUID().toString().substring(0, 8),
                                null, null, null))),
                        false, "tester"));

        ShAgentChangeSet proven = shAgentProofService.attach(written, true);

        assertThat(proven.proof()).isNotNull();
        assertThat(proven.proof().ok()).isTrue();
        assertThat(proven.proof().findings()).isEmpty();
        assertThat(proven.proof().notes())
                .as("""
                        an empty report that does not say why it is empty reads as a clean bill                         of health for content it never looked at — so the note names the reason                         rather than leaving the caller to infer it (SH228).""")
                .isNotEmpty();
    }
}
