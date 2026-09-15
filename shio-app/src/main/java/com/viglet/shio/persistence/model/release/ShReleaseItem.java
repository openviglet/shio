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
 * One post in a release (SH993).
 *
 * <p>{@code postId} is a plain column for SH986's reason: a post's key is {@code (id, state)}, so an
 * association would have to name a state, and the row it named is the one publishing deletes.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Entity
@Table(name = "sh_release_item",
        uniqueConstraints = @UniqueConstraint(name = "UK_sh_release_item_post",
                columnNames = {"release_id", "post_id"}))
public class ShReleaseItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    private String id;

    @Column(name = "release_id", length = 36, nullable = false)
    private String releaseId;

    @Column(name = "post_id", length = 36, nullable = false)
    private String postId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    /** The SH986 revision the post was at when the release ran, and so where a rollback returns. */
    @Column(name = "version_before")
    private Integer versionBefore;

    @TenantId
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;
}
