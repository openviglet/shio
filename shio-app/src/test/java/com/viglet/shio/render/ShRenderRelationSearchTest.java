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

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShSystemPostType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH247 — {@code getRelation} and {@code search}, the two capabilities of the seven that were
 * filed rather than stubbed when SH183 shipped the renderer.
 *
 * <h2>Why their absence mattered</h2>
 *
 * <p>SH183's own note said the renderer "is not yet a match for what it replaced", and these
 * two are most of the gap. Without {@code getRelation}, an SH95 {@code Page} — a post whose
 * whole content is a {@code Multi Select} of section URLs — renders as an empty layout: the
 * digest composes its sections (SH169) and the previewer could not, which is half of SH249's
 * disagreement. Without {@code search}, a tag page or a "more about this" listing had to be a
 * folder query, so the vocabulary could express "everything in /blog" and not "everything
 * about handbooks".</p>
 *
 * <h2>What is asserted here</h2>
 *
 * <p>The behaviours that are decisions rather than mechanics: the row shape is the same one
 * {@code query} yields (so a template body is interchangeable), the reference order is the
 * <em>field's</em> order and not a database order, a dangling reference is skipped while a
 * wholly-unresolvable field renders {@code {{else}}}, the search term is the SH131 predicate
 * (title, summary <em>and</em> field data — not a title match), a blank term is not "everything
 * matched", and an unknown type filter yields nothing rather than the whole site.</p>
 *
 * <p>Its own database, for {@link ShPageRenderTest}'s reason (SH246): the system post-types it
 * creates are instance-wide and the SH164/SH239 benchmarks measure documents whose size depends
 * on how many exist.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh247;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShRenderRelationSearchTest {

    private static final String LAYOUT_TITLE = "SH247_LAYOUT";

    /**
     * The page's own template. {@code SECTIONS} is the field, so the helper is handed the
     * field's <em>value</em> — which is what makes it work without reading the post-type.
     */
    private static final String LAYOUT_HTML = """
            <html><body>
            <main>{{#getRelation SECTIONS}}<section data-t="{{type}}" data-i="{{@index}}"\
            {{#if @first}} data-first{{/if}}{{#if @last}} data-last{{/if}}><h2>{{title}}</h2>\
            {{{TEXT}}}</section>{{else}}<p id="none">no sections</p>{{/getRelation}}</main>
            <aside>{{#search q="handbook" limit=10}}<a href="{{link}}">{{title}}</a>\
            {{else}}<span id="nohits">nothing</span>{{/search}}</aside>
            <nav>{{#search q="handbook" limit=10 locale=post.locale}}<i>{{title}}</i>{{/search}}</nav>
            <footer>{{#query limit=50 locale=post.locale}}<b>{{title}}</b>{{/query}}</footer>
            </body></html>""";

    @Autowired
    private ShPageRenderService shPageRenderService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String pageType;
    private String sectionType;

    @BeforeAll
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        siteName = "sh247-" + suffix;
        pageType = "Sh247Page" + suffix;
        sectionType = "Sh247Section" + suffix;

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");

            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            site.setPostTypeLayout("{\"" + pageType + "\":\"" + LAYOUT_TITLE + "\"}");
            entityManager.persist(site);

            ShFolder root = folder(site, null, "root", (byte) 1, when);

            ShPostType page = postType(pageType, (byte) 0, when);
            ShPostType section = postType(sectionType, (byte) 0, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);

            post(site, root, layoutType, LAYOUT_TITLE, "/layout",
                    "{\"HTML\":" + quote(LAYOUT_HTML) + "}", when);

            // Deliberately created in the reverse of the order the page names them, so the
            // assertion on order cannot pass by accident of insertion or of the date sort.
            post(site, root, section, "Second section", "/sections/second",
                    "{\"TEXT\":\"<p>second body</p>\"}", when);
            post(site, root, section, "First section", "/sections/first",
                    "{\"TEXT\":\"<p>first body</p>\"}", when);

            // The searchable one: the term is in the *body*, never in the title, which is what
            // makes this a test of SH131's predicate rather than of a title match.
            post(site, root, section, "Onboarding", "/onboarding",
                    "{\"TEXT\":\"<p>Read the handbook before you start.</p>\"}", when);
            // SH1278 — the same term in another language, which a page's `locale=` must leave out.
            post(site, root, section, "Integracao", "/integracao",
                    "{\"TEXT\":\"<p>Leia o handbook antes de comecar.</p>\"}", when).setLocale("pt-br");

            // The page: two live references, then one that resolves to nothing.
            post(site, root, page, "Home", "/",
                    "{\"SECTIONS\":[\"/sections/first\",\"/sections/second\",\"/sections/gone\"]}",
                    when);
            post(site, root, page, "Empty", "/empty", "{\"SECTIONS\":[\"/nowhere\"]}", when);
            post(site, root, page, "Untouched", "/untouched", "{}", when);
        });
    }

    // ── getRelation ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a page composed of section references renders its sections, in its own order")
    void getRelationComposesSections() {
        String html = shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html();

        assertThat(html)
                .as("the referenced posts' own fields, resolved through the field's value alone")
                .contains("<h2>First section</h2>")
                .contains("<p>first body</p>")
                .contains("<h2>Second section</h2>");
        assertThat(html.indexOf("First section"))
                .as("""
                        the order is the field's, not the database's — the two sections were \
                        inserted in the opposite order and share a date, so a helper that sorted \
                        them would put Second first. A page's sections are a composition, and \
                        reordering one silently rearranges the page.""")
                .isLessThan(html.indexOf("Second section"));
        assertThat(html)
                .as("the row shape is query's, so `type` is there and a template body is portable")
                .contains("data-t=\"" + sectionType + "\"");
    }

    /**
     * SH777 — the positional variables a relation had none of.
     *
     * <h2>Found by needing it</h2>
     *
     * <p>{@code getRelation} called {@code options.fn(row)} with a plain context, and Handlebars'
     * {@code @index}, {@code @first} and {@code @last} come from a <b>data frame</b> that nothing
     * created. SH772 hit it looking for somewhere to emit a page-wide fact once: the natural
     * spelling — fire on the first row that carries markup — did not exist, so that task shipped a
     * per-row emission and stated the bound instead.</p>
     *
     * <h2>Why the assertion renders rather than reading the helper</h2>
     *
     * <p>Because what is claimed is that <b>a template can ask</b>. {@code options.data(…)} being
     * called proves nothing about whether <code>{{@index}}</code> resolves — the frame has to
     * survive into the block body, which only the engine decides. Same argument SH384's pager made
     * for the listing, and the reason its three names are reused rather than a second spelling
     * invented for one product.</p>
     */
    @Test
    @DisplayName("SH777: a relation's rows carry @index, @first and @last, like an each block's")
    void getRelationRowsCarryTheirPosition() {
        String html = shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html();

        assertThat(html)
                .as("""
                        SH777: the positions Handlebars publishes for its own each block, on the \
                        helper that walks a page's sections. Without them a template cannot draw a \
                        separator between rows and not after the last, size the first section \
                        differently, or emit aria-setsize — each needs a field on every section \
                        carrying its own position, which goes stale the moment somebody reorders.""")
                .contains("data-i=\"0\"")
                .contains("data-i=\"1\"");
        assertThat(html.split("data-first", -1))
                .as("SH777: first fires once, on row zero — a page-wide fact emitted inside the "
                        + "loop is only expressible if exactly one row can claim to be the first")
                .hasSize(2);
        assertThat(html.split("data-last", -1))
                .as("and last once, on the final row, which is what makes a pager or a summary "
                        + "drawn after the rows possible at all (SH384's argument, one helper over)")
                .hasSize(2);
        assertThat(html.indexOf("data-first"))
                .as("and they are not the same row here, or the pair would pass on a one-row page "
                        + "while saying nothing about ordering")
                .isLessThan(html.indexOf("data-last"));
    }

    @Test
    @DisplayName("a reference that resolves to nothing is skipped, and the rest still render")
    void aDanglingReferenceIsSkipped() {
        String html = shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html();

        // `/sections/gone` is in the field and is not in the site. The renderer does not mark it
        // — unlike SH183's missing *region* — because SH154's `dangling-reference` rule already
        // reports it with an address and a fix, and one defect in two reports is two reports that
        // can disagree. What must not happen is the whole block failing.
        assertThat(html).contains("<h2>First section</h2>").contains("<h2>Second section</h2>");
        assertThat(html).doesNotContain("id=\"none\"");
        assertThat(html)
                .as("a skipped reference leaves no empty section behind")
                .doesNotContain("<section data-t=\"\"");
    }

    @Test
    @DisplayName("a field whose references all fail renders {{else}}, not an empty element")
    void nothingResolvedRendersElse() {
        assertThat(shPageRenderService.render(siteName, "/empty", ShPostState.DRAFT).html())
                .as("""
                        "the references are all broken" and "there are no references" must both \
                        reach the else branch — an author who writes a fallback is entitled to \
                        see it rather than an empty <main>.""")
                .contains("id=\"none\"");
    }

    @Test
    @DisplayName("a post with no reference field at all renders {{else}} too")
    void anAbsentFieldRendersElse() {
        assertThat(shPageRenderService.render(siteName, "/untouched", ShPostState.DRAFT).html())
                .contains("id=\"none\"");
    }

    // ── search ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("search matches field data, not just the title — it is the CDA's own predicate")
    void searchUsesTheFullTextPredicate() {
        String html = shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html();

        assertThat(html)
                .as("""
                        "handbook" appears only in the body of /onboarding. A title-only match \
                        would return nothing here and would make the renderer disagree with the \
                        delivery search and the SH240 console listing about what "matches" means.""")
                .contains("<a href=\"/onboarding\">Onboarding</a>");
        assertThat(html).doesNotContain("id=\"nohits\"");
    }

    @Test
    @DisplayName("SH1278: locale=post.locale narrows search and query to the page's language")
    void localeNarrowsToThePagesLanguage() {
        String html = shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html();
        String aside = between(html, "<aside>", "</aside>");
        String nav = between(html, "<nav>", "</nav>");
        String footer = between(html, "<footer>", "</footer>");

        assertThat(aside)
                .as("SH1278: without locale= a search is every locale, so no existing template "
                        + "returns less than it did")
                .contains("Onboarding").contains("Integracao");
        assertThat(nav)
                .as("SH1278: the en page's search keeps its own row and drops the pt-br one, the "
                        + "CDA's /search?locale= meaning (SH1057)")
                .contains("<i>Onboarding</i>").doesNotContain("Integracao");
        assertThat(footer)
                .as("SH1278: {{#query}} takes the same locale=, on its windowed path too")
                .contains("<b>Onboarding</b>").doesNotContain("Integracao");
    }

    private static String between(String html, String open, String close) {
        int start = html.indexOf(open);
        return html.substring(start, html.indexOf(close, start));
    }

    @Test
    @DisplayName("rendering the same page twice produces the same bytes")
    void renderingIsStable() {
        // Both new helpers yield listings, and a listing that reorders itself between renders
        // moves SH101's digest for a change nobody made. `getRelation` is ordered by the field
        // and `search` by the shared date-then-id comparator.
        assertThat(shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html())
                .isEqualTo(shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html());
    }

    // ── fixture ─────────────────────────────────────────────────────────────────────

    private ShFolder folder(ShSite site, ShFolder parent, String name, byte root, Instant when) {
        ShFolder folder = new ShFolder();
        folder.setName(name);
        folder.setRootFolder(root);
        // Only a root folder carries shSite — SH128's invariant.
        folder.setShSite(root == 1 ? site : null);
        folder.setParentFolder(parent);
        folder.setDate(when);
        folder.setOwner("admin");
        entityManager.persist(folder);
        return folder;
    }

    private ShPostType postType(String name, byte system, Instant when) {
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
        type.setSystem(system);
        type.setDate(when);
        type.setOwner("admin");
        entityManager.persist(type);
        return type;
    }

    private ShPostUnified post(ShSite site, ShFolder folder, ShPostType type, String title, String furl,
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
        return post;
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "") + "\"";
    }
}
