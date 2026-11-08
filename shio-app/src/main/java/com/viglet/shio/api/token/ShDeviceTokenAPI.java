/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.token;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.persistence.model.token.ShDeviceToken;
import com.viglet.shio.persistence.model.token.ShDeviceAuthCode;
import com.viglet.shio.persistence.repository.token.ShDeviceAuthCodeRepository;
import com.viglet.shio.persistence.repository.token.ShDeviceTokenRepository;
import com.viglet.shio.property.ShDeviceAuthProperties;
import com.viglet.shio.spring.security.ShDeviceTokenFilter;
import com.viglet.shio.spring.security.ShDeviceTokenFilter.ShDeviceTokenContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * SH1351 — mint, list and revoke the caller's own device tokens.
 *
 * <p>Minting needs a sign-in the instance already runs: HTTP Basic from the app's own sign-in screen
 * or a console session. It is refused to a device token, so a stolen device cannot extend itself, and
 * an API token never reaches this path at all: {@code ShApiTokenFilter} confines machine credentials
 * to the delivery and agent namespaces, which is what keeps an agent from minting a person.</p>
 *
 * <p>Stateless-writable for the same reason as the review queue: the app holds no session, and a
 * curator's browser holding one stays CSRF-checked.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping("/api/v2/device-token")
@Tag(name = "Device Token", description = "Person-bound credentials for a native client (SH1351)")
@Slf4j
public class ShDeviceTokenAPI {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final ShDeviceTokenRepository repository;
    private final Clock clock;
    /** SH1360 — the browser sign-in's one-time codes, and where an app may be sent back to. */
    private final ShDeviceAuthCodeRepository authCodes;
    private final ShDeviceAuthProperties authProperties;

    public ShDeviceTokenAPI(ShDeviceTokenRepository repository, Clock clock,
            ShDeviceAuthCodeRepository authCodes, ShDeviceAuthProperties authProperties) {
        this.repository = repository;
        this.clock = clock;
        this.authCodes = authCodes;
        this.authProperties = authProperties;
    }

    /** What a caller asks for. */
    public record MintRequest(String deviceName) {
    }

    /** The one answer that carries the secret; it is never shown again. */
    public record Minted(String id, String deviceName, String token, Instant createdDate) {
    }

    /** A device as the list shows it: never the secret. */
    public record Device(String id, String deviceName, Instant createdDate, Instant lastUsedAt,
            Instant revokedAt, String revokedBy, boolean current) {
    }

