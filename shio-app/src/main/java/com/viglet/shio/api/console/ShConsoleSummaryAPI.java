/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.console;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.api.agent.ShAddressResolver;
import com.viglet.shio.api.agent.verify.ShAgentVerifyService;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.agent.dto.ShAgentResolved;
import com.viglet.shio.api.history.ShHistoryAPI;
import com.viglet.shio.api.review.ShReviewService;
import com.viglet.shio.persistence.repository.note.ShNoteRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * SH1050 — the console's home, in one request instead of four.
 *
 * <h2>What was wrong, and it was not the request count</h2>
 *
 * <p>Home fetched every site and every post type on mount and used one property of each — the
 * length — because there was no count endpoint. An instance with four hundred sites paid four
 * hundred rows to draw the number 400, on the first screen after logging in, and the tiles rendered
 * nothing until both collections had landed.</p>
 *
 * <h2>Composed, never a third contract</h2>
 *
 * <p>Everything here is a count query or a call into a service that already exists: the review
 * queue's own `pending()`, the same repositories every other reader uses, the same trail rows
 * `/api/v2/history` returns. This adds no new way to ask anything — P10 — and that is what lets
 * `shio status` print the same rows without a second implementation behind it.</p>
 *
 * <h2>It is under `/api/v2` on purpose</h2>
 *
 * <p>Which means SH1048's filter gives it an `ETag` and a `no-cache`, and a dashboard left open on
 * a second screen revalidates into a 304 rather than re-reading the whole thing. The endpoint was
 * designed after that filter for exactly this reason.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping("/api/v2/console")
@Tag(name = "Console", description = "What the console's home needs, in one answer")
public class ShConsoleSummaryAPI {

    /** Enough rows for the home page's activity list, and a ceiling on what this answer costs. */
    private static final int ACTIVITY_ROWS = 8;

    /** Enough runs to see what the agent has been doing, and few enough to read at a glance. */
    private static final int RUN_ROWS = 4;

    /** The incident window this counts. Bounded, for the reason the log itself is bounded. */
    private static final int INCIDENT_WINDOW = 50;

    /** SH1336 — the checks home runs: the two that read content and never render a page. */
    private static final String CHECKS = "content,routes";

    /** SH1336 — failing pages named in the inbox; the count beside them is the whole. */
    private static final int FAILING_ROWS = 5;

    private final ShSiteRepository shSiteRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShNoteRepository shNoteRepository;
    private final ShReviewService shReviewService;
    private final ShAgentIncidentLog shAgentIncidentLog;
    private final ShHistoryAPI shHistoryAPI;
    private final ShAgentVerifyService shAgentVerifyService;
    private final ShAddressResolver shAddressResolver;

    public ShConsoleSummaryAPI(ShSiteRepository shSiteRepository,
                               ShPostTypeRepository shPostTypeRepository,
                               ShPostUnifiedRepository shPostUnifiedRepository,
                               ShNoteRepository shNoteRepository,
                               ShReviewService shReviewService,
                               ShAgentIncidentLog shAgentIncidentLog,
                               ShHistoryAPI shHistoryAPI,
                               ShAgentVerifyService shAgentVerifyService,
                               ShAddressResolver shAddressResolver) {
        this.shAgentVerifyService = shAgentVerifyService;
        this.shAddressResolver = shAddressResolver;
        this.shSiteRepository = shSiteRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shNoteRepository = shNoteRepository;
        this.shReviewService = shReviewService;
        this.shAgentIncidentLog = shAgentIncidentLog;
        this.shHistoryAPI = shHistoryAPI;
    }

    @Operation(summary = "Counts, what is waiting for a person, and the most recent activity")
    @GetMapping("/summary")
    public ShConsoleSummary summary() {
        Instant now = Instant.now();
        var pending = shReviewService.pending();
        var attention = new ShConsoleSummary.Attention(
                pending.sessions(),
                pending.changes(),
                shPostUnifiedRepository.countScheduledBetween(now, now.plus(ShConsoleSummary.SOON)),
                shNoteRepository.countByResolvedFalse());
        return new ShConsoleSummary(
                shSiteRepository.count(),
                shPostTypeRepository.count(),
                version(),
                attention,
                runs(),
                new ShConsoleSummary.Diagnostics(
                        shAgentIncidentLog.recent(INCIDENT_WINDOW).size(),
                        shAgentIncidentLog.unpersisted()),
                shHistoryAPI.recent(ACTIVITY_ROWS, null, null, null, null, null, null, null).rows(),
                checks());
    }

    /**
     * SH1336 — verify's two checks that do not render, over every site this caller reads.
     *
     * <p>One pass per visit, which is what was measured to be affordable; scoping it to the pages
     * home lists cost more, because each address resolves separately. The findings come back
     * errors first (verify's own order), so the first rows per page are the errors.</p>
     */
    private ShConsoleSummary.Checks checks() {
        ShAgentReport report = shAgentVerifyService.verify(null, CHECKS, null);
        Map<String, ShConsoleSummary.Failing> byPage = new LinkedHashMap<>();
        for (ShAgentReport.Finding finding : report.findings()) {
            if (ShAgentReport.ERROR.equals(finding.severity()) && finding.address() != null
                    && byPage.size() < FAILING_ROWS) {
                byPage.computeIfAbsent(finding.address(), address -> new ShConsoleSummary.Failing(
                        address, idOf(address), finding.rule(), finding.message()));
            }
        }
        return new ShConsoleSummary.Checks(report.errors(), List.copyOf(byPage.values()));
    }

    /** The post an address names, for the editor link; at most {@link #FAILING_ROWS} lookups. */
    private String idOf(String address) {
        if (!address.startsWith("post:") && !address.startsWith("id:")) {
            return null;
        }
        // A finding about an address that no longer resolves (a duplicate URL is one) still belongs
        // in the inbox; it links to the search instead of to an editor.
        return shAddressResolver.find(ShAddress.parse(address)).map(ShAgentResolved::id).orElse(null);
    }

    /**
     * What the agent has been doing, newest first.
     *
     * <p>The review queue's own listing, projected to what a dashboard row needs. Sessions with
     * nothing pending are kept on purpose: "the agent ran and you have already dealt with it" is a
     * different fact from "the agent has not run", and a list that showed only the first would make
     * a quiet instance and a busy one look the same.</p>
     */
    private List<ShConsoleSummary.Run> runs() {
        return shReviewService.sessions(RUN_ROWS).stream()
                .map(session -> new ShConsoleSummary.Run(
                        session.session(), session.intent(), session.agentName(),
                        session.changes(), session.pending(),
                        session.lastSeen() == null ? null : session.lastSeen().toString()))
                .toList();
    }

    /**
     * The instance's version, read the way the footer reads it.
     *
     * <p>Duplicated from {@code ShSystemInfoAPI} rather than reached for: it is four lines off the
     * JAR manifest, and injecting a controller to call one private method is the coupling this
     * endpoint exists to avoid. The two can only disagree if the JVM does.</p>
     */
    private String version() {
        String version = getClass().getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }
}
