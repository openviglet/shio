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

import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShSystemPostType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH383 — the renderer learns about locale, which SH348 stored and nothing could read.
 *
 * <h2>The gap</h2>
 *
 * <p>SH348 gave a file {@code locale:} and {@code translationOf:} and stopped at the projection. The
 * render model exposed no locale at all, so a six-language site could be <b>stored</b> and could not
 * render {@code <html lang>}, an {@code hreflang} pair, or the switcher that is the one control every
 * page of such a site carries.
 *
 * <h2>The three decisions under test</h2>
 *
 * <p>{@code post.locale} is <b>never blank</b> — an empty {@code lang} asserts that the language is
 * unknown, which is worse than a defaulted-but-valid tag. {@code {{#translations}}} takes <b>no
 * argument</b> and excludes the current page, because a switcher would otherwise undo both on every
 * template. And the rows are ordered <b>by locale</b>, not newest-first like every other listing here:
 * a language picker whose items move when somebody edits one translation is a control people misclick.
 *
 * <p>Its own database, for {@link ShRenderRelationSearchTest}'s reason (SH246).
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh383;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShRenderLocaleTest {

    private static final String LAYOUT_TITLE = "SH383_LAYOUT";

    /** What a real multilingual layout writes: the lang attribute, the hreflang set, the switcher. */
    private static final String LAYOUT_HTML = """
            <html lang="{{post.locale}}"><head>
            {{#translations}}<link rel="alternate" hreflang="{{locale}}" href="{{url}}">{{/translations}}
            </head><body><nav class="lang">
            {{#translations}}<a href="{{url}}" hreflang="{{locale}}">{{title}}</a><b>{{language}}</b>\
            {{else}}<span class="only">one language</span>{{/translations}}
            </nav><nav class="menu">{{#menu title="Main"}}<a href="{{url}}">{{name}}</a>{{/menu}}\
            </nav><p class="t">{{t "skip"}}|{{t "missing"}}</p><nav>\
            </nav><p class="default">{{render.defaultLocale}}</p></body></html>""";

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
        siteName = "sh383-" + suffix;
        pageType = "Sh383Page" + suffix;

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");

            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            site.setPostTypeLayout("{\"" + pageType + "\":\"" + LAYOUT_TITLE + "\"}");
            entityManager.persist(site);

            ShPostType page = postType(pageType, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, when);
            ShFolder root = folder(site, when);

            String layoutId = UUID.randomUUID().toString();
            for (ShPostState state : new ShPostState[] { ShPostState.DRAFT, ShPostState.PUBLISHED }) {
                post(layoutId, site, root, layoutType, LAYOUT_TITLE, "/layout",
                        "{\"HTML\":" + quote(LAYOUT_HTML) + "}", when, state, null, null);
            }

            // The home page and its two siblings. SH323 makes a furl site-unique, so `/`, `/pt/` and
            // `/de/` are ordinary distinct URLs in one site — which is exactly why SH383 adds no
            // locale-prefixed route: a second addressing scheme beside SH79 would buy nothing.
            String group = UUID.randomUUID().toString();
            post(group, site, root, page, "Home", "/", "{}", when, ShPostState.DRAFT, "en", group);
            post(UUID.randomUUID().toString(), site, root, page, "Início", "/pt/", "{}", when,
                    ShPostState.DRAFT, "pt-br", group);
            post(UUID.randomUUID().toString(), site, root, page, "Startseite", "/de/", "{}", when,
                    ShPostState.DRAFT, "de", group);

            // A page whose locale column was never written — the row shape that predates SH348.
            post(UUID.randomUUID().toString(), site, root, page, "Legacy", "/legacy", "{}", when,
                    ShPostState.DRAFT, null, null);

            // A page alone in its own group: translated by nobody, which is not a defect.
            String lone = UUID.randomUUID().toString();
            post(lone, site, root, page, "Alone", "/alone", "{}", when, ShPostState.DRAFT, "en", lone);

            // SH1271 — one menu in two languages, translated the way a page is. The Portuguese one
            // has its own title, so only the translation group can tell the layout it is "Main".
            ShPostType menuType = postType("Sh1271Menu" + suffix, when);
            String menus = UUID.randomUUID().toString();
            post(menus, site, root, menuType, "Main", "/menu/main", "{\"items\":[\"/alone\"]}", when,
                    ShPostState.DRAFT, "en", menus);
            post(UUID.randomUUID().toString(), site, root, menuType, "Principal", "/menu/principal",
                    "{\"items\":[\"/pt/\"]}", when, ShPostState.DRAFT, "pt-br", menus);

            // SH1271 — the layout's own text, one table per locale, found by its title.
            post(UUID.randomUUID().toString(), site, root, menuType, "Strings", "/strings/en",
                    "{\"strings\":\"# the layout's own text\\nskip = Skip to content\"}", when,
                    ShPostState.DRAFT, "en", null);
            post(UUID.randomUUID().toString(), site, root, menuType, "Strings", "/strings/pt",
                    "{\"strings\":\"skip = Pular para o conteúdo\"}", when, ShPostState.DRAFT,
                    "pt-br", null);
        });
    }

    @Test
    @DisplayName("post.locale reaches the template, which is the fact the render model was missing")
    void theLocaleIsOnTheModel() {
        assertThat(render("/")).startsWith("<html lang=\"en\">");
        assertThat(render("/pt/")).startsWith("<html lang=\"pt-br\">");
    }

    @Test
    @DisplayName("a post with no locale still renders a valid lang, never an empty one")
    void theLocaleIsNeverBlank() {
        // `lang=""` is not "unspecified" to a browser or a screen reader — it asserts the language is
        // *unknown*, which is worse than a defaulted-but-valid tag. The column is nullable, so this is
        // the ordinary state of any row written before SH348.
        assertThat(render("/legacy"))
                .startsWith("<html lang=\"" + ShPostUnifiedWriteService.DEFAULT_LOCALE + "\">");
    }

    @Test
    @DisplayName("render.defaultLocale is what a template compares against, and it is the writer's")
    void theDefaultIsPublished() {
        // On `render` and not on `site`, because there is no per-site default in this model and naming
        // it `site.defaultLocale` would promise a setting nobody can set.
        assertThat(render("/")).contains("<p class=\"default\">"
                + ShPostUnifiedWriteService.DEFAULT_LOCALE + "</p>");
    }

    @Test
    @DisplayName("the siblings are in scope, so an hreflang set and a switcher are both writable")
    void theSiblingsAreInScope() {
        String html = render("/");

        assertThat(html).contains("<link rel=\"alternate\" hreflang=\"de\" href=\"/de/\">");
        assertThat(html).contains("<link rel=\"alternate\" hreflang=\"pt-br\" href=\"/pt/\">");
        // The sibling's own title stays on the row, in the target language by construction.
        assertThat(html).contains("<a href=\"/pt/\" hreflang=\"pt-br\">Início</a>");
    }

    @Test
    @DisplayName("SH1271: one layout naming one menu renders the menu translated into the page's language")
    void theMenuFollowsThePageLanguage() {
        assertThat(render("/"))
                .as("SH1271: the English page renders the menu it named")
                .contains("<nav class=\"menu\"><a href=\"/alone\">Alone</a>");
        assertThat(render("/pt/"))
                .as("SH1271: the Portuguese page renders Main's pt-br translation, though it is titled "
                        + "Principal and the layout never says so")
                .contains("<nav class=\"menu\"><a href=\"/pt/\">Início</a>");
        assertThat(render("/de/"))
                .as("SH1271: a language the menu was never translated into keeps the one named")
                .contains("<nav class=\"menu\"><a href=\"/alone\">Alone</a>");
    }

    @Test
    @DisplayName("SH1271: a layout's own text comes from the string table in the page's language")
    void theLayoutTextFollowsThePageLanguage() {
        assertThat(render("/"))
                .as("SH1271: the English page reads the English table, and a missing key shows itself")
                .contains("<p class=\"t\">Skip to content|missing</p>");
        assertThat(render("/pt/"))
                .as("SH1271: the Portuguese page reads the Portuguese table from the same layout")
                .contains("<p class=\"t\">Pular para o conteúdo|missing</p>");
    }

    @Test
    @DisplayName("SH1270: each sibling names its language in itself, which is what a switcher shows")
    void theLanguageIsNamed() {
        // A headline is not a language: Quilhara's switcher read "Trilhas de costão e de serra".
        // From a table the product owns rather than CLDR, so a JDK upgrade moves no digest.
        assertThat(render("/"))
                .as("SH1270: the English page names its siblings Deutsch and Português")
                .contains("<b>Deutsch</b>", "<b>Português</b>");
        assertThat(render("/pt/"))
                .as("SH1270: and the Portuguese page names English, in English")
                .contains("<b>English</b>");
        assertThat(com.viglet.shio.render.ShLanguageNames.of("pt_BR")).isEqualTo("Português");
        assertThat(com.viglet.shio.render.ShLanguageNames.of("xx-yy"))
                .as("SH1270: an unknown tag is visibly a code, never a guessed name")
                .isEqualTo("XX");
    }

    @Test
    @DisplayName("the current page is not in its own switcher, so no template has to undo it")
    void selfIsExcluded() {
        assertThat(render("/")).doesNotContain("hreflang=\"en\"");
        assertThat(render("/pt/")).doesNotContain("hreflang=\"pt-br\"");
        // And the other two are both there when the page is one of them, which is the check that
        // "excludes self" did not quietly become "excludes the anchor".
        assertThat(render("/pt/")).contains("hreflang=\"de\"").contains("hreflang=\"en\"");
    }

    @Test
    @DisplayName("the rows are ordered by locale, not by date, so a language picker does not move")
    void theOrderIsByLocale() {
        String html = render("/");

        // Newest-first would reorder the switcher whenever somebody edited one translation.
        assertThat(html.indexOf("hreflang=\"de\"")).isLessThan(html.indexOf("hreflang=\"pt-br\""));
    }

    @Test
    @DisplayName("a page with no siblings renders {{else}}, because monolingual is not a defect")
    void aLonePageHasNoSwitcher() {
        assertThat(render("/alone")).contains("<span class=\"only\">one language</span>");
        assertThat(render("/legacy")).contains("<span class=\"only\">one language</span>");
    }

    @Test
    @DisplayName("rendering the same page twice produces the same bytes")
    void renderingIsStable() {
        assertThat(render("/")).isEqualTo(render("/"));
    }

    private String render(String url) {
        return shPageRenderService.render(siteName, url, ShPostState.DRAFT).html();
    }

    // ── fixture ─────────────────────────────────────────────────────────────────────

    private ShFolder folder(ShSite site, Instant when) {
        ShFolder folder = new ShFolder();
        folder.setName("root");
        folder.setRootFolder((byte) 1);
        // Only a root folder carries shSite — SH128's invariant.
        folder.setShSite(site);
        folder.setPosition((byte) 1);
        folder.setDate(when);
        folder.setOwner("admin");
        entityManager.persist(folder);
        return folder;
    }

    private ShPostType postType(String name, Instant when) {
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
        type.setSystem(ShSystemPostType.PAGE_LAYOUT.equals(name) ? (byte) 1 : (byte) 0);
        type.setDate(when);
        type.setOwner("admin");
        entityManager.persist(type);
        return type;
    }

    private void post(String id, ShSite site, ShFolder folder, ShPostType type, String title,
                      String furl, String data, Instant when, ShPostState state, String locale,
                      String group) {
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
        post.setLocale(locale);
        post.setTranslationGroupId(group);
        entityManager.persist(post);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "") + "\"";
    }
}
