/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.query;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * Raised when a filter this listing serves is sent a value it cannot read (SH1022).
 *
 * <p>Separate from {@link ShUnknownQueryKeyException} because the fix is elsewhere: the caller
 * found the right word and spelled its value wrong, so what closes it is the shape of the value,
 * not a list of other words. {@code modifiedSince=last week} is the case this was written for —
 * a plausible thing to send, and one that silently filtering to nothing would report as "no post
 * changed since then".</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ShInvalidQueryValueException extends ShTeachingException {

    public ShInvalidQueryValueException(String key, String sent, String wanted) {
        super(ShProblem.badRequest("invalid-query-value",
                "'" + sent + "' is not a value for '" + key + "'")
                .withDetail("'" + key + "' is a filter this listing serves, and '" + sent
                        + "' is not something it can read. It is refused rather than dropped: a "
                        + "filter that fails open returns every row and one that fails closed "
                        + "returns none, and both answer 200.")
                .withFix("Send " + key + " as " + wanted + ".")
                .withExample("?" + key + "=" + example(key)));
    }

    private static String example(String key) {
        return switch (key) {
            case "modifiedSince", "modifiedBefore" -> "2026-09-01T00:00:00Z";
            case "page" -> "0";
            case "size" -> "50";
            case "subtree" -> "true";
            default -> "";
        };
    }
}
