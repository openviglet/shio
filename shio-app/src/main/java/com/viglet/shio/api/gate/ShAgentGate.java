/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.gate;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * One publish gate on the wire (SH997): a name, whether it is on, and its ordered checks.
 *
 * <p>The same shape in both directions — what {@code GET /agent/gates} answers is what
 * {@code PUT /agent/gates/{name}} takes — so a gate read back can be pushed to another instance
 * without translation, which is what makes the file in the tree the authority rather than a copy.</p>
 *
 * @param enabled {@code null} on the way in means yes: a file that says nothing about being on is a
 *                file somebody wrote to be on
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentGate(String name, Boolean enabled, List<Check> checks) {

    /** One step: what to run, and whether failing it stops the publish or only reports. */
    public record Check(String check, String severity) {
    }
}
