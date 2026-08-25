/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.lint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH939 — two jars supplying one class, and which one wins is classpath order.
 *
 * <h2>What this caught, and why nothing else could</h2>
 *
 * <p>{@code ShUtils.isJSONValid} was the only consumer of {@code org.json} in the codebase, and
 * nothing called it. It kept {@code org.json:json} at compile scope, which put a second
 * {@code org.json.JSONObject} on the <b>test</b> classpath beside the 2013 Android stub that
 * {@code spring-boot-starter-test} brings in through {@code org.skyscreamer:jsonassert}. Spring
 * Boot printed the collision on every test boot and advised an exclusion; nothing failed.</p>
 *
 * <p>SH916's {@link ShDeclaredDependencyUseLintTest} could not see it and is right not to: it asks
 * whether a judged group is imported under {@code src/main/java}, and {@code org.json} <i>was</i>
 * imported. The dead thing was one level in — the importer — and "is this declaration used" and
 * "is its user reachable" are different questions, the second of which no lint here answers.</p>
 *
 * <h2>So this asserts the observable instead</h2>
 *
 * <p>A duplicated class is visible at runtime whatever caused it: an unused method holding a
 * dependency open, a starter changing what it brings, a transitive arriving under a new name. The
 * cause is unbounded and the symptom is one line of code, so the symptom is what is held.</p>
 *
 * <p>It reads the <b>test</b> classpath deliberately, which is where the ambiguity lives and the
 * one place a green suite is least able to report it: production carried a single provider
 * throughout, so a test touching {@code JSONObject} could have passed against an implementation
 * the product does not ship, and the suite would have said nothing either way.</p>
 *
 * <p>Named rather than swept. Scanning every class on the classpath for duplicates finds dozens
 * that are deliberate — shaded jars, multi-release layouts, the same class in an api and an impl
 * artifact — and a lint that reports those is one somebody switches off. An entry is added here
 * when a duplicate has actually cost something, with the reason beside it.</p>
 *
 * @author Alexandre Oliveira
 */
class ShDuplicateClasspathClassLintTest {

    /** Class resource path → why one provider of it is the only acceptable number. */
    private static final Map<String, String> SINGLE_PROVIDER = Map.of(
            "org/json/JSONObject.class",
            "SH939 — org.json:json was declared for one uncalled method, and collided with the 2013"
                    + " android-json stub that jsonassert brings. jsonassert is the only thing that"
                    + " should supply this class here; a second provider means org.json is back in"
                    + " the POM, or a starter changed what it drags in.");

    private static List<URL> providersOf(String resource) throws IOException {
        return Collections.list(Thread.currentThread().getContextClassLoader().getResources(resource));
    }

    @Test
    @DisplayName("SH939: no class named here is supplied by two jars at once")
    void noDuplicateProviders() throws IOException {
        for (Map.Entry<String, String> watched : SINGLE_PROVIDER.entrySet()) {
            List<URL> providers = providersOf(watched.getKey());

            assertThat(providers)
                    .as("SH939: %s is supplied by %d jars, so which implementation a test resolves is"
                            + " classpath order rather than a decision.%n  %s%n  Providers:%n    %s%n"
                            + "  Spring Boot prints this on every boot as \"Found multiple occurrences"
                            + " ... You may wish to exclude one of them\" — which is advice nothing"
                            + " enforces, so it is enforced here.",
                            watched.getKey(), providers.size(), watched.getValue(),
                            String.join("\n    ", providers.stream().map(URL::toString).toList()))
                    .hasSize(1);
        }
    }

    @Test
    @DisplayName("SH939: the reader is not vacuous — it resolves the classes it watches")
    void watchedClassesResolve() throws IOException {
        // A `getResources` call that finds NOTHING returns an empty list, and "zero providers" would
        // sail past a check for "not more than one" — the same shape as a scan that has lost its
        // directory reporting a clean tree. Every entry must be present exactly because the
        // assertion above only means something when the class is really on the classpath.
        for (String resource : SINGLE_PROVIDER.keySet()) {
            assertThat(providersOf(resource))
                    .as("SH939: %s is on no jar at all, so the duplicate check above is asserting"
                            + " nothing. Either the class was removed from the test classpath — drop"
                            + " the entry — or its resource path is misspelled here.", resource)
                    .isNotEmpty();
        }
    }
}
