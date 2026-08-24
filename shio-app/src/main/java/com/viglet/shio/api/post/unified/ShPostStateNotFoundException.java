/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.post.unified.ShPostState;

/**
 * SH921 — a 404 from {@code GET /api/v2/post-unified/{id}} that says which of the two it is.
 *
 * <h2>The two different answers that looked identical</h2>
 *
 * <p>A console read is {@code findByIdAndState}. So {@code ?state=DRAFT} on a post that was
 * published without ever being drafted finds nothing — and until this existed, that answered a
 * bare {@code 404} with an empty body, byte for byte the same as an id nobody has ever heard of.
 * The caller is left choosing between a wrong id, a wrong state, and a deleted row.
 *
 * <p>It is reached by doing something reasonable. {@code /agent/find} returns every addressable
 * post, static files included, and following one of those ids into the console's post endpoint is
 * the obvious next move. The browser check did exactly that and failed; the console does it too,
 * and renders an empty form behind two failed requests rather than saying what happened.
 *
 * <h2>Why it reports the states instead of falling back to one</h2>
 *
 * {@code ShConsolePostReadService.written} falls back to the sibling state, correctly: there the
 * caller has just written the row and wants whatever it became. Here the caller <em>named</em> a
 * state. Answering with a different one is a lie no client can detect — an editor would show
 * published content in a draft form and save it back as an edit nobody made. So the states this
 * id does have are reported, and asking again is the caller's decision.
 *
 * <p>P6 is why this is a class rather than a string: every 4xx carries {@code fix},
 * {@code allowed}, {@code didYouMean} or {@code example}, and an error that only says "no" is one
 * the reader has to debug rather than read.
 */
public class ShPostStateNotFoundException extends ShTeachingException {

    private ShPostStateNotFoundException(ShProblem problem) {
        super(problem);
    }

    /** The id exists, in states that do not include the one asked for. */
    public static ShPostStateNotFoundException wrongState(String id, ShPostState asked,
                                                          List<ShPostState> available) {
        String has = available.stream().map(ShPostStateNotFoundException::name).toList().toString();
        return new ShPostStateNotFoundException(
                ShProblem.of("post-state-not-found", HttpStatus.NOT_FOUND,
                                "No " + name(asked) + " of this post")
                        .withDetail("Post " + id + " exists, but not in " + name(asked)
                                + " — a post published without ever being drafted has no draft row. "
                                + "It is stored as " + has + ".")
                        .withAllowed(available.stream().map(ShPostStateNotFoundException::name).toList())
                        .withDidYouMean(available.isEmpty() ? null : name(available.getFirst()))
                        .withFix("Ask for a state it has: GET /api/v2/post-unified/" + id
                                + "?state=" + name(available.isEmpty()
                                        ? ShPostState.PUBLISHED : available.getFirst())
                                + ". GET /api/v2/post-unified/" + id
                                + "/versions lists every stored row.")
                        .withExample("/api/v2/post-unified/" + id + "?state="
                                + name(available.isEmpty() ? ShPostState.PUBLISHED : available.getFirst())));
    }

    /** No row anywhere carries this id — which is a different problem entirely. */
    public static ShPostStateNotFoundException noSuchPost(String id) {
        return new ShPostStateNotFoundException(
                ShProblem.of("no-such-post", HttpStatus.NOT_FOUND, "No post with this id")
                        .withDetail("Nothing is stored under " + id + " in any state, for this "
                                + "credential. An id that reads elsewhere but not here is usually "
                                + "a different kind of object.")
                        .withFix("Resolve the address first: GET /api/v2/agent/resolve?address=... "
                                + "or find it with GET /api/v2/agent/find. A static file is served "
                                + "by GET /api/v2/staticfile/{id}, and a folder by "
                                + "GET /api/v2/folder/{id}.")
                        .withExample("/api/v2/agent/resolve?address=post:<site>/<friendly-url>"));
    }

    /** Lower-case, because that is how the query parameter is written in every example. */
    private static String name(ShPostState state) {
        return state.name().toLowerCase(Locale.ROOT);
    }
}
