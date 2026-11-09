/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import com.viglet.shio.persistence.model.token.ShApiToken;
import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.model.token.ShDeviceAuthCode;
import com.viglet.shio.persistence.repository.token.ShApiTokenRepository;
import com.viglet.shio.persistence.repository.token.ShDeviceAuthCodeRepository;
import com.viglet.shio.spring.security.ShDeviceTokenFilter;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;
import com.viglet.shio.spring.security.auth.ShApiTokenFilter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * SH1375 — signing in from a site host lands back on the site host, signed in there and only there.
 *
 * <p>A site at {@code <furl>.<host-domain>} is another origin, so the session the instance's login
 * sets is never sent to it. SH1362 therefore brought a visitor back to the page's {@code /sites}
 * address on the instance. Scoping the console session to a parent domain was the other way, and was
 * refused: every site host would then receive the console session, and a replicated site's own
 * scripts run there with {@code /api} passed through. So the sign-in crosses as a <b>one-time
 * handoff code</b>, the shape SH1360 uses for a native app:</p>
 *
 * <ol>
 *   <li>The members-only page on the site host sends the visitor to the instance's login, with
 *       {@link #HANDOFF} as the way back and a random {@code state} it also leaves in a host-only
 *       cookie on the site host ({@link ShSitesAPI#loginFor}).</li>
 *   <li>{@link #handoff}, on the instance and signed in, checks that the destination is a site host
 *       of a site this instance serves, stores a code bound to the state's hash and that origin, good
 *       for {@link #CODE_TTL}, and redirects to the site host's {@link #EXCHANGE} with the code.</li>
 *   <li>{@link #exchange}, on the site host, burns the code, requires the state cookie the first step
 *       left on that same browser (so nobody can sign a victim into their own account with a link),
 *       and sets {@link ShApiTokenFilter#MEMBER_COOKIE}: a {@code MEMBER} token for that one site,
 *       host-only and {@code HttpOnly}, which reaches only that host's delivery.</li>
 * </ol>
 *
 * <p>The code lives in SH1360's table, which is what that table is: the one-time code a browser
 * sign-in hands back. Its {@code redirect_uri} holds the site host's origin, its challenge the state's
 * S256, and its device name the site's label.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Slf4j
@RestController
@RequestMapping(ShSiteMemberAPI.PATH)
@Tag(name = "SiteMember", description = "Sign in on a site host through the instance's login")
public class ShSiteMemberAPI {

    public static final String PATH = "/api/v2/site-member";
    /** Step two, on the instance. */
    public static final String HANDOFF = PATH + "/handoff";
    /** Step three, on the site host: {@code /api} passes {@link ShSiteHostFilter} untouched. */
    public static final String EXCHANGE = PATH + "/exchange";
    /** The site host's half of the binding: a random value only the browser that started holds. */
    public static final String STATE_COOKIE = "shio-site-member-state";

    /** How long a handoff code is good for: one redirect, so a minute is generous. */
    static final Duration CODE_TTL = Duration.ofSeconds(60);
    /** How long a sign-in on a site host lasts, after which the page asks again. */
    static final Duration SESSION = Duration.ofHours(8);
    /** The state cookie outlives the login form a visitor might take a while over, and no more. */
    static final Duration STATE_TTL = Duration.ofMinutes(15);

    private static final String DEVICE_PREFIX = "site host ";
    private static final Pattern STATE = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShSiteHost siteHost;
    private final ShPageRenderService shPageRenderService;
    private final ShDeviceAuthCodeRepository codes;
    private final ShApiTokenRepository tokens;
    private final Clock clock;

    public ShSiteMemberAPI(ShSiteHost siteHost, ShPageRenderService shPageRenderService,
                           ShDeviceAuthCodeRepository codes, ShApiTokenRepository tokens, Clock clock) {
        this.siteHost = siteHost;
        this.shPageRenderService = shPageRenderService;
        this.codes = codes;
        this.tokens = tokens;
        this.clock = clock;
    }

    /** 32 random bytes, base64url without padding: 43 characters. */
    static String random() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * The site host's origin as the browser addressed it: the configured scheme (a proxy terminates
     * TLS, so the request's own scheme says nothing) and the {@code Host} the browser sent, port and
     * all, which {@link ShSiteHost#labelOf} has already accepted.
     */
    static String originOf(HttpServletRequest request, ShSiteHost siteHost) {
        String host = request.getHeader(HttpHeaders.HOST);
        if (host == null || host.isBlank()) {
            int port = request.getServerPort();
            host = request.getServerName() + (port > 0 && port != 80 && port != 443 ? ":" + port : "");
        }
        return siteHost.scheme() + "://" + host.trim().toLowerCase(Locale.ROOT);
    }

    /** The state cookie {@link ShSitesAPI#loginFor} leaves on the site host. */
    static ResponseCookie stateCookie(String state, HttpServletRequest request) {
        return ResponseCookie.from(STATE_COOKIE, state).httpOnly(true).secure(request.isSecure())
                .sameSite("Lax").path(EXCHANGE).maxAge(STATE_TTL).build();
    }

    /**
     * Step two: a signed-in person, on the instance, is handed back to the site host with a code.
     *
     * @param to    the page on the site host, absolute: it is checked, never trusted
     * @param state the value the site host left in {@link #STATE_COOKIE}
     */
    @GetMapping("/handoff")
    @Operation(summary = "Hand a signed-in person back to a site host with a one-time code")
    public ResponseEntity<Object> handoff(@RequestParam(name = "to", required = false) String to,
                                          @RequestParam(name = "state", required = false) String state,
                                          HttpServletRequest request, Authentication authentication) {
        if (!siteHost.enabled()) {
            return refuse(HttpStatus.NOT_FOUND, "This instance serves no site hosts.",
                    "shio.sites.host-domain is empty, so there is no site host to sign in on.");
        }
        Target destination = parse(to);
        Optional<String> label = destination == null ? Optional.empty()
                : siteHost.labelOf(destination.authority());
        Optional<String> siteId = label.flatMap(shPageRenderService::siteIdOf);
        if (siteId.isEmpty()) {
            // Checked before anything else is done with it: a redirect that takes its target from the
            // caller is an open redirect unless the target is one this instance serves.
            return refuse(HttpStatus.BAD_REQUEST, "`to` is not a page on a site host this instance serves.",
                    "`to` is the absolute address of a page at <site>." + siteHost.domain()
                            + ", the one a members-only page puts here when it sends a visitor to sign in.");
        }
        if (state == null || !STATE.matcher(state).matches()) {
            return refuse(HttpStatus.BAD_REQUEST, "`state` is missing or malformed.",
                    "Open the members-only page again: it starts the sign-in and supplies the state.");
        }
        if (!signedIn(authentication)) {
            String back = request.getRequestURI() + "?" + request.getQueryString();
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(ShSitesAPI.LOGIN + URLEncoder.encode(back, StandardCharsets.UTF_8)))
                    .cacheControl(CacheControl.noStore()).build();
        }
        String origin = destination.scheme() + "://" + destination.authority();
        String code = random();
        Instant now = Instant.now(clock);
        ShDeviceAuthCode row = new ShDeviceAuthCode();
        row.setCodeHash(ShDeviceTokenFilter.hash(code));
        row.setUsername(authentication.getName());
        row.setDeviceName(DEVICE_PREFIX + label.get());
        row.setCodeChallenge(ShDeviceTokenFilter.s256(state));
        row.setRedirectUri(origin);
        row.setCreatedDate(now);
        row.setExpiresAt(now.plus(CODE_TTL));
        codes.saveAndFlush(row);
        String location = origin + EXCHANGE + "?code=" + code + "&to="
                + URLEncoder.encode(destination.page(), StandardCharsets.UTF_8);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location))
                .cacheControl(CacheControl.noStore()).build();
    }

    /**
     * Step three: on the site host, the code becomes that host's sign-in, once.
     *
     * <p>Every refusal reads the same and names no reason, so the answer never says whether a code
     * exists; the code is burned before anything else is compared, so a code that leaked through a
     * log cannot be tried twice.</p>
     */
    @GetMapping("/exchange")
    @Operation(summary = "Exchange a one-time code on a site host for a sign-in on that host alone")
    public ResponseEntity<String> exchange(@RequestParam(name = "code", required = false) String code,
                                           @RequestParam(name = "to", required = false) String to,
                                           HttpServletRequest request) {
        String page = sitePath(to);
        Optional<String> label = siteHost.enabled() ? siteHost.labelOf(request.getServerName()) : Optional.empty();
        String state = cookie(request, STATE_COOKIE);
        ShDeviceAuthCode row = code == null || label.isEmpty() ? null
                : codes.findByCodeHash(ShDeviceTokenFilter.hash(code)).orElse(null);
        Instant now = Instant.now(clock);
        if (row == null || row.getUsedAt() != null || !now.isBefore(row.getExpiresAt())) {
            return expired(page);
        }
        row.setUsedAt(now);
        codes.saveAndFlush(row);
        Optional<String> siteId = shPageRenderService.siteIdOf(label.get());
        boolean bound = state != null && same(ShDeviceTokenFilter.s256(state), row.getCodeChallenge())
                && same(originOf(request, siteHost), row.getRedirectUri())
                && same(DEVICE_PREFIX + label.get(), row.getDeviceName());
        if (!bound || siteId.isEmpty()) {
            log.info("Refused a site-host sign-in for {} on {}: the code was not this browser's or this host's",
                    row.getUsername(), label.get());
            return expired(page);
        }
        ShApiToken token = new ShApiToken();
        token.setTitle("site-member:" + label.get());
        token.setDescription("A person signed in on one site host (SH1375)");
        token.setToken(random());
        token.setOwner(row.getUsername());
        token.setCreatedDate(now);
        token.setEnabled(true);
        token.setScope(ShApiTokenScope.MEMBER);
        token.setEnv(ShApiTokenEnv.PROD);
        token.setSiteIds(Set.of(siteId.get()));
        token.setExpiresAt(now.plus(SESSION));
        // Ephemeral: never listed among the API tokens a person manages, and purged once expired.
        token.setEphemeral(true);
        tokens.saveAndFlush(token);
        tokens.deleteByEphemeralTrueAndExpiresAtBefore(now);
        ResponseCookie member = ResponseCookie.from(ShApiTokenFilter.MEMBER_COOKIE, token.getToken())
                .httpOnly(true).secure(request.isSecure()).sameSite("Lax").path("/").maxAge(SESSION).build();
        ResponseCookie spent = ResponseCookie.from(STATE_COOKIE, "").httpOnly(true).secure(request.isSecure())
                .sameSite("Lax").path(EXCHANGE).maxAge(Duration.ZERO).build();
        log.info("Signed {} in on site host {}", row.getUsername(), label.get());
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, member.toString())
                .header(HttpHeaders.SET_COOKIE, spent.toString())
                .location(URI.create(page))
                .cacheControl(CacheControl.noStore()).build();
    }

    /** SH1395 — where a site's own "Sign out" link points, on the site host. */
    public static final String SIGN_OUT = PATH + "/sign-out";

    /**
     * SH1395 — sign out of this site host: the sign-in the cookie carries stops working, the cookie
     * goes, and the visitor lands back on the page they named (a path on this host, never another).
     *
     * <p>A GET, so a plain link can be it, as a template draws one. That leaves a cross-site page able
     * to sign a visitor out, which costs them one sign-in and gains the page nothing, the trade every
     * site's logout link makes.</p>
     */
    @GetMapping("/sign-out")
    @Operation(summary = "Sign out of the site host this is called on, then go back to a page on it")
    public ResponseEntity<Void> signOut(@RequestParam(name = "to", required = false) String to,
                                        HttpServletRequest request) {
        String value = cookie(request, ShApiTokenFilter.MEMBER_COOKIE);
        if (value != null && !value.isBlank()) {
            tokens.disableMemberToken(value);
        }
        ResponseCookie cleared = ResponseCookie.from(ShApiTokenFilter.MEMBER_COOKIE, "").httpOnly(true)
                .secure(request.isSecure()).sameSite("Lax").path("/").maxAge(Duration.ZERO).build();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .location(URI.create(sitePath(to)))
                .cacheControl(CacheControl.noStore()).build();
    }

    /** A person: not anonymous, and not an API token standing in for one. */
    private static boolean signedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && !(authentication.getDetails() instanceof ShApiTokenContext);
    }

    /**
     * An absolute http(s) URL: scheme, an authority with no userinfo, and an optional path and query
     * with no fragment and no whitespace. Matched rather than parsed, so a malformed value is a refusal
     * and never an exception.
     */
    private static final Pattern TARGET = Pattern.compile(
            "(?i)(https?)://([^/?#@\\s\\\\]+)(/[^#\\s]*)?");

    /** Where {@code to} points: its origin's two halves and the page on it. */
    private record Target(String scheme, String authority, String page) {
    }

    /** {@code to} as a {@link Target}, or {@code null}. */
    private static Target parse(String to) {
        java.util.regex.Matcher matcher = to == null ? null : TARGET.matcher(to);
        if (matcher == null || !matcher.matches()) {
            return null;
        }
        String page = matcher.group(3) == null ? "/" : matcher.group(3);
        return new Target(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2).toLowerCase(Locale.ROOT), page);
    }

    /** A path on this host, or its home: never another origin (SH1299's rule, on the site host). */
    static String sitePath(String to) {
        if (to == null || !to.startsWith("/") || to.startsWith("//") || to.startsWith("/\\")) {
            return "/";
        }
        return to;
    }

    private static boolean same(String a, String b) {
        return a != null && b != null
                && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** A browser is here, so it gets a page that says what to do, and nothing about why. */
    private static ResponseEntity<String> expired(String page) {
        String back = HtmlUtils.htmlEscape(page);
        String html = "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                + "<meta name=\"robots\" content=\"noindex\">\n<title>Sign in again</title>\n</head>\n<body>\n"
                + "<main>\n<h1>Sign in again</h1>\n<p>This sign-in link has expired or was already used.</p>\n"
                + "<p><a href=\"" + back + "\">Go back to the page</a> and sign in from there.</p>\n"
                + "</main>\n</body>\n</html>\n";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noStore()).body(html);
    }

    private static ResponseEntity<Object> refuse(HttpStatus status, String detail, String fix) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("fix", fix);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(problem);
    }
}
