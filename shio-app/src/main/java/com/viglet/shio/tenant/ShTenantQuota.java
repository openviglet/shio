/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import java.util.List;

/**
 * SH1353 — a tenant's limits beside what it holds, one entry per resource.
 *
 * @param tenant    the tenant this describes, always the caller's own
 * @param plan      the plan name on the tenant row, {@code null} where the instance runs no plans
 * @param resources sites, posts, storage (bytes) and agent calls (today), in that order
 * @param title     SH1434 — the name the person gave the tenant ("Padaria Aurora"), for a client to
 *                  show; {@code tenant} stays the slug it sends as {@code X-Shio-Tenant}. Absent when
 *                  the tenant has none
 */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record ShTenantQuota(String tenant, String plan, List<Resource> resources, String title) {

    /** Before SH1434 carried the tenant's display name. */
    public ShTenantQuota(String tenant, String plan, List<Resource> resources) {
        this(tenant, plan, resources, null);
    }

    /**
     * One resource.
     *
     * @param key      the quota key: {@code sites}, {@code posts}, {@code storage}, {@code agentCalls}
     * @param limit    the plan's ceiling, {@code null} when unlimited
     * @param used     what the tenant holds now, or for {@code agentCalls} has used today
     * @param left     {@code limit - used} floored at zero, {@code null} when unlimited; zero means the
     *                 next write of this kind is refused with 402
     * @param resetsAt for a per-day resource only, when the window starts again (ISO-8601 UTC)
     */
    public record Resource(String key, Long limit, long used, Long left, String resetsAt) {
    }
}
