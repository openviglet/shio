/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.acl;

import java.io.Serial;
import java.io.Serializable;

import org.hibernate.annotations.TenantId;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * A single content-access grant (SH54): "{@code principal} may {@code permission}
 * the object {@code objectId}". The object is a folder, site or post id (no FK —
 * folders/sites live in {@code ShObject}, posts in {@code sh_post_unified}, two
 * tables). Absence of any ACL up an object's folder chain means open access
 * (default-allow); the presence of any entry at the nearest level switches that
 * level to deny-by-default for non-matching principals.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Entity
@Table(name = "sh_content_acl",
        indexes = @Index(name = "idx_content_acl_object", columnList = "object_id"))
public class ShContentAcl implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @TenantId
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;

    /** The folder / site / post id this grant applies to. */
    @Column(name = "object_id", nullable = false, length = 36)
    private String objectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "principal_type", nullable = false, length = 16)
    private ShAclPrincipalType principalType;

    /** Username / group name / role name, per {@link #principalType}. */
    @Column(name = "principal_name", nullable = false, length = 255)
    private String principalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 16)
    private ShAclPermission permission;
}
