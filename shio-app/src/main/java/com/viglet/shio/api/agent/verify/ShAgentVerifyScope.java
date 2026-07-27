/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import java.util.List;
import java.util.Set;

import com.viglet.shio.persistence.model.post.unified.ShPostUnified;

/**
 * What a verify run covers, when it does not cover everything (SH143).
 *
 * <h2>Why the full pass was the wrong shape for the common call</h2>
 *
 * <p>Verification is read twice for different reasons. In CI, "every post in every readable
 * site" is exactly right — that is the gate. But the call an agent makes right after writing
 * three pages asks a much smaller question, and answering it by reading ten thousand posts
 * costs a hundred times what the question is worth. Both callers want the same rules; only
 * the subject set differs.</p>
 *
 * <h2>What a scope narrows, and what it deliberately does not</h2>
 *
 * <p>It narrows the <b>subjects</b>: the posts whose fields are walked and the folders that
 * are reported on. It does <b>not</b> narrow the indexes two rules need whole — "is another
 * published post claiming this URL" and "does this link resolve anywhere in the site" are
 * cross-cutting questions, and a scoped run that answered them from its own subjects would
 * invent duplicates it could not see and dangling links that resolve fine. Those come from a
 * three-column projection instead (see {@code findUrlIndexBySiteId}), which is one query per
 * site and none of the cost a full read pass is expensive for.</p>
 *
 * <h2>The rule that makes it safe</h2>
 *
 * <p>A partial report that reads as a full one is worse than no report — the SH138 hazard.
 * So a scope is never implicit: it produces {@code partial} and a one-line {@code describe}
 * on the payload, and {@code ok} on a scoped run means "nothing is wrong in what I looked
 * at". A caller that stores the report still knows what it proved.</p>
 *
 * @param rows     the subject rows, both states, already loaded
 * @param folderIds folders to report on, or {@code null} for every readable folder
 * @param describe one line naming the subset, {@code null} on a full pass
 * @param notes    what a reader needs to know to trust the numbers
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
record ShAgentVerifyScope(List<ShPostUnified> rows, Set<String> folderIds, String describe,
                          List<String> notes) {

    /** Everything readable — what CI runs and what the endpoint does with no scope params. */
    static ShAgentVerifyScope full(List<ShPostUnified> rows) {
        return new ShAgentVerifyScope(rows, null, null, List.of());
    }

    boolean partial() {
        return describe != null;
    }
}
