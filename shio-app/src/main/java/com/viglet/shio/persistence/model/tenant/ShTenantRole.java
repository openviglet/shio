/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.tenant;

/**
 * A user's role within a tenant ({@link ShTenantMembership}, Turing parity).
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public enum ShTenantRole {

    /** Created the tenant; full control. */
    OWNER,

    /** Manages the tenant's resources. */
    ADMIN,

    /** An ordinary member. */
    MEMBER
}
