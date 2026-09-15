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
        if (session == null || session.isBlank() || ShAgentSessionContext.isConsole(session)) {
            return;
        }
        try {
            ShAgentRun run = shAgentRunRepository.findById(session).orElseGet(ShAgentRun::new);
            if (run.getId() == null) {
                run.setId(session);
                run.setStartedAt(Instant.now());
            }
            run.setClientName(blankToNull(clientName));
            run.setClientVersion(blankToNull(clientVersion));
            run.setTransport(transport);
            run.setActor(blankToNull(actor));
            shAgentRunRepository.save(run);
        } catch (RuntimeException ignored) {
            // See the javadoc: the name is worth having and never worth a refused initialize.
        }
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
