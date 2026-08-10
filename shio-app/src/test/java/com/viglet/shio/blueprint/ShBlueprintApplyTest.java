/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.agent.ShAddressResolver;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.blueprint.dto.ShBlueprintApplied;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.api.post.type.ShPostTypeAdminService;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.render.ShPageRenderService;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.api.agent.ShAgentBatchService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH93/SH94 — applying a blueprint end to end.
 *
 * <h2>The three properties worth an integration test</h2>
 *
 * <ol>
 *   <li><b>The plan is complete and writes nothing.</b> A blueprint brings its own
 *       post-types, so the content planner has to resolve types that do not exist yet;
 *       the plan provisions them inside its transaction and rolls back. If the rollback
 *       ever stopped working, a dry run would leave a model behind — so the test asserts
 *       the post-type is <em>absent</em> afterwards, which is the only way to see it.</li>
 *   <li><b>Applying converges.</b> The second apply changes nothing. This is inherited
 *       from SH82 rather than implemented here, and that is exactly why it is worth
 *       asserting: it is the property that makes a blueprint safe to re-run after a
 *       context compaction.</li>
 *   <li><b>The package keeps its promise.</b> Every URL in {@code verify.posts} resolves
 *       afterwards, and the SH99 lint over the site reports no error. A blueprint that
 *       cannot prove it worked is a liability (SH94), and this is the proof.</li>
 * </ol>
 *
 * @author Alexandre Oliveira
 */
