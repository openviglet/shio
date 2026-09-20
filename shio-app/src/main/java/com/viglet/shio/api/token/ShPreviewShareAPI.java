/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.token;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.persistence.model.token.ShApiToken;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.token.ShApiTokenRepository;
import com.viglet.shio.render.ShSiteFrontEnd;
import com.viglet.shio.spring.security.auth.ShApiTokenFilter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;

/**
 * SH1042 — the one anonymous door into Shio's own renderer, and it opens once.
 *
 * <h2>Why a share link needed a route at all</h2>
 *
 * <p>{@code /preview/**} is deliberately outside {@code permitAll} (SH340: the entry predated the
 * renderer and made every draft on the instance anonymous-readable), so a draft Shio renders itself
 * could be shown to nobody but a curator. SH184 minted <b>nothing</b> for an internal front end,
 * with the reasoning that "a credential for a console-authenticated path would unlock nothing" —
 * true while the path unlocked for no credential at all, and the reason the only previewable sites
 * were the ones with a deployed external front end. That is the product's premise inverted: a site
 * an agent has just built has neither a front end nor a stakeholder who can log in.</p>
 *
 * <h2>Why the credential is exchanged for a cookie rather than carried</h2>
 *
 * <p>A link is copied, forwarded and pasted into a ticket. A token in the query string therefore
 * ends up in browser history, in the {@code Referer} of every image the page loads, and in whatever
 * the recipient pastes it into. Exchanging it once means the credential leaves the URL on the first
 * hop: what the reader's address bar then holds is an ordinary preview path, and the cookie is
 * {@code HttpOnly} so the page's own scripts cannot read it back out.</p>
 *
 * <h2>This route is not where the authorization happens</h2>
 *
 * <p>It validates the credential and sets it; the <b>site</b> is enforced at the renderer, where the
 * site is already resolved (see {@code ShRenderAPI}). That split is what makes {@code to} safe to
 * take from the caller: tampering with it reaches a page the token does not allow and is refused
 * there, so this route needs no idea of what the token may read. What it does check is that the
 * destination is a preview path at all, because a redirect that takes an arbitrary target is an open
 * redirect wearing a feature's name.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping(ShPreviewShareAPI.PATH)
@Tag(name = "PreviewShare", description = "Exchange an expiring share link for a preview session")
public class ShPreviewShareAPI {

    public static final String PATH = "/api/v2/preview-share";

    /**
     * Where the cookie applies, which is the whole of what it may ever be sent to.
     *
     * <p>{@code /preview} and not {@code /}: a credential scoped to the path it authenticates
     * cannot ride along on a request to the console or the API even by accident, and the browser
     * enforces that without anything here having to remember to.</p>
     */
    private static final String COOKIE_PATH = "/preview";

    private final ShApiTokenRepository shApiTokenRepository;
    private final Clock clock;

    public ShPreviewShareAPI(ShApiTokenRepository shApiTokenRepository, Clock clock) {
        this.shApiTokenRepository = shApiTokenRepository;
        this.clock = clock;
    }

    /**
     * Turns a share link into a preview session.
     *
     * @param token the credential from the link
     * @param to    the preview path to land on, relative to {@code /preview} — the site's own key
     *              and the page, e.g. {@code /Acme/about}. Defaults to the site nothing, which the
     *              renderer answers as that site's home
     */
    @GetMapping("/{token}")
    @Operation(summary = "Exchange a share link for an expiring preview cookie")
    public ResponseEntity<Void> exchange(@PathVariable String token,
                                         @RequestParam(name = "to", required = false) String to,
                                         HttpServletRequest request) {
        ShApiToken row = shApiTokenRepository.findByTokenAcrossTenants(token).orElse(null);
        Instant now = Instant.now(clock);
        // 404 and not 401, for the reason ShAuthenticationEntryPoint gives about an absent path: a
        // 401 tells a prober that the link they guessed is one character from working.
        if (row == null || !row.isEnabled() || row.getScope() != ShApiTokenScope.SHARE
                || row.isExpired(now)) {
            return ResponseEntity.notFound().build();
        }
        String destination = ShSiteFrontEnd.PREVIEW_BASE.substring(0,
                ShSiteFrontEnd.PREVIEW_BASE.length() - 1) + previewPath(to);
        return ResponseEntity.status(302)
                .header(HttpHeaders.SET_COOKIE, cookie(row, now, request).toString())
                .location(URI.create(destination))
                .build();
    }

    /**
     * The path this link lands on, refusing anything that is not one.
     *
     * <p>A redirect that takes its target from the caller is an open redirect, and this one is
     * reached anonymously — so the destination is built rather than accepted: a leading slash is
     * required, a second one (which is a protocol-relative URL, and therefore another origin) is
     * not, and the result is always under {@code /preview}.</p>
     */
    private static String previewPath(String to) {
        if (to == null || to.isBlank() || !to.startsWith("/") || to.startsWith("//")) {
            return "/";
        }
        return to;
    }

    /**
     * The cookie, living exactly as long as the credential in it.
     *
     * <p>{@code HttpOnly} because the page it is sent to is content somebody authored and this
     * instance renders, so a script in it must not be able to read the credential back out.
     * {@code SameSite=Lax} because the link is followed as a top-level navigation, which Lax allows,
     * while a cross-site subrequest — the shape that would spend somebody's session for them — is
     * not. {@code Secure} follows the request, so it is set wherever it can be honoured and omitted
     * on the plain-HTTP localhost a developer runs, where setting it would make the feature silently
     * not work.</p>
     */
    private ResponseCookie cookie(ShApiToken row, Instant now, HttpServletRequest request) {
        Duration life = row.getExpiresAt() == null
                ? Duration.ofMinutes(15)
                : Duration.between(now, row.getExpiresAt());
        return ResponseCookie.from(ShApiTokenFilter.SHARE_COOKIE, row.getToken())
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(life.isNegative() ? Duration.ZERO : life)
                .build();
    }
}
