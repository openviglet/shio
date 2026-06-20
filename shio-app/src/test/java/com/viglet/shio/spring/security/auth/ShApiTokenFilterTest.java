/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.spring.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.shio.api.cda.ShCdaProperties;
import com.viglet.shio.api.cda.ShCdaRateLimiter;
import com.viglet.shio.persistence.model.token.ShApiToken;
import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.token.ShApiTokenRepository;

/**
 * Unit coverage for the SH2 scope + per-site token enforcement in
 * {@link ShApiTokenFilter}: namespace confinement, the scope-based method gate,
 * and the scope-based granted authorities.
 *
 * @author Alexandre Oliveira
 */
@ExtendWith(MockitoExtension.class)
class ShApiTokenFilterTest {

    /** A fixed reference instant so expiry semantics are deterministic (no system clock). */
    private static final Instant FIXED_NOW = Instant.parse("2026-06-01T00:00:00Z");

    @Mock
    private ShApiTokenRepository shApiTokenRepository;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** A limiter with a generous budget so it never interferes. */
    private ShApiTokenFilter filter() {
        return new ShApiTokenFilter(shApiTokenRepository, new ShCdaRateLimiter(new ShCdaProperties()),
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    private ShApiTokenFilter filter(ShCdaRateLimiter rateLimiter) {
        return new ShApiTokenFilter(shApiTokenRepository, rateLimiter, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    private ShCdaRateLimiter limiterWithCapacity(long capacity) {
        ShCdaProperties props = new ShCdaProperties();
        props.getRateLimit().setCapacity(capacity);
        props.getRateLimit().setRefillTokens(capacity);
        props.getRateLimit().setRefillPeriodSeconds(60);
        return new ShCdaRateLimiter(props);
    }

    private ShApiToken token(ShApiTokenScope scope, Set<String> siteIds) {
        return token(scope, siteIds, ShApiTokenEnv.PROD);
    }

    private ShApiToken token(ShApiTokenScope scope, Set<String> siteIds, ShApiTokenEnv env) {
        ShApiToken t = new ShApiToken();
        t.setId("tok-1");
        t.setTitle("test");
        t.setToken("the-key");
        t.setOwner("alice");
        t.setEnabled(true);
        t.setScope(scope);
        t.setSiteIds(siteIds);
        t.setEnv(env);
        return t;
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRequestURI(uri);
        req.addHeader("Key", "the-key");
        return req;
    }

    @Test
    void keyHeaderOutsideCdaNamespaceIsIgnored() throws Exception {
        // No stubbing of the repository: the filter must short-circuit before any lookup.
        MockHttpServletRequest req = request("GET", "/api/v2/post/abc");
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull(); // chain proceeded unauthenticated
    }

    @Test
    void readTokenOnCdaGetAuthenticatesWithContext() throws Exception {
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key"))
                .thenReturn(Optional.of(token(ShApiTokenScope.READ, Set.of("siteA"))));
        MockHttpServletRequest req = request("GET", "/api/v2/cda/site");

        filter().doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority")
                .contains("ROLE_CDA", "ROLE_USER");
        assertThat(auth.getDetails()).isInstanceOf(ShApiTokenContext.class);
        ShApiTokenContext ctx = (ShApiTokenContext) auth.getDetails();
        assertThat(ctx.scope()).isEqualTo(ShApiTokenScope.READ);
        assertThat(ctx.allowsSite("siteA")).isTrue();
        assertThat(ctx.allowsSite("siteB")).isFalse();
    }

    @Test
    void readTokenOnCdaNonGetIsForbidden() throws Exception {
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key"))
                .thenReturn(Optional.of(token(ShApiTokenScope.READ, Set.of())));
        MockHttpServletRequest req = request("POST", "/api/v2/cda/site");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter().doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void writeTokenIsNotMethodGated() throws Exception {
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key"))
                .thenReturn(Optional.of(token(ShApiTokenScope.WRITE, Set.of())));
        MockHttpServletRequest req = request("POST", "/api/v2/cda/post/abc");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter().doFilter(req, res, new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority").contains("ROLE_CDA_WRITE");
    }

    @Test
    void readTokenOnCdaPatchIsForbidden() throws Exception {
        // SH10 write surface uses PATCH; a READ token must still be GET-gated.
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key"))
                .thenReturn(Optional.of(token(ShApiTokenScope.READ, Set.of())));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter().doFilter(request("PATCH", "/api/v2/cda/post/abc"), res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void writeTokenOnCdaPatchAuthenticatesWithWriteAuthority() throws Exception {
        // SH10: a WRITE token may PATCH the CDA write surface and carries ROLE_CDA_WRITE.
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key"))
                .thenReturn(Optional.of(token(ShApiTokenScope.WRITE, Set.of())));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter().doFilter(request("PATCH", "/api/v2/cda/post/abc"), res, new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority").contains("ROLE_CDA_WRITE");
    }

    @Test
    void previewTokenCarriesPreviewAuthority() throws Exception {
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key"))
                .thenReturn(Optional.of(token(ShApiTokenScope.PREVIEW, Set.of())));
        MockHttpServletRequest req = request("GET", "/api/v2/cda/site");

        filter().doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority").contains("ROLE_CDA_PREVIEW");
    }

    @Test
    void previewEnvTokenCarriesPreviewAuthorityAndContext() throws Exception {
        // SH5: a READ-scope token whose environment is PREVIEW is preview-capable —
        // it carries ROLE_CDA_PREVIEW and a context whose preview() drives drafts.
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key"))
                .thenReturn(Optional.of(token(ShApiTokenScope.READ, Set.of(), ShApiTokenEnv.PREVIEW)));
        MockHttpServletRequest req = request("GET", "/api/v2/cda/site");

        filter().doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority").contains("ROLE_CDA_PREVIEW");
        ShApiTokenContext ctx = (ShApiTokenContext) auth.getDetails();
        assertThat(ctx.env()).isEqualTo(ShApiTokenEnv.PREVIEW);
        assertThat(ctx.preview()).isTrue();
    }

    @Test
    void prodEnvTokenIsNotPreview() throws Exception {
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key"))
                .thenReturn(Optional.of(token(ShApiTokenScope.READ, Set.of(), ShApiTokenEnv.PROD)));

        filter().doFilter(request("GET", "/api/v2/cda/site"), new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).extracting("authority").doesNotContain("ROLE_CDA_PREVIEW");
        assertThat(((ShApiTokenContext) auth.getDetails()).preview()).isFalse();
    }

    @Test
    void nullScopeDefaultsToReadGetOnly() throws Exception {
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key"))
                .thenReturn(Optional.of(token(null, Set.of())));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter().doFilter(request("POST", "/api/v2/cda/site"), res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    void readTokenOnGraphQlPostAuthenticates() throws Exception {
        // GraphQL (SH4) reads via POST; the SH2 GET-only gate must not fire for it.
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key"))
                .thenReturn(Optional.of(token(ShApiTokenScope.READ, Set.of("siteA"))));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter().doFilter(request("POST", "/graphql"), res, new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority").contains("ROLE_CDA");
        assertThat(auth.getDetails()).isInstanceOf(ShApiTokenContext.class);
        assertThat(((ShApiTokenContext) auth.getDetails()).allowsSite("siteA")).isTrue();
    }

    @Test
    void expiredTokenIsIgnored() throws Exception {
        // SH13: a short-lived preview token past its expiry stops authenticating.
        ShApiToken expired = token(ShApiTokenScope.PREVIEW, Set.of(), ShApiTokenEnv.PREVIEW);
        expired.setExpiresAt(FIXED_NOW.minusSeconds(60));
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key")).thenReturn(Optional.of(expired));
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request("GET", "/api/v2/cda/site"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull(); // proceeded unauthenticated
    }

    @Test
    void unexpiredEphemeralTokenAuthenticates() throws Exception {
        // SH13: a not-yet-expired ephemeral preview token authenticates and is preview-capable.
        ShApiToken fresh = token(ShApiTokenScope.PREVIEW, Set.of("siteA"), ShApiTokenEnv.PREVIEW);
        fresh.setEphemeral(true);
        fresh.setExpiresAt(FIXED_NOW.plusSeconds(900));
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key")).thenReturn(Optional.of(fresh));

        filter().doFilter(request("GET", "/api/v2/cda/site"), new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority").contains("ROLE_CDA_PREVIEW");
        assertThat(((ShApiTokenContext) auth.getDetails()).preview()).isTrue();
    }

    @Test
    void exceedingRateLimitReturns429() throws Exception {
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key"))
                .thenReturn(Optional.of(token(ShApiTokenScope.READ, Set.of())));
        ShApiTokenFilter filter = filter(limiterWithCapacity(1));

        // First call within budget → authenticates.
        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/api/v2/cda/site"), first, new MockFilterChain());
        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(first.getHeader("X-Rate-Limit-Remaining")).isEqualTo("0");
        SecurityContextHolder.clearContext();

        // Second call exhausts the bucket → 429 + Retry-After, no auth set.
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/api/v2/cda/site"), second, new MockFilterChain());
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getHeader("Retry-After")).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
