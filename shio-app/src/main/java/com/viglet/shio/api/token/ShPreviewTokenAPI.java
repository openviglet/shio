/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.token;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.cda.ShCdaProperties;
import com.viglet.shio.object.ShFolderSite;
import com.viglet.shio.render.ShSiteFrontEnd;
import com.viglet.shio.render.ShSiteFrontEnd.Destination;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.token.ShApiToken;
import com.viglet.shio.spring.security.auth.ShPreviewShare;
import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.token.ShApiTokenRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Short-lived preview tokens (SH13).
 *
 * <p>An authenticated console user mints an <em>expiring</em>, single-site
 * {@code PREVIEW}/{@code PREVIEW} {@link ShApiToken} for one post and is handed a
 * ready-to-open preview URL at the site front-end. The token rides the SH5
 * draft-preferred read path with no extra code; it stops authenticating once
 * {@code expiresAt} passes (enforced in {@code ShApiTokenFilter}). This is a
 * console surface — it lives under the {@code authenticated} rule, not the CDA
 * {@code Key} surface.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@RestController
@RequestMapping("/api/v2/preview-token")
@Tag(name = "Preview Token", description = "Short-lived draft-preview tokens (SH13)")
@Slf4j
public class ShPreviewTokenAPI {

    private final ShApiTokenRepository shApiTokenRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShCdaProperties shCdaProperties;
    private final Clock clock;

    public ShPreviewTokenAPI(ShApiTokenRepository shApiTokenRepository,
                             ShPostUnifiedRepository shPostUnifiedRepository,
                             ShCdaProperties shCdaProperties,
                             Clock clock) {
        this.shApiTokenRepository = shApiTokenRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shCdaProperties = shCdaProperties;
        this.clock = clock;
    }

    /** Request body: the post to preview + an optional requested lifetime. */
    public record MintRequest(String postId, Long ttlSeconds) {
    }

