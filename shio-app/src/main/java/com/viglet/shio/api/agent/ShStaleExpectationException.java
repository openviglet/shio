/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * The post changed between the read that planned this write and the write itself (SH721).
 *
 * <h2>What it prevents</h2>
 *
 * <p>A curator patching one field while an agent runs {@code post.upsert} on the same post got
 * {@code 200}, the agent got {@code changed=1}, and the curator's field was gone — <b>with no
 * signal to either party</b>. That is the surprise P7 names, and neither side could have prevented
 * it, because nothing on the surface expressed <i>only if unchanged</i>.</p>
 *
 * <p>{@code expect} is that sentence, and this is what it says when it is not true. The default is
 * still last-write-wins, which is what keeps the write surface cheap; what changed is that an agent
 * that <em>cares</em> can say so.</p>
 *
 * <h2>Why the fix is not "send it again with the new digest"</h2>
 *
 * <p>That is the one instruction this must not give, and it is the tempting one. Re-sending the
 * same body with a fresh digest performs exactly the overwrite the caller asked to be protected
 * from — it would turn the guard into a two-step version of the defect. So the fix says read
 * first: whatever changed is a decision somebody made, and the caller's own write was composed
 * without it.</p>
 *
 * <p>409 rather than 412. {@code If-Match}'s status is right for HTTP conditional requests, and
 * this is a field inside a batch document where one op of twenty is refused — the batch's own
 * status is about the batch. Everything on this surface that refuses because of <em>state</em>
 * answers 409, and a second status for the same class of answer is a distinction a caller has to
 * learn for nothing.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShStaleExpectationException extends ShTeachingException {

    public ShStaleExpectationException(String address, String expected, String actual) {
        super(ShProblem.conflict("stale-expectation",
                        "'" + address + "' changed since it was read")
                .withDetail(actual == null
                        ? "This op expected the post at " + address + " to be unchanged (digest "
                                + expected + "), and there is no post there now — it was deleted or "
                                + "moved after the read this write was planned against."
                        : "This op expected the post at " + address + " to be unchanged (digest "
                                + expected + "), and it now digests as " + actual + ". Somebody "
                                + "wrote to it after the read this write was planned against.")
                .withFix("Read it again (GET /api/v2/agent/read?address=" + address
                        + "&include=digest), decide what to do about the change, and send a write "
                        + "composed from what is there now. Re-sending this body with the new "
                        + "digest would perform the overwrite `expect` was asked to prevent.")
                .withExample("GET /api/v2/agent/read?address=" + address + "&include=digest"));
    }
}
