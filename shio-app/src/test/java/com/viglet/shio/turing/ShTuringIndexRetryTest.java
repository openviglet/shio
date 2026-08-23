/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.turing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;

import tools.jackson.databind.ObjectMapper;

/**
 * SH912 — a failed index push was recorded and never retried, so a thirty-second Turing restart
 * left a published page permanently unfindable until a human read the diagnostics feed and
 * re-published it by hand.
 *
 * <h2>What is actually being asserted</h2>
 *
 * <p>SH84 already knew the state and already reported it; the defect was that <b>nothing acted on
 * the record</b>. So the assertions here are about the two ends of the retry — that it happens at
 * all, and that it stops — plus the two properties that make it safe to add:</p>
 *
 * <ul>
 *   <li><b>Exactly one incident on exhaustion</b>, not one per attempt. This is the assertion that
 *       would have caught the obvious implementation: recording inside the loop coalesces into a
 *       single {@code Incident} carrying {@code count() == 3}, which reads to an operator as three
 *       failed publishes rather than one push that was retried twice.</li>
 *   <li><b>Silence when a retry succeeds.</b> If the first attempt's failure were recorded before
 *       the second attempt ran, a working index would carry an incident contradicting it for the
 *       whole diagnostics window — the "stale incident" the acceptance names.</li>
 * </ul>
 *
 * <p>No Spring context and no sleeping: the backoff is configured to {@code ZERO} so the loop runs
 * at full speed, and the schedule itself is asserted separately as the pure function it is. A test
 * that actually waited three seconds to prove a bound would be a test people delete.</p>
 *
 * @author Alexandre Oliveira
 */
class ShTuringIndexRetryTest {

    /** Runs the handed-off task on the calling thread, which is what makes these assertions direct. */
    private static final Executor INLINE = Runnable::run;

    private final ShAgentIncidentLog incidents = new ShAgentIncidentLog();

    /** A sender that fails a stated number of times and then works, counting every attempt. */
    private static final class Sender extends ShTuringIndexService {
        private final AtomicInteger attempts = new AtomicInteger();
        private final int failures;
        private final RuntimeException throwing;

        Sender(ShTuringProperties properties, int failures, RuntimeException throwing) {
            super(properties, new ShTuringDocumentMapper(new ObjectMapper()));
            this.failures = failures;
            this.throwing = throwing;
        }

        @Override
        public boolean send(TurSNJobItem item, String what) {
            int attempt = attempts.incrementAndGet();
            if (attempt > failures) {
                return true;
            }
            if (throwing != null) {
                throw throwing;
            }
            return false;
        }
    }

    private static ShTuringProperties properties(int maxAttempts) {
        ShTuringProperties properties = new ShTuringProperties();
        properties.setEnabled(true);
        properties.setUrl("http://localhost:2700");
        properties.setDefaultSite("Sample");
        properties.setMaxAttempts(maxAttempts);
        // The bound is what is under test; the waiting is not, and a suite must not spend it.
        properties.setRetryBackoff(Duration.ZERO);
        return properties;
    }

    private static TurSNJobItem item() {
        return new TurSNJobItem(TurSNJobAction.CREATE, List.of("Sample"), Locale.ENGLISH,
                Map.of("id", "post-1"));
    }

    /** Drives the listener's send path directly, which is the whole of what SH912 changed. */
    private void push(ShTuringProperties properties, Sender sender) {
        // The extractor and the storage are SH913's and unused on this path: sendWithRetries is
        // reached directly, which is the whole of what SH912 changed.
        new ShTuringIndexListener(properties, sender, null, null, incidents, INLINE, null)
                .sendWithRetries(item(), "index post-1", "site-1");
    }