    @PostMapping
    public ResponseEntity<Object> mint(@RequestBody MintRequest body, Principal principal) {
        ShCdaProperties.Preview cfg = shCdaProperties.getPreview();
        if (!cfg.isEnabled()) {
            // Feature off — the console treats a 404 as "preview disabled" and stays quiet.
            return ResponseEntity.notFound().build();
        }
        if (body == null || body.postId() == null || body.postId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "postId is required"));
        }

        ShPostUnified post = draftPreferred(body.postId());
        if (post == null) {
            return ResponseEntity.notFound().build();
        }

        ShSite site = post.getShSite();
        if (site == null) {
            // SH128: walk to the root — the immediate folder carries shSite only when
            // it *is* the root, so a post in /blog/2026 fell through to a 404.
            site = ShFolderSite.of(post.getShFolder());
        }
        if (site == null) {
            return ResponseEntity.notFound().build();
        }
        // SH184 — a site with no front end is no longer a dead end. This used to answer
        // `400 site has no url`, which is the normal state of a site an agent just built
        // (SH170's site.upsert creates a blank one), so Preview Draft was unavailable at
        // exactly the moment there was something new to look at. SH183 removed the reason:
        // Shio renders the page itself. The two destinations are genuinely different, and
        // the difference is a *token* — an external front end needs one to turn its own
        // draft mode on at /api/preview, while /preview/** is already console-authenticated
        // and already draft-preferred. Minting a credential for the second would be issuing
        // a key nobody unlocks anything with.
        // SH1042 — and the last sentence above stopped being true, which is what this task is.
        // "A key nobody unlocks anything with" was right while `/preview/**` unlocked for no
        // credential at all; a SHARE token is now the one that does, so an internal front end mints
        // one too and this answers a link rather than a null. The paragraph stays because it is the
        // reasoning that was correct and then was not, and the reason it stopped being correct is
        // the feature.
        Destination destination = ShSiteFrontEnd.of(site);
        boolean share = !destination.isExternal();
        String base = destination.baseUrl();

        Instant now = Instant.now(clock);
        long ttl = clampTtl(body.ttlSeconds(), cfg);

        ShApiToken token = new ShApiToken();
        token.setTitle("preview:" + post.getId());
        token.setDescription(share
                ? "Expiring share link for Shio's own preview (SH1042)"
                : "Short-lived draft preview (SH13)");
        token.setToken(UUID.randomUUID().toString().replace("-", "").substring(0, 25));
        token.setOwner(principal != null ? principal.getName() : "system");
        token.setCreatedDate(now);
        token.setEnabled(true);
        // SHARE grants one role no rule outside the renderer accepts, which is what makes a
        // credential safe to put in a link somebody forwards; PREVIEW would have granted ROLE_CDA
        // and handed the recipient the whole delivery API for that site.
        token.setScope(share ? ShApiTokenScope.SHARE : ShApiTokenScope.PREVIEW);
        token.setEnv(ShApiTokenEnv.PREVIEW);
        token.setSiteIds(Set.of(site.getId())); // SH2: confine the token to this one site
        // SH1110 — and a share to the page it links to. Site-wide was more than the link promised: the
        // recipient of a link to one draft could read every draft on the site until it expired. An
        // external front end's PREVIEW token keeps the site bound; its drafts are read through the
        // delivery API, which has no page path to narrow to.
        token.setPathPrefix(share ? ShPreviewShare.normalize(post.getFurl()) : null);
        token.setExpiresAt(now.plusSeconds(ttl));
        token.setEphemeral(true);
        shApiTokenRepository.saveAndFlush(token);

        // Opportunistic purge so the table doesn't accumulate dead preview rows (no scheduler).
        shApiTokenRepository.deleteByEphemeralTrueAndExpiresAtBefore(now);

        String previewUrl = share
                ? shareUrl(destination, post.getFurl(), token.getToken())
                : buildPreviewUrl(base, post.getFurl(), token.getToken(), cfg);
        log.info("Minted preview token for post '{}' (site {}), expires {}", post.getId(), site.getId(),
                token.getExpiresAt());

        return ResponseEntity.ok(new ShPreviewTokenResponse(
                token.getToken(), previewUrl, token.getExpiresAt(), ttl));
    }

    /** Resolve the post draft-preferred: the {@code DRAFT} row, else {@code PUBLISHED}, else any. */
    private ShPostUnified draftPreferred(String postId) {
        List<ShPostUnified> rows = shPostUnifiedRepository.findById(postId);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        ShPostUnified published = null;
        for (ShPostUnified row : rows) {
            if (row.getState() == ShPostState.DRAFT) {
                return row;
            }
            if (row.getState() == ShPostState.PUBLISHED) {
                published = row;
            }
        }
        return published != null ? published : rows.get(0);
    }

    private static long clampTtl(Long requested, ShCdaProperties.Preview cfg) {
        long ttl = requested != null ? requested : cfg.getTtlSeconds();
        ttl = Math.min(ttl, cfg.getMaxTtlSeconds());
        return Math.max(ttl, 1);
    }

    /** {@code <base><path>?<tokenParam>=<token>&<urlParam>=<encoded furl>}. */
    /**
     * SH1042 — the link for Shio's own renderer: the exchange route, and where to land.
     *
     * <p>Relative, like the preview URL beside it, because this instance does not reliably know its
     * own public origin (a reverse proxy, a silo host) and a wrong absolute URL is worse than one a
     * caller composes. The destination is the ordinary preview path the credential is minted for —
     * the exchange refuses anything that is not one, so it is a hint rather than an authority.</p>
     */
    private static String shareUrl(Destination destination, String furl, String token) {
        String path = ShSiteFrontEnd.pageUrl(destination, furl)
                .substring(ShSiteFrontEnd.PREVIEW_BASE.length() - 1);
        return ShPreviewShareAPI.PATH + "/" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "?to=" + URLEncoder.encode(path, StandardCharsets.UTF_8);
    }

    private static String buildPreviewUrl(String base, String furl, String token, ShCdaProperties.Preview cfg) {
        String trimmedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String url = (furl == null || furl.isBlank()) ? "/" : furl;
        return trimmedBase + cfg.getPath()
                + "?" + cfg.getTokenParam() + "=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "&" + cfg.getUrlParam() + "=" + URLEncoder.encode(url, StandardCharsets.UTF_8);
    }
}
