/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.api.review.dto.ShReviewSession;

/**
 * SH1428 — what a finished run's notice says, and when it is due.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShReviewNotifierTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    @Test
    @DisplayName("SH1428: the notice says what the queue says, in pages, with a link that opens the run")
    void theNoticeNamesPagesAndLinksTheRun() {
        ShReviewSession run = run(NOW.minus(Duration.ofMinutes(15)), 4, Map.of("CREATE", 4));

        Map<String, String> facts = ShReviewNotifier.facts(run, "https://shio.viglet.cloud/");

        assertThat(facts.get("summary")).as("SH1428").isEqualTo("4 new pages in quilhara wait for review");
        assertThat(facts.get("reviewUrl")).as("SH1428: the link opens that run in the queue")
                .isEqualTo("https://shio.viglet.cloud/bento/admin/review?session=sess-7f3c");
        assertThat(facts).containsEntry("pages", "4").containsEntry("session", "sess-7f3c");
        assertThat(ShReviewNotifier.summary(run(NOW, 1, Map.of("UPDATE", 1))))
                .isEqualTo("1 edited page in quilhara waits for review");
    }

    @Test
    @DisplayName("SH1428: a run is announced once it is quiet, once, and again only after it writes again")
    void aRunIsDueOnceWhenQuiet() {
        Instant last = NOW.minus(Duration.ofMinutes(15));
        ShReviewSession quiet = run(last, 4, Map.of("CREATE", 4));

        assertThat(ShReviewNotifier.due(quiet, null, NOW)).as("SH1428: finished and waiting").isTrue();
        assertThat(ShReviewNotifier.due(quiet, NOW.minus(Duration.ofMinutes(1)), NOW))
                .as("SH1428: never twice for the same writes").isFalse();
        assertThat(ShReviewNotifier.due(run(NOW.minus(Duration.ofMinutes(2)), 4, Map.of()), null, NOW))
                .as("SH1428: still writing is not finished").isFalse();
        assertThat(ShReviewNotifier.due(run(last, 0, Map.of("CREATE", 4)), null, NOW))
                .as("SH1428: nothing waiting, nothing to say").isFalse();
        assertThat(ShReviewNotifier.due(run(NOW.minus(Duration.ofDays(3)), 4, Map.of()), null, NOW))
                .as("SH1428: last month is history, not news").isFalse();
        assertThat(ShReviewNotifier.due(quiet, last.minus(Duration.ofMinutes(5)), NOW))
                .as("SH1428: a run that wrote after its notice is announced again").isTrue();
    }

    private static ShReviewSession run(Instant lastSeen, int pending, Map<String, Integer> byPage) {
        Map<String, Integer> bySite = new LinkedHashMap<>();
        bySite.put("quilhara", 8);
        return new ShReviewSession("sess-7f3c", "ana", 8, Map.of(), lastSeen.minus(Duration.ofMinutes(3)),
                lastSeen, List.of("site-1"), bySite, null, null, pending,
                byPage.values().stream().mapToInt(Integer::intValue).sum(), byPage);
    }
}
