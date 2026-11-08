/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

import com.viglet.shio.property.ShDeviceAuthProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1360 — a native app signs in through the instance's own login and gets a device token without a
 * pasted secret.
 *
 * <p>Through the real filter chain, as SH1351's test is, because the two routes being reachable
 * without a session is half of what is under test. The assertion the design names: a code is single
 * use, expires, and fails with the wrong verifier, and no secret ever rides in a URL.</p>
 *
 * @author Alexandre Oliveira
 */
// SH1351's context, so this spends no slot ShContextCacheHeadroomTest counts. The allow-list is set
// on the properties bean for this class and restored after it.
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShDeviceAuthCodeTest {

    private static final String AUTHORIZE = "/api/v2/device-token/authorize";
    private static final String EXCHANGE = "/api/v2/device-token/exchange";
    private static final String REDIRECT = "shio-sh1360://signed-in";

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShDeviceAuthProperties properties;

    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;
    private List<String> savedUris;
    private Duration savedTtl;

    @BeforeAll
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        savedUris = properties.getRedirectUris();
        savedTtl = properties.getCodeTtl();
        properties.setRedirectUris(new ArrayList<>(List.of(REDIRECT)));
    }

    @AfterAll
    void tearDown() {
        properties.setRedirectUris(savedUris);
        properties.setCodeTtl(savedTtl);
    }

    private static String verifier(char fill) {
        return String.valueOf(fill).repeat(50);
    }

    private static String challenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static MockHttpServletRequestBuilder authorize(String verifier, String redirect) throws Exception {
        return get(AUTHORIZE).param("redirect_uri", redirect).param("code_challenge", challenge(verifier))
                .param("code_challenge_method", "S256").param("device_name", "Pixel 9").param("state", "xyz");
    }

    /** Signs in and follows the redirect back to the app: the code it carries. */
    private String codeFor(String verifier) throws Exception {
        String location = mvc.perform(authorize(verifier, REDIRECT).with(user("admin")))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).startsWith(REDIRECT + "?");
        assertThat(location).as("SH1360: the redirect carries a code and the state, never a secret")
                .doesNotContain(ShDeviceTokenFilter.PREFIX).contains("state=xyz");
        return UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");
    }

    private int exchange(String code, String verifier, StringBuilder token) throws Exception {
        var result = mvc.perform(post(EXCHANGE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"codeVerifier\":\"" + verifier + "\"}")).andReturn();
        if (token != null && result.getResponse().getStatus() == 201) {
            JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
            token.append(body.get("token").asString());
        }
        return result.getResponse().getStatus();
    }

    @Test
    @DisplayName("SH1360: someone not signed in is sent to the instance's own login, with the way back")
    void anUnsignedBrowserGoesToTheLogin() throws Exception {
        String location = mvc.perform(authorize(verifier('a'), REDIRECT))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).startsWith("/login?returnUrl=").contains("authorize");
    }

    @Test
    @DisplayName("SH1360: a code exchanges once for a device token that works, and never twice")
    void aCodeIsSingleUse() throws Exception {
        String verifier = verifier('b');
        String code = codeFor(verifier);
        StringBuilder token = new StringBuilder();

        assertThat(exchange(code, verifier, token)).isEqualTo(201);
        assertThat(token.toString()).startsWith(ShDeviceTokenFilter.PREFIX);
        mvc.perform(get("/api/v2/device-token").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(exchange(code, verifier, null)).as("SH1360: a code is good for one exchange").isEqualTo(400);
    }

    @Test
    @DisplayName("SH1360: a wrong verifier fails and burns the code, so a leaked code cannot be guessed at")
    void aWrongVerifierBurnsTheCode() throws Exception {
        String verifier = verifier('c');
        String code = codeFor(verifier);

        assertThat(exchange(code, verifier('d'), null)).isEqualTo(400);
        assertThat(exchange(code, verifier, null))
                .as("SH1360: the right verifier after a wrong one is too late").isEqualTo(400);
    }

    @Test
    @DisplayName("SH1360: an expired code cannot be exchanged")
    void anExpiredCodeFails() throws Exception {
        properties.setCodeTtl(Duration.ZERO);
        try {
            String verifier = verifier('e');
            String code = codeFor(verifier);
            assertThat(exchange(code, verifier, null)).isEqualTo(400);
        } finally {
            properties.setCodeTtl(savedTtl);
        }
    }

    @Test
    @DisplayName("SH1360: an unlisted redirect and a plain challenge are refused before any redirect")
    void unlistedRedirectsAndPlainChallengesAreRefused() throws Exception {
        mvc.perform(authorize(verifier('f'), "https://evil.example/cb").with(user("admin")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(AUTHORIZE).with(user("admin")).param("redirect_uri", REDIRECT)
                        .param("code_challenge", verifier('g')).param("code_challenge_method", "plain")
                        .param("device_name", "Pixel 9"))
                .andExpect(status().isBadRequest());
    }
}
