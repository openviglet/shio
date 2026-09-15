/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.release;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.release.ShRelease;
import com.viglet.shio.persistence.model.release.ShReleaseItem;
import com.viglet.shio.persistence.repository.release.ShReleaseItemRepository;
import com.viglet.shio.persistence.repository.release.ShReleaseRepository;

/**
 * Releases: a named set of posts in one site that goes live as one unit (SH993).
 *
 * <p>The domain half, with no knowledge of the batch vocabulary. The {@code release.*} ops in
 * {@code ShAgentBatchService} resolve addresses and plan, and call this to write; the listing in
 * {@code ShAgentReadService} calls it to read. One class answers what a release is, so the two cannot
 * disagree about which posts it holds.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShReleaseService {

    /**
     * A release name: one segment, because it is both the last part of {@code release:<site>/<name>}
     * and the file name a release projects to, so it has to be safe as either.
     */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");

    private final ShReleaseRepository shReleaseRepository;
    private final ShReleaseItemRepository shReleaseItemRepository;

    public ShReleaseService(ShReleaseRepository shReleaseRepository,
                            ShReleaseItemRepository shReleaseItemRepository) {
        this.shReleaseRepository = shReleaseRepository;
        this.shReleaseItemRepository = shReleaseItemRepository;
    }

    /** What a reader is told about one release. */
    public record View(String name, String state, String description, Instant scheduledAt,
                       List<String> postIds) {
    }

    /** The name, checked, or a teaching 400. */
    public static String requireName(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new BadReleaseNameException(name);
        }
        return name;
    }

    @Transactional(readOnly = true)
    public Optional<ShRelease> find(String siteId, String name) {
        return siteId == null ? Optional.empty() : shReleaseRepository.findBySiteIdAndName(siteId, name);
    }

    /** The release, or a 404 naming the site's releases with the nearest match. */
    @Transactional(readOnly = true, noRollbackFor = ShTeachingException.class)
    public ShRelease require(String siteId, String siteName, String name) {
        return find(siteId, name).orElseThrow(() -> new UnknownReleaseException(siteName, name,
                siteId == null ? List.of()
                        : shReleaseRepository.findBySiteIdOrderByNameAsc(siteId).stream()
                                .map(ShRelease::getName).toList()));
    }

    /** The post ids a release holds, in the order they were added. */
    @Transactional(readOnly = true)
    public List<String> postIds(String releaseId) {
        return shReleaseItemRepository.findByReleaseIdOrderByAddedAtAsc(releaseId).stream()
                .map(ShReleaseItem::getPostId).toList();
    }

    @Transactional(readOnly = true)
    public View view(ShRelease release) {
        return new View(release.getName(), release.getState(), release.getDescription(),
                release.getScheduledAt(), postIds(release.getId()));
    }

    @Transactional
    public ShRelease create(String siteId, String name, String description, String actor) {
        Instant now = Instant.now();
        ShRelease release = new ShRelease();
        release.setSiteId(siteId);
        release.setName(requireName(name));
        release.setDescription(description);
        release.setState(ShRelease.State.PLANNED.name());
        release.setActor(actor);
        release.setCreatedAt(now);
        release.setModifiedAt(now);
        return shReleaseRepository.save(release);
    }

    @Transactional
    public ShRelease describe(ShRelease release, String description, String actor) {
        release.setDescription(description);
        return touched(release, actor);
    }

    /** Adds the posts not already held, and returns the ids it added. */
    @Transactional
    public List<String> add(ShRelease release, Collection<String> postIds, String actor) {
        requireOpen(release);
        Set<String> held = new LinkedHashSet<>(postIds(release.getId()));
        Instant now = Instant.now();
        List<String> added = postIds.stream().distinct().filter(id -> !held.contains(id)).toList();
        added.forEach(id -> {
            ShReleaseItem item = new ShReleaseItem();
            item.setReleaseId(release.getId());
            item.setPostId(id);
            item.setAddedAt(now);
            shReleaseItemRepository.save(item);
        });
        if (!added.isEmpty()) {
            touched(release, actor);
        }
        return added;
    }

    /** Removes the posts it holds from the ones named, and returns the ids it removed. */
    @Transactional
    public List<String> remove(ShRelease release, Collection<String> postIds, String actor) {
        requireOpen(release);
        Set<String> named = new LinkedHashSet<>(postIds);
        List<ShReleaseItem> gone = shReleaseItemRepository.findByReleaseIdOrderByAddedAtAsc(release.getId())
                .stream().filter(item -> named.contains(item.getPostId())).toList();
        shReleaseItemRepository.deleteAll(gone);
        if (!gone.isEmpty()) {
            touched(release, actor);
        }
        return gone.stream().map(ShReleaseItem::getPostId).toList();
    }

    /** SH993 — the release runs at this instant instead of now; the SH15 sweep fires it. */
    @Transactional
    public ShRelease scheduleAt(ShRelease release, Instant when, String actor) {
        requireRunnable(release);
        release.setState(ShRelease.State.SCHEDULED.name());
        release.setScheduledAt(when);
        return touched(release, actor);
    }

    /** SH993 — the pending run is called off, and the release goes back to being assembled. */
    @Transactional
    public ShRelease unschedule(ShRelease release, String actor) {
        release.setState(ShRelease.State.PLANNED.name());
        release.setScheduledAt(null);
        return touched(release, actor);
    }

    /**
     * SH993 — the release ran: each item keeps the revision its post was at, which is where
     * {@code release.rollback} will return to, and the release becomes the record of what went live.
     */
    @Transactional
    public ShRelease published(ShRelease release, Map<String, Integer> anchors, String actor) {
        shReleaseItemRepository.findByReleaseIdOrderByAddedAtAsc(release.getId()).forEach(item -> {
            item.setVersionBefore(anchors.get(item.getPostId()));
            shReleaseItemRepository.save(item);
        });
        release.setState(ShRelease.State.PUBLISHED.name());
        release.setScheduledAt(null);
        return touched(release, actor);
    }

    /** SH993 — the releases the sweep should run now, oldest instant first. */
    @Transactional(readOnly = true)
    public List<ShRelease> dueRuns(Instant now) {
        return shReleaseRepository.findByStateAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                ShRelease.State.SCHEDULED.name(), now);
    }

    /** A release runs once: PLANNED or SCHEDULED, never one that already went live or was undone. */
    public static void requireRunnable(ShRelease release) {
        String state = release.getState();
        if (!ShRelease.State.PLANNED.name().equals(state)
                && !ShRelease.State.SCHEDULED.name().equals(state)) {
            throw new ReleaseClosedException(release.getName(), state, false);
        }
    }

    /**
     * A release that ran is a record of what went live together, so its membership is closed. Only a
     * PLANNED or SCHEDULED release takes posts in or out.
     */
    public static void requireOpen(ShRelease release) {
        String state = release.getState();
        if (!ShRelease.State.PLANNED.name().equals(state) && !ShRelease.State.SCHEDULED.name().equals(state)) {
            throw new ReleaseClosedException(release.getName(), state, true);
        }
    }

    private ShRelease touched(ShRelease release, String actor) {
        release.setModifiedAt(Instant.now());
        release.setActor(actor);
        return shReleaseRepository.save(release);
    }

    /** A 400 for a name that cannot be a release's. */
    public static class BadReleaseNameException extends ShTeachingException {

        public BadReleaseNameException(String sent) {
            super(ShProblem.badRequest("bad-release-name",
                            "'" + (sent == null ? "" : sent) + "' is not a release name")
                    .withDetail("A release name is one segment of letters, digits, `.`, `_` and `-`, "
                            + "starting with a letter or digit, up to 100 characters. It is the last "
                            + "part of the address and the name of the file the release projects to.")
                    .withFix("Pick a name like `spring-launch`.")
                    .withExample("release:Blog/spring-launch"));
        }
    }

    /** A 404 for a release the site does not have. */
    public static class UnknownReleaseException extends ShTeachingException {

        public UnknownReleaseException(String site, String name, List<String> names) {
            super(ShProblem.of("unknown-release", HttpStatus.NOT_FOUND,
                            "No release '" + name + "' in " + site)
                    .withDetail(names.isEmpty() ? site + " has no releases yet."
                            : site + " has " + names.size() + " release(s).")
                    .withAllowed(names)
                    .withDidYouMeanFrom(name)
                    .withFix("Create it first with release.upsert, in the same batch or before.")
                    .withExample("{\"op\":\"release.upsert\",\"address\":\"release:" + site + "/"
                            + name + "\"}"));
        }
    }

    /**
     * A 409 for changing or re-running a release that already ran.
     *
     * <p>One exception, two messages, for {@code BadScheduleException}'s reason: the state is the same
     * fact and what differs is what the caller was trying to do with it. A run says <em>runs once</em>
     * rather than <em>takes no posts</em>, because an agent reading the membership message after a
     * second {@code release.run} would go looking for the post it thought it was adding.</p>
     */
    public static class ReleaseClosedException extends ShTeachingException {

        public ReleaseClosedException(String name, String state, boolean membership) {
            super(ShProblem.conflict("release-closed",
                            "release '" + name + "' is " + state
                                    + (membership ? " and takes no posts in or out" : " and runs once"))
                    .withDetail("A release that ran is the record of what went live together, so "
                            + (membership ? "its posts are fixed." : "running it again would claim a "
                                    + "second launch of the same set."))
                    .withFix("Plan a new release for the next change"
                            + (membership ? "." : ", or roll this one back."))
                    .withExample("{\"op\":\"release.upsert\",\"address\":\"release:<site>/<new-name>\"}"));
        }
    }
}
