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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

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
 * @param commit    abbreviated git commit this build came from, absent outside a build
 *                  (SH460). {@code version} could not answer "which code is this?" — it
 *                  reads {@code dev} on every developer instance, before and after a
 *                  rebuild
 * @param builtAt   ISO-8601 instant the <b>running</b> classes were produced (SH460),
 *                  captured at startup rather than read per request, so an instance whose
 *                  classes were recompiled underneath it still reports the build it is
 *                  actually running. This is the field a client compares against the tree
 *                  it is testing, and the reason a red conformance run can now say which
 *                  side of the live boundary is stale
 * @param features  capability name → present. **Always populated for known
 *                  capabilities, including the ones that are {@code false}** — an
 *                  absent flag would send the caller probing, which is the cost
 *                  this field exists to remove
 * @param elsewhere capability name → <b>where it lives, when that is not this surface</b>
 *                  (SH393). Only the capabilities this protocol does not carry appear here:
 *                  for everything else {@code endpoints} and {@code tools/list} already say
 *                  where to go, and repeating them would be the cost this document exists to
 *                  remove. A flat map of pointers rather than prose — one is a lookup, the
 *                  other is a paragraph re-sent every session (P3). It is the third answer
 *                  {@code features} could not give: {@code visualSnapshot:false} is honest and
 *                  useless on its own, because the capability *exists*, as
 *                  {@code shio snapshot}. Which surface owns what is a **decision** the six
 *                  tasks in §VIII.15 each re-derived; published here, the next caller looks it
 *                  up instead of discovering it by failing
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
// NON_NULL only. NON_DEFAULT would strip the `false` entries from `features`, which
// are the most important thing the manifest says — an absent flag sends the caller
// probing, which is the cost this document exists to remove.
@JsonInclude(Include.NON_NULL)
public record ShAgentManifest(
        String product,
        String version,
        int protocol,
        String apiBase,
        String commit,
        String builtAt,
        Map<String, Boolean> features,
        Map<String, String> elsewhere,
        ShAgentLimits limits,
        List<ShAgentEndpoint> endpoints,
        List<String> startHere) {

    /** Every caller from before SH393, for whom no capability lived anywhere else. */
    public ShAgentManifest(String product, String version, int protocol, String apiBase,
                           Map<String, Boolean> features, ShAgentLimits limits,
                           List<ShAgentEndpoint> endpoints, List<String> startHere) {
        this(product, version, protocol, apiBase, null, null, features, null, limits, endpoints,
                startHere);
    }
}
