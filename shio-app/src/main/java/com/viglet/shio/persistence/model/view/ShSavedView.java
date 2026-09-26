/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.view;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import org.hibernate.annotations.TenantId;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

/**
 * A named filtered list, which is a named URL (SH1024).
 *
 * <p>Every console list keeps its state in its address (SH1023), so what a curator saves is the
 * path and the query string under a name, and opening it is navigating there. Nothing here
 * interprets the query: the list that reads it is the one that validates it, as it does for a link
 * somebody pasted.</p>
 *
 * <p>Per person, and {@code shared} offers it to everyone in the tenant. A console preference with
 * no agent leg, like the dock's: an agent states its query every time, so a name for one is a
 * curator's convenience. It is classified that way in {@code surfaces.json}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Entity
@Table(name = "sh_saved_view",
        // Declared here as well as in the changelog: the unit suite builds H2 from this metadata
        // with Liquibase off (SH719), so a constraint only the changelog knows is one no test sees.
        uniqueConstraints = @UniqueConstraint(name = "UK_sh_saved_view_owner_list_name",
                columnNames = {"owner", "list_key", "name", "tenant_id"}))
public class ShSavedView implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    private String id;

    /** Who saved it, as the console resolves the signed-in user. */
    @Column(name = "owner", length = 255, nullable = false)
    private String owner;

    /** Which list it belongs to: {@code browser}, {@code search}, {@code trash}, {@code review}. */
    @Column(name = "list_key", length = 64, nullable = false)
    private String listKey;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /** The console path, e.g. {@code /console/content/folder/<id>}. */
    @Column(name = "path", length = 512, nullable = false)
    private String path;

    /** The query string, without its {@code ?}; empty is the unfiltered list. */
    @Column(name = "query", length = 2048)
    private String query;

    /** Offered to everyone in the tenant, not only its owner. */
    @Column(name = "shared", nullable = false)
    private boolean shared;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @TenantId
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;
}
