/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.persistence.model.webhook;

/**
 * The content-lifecycle transitions an outbound webhook (SH12) can fire on.
 * The wire value emitted in the payload is the {@link #wire() lower-cased} name
 * (e.g. {@code "publish"}), to match the {@code create-shio-app} receiver (SH9).
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public enum ShWebhookEventType {

    PUBLISH,
    UNPUBLISH,
    DELETE,

    /**
     * SH998 — a gate refused a publish, and the check it refused on.
     *
     * <p>Not a transition: nothing moved, which is exactly why somebody outside has to hear about
     * it. Shio reports and something else decides whether to page a human, pause a pipeline or open
     * a ticket — the honest division of labour with an orchestrator, and the alternative is a
     * workflow engine inside a CMS deciding who is on call.</p>
     */
    GATE_FAILED,

    /**
     * SH998 — an agent run left work a person has to release.
     *
     * <p>The review queue is a screen somebody has to be looking at. This is the same fact pushed:
     * a nightly run that finished at 03:00 with eleven drafts waiting is not a thing anybody
     * discovers by opening a console.</p>
     */
    REVIEW_REQUESTED;

    /** The lower-cased token sent in the JSON {@code event} field. */
    public String wire() {
        return name().toLowerCase();
    }

    /**
     * True for the three transitions that change what a visitor can see (SH998).
     *
     * <p>The distinction is load-bearing at the subscription: an empty event set has always meant
     * "all of them", and a receiver that subscribed to everything before this enum grew asked for
     * content transitions — not for notices. See {@code ShSiteWebhook#matches}.</p>
     */
    public boolean contentTransition() {
        return this == PUBLISH || this == UNPUBLISH || this == DELETE;
    }
}
