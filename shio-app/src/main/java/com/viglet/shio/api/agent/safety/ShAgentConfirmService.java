/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.safety;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.viglet.shio.api.agent.ShAgentProperties;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet.Change;

/**
 * Destructive-op confirm tokens (SH108).
 *
 * <h2>The rule</h2>
 *
 * <p>A write that deletes something takes <b>two steps</b>. The first is a plan, which
 * writes nothing and returns a {@code confirm} token; the second repeats the request
 * carrying that token. An agent therefore cannot destroy anything it did not just render
 * for the human, and — because the token is derived from the plan's <em>content</em> —
 * cannot destroy something that changed underneath it between the two calls.</p>
 *
 * <h2>Why the refusal does not contain the token</h2>
 *
 * <p>This is the whole design. A 409 that helpfully included the value the caller needs
 * would collapse two steps into one call with a retry, and the human would never see the
 * plan. The refusal says how to get a token; it does not hand one over. That is also why
 * the token is an HMAC over the plan under an instance secret rather than a plain hash of
 * it: a plain hash is something a caller can compute for itself, which makes the gate a
 * speed bump rather than a gate.</p>
 *
 * <h2>What goes into the signature, and what deliberately does not</h2>
 *
 * <p>Only the <b>destructive rows</b> of the plan — the {@code delete} actions with their
 * canonical addresses. Not the creates and updates alongside them: a caller that adds a page between plan and apply has not changed
 * what the confirmation was about, and invalidating the token would train callers to
 * re-plan blindly until it stuck. Not the timestamp of the plan either — the token carries
 * its own expiry, in the clear, so a stale one fails with "expired" instead of with the
 * indistinguishable "does not match".</p>
 *
 * <h2>Statelessness, and the one place it costs</h2>
 *
 * <p>Nothing is stored: the token verifies from its own bytes. That keeps the MCP server's
 * "any instance can answer any request" property true. The price is that the signing secret
 * has to be the same on every node — random per JVM unless {@code shio.agent.confirm.secret}
 * is set — which is a configuration note rather than a design flaw, and is the only thing
 * that would make a plan on node A un-appliable on node B.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShAgentConfirmService {

    private static final String ALGORITHM = "HmacSHA256";

    /** How much of the digest travels. 80 bits is unguessable and stays short in a payload. */
    private static final int SIGNATURE_HEX_CHARS = 20;

    private final ShAgentProperties properties;
    private final Clock clock;
    private final byte[] secret;

    public ShAgentConfirmService(ShAgentProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.secret = resolveSecret(properties.getConfirm().getSecret());
    }

    /**
     * Whether this change set destroys anything.
     *
     * <p>A {@code noop} delete ("already absent") is not destruction — nothing goes — and
     * requiring a confirmation for one would make re-running a converged document a
     * two-step operation forever.</p>
     */
    public boolean isDestructive(ShAgentChangeSet changeSet) {
        return !destructiveRows(changeSet).isEmpty();
    }

    /**
     * The token that unlocks this exact plan, or {@code null} when it destroys nothing.
     *
     * @param changeSet a plan produced by a dry run
     */
    public String tokenFor(ShAgentChangeSet changeSet) {
        List<String> rows = destructiveRows(changeSet);
        if (rows.isEmpty()) {
            return null;
        }
        long expiresAt = Instant.now(clock)
                .plusSeconds(Math.max(1, properties.getConfirm().getTtlSeconds()))
                .getEpochSecond();
        return expiresAt + "." + sign(expiresAt, rows);
    }

    /**
     * Verifies a token against a freshly computed plan.
     *
     * @return why it failed, or {@link Verdict#OK}
     */
    public Verdict verify(String presented, ShAgentChangeSet plan) {
        List<String> rows = destructiveRows(plan);
        if (rows.isEmpty()) {
            return Verdict.OK;
        }
        if (presented == null || presented.isBlank()) {
            return Verdict.MISSING;
        }
        int dot = presented.indexOf('.');
        if (dot <= 0) {
            return Verdict.MISMATCH;
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(presented.substring(0, dot));
        } catch (NumberFormatException e) {
            return Verdict.MISMATCH;
        }
        String signature = presented.substring(dot + 1);
        // Signature before expiry: a token whose signature does not verify is not a stale
        // token of ours, and reporting it as "expired" would send the caller re-planning
        // instead of looking at what it actually sent.
        if (!constantTimeEquals(signature, sign(expiresAt, rows))) {
            return Verdict.MISMATCH;
        }
        return Instant.now(clock).getEpochSecond() > expiresAt ? Verdict.EXPIRED : Verdict.OK;
    }

    /** How many objects a plan would destroy — the number the refusal quotes back. */
    public int destructiveCount(ShAgentChangeSet changeSet) {
        return destructiveRows(changeSet).size();
    }

    /** The addresses a plan would destroy, in plan order, for the refusal's detail line. */
    public List<String> destructiveAddresses(ShAgentChangeSet changeSet) {
        List<String> addresses = new ArrayList<>();
        for (Change change : changeSet == null || changeSet.results() == null
                ? List.<Change>of() : changeSet.results()) {
            if (isDestruction(change)) {
                addresses.add(change.address());
            }
        }
        return List.copyOf(addresses);
    }

    /**
     * The canonical, order-independent projection of what a plan destroys.
     *
     * <p>Sorted, so two plans that delete the same things in a different order share a
     * token — the order of a prune's output is fixed (P5) but a hand-written batch's is
     * the caller's, and making the token order-sensitive would reject a re-send that
     * differed only in how the caller listed its ops.</p>
     */
    private static List<String> destructiveRows(ShAgentChangeSet changeSet) {
        List<String> rows = new ArrayList<>();
        for (Change change : changeSet == null || changeSet.results() == null
                ? List.<Change>of() : changeSet.results()) {
            if (isDestruction(change)) {
                // The op and the address, and deliberately not the object id: the id is
                // only present when the caller asked for ids (SH121), so signing it would
                // make the token depend on a display flag — a plan taken with ids=false
                // would not unlock the same apply taken with ids=true.
                rows.add(change.op() + "|" + change.address());
            }
        }
        Collections.sort(rows);
        return rows;
    }

    private static boolean isDestruction(Change change) {
        return change != null && Change.DELETE.equals(change.action()) && change.address() != null;
    }

    private String sign(long expiresAt, List<String> rows) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            mac.update(Long.toString(expiresAt).getBytes(StandardCharsets.UTF_8));
            for (String row : rows) {
                mac.update((byte) '\n');
                mac.update(row.getBytes(StandardCharsets.UTF_8));
            }
            String hex = HexFormat.of().formatHex(mac.doFinal());
            return hex.substring(0, SIGNATURE_HEX_CHARS);
        } catch (java.security.GeneralSecurityException e) {
            // HmacSHA256 is required of every JVM; if it is genuinely absent, failing the
            // request is far better than falling back to something forgeable.
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }

    private static byte[] resolveSecret(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured.getBytes(StandardCharsets.UTF_8);
        }
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return random;
    }

    /** Length-independent comparison; the values are short and public-ish, but free is free. */
    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null || left.length() != right.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < left.length(); i++) {
            diff |= left.charAt(i) ^ right.charAt(i);
        }
        return diff == 0;
    }

    /** Why a presented confirm token was, or was not, accepted. */
    public enum Verdict {
        OK,
        /** Nothing was sent for a plan that destroys something. */
        MISSING,
        /** Well-formed and correctly signed, but issued too long ago. */
        EXPIRED,
        /** Not a token for this plan — usually the content changed underneath it. */
        MISMATCH
    }
}
