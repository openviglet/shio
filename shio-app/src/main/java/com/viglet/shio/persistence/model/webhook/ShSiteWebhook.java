/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.persistence.model.webhook;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * Per-site outbound webhook subscription (SH12). A target URL + shared HMAC
 * secret + event filter; the publish pipeline POSTs a signed payload here on
 * publish / unpublish / delete (the backbone for incremental static
 * regeneration — SH9).
 *
 * <p>Mirrors {@link com.viglet.shio.persistence.model.token.ShApiToken}:
 * a standalone {@code @Entity} with a {@code @TenantId} and a thin CRUD
 * controller, no service layer.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Getter
@Setter
@Entity
@Table(name = "site_webhook")
public class ShSiteWebhook implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @TenantId
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;

    /**
     * The site this subscription listens to. {@code null}/blank means every
     * site (a catch-all subscription).
     */
    @Column(name = "site_id")
    private String siteId;

    @Column(name = "name", length = 100)
    private String name;

    /** Target endpoint the signed POST is sent to. */
    @Column(name = "target_url", length = 1024)
    private String url;

    /**
     * Shared HMAC-SHA256 secret. Must equal {@code SHIO_WEBHOOK_SECRET} on the
     * receiver (e.g. {@code create-shio-app}'s {@code /api/revalidate}).
     */
    @Column(name = "secret")
    private String secret;

    /**
     * Which transitions to fire on. An <b>empty</b> set means all of them
     * (the common case). Fetched eagerly because the dispatcher reads it
     * outside an open session, off the request thread.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "site_webhook_event", joinColumns = @JoinColumn(name = "site_webhook_id"))
    @Column(name = "event_type", length = 16)
    @Enumerated(EnumType.STRING)
    private Set<ShWebhookEventType> events = new HashSet<>();

    @Column(name = "enabled")
    private boolean enabled = true;

    @Column(name = "created_date")
    private Instant createdDate;

    @Column(name = "owner")
    private String owner;

    /** True when this subscription should fire for the given site + event. */
    public boolean matches(String eventSiteId, ShWebhookEventType type) {
        boolean siteOk = siteId == null || siteId.isBlank() || siteId.equals(eventSiteId);
        boolean eventOk = events == null || events.isEmpty() || events.contains(type);
        return enabled && siteOk && eventOk;
    }
}
