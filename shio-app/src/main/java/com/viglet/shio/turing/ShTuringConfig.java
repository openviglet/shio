/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.turing;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one thread that talks to Turing (SH84).
 *
 * <h2>Why one thread and not a pool</h2>
 *
 * <p>Index operations for a post are <b>ordered</b>: publish then unpublish must arrive in that
 * order, and a pool would let the de-index overtake the index and leave a withdrawn page
 * searchable — with no error anywhere, because both calls succeeded. A single worker makes the
 * ordering a property of the design rather than a race that usually resolves the right way.
 * Throughput is not the constraint this sits behind: Turing's own {@code /api/sn/import} hands the
 * job to a JMS queue and returns.</p>
 *
 * <h2>Why the queue is bounded</h2>
 *
 * <p>With Turing unreachable, an unbounded queue turns an outage in another system into this
 * instance running out of memory. Bounded means a long outage <em>drops</em> changes, which is the
 * honest failure: the rejection is recorded as an {@code INDEX} incident naming what was not sent
 * (see {@link ShTuringIndexListener}), so the operator learns the index is behind instead of
 * discovering it from a user's search. The two alternatives are worse — {@code CallerRunsPolicy}
 * would block the editor's publish on a dead third party, and discarding silently would make a
 * stale index indistinguishable from a current one.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Configuration
public class ShTuringConfig {

    /**
     * Deep enough to absorb a bulk apply (SH82 batches hundreds of posts) without being deep
     * enough to matter if it is never drained.
     */
    private static final int QUEUE_CAPACITY = 1000;

    /** Idle worker exits, so a Shio that never indexes carries no thread at all. */
    private static final long KEEP_ALIVE_SECONDS = 60;

    @Bean(destroyMethod = "shutdown")
    Executor shTuringIndexExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1,
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "sh-turing-index");
                    // Daemon: an unreachable Turing must not hold up a shutdown, and the queue is
                    // already understood to be droppable — see the class note.
                    thread.setDaemon(true);
                    return thread;
                });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
