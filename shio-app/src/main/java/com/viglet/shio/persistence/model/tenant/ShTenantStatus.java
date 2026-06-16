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
 * Lifecycle state of a {@link ShTenant} (SH22 follow-up / Turing parity).
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public enum ShTenantStatus {

    /** The tenant is live: its members can sign in and its content is served. */
    ACTIVE,

    /**
     * Authentication is blocked but data is retained (billing lapse, ops hold,
     * pre-delete). {@code ShTenantFilter} rejects a suspended tenant's members
     * with HTTP 403; platform admins still reach it.
     */
    SUSPENDED
}
