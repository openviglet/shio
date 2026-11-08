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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.api.site.ShSiteListingService;
import com.viglet.shio.api.site.ShSiteThumbnailStore;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShSystemPostType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * SH1354 — a delivered site has an address of its own: {@code <site>.<service-domain>}.
 *
 * <p>Delivery was a path under {@code /sites/<name>/} on the instance host, which nobody prints on a
 * card. With {@code shio.sites.host-domain} set, the host names the site and the path is the site's
 * own; the renderer's root-relative links already are, the canonical URL says the host, and a host no
 * site claims is a teaching 404.</p>
 *
 * @author Alexandre Oliveira
 */
// The context ShPreviewStateTest and ShImportRendersSourceTest already share, so this spends none of
// the slots ShContextCacheHeadroomTest counts and claims no database of its own. The host domain is
// given to the filter here, not to the context: the delivery route reads only the label it leaves.
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShSiteHostTest {

    private static final String DOMAIN = "sites.test";
    private static final String LAYOUT = "<html><head>"
            + "</head><body><p>{{TEXT}}</p><a id=\"about\" href=\"/about\">about</a></body></html>";

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private PlatformTransactionManager transactionManager;
    private final ShSiteHost siteHost = new ShSiteHost(DOMAIN, "https");
    @Autowired
    private FilterRegistrationBean<ShSiteHostFilter> registration;
    @Autowired
    private ShSiteRepository siteRepository;
    @Autowired
    private ShPostUnifiedRepository postRepository;
    @Autowired
    private ShHistoryRepository historyRepository;
    @Autowired
    private ShSiteThumbnailStore thumbnailStore;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private String furl;

    @BeforeAll
    void seedAPublishedSite() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(new ShSiteHostFilter(siteHost)).build();
        furl = "sh1354-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(furl);
            site.setFurl(furl);
            site.setDate(when);
            site.setOwner("admin");
            site.setPostTypeLayout("{\"Sh1354Page\":\"SH1354_LAYOUT\"}");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName(furl);
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            ShPostType pageType = postType("Sh1354Page", (byte) 0, when);
            page(site, root, layoutType, "SH1354_LAYOUT", "/l",
                    "{\"HTML\":\"" + LAYOUT.replace("\"", "\\\"") + "\"}", when);
            page(site, root, pageType, "Home", "/", "{\"TEXT\":\"hello\"}", when);
            page(site, root, pageType, "About", "/about", "{\"TEXT\":\"about-us\"}", when);
        });
    }

    private static MockHttpServletRequestBuilder at(MockHttpServletRequestBuilder request, String host) {
        return request.with(r -> {
            r.setServerName(host);
            return r;
        });
    }

    @Test
    @DisplayName("SH1354: the host names the site, the path is the site's own, and links stay root-relative")
    void aSiteHostServesTheSiteAtItsRoot() throws Exception {
        mockMvc.perform(at(get("/"), furl + "." + DOMAIN))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("hello")
                        .as("SH1354: the site owns the host's root, so a root-relative link is already its own")
                        .contains("href=\"/about\""));
        mockMvc.perform(at(get("/about"), furl + "." + DOMAIN + ":8443"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("about-us"));
    }

    @Test
    @DisplayName("SH1354: a host no site claims is a teaching 404")
    void anUnclaimedHostIsATeaching404() throws Exception {
        mockMvc.perform(at(get("/"), "nobody-here." + DOMAIN))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("sites-unknown-host").contains("nobody-here"));
    }

    @Test
    @DisplayName("SH1354: the instance host keeps the /sites path form unchanged")
    void theInstanceHostIsUntouched() throws Exception {
        mockMvc.perform(at(get("/sites/" + furl + "/"), "localhost"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("hello"));
    }

    @Test
    @DisplayName("SH1354: the filter runs first, so security sees the re-addressed request")
    void theFilterIsRegisteredFirst() {
        assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    @DisplayName("SH1354: instance routes and writes pass through a site host untouched")
    void instanceRoutesAndWritesPassThrough() throws Exception {
        ShSiteHostFilter filter = new ShSiteHostFilter(new ShSiteHost(DOMAIN, "https"));
        assertThat(seenBy(filter, "GET", "/file_source/x/logo.png")).isEqualTo("/file_source/x/logo.png");
        assertThat(seenBy(filter, "GET", "/api/v2/cda/form/acme")).isEqualTo("/api/v2/cda/form/acme");
        assertThat(seenBy(filter, "POST", "/contact")).isEqualTo("/contact");
        assertThat(seenBy(filter, "GET", "/a%20b/")).isEqualTo("/sites/a%20b/");
        mockMvc.perform(at(post("/anything"), furl + "." + DOMAIN)).andExpect(result ->
                assertThat(result.getResponse().getStatus()).isNotEqualTo(200));
    }

    private static String seenBy(ShSiteHostFilter filter, String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setServerName("acme." + DOMAIN);
        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> seen.set(((HttpServletRequest) req).getRequestURI()));
        return seen.get();
    }

    @Test
    @DisplayName("SH1354: only <label>.<domain> is a site host, and only a DNS label is a label")
    void theLabelRule() {
        assertThat(ShSiteHost.labelOf("acme.sites.test", DOMAIN)).contains("acme");
        assertThat(ShSiteHost.labelOf("ACME.Sites.Test:443", DOMAIN)).contains("acme");
        assertThat(ShSiteHost.labelOf("acme.sites.test.", DOMAIN)).contains("acme");
        assertThat(ShSiteHost.labelOf("a.b.sites.test", DOMAIN)).isEmpty();
        assertThat(ShSiteHost.labelOf("sites.test", DOMAIN)).isEmpty();
        assertThat(ShSiteHost.labelOf("evilsites.test", DOMAIN)).isEmpty();
        assertThat(ShSiteHost.labelOf("acme.sites.test", "")).isEmpty();
        assertThat(new ShSiteHost("", "https").enabled()).isFalse();
    }

    @Test
    @DisplayName("SH1354: a host URL is emitted only where it resolves, and a chosen front end wins")
    void theOriginRule() {
        assertThat(siteHost.originOf(site("acme", null))).contains("https://acme." + DOMAIN);
        assertThat(siteHost.originOf(site("Acme", null))).as("a host is lower-cased, so this cannot round-trip")
                .isEmpty();
        assertThat(siteHost.originOf(site("acme", "https://www.acme.com"))).isEmpty();
        assertThat(new ShSiteHost("", "https").originOf(site("acme", null))).isEmpty();
        assertThat(ShSiteFrontEnd.publishedUrl(site("acme", null), "/about",
                siteHost.originOf(site("acme", null)).orElse(null)))
                .isEqualTo("https://acme." + DOMAIN + "/about");
        assertThat(ShSiteFrontEnd.publishedUrl(site("acme", null), "/about", null))
                .isEqualTo("/sites/acme/default/en-us/about");
    }

    @Test
    @DisplayName("SH1361: the console's site reads carry the published address the server computes")
    void theConsoleReadsCarryThePublishedAddress() throws Exception {
        // The context's host domain is unset, so the single read answers the /sites form...
        mockMvc.perform(get("/api/v2/site/" + furl))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .as("SH1361: the read keeps the site's own fields unwrapped")
                        .contains("\"furl\":\"" + furl + "\"")
                        .contains("\"publishedUrl\":\"/sites/" + furl + "/\""));
        // ...and the listing, given a domain, answers the site's own host: the address the agent's
        // `live` field and the canonical link already say, so the console cannot disagree with them.
        ShSiteListingService listing = new ShSiteListingService(siteRepository, postRepository,
                historyRepository, thumbnailStore, siteHost);
        String published = new TransactionTemplate(transactionManager).execute(status -> listing.list()
                .stream().filter(row -> furl.equals(row.getSite().getFurl())).findFirst().orElseThrow()
                .getPublishedUrl());
        assertThat(published).isEqualTo("https://" + furl + "." + DOMAIN + "/");
        assertThat(siteHost.publishedUrl(site("acme", null), "/about"))
                .isEqualTo("https://acme." + DOMAIN + "/about");
    }

    @Test
    @DisplayName("SH1354: the host form's address carries no format, locale or prefix")
    void theHostAddress() {
        ShSitesAPI.Address address = ShSitesAPI.Address.ofHost("/blog/", "acme");
        assertThat(address.site()).isEqualTo("acme");
        assertThat(address.path()).isEqualTo("/blog/");
        assertThat(address.prefix()).isEmpty();
        assertThat(address.isHeaderForm()).isTrue();
        assertThat(Optional.ofNullable(address.selectedLocale())).isEmpty();
    }

    private static ShSite site(String furl, String url) {
        ShSite site = new ShSite();
        site.setName(furl);
        site.setFurl(furl);
        site.setUrl(url);
        return site;
    }

    private ShPostType postType(String name, byte system, Instant when) {
        ShPostType existing = entityManager
                .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                .setParameter("name", name).getResultStream().findFirst().orElse(null);
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

    private void page(ShSite site, ShFolder folder, ShPostType type, String title, String url,
                      String data, Instant when) {
        ShPostUnified row = new ShPostUnified();
        row.setId(UUID.randomUUID().toString());
        row.setState(ShPostState.PUBLISHED);
        row.setShSite(site);
        row.setShFolder(folder);
        row.setShPostType(type);
        row.setTitle(title);
        row.setFurl(url);
        row.setOwner("admin");
        row.setDate(when);
        row.setData(data);
        entityManager.persist(row);
    }
}
