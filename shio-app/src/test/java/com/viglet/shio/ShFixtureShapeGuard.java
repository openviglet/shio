/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * SH324 — a test fixture may not persist a shape the product cannot produce.
 *
 * <h2>The defect this is written from</h2>
 *
 * <p>SH321's refusal fired on {@code ShTemplateGraphTest}, which built a "published page" as two
 * {@code post(...)} calls and therefore two ids — <b>two posts sharing one friendly URL</b>, which
 * is a defect rather than a published page. Publishing keeps the draft's id, so one page is one id
 * and one furl. The suite had been green for blocks against a fixture describing something
 * {@code ShPostUnifiedWriteService} cannot create, and the correct helper existed the whole time in
 * a sibling class. It is SH290's class one layer down: a hand-written artefact claiming to represent
 * something it does not.</p>
 *
 * <h2>Why this instrument and not the cheaper one</h2>
 *
 * <p>The cheap option was a source lint over fixtures — and it is not sound: it would have to
 * recognise "two calls that end up sharing a furl" across helper indirection and loop variables,
 * which is reading the wrong document about a fact that only exists after the insert.
 * {@code build.md}'s test for this decides it: <em>what would a broken implementation do differently
 * inside one JVM?</em> Here, it inserts a second row — observable, so the guard is runtime.</p>
 *
 * <p>It runs <b>once per test class</b>, as one query, and only when the class has a Spring context
 * with a {@code DataSource}: a class with no context has no fixture to be wrong about. Auto-detected
 * rather than declared on each class, because a guard you have to remember to add is absent from
 * exactly the class that needed it — which is how the original defect survived.</p>
 *
 * <h2>The escape hatch, which costs a sentence</h2>
 *
 * <p>Some tests create this shape <b>on purpose</b>: the SH202/SH263 duplicate-URL rules cannot be
 * tested without it. Those declare {@link BuildsDuplicateUrls} with a reason, which is
 * {@code build.md}'s rule — a guard with no exemption gets deleted the first time it is right about
 * something somebody meant.</p>
 *
 * @author Alexandre Oliveira
 */
public class ShFixtureShapeGuard implements BeforeAllCallback, AfterAllCallback {

    /**
     * This class builds two posts on one URL deliberately.
     *
     * @return why — a duplicate-URL rule cannot be tested without producing one
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface BuildsDuplicateUrls {
        String value();
    }

    /**
     * Two ids on one {@code (site, furl)} in the same database, which publishing cannot produce.
     *
     * <p>Grouped by site as well as furl: two sites legitimately have a {@code /home}, and a check
     * that missed that would fire on almost every fixture here.</p>
     *
     * <h3>Live rows only, since SH243</h3>
     *
     * <p>The trash was counted, and that made the guard's own claim false: a <b>trashed</b> post
     * keeps its {@code furl}, and SH243's whole subject is that this is a legitimate state the
     * product not only produces but now <em>defends</em> — the naming loop skips such a URL, and a
     * restore onto an occupied one is refused rather than duplicated. Only one of the two rows is
     * reachable, so it is not the shape this guard is about: two posts the resolver cannot tell
     * apart. Counting the sleeping one reported the defect's <em>fix</em> as the defect.</p>
     *
     * <p>Left as raw SQL rather than moved behind a repository for the reason the class note gives:
     * the fact only exists after the insert, and the guard has to be able to see rows every
     * Hibernate read is deliberately blind to.</p>
     */
    private static final String DUPLICATES = """
            select site_id, furl, count(distinct id) as ids
              from sh_post_unified
             where furl is not null and site_id is not null and deleted_date is null
             group by site_id, furl
            having count(distinct id) > 1""";

    /**
     * What the class INHERITED, so it is judged on what it created (SH239's confound).
     *
     * <p>The first version of this reported the whole database and named whichever class happened to
     * finish, which on the shared datasource meant two classes accused of one fixture — the same
     * shape as the benchmark that measured its neighbour. A before/after diff is the difference
     * between a guard and an accusation.</p>
     */
    private static final ExtensionContext.Namespace SPACE =
            ExtensionContext.Namespace.create(ShFixtureShapeGuard.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        DataSource dataSource = dataSourceOf(context);
        if (dataSource != null) {
            context.getStore(SPACE).put("inherited", new java.util.TreeSet<>(duplicates(dataSource)));
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        Class<?> testClass = context.getRequiredTestClass();
        if (AnnotationSupport.isAnnotated(testClass, BuildsDuplicateUrls.class)) {
            return;
        }
        DataSource dataSource = dataSourceOf(context);
        if (dataSource == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        java.util.Set<String> inherited = (java.util.Set<String>) context.getStore(SPACE)
                .getOrDefault("inherited", java.util.Set.class, java.util.Set.of());
        List<String> found = new ArrayList<>(duplicates(dataSource));
        found.removeAll(inherited);
        if (!found.isEmpty()) {
            throw new AssertionError("""
                    %s left a shape the product cannot produce: publishing keeps the draft's id, \
                    so one page is one id and one furl (SH321). A fixture that persists two ids on \
                    one URL is describing the duplicate-URL defect, not a published page — build \
                    the published row with the same id, or through the writer.

                    If the duplicate is the point (an SH202/SH263 rule cannot be tested without \
                    one), annotate the class @ShFixtureShapeGuard.BuildsDuplicateUrls("why").

                    %s""".formatted(testClass.getSimpleName(), String.join("\n", found)));
        }
    }

    /** Every {@code (site, furl)} two ids share, as stable strings, or empty when unreadable. */
    private static List<String> duplicates(DataSource dataSource) {
        List<String> found = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(DUPLICATES)) {
            while (rows.next()) {
                found.add(rows.getString("furl") + " is held by " + rows.getInt("ids")
                        + " ids in site " + rows.getString("site_id"));
            }
        } catch (RuntimeException | java.sql.SQLException e) {
            // A class whose schema is not this one, or a datasource already closed: not this
            // guard's business, and failing here would make an unrelated test red for a reason
            // that has nothing to do with it.
            return List.of();
        }
        return found;
    }

    /**
     * The context's {@code DataSource}, or {@code null} when the class has no Spring context.
     *
     * <p>Asking is the only way to find out: {@code getApplicationContext} throws for a plain JUnit
     * class, and this guard runs on every class because the alternative — declaring it — leaves it
     * off the one that needs it.</p>
     */
    private static DataSource dataSourceOf(ExtensionContext context) {
        try {
            ApplicationContext spring = SpringExtension.getApplicationContext(context);
            return spring.getBeanProvider(DataSource.class).getIfAvailable();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
