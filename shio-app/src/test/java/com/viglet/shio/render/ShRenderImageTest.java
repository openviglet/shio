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
 * SH390 — {@code {{#image}}}, and the two facts a template could not have.
 *
 * <h2>What the task turned out to be</h2>
 *
 * <p>SH390 offered three answers and said the choice was the task: build derivatives into the tree,
 * add a resizing endpoint, or ship "the helper shape" and delegate. It called the second <em>"a new
 * subsystem and a cache-invalidation question"</em> — and that subsystem <b>already exists</b>
 * (SH40/SH41: {@code ShImageTransformPipeline}, served with {@code ?w=}, cached, guarded, optionally
 * signed). So two of the three options were about building something built, and what was actually
 * missing is the small third thing: a template cannot know an image's <b>intrinsic width</b> (so every
 * candidate it wrote would risk upscaling) and cannot compute an <b>HMAC</b> when signing is on.
 *
 * <h2>Its own database, and one signing property</h2>
 *
 * <p>The signing half needs a configured secret, and toggling a property is what a second context
 * would otherwise be for — so both states are exercised here by asserting the *shape* of a signed
 * srcset rather than by running the suite twice.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-sh390;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.image-transform.signing.enabled=true",
        "shio.image-transform.signing.secret=sh390-test-secret" })
@TestInstance(Lifecycle.PER_CLASS)
class ShRenderImageTest {

    private static final String LAYOUT_HTML = """
            <html><body>
            {{#image HERO}}<img src="{{url}}" width="{{width}}" height="{{height}}"\
             srcset="{{srcset}}" sizes="{{sizes}}" alt="{{alt}}">{{/image}}
            {{#image FLAT sizes="50vw"}}<i class="flat" data-w="{{width}}" data-s="{{srcset}}"\
            {{{altAttr}}}></i>{{/image}}
            {{#image FLAT decorative="true"}}<i class="deco"{{{altAttr}}}></i>{{/image}}
            {{#image MISSING}}<b>never</b>{{else}}<p class="none">no image</p>{{/image}}
            </body></html>""";

    @Autowired
    private ShPageRenderService shPageRenderService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;

    @BeforeAll
    void setup() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        siteName = "sh390-" + tag;
        String pageType = "Sh390Page" + tag;

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            site.setPostTypeLayout("{\"" + pageType + "\":\"SH390_LAYOUT\"}");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setPosition((byte) 1);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            ShPostType fileType = postType(ShSystemPostType.FILE, (byte) 0, when);
            ShPostType page = postType(pageType, (byte) 0, when);

            post(site, root, layoutType, "SH390_LAYOUT", "/l",
                    "{\"HTML\":" + quote(LAYOUT_HTML) + "}", when);

            // A wide image: the ladder has room below it. `WIDTH`/`HEIGHT` are what
            // ShStaticFileService writes at upload, which is why this costs no file read.
            String hero = UUID.randomUUID().toString();
            post(hero, site, root, fileType, "hero.jpg", "/f/hero",
                    "{\"FILE\":\"hero.jpg\",\"WIDTH\":1600,\"HEIGHT\":900,\"ALT\":\"A wide hero\"}", when);

            // An image nothing measured — an SVG, or a file uploaded before the reader existed.
            String flat = UUID.randomUUID().toString();
            post(flat, site, root, fileType, "logo.svg", "/f/logo",
                    "{\"FILE\":\"logo.svg\"}", when);

