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

import com.viglet.shio.api.agent.verify.dto.ShAgentDigest;
import com.viglet.shio.persistence.model.folder.ShFolder;
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

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String boundType;
    private String looseType;

    @BeforeAll
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        siteName = "sh249-" + suffix;
        boundType = "Sh249Bound" + suffix;
        looseType = "Sh249Loose" + suffix;

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");

            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            // Only one of the two types is bound, which is what makes the fallback observable.
            site.setPostTypeLayout("{\"" + boundType + "\":\"" + LAYOUT + "\"}");
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
            post(site, root, layoutType, LAYOUT, "/layout", json("HTML",
                    "<html><body><h1>{{post.title}}</h1>"
                            + "<div sh-region=\"" + REGION + "\"></div>"
                            + "<main>{{{TEXT}}}</main></body></html>"), when);
            post(site, root, regionType, REGION, "/region", json("HTML",
                    "<nav><a href=\"/docs\">Docs</a></nav>"), when);

            post(site, root, bound, "Bound page", "/bound",
                    json("TEXT", "<h2>From the page</h2><p>body</p>"), when);
            post(site, root, loose, "Loose page", "/loose",
                    json("TEXT", "<h2>Only the field</h2>"), when);
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
