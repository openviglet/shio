/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A value resolved once per <b>read-only</b> transaction, or not cached at all (SH311, SH316).
 *
 * <h2>Why the mechanism is a class and no longer a private method</h2>
 *
 * <p>SH311 put this inside {@link ShPageRenderService} to stop a sweep re-resolving one site's
 * templates per page. SH316 measured the layer above it and found a second population with exactly
 * the same shape and the same lifetime — {@link ShTemplateGraph} re-reads <em>every row in the
 * site</em> for each question it answers, which is two queries per {@code impactOf} and a dozen per
 * {@code verify} rule. Copying twenty lines of {@code TransactionSynchronizationManager} into a
 * second service would have been the fork P10 forbids one layer down: two implementations of one
 * staleness rule, free to drift apart at the only point where drifting is unobservable.</p>
 *
 * <h2>The lifetime is the whole design, and it is a condition rather than a convention</h2>
 *
 * <p>A longer-lived cache has to be invalidated when a layout, a region or a theme is written
 * <em>and</em> when {@code site.postTypeLayout} changes — that last being an {@code ShSite} write no
 * post-level listener would see, which is the trap SH193 avoided by computing its graph instead of
 * storing it. So a value lives exactly as long as one read-only transaction: a {@code ?digest=true}
 * proof, a preview render, a {@code verify} pass. Staleness cannot outlive the call.</p>
 *
 * <p><b>A writing transaction gets no cache at all</b> — the condition on {@code isCurrentTransaction
 * ReadOnly}, not a comment — so a caller that renders or asks for a blast radius mid-write cannot
 * read what it has just replaced. {@code ShRenderCacheReachLintTest} (SH313) is the other half of
 * that: it fails the build when a render call site acquires a writing transaction, because the cost
 * of losing the cache is otherwise silent.</p>
 *
 * <h2>Using it</h2>
 *
 * <p>One instance per population, held in a {@code private static final} field: the instance
 * <em>is</em> the resource key, so two populations cannot collide on a key string, and a per-class
 * field means the binding does not depend on how many copies of a bean exist.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShReadOnlyTransactionCache {

    /** This instance is the transaction resource key — one per population, never per bean. */
    private final Object resourceKey = new Object();

    /**
     * The cached value for this key, computing it if the transaction has not seen it yet.
     *
     * <p>Outside a read-only transaction the loader simply runs, uncached, every time.</p>
     *
     * @param key    identifies the value within this population — include everything the loader
     *               reads, since two different questions sharing a key is the one failure this
     *               cannot detect
     * @param loader computes the value; a {@code null} result is returned and not cached, so a
     *               loader whose answer is "nothing" is asked again rather than remembered as a hit
     */
    public <T> T get(String key, Supplier<T> loader) {
        Map<String, Object> bound = boundMap();
        if (bound == null) {
            return loader.get();
        }
        Object held = bound.get(key);
        if (held != null) {
            @SuppressWarnings("unchecked")
            T typed = (T) held;
            return typed;
        }
        // Deliberately not `computeIfAbsent`: a loader that reaches this cache again — the graph
        // asking for the site's rows while assembling itself — would be a recursive update on the
        // same HashMap, which is a ConcurrentModificationException in one JDK and a corrupted table
        // in another. Get-then-put is correct under both, and there is no race to lose: a
        // transaction is one thread.
        T value = loader.get();
        if (value != null) {
            bound.put(key, value);
        }
        return value;
    }

    /**
     * The map bound to the current read-only transaction, or {@code null} when there is none.
     *
     * <p>It unbinds itself on completion; a resource left bound would leak into whatever the thread
     * does next, which is the one way a request-scoped cache turns into a shared one.</p>
     */
    private Map<String, Object> boundMap() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return null;
        }
        Object held = TransactionSynchronizationManager.getResource(resourceKey);
        if (held instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return typed;
        }
        Map<String, Object> fresh = new HashMap<>();
        TransactionSynchronizationManager.bindResource(resourceKey, fresh);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(resourceKey);
            }
        });
        return fresh;
    }
}
