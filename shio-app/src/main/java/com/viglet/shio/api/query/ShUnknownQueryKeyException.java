/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.query;

import java.util.Collection;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * Raised when a listing is sent a parameter it does not serve (SH1022).
 *
 * <p>Spring drops an unbound query parameter silently, which is the worst possible answer to a
 * typo: {@code ?modifedSince=...} returns every row, {@code 200}, and reads exactly like a
 * correct call that matched everything. The caller has no way to tell the two apart, and an
 * agent least of all — it has no screen to notice the count on.</p>
 *
 * <p>Two shapes, because two different things go wrong and the fix is not the same. A key
 * nothing in the vocabulary spells is a typo, and {@code didYouMean} usually closes it. A key the
 * vocabulary <em>does</em> spell but this surface does not serve is a caller on the wrong
 * surface — {@code sort} on {@code /agent/find}, whose cursor is an address and therefore cannot
 * be ordered by anything else — and what closes that one is being told where the key does work.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ShUnknownQueryKeyException extends ShTeachingException {

    /** A key no surface spells: a typo, closed by {@code didYouMean}. */
    public ShUnknownQueryKeyException(String key, Collection<String> allowed) {
        super(ShProblem.badRequest("unknown-query-key", "Unknown filter '" + key + "'")
                .withDetail("'" + key + "' is not a filter this listing understands. It was not "
                        + "ignored: a listing that quietly drops a filter answers 200 with every "
                        + "row, which reads exactly like a filter that matched everything.")
                .withAllowed(allowed)
                .withFix("Use one of the listed filters, or drop the parameter.")
                .withExample("?type=Article&state=draft&modifiedSince=2026-09-01T00:00:00Z")
                .withDidYouMeanFrom(key));
    }

    /** A key the vocabulary spells and this surface does not serve. */
    public ShUnknownQueryKeyException(String key, Collection<String> allowed, String elsewhere) {
        super(ShProblem.badRequest("unserved-query-key",
                "'" + key + "' is not served by this listing")
                .withDetail("'" + key + "' is part of the content query vocabulary, but this "
                        + "listing cannot answer it. " + elsewhere)
                .withAllowed(allowed)
                .withFix(elsewhere)
                .withExample("?type=Article&state=draft&limit=25"));
    }
}
