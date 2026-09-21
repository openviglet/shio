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

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.api.site.ShSiteLocaleService;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import tools.jackson.databind.ObjectMapper;

/**
 * SH337 — the two URLs a template can ask for, and why there are two.
 *
 * <p>A canonical tag, an {@code og:url} and a sitemap entry all need the page's address at the
 * site's front end. {@code site.url} could not answer: it is the customer's deployed front end
 * and it is <b>blank</b> on a site an agent just built (SH170 creates one empty, SH184 is the
 * task that stopped treating that as a dead end). So the model carries the resolved pair
 * beside the raw one.</p>
 *
 * <p>No Spring context: {@link ShRenderModel} needs an {@code ObjectMapper} and the incident
 * log, and the log has a no-argument constructor for exactly this (a test of a collaborator
 * that would otherwise stand up a repository). SH317 counts contexts, so one that buys
 * nothing is one not to start.</p>
 *
 * @author Alexandre Oliveira
 */
class ShRenderModelUrlsTest {

    // SH1056 — the locale service reads the site's own column here and never the content, so the
    // two repositories it would need for a DERIVED policy are genuinely unused on this path. A
    // mock would assert that; a null says it, and fails loudly if the path ever starts deriving.
    private final ShRenderModel renderModel = new ShRenderModel(new ObjectMapper(),
            new ShAgentIncidentLog(),
            new ShSiteLocaleService(null, null, new ShAgentIncidentLog(), new ObjectMapper()));

    @Test
    @DisplayName("a deployed site resolves to its own origin, and the trailing slash is gone")
    void aDeployedSiteResolvesToItsOrigin() {
        Map<String, Object> model = modelOf("Demo", "https://demo.example.com/", "/contato");

        assertThat(site(model)).containsEntry("baseUrl", "https://demo.example.com");
        assertThat(post(model)).containsEntry("canonical", "https://demo.example.com/contato");
    }

    @Test
    @DisplayName("a site with no front end resolves to the preview route, which is where it really is")
    void aSiteWithNoFrontEndResolvesToThePreview() {
        Map<String, Object> model = modelOf("Demo", null, "/contato");

        assertThat(site(model)).containsEntry("baseUrl", "/preview/Demo");
        assertThat(post(model)).containsEntry("canonical", "/preview/Demo/contato");
    }

    /**
     * The reason both pairs exist rather than one replacing the other: {@code site.url} is the
     * answer to "has anybody deployed this?", and a fallback written into it would destroy that
     * question — which is the argument {@link ShSiteFrontEnd} makes for resolving rather than
     * storing, held here at the layer that could have quietly undone it.
     */
    @Test
    @DisplayName("the raw fields keep meaning what they meant: url stays blank, and post.url stays the furl")
    void theRawFieldsAreNotOverwritten() {
        Map<String, Object> model = modelOf("Demo", null, "/contato");

        assertThat(site(model).get("url")).isNull();
        assertThat(post(model)).containsEntry("url", "/contato");
    }

    /**
     * The hazard that makes this a composed value rather than
     * {@code {{site.baseUrl}}{{post.url}}} in every template: a furl without a leading slash
     * concatenates into {@code /preview/Democontato}, which is a 404 nobody would suspect of
     * being a string bug.
     */
    @Test
    @DisplayName("a furl with no leading slash is still composed into a real path")
    void aFurlWithoutALeadingSlashStillComposes() {
        assertThat(post(modelOf("Demo", null, "contato")))
                .containsEntry("canonical", "/preview/Demo/contato");
    }

    @Test
    @DisplayName("a post with no site at all yields no canonical rather than a broken one")
    void aPostWithNoSiteYieldsNoCanonical() {
        ShPostUnified post = new ShPostUnified();
        post.setFurl("/contato");

        assertThat(post(renderModel.of(post, null))).containsEntry("canonical", null);
    }

    private Map<String, Object> modelOf(String siteName, String siteUrl, String furl) {
        ShSite site = new ShSite();
        site.setId("11111111-2222-3333-4444-555555555555");
        site.setName(siteName);
        site.setUrl(siteUrl);

        ShPostUnified post = new ShPostUnified();
        post.setId("99999999-8888-7777-6666-555555555555");
        post.setTitle("Contato");
        post.setFurl(furl);
        post.setShSite(site);

        return renderModel.of(post, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> site(Map<String, Object> model) {
        return (Map<String, Object>) model.get("site");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(Map<String, Object> model) {
        return (Map<String, Object>) model.get("post");
    }
}
