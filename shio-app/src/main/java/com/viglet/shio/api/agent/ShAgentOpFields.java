/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

import tools.jackson.databind.JsonNode;

/**
 * SH1083 — the op fields {@code POST /api/v2/agent/batch} takes, and the refusal of any other.
 *
 * <p>Spring's mapper ignores unknown properties, so an op carrying {@code cancle: true} bound to an
 * {@link ShAgentBatch.Op} without it and ran: on a {@code post.publish} that is a page published now
 * instead of a schedule stood down. {@code shio_write} has refused an undeclared field since SH992;
 * this is the same rule on the other door to the same service.</p>
 *
 * <p>The names are the record's own components, so the list cannot fall behind the op: a field
 * added to {@code Op} is allowed the moment it exists. The check is scoped to the op objects rather
 * than to the mapper, because other request bodies may rely on lenient binding and this change
 * should not decide that for them.</p>
 */
public final class ShAgentOpFields {

    /** Every field an op may carry, in the order the record declares them. */
    public static final Set<String> NAMES = names();

    private ShAgentOpFields() {
    }

    private static Set<String> names() {
        Set<String> names = Arrays.stream(ShAgentBatch.Op.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return java.util.Collections.unmodifiableSet(names);
    }

    /**
     * Refuses the first op field {@link #NAMES} does not hold.
     *
     * @param ops the request's {@code ops} array; anything else is left to binding, which reports it
     */
    public static void check(JsonNode ops) {
        if (ops == null || !ops.isArray()) {
            return;
        }
        for (int index = 0; index < ops.size(); index++) {
            JsonNode op = ops.get(index);
            if (op == null || !op.isObject()) {
                continue;
            }
            for (String key : op.propertyNames()) {
                if (!NAMES.contains(key)) {
                    throw new UnknownOpFieldException(index, key);
                }
            }
        }
    }

    /** An op field the batch does not take, named with its index and the allowed names. */
    public static class UnknownOpFieldException extends ShTeachingException {

        public UnknownOpFieldException(int index, String key) {
            super(ShProblem.badRequest("undeclared-op-field",
                            "ops[" + index + "] carries `" + key + "`, which is not an op field")
                    .withDetail("A field the batch does not take would be dropped, and the op would "
                            + "run without it. Nothing in this batch was written.")
                    .withAllowed(new TreeSet<>(NAMES))
                    .withDidYouMeanFrom(key)
                    .withFix("Spell it as one of the allowed names, or drop it.")
                    .withExample("{\"ops\":[{\"op\":\"post.publish\","
                            + "\"address\":\"post:Blog/blog/hello\",\"cancel\":true}]}"));
        }
    }
}