// SH395 — its own database, and SH315's rule arriving late. This class APPLIES WHOLE PACKAGES:
// it provisions the render post-types, writes a Theme and a PageLayout, and asserts instance-wide
// counts. On the shared context another test's minimal `Theme` post-type — created without the
// `CSS` field this one writes — makes the apply refuse a field the type "has no place for", which
// is a true statement about a type this package did not create. It is also the isolation SH635's
// flake wanted: that defect was another test's orphan post reaching this class's verify.
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-blueprint-apply;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class ShBlueprintApplyTest {

    /** Widgets the first-party packages reference. Seeded here if the catalogue lacks them. */
    private static final List<String> WIDGETS = List.of("Text", "Text Area", "HTML Editor",
            "Date", "Multi Select", "Combo Box", "File", "Relator");

    @Autowired
    private ShBlueprintService shBlueprintService;
    @Autowired
    private ShPageRenderService shPageRenderService;
    @Autowired
    private ShAddressResolver shAddressResolver;
    @Autowired
    private ShPostTypeRepository shPostTypeRepository;
    @Autowired
    private ShSiteRepository shSiteRepository;
    @Autowired
    private ShPostTypeAdminService shPostTypeAdminService;
    @Autowired
    private com.viglet.shio.api.agent.memory.ShAgentMemoryService shAgentMemoryService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Autowired
    private com.viglet.shio.api.agent.verify.ShAgentVerifyService shAgentVerifyService;

    private String siteName;

    private void withTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private void createSite() {
        withTransaction(() -> {
            ShSite site = new ShSite();
            siteName = "bp-site-" + UUID.randomUUID();
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

            for (String name : WIDGETS) {
                boolean present = entityManager
                        .createQuery("select w from ShWidget w where w.name = :name", ShWidget.class)
                        .setParameter("name", name)
                        .getResultStream().findAny().isPresent();
                if (!present) {
                    ShWidget widget = new ShWidget();
                    widget.setName(name);
                    widget.setDescription(name);
                    entityManager.persist(widget);
                }
            }
        });
    }

    private JsonNode params(String json) {
        return objectMapper.readTree(json);
    }

    @Test
    @Order(1)
    @DisplayName("the plan names the post-types it would create — and creates none of them")
    void planIsCompleteAndWritesNothing() {
        createSite();

        ShBlueprintApplied plan = shBlueprintService.plan("blog", siteName,
                params("{\"blogTitle\":\"The Acme Blog\"}"), "admin");

        assertThat(plan.dryRun()).isTrue();
        assertThat(plan.postTypes())
                .extracting(ShBlueprintApplied.PostType::name,
                        ShBlueprintApplied.PostType::action)
                .containsExactly(org.assertj.core.api.Assertions.tuple("Article", "create"));
        // The whole point of provisioning inside the plan's transaction: without it every
        // post row below would have failed with "no post-type named Article".
        // SH395 — 14 since the package started shipping its own design: the /theme folder, a
        // Theme and a PageLayout with a publish each, the render.provision the templates imply,
        // and the site binding. The number is asserted rather than bounded because it is the
        // measure of what "one command, one visible site" costs.
        assertThat(plan.content().changed()).isEqualTo(14);
        assertThat(plan.content().results())
                .extracting(change -> change.address())
                .contains("post:" + siteName + "/blog/hello-world");
        assertThat(plan.verify()).as("a plan has nothing to prove").isNull();
        // Parameters are echoed with the defaults applied, so the record of what ran is
        // complete even for values the caller never sent.
        assertThat(plan.params())
                .containsEntry("blogTitle", "The Acme Blog")
                .containsEntry("authorName", "Editorial");

        withTransaction(() -> assertThat(shPostTypeRepository.findByName("Article"))
                .as("a plan that left a post-type behind is not a plan")
                .isNull());
        withTransaction(() -> assertThat(shAddressResolver
                .find(ShAddress.post(siteName, "/blog/hello-world")))
                .as("and it wrote no content either")
                .isEmpty());
    }

    @Test
    @Order(2)
    @DisplayName("applying creates the model and the content, and proves its own promise")
    void applyKeepsThePackagesPromise() {
        ShBlueprintApplied applied = shBlueprintService.apply("blog", siteName,
                params("{\"blogTitle\":\"The Acme Blog\",\"authorName\":\"Acme Editorial\"}"),
                "admin");

        assertThat(applied.dryRun()).isFalse();
        assertThat(applied.postTypes()).singleElement()
                .satisfies(postType -> {
                    assertThat(postType.name()).isEqualTo("Article");
                    assertThat(postType.action()).isEqualTo("create");
                    assertThat(postType.missing()).isNull();
                });
        assertThat(applied.content().changed()).isEqualTo(14);
        assertThat(applied.verify()).isNotNull();
        assertThat(applied.verify().missing()).isEmpty();
        // SH635 — this assertion has failed twice, in a full suite, and never alone: `errors()` came
        // back as 2 where 0 was expected, and both times the run was green on the next attempt with
        // the same code. There was nothing to diagnose from, because the number is all the assertion
        // says and the next `clean` removes target/surefire-reports/ (SH354).
        //
        // So the capture is permanent rather than something somebody adds after the next occurrence.
        // It costs one extra verify on a run that has already failed and nothing at all on a green
        // one, and it turns an unreproducible red into a report naming the rule, the address and the
        // message. That is the whole difference between a flake somebody re-runs and a defect
        // somebody fixes.
        if (applied.verify().errors() != 0) {
            System.out.println("SH635: unexpected verify errors on blueprint site " + siteName + " -> "
                    + shAgentVerifyService.verify(siteName, "content", 200).findings());
        }
        assertThat(applied.verify().errors()).isZero();
        assertThat(applied.verify().ok()).isTrue();
        // SH216 — this package now carries an index page of its own, so `files` is a list where
        // it used to be absent. The rule that made it absent still holds (a package with no front
        // end reports `null` rather than `[]`, so an MCP-only caller is not told it has files it
        // does not have); what changed is that no first-party package is in that state any more.
        //
        // Asserted on the ROUTE rather than on the count: the point of SH216 is that the page is
        // about the model — the posts, newest first, at a URL the catch-all cannot produce — and a
        // count would pass just as well if the overlay carried the wrong file.
        assertThat(applied.files())
                .extracting(ShBlueprintApplied.CarriedFile::path)
                .contains("app/blog/page.tsx", "app/[...slug]/page.tsx");

        withTransaction(() -> {
            assertThat(shPostTypeRepository.findByName("Article")).isNotNull();
            assertThat(shAddressResolver.find(ShAddress.post(siteName, "/blog/hello-world")))
                    .isPresent();
        });
    }

    @Test
    @Order(6)
    @DisplayName("SH395: the applied site renders through the package's own layout")
    void theAppliedSiteIsVisible() {
        // The claim SH395 is about, and the only assertion that can make it: one command, one
        // site somebody can look at. Everything else in this class proves the CONTENT arrived —
        // which was true before, and left every page on SH169's labelled fallback.
        //
        // Its own site and its own apply: `createSite()` reassigns `siteName`, so a test that
        // read the one an earlier @Order left behind would be asserting against whichever
        // sibling ran last. That is the fixture equivalent of the bug this class exists for.
        createSite();
        shBlueprintService.apply("blog", siteName,
                params("{\"blogTitle\":\"The Acme Blog\",\"authorName\":\"Acme Editorial\"}"),
                "admin");

        String html = shPageRenderService
                .render(siteName, "/blog/hello-world", ShPostState.PUBLISHED).html();

        assertThat(html)
                .as("""
                        the page came through the package's PageLayout, not the fallback. Before                         SH395 no first-party package could ship one: a blueprint parameter and a                         Handlebars expression are both curly-braced, so the format refused                         `{{post.title}}` as a parameter nobody declared.""")
                .contains("Welcome to")
                .doesNotContain("data-shio-fallback");
        assertThat(html)
                .as("and the theme it names resolved, so the layout's `{{theme.css}}` is a "
                        + "stylesheet rather than an empty <style>")
                .contains("--ink");
        assertThat(html)
                .as("while the package's own parameter substituted in the masthead — the half "
                        + "the template-field exemption had to keep working")
                .contains("The Acme Blog");
    }

    @Test
    @Order(3)
    @DisplayName("substitution reached the content, and the parameter's value is what is stored")
    void substitutionReachedTheContent() {
        withTransaction(() -> assertThat(shAddressResolver
                .find(ShAddress.post(siteName, "/blog/hello-world"))
                .orElseThrow().title())
                .isEqualTo("Welcome to The Acme Blog"));
    }

    @Test
    @Order(4)
    @DisplayName("a second apply changes nothing — a blueprint survives a compaction")
    void applyingTwiceConverges() {
        ShBlueprintApplied again = shBlueprintService.apply("blog", siteName,
                params("{\"blogTitle\":\"The Acme Blog\",\"authorName\":\"Acme Editorial\"}"),
                "admin");

        assertThat(again.postTypes()).singleElement()
                .satisfies(postType -> assertThat(postType.action()).isEqualTo("exists"));
        assertThat(again.content().changed())
                .as("convergence is the property that makes re-running safe")
                .isZero();
        assertThat(again.verify().ok()).isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("an existing post-type missing a field the package writes is named, not hidden")
    void existingPostTypeMismatchIsReported() {
        // The collision that actually happens: a console-created site already carries the
        // SH71 bootstrap `Page` (title + content), and the section vocabulary's `Page` has
        // `sections`. The older type wins — and the caller is told which fields it lacks.
        createSite();
        withTransaction(() -> {
            // Reused when it is already there: the SH71 bootstrap template provisions a
            // `Page` too, so a second one would make findByName ambiguous for every test in
            // the run — and either origin produces the same collision, which is the point.
            if (shPostTypeRepository.findByName("Page") != null) {
                return;
            }
            com.viglet.shio.persistence.model.post.type.ShPostType page =
                    new com.viglet.shio.persistence.model.post.type.ShPostType();
            page.setName("Page");
            page.setTitle("Page");
            page.setSystem((byte) 0);
            page.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            page.setOwner("admin");
            entityManager.persist(page);

            com.viglet.shio.persistence.model.post.type.ShPostTypeAttr title =
                    new com.viglet.shio.persistence.model.post.type.ShPostTypeAttr();
            title.setName("title");
            title.setLabel("Title");
            title.setOrdinal(1);
            title.setIsTitle((byte) 1);
            title.setShPostType(page);
            title.setShWidget(entityManager
                    .createQuery("select w from ShWidget w where w.name = 'Text'", ShWidget.class)
                    .getResultStream().findFirst().orElseThrow());
            entityManager.persist(title);
        });

        ShBlueprintApplied plan = shBlueprintService.plan("landing", siteName,
                params("{\"brand\":\"Acme\"}"), "admin");

        assertThat(plan.postTypes())
                .filteredOn(postType -> "Page".equals(postType.name()))
                .singleElement()
                .satisfies(postType -> {
                    assertThat(postType.action()).isEqualTo("exists");
                    // `contains` rather than `containsExactly`, and the reason is the finding
                    // SH607 is about. `missing()` is relative to the **live** type, and a
                    // post-type is instance-wide: whichever `Page` this database already carries
                    // decides the tail of this list. The suite shares one database, so an earlier
                    // test that provisioned the SH71 bootstrap starter — whose `Page` is
                    // title+content — leaves `content` present and only these two missing, while
                    // this test alone leaves all three. Both are correct answers to "what does
                    // the package expect that this type does not have"; the assertion this test
                    // owns is that the mismatch is *reported*, not how long it is. The canonical
                    // field list is asserted where it belongs, in ShVocabularyCatalogTest.
                    assertThat(postType.missing()).contains("description", "sections");
                });
        // SH168: and the content half of the same answer — the plan says which writes the
        // missing fields will break, rather than reporting `update` and failing on apply.
        // The plan must still be a *whole* report: dying on the first bad field would have
        // hidden the post-type list above, which is the more actionable half.
        assertThat(plan.content().results())
                .anySatisfy(change -> assertThat(change.detail())
                        .as("a plan warns where a real run refuses")
                        .contains("undeclared field(s)", "sections"));
    }

    @Test
    @DisplayName("an unknown site and an unknown blueprint both teach instead of failing blankly")
    void refusalsTeach() {
        createSite();

        assertThatThrownBy(() -> shBlueprintService.apply("blog", null, null, "admin"))
                .isInstanceOf(ShBlueprintService.MissingSiteException.class)
                .satisfies(failure -> assertThat(((ShTeachingException) failure).problem()
                        .toProblemDetail().getProperties()).containsKey("fix"));

        assertThatThrownBy(() -> shBlueprintService.apply("no-such-package", siteName, null, "admin"))
                .isInstanceOf(ShBlueprintRegistry.UnknownBlueprintException.class);
    }

    // --- SH151: an apply can create the site it needs ---

    @Test
    @Order(10)
    @DisplayName("an apply refuses a site that does not exist, and the refusal names both routes")
    void anUnknownSiteIsStillRefusedByDefault() {
        // The default has to stay a refusal: "the site you named does not exist" must never read
        // as permission to make one (P7). What changed is that the error now says how.
        assertThatThrownBy(() -> shBlueprintService.plan("landing", "sh151-no-such-site-"
                + UUID.randomUUID(), params("{\"brand\":\"Acme\"}"), "admin"))
                .isInstanceOf(ShBlueprintService.MissingSiteException.class)
                .satisfies(failure -> {
                    var problem = ((ShBlueprintService.MissingSiteException) failure).problem()
                            .toProblemDetail();
                    assertThat(problem.getProperties().get("fix").toString())
                            .contains("createSite").contains("site.upsert");
                });
    }

    @Test
    @Order(20)
    @DisplayName("createSite makes it, blank — and the plan says so without making it")
    void createSiteMakesTheSiteItNeeds() {
        String name = "sh151-" + UUID.randomUUID().toString().substring(0, 8);
        // Both flags, because this is the realistic pair: "build me a site with this package" has
        // to survive an instance that already has a `Page` of its own, and since SH168 the content
        // half is *refused* rather than silently mangled when it does not. SH151 and SH152 are two
        // halves of the same call.
        var options = new ShBlueprintWriteService.Options(true, true);

        ShBlueprintApplied plan = shBlueprintService.plan("landing", name,
                params("{\"brand\":\"Acme\"}"), "admin", options);
        assertThat(plan.site()).isEqualTo(name);
        assertThat(plan.content().results()).as("the content plan is real, so the site existed "
                + "inside the transaction the plan rolls back").isNotEmpty();
        assertThat(shSiteRepository.findByName(name))
                .as("and nothing survived it — a plan writes nothing")
                .isNull();

        ShBlueprintApplied applied = shBlueprintService.apply("landing", name,
                params("{\"brand\":\"Acme\"}"), "admin", options);

        assertThat(applied.createdSite()).isEqualTo(name);
        assertThat(shSiteRepository.findByName(name)).isNotNull();
        // Blank: the package's own pages are creates, not updates over a bootstrap template.
        //
        // SH518 narrowed this to the CONTENT ops. Binding the layout is a `site.upsert` that
        // reports `update` — the site row exists and gains `postTypeLayout.Page` — and that is
        // the feature rather than a bootstrap template being written over. The claim this test
        // makes is about the package's pages, so it now says so instead of reading every op.
        assertThat(applied.content().results())
                .filteredOn(change -> !ShAgentBatchService.SITE_UPSERT.equals(change.op()))
                .allSatisfy(change -> assertThat(change.action()).isNotEqualTo("update"));
    }

    @Test
    @Order(21)
    @DisplayName("createSite is idempotent — the second apply reports no site created")
    void createSiteIsIdempotent() {
        String name = "sh151-" + UUID.randomUUID().toString().substring(0, 8);
        var options = new ShBlueprintWriteService.Options(true, true);
        shBlueprintService.apply("landing", name, params("{\"brand\":\"Acme\"}"), "admin", options);

        ShBlueprintApplied second = shBlueprintService.apply("landing", name,
                params("{\"brand\":\"Acme\"}"), "admin", options);

        assertThat(second.createdSite()).isNull();
        assertThat(second.content().changed()).isZero();
    }

    // --- SH152: additive post-type reconciliation ---

    @Test
    @Order(11)
    @DisplayName("mergePostTypes adds the fields an existing type lacks, and says it merged")
    void mergeAddsTheMissingFields() {
        createSite();
        // The collision that actually happens: a `Page` with only `title`, and a package whose
        // `Page` also declares `description` and `sections`.
        withTransaction(() -> {
            if (shPostTypeRepository.findByName("Page") != null) {
                return;
            }
            com.viglet.shio.persistence.model.post.type.ShPostType page =
                    new com.viglet.shio.persistence.model.post.type.ShPostType();
            page.setName("Page");
            page.setTitle("Page");
            page.setSystem((byte) 0);
            page.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            page.setOwner("admin");
            entityManager.persist(page);

            com.viglet.shio.persistence.model.post.type.ShPostTypeAttr title =
                    new com.viglet.shio.persistence.model.post.type.ShPostTypeAttr();
            title.setName("title");
            title.setLabel("Title");
            title.setOrdinal(1);
            title.setIsTitle((byte) 1);
            title.setShPostType(page);
            title.setShWidget(entityManager
                    .createQuery("select w from ShWidget w where w.name = 'Text'", ShWidget.class)
                    .getResultStream().findFirst().orElseThrow());
            entityManager.persist(title);
        });

        ShBlueprintApplied applied = shBlueprintService.apply("landing", siteName,
                params("{\"brand\":\"Acme\"}"), "admin",
                new ShBlueprintWriteService.Options(false, true));

        assertThat(applied.postTypes())
                .filteredOn(postType -> "Page".equals(postType.name()))
                .singleElement()
                .satisfies(postType -> {
                    assertThat(postType.action())
                            .as("a merge that read as a provision would hide that the type is now "
                                    + "a hybrid of two models")
                            .isEqualTo("merge");
                    assertThat(postType.added()).contains("sections");
                });

        // The human's field survived untouched, and the added ones are appended after it —
        // a form that rearranged itself because a blueprint was applied is a surprise with no upside.
        var fields = shPostTypeAdminService.get("Page").orElseThrow().fields();
        assertThat(fields).extracting("name").startsWith("title");
        assertThat(fields).extracting("name").contains("sections");

        // And the content it writes now lands: SH168 refuses an undeclared field, so before the
        // merge these very pages would have been refused rather than silently mangled.
        assertThat(applied.content().results())
                .noneSatisfy(change -> assertThat(change.detail()).contains("undeclared field"));
    }

    @Test
    @Order(9)
    @DisplayName("without the flag the mismatch is still only reported")
    void withoutTheFlagNothingIsMerged() {
        createSite();
        ShBlueprintApplied plan = shBlueprintService.plan("landing", siteName,
                params("{\"brand\":\"Acme\"}"), "admin");

        assertThat(plan.postTypes())
                .filteredOn(postType -> "Page".equals(postType.name()))
                .singleElement()
                .satisfies(postType -> assertThat(postType.action()).isEqualTo("exists"));
    }

    // --- SH155: the site records which package it received ---

    @Test
    @Order(40)
    @DisplayName("an apply leaves a note saying which package and version built the site")
    void applyRecordsTheBlueprint() {
        String name = "sh155-" + UUID.randomUUID().toString().substring(0, 8);
        var options = new ShBlueprintWriteService.Options(true, true);

        ShBlueprintApplied applied = shBlueprintService.apply("landing", name,
                params("{\"brand\":\"Acme\"}"), "admin", options);

        assertThat(applied.recordedAs()).isEqualTo("blueprint.landing");
        // Scoped to the site, so it reaches the next session through the context pack's
        // `conventions` — the whole reason this is a memory note and not a table.
        assertThat(shAgentMemoryService.notes(name))
                .filteredOn(note -> "blueprint.landing".equals(note.key()))
                .singleElement()
                .satisfies(note -> {
                    assertThat(note.scope()).isEqualTo(name);
                    assertThat(note.note()).contains("Built from blueprint 'landing'")
                            .contains("brand=Acme");
                    assertThat(note.author()).isEqualTo("admin");
                });
    }

    @Test
    @Order(41)
    @DisplayName("a plan records nothing, and a re-apply rewrites one row rather than adding one")
    void planRecordsNothingAndReapplyConverges() {
        String name = "sh155-" + UUID.randomUUID().toString().substring(0, 8);
        var options = new ShBlueprintWriteService.Options(true, true);

        ShBlueprintApplied plan = shBlueprintService.plan("landing", name,
                params("{\"brand\":\"Plans\"}"), "admin", options);
        assertThat(plan.recordedAs()).as("a plan writes nothing, including about itself").isNull();

        shBlueprintService.apply("landing", name, params("{\"brand\":\"First\"}"), "admin", options);
        shBlueprintService.apply("landing", name, params("{\"brand\":\"Second\"}"), "admin", options);

        // Upsert by (scope, key): the record cannot grow with the number of applies, and the
        // latest one is what the site is made of. A converging re-apply still writes, because
        // the fact recorded is "this version was applied here" and its date is the answer to
        // "when was this last confirmed against the package".
        assertThat(shAgentMemoryService.notes(name))
                .filteredOn(note -> note.key().startsWith("blueprint."))
                .singleElement()
                .satisfies(note -> assertThat(note.note()).contains("brand=Second"));
    }

    @Test
    @Order(42)
    @DisplayName("two packages leave two records — 'what is this site made of' is the set of them")
    void twoPackagesLeaveTwoRecords() {
        String name = "sh155-" + UUID.randomUUID().toString().substring(0, 8);
        var options = new ShBlueprintWriteService.Options(true, true);

        shBlueprintService.apply("landing", name, params("{\"brand\":\"Acme\"}"), "admin", options);
        shBlueprintService.apply("blog", name, params("{\"blogTitle\":\"Acme Blog\"}"), "admin",
                options);

        assertThat(shAgentMemoryService.notes(name))
                .filteredOn(note -> note.key().startsWith("blueprint."))
                .extracting(note -> note.key())
                .as("a single `blueprint` key would make the second apply erase the first")
                .containsExactlyInAnyOrder("blueprint.landing", "blueprint.blog");
    }

    // --- SH153: the plan needs no write transaction ---

    // Ordered before the applies below: this class shares one instance and one database, so a
    // test that wants to see a post-type being *planned* has to run before the one that creates it.
    @Test
    @Order(0)
    @DisplayName("a plan runs inside a read-only transaction — it no longer writes and rolls back")
    void planRunsReadOnly() {
        createSite();

        // The regression guard, and the only way to see the property from outside: the previous
        // implementation provisioned the package's post-types inside the plan's own transaction
        // and marked it rollback-only, so a read-only transaction — a read-only credential, a
        // replica — could not plan a blueprint at all. Asserting the post-type is absent
        // afterwards (below) proves nothing survived; this proves nothing was attempted.
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);

        ShBlueprintApplied plan = readOnly.execute(status ->
                shBlueprintService.plan("blog", siteName,
                        params("{\"blogTitle\":\"Read Only\"}"), "admin"));

        assertThat(plan).isNotNull();
        assertThat(plan.dryRun()).isTrue();
        assertThat(plan.postTypes())
                .extracting(ShBlueprintApplied.PostType::name,
                        ShBlueprintApplied.PostType::action)
                .contains(org.assertj.core.api.Assertions.tuple("Article", "create"));
        // The content half is the part that used to need the write: every post resolves its
        // type against a model that does not exist yet, and now does so from the declaration.
        assertThat(plan.content().results())
                .extracting(change -> change.address())
                .contains("post:" + siteName + "/blog/hello-world");
        // SH395 — 14 since the package started shipping its own design: the /theme folder, a
        // Theme and a PageLayout with a publish each, the render.provision the templates imply,
        // and the site binding. The number is asserted rather than bounded because it is the
        // measure of what "one command, one visible site" costs.
        assertThat(plan.content().changed()).isEqualTo(14);
    }

    @Test
    @Order(31)
    @DisplayName("createSite plans read-only too, and still makes nothing")
    void createSitePlansReadOnly() {
        String name = "sh153-" + UUID.randomUUID().toString().substring(0, 8);
        var options = new ShBlueprintWriteService.Options(true, true);
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);

        ShBlueprintApplied plan = readOnly.execute(status ->
                shBlueprintService.plan("landing", name, params("{\"brand\":\"Acme\"}"), "admin",
                        options));

        assertThat(plan).isNotNull();
        assertThat(plan.site()).isEqualTo(name);
        // A site that does not exist has no root folder, so every folder and post below it is a
        // create — which is exactly what a plan for it should say.
        assertThat(plan.content().results()).isNotEmpty();
        assertThat(plan.content().results())
                .allSatisfy(change -> assertThat(change.action()).isNotEqualTo("update"));
        assertThat(shSiteRepository.findByName(name))
                .as("planning a site into existence is not planning")
                .isNull();
    }

    // Between test 5 (which creates a `Page` carrying only `title`) and test 11 (which merges
    // the package's fields into it): the whole assertion is about a type that is still missing them.
    @Test
    @Order(6)
    @DisplayName("a declaration cannot make a plan greener than the run it predicts")
    void declaringDoesNotSilenceTheUndeclaredFieldWarning() {
        // The failure mode SH153 is one wrong line away from: declare the package's fields for a
        // post-type that already exists, and the plan stops reporting the writes a real run will
        // refuse (SH168). Without `mergePostTypes` nothing is declared for an existing type, so
        // the warning has to survive — this is test 5's assertion made against the new path.
        createSite();
        withTransaction(() -> {
            if (shPostTypeRepository.findByName("Page") != null) {
                return;
            }
            com.viglet.shio.persistence.model.post.type.ShPostType page =
                    new com.viglet.shio.persistence.model.post.type.ShPostType();
            page.setName("Page");
            page.setTitle("Page");
            page.setSystem((byte) 0);
            page.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            page.setOwner("admin");
            entityManager.persist(page);

            com.viglet.shio.persistence.model.post.type.ShPostTypeAttr title =
                    new com.viglet.shio.persistence.model.post.type.ShPostTypeAttr();
            title.setName("title");
            title.setLabel("Title");
            title.setOrdinal(1);
            title.setIsTitle((byte) 1);
            title.setShPostType(page);
            title.setShWidget(entityManager
                    .createQuery("select w from ShWidget w where w.name = 'Text'", ShWidget.class)
                    .getResultStream().findFirst().orElseThrow());
            entityManager.persist(title);
        });

        ShBlueprintApplied plan = shBlueprintService.plan("landing", siteName,
                params("{\"brand\":\"Acme\"}"), "admin");

        assertThat(plan.content().results())
                .anySatisfy(change -> assertThat(change.detail())
                        .as("a plan may never be more optimistic than the apply it predicts")
                        .contains("undeclared field(s)"));

        // …and with the merge asked for, the same plan is clean, because then the run really
        // would add the fields. The declaration follows what the apply would do, nothing more.
        ShBlueprintApplied merging = shBlueprintService.plan("landing", siteName,
                params("{\"brand\":\"Acme\"}"), "admin",
                new ShBlueprintWriteService.Options(false, true));

        assertThat(merging.content().results())
                .noneSatisfy(change -> assertThat(change.detail()).contains("undeclared field"));
        assertThat(merging.postTypes())
                .filteredOn(postType -> "Page".equals(postType.name()))
                .singleElement()
                .satisfies(postType -> {
                    assertThat(postType.action()).isEqualTo("merge");
                    assertThat(postType.added()).contains("sections");
                    assertThat(postType.missing())
                            .as("what a merge would leave missing is nothing, not what it adds")
                            .isNull();
                });
        withTransaction(() -> assertThat(shPostTypeAdminService.get("Page").orElseThrow().fields())
                .as("and the plan added no field to the live type")
                .extracting("name")
                .doesNotContain("sections"));
    }
}
