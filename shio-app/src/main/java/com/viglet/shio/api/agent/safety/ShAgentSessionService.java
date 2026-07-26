/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.safety;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAgentProperties;
import com.viglet.shio.api.agent.ShAgentSiteAccess;
import com.viglet.shio.api.agent.safety.dto.ShAgentSessionInfo;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

/**
 * Answers "what am I, and what may I do?" for the current credential (SH106).
 *
 * <p>Design law P2 applied to the safety surface. Without this an agent discovers its own
 * bounds by hitting them: it writes a twelve-page document and learns at the last op that
 * it may not publish, or it plans a prune and learns that deletions need a second step
 * after the first one is refused. One cheap call at the start of a session replaces both
 * discoveries, and the answer is short enough that making it is never the wrong choice.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Transactional(readOnly = true)
public class ShAgentSessionService {

    private final ShAgentBudgetService budget;
    private final ShAgentSiteAccess siteAccess;
    private final ShAgentProperties properties;

    public ShAgentSessionService(ShAgentBudgetService budget, ShAgentSiteAccess siteAccess,
                                 ShAgentProperties properties) {
        this.budget = budget;
        this.siteAccess = siteAccess;
        this.properties = properties;
    }

    public ShAgentSessionInfo info() {
        ShAgentBudgetService.Snapshot snapshot = budget.snapshot();
        ShAgentGrant grant = ShAgentSessionContext.grant();
        var token = ShApiTokenContext.current();

        return new ShAgentSessionInfo(
                snapshot.session(),
                token.map(context -> context.scope().name()).orElse("console"),
                principal(),
                grant.mayPublish(),
                properties.getConfirm().isRequired(),
                snapshot.metered(),
                snapshot.calls(),
                snapshot.maxCalls(),
                snapshot.writes(),
                snapshot.maxWrites(),
                siteAccess.readable().stream().map(ShSite::getName).toList(),
                notes(snapshot));
    }

    /**
     * What this answer cannot promise.
     *
     * <p>Each note exists because its absence would make a true statement read as a
     * stronger one — the same rule SH103's diagnostics window follows. "You have 480 writes
     * left" is true and means nothing across a restart, and a caller that believed
     * otherwise would be surprised in the direction that costs content.</p>
     */
    private List<String> notes(ShAgentBudgetService.Snapshot snapshot) {
        List<String> notes = new ArrayList<>();
        if (snapshot.session() == null) {
            notes.add("this request carries no session id, so its writes are attributed to the "
                    + "principal alone and are harder to review as a unit; send "
                    + ShAgentSessionContext.SESSION_HEADER + " to keep one id across a run");
        }
        if (snapshot.metered()) {
            notes.add("the counters are held in memory: a restart resets them, and an idle "
                    + "session is forgotten after " + properties.getBudget().getSessionTtlMinutes()
                    + " minutes");
        } else {
            notes.add("this credential is not metered — ceilings apply to AGENT-scope tokens "
                    + "with a session id, not to a console credential");
        }
        if (!ShAgentSessionContext.grant().mayPublish()) {
            notes.add("writes land in DRAFT and stay there: this credential may not publish, so "
                    + "hand finished work to a human rather than trying");
        }
        return List.copyOf(notes);
    }

    private static String principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }
}
