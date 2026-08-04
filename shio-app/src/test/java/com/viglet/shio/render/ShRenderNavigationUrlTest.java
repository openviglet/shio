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
 * SH381 — {@code navigation} yields the {@code url} its own docstring had promised since SH183.
 *
 * <h2>What was wrong, and why the comment is the evidence</h2>
 *
 * <p>The helper documented each row as {@code {name, url, id}} and built {@code {id, name, title}}.
 * So the one helper a site's primary menu exists for rendered labels nobody could click, every
 * shipped template worked around it, and a fixture in {@code ShTemplateFieldLintTest} had been
 * writing {@code <a href="{{url}}">{{name}}</a>} against a field that was never there.
 *
 * <h2>Why the fix is a decision and not a field</h2>
 *
 * <p><b>A folder has no URL.</b> SH79 keeps folder chains and friendly URLs in separate namespaces
 * on purpose, so the value had to be chosen: the folder's <em>index post</em>, absent where there is
 * none, never fabricated. Each of those three is a test below, and the third is the one that matters
 * — an absent {@code url} is a <em>legitimate</em> row (a group heading, which is what a nested menu
 * is made of) rather than an error, so a template branches on it.
 *
 * <p>Its own database, for {@link ShRenderRelationSearchTest}'s reason (SH246): the system
 * post-types it creates are instance-wide and the SH164/SH239 benchmarks measure documents whose
 * size depends on how many exist.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh381;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShRenderNavigationUrlTest {

    private static final String LAYOUT_TITLE = "SH381_LAYOUT";

    /**
     * The branch the whole task exists to make possible: a row with a url is a link, a row without
     * one is a heading. A template that could not tell them apart had to choose between a menu of
     * plain text and a menu of 404s.
     */
    private static final String LAYOUT_HTML = """
            <html><body><nav>
            {{#navigation}}{{#if url}}<a href="{{url}}" data-id="{{id}}">{{name}}</a>\
            {{else}}<span class="group">{{name}}</span>{{/if}}{{/navigation}}
            </nav><aside>
            {{#navigation folder="/solutions"}}<a class="sub" href="{{link}}">{{title}}</a>\
            {{/navigation}}
            </aside></body></html>""";

    @Autowired
    private ShPageRenderService shPageRenderService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String pageType;

    @BeforeAll
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        siteName = "sh381-" + suffix;
        pageType = "Sh381Page" + suffix;

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");

            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            site.setPostTypeLayout("{\"" + pageType + "\":\"" + LAYOUT_TITLE + "\"}");
            entityManager.persist(site);

            ShPostType page = postType(pageType, (byte) 0, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);

            ShFolder root = folder(site, null, "root", (byte) 1, when, (byte) 1);
            // Both states, because one test renders the page PUBLISHED and the layout is a post like
            // any other — a draft-only layout is not found by a published render. **One id for the
            // pair**: publishing keeps the draft's id, so one page is one id and one furl (SH321).
            // The first draft of this fixture minted two, and `ShFixtureShapeGuard` refused it —
            // correctly, since that shape is the duplicate-URL defect rather than a published page.
            String layoutId = UUID.randomUUID().toString();
            String homeId = UUID.randomUUID().toString();
            for (ShPostState state : new ShPostState[] { ShPostState.DRAFT, ShPostState.PUBLISHED }) {
                post(layoutId, site, root, layoutType, LAYOUT_TITLE, "/layout",
                        "{\"HTML\":" + quote(LAYOUT_HTML) + "}", when, state);
                post(homeId, site, root, page, "Home", "/", "{}", when, state);
            }

            // 1. A folder whose index post exists — the ordinary case. The folder is "Solutions"
            //    and the post is "/solutions", which is the case difference SH79 makes normal.
            ShFolder solutions = folder(site, root, "Solutions", (byte) 0, when, (byte) 1);
            post(site, solutions, page, "Solutions", "/solutions", "{}", when, ShPostState.DRAFT);
            // A child *folder*, because navigation lists folders and not posts — the nested-menu
            // assertion needs one, and the first draft of this fixture put a post here instead.
            ShFolder pricing = folder(site, solutions, "Pricing", (byte) 0, when, (byte) 1);
            post(site, pricing, page, "Pricing", "/solutions/pricing", "{}", when, ShPostState.DRAFT);

            // 2. A folder with posts but no index post — a group heading, not a defect.
            ShFolder resources = folder(site, root, "Resources", (byte) 0, when, (byte) 2);
            post(site, resources, page, "A guide", "/resources/a-guide", "{}", when, ShPostState.DRAFT);

            // 3. A folder whose only candidate is PUBLISHED, so a draft render finds it and a
            //    published render does too — but the state is what decides, not the row's existence.
            ShFolder company = folder(site, root, "Company", (byte) 0, when, (byte) 3);
            post(site, company, page, "Company", "/company", "{}", when, ShPostState.PUBLISHED);

            // 4. A folder with a post whose URL belongs to a *different* folder's chain. Nothing
            //    should capture a menu entry it does not own.
            ShFolder legal = folder(site, root, "Legal", (byte) 0, when, (byte) 4);
            post(site, legal, page, "Impostor", "/resources", "{}", when, ShPostState.DRAFT);

            // 5. Two candidates differing only in case — ambiguous, so no url rather than a guess.
            ShFolder press = folder(site, root, "Press", (byte) 0, when, (byte) 5);
            post(site, press, page, "Press lower", "/press", "{}", when, ShPostState.DRAFT);
            post(site, press, page, "Press upper", "/PRESS", "{}", when, ShPostState.DRAFT);
        });
    }

    @Test
    @DisplayName("a folder with an index post yields its friendly URL as the row's url")
    void anIndexPostBecomesTheUrl() {
        String html = render(ShPostState.DRAFT);

        assertThat(html)
                .as("""
                        the field the docstring promised for two releases. The folder is called \
                        "Solutions" and its landing page is "/solutions" — matching those \
                        case-sensitively would make the feature almost never fire, because a \
                        folder name is a label and a friendly URL is a slug (SH79).""")
                .contains("<a href=\"/solutions\"");
        assertThat(html).contains(">Solutions</a>");
    }

    @Test
    @DisplayName("a folder with no index post is a group heading, not a broken link")
    void noIndexPostIsAHeadingRatherThanAFabricatedPath() {
        String html = render(ShPostState.DRAFT);

        // The half that makes the field usable rather than merely present. A menu whose entries
        // 404 is worse than one whose entries are plain text: the second is visibly unfinished.
        assertThat(html)
                .as("Resources holds a post but has no landing page of its own")
                .contains("<span class=\"group\">Resources</span>");
        assertThat(html)
                .as("and no path was invented for it — /resources is a post in Legal, not here")
                .doesNotContain("<a href=\"/resources\"");
    }

    @Test
    @DisplayName("only the folder's own posts are candidates, so nothing captures a menu entry")
    void aPostElsewhereCannotClaimTheChain() {
        String html = render(ShPostState.DRAFT);

        // "/resources" exists as a post — in the Legal folder. If the lookup were by URL across the
        // site rather than within the folder, Legal would link to Resources' chain and Resources
        // would look like it had a landing page.
        assertThat(html)
                .as("Legal has no post whose URL is /legal, so Legal is a heading")
                .contains("<span class=\"group\">Legal</span>");
    }

    @Test
    @DisplayName("two candidates differing only in case yield no url, rather than a guess")
    void anAmbiguousMatchIsNotResolvedBySortOrder() {
        String html = render(ShPostState.DRAFT);

        // Choosing between /press and /PRESS by sort order would be a fabricated path wearing a
        // deterministic hat: the menu would link somewhere plausible and possibly wrong, which is
        // the outcome "never a fabricated path" rules out.
        assertThat(html).contains("<span class=\"group\">Press</span>");
        assertThat(html).doesNotContain("href=\"/PRESS\"");
        assertThat(html).doesNotContain("href=\"/press\"");
    }

    @Test
    @DisplayName("the state decides which posts are candidates, as it does for every listing")
    void theRenderStateBoundsTheLookup() {
        // Company's only candidate is PUBLISHED. A published render must find it; a draft render
        // reads both states and prefers the draft, so it finds it too — what must not happen is a
        // menu that links to a page a visitor cannot see.
        assertThat(render(ShPostState.PUBLISHED))
                .as("a published render finds a published index post")
                .contains("<a href=\"/company\"");
        assertThat(render(ShPostState.DRAFT))
                .as("and a draft render reads both states, so it finds it as well")
                .contains("<a href=\"/company\"");
    }

    @Test
    @DisplayName("`link` carries the same value, so a query body iterates a menu unchanged")
    void linkMirrorsUrlForOneVocabulary() {
        String html = render(ShPostState.DRAFT);

        // `row()` puts both `url` and `link` on a post row; navigation follows that precedent, or a
        // card body written for {{#query}} silently produces <a href=""> over a menu.
        assertThat(html)
                .as("the nested menu uses {{link}} and resolves to the child's index post")
                .contains("<a class=\"sub\" href=\"/solutions/pricing\">Pricing</a>");
    }

    @Test
    @DisplayName("rendering the same page twice produces the same bytes")
    void renderingIsStable() {
        // The url is now one more query per row, and a lookup whose result depended on database
        // order would move SH101's digest for a change nobody made.
        assertThat(render(ShPostState.DRAFT)).isEqualTo(render(ShPostState.DRAFT));
    }

    private String render(ShPostState state) {
        return shPageRenderService.render(siteName, "/", state).html();
    }

    // ── fixture ─────────────────────────────────────────────────────────────────────

    private ShFolder folder(ShSite site, ShFolder parent, String name, byte root, Instant when,
                            byte position) {
        ShFolder folder = new ShFolder();
        folder.setName(name);
        folder.setRootFolder(root);
        // Only a root folder carries shSite — SH128's invariant.
        folder.setShSite(root == 1 ? site : null);
        folder.setParentFolder(parent);
        folder.setPosition(position);
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

    private void post(ShSite site, ShFolder folder, ShPostType type, String title, String furl,
                      String data, Instant when, ShPostState state) {
        post(UUID.randomUUID().toString(), site, folder, type, title, furl, data, when, state);
    }

    /** The id-taking form, for a draft/published pair — which is one id by SH321. */
    private void post(String id, ShSite site, ShFolder folder, ShPostType type, String title,
                      String furl, String data, Instant when, ShPostState state) {
        ShPostUnified post = new ShPostUnified();
        post.setId(id);
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

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "") + "\"";
    }
}
