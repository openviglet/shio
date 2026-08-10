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

import com.viglet.shio.api.agent.verify.dto.ShAgentReport.Finding;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * {@code site-scripts-dropped} fires on a layout that forgot, and not on one that declined (SH421).
 *
 * <h2>What was wrong, and why it is a false positive rather than a nuisance</h2>
 *
 * <p>SH389's rule warns for <b>every</b> bound layout in a site that declares scripts and whose HTML
 * never names {@code site.scripts}. That is right for the case it was built for — a set of pages with
 * no consent banner, rendering perfectly, which is a compliance defect nothing else can see. It is
 * wrong for a transactional email template, a print sheet, an AMP-shaped variant or an embed a
 * partner iframes: each is a legitimate layout that must <em>not</em> load a chat widget, and its
 * author had no way to say so.</p>
 *
 * <p>SH388 wrote the lesson down and it applies directly: <b>a lint over templates that fires on
 * correct work is one authors learn to skip, and the true finding goes unread with it.</b> So the
 * cost of the false positive is not the noise, it is the rule.</p>
 *
 * <h2>Both directions, because an opt-out is only as good as what it does not silence</h2>
 *
 * <p>The fixture holds two bound layouts in one site, alike in every way except the flag. A test that
 * only asserted the silence would pass just as well if the rule had been deleted.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShSiteScriptsDroppedTest {

    private static final String RULE = "site-scripts-dropped";

    /** Two entries, so the finding's count is a number the assertion can recognise. */
    private static final String SCRIPTS_JSON =
            "[{\"src\":\"https://cdn.example.com/consent.js\",\"position\":\"head\"},"
                    + "{\"src\":\"https://cdn.example.com/chat.js\",\"position\":\"bodyEnd\"}]";

    /** Neither layout emits `site.scripts`; only one of them says so on purpose. */
    private static final String LAYOUT_HTML =
            "<html><head><title>{{post.title}}</title></head><body><main>{{TEXT}}</main></body></html>";

    @Autowired
    private ShAgentVerifyService verifyService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String forgotLayout;
    private String declinedLayout;

    @BeforeAll
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        siteName = "sh421-" + suffix;
        String forgotType = "Sh421Web" + suffix;
        String declinedType = "Sh421Mail" + suffix;
        forgotLayout = "L_forgot_" + suffix;
        declinedLayout = "L_declined_" + suffix;

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");

            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            // Both layouts are BOUND and both are USED — the rule only looks at layouts a page
            // actually renders through, so an unbound one would prove nothing either way.
            site.setPostTypeLayout("{\"" + forgotType + "\":\"" + forgotLayout + "\","
                    + "\"" + declinedType + "\":\"" + declinedLayout + "\"}");
            entityManager.persist(site);

            ShFolder root = folder(site, when);

            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when,
                    ShSystemPostTypeAttr.HTML, ShSystemPostTypeAttr.NO_SITE_SCRIPTS);
            ShPostType scriptsType = postType(ShSystemPostType.SITE_SCRIPTS, (byte) 1, when,
                    ShSystemPostTypeAttr.SCRIPTS);
            ShPostType web = postType(forgotType, (byte) 0, when, "TEXT");
            ShPostType mail = postType(declinedType, (byte) 0, when, "TEXT");

            post(site, root, scriptsType, "Scripts", "/site-scripts",
                    json(ShSystemPostTypeAttr.SCRIPTS, SCRIPTS_JSON), when);

            post(site, root, layoutType, forgotLayout, "/l-forgot",
                    json(ShSystemPostTypeAttr.HTML, LAYOUT_HTML), when);
            // Identical HTML. The ONLY difference is the declared intent.
            post(site, root, layoutType, declinedLayout, "/l-declined",
                    json(ShSystemPostTypeAttr.HTML, LAYOUT_HTML,
                            ShSystemPostTypeAttr.NO_SITE_SCRIPTS, "true"), when);

            post(site, root, web, "A web page", "/web", json("TEXT", "<p>hello</p>"), when);
            post(site, root, mail, "A receipt", "/receipt", json("TEXT", "<p>thanks</p>"), when);
        });
    }

    private List<Finding> dropped() {
        return verifyService.verify(siteName, "content", 200).findings().stream()
                .filter(finding -> RULE.equals(finding.rule()))
                .toList();
    }

    @Test
    @DisplayName("SH421: the rule still fires on a bound layout that simply forgot")
    void aLayoutThatForgotIsStillReported() {
        // The half that must not be lost. SH389's finding is a compliance defect that renders
        // perfectly — pages with no consent banner — so an opt-out that quietly disabled the rule
        // would be worse than the false positive it removes.
        assertThat(dropped())
                .as("""
                        a bound, used layout that never emits site.scripts and has NOT declared the \
                        omission is still a warning (SH389, kept by SH421)""")
                .singleElement()
                .satisfies(finding -> {
                    // The address is the SH79 form over the friendly URL, not the title.
                    assertThat(finding.address()).isEqualTo("post:" + siteName + "/l-forgot");
                    assertThat(finding.message()).contains("2 script(s)");
                    // P6 — the finding now teaches BOTH ways out, and the second one is the whole
                    // point of the task: emit them, or say you meant not to.
                    assertThat(finding.fix())
                            .contains("site.scripts.head")
                            .contains(ShSystemPostTypeAttr.NO_SITE_SCRIPTS);
                });
    }

    @Test
    @DisplayName("SH421: and it is silent about the layout that declared the omission")
    void aLayoutThatDeclinedIsNotReported() {
        assertThat(dropped())
                .as("""
                        the email template omits the site's scripts on purpose and says so, so it is \
                        not a finding — the two layouts are byte-identical apart from the flag, which \
                        is what makes this an assertion about the flag rather than about the HTML""")
                .noneSatisfy(finding ->
                        assertThat(finding.address()).isEqualTo("post:" + siteName + "/l-declined"));
    }

    // --- fixture ---

    private ShFolder folder(ShSite site, Instant when) {
        ShFolder root = new ShFolder();
        root.setName("root");
        root.setRootFolder((byte) 1);
        root.setShSite(site);
        root.setPosition((byte) 1);
        root.setDate(when);
        root.setOwner("admin");
        entityManager.persist(root);
        return root;
    }

    private ShPostType postType(String name, byte system, Instant when, String... fields) {
        ShPostType existing = entityManager
                .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                .setParameter("name", name)
                .getResultStream().findFirst().orElse(null);
        if (existing != null) {
            // Instance-wide and shared with every other suite in this JVM. Never mutated here:
            // removing an attribute cascades a REMOVE into the shared widget catalogue (SH289).
            return existing;
        }
        ShPostType type = new ShPostType();
        type.setName(name);
        type.setTitle(name);
        type.setSystem(system);
        type.setDate(when);
        type.setOwner("admin");
        entityManager.persist(type);
        int ordinal = 1;
        for (String field : fields) {
            attr(type, field, ordinal++);
        }
        return type;
    }

    private void attr(ShPostType postType, String name, int ordinal) {
        ShWidget widget = entityManager
                .createQuery("select w from ShWidget w where w.name = :name", ShWidget.class)
                .setParameter("name", ShSystemWidget.TEXT)
                .getResultStream().findFirst().orElseGet(() -> {
                    ShWidget created = new ShWidget();
                    created.setName(ShSystemWidget.TEXT);
                    created.setDescription(ShSystemWidget.TEXT);
                    entityManager.persist(created);
                    return created;
                });
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName(name);
        attr.setLabel(name);
        attr.setOrdinal(ordinal);
        attr.setIsTitle((byte) 0);
        attr.setRequired((byte) 0);
        attr.setShWidget(widget);
        attr.setShPostType(postType);
        entityManager.persist(attr);
    }

    private static String json(String... pairs) {
        StringBuilder out = new StringBuilder("{");
        for (int index = 0; index < pairs.length; index += 2) {
            if (index > 0) {
                out.append(',');
            }
            out.append('"').append(pairs[index]).append("\":\"")
                    .append(pairs[index + 1].replace("\\", "\\\\").replace("\"", "\\\""))
                    .append('"');
        }
        return out.append('}').toString();
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
}
