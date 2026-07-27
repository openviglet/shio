/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.utils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An immutable copy of a map that <b>keeps its iteration order</b> — the replacement for
 * {@link Map#copyOf} anywhere the order is observable (SH136).
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@code Map.copyOf} reads as a defensive copy and is also a <em>re-ordering</em>: the
 * returned map iterates in an order derived from a salt that {@code ImmutableCollections}
 * initialises once per JVM. So the same data serializes to different bytes after a restart,
 * and every hash computed over that iteration changes with it.</p>
 *
 * <p>That is not a hypothetical. SH136 found four call sites in this codebase with the
 * identical shape: build a {@code TreeMap} or {@code LinkedHashMap} <em>deliberately, for
 * determinism</em>, then throw the order away in the return expression. One of them fed
 * SH101's render digest, whose entire promise is that the same page hashes the same.</p>
 *
 * <h2>Why a helper rather than a rule to remember</h2>
 *
 * <p>Because the failure is silent and, worse, <b>invisible to any test in one JVM</b>: the
 * salt is fixed for a process, so "call it twice and compare" passes no matter what. A
 * contract that can only be honoured by remembering belongs inside a method (§VIII.11), and
 * {@code ShByteStabilityLintTest} is what points at this one.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShStableMap {

    private ShStableMap() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * @param source any map; a {@code TreeMap} stays sorted, a {@code LinkedHashMap} stays
     *               in insertion order
     * @return an unmodifiable view over a copy that iterates exactly as {@code source} did
     */
    public static <K, V> Map<K, V> copyOf(Map<K, V> source) {
        return source == null || source.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
