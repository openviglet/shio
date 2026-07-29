/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.onstartup.widget;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.render.ShRenderPostTypes;
import com.viglet.shio.widget.ShSystemWidget;

/**
 * SH325 — a widget added by a later release must reach an instance that already exists.
 *
 * <h2>Why this class boots a database of its own</h2>
 *
 * <p>It <b>deletes a row from the widget catalogue</b>, which is shared, global and code-owned. On the
 * shared test datasource that is the hazard {@code agents.md} records from SH289: a test that mutated
 * a post-type cascade-deleted {@code Text} and {@code Ace Editor - Javascript} for the whole JVM, and
 * it surfaced as {@code Widget 'Text' is not seeded} thrown by production code several steps from the
 * cause. So the isolation here is not tidiness — it is the difference between this test proving
 * something and this test breaking five others. SH315's rule, one package along from where the lint
 * enforces it.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-widget-seed;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShWidgetSeedTest {

    @Autowired
    private ShWidgetOnStartup shWidgetOnStartup;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private ShRenderPostTypes shRenderPostTypes;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("every widget this build declares exists on a booted instance")
    void theCatalogueIsComplete() {
        // The assertion that would have caught SH252's gap the day it was added, had the seed been
        // reached at all: the build declares a widget, so the instance has it.
        List<String> present = shWidgetRepository.findAll().stream().map(ShWidget::getName).toList();

        assertThat(present)
                .as("a widget is a Java class plus a React component, so the catalogue is closed "
                        + "and code-owned — a name this build declares and the instance lacks is a "
                        + "field no post-type can reference")
                .containsAll(ShWidgetOnStartup.expectedNames());
        assertThat(present)
                .as("SH252's row, whose absence made SH183's render model uncreatable")
                .contains(ShSystemWidget.ACE_CSS);
    }

    @Test
    @DisplayName("SH325: a missing widget is re-created, and the rest are left alone")
    void aMissingWidgetIsRecreated() {
        // `Ace Editor - CSS` is the right row to remove: SH252 declares it on `Theme`'s CSS field,
        // and the render post-types are created on demand rather than seeded, so nothing in a
        // freshly booted instance references it. Deleting one that a ShPostTypeAttr points at
        // would fail on the FK instead of testing the seed.
        ShWidget seeded = shWidgetRepository.findFirstByName(ShSystemWidget.ACE_CSS);
        assertThat(seeded).as("precondition: the row this test removes must be there").isNotNull();
        int before = shWidgetRepository.findAll().size();
        // The repository's delete is a @Modifying @Query, so it needs a transaction of its own —
        // this test is deliberately not @Transactional, because the seed it is checking runs in
        // ShOnStartup's and a rolled-back fixture would prove nothing about what a boot leaves.
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> shWidgetRepository.delete(seeded.getId()));
        assertThat(shWidgetRepository.findFirstByName(ShSystemWidget.ACE_CSS)).isNull();

        List<String> created = shWidgetOnStartup.createDefaultRows();

        assertThat(created)
                .as("""
                        per-widget find-or-create, which is SH152's provision-never-upsert rule at \
                        the right granularity: the old guard was `is the whole catalogue empty`, so \
                        a widget added by a later release only ever existed on a brand-new \
                        database — and the return value names what an upgrade added rather than \
                        leaving it to the log.""")
                .containsExactly(ShSystemWidget.ACE_CSS);
        assertThat(shWidgetRepository.findFirstByName(ShSystemWidget.ACE_CSS)).isNotNull();
        assertThat(shWidgetRepository.findAll())
                .as("nothing else moved — a second row for a widget that already exists is how a "
                        + "seed turns findByName into IncorrectResultSizeDataAccessException")
                .hasSize(before);

        assertThat(shWidgetOnStartup.createDefaultRows())
                .as("idempotent, which is what makes it safe to run outside ShOnStartup's "
                        + "first-time gate — and running outside that gate is the half of SH325 "
                        + "the roadmap entry did not mention")
                .isEmpty();
    }

    @Test
    @DisplayName("SH325: an unseeded widget is a teaching 409, not a 500")
    void anUnseededWidgetTeaches() {
        // The branch is nearly unreachable now, which is precisely the state SH189 warns about:
        // nobody expected it, so it answered with the least useful thing available while knowing
        // exactly which widget was missing.
        var problem = new ShRenderPostTypes.UnseededWidgetException(ShSystemWidget.ACE_CSS)
                .problem().toProblemDetail();

        assertThat(problem.getStatus())
                .as("409 rather than 500 because the caller can act: a restart now seeds what is "
                        + "absent, and POST /api/v2/widget creates it directly")
                .isEqualTo(409);
        assertThat(problem.getDetail())
                .as("naming the widget is the whole point — 'not seeded' alone sends the reader "
                        + "hunting through a field table")
                .contains(ShSystemWidget.ACE_CSS);
        assertThat(problem.getProperties().get("fix").toString())
                .contains("Restart")
                .contains("/api/v2/widget");
        assertThat(shRenderPostTypes).as("the owner of the refusal is wired").isNotNull();
    }
}
