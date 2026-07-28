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
    void readTokenOnAgentGetAuthenticates() throws Exception {
        // SH76: the agent surface is a shape over the CDA, so a delivery key must
        // read it without also holding a console credential.
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key"))
                .thenReturn(Optional.of(token(ShApiTokenScope.READ, Set.of())));

        filter().doFilter(request("GET", "/api/v2/agent/manifest"),
                new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority").contains("ROLE_CDA");
    }

    @Test
    void readTokenOnAgentNonGetIsStillForbidden() throws Exception {
        // Widening the prefix must widen reads only: the SH2 method gate still holds,
        // so a READ key cannot write through the agent namespace either.
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key"))
                .thenReturn(Optional.of(token(ShApiTokenScope.READ, Set.of())));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter().doFilter(request("POST", "/api/v2/agent/batch"), res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void agentTokenWritesOnTheAgentSurface() throws Exception {
        // SH106: the whole point of the scope — an agent writes with POST, on its own surface.
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key"))
                .thenReturn(Optional.of(token(ShApiTokenScope.AGENT, Set.of())));

        filter().doFilter(request("POST", "/api/v2/agent/batch"),
                new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority")
                .as("an agent reads drafts by definition, and is not a CDA write key")
                .contains("ROLE_AGENT", "ROLE_CDA_PREVIEW")
                .doesNotContain("ROLE_CDA_WRITE");
    }

    @Test
    void agentTokenIsStillReadOnlyOnTheCda() throws Exception {
        // The reason AGENT is not just WRITE: the CDA write path patches published content
        // outside the agent protocol's guards, so an agent's credential must not reach it.
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key"))
                .thenReturn(Optional.of(token(ShApiTokenScope.AGENT, Set.of())));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter().doFilter(request("PATCH", "/api/v2/cda/post/abc"), res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void agentTokenCarriesItsPublishCapability() throws Exception {
        // SH107: the decision travels on the request's own context, not a later DB read.
        ShApiToken granted = token(ShApiTokenScope.AGENT, Set.of());
        granted.setMayPublish(true);
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key")).thenReturn(Optional.of(granted));

        filter().doFilter(request("POST", "/api/v2/agent/batch"),
                new MockHttpServletResponse(), new MockFilterChain());

        assertThat(ShApiTokenContext.current()).get()
                .extracting(ShApiTokenContext::mayPublish).isEqualTo(true);
    }

    @Test
    void anAgentTokenMayNotPublishByDefault() throws Exception {
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key"))
                .thenReturn(Optional.of(token(ShApiTokenScope.AGENT, Set.of())));

        filter().doFilter(request("POST", "/api/v2/agent/batch"),
                new MockHttpServletResponse(), new MockFilterChain());

        assertThat(ShApiTokenContext.currentMayPublish())
                .as("publishing is granted per token, never implied by write access")
                .isFalse();
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

    // ── SH233: which credential wins when a caller holds both ──────────────────────────

    @Test
    void aPresentedKeyWinsOverAnExistingSession() throws Exception {
        // The defect this fixes: the console is same-origin with the API, so its session cookie
        // rides along whatever `withCredentials` says (that flag is cross-origin only), the
        // security context is restored before this filter runs, and the `Key` header was never
        // read — so the Universal Editor's PATCH authenticated as a curator with no
        // ROLE_CDA_WRITE and 403'd. Presenting a key is a request to be that principal.
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key"))
                .thenReturn(Optional.of(token(ShApiTokenScope.WRITE, Set.of())));
        SecurityContextHolder.getContext().setAuthentication(session("curator"));

        filter().doFilter(request("PATCH", "/api/v2/cda/post/abc"),
                new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getName()).isEqualTo("alice");
        assertThat(auth.getAuthorities()).extracting("authority").contains("ROLE_CDA_WRITE");
    }

    @Test
    void withoutAKeyAnExistingSessionIsLeftAlone() throws Exception {
        // No stubbing: the filter must not even look a token up when none was presented.
        MockHttpServletRequest req = new MockHttpServletRequest("PATCH", "/api/v2/cda/post/abc");
        req.setRequestURI("/api/v2/cda/post/abc");
        SecurityContextHolder.getContext().setAuthentication(session("curator"));

        filter().doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("curator");
    }

    @Test
    void anUnusableKeyDoesNotUnauthenticateTheSession() throws Exception {
        // A key is a request to be someone, not a request to stop being someone else — so a
        // rejected one leaves the context as it was and the call fails on authorization, which
        // is the same answer it gave before this rule existed.
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key")).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(session("curator"));

        filter().doFilter(request("GET", "/api/v2/cda/site"),
                new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("curator");
    }

    @Test
    void aKeyStillCannotAuthenticateAConsoleRequest() throws Exception {
        // The SH2 confinement is what stops this being an escalation: outside the delivery and
        // agent namespaces the header is ignored, session or no session.
        SecurityContextHolder.getContext().setAuthentication(session("curator"));

        filter().doFilter(request("DELETE", "/api/v2/post-unified/abc"),
                new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("curator");
    }

    @Test
    void aSessionDoesNotExemptAKeyFromItsOwnBounds() throws Exception {
        // Holding a console session must not buy the token anything: a READ key that PATCHes is
        // still the SH2 403, and the session is not consulted as a fallback.
        when(shApiTokenRepository.findByTokenAcrossTenants("the-key"))
                .thenReturn(Optional.of(token(ShApiTokenScope.READ, Set.of())));
        SecurityContextHolder.getContext().setAuthentication(session("curator"));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter().doFilter(request("PATCH", "/api/v2/cda/post/abc"), res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(403);
    }

    /** A console session, as SecurityContextHolderFilter would have restored it. */
    private static Authentication session(String username) {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                username, "x",
                java.util.List.of(new org.springframework.security.core.authority
                        .SimpleGrantedAuthority("ROLE_USER")));
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
