/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.api.gate.ShPublishGateService;
import com.viglet.shio.api.release.ShReleaseRunner;
import com.viglet.shio.api.release.ShReleaseService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;
import com.viglet.shio.property.ShAppProperties;

/**
 * SH1424 — the scheduler's timer is apart from its work, and the test suite runs without the timer.
 *
 * <p>{@code ShScheduledGateTest} once saw two incidents for one stopped release, 0.5 ms apart: its
 * own {@code sweep()} and a cached context's background sweep over the fork's shared database. The
 * timer is now {@code shio.schedule.background}, separate from {@code enabled}, so a test can switch
 * the timer off and still drive the sweep itself.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShScheduleBackgroundTest {

    @Test
    @DisplayName("SH1424: with the timer off, the scheduled entry sweeps nothing and a direct call still sweeps")
    void theTimerIsApartFromTheWork() {
        ShPostUnifiedWriteService writes = mock(ShPostUnifiedWriteService.class);
        when(writes.findScheduledPublishDue(any())).thenReturn(List.of());
        ShScheduleProperties properties = new ShScheduleProperties();
        properties.setBackground(false);
        ShAppProperties app = mock(ShAppProperties.class);
        when(app.isMultiTenant()).thenReturn(false);
        ShPostScheduler scheduler = new ShPostScheduler(writes, mock(ShReleaseService.class),
                mock(ShReleaseRunner.class), properties, mock(ShAgentIncidentLog.class),
                mock(ShTenantRepository.class), app, mock(ShPublishGateService.class));

        scheduler.scheduledSweep();
        verify(writes, never()).findScheduledPublishDue(any());

        scheduler.sweep();
        verify(writes).findScheduledPublishDue(any());
    }

    @Test
    @DisplayName("SH1424: the test classpath runs every context without the scheduler's timer")
    void theSuiteHasNoBackgroundSweeper() throws Exception {
        Properties test = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            test.load(in);
        }
        assertThat(test.getProperty("shio.schedule.background"))
                .as("SH1424: a cached context's timer sweeps the database every other test in its fork reads")
                .isEqualTo("false");
        assertThat(new ShScheduleProperties().isBackground())
                .as("SH1424: production lost its timer").isTrue();
    }
}
