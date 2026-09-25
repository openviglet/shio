/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.gate;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

/**
 * The checks a site runs before content reaches visitors (SH997).
 *
 * <p>Shio's instruments were all opt-in — the address-scoped verify, the render digest,
 * {@code shio audit} — so nothing stood between content and a visitor. The only gate that existed
 * asked <b>who</b> may publish (SH995); this is the other question, and it is declared per site rather
 * than configured per instance because what a site considers publishable is a fact about that site.</p>
 *
 * <p><b>Not a workflow engine.</b> A gate is an ordered list of checks with severities, evaluated
 * where the publish happens. An orchestrator inside Shio would be a third contract over the CDA and
 * the console, which P10 forbids, and the operator driving this instance already is one.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Entity
@Table(name = "sh_publish_gate",
        uniqueConstraints = @UniqueConstraint(name = "UK_sh_publish_gate_site_name",
                columnNames = {"site_id", "name"}))
public class ShPublishGate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    private String id;

    /** The file's name in {@code shio/gates/<name>.yaml}, unique within the site. */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "site_id", length = 36, nullable = false)
    private String siteId;

    /**
     * The ordered checks, as JSON: the CLI owns the YAML and the wire is JSON. Long text bound as a
     * string, never a large object (SH1099, on {@code ShPostUnified.data}).
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "definition", nullable = false)
    private String definition;

    /** Held rather than deleted: an operator turning a gate off should not lose what it said. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

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
