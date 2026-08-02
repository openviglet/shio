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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.exchange.model.ShExchangeFolder;
import com.viglet.shio.exchange.model.ShExchangePackage;
import com.viglet.shio.exchange.model.ShExchangePost;
import com.viglet.shio.exchange.model.ShExchangeSite;
import com.viglet.shio.exchange.site.ShSiteImportService;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH294 — the second half of the class comment's claim: a 2018 package <b>imports and renders</b>.
 *
 * <h2>What SH290 left</h2>
 *
 * <p>{@link ShRenderPostTypes} says the field names it declares are the 2018 package's, <em>"so a
 * real {@code openviglet/bootstrap-site} export imports and renders without a migration"</em>.
 * {@code ShRenderModelSourceTest} asserts the first clause — the <b>model</b> matches
 * {@code /import/post-types.json}, the real 2018 artefact, and every divergence is declared. The
 * second clause, which is the end-to-end evidence for Block P's whole compatibility story, was a
 * sentence: nothing imported 2018-shaped content and asked whether a page came back.</p>
 *
 * <h2>The fixture problem, and how this answers it</h2>
 *
 * <p>SH294 stayed an idea because of the fixture. The package bundled here cannot do the job — its
 * generator is {@code shio-bootstrap}, it declares one post-type ({@code Page}) with lowercase
 * field names, and it carries no template posts at all: the SH71 starter is a content site, not a
 * rendering one. And hand-writing a package risks the worse outcome, which is asserting against a
 * shape the 2018 exporter never produced — proving the code agrees with this test's author.</p>
 *
 * <p>So <b>nothing here is typed</b>. Every post-type name and every field name in the fixture is
 * read out of {@code /import/post-types.json}, whose {@code PageLayout}, {@code Region} and
 * {@code Theme} entries carry their own {@code date} of 2018-06-14 and 2019-07-11, and the read
 * fails the build if one is missing. {@link #theFixtureIsTheArtefactsAndNotItsAuthors} states that
 * as its own assertion rather than leaving it to be inferred from the code.</p>
 *
 * <h2>What is deliberately not claimed</h2>
 *
 * <p>The <b>envelope</b> is the current SH68 format, not 2018's, and that is not a cheat — it is
 * the honest scope of the claim. SH189 records that bringing the real package forward cost seven
 * field renames inferred by reading the {@code ShExchange*} records, and those renames are in the
 * manifest. The comment's promise is about the <em>content</em>: a 2018 template's {@code data} is
 * keyed {@code TITLE}/{@code HTML}/{@code JAVASCRIPT}/{@code CSS}, and the renderer reads those
 * keys unchanged. That is what fails if somebody renames one, and it is what this asserts.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShImportRendersSourceTest {

    /** The 2018 content model, the same artefact {@code ShRenderModelSourceTest} reads. */
    private static final String REFERENCE = "/import/post-types.json";

    @Autowired
    private ShSiteImportService shSiteImportService;
    @Autowired
    private ShPageRenderService shPageRenderService;
    @Autowired
    private ShRenderPostTypes shRenderPostTypes;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("a package whose templates are keyed the 2018 way imports and renders")
    void aPackageInThe2018ShapeImportsAndRenders() {
        // The three template types are the *instance's*, created on demand — a 2018 package does
        // not carry them and must not have to. Calling this here is what an instance that renders
        // has already done, and it is the half of "no migration" that is about the model.
        shRenderPostTypes.ensure();

        Fixture fixture = new Fixture();
        ShSite site = shSiteImportService.importPackage(fixture.build(), "admin");

        assertThat(site).isNotNull();
        String html = shPageRenderService.render(site.getName(), "/", ShPostState.DRAFT).html();

        // The layout's own markup, from a post whose data key is the 2018 `HTML`.
        assertThat(html)
                .as("""
                        the layout did not reach the page. Either `postTypeLayout` did not resolve \
                        the imported layout by title, or the renderer read a field name the 2018 \
                        package does not carry — which is the migration the comment promises there \
                        is none of.""")
                .contains("<main id=\"sh294-layout\">");
        // The region, slotted by `sh-region`, which has been in this content since 2018.
        assertThat(html)
                .as("the slot resolved to no Region post, so composition did not survive import")
                .contains("<p id=\"sh294-region\">")
                .contains("sh-region=\"SH294_BODY\"");
        // The theme, addressed by title from the layout's 2018 `THEME` field.
        assertThat(html)
                .as("the layout's THEME did not resolve to the imported Theme post")
                .contains("--sh294:1");
        // The page's own content, so what rendered is this page and not an empty layout.
        assertThat(html).contains("Imported from 2018");
    }

    @Test
    @DisplayName("every name this fixture uses comes from the 2018 artefact, not from its author")
    void theFixtureIsTheArtefactsAndNotItsAuthors() {
        // The guard that makes the test above evidence rather than a tautology. Without it the
        // fixture could drift to whatever the renderer happens to read, and would still pass — the
        // exact failure §XVI.46 refused to risk when it left SH294 an idea.
        Map<String, List<String>> declared = reference();

        assertThat(declared)
                .as("""
                        %s no longer declares the three render post-types, so this comparison is \
                        meaningless rather than merely failing — the same rule SH290 states.""",
                        REFERENCE)
                .containsKeys(ShSystemPostType.PAGE_LAYOUT, ShSystemPostType.REGION,
                        ShSystemPostType.THEME);

        assertThat(declared.get(ShSystemPostType.PAGE_LAYOUT))
                .as("the layout fields the fixture writes are the 2018 model's")
                .contains(ShSystemPostTypeAttr.TITLE, ShSystemPostTypeAttr.HTML,
                        ShSystemPostTypeAttr.THEME);
        assertThat(declared.get(ShSystemPostType.REGION))
                .contains(ShSystemPostTypeAttr.TITLE, ShSystemPostTypeAttr.HTML);
        assertThat(declared.get(ShSystemPostType.THEME))
                .contains(ShSystemPostTypeAttr.TITLE, ShSystemPostTypeAttr.CSS);
    }

    /** {@code {post-type name: [field name…]}} as the 2018 file declares them. */
    private Map<String, List<String>> reference() {
        try (InputStream stream = getClass().getResourceAsStream(REFERENCE)) {
            assertThat(stream).as("%s is not on the classpath", REFERENCE).isNotNull();
            JsonNode root = objectMapper.readTree(stream);
            Map<String, List<String>> byType = new LinkedHashMap<>();
            for (JsonNode type : root.get("postTypes")) {
                List<String> fields = new ArrayList<>();
                type.get("fields").propertyNames().forEach(fields::add);
                byType.put(type.get("name").asString(), fields);
            }
            return byType;
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + REFERENCE, e);
        }
    }

    /**
     * One site, one folder, four posts — a layout, a region, a theme and the page itself.
     *
     * <p>Ids are the package's own and are reassigned on clone (SH70), so they only have to be
     * internally consistent. The names and the {@code data} keys are the part that matters and
     * they come from {@link #reference()}.</p>
     */
    private final class Fixture {

        private static final String LAYOUT = "SH294_LAYOUT";
        private static final String THEME = "SH294_THEME";
        private static final String REGION = "SH294_BODY";

        private final String suffix = UUID.randomUUID().toString().substring(0, 8);
        private final String siteId = UUID.randomUUID().toString();
        private final String folderId = UUID.randomUUID().toString();
        private final String pageType = "Sh294Page" + suffix;
        private final Map<String, List<String>> declared = reference();

        ShExchangePackage build() {
            ShExchangeSite site = new ShExchangeSite(siteId, "sh294-" + suffix,
                    "SH294 import-and-render fixture", null, "sh294-" + suffix,
                    "{\"" + pageType + "\":\"" + LAYOUT + "\"}", null, null, "admin",
                    List.of(folderId));
            ShExchangeFolder root = new ShExchangeFolder(folderId, "root", "root", true,
                    siteId, null, 0, "admin");

            List<ShExchangePost> posts = List.of(
                    post(ShSystemPostType.THEME, THEME, "/theme", data(ShSystemPostType.THEME,
                            ShSystemPostTypeAttr.TITLE, THEME,
                            ShSystemPostTypeAttr.CSS, ":root{--sh294:1}")),
                    post(ShSystemPostType.PAGE_LAYOUT, LAYOUT, "/layout",
                            data(ShSystemPostType.PAGE_LAYOUT,
                                    ShSystemPostTypeAttr.TITLE, LAYOUT,
                                    ShSystemPostTypeAttr.THEME, THEME,
                                    ShSystemPostTypeAttr.HTML,
                                    // `{{theme.css}}` in a <style> is the shape the 2018 layout
                                    // used: a theme is addressed by title and inlined by the
                                    // layout, not linked as an asset.
                                    "<html><head><style>{{theme.css}}</style></head>"
                                            + "<body><main id=\"sh294-layout\">"
                                            + "<div sh-region=\"" + REGION + "\"></div>"
                                            + "</main></body></html>")),
                    post(ShSystemPostType.REGION, REGION, "/region",
                            data(ShSystemPostType.REGION,
                                    ShSystemPostTypeAttr.TITLE, REGION,
                                    ShSystemPostTypeAttr.HTML,
                                    // A 2018 template names a field at the model root — the page's
                                    // own `TITLE`, not `post.title`, which is the entity property.
                                    "<p id=\"sh294-region\">{{TITLE}}</p>")),
                    // The page. Its own type is not a 2018 name — a site's content types are the
                    // author's, and only the three template types are the model this claim is about.
                    post(pageType, "Imported from 2018", "/", freeform()));

            return new ShExchangePackage(1, "2026-01-01T00:00:00Z", "sh294-fixture",
                    List.of(site), List.of(pageTypeDef()), List.of(root), posts, List.of());
        }

        /**
         * The page's content type, carried by the package the way a real export carries its own.
         *
         * <p>The three template types are deliberately <b>not</b> declared: they are system types
         * this instance creates on demand (`ShRenderPostTypes.ensure()`), and a package that
         * redeclared them would be testing the importer's post-type merge rather than the claim —
         * which is that 2018 template <em>content</em> lands on the current model unchanged.</p>
         */
        private ShPostTypeDef pageTypeDef() {
            ShPostTypeAttrDef title = new ShPostTypeAttrDef(ShSystemPostTypeAttr.TITLE, "Title",
                    null, ShSystemWidget.TEXT, true, true, false, 1, null, List.of());
            return new ShPostTypeDef(pageType, "SH294 Page", "SH294 Pages",
                    "The site's own content type, which no 2018 template governs",
                    null, false, false, List.of(title));
        }

        private ShExchangePost post(String type, String title, String furl, JsonNode data) {
            return new ShExchangePost(UUID.randomUUID().toString(), folderId, type,
                    ShPostState.DRAFT.name(), title, null, furl, "admin", false, null, 0, data);
        }

        /** Refuses to write a key the 2018 model does not declare for that type. */
        private ObjectNode data(String type, String... pairs) {
            List<String> fields = declared.get(type);
            assertThat(fields).as("%s declares no %s", REFERENCE, type).isNotNull();
            ObjectNode node = objectMapper.createObjectNode();
            for (int i = 0; i < pairs.length; i += 2) {
                assertThat(fields)
                        .as("""
                                the fixture is about to write %s.%s, which the 2018 model does not \
                                declare — that would assert against a shape the exporter never \
                                produced, which is the one thing this test may not do.""",
                                type, pairs[i])
                        .contains(pairs[i]);
                node.put(pairs[i], pairs[i + 1]);
            }
            return node;
        }

        /** The page's own data, which no 2018 template type governs. */
        private ObjectNode freeform() {
            ObjectNode node = objectMapper.createObjectNode();
            node.put(ShSystemPostTypeAttr.TITLE, "Imported from 2018");
            return node;
        }
    }
}
