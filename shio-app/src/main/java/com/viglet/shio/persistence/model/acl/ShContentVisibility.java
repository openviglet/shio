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
import java.util.Arrays;
import java.util.List;

import org.hibernate.annotations.TenantId;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * SH1285 — "the object {@code objectId}, and everything below it, is for members only".
 *
 * <p>A visitor's read, not an editor's: {@link ShContentAcl} governs authoring and never gated a
 * delivered page, and the owner decided the two stay separate. The presence of a row is the gate;
 * {@link #memberGroups} names the groups that may read, and none means any signed-in user. The
 * nearest row up the folder chain decides, as with the ACL.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Entity
@Table(name = "sh_content_visibility",
        indexes = @Index(name = "idx_content_visibility_object", columnList = "object_id"))
public class ShContentVisibility implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @TenantId
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;

    /** The folder or post id this gate applies to. */
    @Column(name = "object_id", nullable = false, length = 36)
    private String objectId;

    /** Group names, one per line; empty means any signed-in user. */
    @Column(name = "member_groups", length = 4000)
    private String memberGroups;

    /** {@link #memberGroups} as a sorted list, so two declarations compare by content. */
    public List<String> groups() {
        if (memberGroups == null || memberGroups.isBlank()) {
            return List.of();
        }
        return Arrays.stream(memberGroups.split("\n")).map(String::trim)
                .filter(name -> !name.isEmpty()).sorted().toList();
    }
}
