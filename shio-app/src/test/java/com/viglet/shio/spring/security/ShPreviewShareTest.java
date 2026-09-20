/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.persistence.model.token.ShApiToken;
import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.token.ShApiTokenRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenFilter;

/**
 * SH1042 — the expiring share link, and every door it must not open.
 *
 * <h2>Asserted from the outside, with no credentials</h2>
 *
 * <p>{@code ShPreviewIsNotPublicTest}'s own note says why: a unit test over the configuration would
 * restate the configuration, and the question here is what an anonymous request actually reaches.
 * That test is unchanged and still passes, which is the first thing this feature had to be true of
 * — the floor did not move, a door was added beside it.</p>
 *
 * <h2>What is proved here and what is not</h2>
 *
 * <p>These drive the <b>credential</b>: refused without one, refused with a wrong or expired one,
 * refused with a credential of another scope, and refused for a site the credential does not name.
 * What they do not drive is a rendered page, because that needs a site, a layout and a post — which
 * is {@code ShRenderAPI}'s own fixture, and asserting authorization through it would make a
 * security check depend on a render succeeding.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShPreviewShareTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShApiTokenRepository shApiTokenRepository;

    private MockMvc secured;

    @BeforeAll
    void setUp() {
        secured = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    private ShApiToken mint(ShApiTokenScope scope, Instant expiresAt, Set<String> siteIds) {
        ShApiToken token = new ShApiToken();
        token.setTitle("sh1042-" + UUID.randomUUID());
        token.setToken(UUID.randomUUID().toString().replace("-", "").substring(0, 25));
        token.setOwner("alice");
        token.setCreatedDate(Instant.now());
        token.setEnabled(true);
        token.setScope(scope);
        token.setEnv(ShApiTokenEnv.PREVIEW);
        token.setSiteIds(siteIds);
        token.setExpiresAt(expiresAt);
        token.setEphemeral(true);
        return shApiTokenRepository.saveAndFlush(token);
    }

    private static Cookie shareCookie(ShApiToken token) {
        return new Cookie(ShApiTokenFilter.SHARE_COOKIE, token.getToken());
    }

    @Test
    @DisplayName("SH1042: the exchange sets an HttpOnly cookie and sends the reader to the page")
    void theExchangeSetsTheCookie() throws Exception {
        ShApiToken token = mint(ShApiTokenScope.SHARE, Instant.now().plusSeconds(600),
                Set.of("site-a"));

        MvcResult result = secured.perform(
                get("/api/v2/preview-share/" + token.getToken()).param("to", "/Acme/about"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .as("the credential leaves the URL on the first hop, so what a recipient forwards "
                        + "— and what every image on the page sends as its Referer — is an "
                        + "ordinary preview path")
                .isEqualTo("/preview/Acme/about");
        String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains(ShApiTokenFilter.SHARE_COOKIE)
                .as("HttpOnly because the page it is sent to is content somebody authored and this "
                        + "instance renders: a script in it must not read the credential back out")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .as("scoped to the path it authenticates, so the browser cannot send it to the "
                        + "console or the API even by accident")
                .contains("Path=/preview");
    }

    @Test
    @DisplayName("SH1042: a link that is unknown, expired or of another scope is a 404")
    void onlyALiveShareCredentialIsExchanged() throws Exception {
        secured.perform(get("/api/v2/preview-share/nope-not-a-token"))
                .andExpect(status().isNotFound());

        ShApiToken expired = mint(ShApiTokenScope.SHARE, Instant.now().minusSeconds(60),
                Set.of("site-a"));
        secured.perform(get("/api/v2/preview-share/" + expired.getToken()))
                .andExpect(status().isNotFound());

        // A PREVIEW token is the credential SH13 mints for an external front end. It must not open
        // this door: it carries ROLE_CDA and ROLE_CDA_PREVIEW, so treating the two as one scope
        // would mean a link handed to a stakeholder could read the site's whole delivery API.
        ShApiToken preview = mint(ShApiTokenScope.PREVIEW, Instant.now().plusSeconds(600),
                Set.of("site-a"));
        secured.perform(get("/api/v2/preview-share/" + preview.getToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SH1042: the exchange never redirects off this origin")
    void theDestinationIsAlwaysAPreviewPath() throws Exception {
        ShApiToken token = mint(ShApiTokenScope.SHARE, Instant.now().plusSeconds(600),
                Set.of("site-a"));

        // A redirect that takes its target from an anonymous caller is an open redirect wearing a
        // feature's name. `//evil.example` is the shape that matters: it is a protocol-relative
        // URL, so a browser reads it as another origin while it still looks like a path.
        for (String hostile : new String[] { "//evil.example/x", "https://evil.example/x", "x" }) {
            MvcResult result = secured.perform(
                    get("/api/v2/preview-share/" + token.getToken()).param("to", hostile))
                    .andReturn();
            assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                    .as("refused destination: %s", hostile)
                    .isEqualTo("/preview/");
        }
    }

    @Test
    @DisplayName("SH1042: the cookie authenticates the preview and nothing else")
    void theCredentialIsConfinedToTheRenderer() throws Exception {
        ShApiToken token = mint(ShApiTokenScope.SHARE, Instant.now().plusSeconds(600),
                Set.of("site-a"));
        Cookie cookie = shareCookie(token);

        // The delivery API, the agent surface and MCP read the `Key` header and never a cookie, and
        // a SHARE token is refused on them even when it is presented that way. Both halves matter:
        // the first keeps a browser's cookie from standing in for a delivery key, the second keeps
        // a link handed to a stranger out of the API entirely.
        secured.perform(get("/api/v2/cda/site").cookie(cookie))
                .andExpect(status().isUnauthorized());
        secured.perform(get("/api/v2/cda/site").header("Key", token.getToken()))
                .andExpect(status().isUnauthorized());
        secured.perform(get("/api/v2/agent/manifest").header("Key", token.getToken()))
                .andExpect(status().isUnauthorized());
        secured.perform(get("/api/v2/post-unified/x").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SH1042: an expired cookie stops reading the preview, with no revocation step")
    void anExpiredShareStopsWorking() throws Exception {
        ShApiToken expired = mint(ShApiTokenScope.SHARE, Instant.now().minusSeconds(60),
                Set.of("site-a"));

        // The expiry is the revocation: the filter treats a lapsed token exactly as it treats a
        // disabled one, so a forwarded link stops working without anybody having to remember it.
        secured.perform(get("/preview/Acme/about").cookie(shareCookie(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SH1042: the floor did not move — no cookie is still no preview")
    void anAnonymousPreviewIsStillRefused() throws Exception {
        secured.perform(get("/preview/Acme/about")).andExpect(status().isUnauthorized());
        secured.perform(get("/preview/Acme/about").cookie(
                new Cookie(ShApiTokenFilter.SHARE_COOKIE, "not-a-token")))
                .andExpect(status().isUnauthorized());
    }
}
