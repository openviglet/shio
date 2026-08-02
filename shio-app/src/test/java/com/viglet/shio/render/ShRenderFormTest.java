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
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH247 — {@code form}, the seventh and last of {@code shObject.js}' capabilities.
 *
 * <h2>What was actually blocking, and what the artefact said</h2>
 *
 * <p>This capability was filed rather than built for a whole block, on the argument that "a form
 * needs somewhere for a submission to go — storage, spam handling, notification, retention — which
 * is a subsystem, not a helper". <b>The engine being replaced had none of that.</b> FSP's
 * {@code ShFormComponent} is two files and neither stores anything, and its
 * {@code ShFormConfiguration} carries exactly four settings: {@code method}, {@code pageLayout},
 * {@code createPost} and {@code folder}. So 2018 had answered the question, and its answer was
 * that a submission becomes a <b>post in a named folder</b> — no subsystem, because the CMS
 * already is one. The premise that blocked the task was contradicted by the artefact it claimed
 * to be replacing, which is SH274's lesson arriving from a different direction.</p>
 *
 * <h2>What this ships, and the line it does not cross</h2>
 *
 * <p>Rendering only. The helper yields a post-type's declared fields and the author writes the
 * {@code <form>}, the {@code action} and the inputs; Shio never receives a submission. Two
 * reasons, and neither is caution for its own sake. The receiving half is a <b>public,
 * unauthenticated endpoint that writes content</b>, which is a security decision rather than a
 * rendering one. And the renderer's declared scope is <b>preview-grade</b> — production delivery
 * is Next.js over the CDA (P10) — so a preview needs the form to <em>look</em> right, not to
 * accept a stranger's data.</p>
 *
 * <p>The markup is the author's for SH183's reason: HTML is content. A helper that emitted
 * {@code <input class="form-control">} from Java would be the one place in this engine where
 * markup is a Java decision, un-themeable by the content that owns every other tag on the page.</p>
 *
 * <p>Its own database, for {@link ShPageRenderTest}'s reason (SH246): the system post-types it
 * creates are instance-wide and the SH164/SH239 benchmarks measure documents whose size depends
 * on how many exist.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh247form;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShRenderFormTest {

    private static final String LAYOUT_TITLE = "SH247_FORM_LAYOUT";

    /**
     * The author's markup, which is the point: the {@code <form>}, the {@code action} and every
     * input are written here, and the helper supplies only what the post-type declares.
     */
    private static final String LAYOUT_HTML = """
            <html><body>
            <form action="https://forms.example.com/contact" method="post">
            {{#form type="%s"}}<label for="{{name}}">{{label}}</label>\
            <input type="{{input}}" name="{{name}}" data-widget="{{widget}}"\
            {{#if required}} required{{/if}}>\
            {{else}}<p id="noform">no such form</p>{{/form}}
            </form>
            <div id="unknown">{{#form type="NoSuchType"}}<b>x</b>{{else}}<i>absent</i>{{/form}}</div>
            <div id="nofields">{{#form type="%s"}}<b>y</b>{{else}}<i>empty</i>{{/form}}</div>
            </body></html>""";

    @Autowired
    private ShPageRenderService shPageRenderService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String pageType;
    private String contactType;
    private String emptyType;

    @BeforeAll
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        siteName = "sh247f-" + suffix;
        pageType = "Sh247FPage" + suffix;
        contactType = "Sh247FContact" + suffix;
        emptyType = "Sh247FEmpty" + suffix;

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");

            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            site.setPostTypeLayout("{\"" + pageType + "\":\"" + LAYOUT_TITLE + "\"}");
            entityManager.persist(site);

            ShFolder root = folder(site, when);

            ShPostType page = postType(pageType, (byte) 0, when);
            postType(emptyType, (byte) 0, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);

            ShPostType contact = postType(contactType, (byte) 0, when);
            // Declared out of ordinal order on purpose: the helper sorts, and a fixture that
            // inserted them already sorted could not tell a sort from an accident of insertion.
            attr(contact, "MESSAGE", "Your message", ShSystemWidget.TEXT_AREA, 3, false);
            attr(contact, "NAME", "Your name", ShSystemWidget.TEXT, 1, true);
            attr(contact, "EMAIL", "Email", ShSystemWidget.TEXT, 2, true);

            post(site, root, layoutType, LAYOUT_TITLE, "/layout",
                    "{\"HTML\":" + quote(LAYOUT_HTML.formatted(contactType, emptyType)) + "}", when);
            post(site, root, page, "Contact us", "/", "{}", when);
        });
    }

    @Test
    @DisplayName("a post-type's fields render as the author's inputs, in ordinal order")
    void aFormRendersItsPostTypesFields() {
        String html = shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html();

        assertThat(html)
                .as("the label and the field name, which is everything a form needs to post")
                .contains("<label for=\"NAME\">Your name</label>")
                .contains("name=\"NAME\"")
                .contains("<label for=\"MESSAGE\">Your message</label>");
        assertThat(html.indexOf("NAME"))
                .as("""
                        ordinal, not insertion: MESSAGE was declared first and is ordinal 3, so a \
                        helper that yielded the set's own order would put it at the top. A form \
                        whose fields reorder between renders is one no author can lay out, and it \
                        would move SH101's digest for a page nobody edited.""")
                .isLessThan(html.indexOf("MESSAGE"));
        assertThat(html.indexOf("EMAIL")).isLessThan(html.indexOf("MESSAGE"));
    }

    @Test
    @DisplayName("the widget is Shio's vocabulary and `input` is the browser's, and both are given")
    void bothVocabulariesAreOffered() {
        String html = shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html();

        assertThat(html)
                .as("""
                        a template that only had `widget` would hard-code this catalogue to emit \
                        the right tag; one that only had `input` could not tell a Date from a Text \
                        it happens to render the same way. Both, so neither guess is forced.""")
                .contains("type=\"text\" name=\"NAME\" data-widget=\"" + ShSystemWidget.TEXT + "\"")
                .contains("type=\"textarea\" name=\"MESSAGE\" data-widget=\""
                        + ShSystemWidget.TEXT_AREA + "\"");
        assertThat(html)
                .as("`required` is a byte in the model and a truth to a template")
                .contains("name=\"NAME\" data-widget=\"" + ShSystemWidget.TEXT + "\" required")
                .doesNotContain("name=\"MESSAGE\" data-widget=\""
                        + ShSystemWidget.TEXT_AREA + "\" required");
    }

    @Test
    @DisplayName("an unknown post-type renders {{else}} rather than failing the page")
    void anUnknownTypeRendersTheInverse() {
        String html = shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html();

        // getRelation's rule, for getRelation's reason: an unknown post-type is already an SH99
        // finding with an address and a fix, so failing the page would put one defect in two
        // reports that can disagree. `{{else}}` means "there is no form here", which is what a
        // visitor should see and what an author will notice.
        assertThat(html).contains("<div id=\"unknown\"><i>absent</i></div>");
        assertThat(html)
                .as("a declared type with no fields is the same answer — an empty <form> is not "
                        + "a form, and the author's {{else}} is the place to say so")
                .contains("<div id=\"nofields\"><i>empty</i></div>");
    }

    @Test
    @DisplayName("the author owns the form element; the helper never emits markup")
    void theHelperEmitsNoMarkupOfItsOwn() {
        String html = shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html();

        assertThat(html)
                .as("""
                        SH183's rule: HTML is content. The action is the author's and points \
                        somewhere else entirely — Shio renders the form and never receives the \
                        submission, which is the SH247 decision this test is the record of.""")
                .contains("<form action=\"https://forms.example.com/contact\" method=\"post\">");
        assertThat(html)
                .as("""
                        nothing the helper produced is a tag. If this ever fails because the \
                        helper started emitting a wrapper, the capability has quietly become a \
                        Java opinion about markup — un-themeable, and the one thing this engine \
                        was built not to be.""")
                .doesNotContain("form-control")
                .doesNotContain("<fieldset");
    }

    // ── fixture ─────────────────────────────────────────────────────────────────────

    private ShFolder folder(ShSite site, Instant when) {
        ShFolder folder = new ShFolder();
        folder.setName("root");
        folder.setFurl("root");
        folder.setRootFolder((byte) 1);
        folder.setShSite(site);
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

    /**
     * One declared field on a post-type.
     *
     * <p>The widget is <b>looked up</b>, never created: {@code ShPostTypeAttr.shWidget} cascades
     * {@code ALL}, so a widget this test owned would be a row in the shared catalogue with a
     * lifetime tied to a fixture (SH289).</p>
     */
    private void attr(ShPostType type, String name, String label, String widgetName,
                      int ordinal, boolean required) {
        ShWidget widget = entityManager
                .createQuery("select w from ShWidget w where w.name = :name", ShWidget.class)
                .setParameter("name", widgetName)
                .getResultStream().findFirst().orElseThrow(
                        () -> new IllegalStateException("widget '" + widgetName + "' is not seeded"));
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName(name);
        attr.setLabel(label);
        attr.setShWidget(widget);
        attr.setOrdinal(ordinal);
        attr.setRequired((byte) (required ? 1 : 0));
        attr.setShPostType(type);
        entityManager.persist(attr);
        type.getShPostTypeAttrs().add(attr);
    }

    private void post(ShSite site, ShFolder folder, ShPostType type, String title, String furl,
                      String data, Instant when) {
        ShPostUnified post = new ShPostUnified();
        post.setTitle(title);
        post.setFurl(furl);
        post.setData(data);
        post.setShPostType(type);
        post.setShFolder(folder);
        post.setShSite(site);
        post.setState(ShPostState.DRAFT);
        post.setDate(when);
        post.setOwner("admin");
        entityManager.persist(post);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
    }
}
