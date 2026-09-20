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

import com.viglet.shio.api.agent.verify.dto.ShAgentDigest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.revision.ShPostRevision;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShSystemPostType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH249 — the digest and the previewer compose the same page the same way.
 *
 * <p>Before this, {@code /agent/render} composed a page's <b>sections</b> (SH169) and
 * {@code /preview} composed its <b>regions</b> (SH183), and neither knew the other existed: an
 * agent proving structure and a human looking at the page could be reading two different
 * compositions of the same post, with nothing reporting the disagreement. That is law P9 failing in
 * the one place it is supposed to work, since the whole value of a cheap textual proof is that it
 * stands in for looking.</p>
 *
 * <p>The decision is written down in {@code docs/specs/SH249-sections-and-regions.md}: two
 * mechanisms, one composition path. The digest reads the <b>rendered</b> page when the post-type
 * has a layout binding, and falls back to the synthetic section composition — <em>labelled</em> —
 * when the page cannot be rendered, which is the normal state of a site mid-build.</p>
 *
 * <p>Its own database, for {@link com.viglet.shio.render.ShPageRenderTest}'s reason (SH246).</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh249;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentRenderedDigestTest {

    private static final String LAYOUT = "SH249_LAYOUT";
    private static final String REGION = "SH249_NAV";

    @Autowired
    private ShAgentRenderService renderService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private com.viglet.shio.api.post.revision.ShPostRevisionService revisionService;
    @Autowired
    private com.viglet.shio.persistence.repository.post.revision.ShPostRevisionRepository revisionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String boundType;
    private String looseType;
    private String brokenType;

    @BeforeAll
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        siteName = "sh249-" + suffix;
        boundType = "Sh249Bound" + suffix;
        looseType = "Sh249Loose" + suffix;
        brokenType = "Sh299Broken" + suffix;

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");

            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            // Only one of the two types is bound, which is what makes the fallback observable.
            site.setPostTypeLayout("{\"" + boundType + "\":\"" + LAYOUT + "\",\""
                    + brokenType + "\":\"SH299_LAYOUT\"}");
            entityManager.persist(site);

            ShFolder root = folder(site, when);
            // TEXT is an `HTML Editor`, which is what makes it a markup field the fallback can
            // read. The rendered path needs no such declaration — a layout interpolates the data
            // directly — so without it only the fallback looks empty, which is the one asymmetry
            // between the two paths worth having in a fixture.
            ShPostType bound = postType(boundType, (byte) 0, when, "TEXT");
            ShPostType loose = postType(looseType, (byte) 0, when, "TEXT");
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when, "HTML");
            ShPostType regionType = postType(ShSystemPostType.REGION, (byte) 1, when, "HTML");

            // The layout contributes an <h1> and a <nav> landmark that exist in *neither* the
            // page's own fields nor any section — so seeing them proves the digest read the
            // rendered page rather than the content.
            // SH1039 — `<main>` declares what may be placed in it: one type this fixture has and
            // one it has not, so the catalogue's two answers are both observable. It changes no
            // digest: the digest hashes headings, landmarks, links and images, and an attribute is
            // none of those.
            post(site, root, layoutType, LAYOUT, "/layout", json("HTML",
                    "<html><head><style>.hero{display:block}.lede{font-size:1.2em}</style></head>"
                            + "<body><h1>{{post.title}}</h1>"
                            + "<div sh-region=\"" + REGION + "\"></div>"
                            + "<main sh-accepts=\"" + boundType + " Sh1039Absent\">"
                            + "{{{TEXT}}}</main></body></html>"), when);
            post(site, root, regionType, REGION, "/region", json("HTML",
                    "<nav><a href=\"/docs\">Docs</a></nav>"), when);

            // SH299's subject: a layout with an unfillable slot and a theme that is not there, so
            // the page renders and carries two markers saying it is broken.
            ShPostType broken = postType(brokenType, (byte) 0, when, "TEXT");
            // SH1039 — and it composes a section in a container that declares nothing, which is the
            // other half of the catalogue: an empty `accepts` is the shape of both "nothing fits
            // here" and "nobody said", and only the second carries the declaration to add.
            post(site, root, layoutType, "SH299_LAYOUT", "/layout-broken", json(
                    "HTML", "<html><body><h1>{{post.title}}</h1>"
                            + "<div sh-region=\"SH299_ABSENT\"></div>"
                            + "<div><section data-shio-section=\"Sh1039Kept\"></section></div>"
                            + "</body></html>",
                    "THEME", "SH299_NO_THEME"), when);
            post(site, root, broken, "Broken page", "/broken", json("TEXT", "<p>x</p>"), when);

            post(site, root, bound, "Bound page", "/bound",
                    json("TEXT", "<h2>From the page</h2><p>body</p>"), when);
            post(site, root, loose, "Loose page", "/loose",
                    json("TEXT", "<h2>Only the field</h2>"), when);
            // SH187's subject: two classes the stylesheet defines, one it does not, an inline
            // style, and an image with no declared size.
            post(site, root, bound, "Styled page", "/styled", json("TEXT",
                    "<section class=\"hero\"><p class=\"lede typo-here\" style=\"color:red\">x</p>"
                            + "<img src=\"/file_source/x/hero.png\" alt=\"Hero\"></section>"), when);
        });
    }

    @Test
    @DisplayName("a page whose type is bound to a layout digests as the rendered page")
    void aBoundPageDigestsAsRendered() {
        ShAgentDigest digest = renderService.digest("post:" + siteName + "/bound", null);

        assertThat(digest.source())
                .as("""
                        the whole point of SH249: the artefact a visitor gets, which is the only \
                        one that reflects the layout, its regions and the page's own fields at \
                        once.""")
                .isEqualTo("rendered");
        assertThat(digest.headings()).extracting(ShAgentDigest.Heading::text)
                .as("the layout's h1 and the page's own h2 — neither is visible to the other path")
                .containsExactly("Bound page", "From the page");
        assertThat(digest.landmarks())
                .as("the region's <nav> is a landmark the content composition could never see")
                .containsEntry("nav", 1);
        assertThat(digest.links()).extracting(ShAgentDigest.Link::href).contains("/docs");
    }

    @Test
    @DisplayName("SH1000: the review queue's before/after is two renders, and it discriminates")
    void anAppearanceComparisonAcrossARevision() {
        String address = "post:" + siteName + "/styled";
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ShPostUnified page = tx.execute(status -> entityManager.createQuery(
                        "select p from ShPostUnified p where p.furl = '/styled' and p.state = :state",
                        ShPostUnified.class)
                .setParameter("state", ShPostState.DRAFT)
                .getSingleResult());

        // The run's "before": what the page said when this revision was captured. Nothing stores
        // the appearance a page HAD — a revision keeps what it said — so the only honest answer is
        // to render that revision through today's templates, which is also the question a reviewer
        // has: a page whose look moved because somebody edited a shared region did move.
        ShPostRevision unchanged = tx.execute(status -> {
            ShPostUnified before = new ShPostUnified();
            before.setId(page.getId());
            before.setState(ShPostState.DRAFT);
            before.setShPostType(entityManager.merge(page).getShPostType());
            before.setTitle(page.getTitle());
            before.setData(page.getData());
            return revisionService.capture(before, ShPostRevision.Reason.SAVE, "admin");
        });
        ShPostRevision restyled = tx.execute(status -> {
            ShPostUnified before = new ShPostUnified();
            before.setId(page.getId());
            before.setState(ShPostState.DRAFT);
            before.setShPostType(entityManager.merge(page).getShPostType());
            before.setTitle(page.getTitle());
            before.setData(json("TEXT", "<p class=\"lede\">It said something plainer.</p>"));
            return revisionService.capture(before, ShPostRevision.Reason.SAVE, "admin");
        });
        try {
            String now = renderService.digest(address, null, null).appearanceDigest();

            assertThat(renderService.digest(address, null, "rev:" + unchanged.getVersionNo())
                    .appearanceDigest())
                    .as("SH1000: a run that changed nothing a visitor can see must not appear in "
                            + "`moved` — otherwise the list is every page the run touched, which is "
                            + "the list of changes the reviewer already has")
                    .isEqualTo(now);
            assertThat(renderService.digest(address, null, "rev:" + restyled.getVersionNo())
                    .appearanceDigest())
                    .as("SH1000: and one that did must, which is what the action column cannot say "
                            + "— an UPDATE that moved a headline and one that moved the whole page "
                            + "read identically there")
                    .isNotEqualTo(now);
        } finally {
            tx.executeWithoutResult(status -> {
                revisionRepository.delete(entityManager.merge(unchanged));
                revisionRepository.delete(entityManager.merge(restyled));
            });
        }
    }

    @Test
    @DisplayName("SH990: state picks the version a digest and an editable read take, a revision rendered like the page")
    void stateSelectsTheVersionRead() {
        String address = "post:" + siteName + "/bound";
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ShPostUnified draft = tx.execute(status -> entityManager.createQuery(
                        "select p from ShPostUnified p where p.furl = '/bound' and p.state = :state "
                                + "and p.title = 'Bound page'", ShPostUnified.class)
                .setParameter("state", ShPostState.DRAFT)
                .getSingleResult());
        // What is live says something else than the draft, and a revision says a third thing.
        tx.executeWithoutResult(status -> {
            ShPostUnified live = new ShPostUnified();
            live.setId(draft.getId());
            live.setState(ShPostState.PUBLISHED);
            live.setShSite(entityManager.merge(draft).getShSite());
            live.setShFolder(entityManager.merge(draft).getShFolder());
            live.setShPostType(entityManager.merge(draft).getShPostType());
            live.setTitle("Bound page");
            live.setFurl("/bound");
            live.setData(json("TEXT", "<h2>What is live</h2>"));
            live.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            live.setOwner("admin");
            entityManager.persist(live);
        });
        ShPostRevision then = tx.execute(status -> {
            ShPostUnified before = new ShPostUnified();
            before.setId(draft.getId());
            before.setState(ShPostState.DRAFT);
            before.setShPostType(entityManager.merge(draft).getShPostType());
            before.setTitle("Bound page");
            before.setData(json("TEXT", "<h2>What it said then</h2>"));
            return revisionService.capture(before, ShPostRevision.Reason.SAVE, "admin");
        });
        try {
            assertThat(renderService.digest(address, null, "published").headings())
                    .extracting(ShAgentDigest.Heading::text)
                    .as("SH990: the published row, which is the digest of what a publish replaced")
                    .contains("What is live")
                    .doesNotContain("From the page");

            ShAgentDigest revision = renderService.digest(address, null, "rev:" + then.getVersionNo());
            assertThat(revision.source())
                    .as("SH990: a revision goes through the renderer's revision path, so this digest "
                            + "and /preview?state=rev:<n> are the same composition")
                    .isEqualTo("rendered");
            assertThat(revision.headings()).extracting(ShAgentDigest.Heading::text)
                    .containsExactly("Bound page", "What it said then");
            assertThat(renderService.editable(address, "rev:" + then.getVersionNo()).address())
                    .isEqualTo(address);

            assertThat(renderService.digest(address, null, null).headings())
                    .extracting(ShAgentDigest.Heading::text)
                    .as("SH990: no state is the draft-preferred digest it always was")
                    .contains("From the page");
        } finally {
            tx.executeWithoutResult(status -> {
                revisionRepository.delete(entityManager.merge(then));
                entityManager.createQuery("delete from ShPostUnified p where p.id = :id and p.state = :state")
                        .setParameter("id", draft.getId())
                        .setParameter("state", ShPostState.PUBLISHED)
                        .executeUpdate();
            });
        }

        assertThatThrownBy(() -> renderService.digest("post:" + siteName + "/loose", null, "published"))
                .as("SH990: a row the post does not have is SH619's 404, not the draft under a 200")
                .isInstanceOf(com.viglet.shio.api.agent.ShAgentReadService.NoSuchStateException.class);
        assertThatThrownBy(() -> renderService.digest(null, "https://example.test/", "published"))
                .as("SH990: `url` digests what a visitor is served, which has one version")
                .isInstanceOf(ShAgentRenderService.StateWithUrlException.class);
    }

    // --- SH1039: what may be inserted, and where ---

    @Test
    @DisplayName("SH1039: the catalogue is absent until it is asked for, and empty is a real answer")
    void theCatalogueIsOptIn() {
        // Absent rather than empty when nobody asked, because an empty list is a page that composes
        // no sections — a different answer, and the one a caller would act on. P3 makes it opt-in:
        // the fields of every accepted type are what a properties panel needs and what a caller
        // asking "which fields can I edit" did not ask for.
        assertThat(renderService.editable("post:" + siteName + "/bound").containers()).isNull();
        assertThat(renderService.editable("post:" + siteName + "/loose", null, true).containers())
                .as("a page that does not render yet has no containers, and that is not a refusal")
                .isEmpty();
    }

    @Test
    @DisplayName("SH1039: a container reports what it holds, and the types it accepts, resolved")
    void theCatalogueResolvesWhatTheTemplateDeclares() {
        List<ShAgentInsertable.Container> containers =
                renderService.editable("post:" + siteName + "/bound", null, true).containers();

        assertThat(containers)
                .as("""
                        SH1038 made a section selectable, movable and removable, and an editor that \
                        can do all three still cannot ADD one: the editable read answered fields and \
                        regions, with no insertable set and no catalogue, so the section types \
                        reached the console as post-type names in a picker.""")
                .hasSize(1);
        ShAgentInsertable.Container container = containers.getFirst();
        assertThat(container.index()).isZero();
        assertThat(container.holds()).isZero();
        assertThat(container.fix())
                .as("a declared container needs no teaching sentence; the empty one is what gets it")
                .isNull();
        assertThat(container.accepts()).extracting(ShAgentInsertable.Type::type)
                .containsExactly(boundType, "Sh1039Absent");
        assertThat(container.accepts().getFirst().provisioned()).isTrue();
        assertThat(container.accepts().getFirst().fields())
                .as("what a properties panel would draw, from the one definition the console reads")
                .extracting(ShAgentInsertable.Field::name).containsExactly("TEXT");
        assertThat(container.accepts().get(1).provisioned())
                .as("""
                        The template names it, so dropping it would report a shorter catalogue than \
                        the page declares — a quieter lie than an honest gap (SH169). The caller \
                        provisions the type; nothing here invents one.""")
                .isFalse();
    }

    @Test
    @DisplayName("SH1039: a container that declares nothing carries the declaration to add")
    void anUndeclaredContainerTeaches() {
        List<ShAgentInsertable.Container> containers =
                renderService.editable("post:" + siteName + "/broken", null, true).containers();

        assertThat(containers).hasSize(1);
        assertThat(containers.getFirst().holds()).isOne();
        assertThat(containers.getFirst().accepts()).isEmpty();
        // P6 inside an ordinary 200: an empty catalogue is the shape of both "nothing fits here" and
        // "nobody said", and those need different actions. This layout composes a section and
        // declares no `sh-accepts`, so the container exists and has nothing to offer — which is
        // only useful if it says what would give it something.
        assertThat(containers.getFirst().fix())
                .as("an absent declaration is never read as \"anything\", so it has to be sayable")
                .contains("sh-accepts");
    }

    @Test
    @DisplayName("a page that cannot be rendered falls back, and says so")
    void anUnboundPageFallsBack() {
        ShAgentDigest digest = renderService.digest("post:" + siteName + "/loose", null);

        assertThat(digest.source())
                .as("""
                        labelled, not guessed: a post-type with no layout binding is the normal \
                        state of a site mid-build, and a digest that started failing where it used \
                        to answer would break the SH99 loop for every one of them. `source` is how \
                        a caller tells which question was answered.""")
                .isEqualTo("content");
        assertThat(digest.headings()).extracting(ShAgentDigest.Heading::text)
                .containsExactly("Only the field");
        assertThat(digest.landmarks())
                .as("no layout ran, so no region's landmark is present")
                .doesNotContainKey("nav");
    }

    @Test
    @DisplayName("the rendered digest is stable, so it is a comparison and not noise")
    void theRenderedDigestIsStable() {
        // Both composition paths are deterministic by construction (SH183's ordering, SH247's
        // total orders, ShStableMap), and the digest hashes structure only. A digest that moved
        // between identical calls would be unusable as the before/after SH194 wants.
        String first = renderService.digest("post:" + siteName + "/bound", null).digest();
        String second = renderService.digest("post:" + siteName + "/bound", null).digest();

        assertThat(first).isEqualTo(second).isNotBlank();
    }

    @Test
    @DisplayName("the two paths produce different digests, which is why the disagreement mattered")
    void thePathsAreDistinguishable() {
        String rendered = renderService.digest("post:" + siteName + "/bound", null).digest();
        String content = renderService.digest("post:" + siteName + "/loose", null).digest();

        // Not a tautology: it is the evidence that "which composition did you read" was a real
        // question about the same kind of post, and the reason `source` has to be in the response
        // rather than implied.
        assertThat(rendered).isNotEqualTo(content);
    }

    // ── SH187: appearance ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("a rendered digest carries the appearance surface the document holds")
    void appearanceIsReported() {
        ShAgentDigest digest = renderService.digest("post:" + siteName + "/styled", null);

        assertThat(digest.appearance()).isNotNull();
        assertThat(digest.appearance().classes())
                .as("every stylistic hook the markup uses, sorted")
                .containsExactly("hero", "lede", "typo-here");
        assertThat(digest.appearance().unstyledClasses())
                .as("""
                        the appearance defect an *agent* actually creates: markup written against                         a class the stylesheet does not have. `hero` and `lede` are defined in the                         theme; `typo-here` is not, and nothing else in the system reports it.""")
                .containsExactly("typo-here");
        assertThat(digest.appearance().definedClasses()).isPositive();
        assertThat(digest.appearance().inlineStyles())
                .as("styling the theme cannot reach and a curator cannot review")
                .isEqualTo(1);
        assertThat(digest.appearance().imagesWithoutSize())
                .as("the one appearance defect a parser can be certain of — the page reflows on load")
                .containsExactly("/file_source/x/hero.png");
    }

    @Test
    @DisplayName("the appearance hash is separate from the structural one, and answers a different question")
    void twoHashesTwoQuestions() {
        ShAgentDigest styled = renderService.digest("post:" + siteName + "/styled", null);
        ShAgentDigest plain = renderService.digest("post:" + siteName + "/bound", null);

        assertThat(styled.appearanceDigest()).startsWith("a:");
        assertThat(styled.digest()).startsWith("d:");
        assertThat(styled.appearanceDigest())
                .as("""
                        Two hashes because a theme edit changes how a page looks and not what it                         is: one hash covering both could not tell "I restyled it" from "I broke                         the outline", which is exactly the discrimination an iterative loop needs.""")
                .isNotEqualTo(plain.appearanceDigest());
        assertThat(styled.appearanceDigest())
                .isEqualTo(renderService.digest("post:" + siteName + "/styled", null).appearanceDigest());
    }

    @Test
    @DisplayName("SH299: a rendered page carrying defect markers says how many, beside the digest")
    void aBrokenPageCountsItsMarkers() {
        ShAgentDigest digest = renderService.digest("post:" + siteName + "/broken", null);

        assertThat(digest.source()).isEqualTo("rendered");
        assertThat(digest.markers()).isNotNull();
        assertThat(digest.markers().missingRegions())
                .as("""
                        an SH99 loop could close green on this page before SH299: the markers are \
                        attributes on elements the structural outline does not describe, which is \
                        the false green SH141 was filed for one level out — a route that resolves \
                        in the CMS and does not serve.""")
                .isEqualTo(1);
        assertThat(digest.markers().missingThemes()).isEqualTo(1);
        assertThat(digest.markers().unpublishedRegions()).isZero();
        assertThat(digest.markers().any()).isTrue();
    }

    @Test
    @DisplayName("SH299: a clean page carries the section too, all zeroes")
    void aCleanPageStillCarriesTheSection() {
        // Present rather than absent here, unlike `appearance` — because the question is "is this
        // page broken", and a caller that has to tell "no markers" from "this build does not
        // report markers" is back to the ambiguity the field exists to remove. It is a rendered
        // page's field, so the `source: content` fallback still has none.
        assertThat(renderService.digest("post:" + siteName + "/bound", null).markers())
                .isNotNull()
                .satisfies(markers -> assertThat(markers.any()).isFalse());
        assertThat(renderService.digest("post:" + siteName + "/loose", null).markers())
                .as("a post that cannot be rendered has no page to carry markers")
                .isNull();
    }

    @Test
    @DisplayName("a page that could not be rendered has no appearance at all")
    void theFallbackHasNoAppearance() {
        ShAgentDigest digest = renderService.digest("post:" + siteName + "/loose", null);

        assertThat(digest.appearance())
                .as("""
                        absent, not a section of nulls: a post's own markup field is not a document                         and carries no stylesheet, so there is nothing honest to say about how it                         looks. SH121's rule for ids, SH193's for `affects`.""")
                .isNull();
        assertThat(digest.appearanceDigest()).isNull();
    }

    // ── fixture ─────────────────────────────────────────────────────────────────────

    private ShFolder folder(ShSite site, Instant when) {
        ShFolder folder = new ShFolder();
        folder.setName("root");
        folder.setRootFolder((byte) 1);
        folder.setShSite(site);
        folder.setDate(when);
        folder.setOwner("admin");
        entityManager.persist(folder);
        return folder;
    }

    private ShPostType postType(String name, byte system, Instant when, String... fields) {
        ShPostType existing = entityManager
                .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                .setParameter("name", name)
                .getResultList().stream().findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }
        ShPostType type = new ShPostType();
        type.setName(name);
        type.setTitle(name);
        type.setSystem(system);
        type.setDate(when);
        type.setOwner("admin");
        entityManager.persist(type);

        com.viglet.shio.persistence.model.widget.ShWidget html = entityManager
                .createQuery("select w from ShWidget w where w.name = :name",
                        com.viglet.shio.persistence.model.widget.ShWidget.class)
                .setParameter("name", com.viglet.shio.widget.ShSystemWidget.ACE_HTML)
                .getResultList().stream().findFirst().orElseThrow();
        int ordinal = 1;
        for (String field : fields) {
            com.viglet.shio.persistence.model.post.type.ShPostTypeAttr attr =
                    new com.viglet.shio.persistence.model.post.type.ShPostTypeAttr();
            attr.setName(field);
            attr.setLabel(field);
            attr.setOrdinal(ordinal++);
            attr.setRequired((byte) 0);
            attr.setIsTitle((byte) 0);
            attr.setIsSummary((byte) 0);
            attr.setShWidget(html);
            attr.setShPostType(type);
            entityManager.persist(attr);
        }
        entityManager.flush();
        return type;
    }

    private void post(ShSite site, ShFolder folder, ShPostType type, String title, String furl,
                      String data, Instant when) {
        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(ShPostState.DRAFT);
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

    private String json(String... pairs) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(pairs[i]).append("\":\"")
                    .append(pairs[i + 1].replace("\\", "\\\\").replace("\"", "\\\""))
                    .append('"');
        }
        return sb.append('}').toString();
    }
}
