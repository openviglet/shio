/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.model.agent.ShAgentRun;
import com.viglet.shio.persistence.repository.agent.ShAgentRunRepository;

/**
 * Who a session id is, for the two surfaces that read the audit trail (SH999).
 *
 * <h2>Written once, on the introduction</h2>
 *
 * <p>MCP's {@code initialize} is the one call in the protocol whose whole purpose is a client saying
 * what it is, and it carried a name this product discarded. {@link #introduce} keeps it. A second
 * {@code initialize} on the same session id updates the row rather than failing: a client that
 * reconnects with its own id is the same run, and the newest thing it said about itself is the true
 * one.</p>
 *
 * <h2>Why the absent case is absent and not "unknown"</h2>
 *
 * <p>The REST agent surface accepts a session id it did not mint (SH106), so a caller can write under
 * a session that never introduced itself. {@link #names} then has nothing for that id, and both
 * readers report no name rather than inventing one — SH190's rule, and here it is load-bearing: a row
 * saying the agent was called "unknown" is a row a reviewer cannot tell from one written by an agent
 * that is called that.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShAgentRunService {

    /** The transports that mint or accept a session (SH106), as the row records them. */
    public static final String MCP = "mcp";
    public static final String REST = "rest";

    /** The intent column's width — a sentence, not a plan. See {@code v2026.3.38}. */
    public static final int INTENT_MAX = 1000;

    private final ShAgentRunRepository shAgentRunRepository;

    public ShAgentRunService(ShAgentRunRepository shAgentRunRepository) {
        this.shAgentRunRepository = shAgentRunRepository;
    }

    /**
     * Record what this run calls itself.
     *
     * <p>Never throws: a client that introduced itself badly still gets to work, because the name is
     * an attribution nicety and refusing {@code initialize} over it would make an audit improvement
     * into an outage. A write that fails leaves the run nameless, which is the state every pre-SH999
     * run is in anyway.</p>
     *
     * @param session the session id the transport minted or accepted; nothing is written for a
     *                console session, which is a person and already has a name
     */
    @Transactional
    public void introduce(String session, String clientName, String clientVersion,
                          String transport, String actor) {
        introduce(session, clientName, clientVersion, transport, actor, null);
    }

    /**
     * The same, with what the run says it is for (SH1000).
     *
     * <p>{@code null} leaves whatever is on record, which is what makes the header safe to send on
     * every request and the intent safe to declare once: a later call that does not repeat the
     * sentence must not erase it. Only a non-blank value replaces one.</p>
     *
     * @param intent one sentence, clipped to the column rather than refused — see {@link #INTENT_MAX}
     */
    @Transactional
    public void introduce(String session, String clientName, String clientVersion,
                          String transport, String actor, String intent) {
        if (session == null || session.isBlank() || ShAgentSessionContext.isConsole(session)) {
            return;
        }
        try {
            ShAgentRun run = shAgentRunRepository.findById(session).orElseGet(ShAgentRun::new);
            if (run.getId() == null) {
                run.setId(session);
                run.setStartedAt(Instant.now());
            }
            if (clientName != null) {
                run.setClientName(blankToNull(clientName));
            }
            if (clientVersion != null) {
                run.setClientVersion(blankToNull(clientVersion));
            }
            if (transport != null) {
                run.setTransport(transport);
            }
            if (actor != null) {
                run.setActor(blankToNull(actor));
            }
            String declared = blankToNull(intent);
            if (declared != null) {
                // Clipped, not refused: SH544's rule, and it binds harder here than anywhere. The
                // record describes the run and must never be what stops it — a client whose premise
                // ran long gets a shorter premise, not a failed handshake.
                run.setIntent(declared.length() > INTENT_MAX
                        ? declared.substring(0, INTENT_MAX) : declared);
            }
            shAgentRunRepository.save(run);
        } catch (RuntimeException ignored) {
            // See the javadoc: the name is worth having and never worth a refused initialize.
        }
    }

    /**
     * Declare only what a run is for, leaving everything else on the row alone (SH1000).
     *
     * <p>The REST surface's shape: a caller sends {@code X-Shio-Intent} and this is all it said.
     * Writing on every request carrying the header is the caller's choice and costs a read and a
     * write; a client that declares once pays it once, which is what the CLI does.</p>
     */
    @Transactional
    public void declare(String session, String intent, String actor) {
        introduce(session, null, null, REST, actor, intent);
    }

    /** What these runs said they were for, in one query, insertion-ordered for {@link #names}'s reason. */
    @Transactional(readOnly = true)
    public Map<String, String> intents(Collection<String> sessions) {
        Map<String, String> declared = new LinkedHashMap<>();
        if (sessions == null || sessions.isEmpty()) {
            return declared;
        }
        for (ShAgentRun run : shAgentRunRepository.findByIdIn(sessions)) {
            if (run.getIntent() != null) {
                declared.put(run.getId(), run.getIntent());
            }
        }
        return declared;
    }

    /** The runs themselves, for the surface that reports them (SH1000). */
    @Transactional(readOnly = true)
    public List<ShAgentRun> runs(Collection<String> sessions) {
        return sessions == null || sessions.isEmpty() ? List.of()
                : shAgentRunRepository.findByIdIn(sessions);
    }

    /** What this run calls itself, or empty when it never said. */
    @Transactional(readOnly = true)
    public Optional<String> name(String session) {
        return names(Set.of(session == null ? "" : session)).entrySet().stream()
                .map(Map.Entry::getValue).findFirst();
    }

    /**
     * The names of a page's runs, in one query.
     *
     * <p>Insertion-ordered and never {@code Map.copyOf}: a caller reading this to build a response
     * would otherwise order its output by a hash, and two runs of the same feed would serialize
     * differently (P5).</p>
     */
    @Transactional(readOnly = true)
    public Map<String, String> names(Collection<String> sessions) {
        Map<String, String> named = new LinkedHashMap<>();
        if (sessions == null || sessions.isEmpty()) {
            return named;
        }
        for (ShAgentRun run : shAgentRunRepository.findByIdIn(sessions)) {
            if (run.getClientName() != null) {
                named.put(run.getId(), run.getClientName());
            }
        }
        return named;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
