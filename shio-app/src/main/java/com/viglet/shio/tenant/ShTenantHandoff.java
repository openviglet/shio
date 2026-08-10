/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

/**
 * Carrying the current tenant across a thread boundary (SH612).
 *
 * <h2>The defect this exists for</h2>
 *
 * <p>{@link ShTenantContext} is a thread-local and {@code @TenantId} is resolved from it at the
 * moment a Hibernate session opens. Two recorders write from a pool thread that never had one set
 * — the webhook dispatcher inside {@code dispatchAsync(...).whenComplete(...)} and the Turing
 * listener inside {@code executor.execute(...)}, both running past {@code AFTER_COMMIT}, where
 * there is no request, no session and no tenant. Their {@code REQUIRES_NEW} write was therefore
 * stamped with the default discriminator, {@code shio}.</p>
 *
 * <p><b>The consequence is quiet, which is why it survived SH22.</b> Until SH204 an incident row
 * held a URL, a message and a timestamp, so being filed under the wrong tenant made it invisible
 * rather than wrong. SH204 added a site id: a misfiled row now carries an identifier belonging to
 * a tenant that cannot see it, matches no scoped read and resolves to no name in an unrestricted
 * one. Nothing leaks — but the operator whose site actually failed is the one person who cannot
 * see the failure, and the webhook that never arrived is exactly the incident a tenant is paying
 * for.</p>
 *
 * <h2>Capture on the committing thread, restore around the write</h2>
 *
 * <p>The same shape {@code siteId} and {@code what} already use at both call sites: a plain value
 * read while the tenant is still there, carried by the lambda, applied on the far side. Nothing
 * here queries and nothing lazily initialises, which is the property that makes a value safe to
 * cross this boundary at all.</p>
 *
 * <h2>The restore is not housekeeping — it is the other half of the fix</h2>
 *
 * <p>A pool thread is <em>reused</em>. Setting the tenant and walking away would leave it bound
 * for whatever task the pool hands that thread next, and that task's write would then be stamped
 * with a tenant it never had — a foreign row created rather than a misfiled one, which is
 * strictly worse than the defect being repaired. So the previous value is saved and put back in a
 * {@code finally}, and an absent capture clears rather than sets: {@link ShTenantResolver} falls
 * back to {@link ShTenantContext#DEFAULT_TENANT} when nothing is bound, so "no tenant" has a
 * meaning and it is not "keep whatever the last task left".</p>
 *
 * <p>Two entry points rather than one, because the two boundaries have different shapes and
 * neither should be bent to fit the other: a {@link Runnable} handed to an executor is wrapped
 * where it is submitted, and a completion callback — whose {@code BiConsumer} type cannot be
 * inferred through a wrapper — reads the tenant at the boundary and names it inside.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShTenantHandoff {

    private ShTenantHandoff() {
    }

    /**
     * Wraps a task with the tenant bound <b>now</b>, on the calling thread.
     *
     * <p>Read at wrap time, not at run time — that is the whole point. Calling this from the pool
     * thread itself would capture exactly the nothing the task is trying to avoid.</p>
     */
    public static Runnable capture(Runnable task) {
        return runnable(ShTenantContext.getTenantId(), task);
    }

    /**
     * The same, for a boundary that cannot take a wrapped {@link Runnable}.
     *
     * @param tenant read on the committing thread, typically
     *               {@code ShTenantContext.getTenantId()} in the enclosing method
     */
    public static void runAs(String tenant, Runnable body) {
        runnable(tenant, body).run();
    }

    private static Runnable runnable(String tenant, Runnable body) {
        return () -> {
            String held = ShTenantContext.getTenantId();
            apply(tenant);
            try {
                body.run();
            } finally {
                apply(held);
            }
        };
    }

    private static void apply(String tenant) {
        if (tenant == null || tenant.isBlank()) {
            ShTenantContext.clear();
        } else {
            ShTenantContext.setTenantId(tenant);
        }
    }
}
