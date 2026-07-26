/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.diagnostics.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * What has gone wrong lately, in one call (SH103).
 *
 * <p>The complement to SH80's teaching errors: those answer the call that failed, this
 * answers the failures that happened after the agent moved on — a webhook the front end
 * never received, an image transform that silently served the original, a scheduled
 * publish that threw overnight, a 500 whose response is long gone. Until now the only
 * record was a line in {@code store/logs}, which is not a surface an agent has.</p>
 *
 * <p>{@code since} and {@code dropped} are not decoration. The log is a bounded
 * in-memory window (see {@code ShAgentIncidentLog}), so "no incidents" can mean either
 * "nothing failed" or "nothing is remembered" — and a diagnostics endpoint that cannot
 * tell those apart is the kind of clean bill of health that costs a human a day.</p>
 *
 * @param since     when this window started collecting (process start), ISO-8601
 * @param dropped   incidents evicted because the window was full
 * @param counts    kind → occurrences, name-sorted, folded over repeats
 * @param incidents the incidents themselves, most recent occurrence first
 * @param notes     what this view cannot see, stated rather than implied
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentDiagnostics(String since, long dropped, Map<String, Integer> counts,
                                 List<Incident> incidents, List<String> notes) {

    /**
     * One failure, folded over its repeats.
     *
     * @param kind      {@code api-error}, {@code webhook}, {@code transform}, {@code schedule}
     * @param subject   what it happened to — a URL, a post id, a request path
     * @param message   the cause, shortest form that identifies it
     * @param fix       what to do about it, when there is something generic to say
     * @param count     occurrences of this exact incident
     * @param firstSeen ISO-8601 first occurrence
     * @param lastSeen  ISO-8601 most recent occurrence
     */
    @JsonInclude(Include.NON_NULL)
    public record Incident(String kind, String subject, String message, String fix,
                           int count, String firstSeen, String lastSeen) {
    }
}
