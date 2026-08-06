/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.persistence.model.token;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * CDA (Content Delivery API) Token entity.
 * Read-only tokens for accessing published content.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Getter
@Setter
@Entity
@Table(name = "api_token")
public class ShApiToken implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @TenantId
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;

    @Column(name = "title", length = 100, nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "token")
    private String token;

    @Column(name = "owner")
    private String owner;

    /**
     * Access scope of this token (SH2). Defaults to {@code READ}. A {@code null}
     * value (pre-SH2 rows) is treated as {@code READ} by the filter.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", length = 16)
    private ShApiTokenScope scope = ShApiTokenScope.READ;

    /**
     * Optional per-site allow-list (SH2). Empty means the token may read every
     * site; non-empty restricts it to exactly these site ids. Fetched eagerly
     * because {@code ShApiTokenFilter} reads it outside an open session.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_token_site", joinColumns = @JoinColumn(name = "api_token_id"))
    @Column(name = "site_id")
    private Set<String> siteIds = new HashSet<>();

    /**
     * Target content environment of this token (SH5). Defaults to {@code PROD}
     * (published-only). A {@code PREVIEW} token reads draft-preferred. A
     * {@code null} value (pre-SH5 rows) is treated as {@code PROD}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "environment", length = 16)
    private ShApiTokenEnv env = ShApiTokenEnv.PROD;

    @Column(name = "created_date")
    private Instant createdDate;

    @Column(name = "enabled")
    private boolean enabled = true;

    /**
     * Optional expiry (SH13). A {@code null} value (every durable token) never
     * expires; a non-null value is enforced by {@code ShApiTokenFilter}, which
     * stops authenticating the token once {@code expiresAt} is in the past. Set
     * on short-lived preview tokens minted by {@code ShPreviewTokenAPI}.
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * Whether this token was minted by the SH13 preview flow. Ephemeral tokens
     * are hidden from the token-management list and are the only rows the
     * opportunistic expiry purge deletes.
     */
    @Column(name = "ephemeral")
    private boolean ephemeral = false;

    /**
     * Whether this credential may publish (SH107). Publishing is the one agent write
     * that changes what the public sees, so it is off unless the token says otherwise —
     * an agent that writes drafts and cannot make them live is one a human can review
     * before anyone else notices, which is what P7 asks for.
     *
     * <p>It gates the <b>agent surface</b> (batch/apply publish ops, {@code shio_publish}).
     * A console-authenticated caller has no token and is unaffected; the SH10 CDA write
     * path is the Universal Editor's, where a human is already the one clicking.</p>
     *
     * <p><b>Nullable on purpose, and read through {@link #isMayPublish()} (SH188).</b>
     * {@code v2026.3.11} added the column with no default and no backfill, stating the
     * intent that it "read as {@code false} when absent" — which a primitive
     * {@code boolean} cannot do: Hibernate throws {@code IllegalArgumentException}
     * hydrating NULL into one, so every install that held a token before that changeset
     * failed on {@code GET /api/v2/api-token} and on minting a preview token. The
     * wrapper is what makes the absent case expressible; {@code v2026.3.14} repairs the
     * rows and adds the default {@code ephemeral} has had since SH13, so a fresh install
     * never stores NULL again. Both halves are kept: the migration fixes what is on disk,
     * the null read means a row arriving NULL from anywhere else is still only
     * <em>not granted</em> rather than a 500.</p>
     */
    @Getter(AccessLevel.NONE)
    @Column(name = "may_publish")
    private Boolean mayPublish = Boolean.FALSE;

    /**
     * @return whether this credential may publish — {@code false} when the column is
     *         NULL, which is what "not granted" looks like on a pre-SH107 row.
     */
    public boolean isMayPublish() {
        return Boolean.TRUE.equals(mayPublish);
    }

    /** @return {@code true} when this token carries an expiry that has passed. */
    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }
}
