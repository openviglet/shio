/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.agent.verify;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * SH809 — the answer to <em>which fields on this page can somebody change</em>.
 *
 * <p>Small on purpose. Every field here is either an address a write already takes (SH79) or a name
 * the page itself carries, so an agent can go straight from this to {@code post.upsert} without a
 * second call — which is the difference between a capability and a hint (P2).</p>
 *
 * @param address the page, as this surface spells addresses
 * @param source  {@code rendered} where the page composed, {@code content} where it cannot yet —
 *                an empty list means two different things and this says which (SH169)
 * @param fields  every editable field the page declares, in the order a curator meets them
 * @param regions every region the page declares, whether or not anything in it is editable
 * @param containers SH1039 — what may be inserted and where, on {@code include=insertable} only,
 *                   and {@code null} otherwise. Opt-in because it is the expensive half: the fields
 *                   of every accepted type are what a properties panel needs and what a caller
 *                   asking <em>which fields can I edit</em> did not ask for (P3). {@code null} and
 *                   an empty list are different answers — nobody asked, against a page that
 *                   composes no sections — so the key is absent rather than empty when it was not
 *                   requested
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentEditable(
        String address,
        String source,
        List<ShHtmlEditable.Field> fields,
        List<String> regions,
        List<ShAgentInsertable.Container> containers) {

    /** The four-field answer, for every caller that did not ask for the catalogue. */
    public ShAgentEditable(String address, String source, List<ShHtmlEditable.Field> fields,
            List<String> regions) {
        this(address, source, fields, regions, null);
    }
}
