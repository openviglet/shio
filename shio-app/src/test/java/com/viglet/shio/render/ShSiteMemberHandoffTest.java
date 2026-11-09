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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.persistence.model.auth.ShGroup;
import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.spring.security.auth.ShApiTokenFilter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.Cookie;

/**
 * SH1375 — a visitor who signs in from a site host lands back on the site host, signed in there and
 * nowhere else.
 *
 * <p>Driven through the security chain with {@link ShSiteHostFilter} in front of it, the way a request
 * on {@code <furl>.sites.test} meets the instance: the members-only page, the login's way back, the
 * handoff on the instance, the exchange on the site host, and then what the cookie it set can and
 * cannot open.</p>
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShSiteMemberHandoffTest {

    static final String DOMAIN = "sites.test";

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ShContentAclService shContentAclService;
    @Autowired
    private ShSiteHost siteHost;
    /** Set and restored rather than declared as a property, which would cost a context (SH1063). */
    @Autowired
    private ShSitesProperties sitesProperties;
    private String domainBefore;
    @Autowired
    private com.viglet.shio.property.ShAppProperties appProperties;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private String label;
    private String otherLabel;
    private String member;

    @BeforeAll
    void seedTwoSitesWithAGatedPage() {
        domainBefore = sitesProperties.getHostDomain();
        sitesProperties.setHostDomain(DOMAIN);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(new ShSiteHostFilter(siteHost))
                .apply(SecurityMockMvcConfigurers.springSecurity()).build();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        label = "sh1375-" + suffix;
        otherLabel = "sh1375-other-" + suffix;
        member = "sh1375-member-" + suffix;
        String group = "Sh1375Members-" + suffix;
        String[] gated = new String[2];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShGroup members = new ShGroup();
            members.setName(group);
            entityManager.persist(members);
            ShUser user = new ShUser();
            user.setUsername(member);
            user.setEmail(member + "@example.invalid");
            user.setPassword("not-a-real-hash");
            user.setEnabled(1);
            user.getShGroups().add(members);
            entityManager.persist(user);
            // SH1395 — a second member, disabled by one test and so kept apart from the others.
            ShUser second = new ShUser();
            second.setUsername(member + "-b");
            second.setEmail(member + "-b@example.invalid");
            second.setPassword("not-a-real-hash");
            second.setEnabled(1);
            second.getShGroups().add(members);
            entityManager.persist(second);
            gated[0] = site(label, when);
            gated[1] = site(otherLabel, when);
        });
        shContentAclService.replaceGate(gated[0], true, List.of(group));
        shContentAclService.replaceGate(gated[1], true, List.of(group));
    }

    @org.junit.jupiter.api.AfterAll
    void restoreTheDomain() {
        sitesProperties.setHostDomain(domainBefore);
    }

    @Test
    @DisplayName("SH1375: signing in from a site host lands back on the site host, signed in there")
    void aSignInComesBackToTheSiteHost() throws Exception {
        Handoff handoff = startOn(label);
        assertThat(handoff.returnUrl())
                .as("SH1375: the login's way back is the instance's handoff, a path, so SH1299's rule stands")
                .startsWith(ShSiteMemberAPI.HANDOFF + "?to=")
                .contains("to=" + java.net.URLEncoder.encode("https://" + host(label) + "/lounge?from=card",
                        StandardCharsets.UTF_8));

        MockHttpServletResponse handed = mockMvc.perform(at(handoff.returnUrl()).with(user(member)))
                .andExpect(status().isFound()).andReturn().getResponse();
        URI exchange = URI.create(handed.getRedirectedUrl());
        assertThat(exchange.getHost()).as("SH1375: the code goes to the site host it came from").isEqualTo(host(label));
        assertThat(exchange.getPath()).isEqualTo(ShSiteMemberAPI.EXCHANGE);

        MockHttpServletResponse exchanged = mockMvc.perform(onHost(label, exchange.getPath() + "?" + exchange.getRawQuery())
                        .cookie(handoff.state()))
                .andExpect(status().isFound()).andReturn().getResponse();
        assertThat(exchanged.getRedirectedUrl()).isEqualTo("/lounge?from=card");
        Cookie signedIn = exchanged.getCookie(ShApiTokenFilter.MEMBER_COOKIE);
        assertThat(signedIn).as("SH1375: the exchange signs the browser in on the site host").isNotNull();
        assertThat(signedIn.isHttpOnly()).isTrue();
        assertThat(signedIn.getDomain()).as("SH1375: host-only, so no other host is ever sent it").isNull();

        mockMvc.perform(onHost(label, "/lounge").cookie(signedIn))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .as("SH1375: the member reads the members-only page on the site host")
                        .contains("members lounge"));

        // The same code a second time is spent: the code that reached a log cannot be replayed.
        mockMvc.perform(onHost(label, exchange.getPath() + "?" + exchange.getRawQuery()).cookie(handoff.state()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SH1375: the site-host sign-in opens that site's delivery and nothing else")
    void theSignInIsScopedToItsHost() throws Exception {
        Cookie signedIn = signIn(label);
        mockMvc.perform(onHost(otherLabel, "/lounge").cookie(signedIn))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .as("SH1375: a sign-in on one site host is not one on another")
                        .doesNotContain("members lounge"));
        mockMvc.perform(get("/sites/" + label + "/default/en-us/lounge").cookie(signedIn))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .as("SH1375: nor on the instance's own /sites address")
                        .doesNotContain("members lounge"));
        mockMvc.perform(onHost(label, "/api/v2/site").cookie(signedIn))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v2/site").cookie(signedIn))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SH1375: a code is spent only by the browser that started the sign-in")
    void aCodeNeedsTheBrowserThatStarted() throws Exception {
        Handoff handoff = startOn(label);
        URI exchange = URI.create(mockMvc.perform(at(handoff.returnUrl()).with(user(member)))
                .andReturn().getResponse().getRedirectedUrl());
        // Somebody mailing their own code to a victim would sign the victim in as themselves.
        mockMvc.perform(onHost(label, exchange.getPath() + "?" + exchange.getRawQuery()))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResponse().getCookie(ShApiTokenFilter.MEMBER_COOKIE)).isNull());
    }

    @Test
    @DisplayName("SH1375: the handoff sends a code only to a site host this instance serves")
    void theHandoffIsNoOpenRedirect() throws Exception {
        String state = ShSiteMemberAPI.random();
        for (String to : List.of("https://evil.example/lounge", "https://" + label + ".evil.example/",
                "https://" + label + "." + DOMAIN + ".evil.example/", "https://nosuchsite." + DOMAIN + "/",
                "javascript:alert(1)", "//" + host(label) + "/", "https://x@" + host(label) + "/")) {
            mockMvc.perform(get(ShSiteMemberAPI.HANDOFF).param("to", to).param("state", state).with(user(member)))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                            .as("SH1375: %s", to).isNull());
        }
        assertThat(ShSiteMemberAPI.sitePath("//evil.example")).isEqualTo("/");
        assertThat(ShSiteMemberAPI.sitePath("https://evil.example")).isEqualTo("/");
        assertThat(ShSiteMemberAPI.sitePath("/\\evil.example")).isEqualTo("/");
    }

    @Test
    @DisplayName("SH1375: a person nobody signed in is sent to the login, with the handoff as the way back")
    void theHandoffAsksForTheLoginFirst() throws Exception {
        Handoff handoff = startOn(label);
        String location = mockMvc.perform(at(handoff.returnUrl()))
                .andExpect(status().isFound()).andReturn().getResponse().getRedirectedUrl();
        assertThat(location).startsWith("/login?returnUrl=");
        assertThat(URLDecoder.decode(location.substring("/login?returnUrl=".length()), StandardCharsets.UTF_8))
                .startsWith(ShSiteMemberAPI.HANDOFF + "?");
    }


    // --- SH1395: a site-host sign-in ends ---

    private void readsTheLounge(Cookie signedIn, boolean expected, String why) throws Exception {
        mockMvc.perform(onHost(label, "/lounge").cookie(signedIn))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString().contains("members lounge"))
                        .as(why).isEqualTo(expected));
    }

    @Test
    @DisplayName("SH1395: signing out on the site host ends that sign-in, and a fresh one still works")
    void signingOutOnTheSiteHostEndsIt() throws Exception {
        Cookie signedIn = signIn(label);
        readsTheLounge(signedIn, true, "SH1395: the sign-in works before the sign-out");
        MockHttpServletResponse out = mockMvc.perform(onHost(label, ShSiteMemberAPI.SIGN_OUT + "?to=%2Flounge")
                        .cookie(signedIn))
                .andExpect(status().isFound()).andReturn().getResponse();
        assertThat(out.getRedirectedUrl()).isEqualTo("/lounge");
        assertThat(out.getCookie(ShApiTokenFilter.MEMBER_COOKIE).getMaxAge())
                .as("SH1395: the browser is told to drop the cookie").isZero();
        readsTheLounge(signedIn, false,
                "SH1395: the same cookie, kept by hand, opens nothing once its sign-in has ended");
        readsTheLounge(signIn(label), true, "SH1395: and signing in again works");
    }

    @Test
    @DisplayName("SH1395: signing out of Shio ends the person's site-host sign-ins")
    void signingOutOfShioEndsThem() throws Exception {
        Cookie signedIn = signIn(label);
        readsTheLounge(signedIn, true, "SH1395: the sign-in works before the console sign-out");
        // The console signs out with a GET to /logout (ShSecurityConfig's logout matcher).
        mockMvc.perform(get("/logout").with(user(member)));
        readsTheLounge(signedIn, false,
                "SH1395: a shared computer would keep the member's pages open after they signed out of Shio");
    }

    @Test
    @DisplayName("SH1395: a sign-in ends with its person, once disabled")
    void aDisabledPersonsSignInEnds() throws Exception {
        String second = member + "-b";
        Cookie signedIn = signIn(label, second);
        readsTheLounge(signedIn, true, "SH1395: the second member reads the lounge while enabled");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> entityManager
                .createQuery("select u from ShUser u where u.username = :name", ShUser.class)
                .setParameter("name", second).getSingleResult().setEnabled(0));
        readsTheLounge(signedIn, false, "SH1395: a disabled person's site-host sign-in reads as a visitor");
    }

    /** The members-only page on the site host, turned away: where it sends the visitor, and its state. */
    private Handoff startOn(String site) throws Exception {
        MockHttpServletResponse turned = mockMvc.perform(onHost(site, "/lounge?from=card"))
                .andExpect(status().isFound()).andReturn().getResponse();
        String prefix = instanceUrl() + "/login?returnUrl=";
        assertThat(turned.getRedirectedUrl()).startsWith(prefix);
        Cookie state = turned.getCookie(ShSiteMemberAPI.STATE_COOKIE);
        assertThat(state).as("SH1375: the site host keeps its half of the binding in a cookie").isNotNull();
        String returnUrl = URLDecoder.decode(turned.getRedirectedUrl().substring(prefix.length()), StandardCharsets.UTF_8);
        assertThat(returnUrl).endsWith("&state=" + state.getValue());
        return new Handoff(returnUrl, state);
    }

    private Cookie signIn(String site) throws Exception {
        return signIn(site, member);
    }

    private Cookie signIn(String site, String who) throws Exception {
        Handoff handoff = startOn(site);
        URI exchange = URI.create(mockMvc.perform(at(handoff.returnUrl()).with(user(who)))
                .andReturn().getResponse().getRedirectedUrl());
        return mockMvc.perform(onHost(site, exchange.getPath() + "?" + exchange.getRawQuery()).cookie(handoff.state()))
                .andReturn().getResponse().getCookie(ShApiTokenFilter.MEMBER_COOKIE);
    }

    private record Handoff(String returnUrl, Cookie state) {
    }

    private static String host(String site) {
        return site + "." + DOMAIN;
    }

    /** A URI, not a template: the paths here are already encoded, and a template would encode them twice. */
    private static MockHttpServletRequestBuilder at(String path) {
        return get(URI.create(path));
    }

    private static MockHttpServletRequestBuilder onHost(String site, String path) {
        return at(path).with(request -> {
            request.setServerName(host(site));
            return request;
        });
    }

    private String instanceUrl() {
        String url = appProperties.getUrl().trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** A site sending members to the login, with a public home and a gated page; the gated folder's id. */
    private String site(String name, Instant when) {
        ShSite site = new ShSite();
        site.setName(name);
        site.setFurl(name);
        site.setDate(when);
        site.setOwner("admin");
        site.setPostTypeLayout("{\"Sh1375Page\":\"SH1375_LAYOUT\"}");
        site.setMembersLogin(true);
        entityManager.persist(site);
        ShFolder root = folder(site, null, name, when);
        ShFolder members = folder(null, root, "members", when);
        ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
        ShPostType pageType = postType("Sh1375Page", (byte) 0, when);
        post(site, root, layoutType, "SH1375_LAYOUT", "/l", "{\"HTML\":\"<html><body>{{TEXT}}</body></html>\"}", when);
        post(site, root, pageType, "Home", "/", "{\"TEXT\":\"welcome\"}", when);
        post(site, members, pageType, "Lounge", "/lounge", "{\"TEXT\":\"members lounge\"}", when);
        return members.getId();
    }

    private ShFolder folder(ShSite site, ShFolder parent, String name, Instant when) {
        ShFolder folder = new ShFolder();
        folder.setName(name);
        folder.setRootFolder(parent == null ? (byte) 1 : (byte) 0);
        folder.setShSite(site);
        folder.setParentFolder(parent);
        folder.setDate(when);
        folder.setOwner("admin");
        entityManager.persist(folder);
        return folder;
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

    private void post(ShSite site, ShFolder folder, ShPostType type, String title, String furl,
                      String data, Instant when) {
        ShPostUnified row = new ShPostUnified();
        row.setId(UUID.randomUUID().toString());
        row.setState(ShPostState.PUBLISHED);
        row.setShSite(site);
        row.setShFolder(folder);
        row.setShPostType(type);
        row.setTitle(title);
        row.setFurl(furl);
        row.setOwner("admin");
        row.setDate(when);
        row.setData(data);
        entityManager.persist(row);
    }
}
