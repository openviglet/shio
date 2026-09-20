/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.console;

import java.time.Duration;
import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    /** How far ahead "soon" looks. A week is the horizon somebody plans a publish inside. */
    static final Duration SOON = Duration.ofDays(7);

    /** Enough rows for the home page's activity list, and a ceiling on what this answer costs. */
    private static final int ACTIVITY_ROWS = 8;

    private final ShSiteRepository shSiteRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShNoteRepository shNoteRepository;
    private final ShReviewService shReviewService;
    private final ShHistoryAPI shHistoryAPI;

    public ShConsoleSummaryAPI(ShSiteRepository shSiteRepository,
                               ShPostTypeRepository shPostTypeRepository,
                               ShPostUnifiedRepository shPostUnifiedRepository,
                               ShNoteRepository shNoteRepository,
                               ShReviewService shReviewService,
                               ShHistoryAPI shHistoryAPI) {
        this.shSiteRepository = shSiteRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shNoteRepository = shNoteRepository;
        this.shReviewService = shReviewService;
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
                shPostUnifiedRepository.countScheduledBetween(now, now.plus(SOON)),
                shNoteRepository.countByResolvedFalse());
        return new ShConsoleSummary(
                shSiteRepository.count(),
                shPostTypeRepository.count(),
                version(),
                attention,
                shHistoryAPI.recent(ACTIVITY_ROWS, null, null, null, null, null, null, null).rows());
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
