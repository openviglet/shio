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
 * SH382 — {@code {{#menu title=}}}, because a site's menu was the folder tree or nothing.
 *
 * <h2>What SH381 did not fix</h2>
 *
 * <p>SH381 gave a {@code {{#navigation}}} row the {@code url} its docstring had promised, and the
 * folder tree is <em>still</em> the wrong source for a menu. Navigation is <b>curated</b>: a label
 * that differs from the page title, groups that are not folders, an ordering that is editorial,
 * entries that leave the site, a page deliberately left out. A folder chain expresses none of them,
 * so every site hand-wrote its nav into every layout — and each copy then drifted.
 *
 * <h2>What is asserted here, and why each one is a decision rather than a detail</h2>
 *
 * <p>The four that could plausibly have gone the other way: a <b>page listed directly</b> needs no
 * wrapper post (the cheap case must stay cheap, or nobody uses the vocabulary); a <b>nested menu is a
 * row with a name and no url</b>, which is SH381's own convention reused so nesting needs no new row
 * key and no new helper; <b>two candidates yield nothing</b>, because a fabricated menu is worse than
 * an empty one; and a <b>cycle stops</b>, because a menu naming itself is authorable content and
 * rendering it would loop until the request died.
 *
 * <p>Its own database, for {@link ShRenderRelationSearchTest}'s reason (SH246): the system post-types
 * it creates are instance-wide and the SH164/SH239 benchmarks measure documents whose size depends on
 * how many exist.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh382;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShRenderMenuTest {

    private static final String LAYOUT_TITLE = "SH382_LAYOUT";

    /**
     * The template a real layout writes, and the branch is the point: a row with a url is a link, a
     * row without one is a group heading whose children are another menu. One shape, arbitrary depth.
     */
    private static final String LAYOUT_HTML = """
            <html><body><nav>
            {{#menu title="Primary"}}\
            {{#if url}}<a href="{{url}}">{{name}}</a>\
            {{else}}<span class="group">{{name}}</span>\
            {{#menu title=title}}<a class="sub" href="{{link}}">{{name}}</a>\
            {{#if current}}<b class="here">{{name}}</b>{{/if}}{{/menu}}\
            {{/if}}{{#if current}}<b class="here">{{name}}</b>{{/if}}\
            {{#if ancestor}}<i class="above">{{name}}</i>{{/if}}\
            {{else}}<p class="empty">no menu</p>{{/menu}}
            </nav><aside>
            {{#menu title="Nowhere"}}<a href="{{url}}">{{name}}</a>\
            {{else}}<p class="missing">no such menu</p>{{/menu}}
            </aside><footer>
            {{#menu title="Ambiguous"}}<a href="{{url}}">{{name}}</a>\
            {{else}}<p class="ambiguous">two candidates</p>{{/menu}}
            </footer><section>
            {{#menu title="Loop"}}{{#menu title=title}}{{#menu title=title}}\
            <a href="{{url}}">{{name}}</a>\
            {{else}}<p class="cycle">stopped</p>{{/menu}}{{/menu}}{{/menu}}
            </section></body></html>""";

    @Autowired
    private ShPageRenderService shPageRenderService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String pageType;
    private String menuType;
    private String itemType;

    @BeforeAll
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        siteName = "sh382-" + suffix;
        pageType = "Sh382Page" + suffix;
        // The vocabulary's names are `Menu` and `MenuItem`, and the fixture deliberately does NOT use
        // them: the helper's contract is structural (a post whose `items` lists addresses), so a
        // fixture that used the real names could not tell a structural match from a hardcoded one.
        menuType = "Sh382Group" + suffix;
        itemType = "Sh382Entry" + suffix;

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");

            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            site.setPostTypeLayout("{\"" + pageType + "\":\"" + LAYOUT_TITLE + "\"}");
            entityManager.persist(site);

            ShPostType page = postType(pageType, when);
            ShPostType group = postType(menuType, when);
            ShPostType entry = postType(itemType, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, when);

            ShFolder root = folder(site, null, "root", (byte) 1, when);
            String layoutId = UUID.randomUUID().toString();
            String homeId = UUID.randomUUID().toString();
            for (ShPostState state : new ShPostState[] { ShPostState.DRAFT, ShPostState.PUBLISHED }) {
                post(layoutId, site, root, layoutType, LAYOUT_TITLE, "/layout",
                        "{\"HTML\":" + quote(LAYOUT_HTML) + "}", when, state);
                post(homeId, site, root, page, "Home", "/", "{}", when, state);
            }

            // The pages a menu points at.
            post(site, root, page, "Pricing", "/pricing", "{}", when, ShPostState.DRAFT);
            post(site, root, page, "Our whole story, at length", "/about", "{}", when, ShPostState.DRAFT);
            post(site, root, page, "Enterprise", "/solutions/enterprise", "{}", when, ShPostState.DRAFT);
            // SH1267 — a page below a menu entry in the URL, which no menu lists itself.
            post(site, root, page, "Teams", "/pricing/teams", "{}", when, ShPostState.DRAFT);

            // A label override: the page's title is long and the menu says "About".
            post(site, root, entry, "About", "/menu/about",
                    json("label", "About", "target", "[\"/about\"]"), when, ShPostState.DRAFT);
            // An entry that leaves the site.
            post(site, root, entry, "Status", "/menu/status",
                    json("label", "Status", "href", "https://status.example.com"), when, ShPostState.DRAFT);
            // An entry whose target does not exist — SH154's finding, and no fabricated fallback here.
            post(site, root, entry, "Gone", "/menu/gone",
                    json("label", "Gone", "target", "[\"/no-such-page\"]"), when, ShPostState.DRAFT);

            // The nested group, which is a menu naming a menu.
            post(site, root, group, "Solutions", "/menu/solutions",
                    json("items", "[\"/solutions/enterprise\"]"), when, ShPostState.DRAFT);

            // The primary menu: a page listed directly, a label override, an external entry, a
            // dangling one, and a nested group — in the order the author wrote them.
            post(site, root, group, "Primary", "/menu/primary",
                    json("items", "[\"/pricing\",\"/menu/about\",\"/menu/status\",\"/menu/gone\","
                            + "\"/menu/solutions\"]"), when, ShPostState.DRAFT);

            // Two menus with one title — ambiguous, so nothing rather than a guess.
            post(site, root, group, "Ambiguous", "/menu/ambiguous-one",
                    json("items", "[\"/pricing\"]"), when, ShPostState.DRAFT);
            post(site, root, group, "ambiguous", "/menu/ambiguous-two",
                    json("items", "[\"/about\"]"), when, ShPostState.DRAFT);

            // A cycle: Loop names Loop Inner, which names Loop back again.
            post(site, root, group, "Loop", "/menu/loop",
                    json("items", "[\"/menu/loop-inner\"]"), when, ShPostState.DRAFT);
            post(site, root, group, "Loop Inner", "/menu/loop-inner",
                    json("items", "[\"/menu/loop\"]"), when, ShPostState.DRAFT);
        });
    }

    @Test
    @DisplayName("a page listed directly is a link with its own title, so the cheap case stays cheap")
    void aPageNeedsNoWrapperPost() {
        // If every entry needed a MenuItem, the smallest useful menu would be six posts instead of
        // one, and nobody would use the vocabulary — they would go on hand-writing the nav.
        assertThat(render(ShPostState.DRAFT)).contains("<a href=\"/pricing\">Pricing</a>");
    }

    @Test
    @DisplayName("a label overrides the target page's title, which is the whole reason a menu is content")
    void theLabelWins() {
        String html = render(ShPostState.DRAFT);

        assertThat(html)
                .as("the page is titled \"Our whole story, at length\" and the menu says About")
                .contains("<a href=\"/about\">About</a>");
        assertThat(html).doesNotContain("Our whole story, at length</a>");
    }

    @Test
    @DisplayName("an external entry keeps its absolute URL, because a real menu leaves the site")
    void anExternalEntryIsALink() {
        assertThat(render(ShPostState.DRAFT))
                .contains("<a href=\"https://status.example.com\">Status</a>");
    }

    @Test
    @DisplayName("a target that resolves to nothing yields no url, not the wrapper post's own address")
    void aDanglingTargetDoesNotFallThrough() {
        String html = render(ShPostState.DRAFT);

        // The fallback would send a visitor to `/menu/gone` — a post with a label and no content. It
        // is not marked either: SH154's `dangling-reference` reports it, and one defect in two
        // reports is two reports that can disagree.
        assertThat(html).contains("<span class=\"group\">Gone</span>");
        assertThat(html).doesNotContain("href=\"/menu/gone\"");
    }

    @Test
    @DisplayName("a nested menu is a row with a name and no url, and the same helper recurses into it")
    void nestingNeedsNoNewShape() {
        String html = render(ShPostState.DRAFT);

        // SH381's convention, reused rather than re-invented: an absent url beside a present name is
        // a group heading. That is what lets one helper and one row shape carry arbitrary depth.
        assertThat(html).contains("<span class=\"group\">Solutions</span>");
        assertThat(html).contains("<a class=\"sub\" href=\"/solutions/enterprise\">Enterprise</a>");
    }

    @Test
    @DisplayName("the author's order is the render order, because an editorial ordering is the point")
    void theOrderIsTheItemsList() {
        String html = render(ShPostState.DRAFT);

        // Not sorted by date like every other listing helper — a menu's order is a decision somebody
        // made, and re-sorting it would discard the only thing the items field records.
        assertThat(html.indexOf("/pricing")).isLessThan(html.indexOf("/about"));
        assertThat(html.indexOf("/about")).isLessThan(html.indexOf("status.example.com"));
        assertThat(html.indexOf("status.example.com")).isLessThan(html.indexOf("Solutions</span>"));
    }

    @Test
    @DisplayName("a menu nobody wrote renders {{else}}, so an unfinished layout is visibly unfinished")
    void anAbsentMenuRendersTheInverse() {
        assertThat(render(ShPostState.DRAFT)).contains("<p class=\"missing\">no such menu</p>");
    }

    @Test
    @DisplayName("two menus with one title yield nothing, rather than whichever the database returned")
    void anAmbiguousTitleIsNotResolvedBySortOrder() {
        String html = render(ShPostState.DRAFT);

        // Same decision as SH381's url: choosing between two candidates is a fabricated answer
        // wearing a deterministic hat. An empty menu is visibly unfinished; somebody else's menu is
        // not. The two titles here differ only in case, which the match deliberately ignores.
        assertThat(html).contains("<p class=\"ambiguous\">two candidates</p>");
    }

    @Test
    @DisplayName("a menu that names itself round a chain stops at the repeat, rather than rendering it")
    void aCycleStopsAtTheRepeat() {
        // The claim worth NOT making: that without the guard this would hang. It could not —
        // `ShTemplateEngine` refuses partials, so a template cannot include itself and the recursion
        // is bounded by the layout's own nesting depth whatever the content says. The first draft of
        // the helper's docstring asserted the hang, and finding that out is why this fixture nests
        // three deep: two levels never reach the cycle, so a two-level template would have passed
        // with no guard at all and proved nothing.
        //
        // What it does buy: Loop names Loop Inner which names Loop, so the third level would
        // otherwise render the cycle — wrong output that looks like a real menu, at a site scan per
        // level. The author gets an empty branch instead, which is what they meant.
        assertThat(render(ShPostState.DRAFT)).contains("<p class=\"cycle\">stopped</p>");
    }

    @Test
    @DisplayName("the guard does not leak, so the same menu renders again in the next request")
    void theCycleGuardIsReleased() {
        // A ThreadLocal on a pooled thread is a leak waiting to happen: without the `finally`, the
        // first render would poison every later one on that thread and the menu would simply vanish.
        assertThat(render(ShPostState.DRAFT)).isEqualTo(render(ShPostState.DRAFT));
        assertThat(render(ShPostState.DRAFT)).contains("<a href=\"/pricing\">Pricing</a>");
    }

    @Test
    @DisplayName("rendering the same page twice produces the same bytes")
    void renderingIsStable() {
        assertThat(render(ShPostState.DRAFT)).isEqualTo(render(ShPostState.DRAFT));
    }

    @Test
    @DisplayName("SH1267: each page marks the entry that is it, and the entries that lead to it")
    void theCurrentEntryIsMarked() {
        assertThat(render("/"))
                .as("SH1267: the home page is in no entry, so nothing is marked")
                .doesNotContain("class=\"here\"", "class=\"above\"");

        assertThat(render("/pricing"))
                .as("SH1267: a page listed directly is current on itself and nothing leads to it")
                .contains("<b class=\"here\">Pricing</b>")
                .doesNotContain("class=\"above\"");

        assertThat(render("/pricing/teams"))
                .as("SH1267: a page below an entry in the URL makes that entry an ancestor, not current")
                .contains("<i class=\"above\">Pricing</i>")
                .doesNotContain("class=\"here\"");

        String enterprise = render("/solutions/enterprise");
        assertThat(enterprise)
                .as("SH1267: inside a nested menu the entry is current and its group heading leads to it")
                .contains("<b class=\"here\">Enterprise</b>", "<i class=\"above\">Solutions</i>");
        assertThat(enterprise).doesNotContain("<b class=\"here\">Solutions</b>",
                "<i class=\"above\">Pricing</i>");
    }

    private String render(ShPostState state) {
        return shPageRenderService.render(siteName, "/", state).html();
    }

    private String render(String url) {
        return shPageRenderService.render(siteName, url, ShPostState.DRAFT).html();
    }

    // ── fixture ─────────────────────────────────────────────────────────────────────

    /** `{"a":"x","b":"y"}` from alternating name/value pairs; a value starting with `[` stays raw. */
    private static String json(String... pairs) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                out.append(',');
            }
            String value = pairs[i + 1];
            out.append(quote(pairs[i])).append(':')
                    .append(value.startsWith("[") ? value : quote(value));
        }
        return out.append('}').toString();
    }

    private ShFolder folder(ShSite site, ShFolder parent, String name, byte root, Instant when) {
        ShFolder folder = new ShFolder();
        folder.setName(name);
        folder.setRootFolder(root);
        // Only a root folder carries shSite — SH128's invariant.
        folder.setShSite(root == 1 ? site : null);
        folder.setParentFolder(parent);
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

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "") + "\"";
    }
}
