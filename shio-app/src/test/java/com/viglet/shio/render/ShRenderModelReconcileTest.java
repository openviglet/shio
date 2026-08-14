/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
import com.viglet.shio.api.agent.verify.ShAgentVerifyService;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH289 — an instance created by an older build can be repaired, and says it needs to be.
 *
 * <p>The fixture is the shape SH152's rule leaves behind and the reason this task exists: a
 * {@code Region} created before SH277, so it has {@code HTML} and no title field. {@code ensure}
 * will never touch it — correctly, since a startup that rewrites somebody's model is worse — so an
 * instance in that state has templates it cannot address and no way to fix them.</p>
 *
 * <p>Its own database, for {@link ShPageRenderTest}'s reason (SH246), and because it deliberately
 * creates a <em>malformed</em> system post-type that every other test in this package would then
 * inherit.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh289;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShRenderModelReconcileTest {

    @Autowired
    private ShRenderPostTypes shRenderPostTypes;
    @Autowired
    private ShAgentVerifyService verifyService;
    @Autowired
    private ShPostTypeRepository shPostTypeRepository;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ShAgentBatchService batchService;
    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("a pre-SH277 Region is reported as missing its title field, then repaired")
    void reconcileAddsTheMissingField() {
        oldRegionWithNoTitleField();

        assertThat(shRenderPostTypes.reconcile(true))
                .as("""
                        a dry run answers from the same code that writes (SH184's rule for \
                        site.upsert): a plan produced by different code is a plan that can come \
                        back greener than the run it predicts.""")
                .anySatisfy(line -> assertThat(line).contains(ShSystemPostType.REGION)
                        .contains(ShSystemPostTypeAttr.TITLE));
        assertThat(fieldNames(ShSystemPostType.REGION))
                .as("and it wrote nothing")
                .doesNotContain(ShSystemPostTypeAttr.TITLE);

        assertThat(shRenderPostTypes.reconcile(false)).isNotEmpty();

        assertThat(fields(ShSystemPostType.REGION))
                .filteredOn(field -> ShSystemPostTypeAttr.TITLE.equals(field.name()))
                .singleElement()
                .satisfies(field -> assertThat(field.isTitle())
                        .as("the flag is the whole point — without it the writer derives no title "
                                + "and the template stays unaddressable")
                        .isTrue());
        assertThat(shRenderPostTypes.reconcile(true))
                .as("idempotent: a second run has nothing to add, so an operator can run it "
                        + "after every upgrade without reading the changelog first")
                .isEmpty();
    }

    @Test
    @DisplayName("SH295: the drift is readable without a write verb, and says the same thing")
    void driftIsReadableWithoutWriting() {
        oldRegionWithNoTitleField();

        assertThat(shRenderPostTypes.drift())
                .as("""
                        the fact an operator needs BEFORE a template is written. SH289 surfaced                         it only as untitled-template, a finding on a post, so an instance that had                         run ensure and written nothing looked exactly like a sound one.""")
                .anySatisfy(drifted -> assertThat(drifted.postType())
                        .isEqualTo(ShSystemPostType.REGION));
        assertThat(shRenderPostTypes.drift())
                .filteredOn(drifted -> ShSystemPostType.REGION.equals(drifted.postType()))
                .singleElement()
                .satisfies(drifted -> assertThat(drifted.missingFields())
                        .contains(ShSystemPostTypeAttr.TITLE));

        assertThat(shRenderPostTypes.drift().stream()
                .map(drifted -> drifted.postType() + ": "
                        + String.join(", ", drifted.missingFields()))
                .toList())
                .as("""
                        one computation, two doors: reconcile FORMATS the read rather than                         repeating the comparison, so a plan cannot describe a repair different                         from the one that runs (SH184's rule) — and the creation and repair paths                         cannot drift apart the way they did before SH289.""")
                .isEqualTo(shRenderPostTypes.reconcile(true));

        assertThat(fieldNames(ShSystemPostType.REGION))
                .as("and reading it wrote nothing")
                .doesNotContain(ShSystemPostTypeAttr.TITLE);

        shRenderPostTypes.reconcile(false);
        assertThat(shRenderPostTypes.drift())
                .as("a repaired instance reports nothing, which is what makes this answerable at "
                        + "all: silence has to mean sound rather than unasked")
                .noneSatisfy(drifted -> assertThat(drifted.postType())
                        .isEqualTo(ShSystemPostType.REGION));
    }

    @Test
    @DisplayName("reconcile never changes a field the type already has")
    void reconcileIsAdditiveOnly() {
        // Deliberately not "the type is missing TITLE, so reconcile runs": the other test in this
        // class repairs it, and a PER_CLASS fixture makes every mutating test an input to the
        // others (SH239's confound, and the one SH272's test met head-on). A curator's edit that
        // is present in either order is the honest fixture.
        oldRegionWithNoTitleField();
        java.util.concurrent.atomic.AtomicReference<String> chosen =
                new java.util.concurrent.atomic.AtomicReference<>();
        curatorChangedTheHtmlWidget(chosen);

        shRenderPostTypes.reconcile(false);

        assertThat(fields(ShSystemPostType.REGION))
                .filteredOn(field -> ShSystemPostTypeAttr.HTML.equals(field.name()))
                .singleElement()
                .satisfies(field -> assertThat(field.widget())
                        .isNotEqualTo("Ace Editor - HTML")
                        .as("additive only, so a curator's edit survives — and it is why the "
                                + "SH252 widget drift is deliberately out of scope: changing a "
                                + "field is a different decision from adding a missing one, and "
                                + "bundling them would have an operator accept one by fixing the "
                                + "other")
                        .isEqualTo(chosen.get()));
    }

    @Test
    @DisplayName("verify reports the symptom: a template nothing can address")
    void verifyReportsAnUntitledTemplate() {
        oldRegionWithNoTitleField();
        String site = untitledRegionIn();

        ShAgentReport report = verifyService.verify(site, "content", 200);

        assertThat(report.findings())
                .filteredOn(finding -> "untitled-template".equals(finding.rule()))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.severity())
                            .as("""
                                    an error, unlike the unused-template warnings beside it: a \
                                    region waiting for its layout is work in progress, while a \
                                    template with no title cannot be named by any sh-region slot \
                                    or postTypeLayout binding whatever anybody intended.""")
                            .isEqualTo("error");
                    assertThat(finding.fix())
                            .as("P6 — and the fix names the repair, because the cause is usually "
                                    + "the instance's model rather than the post")
                            .contains("reconcile");
                });
        assertThat(report.ok()).isFalse();
    }

    // --- fixture ---

    /**
     * A {@code Region} as an instance created before SH277 has it: {@code HTML}, no title field.
     *
     * <p>It <b>forces</b> that state rather than creating it once, because the tests in this class
     * repair the type and a {@code PER_CLASS} fixture makes every mutating test an input to the
     * others. The first version returned early when the type existed, and the additive-only test
     * then ran against an already-repaired type — where {@code reconcile} finds nothing missing and
     * writes nothing, so the assertion passed whatever the code did. Proved by mutation: making
     * {@code reconcile} overwrite every field did not fail it.</p>
     */
    private void oldRegionWithNoTitleField() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShPostType found = shPostTypeRepository.findByName(ShSystemPostType.REGION);
            if (found != null) {
                // The cascade gotcha, in a test fixture: ShPostTypeAttr maps shWidget as
                // @ManyToOne(cascade = ALL), so removing an attribute cascades a REMOVE into the
                // *shared* widget catalog. The first version of this method did exactly that and
                // deleted `Text` and `Ace Editor - Javascript` for the whole JVM — after which the
                // failure was "Widget 'Text' is not seeded" from production code, several steps
                // from the cause. ShPostTypeAdminService.detach is the same three lines, for the
                // same reason.
                found.getShPostTypeAttrs().stream()
                        .filter(attr -> !ShSystemPostTypeAttr.HTML.equals(attr.getName()))
                        .forEach(attr -> {
                            attr.setShWidget(null);
                            attr.setShParentPostTypeAttr(null);
                            attr.setShPostType(null);
                        });
                found.getShPostTypeAttrs().removeIf(attr ->
                        !ShSystemPostTypeAttr.HTML.equals(attr.getName()));
                found.getShPostTypeAttrs().forEach(attr ->
                        attr.setShWidget(widget("Ace Editor - HTML")));
                shPostTypeRepository.saveAndFlush(found);
                return;
            }
            ShPostType type = new ShPostType();
            type.setName(ShSystemPostType.REGION);
            type.setNamePlural("Regions");
            type.setTitle("Region");
            type.setDescription("A pre-SH277 Region");
            type.setSystem((byte) 1);
            type.setDate(Instant.now());
            type.setOwner("admin");
            ShPostTypeAttr html = new ShPostTypeAttr();
            html.setName(ShSystemPostTypeAttr.HTML);
            html.setLabel("HTML");
            html.setDescription("HTML");
            html.setOrdinal(1);
            html.setRequired((byte) 1);
            html.setIsTitle((byte) 0);
            html.setIsSummary((byte) 0);
            html.setShWidget(widget("Ace Editor - HTML"));
            html.setShPostType(type);
            type.setShPostTypeAttrs(new java.util.HashSet<>(List.of(html)));
            shPostTypeRepository.save(type);
        });
    }

    /** A curator's own edit to a field the shipped model also declares. */
    private void curatorChangedTheHtmlWidget(
            java.util.concurrent.atomic.AtomicReference<String> chosen) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShPostType type = shPostTypeRepository.findByName(ShSystemPostType.REGION);
            type.getShPostTypeAttrs().stream()
                    .filter(attr -> ShSystemPostTypeAttr.HTML.equals(attr.getName()))
                    .forEach(attr -> {
                        ShWidget other = someOtherWidget();
                        chosen.set(other.getName());
                        attr.setShWidget(other);
                    });
            shPostTypeRepository.save(type);
        });
    }

    /** A site holding one Region post that landed with no title, which is the symptom. */
    private String untitledRegionIn() {
        String name = "sh289-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(name);
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

            ShPostUnified post = new ShPostUnified();
            post.setId(UUID.randomUUID().toString());
            post.setState(ShPostState.DRAFT);
            post.setShSite(site);
            post.setShFolder(root);
            post.setShPostType(shPostTypeRepository.findByName(ShSystemPostType.REGION));
            post.setTitle("");
            post.setFurl("/r-untitled");
            post.setData("{\"HTML\":\"<nav>no title</nav>\"}");
            post.setDate(when);
            post.setOwner("admin");
            entityManager.persist(post);
        });
        return name;
    }

    /**
     * Any seeded widget that is not the one the shipped model declares for {@code HTML}.
     *
     * <p>Chosen from what the instance actually has rather than named: this database seeds a
     * subset, and asking for {@code Text} by name failed here while {@code Ace Editor - HTML}
     * resolved — which would have made the test's subject "a widget that happens to exist"
     * instead of "a widget the curator chose".</p>
     */
    private ShWidget someOtherWidget() {
        for (ShWidget candidate : shWidgetRepository.findAll()) {
            if (!"Ace Editor - HTML".equals(candidate.getName())) {
                return candidate;
            }
        }
        throw new IllegalStateException("no second widget is seeded");
    }

    /**
     * The seeded widget by name, through the repository the production code uses.
     *
     * <p>Not a JPQL query of its own: the obvious {@code select w from ShWidget w where w.name = :n}
     * answered {@code NoResultException} for a widget that certainly exists, and the repository
     * finder is both proven and the thing this test should agree with.</p>
     */
    private ShWidget widget(String name) {
        // findFirstByName since SH753 deleted the throwing twin — and this test should agree with
        // what production resolves through, which is now the only resolver there is.
        ShWidget found = shWidgetRepository.findFirstByName(name);
        assertThat(found).as("widget '%s' is not seeded; seeded are %s", name,
                shWidgetRepository.findAll().stream().map(ShWidget::getName).sorted().toList())
                .isNotNull();
        return found;
    }

    // --- SH326: the repair verb, from the surface an MCP-only client actually has ---

    /**
     * SH186 made {@code ensure} reachable as {@code render.provision}; {@code reconcile} stayed
     * HTTP-only, so an older instance could be <em>diagnosed</em> by an MCP-only client and not
     * repaired by one. `untitled-template`'s own fix named the route — the SH328 shape again: a
     * surface instructing a write no agent op could perform.
     */
    @Test
    @DisplayName("SH326: reconcile is reachable as an op, and a dry run still writes nothing")
    void reconcileIsReachableAsAnOp() {
        oldRegionWithNoTitleField();

        ShAgentChangeSet planned = batchService.run(
                new ShAgentBatch(true, List.of(reconcileOp())), false, "tester");

        assertThat(planned.results()).singleElement().satisfies(row ->
                assertThat(row.detail()).contains("would add")
                        .contains(ShSystemPostTypeAttr.TITLE));
        assertThat(fieldNames(ShSystemPostType.REGION))
                .as("the plan wrote nothing, which is the property every other op has")
                .doesNotContain(ShSystemPostTypeAttr.TITLE);

        batchService.run(new ShAgentBatch(false, List.of(reconcileOp())), false, "tester");

        assertThat(fieldNames(ShSystemPostType.REGION)).contains(ShSystemPostTypeAttr.TITLE);
    }

    /**
     * The division SH289 drew, held at the op: {@code ensure} is safe to call unattended and
     * {@code reconcile} changes a type the instance already has, so the repair is opt-in and never
     * implied. Without this, an agent provisioning on startup would silently reshape a curator's
     * types.
     */
    @Test
    @DisplayName("SH326: provisioning without the flag never repairs")
    void provisioningAloneNeverRepairs() {
        oldRegionWithNoTitleField();

        batchService.run(new ShAgentBatch(false, List.of(new ShAgentBatch.Op(
                ShAgentBatchService.RENDER_PROVISION, null, null, null, null))), false, "tester");

        assertThat(fieldNames(ShSystemPostType.REGION))
                .doesNotContain(ShSystemPostTypeAttr.TITLE);
    }

    private ShAgentBatch.Op reconcileOp() {
        return new ShAgentBatch.Op(ShAgentBatchService.RENDER_PROVISION, null, null, null, null,
                objectMapper.valueToTree(java.util.Map.of("reconcile", true)));
    }

    private List<ShPostTypeAttrDef> fields(String typeName) {
        return shRenderPostTypes.existing().stream()
                .filter(def -> typeName.equals(def.name()))
                .findFirst()
                .orElseThrow()
                .fields();
    }

    private List<String> fieldNames(String typeName) {
        return fields(typeName).stream().map(ShPostTypeAttrDef::name).toList();
    }
}