            post(site, root, page, "Home", "/",
                    "{\"HERO\":\"" + hero + "\",\"FLAT\":\"" + flat + "\",\"MISSING\":\"\"}", when);
        });
    }

    @Test
    @DisplayName("the intrinsic size reaches the template, so an <img> can stop the layout shifting")
    void theIntrinsicSizeIsOnTheRow() {
        // Already on the File post's own data (ShStaticFileService writes it at upload), so this is
        // zero extra queries and zero file reads — which is why it is a helper and not a build pass.
        assertThat(render()).contains("width=\"1600\" height=\"900\"");
        assertThat(render()).contains("alt=\"A wide hero\"");
    }

    @Test
    @DisplayName("the srcset never offers a width above the original, which is the fidelity claim")
    void theLadderNeverUpscales() {
        String html = render();

        // 1600 wide: 320/640/960/1280 fit below it, and the original is the largest entry. A srcset
        // offering 1920w for a 1600px image tells the browser a lie it will act on.
        for (int width : new int[] { 320, 640, 960, 1280, 1600 }) {
            assertThat(html).as("%dw", width).contains(width + "w");
        }
        assertThat(html).doesNotContain("1920w");
    }

    @Test
    @DisplayName("each candidate is signed, which is the half only the server can do")
    void everyCandidateIsSigned() {
        String html = render();

        // With signing on, an unsigned transform is a 403 — so a srcset a template composed by hand
        // would be a page of broken images. This is the reason the helper emits whole URLs.
        //
        // The separator is the ESCAPED ampersand, not the raw one: Handlebars escapes `{{srcset}}`
        // because it is an attribute value, which is correct HTML and what a browser parses back. The
        // first draft of this assertion looked for the raw form and failed — asserting that would have
        // been asserting that the renderer had an XSS hole.
        assertThat(html.split("&amp;sig=", -1)).hasSize(6);
        assertThat(html).contains("/file_source/");
    }

    @Test
    @DisplayName("an image nothing measured yields the plain url and no srcset, not a fabricated ladder")
    void anUnmeasuredImageDegrades() {
        String html = render();

        // `srcset=""` means "use src" in HTML, which is exactly right. Inventing a ladder from a width
        // nobody knows is the one thing that would produce broken images rather than unoptimised ones.
        assertThat(html).contains("data-w=\"0\" data-s=\"\"");
    }

    @Test
    @DisplayName("an empty field renders {{else}}, so a page with no hero is not a broken <img>")
    void anAbsentImageRendersTheInverse() {
        assertThat(render()).contains("<p class=\"none\">no image</p>");
    }

    @Test
    @DisplayName("sizes defaults to 100vw and takes what the template passes")
    void sizesIsAHashWithADefault() {
        String html = render();

        assertThat(html).contains("sizes=\"100vw\"");
        assertThat(html).contains("class=\"flat\"");
    }

    @Test
    @DisplayName("rendering the same page twice produces the same bytes, signatures included")
    void renderingIsStable() {
        assertThat(render()).isEqualTo(render());
    }

    private String render() {
        return shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html();
    }

    // ── fixture ─────────────────────────────────────────────────────────────────────

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
                      String data, Instant when) {
        post(UUID.randomUUID().toString(), site, folder, type, title, furl, data, when);
    }

    private void post(String id, ShSite site, ShFolder folder, ShPostType type, String title,
                      String furl, String data, Instant when) {
        ShPostUnified post = new ShPostUnified();
        post.setId(id);
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

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "") + "\"";
    }

    @Test
    @DisplayName("SH1031: an undescribed image emits no alt attribute, so the finding stays reachable")
    void sh1031AnUndescribedImageEmitsNoAttribute() {
        String html = render();

        // The three states HTML has, and the one the helper had no spelling for: `alt="text"` is
        // described, `alt=""` is "decorative, skip it", and NO attribute is "nobody has said". The
        // helper wrote the second whenever ALT was null — so a site built the recommended way told
        // every screen reader to skip every undescribed image, and `image-no-alt`, which fires on
        // an ABSENT attribute, could never fire on it.
        assertThat(html)
                .as("""
                        SH1031: FLAT has no ALT and was not declared decorative, so its tag carries                         no alt at all. An empty one would be a claim nobody made.""")
                .contains("class=\"flat\"")
                .doesNotContain("data-s=\"\" alt=\"\"");
        assertThat(html)
                .as("""
                        SH1031: and `decorative=true` is the opt-in that writes the empty one.                         Decoration is a decision somebody made, so it is recorded rather than                         inferred from a blank field.""")
                .contains("class=\"deco\" alt=\"\"");
    }

    @Test
    @DisplayName("SH1031: a described image still carries its text, through the whole attribute")
    void sh1031ADescribedImageCarriesItsText() {
        assertThat(render())
                .as("SH1031: `altAttr` is the whole attribute, for the reason `srcset` is — a "
                        + "template cannot express an attribute's own absence")
                .contains("alt=\"A wide hero\"");
    }
}
