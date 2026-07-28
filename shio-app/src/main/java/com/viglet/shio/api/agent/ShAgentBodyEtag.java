/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Conditional reads for the content half of the agent surface (SH159).
 *
 * <h2>Why this one tags the body and SH116 tags a digest</h2>
 *
 * <p>SH116 covered the three discovery reads, and each of them already knew its own content
 * digest — the manifest's configuration hash, the pack's SH77 fingerprint, the blueprint
 * registry's. {@code find}, {@code read}, {@code changes} and the post-type listing have no such
 * number, and inventing one per endpoint would mean four more things that can silently disagree
 * with what was actually rendered. A digest that is <em>almost</em> right is worse than none: it
 * answers {@code 304} to a caller holding different bytes, and the caller has no way to find
 * out.</p>
 *
 * <p>So the tag here is taken over the rendered body itself. That is exact by construction —
 * identical bytes, identical tag, and no derivation to get wrong — and it needs no per-endpoint
 * bookkeeping. Rendering happens once: the serialized string is what gets hashed <em>and</em>
 * what gets returned, so a {@code 200} costs the same as it did before.</p>
 *
 * <h2>What it saves, and what it does not</h2>
 *
 * <p>Exactly what SH116 saves, and no more: the payload. The query still runs, the rows are
 * still loaded, the JSON is still written. What the caller stops paying is the part that is
 * scarce — the tokens — and for {@code find}, the read an agent repeats on every iteration of a
 * loop, that is the cost that actually accumulates. Saving the server's work would need a
 * cached digest that can go stale, and a stale {@code 304} is the one failure mode that makes a
 * caching scheme unusable.</p>
 *
 * <h2>The representation rule, restated because it is easy to lose</h2>
 *
 * <p>A tag names a <b>representation</b>. Hashing the body gets this right for free — a
 * projection to two fields and a whole document are different bytes, so they are different tags,
 * with nobody having to remember to mix {@code fields} in. The {@code kind} argument exists for
 * the remaining case: two endpoints that happen to render byte-identical payloads (an empty
 * {@code find} and an empty {@code read} are both {@code []}) must not share a tag, because a
 * client keyed only on the tag would then reuse one for the other.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShAgentBodyEtag {

    private final ObjectMapper objectMapper;

    public ShAgentBodyEtag(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * A conditional JSON response.
     *
     * <p>The payload is serialized with the application's own mapper — the same instance behind
     * the message converter — so the bytes returned here are the bytes the endpoint would have
     * returned unconditionally. Using a private mapper would be a quiet way to make one endpoint
     * serialize differently from every other one.</p>
     *
     * @param kind         endpoint discriminator, e.g. {@code find}
     * @param payload      the object that would otherwise have been returned
     * @param ifNoneMatch  the request's {@code If-None-Match}, or null
     */
    public ResponseEntity<Object> json(String kind, Object payload, String ifNoneMatch) {
        return respond(kind, objectMapper.writeValueAsString(payload),
                MediaType.APPLICATION_JSON, ifNoneMatch);
    }

    /**
     * A conditional terse response. Already a string, so there is nothing to serialize.
     */
    public ResponseEntity<Object> terse(String kind, String body, String ifNoneMatch) {
        return respond(kind, body, MediaType.TEXT_PLAIN, ifNoneMatch);
    }

    private ResponseEntity<Object> respond(String kind, String body, MediaType contentType,
                                           String ifNoneMatch) {
        String etag = ShAgentEtag.of(kind, contentType.toString(), body);
        if (ShAgentEtag.matches(ifNoneMatch, etag)) {
            return ShAgentEtag.notModified(etag);
        }
        return ShAgentEtag.ok(etag).contentType(contentType).body(body);
    }
}