    @Test
    void aFailedPushIsRetriedUpToTheBoundAndThenGivenUpOn() {
        ShTuringProperties properties = properties(3);
        Sender sender = new Sender(properties, Integer.MAX_VALUE, null);

        push(properties, sender);

        assertThat(sender.attempts.get())
                .as("SH912: a refused push is attempted maxAttempts times in total, the first included")
                .isEqualTo(3);
    }

    @Test
    void anExhaustedPushLeavesExactlyOneIncidentAndNotOnePerAttempt() {
        ShTuringProperties properties = properties(3);
        push(properties, new Sender(properties, Integer.MAX_VALUE, null));

        List<ShAgentIncidentLog.Incident> recorded = new ArrayList<>(incidents.recent(10));
        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).count())
                .as("SH912: recording inside the retry loop would coalesce to a count of 3, which reads "
                        + "as three failed publishes rather than one retried push")
                .isEqualTo(1);
        assertThat(recorded.get(0).kind()).isEqualTo(ShAgentIncidentLog.Kind.INDEX);
        assertThat(recorded.get(0).message())
                .as("the operator is told it was retried, or the bound is invisible")
                .contains("3 attempts");
    }

    @Test
    void aRetryThatSucceedsRecordsNothingAtAll() {
        ShTuringProperties properties = properties(3);
        Sender sender = new Sender(properties, 2, null);

        push(properties, sender);

        assertThat(sender.attempts.get()).isEqualTo(3);
        assertThat(incidents.recent(10))
                .as("SH912: an incident recorded before the retry succeeded would sit in the diagnostics "
                        + "window contradicting an index that is in fact current")
                .isEmpty();
    }

    @Test
    void aThrownErrorIsRetriedToo_andTheIncidentKeepsItsFrames() {
        ShTuringProperties properties = properties(2);
        Sender sender = new Sender(properties, Integer.MAX_VALUE,
                new IllegalStateException("connection refused"));

        push(properties, sender);

        assertThat(sender.attempts.get())
                .as("SH912: a Turing restart surfaces as a thrown connection error, which is the case "
                        + "this whole task exists for — retrying only a clean refusal would miss it")
                .isEqualTo(2);
        List<ShAgentIncidentLog.Incident> recorded = new ArrayList<>(incidents.recent(10));
        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).message()).contains("connection refused");
        assertThat(recorded.get(0).frames())
                .as("something of ours threw, so there is a stack to point at — unlike a refusal")
                .isNotBlank();
    }

    @Test
    void oneAttemptRestoresTheBehaviourFromBeforeThisExisted() {
        ShTuringProperties properties = properties(1);
        Sender sender = new Sender(properties, Integer.MAX_VALUE, null);

        push(properties, sender);

        assertThat(sender.attempts.get()).isEqualTo(1);
        List<ShAgentIncidentLog.Incident> recorded = new ArrayList<>(incidents.recent(10));
        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).message())
                .as("SH912: with retrying off the message must not claim a retry that never happened")
                .doesNotContain("attempts");
    }

    @Test
    void theBackoffDoublesFromTheConfiguredBaseAndIsZeroWhenTurnedOff() {
        Duration base = Duration.ofSeconds(1);
        assertThat(ShTuringIndexListener.backoffBefore(2, base)).isEqualTo(Duration.ofSeconds(1));
        assertThat(ShTuringIndexListener.backoffBefore(3, base)).isEqualTo(Duration.ofSeconds(2));
        assertThat(ShTuringIndexListener.backoffBefore(4, base)).isEqualTo(Duration.ofSeconds(4));

        // The first attempt never waits, and a zero or absent base disables waiting entirely —
        // which is what lets this suite drive the bound without spending three seconds on it.
        assertThat(ShTuringIndexListener.backoffBefore(1, base)).isEqualTo(Duration.ZERO);
        assertThat(ShTuringIndexListener.backoffBefore(2, Duration.ZERO)).isEqualTo(Duration.ZERO);
        assertThat(ShTuringIndexListener.backoffBefore(2, null)).isEqualTo(Duration.ZERO);
    }
}
