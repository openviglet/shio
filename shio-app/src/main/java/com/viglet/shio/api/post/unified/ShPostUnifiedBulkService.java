/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.ShAgentSessionContext;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * SH1021 — the curator's bulk verbs, which are the agent's verbs.
 *
 * <h2>Composed, not forked (P10)</h2>
 *
 * <p>{@code shio_publish} declares its address as an array and promises atomicity across everything
 * passed. The console's batch menu offered copy, move and delete; REST offered single-id publish.
 * So the agent half shipped and the curator half did not, and the obvious fix — a second publish
 * loop in a console service — is the one that lets the two surfaces drift about what publishing
 * means.</p>
 *
 * <p>This builds ops and hands them to {@link ShAgentBatchService}, the same writer the agent's
 * batch runs through. One transaction, one vocabulary, one set of gates, and a per-item result that
 * already exists: {@code ShAgentChangeSet} carries an outcome and a problem per op, so a bare 200
 * over a partly-failed run is not a shape this can accidentally return.</p>
 *
 * <h2>The permission is asked here and again inside</h2>
 *
 * <p>Deliberately twice. The batch's own op checks it per row, and this checks it before building
 * anything so a caller with no publish right gets one refusal rather than a change set that is all
 * refusals — and, on a dry run, a plan they could not carry out. SH995's rule is that a curator who
 * may edit a page is not automatically the one who decides it goes live, and a plan that misleads
 * about that is worse than no plan.</p>
 */
@Service
public class ShPostUnifiedBulkService {

    /** The verbs a selection can take. Deliberately not every op: this is the browser's menu. */
    public enum Op {
        PUBLISH(ShAgentBatchService.POST_PUBLISH),
        UNPUBLISH(ShAgentBatchService.POST_UNPUBLISH),
        DELETE(ShAgentBatchService.POST_DELETE);

        private final String op;

        Op(String op) {
            this.op = op;
        }

        public String op() {
            return op;
        }
    }

    private final ShAgentBatchService shAgentBatchService;
    private final ShContentAclBulkGuard guard;

    public ShPostUnifiedBulkService(ShAgentBatchService shAgentBatchService,
                                    ShContentAclBulkGuard guard) {
        this.shAgentBatchService = shAgentBatchService;
        this.guard = guard;
    }

    /**
     * @param op      the verb
     * @param ids     the posts, by id — the console holds ids, the agent holds addresses (SH172)
     * @param dryRun  plan and write nothing
     * @param confirm the token a destructive op needs, from its own dry run
     * @param when    an instant, for a scheduled publish or unpublish (SH342)
     */
    public ShAgentChangeSet run(Op op, List<String> ids, boolean dryRun, String confirm,
                                String when, String actor) {
        if (ids == null || ids.isEmpty()) {
            throw new NothingSelectedException(op);
        }
        // Before anything is built, so a caller who may not publish gets one refusal rather than a
        // change set that is all refusals — or, worse, a dry-run plan they could not carry out.
        guard.requireAll(op, ids);

        List<ShAgentBatch.Op> ops = new ArrayList<>();
        for (String id : ids) {
            // `id:` — the escape hatch SH172 kept for exactly this: a console holds an id and the
            // agent surface is addressed, and resolving here would be a second resolver.
            ops.add(new ShAgentBatch.Op(op.op(), "id:" + id, null, null, null, null,
                    when, null, null, null, null));
        }
        ShAgentBatch batch = new ShAgentBatch(dryRun, ops, confirm, false);

        ShAgentSessionContext.set(ShAgentSessionContext.consoleSession(actor, Instant.now()));
        try {
            return shAgentBatchService.run(batch, true, actor);
        } finally {
            // The thread is pooled, so a session left bound stamps the next request's audit rows
            // with this curator's day.
            ShAgentSessionContext.clear();
        }
    }

    /** A selection of nothing is a caller mistake, and saying so beats an empty change set. */
    public static class NothingSelectedException extends ShTeachingException {
        public NothingSelectedException(Op op) {
            super(ShProblem.badRequest("nothing-selected",
                    "This bulk " + op.name().toLowerCase(Locale.ROOT) + " names no posts.")
                    .withDetail("A run over an empty selection is a caller mistake rather than a "
                            + "no-op: the button that sent it was enabled, so something upstream "
                            + "believes there is a selection.")
                    .withFix("Send `ids` with at least one post id.")
                    .withExample("{\"op\":\"PUBLISH\",\"ids\":[\"<post id>\"],\"dryRun\":true}"));
        }
    }
}
