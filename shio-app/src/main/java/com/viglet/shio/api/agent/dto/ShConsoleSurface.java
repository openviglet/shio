/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * One surface the console offers, and how an agent reaches the same capability (SH978).
 *
 * <p>On the wire as it is in the file, so a caller reading {@code shio://surfaces} and a
 * contributor reading {@code console/surfaces.json} are reading one document. The icon and the
 * i18n keys are deliberately not here: they belong to the console's own declaration, and a client
 * that cannot render them would be paying for them on every read.</p>
 *
 * @param id         the surface's id, which is also the console's
 * @param section    the section of the rail it belongs to
 * @param route      where it is in the console
 * @param capability what a curator does there, in a phrase
 * @param agent      the tool, op or declared endpoint that does the same thing, or absent
 * @param exception  why an agent has no equivalent, when it has none
 */
@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShConsoleSurface(String id, String section, String route, String capability,
                               Equivalent agent, Exception exception) {

    /**
     * What an agent calls instead.
     *
     * @param kind   {@code tool}, {@code op} or {@code endpoint}
     * @param target the tool name, the op name or the path in the manifest's endpoint index
     */
    @JsonInclude(Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Equivalent(String kind, String target) {
    }

    /**
     * Why there is none.
     *
     * @param reason   the classification, in the vocabulary the declaration uses
     * @param why      the argument, which is what a reader checks
     * @param closedBy what would make this stale — a task id, or empty when nothing should
     */
    @JsonInclude(Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Exception(String reason, String why, String closedBy) {
    }
}
