/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.gate;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAgentSessionContext;
import com.viglet.shio.api.agent.ShAgentSiteAccess;
import com.viglet.shio.persistence.model.gate.ShPublishGate;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.gate.ShPublishGateRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * The wire half of publish gates (SH997): reading a site's gates, and declaring one.
 *
 * <p>Kept apart from {@link ShPublishGateService}, which evaluates them, for the reason the release
 * service and its runner are apart: what a gate <em>is</em> and what a gate <em>does</em> are two
 * questions, and the second one runs inside a publish while the first answers an HTTP call.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShAgentGateApiService {

    private final ShPublishGateRepository shPublishGateRepository;
    private final ShPublishGateService shPublishGateService;
    private final ShAgentSiteAccess siteAccess;
    private final ObjectMapper objectMapper;

    public ShAgentGateApiService(ShPublishGateRepository shPublishGateRepository,
                                 ShPublishGateService shPublishGateService,
                                 ShAgentSiteAccess siteAccess,
                                 ObjectMapper objectMapper) {
        this.shPublishGateRepository = shPublishGateRepository;
        this.shPublishGateService = shPublishGateService;
        this.siteAccess = siteAccess;
        this.objectMapper = objectMapper;
    }

    /** A site's gates, in name order — the same order they are evaluated in. */
    @Transactional(readOnly = true)
    public List<ShAgentGate> gates(String site) {
        ShSite found = siteAccess.require(site);
        return shPublishGateRepository.findBySiteIdOrderByNameAsc(found.getId()).stream()
                .map(this::wire)
                .toList();
    }

    /**
     * Declares one gate, replacing what that name said.
     *
     * <p>Idempotent by {@code (site, name)}, like a note: re-pushing a file that has not changed must
     * rewrite one row rather than add a second, because the tree is the declaration and a push is how
     * often it is stated.</p>
     */
    @Transactional
    public ShAgentGate put(String site, String name, ShAgentGate body) {
        ShSite found = siteAccess.require(site);
        String definition = objectMapper.writeValueAsString(
                java.util.Map.of("checks", body == null || body.checks() == null
                        ? List.of() : body.checks()));
        return wire(shPublishGateService.upsert(found.getId(), name, definition,
                body == null || body.enabled() == null || body.enabled(),
                ShAgentSessionContext.actor()));
    }

    /** SH997 — the CI form's answer: the same evaluation a publish makes, without publishing. */
    @Transactional(readOnly = true)
    public List<ShPublishGateService.Outcome> check(String site, String address) {
        ShSite found = siteAccess.require(site);
        return shPublishGateService.evaluate(found.getName(), found.getId(), address);
    }

    private ShAgentGate wire(ShPublishGate gate) {
        List<ShPublishGateService.Step> steps = shPublishGateService.steps(gate);
        return new ShAgentGate(gate.getName(), gate.isEnabled(), steps.stream()
                .map(step -> new ShAgentGate.Check(step.check().wire(), step.severity()))
                .toList());
    }
}
