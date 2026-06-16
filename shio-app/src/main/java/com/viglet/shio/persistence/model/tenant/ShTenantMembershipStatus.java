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
 * State of a {@link ShTenantMembership} (Turing parity). Only {@code ACTIVE}
 * grants access; {@code ShTenantFilter} rejects everything else with 403.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public enum ShTenantMembershipStatus {

    /** The user can access the tenant. */
    ACTIVE,

    /** Invited but not yet accepted — no access. */
    INVITED,

    /** Access revoked — no access. */
    SUSPENDED
}