    @PostMapping
    @Operation(summary = "Mint a device token acting as the signed-in person; the secret is returned once")
    public ResponseEntity<Object> mint(@RequestBody(required = false) MintRequest body, Authentication authentication) {
        if (authentication.getDetails() instanceof ShDeviceTokenContext) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                    "A device token cannot mint another device token.");
            problem.setProperty("fix", "Sign in with your password (HTTP Basic) or from the console, "
                    + "then POST /api/v2/device-token.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
        }
        String name = body != null && body.deviceName() != null && !body.deviceName().isBlank()
                ? body.deviceName().strip() : null;
        if (name == null || name.length() > 100) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                    "deviceName is required and at most 100 characters.");
            problem.setProperty("fix", "Name the device so its owner can recognise it in the list.");
            problem.setProperty("example", "{\"deviceName\":\"Pixel 9\"}");
            return ResponseEntity.badRequest().body(problem);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(mintFor(authentication.getName(), name));
    }

    /** The one place a device token is created, for a sign-in or for an exchanged code (SH1360). */
    private Minted mintFor(String username, String name) {
        String secret = ShDeviceTokenFilter.newSecret();
        ShDeviceToken device = new ShDeviceToken();
        device.setUsername(username);
        device.setDeviceName(name);
        device.setTokenHash(ShDeviceTokenFilter.hash(secret));
        device.setCreatedDate(Instant.now(clock));
        repository.saveAndFlush(device);
        log.info("Minted device token '{}' for {}", name, device.getUsername());
        return new Minted(device.getId(), name, secret, device.getCreatedDate());
    }

    @GetMapping
    @Operation(summary = "The caller's own devices, newest first; never the secret")
    public List<Device> list(Authentication authentication) {
        String current = currentDevice(authentication);
        return repository.findByUsernameOrderByCreatedDateDesc(authentication.getName()).stream()
                .map(d -> new Device(d.getId(), d.getDeviceName(), d.getCreatedDate(), d.getLastUsedAt(),
                        d.getRevokedAt(), d.getRevokedBy(), d.getId().equals(current)))
                .toList();
    }

    /**
     * Revoke one device: its next call answers 401. The owner may revoke their own (a device may
     * revoke itself, which is the app's sign-out); an administrator may revoke anyone's.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke a device token; its next call answers 401")
    public ResponseEntity<Object> revoke(@PathVariable String id, Authentication authentication) {
        ShDeviceToken device = repository.findById(id).orElse(null);
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> ROLE_ADMIN.equals(a.getAuthority()));
        if (device == null || !(admin || device.getUsername().equals(authentication.getName()))) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                    "No device token with this id belongs to you.");
            problem.setProperty("fix", "GET /api/v2/device-token lists your devices and their ids.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
        }
        if (!device.isRevoked()) {
            device.setRevokedAt(Instant.now(clock));
            device.setRevokedBy(authentication.getName());
            repository.saveAndFlush(device);
            log.info("Revoked device token '{}' of {} by {}", device.getDeviceName(), device.getUsername(),
                    authentication.getName());
        }
        return ResponseEntity.ok(new Device(device.getId(), device.getDeviceName(), device.getCreatedDate(),
                device.getLastUsedAt(), device.getRevokedAt(), device.getRevokedBy(),
                device.getId().equals(currentDevice(authentication))));
    }

    // --- SH1360: the browser sign-in for a native app ----------------------------------------------

    /** The console login, which follows {@code returnUrl} back once the person has signed in. */
    static final String LOGIN = "/login?returnUrl=";

    private static final java.util.regex.Pattern CHALLENGE =
            java.util.regex.Pattern.compile("[A-Za-z0-9_-]{43,128}");
    private static final java.util.regex.Pattern VERIFIER =
            java.util.regex.Pattern.compile("[A-Za-z0-9._~-]{43,128}");
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    /**
     * Step one, in the system browser the app opened: sign the person in through the instance's own
     * login, then send the app a one-time code.
     *
     * <p>Unauthenticated, it sends the browser to the console login with this URL as the way back,
     * which is the password form or OIDC, whichever the instance runs, so the app never handles a
     * password or a provider. Signed in, it stores a code bound to the PKCE challenge and redirects to
     * the app with the code only: no secret rides in a URL. A redirect URI not on
     * {@code shio.device-auth.redirect-uris} is refused here, before any redirect happens, because a
     * code sent to an unlisted URI is a code handed to whoever registered it.</p>
     */
    @GetMapping("/authorize")
    @Operation(summary = "Browser sign-in for a native app: redirects back with a one-time code (PKCE S256)")
    public ResponseEntity<Object> authorize(
            @org.springframework.web.bind.annotation.RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @org.springframework.web.bind.annotation.RequestParam(name = "code_challenge", required = false) String challenge,
            @org.springframework.web.bind.annotation.RequestParam(name = "code_challenge_method", required = false) String method,
            @org.springframework.web.bind.annotation.RequestParam(name = "device_name", required = false) String deviceName,
            @org.springframework.web.bind.annotation.RequestParam(name = "state", required = false) String state,
            jakarta.servlet.http.HttpServletRequest request, Authentication authentication) {
        if (redirectUri == null || !authProperties.getRedirectUris().contains(redirectUri)) {
            return refuse(HttpStatus.BAD_REQUEST, "redirect_uri is not one this instance sends codes to.",
                    "An operator lists the app's redirect in shio.device-auth.redirect-uris, compared "
                            + "exactly. Allowed now: " + (authProperties.getRedirectUris().isEmpty()
                                    ? "none, so the browser sign-in is off." : authProperties.getRedirectUris()),
                    null);
        }
        if (!"S256".equals(method) || challenge == null || !CHALLENGE.matcher(challenge).matches()) {
            return refuse(HttpStatus.BAD_REQUEST, "A PKCE S256 code_challenge is required.",
                    "Send code_challenge = base64url(SHA-256(code_verifier)) without padding, and "
                            + "code_challenge_method=S256. A plain challenge is not accepted.",
                    "code_challenge_method=S256&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
        }
        String name = deviceName == null ? null : deviceName.strip();
        if (name == null || name.isEmpty() || name.length() > 100) {
            return refuse(HttpStatus.BAD_REQUEST, "device_name is required and at most 100 characters.",
                    "Name the device so its owner can recognise it in My devices.", "device_name=Pixel%209");
        }
        if (!signedIn(authentication)) {
            String back = request.getRequestURI() + "?" + request.getQueryString();
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(java.net.URI.create(LOGIN + java.net.URLEncoder.encode(back,
                            java.nio.charset.StandardCharsets.UTF_8)))
                    .cacheControl(org.springframework.http.CacheControl.noStore())
                    .build();
        }
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String code = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Instant now = Instant.now(clock);
        ShDeviceAuthCode row = new ShDeviceAuthCode();
        row.setCodeHash(ShDeviceTokenFilter.hash(code));
        row.setUsername(authentication.getName());
        row.setDeviceName(name);
        row.setCodeChallenge(challenge);
        row.setRedirectUri(redirectUri);
        row.setCreatedDate(now);
        row.setExpiresAt(now.plus(authProperties.getCodeTtl()));
        authCodes.saveAndFlush(row);
        String location = org.springframework.web.util.UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("code", code)
                .queryParamIfPresent("state", java.util.Optional.ofNullable(state))
                .encode().build().toUriString();
        return ResponseEntity.status(HttpStatus.FOUND).location(java.net.URI.create(location))
                .cacheControl(org.springframework.http.CacheControl.noStore()).build();
    }

    /** What the app sends back: the code from the redirect and the verifier it kept. */
    public record ExchangeRequest(String code, String codeVerifier) {
    }

    /**
     * Step two, from the app: trade the code and the PKCE verifier for the device token, once.
     *
     * <p>Unauthenticated by design, since the code and the verifier together are the credential. A
     * code is good for exactly one attempt: a wrong verifier burns it as surely as a right one, so a
     * code that leaked through a redirect cannot be guessed at. Every refusal reads the same, so the
     * answer never says whether a code exists.</p>
     */
    @PostMapping("/exchange")
    @Operation(summary = "Exchange a one-time code and its PKCE verifier for a device token, once")
    public ResponseEntity<Object> exchange(@RequestBody(required = false) ExchangeRequest body) {
        String code = body == null ? null : body.code();
        String verifier = body == null ? null : body.codeVerifier();
        if (code == null || verifier == null || !VERIFIER.matcher(verifier).matches()) {
            return invalidGrant();
        }
        ShDeviceAuthCode row = authCodes.findByCodeHash(ShDeviceTokenFilter.hash(code)).orElse(null);
        Instant now = Instant.now(clock);
        if (row == null || row.getUsedAt() != null || !now.isBefore(row.getExpiresAt())) {
            return invalidGrant();
        }
        row.setUsedAt(now);
        authCodes.saveAndFlush(row);
        if (!java.security.MessageDigest.isEqual(ShDeviceTokenFilter.s256(verifier).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                row.getCodeChallenge().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            log.info("Burned a device sign-in code for {}: the verifier did not match", row.getUsername());
            return invalidGrant();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(mintFor(row.getUsername(), row.getDeviceName()));
    }

    /** Signed in by a person: not anonymous, and not a device token extending itself. */
    private static boolean signedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                && !(authentication.getDetails() instanceof ShDeviceTokenContext);
    }

    private static ResponseEntity<Object> invalidGrant() {
        return refuse(HttpStatus.BAD_REQUEST, "This code cannot be exchanged.",
                "A code is good for one attempt, before it expires, with the verifier whose challenge "
                        + "started the sign-in. Start the sign-in again from the app.", null);
    }

    private static ResponseEntity<Object> refuse(HttpStatus status, String detail, String fix, String example) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("fix", fix);
        if (example != null) {
            problem.setProperty("example", example);
        }
        return ResponseEntity.status(status).cacheControl(org.springframework.http.CacheControl.noStore())
                .body(problem);
    }

    private static String currentDevice(Authentication authentication) {
        return authentication.getDetails() instanceof ShDeviceTokenContext context ? context.deviceTokenId() : null;
    }
}
