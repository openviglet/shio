/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.persistence.model.token;

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
    @Column(name = "TENANT_ID", updatable = false)
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

    @Column(name = "created_date")
    private Instant createdDate;

    @Column(name = "enabled")
    private boolean enabled = true;
}
