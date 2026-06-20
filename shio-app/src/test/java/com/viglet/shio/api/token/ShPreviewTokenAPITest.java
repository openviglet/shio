/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
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
    void blankSiteUrlIsBadRequest() {
        when(shPostUnifiedRepository.findById("post-1"))
                .thenReturn(List.of(post("post-1", ShPostState.DRAFT, "/x", "siteA", "")));

        ResponseEntity<?> response = api(properties())
                .mint(new ShPreviewTokenAPI.MintRequest("post-1", null), () -> "alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
