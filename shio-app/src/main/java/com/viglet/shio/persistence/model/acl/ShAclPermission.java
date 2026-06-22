/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.acl;

/**
 * The access level a content ACL entry grants (SH54). {@code WRITE} implies
 * {@code READ}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public enum ShAclPermission {
    READ,
    WRITE;

    /** True when holding this permission satisfies the {@code required} one (WRITE ⊇ READ). */
    public boolean satisfies(ShAclPermission required) {
        return this == WRITE || this == required;
    }
}
