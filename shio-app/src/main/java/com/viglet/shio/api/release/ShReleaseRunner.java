/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.release;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.post.ShPostDrift;
import com.viglet.shio.api.post.revision.ShPostRevisionService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.release.ShRelease;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Running a release: every post in it goes live in one transaction (SH993).
 *
 * <p>One implementation, two callers — the {@code release.run} op and the SH15 sweep firing a
 * scheduled release — because a launch that behaved differently at midnight than it did on the call
 * would be the worst kind of surprise to debug. The op does the authorisation it owes before calling
 * in; this class does the publishing.</p>
 *
 * <p><b>Atomicity is the whole point.</b> The method is one transaction, so a post that refuses takes
 * the rest of the launch back with it rather than leaving a site half-live — which is the state
 * SH993 exists to make unreachable, and the one a per-post schedule produced.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShReleaseRunner {

    private final ShReleaseService shReleaseService;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostUnifiedWriteService shPostUnifiedWriteService;
    private final ShPostRevisionService shPostRevisionService;
    private final ObjectMapper objectMapper;

    public ShReleaseRunner(ShReleaseService shReleaseService,
                           ShPostUnifiedRepository shPostUnifiedRepository,
                           ShPostUnifiedWriteService shPostUnifiedWriteService,
                           ShPostRevisionService shPostRevisionService,
                           ObjectMapper objectMapper) {
        this.shReleaseService = shReleaseService;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.shPostRevisionService = shPostRevisionService;
        this.objectMapper = objectMapper;
    }

    /**
     * What running this release would do, post by post, in the order they were added.
     *
     * @param publishes the post ids whose live page would change
     * @param unchanged the post ids already live with exactly this content, which publish as noops
     * @param missing   ids whose draft is gone — the release names a post that was deleted
     */
    public record Plan(List<String> publishes, List<String> unchanged, List<String> missing) {

        public int size() {
            return publishes.size() + unchanged.size() + missing.size();
        }
    }

    /** The plan, written by nothing: the same read the run makes, one step earlier. */
    @Transactional(readOnly = true)
    public Plan plan(ShRelease release) {
        List<String> publishes = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String postId : shReleaseService.postIds(release.getId())) {
            Optional<ShPostUnified> draft = shPostUnifiedRepository
                    .findByIdAndState(postId, ShPostState.DRAFT);
            if (draft.isEmpty()) {
                missing.add(postId);
                continue;
            }
            Optional<ShPostUnified> live = shPostUnifiedRepository
                    .findByIdAndState(postId, ShPostState.PUBLISHED);
            if (live.isPresent() && ShPostDrift.sameContent(objectMapper, draft.get(), live.get())) {
                unchanged.add(postId);
            } else {
                publishes.add(postId);
            }
        }
        return new Plan(List.copyOf(publishes), List.copyOf(unchanged), List.copyOf(missing));
    }

    /**
     * Publishes the release: every post that would change goes live, each item keeps the revision it
     * was at as its rollback anchor, and the release becomes PUBLISHED.
     *
     * <p>The anchor is read <b>before</b> the publish and for every item, including the ones that
     * publish as noops: a rollback has to be able to put the whole set back, and an item with no
     * anchor because nothing about it changed would be the one page a rollback silently skipped.</p>
     *
     * @return the plan it carried out, so the caller can report per post what happened
     */
    @Transactional
    public Plan run(ShRelease release, String actor) {
        ShReleaseService.requireRunnable(release);
        Plan plan = plan(release);
        Map<String, Integer> anchors = new LinkedHashMap<>();
        for (String postId : shReleaseService.postIds(release.getId())) {
            shPostRevisionService.latestVersion(postId).ifPresent(version -> anchors.put(postId, version));
        }
        plan.publishes().forEach(postId -> shPostUnifiedWriteService.publish(postId, actor));
        shReleaseService.published(release, anchors, actor);
        return plan;
    }
}
