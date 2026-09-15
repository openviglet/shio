/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.release;

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
 * A named set of posts in one site that goes live as one unit (SH993).
 *
 * <p>One batch call is atomic, and nothing persisted a set across calls, so a launch of forty related
 * pages went live as forty separate events in sweep order. A release is the unit a launch lacked: it
 * is declared, filled, read back and run, and its posts publish in one transaction.</p>
 *
 * <p>{@code siteId} is a plain column rather than an association, for the invariant {@code agents.md}
 * states: an {@code ShSite} is an {@code ShObject} subclass, and a JPQL path past its {@code .id}
 * fails at query-prepare time under the tenant filter. The service resolves the name.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Entity
@Table(name = "sh_release",
        // SH729 — declared here as well as in the changelog: the unit suite builds its schema from
        // this metadata, so a constraint that lived only in Liquibase would be one no test had seen.
        uniqueConstraints = @UniqueConstraint(name = "UK_sh_release_site_name",
                columnNames = {"site_id", "name"}))
public class ShRelease implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Where a release is in its life. Stored as the name, never the ordinal. */
    public enum State {
        /** Being assembled: posts can be added and removed. */
        PLANNED,
        /** Due to run at {@link ShRelease#getScheduledAt()}. */
        SCHEDULED,
        /** Ran: every post in it went live together. */
        PUBLISHED,
        /** Ran and was undone as a unit. */
        ROLLED_BACK
    }

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    private String id;

    /** The last segment of {@code release:<site>/<name>}, unique within the site. */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "site_id", length = 36, nullable = false)
    private String siteId;

    @Column(name = "state", length = 16, nullable = false)
    private String state;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    /** SH993 — when it was taken out of the way; still addressable, just not listed. */
    @Column(name = "archived_at")
    private Instant archivedAt;

    /** Who last changed it. */
    @Column(name = "actor", length = 255)
    private String actor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    @TenantId
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;
}
