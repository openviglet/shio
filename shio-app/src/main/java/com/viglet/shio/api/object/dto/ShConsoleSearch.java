/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object.dto;

import java.util.List;

import com.viglet.shio.api.agent.dto.ShAgentFound;

/**
 * What the console's search answers with (SH1027).
 *
 * <p>The rows are {@link ShAgentFound.Row} unchanged, because this listing <em>is</em> the agent's
 * find with a permission filter over it — a shape, not a third contract (P10). Re-projecting them
 * into a console vocabulary would be the third spelling of a post's address and state, and the one
 * that drifts.</p>
 *
 * @param results  the rows this reader may see, in the order {@code find} returned them
 * @param count    how many that is
 * @param withheld how many matched and were removed because the reader may not open them.
 *
 *                 <p><b>The number, not silence.</b> A filtered listing that says nothing is
 *                 indistinguishable from a search that found less, and the curator's next move is
 *                 to conclude the content is not there — which is the wrong conclusion and an
 *                 expensive one to act on. Saying "and six you cannot see" tells them to ask
 *                 somebody for access rather than to write the page again.</p>
 * @param truncated whether the underlying walk was cut at the cap
 * @param cursor    the address to continue from, or {@code null} on the last page
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShConsoleSearch(List<ShAgentFound.Row> results, int count, int withheld,
                              boolean truncated, String cursor) {

    public ShConsoleSearch {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
