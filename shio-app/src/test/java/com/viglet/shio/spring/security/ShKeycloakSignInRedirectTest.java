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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.viglet.shio.property.ShAppProperties;

/**
 * SH1426 — on a Keycloak instance, a person's browser with no session is sent to sign in.
 *
 * <p>Measured on shio.viglet.cloud: {@code /} answered the 401 problem document as a page and
 * {@code /bento} answered {@code {"error":"Not Found"}}. A program asking for JSON keeps the 401.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShKeycloakSignInRedirectTest {

    private static final String NAVIGATE_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    @Test
    @DisplayName("SH1426: /, /bento and /bento/content opened by a browser go to the Keycloak sign-in")
    void aBrowserNavigationIsSignedIn() throws Exception {
        for (String path : new String[] {"/", "/bento", "/bento/content"}) {
            MockHttpServletRequest request = navigation(path);
            MockHttpServletResponse response = new MockHttpServletResponse();

            entryPoint(true).commence(request, response, null);

            assertThat(response.getStatus()).as("SH1426: %s", path).isEqualTo(302);
            assertThat(response.getRedirectedUrl()).as("SH1426: %s", path)
                    .isEqualTo("/oauth2/authorization/keycloak");
        }
    }

    @Test
    @DisplayName("SH1426: a client asking for JSON still gets the 401 document")
    void aJsonClientKeepsTheProblem() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint(true).commence(request, response, null);

        assertThat(response.getRedirectedUrl()).as("SH1426").isNull();
        assertThat(response.getStatus()).isNotEqualTo(302);
    }

    @Test
    @DisplayName("SH1426: a console fetch and an /api/** call never redirect, whatever Accept says")
    void fetchesAndTheApiNeverRedirect() {
        ShAuthenticationEntryPoint keycloak = entryPoint(true);
        MockHttpServletRequest fetch = new MockHttpServletRequest("GET", "/bento");
        fetch.addHeader("Accept", NAVIGATE_ACCEPT);
        fetch.addHeader("Sec-Fetch-Mode", "cors");
        assertThat(keycloak.signsIn(fetch)).as("SH1426: a fetch is not a person arriving").isFalse();
        assertThat(keycloak.signsIn(navigation("/api/v2/object"))).as("SH1426").isFalse();
        MockHttpServletRequest post = navigation("/bento");
        post.setMethod("POST");
        assertThat(keycloak.signsIn(post)).isFalse();
    }

    @Test
    @DisplayName("SH1426: with local login the entry point answers as before")
    void localLoginIsUnchanged() {
        assertThat(entryPoint(false).signsIn(navigation("/"))).as("SH1426").isFalse();
    }

    private static MockHttpServletRequest navigation(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("Accept", NAVIGATE_ACCEPT);
        request.addHeader("Sec-Fetch-Mode", "navigate");
        return request;
    }

    @SuppressWarnings("unchecked")
    private static ShAuthenticationEntryPoint entryPoint(boolean keycloak) {
        ObjectProvider<RequestMappingHandlerMapping> mappings = mock(ObjectProvider.class);
        when(mappings.orderedStream()).thenAnswer(invocation -> Stream.empty());
        ShAppProperties properties = new ShAppProperties();
        properties.setKeycloak(keycloak);
        return new ShAuthenticationEntryPoint(mappings, properties);
    }
}
