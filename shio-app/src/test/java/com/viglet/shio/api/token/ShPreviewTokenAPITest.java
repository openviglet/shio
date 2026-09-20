/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.viglet.shio.api.cda.ShCdaProperties;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.token.ShApiToken;
import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.token.ShApiTokenRepository;

/**
 * Unit coverage for the SH13 short-lived preview-token minter
 * {@link ShPreviewTokenAPI}: the minted token's shape, the preview URL it builds,
 * ttl clamping, the disabled / no-url guards, and the opportunistic purge.
 *
 * @author Alexandre Oliveira
 */
@ExtendWith(MockitoExtension.class)
class ShPreviewTokenAPITest {

    /** A fixed reference instant so expiry semantics are deterministic (no system clock). */
    private static final Instant FIXED_NOW = Instant.parse("2026-06-01T00:00:00Z");

    @Mock
    private ShApiTokenRepository shApiTokenRepository;
    @Mock
    private ShPostUnifiedRepository shPostUnifiedRepository;

    private ShCdaProperties properties() {
        return new ShCdaProperties(); // preview defaults: enabled, ttl 900, max 3600, /api/preview
    }

    private ShPreviewTokenAPI api(ShCdaProperties props) {
        return new ShPreviewTokenAPI(shApiTokenRepository, shPostUnifiedRepository, props,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    private ShPostUnified post(String id, ShPostState state, String furl, String siteId, String siteUrl) {
        ShSite site = new ShSite();
        site.setId(siteId);
        site.setUrl(siteUrl);
        ShPostUnified p = new ShPostUnified();
        p.setId(id);
        p.setState(state);
        p.setFurl(furl);
        p.setShSite(site);
        return p;
    }

    private ShApiToken capturedToken() {
        ArgumentCaptor<ShApiToken> captor = ArgumentCaptor.forClass(ShApiToken.class);
        verify(shApiTokenRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    void mintBuildsPreviewTokenAndUrl() {
        when(shPostUnifiedRepository.findById("post-1"))
                .thenReturn(List.of(post("post-1", ShPostState.DRAFT, "/blog/hello", "siteA", "https://front.example.com")));

        ResponseEntity<?> response = api(properties())
                .mint(new ShPreviewTokenAPI.MintRequest("post-1", null), () -> "alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ShPreviewTokenResponse body = (ShPreviewTokenResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.token()).hasSize(25);
        assertThat(body.ttlSeconds()).isEqualTo(900);
        assertThat(body.expiresAt()).isEqualTo(FIXED_NOW.plusSeconds(900));
        // <base><path>?token=<token>&url=<encoded furl>
        assertThat(body.previewUrl())
                .isEqualTo("https://front.example.com/api/preview?token=" + body.token() + "&url=%2Fblog%2Fhello");

        ShApiToken minted = capturedToken();
        assertThat(minted.getScope()).isEqualTo(ShApiTokenScope.PREVIEW);
        assertThat(minted.getEnv()).isEqualTo(ShApiTokenEnv.PREVIEW);
        assertThat(minted.isEphemeral()).isTrue();
        assertThat(minted.isEnabled()).isTrue();
        assertThat(minted.getSiteIds()).containsExactly("siteA");
        assertThat(minted.getExpiresAt()).isNotNull();
        assertThat(minted.getOwner()).isEqualTo("alice");
        // Opportunistic purge of expired ephemeral rows runs on every mint.
        verify(shApiTokenRepository).deleteByEphemeralTrueAndExpiresAtBefore(any(Instant.class));
    }

    @Test
    void requestedTtlIsClampedToMax() {
        when(shPostUnifiedRepository.findById("post-1"))
                .thenReturn(List.of(post("post-1", ShPostState.DRAFT, "/x", "siteA", "https://f.example.com")));

        ResponseEntity<?> response = api(properties())
                .mint(new ShPreviewTokenAPI.MintRequest("post-1", 99999L), () -> "alice");

        ShPreviewTokenResponse body = (ShPreviewTokenResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.ttlSeconds()).isEqualTo(3600); // max-ttl-seconds default
    }

    @Test
    @DisplayName("SH1042: no front end means Shio renders it, behind an expiring share link")
    void blankSiteUrlFallsBackToShiosOwnPreview() {
        when(shPostUnifiedRepository.findById("post-1"))
                .thenReturn(List.of(post("post-1", ShPostState.DRAFT, "/x", "siteA", "")));

        ResponseEntity<?> response = api(properties())
                .mint(new ShPreviewTokenAPI.MintRequest("post-1", null), () -> "alice");

        assertThat(response.getStatusCode())
                .as("""
                        this answered `400 site has no url` until SH184, and an empty url is the \
                        normal state of a site an agent just built (SH170's site.upsert creates a \
                        blank one) — so Preview Draft was unavailable at exactly the moment there \
                        was something new to look at. SH183 removed the reason.""")
                .isEqualTo(HttpStatus.OK);

        ShPreviewTokenResponse body = (ShPreviewTokenResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.token())
                .as("""
                        SH184 answered a NULL token here, and the reasoning was right at the time: \
                        "/preview/** is already console-authenticated, so a credential for it \
                        would unlock nothing". SH1042 is the change that made the second half \
                        false — a SHARE token is now the one thing that unlocks it — so the \
                        branch that minted nothing is the branch that left a draft unshowable to \
                        anyone without a console login.""")
                .isNotNull();
        assertThat(body.previewUrl())
                .as("the exchange route, not the preview path: the credential leaves the URL on "
                        + "the first hop, so what a recipient forwards is an ordinary page address")
                .startsWith("/api/v2/preview-share/")
                .contains("to=%2FsiteA%2Fx");
        assertThat(body.expiresAt()).isNotNull();

        ArgumentCaptor<ShApiToken> written = ArgumentCaptor.forClass(ShApiToken.class);
        verify(shApiTokenRepository).saveAndFlush(written.capture());
        assertThat(written.getValue().getScope())
                .as("""
                        SHARE and not PREVIEW, and the difference is the whole of what makes a \
                        link safe to forward: PREVIEW grants ROLE_CDA + ROLE_CDA_PREVIEW, so a \
                        recipient who lifted the value out of the cookie into a `Key` header would \
                        read that site's entire delivery API, drafts included.""")
                .isEqualTo(ShApiTokenScope.SHARE);
        assertThat(written.getValue().getSiteIds()).containsExactly("siteA");
        assertThat(written.getValue().isEphemeral()).isTrue();
    }

    @Test
    void missingPostIsNotFound() {
        when(shPostUnifiedRepository.findById("nope")).thenReturn(List.of());

        ResponseEntity<?> response = api(properties())
                .mint(new ShPreviewTokenAPI.MintRequest("nope", null), () -> "alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void disabledFeatureIsNotFound() {
        ShCdaProperties props = properties();
        props.getPreview().setEnabled(false);

        ResponseEntity<?> response = api(props)
                .mint(new ShPreviewTokenAPI.MintRequest("post-1", null), () -> "alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void publishedFurlUsedWhenNoDraftRow() {
        when(shPostUnifiedRepository.findById("post-1"))
                .thenReturn(List.of(post("post-1", ShPostState.PUBLISHED, "/only-published", "siteA", "https://f.example.com")));

        ResponseEntity<?> response = api(properties())
                .mint(new ShPreviewTokenAPI.MintRequest("post-1", null), () -> "alice");

        ShPreviewTokenResponse body = (ShPreviewTokenResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.previewUrl()).endsWith("&url=%2Fonly-published");
    }
}
