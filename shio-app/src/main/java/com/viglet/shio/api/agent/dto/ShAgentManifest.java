/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.dto;

import java.util.List;
import java.util.Map;

/**
 * The agent manifest (SH76) — this instance describing itself to a coding agent.
 *
 * <p>Design law <b>P2</b>: the dominant failure mode of an agent against any CMS
 * is <em>guessing</em> — an endpoint that doesn't exist, a capability that isn't
 * installed, a parameter name that was invented. Each field here closes one of
 * those guesses with a single cached call.</p>
 *
 * @param product   product name
 * @param version   running build version ({@code dev} outside a packaged jar)
 * @param protocol  revision of the agent surface itself; bumped when the shape of
 *                  the manifest/context changes, so a client can branch on it
 *                  without version-sniffing the product
 * @param apiBase   base path every {@code endpoints} path is relative to
 * @param features  capability name → present. **Always populated for known
 *                  capabilities, including the ones that are {@code false}** — an
 *                  absent flag would send the caller probing, which is the cost
 *                  this field exists to remove
 * @param limits    operating limits (rate limit, paging, cache)
 * @param endpoints curated index of the endpoints an agent actually needs, each
 *                  with a runnable example — deliberately *not* the full API
 *                  surface, which would cost tokens on every session (P3)
 * @param startHere the first calls to make, in order — the manifest's answer to
 *                  "where do I begin?"
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShAgentManifest(
        String product,
        String version,
        int protocol,
        String apiBase,
        Map<String, Boolean> features,
        ShAgentLimits limits,
        List<ShAgentEndpoint> endpoints,
        List<String> startHere) {
}
