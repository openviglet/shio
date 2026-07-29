/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.persistence.model.webhook.ShSiteWebhook;

import tools.jackson.databind.ObjectMapper;

/**
 * SH343 — a webhook's signing secret does not come back on a read.
 *
 * <h2>Why this is a test and not a review note</h2>
 *
 * <p>It was measured, not suspected: serializing the entity produced
 * {@code "secret":"SUPER-SECRET-VALUE"}, and {@code ShSiteWebhookAPI} returns this entity <em>directly</em>
 * from {@code GET /api/v2/site-webhook}. So every subscription's HMAC key was readable by any
 * authenticated caller, and a signing key that can be read is a signing key that can be used to forge
 * deliveries to the receiver.</p>
 *
 * <p>The exposure was pre-existing and the reachability work is what surfaced it: SH343 is about
 * narrowing CSRF on this path so an agent can manage a subscription, and narrowing widens who reaches
 * the read from "a console session" to "any token-authenticated caller". <b>So the exposure had to
 * close first.</b> That ordering is the part worth remembering — a reachability change is also an
 * access change, and the question "who can read this now" belongs in the same commit as the narrowing.</p>
 *
 * <p>It is asserted on the <b>serialization</b> rather than through the controller because that is
 * where the defect lives: the annotation is one edit away from being removed, and a controller test
 * would still pass if somebody introduced a second endpoint returning the same entity.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
class ShWebhookSecretExposureTest {

    private static final String SECRET = "sh343-hmac-key-that-must-not-leak";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("SH343: the secret is never serialized, and everything else still is")
    void theSecretIsWriteOnly() {
        ShSiteWebhook webhook = new ShSiteWebhook();
        webhook.setName("revalidate");
        webhook.setUrl("https://example.test/api/revalidate");
        webhook.setSecret(SECRET);

        String json = objectMapper.writeValueAsString(webhook);

        assertThat(json)
                .as("""
                        a signing key that comes back on a read is a key anyone who can list \
                        subscriptions can forge deliveries with — and this entity is returned \
                        directly by GET /api/v2/site-webhook.""")
                .doesNotContain(SECRET)
                .doesNotContain("\"secret\"");
        assertThat(json)
                .as("write-only, not ignored: the rest of the subscription must still be readable, "
                        + "or the console's list page and the agent's own inspection both break")
                .contains("revalidate")
                .contains("api/revalidate");
    }

    @Test
    @DisplayName("SH343: the secret is still accepted on the way in")
    void theSecretIsStillAccepted() {
        // Write-only means exactly that. If this ever stops working, creating a subscription silently
        // stores a null secret and every delivery is signed with nothing — a failure that shows up on
        // the receiver, not here, which is why it is asserted from this side.
        ShSiteWebhook parsed = objectMapper.readValue(
                "{\"name\":\"revalidate\",\"url\":\"https://example.test/hook\",\"secret\":\""
                        + SECRET + "\"}",
                ShSiteWebhook.class);

        assertThat(parsed.getSecret()).isEqualTo(SECRET);
    }
}
