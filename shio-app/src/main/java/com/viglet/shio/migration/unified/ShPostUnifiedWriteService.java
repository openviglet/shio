/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.migration.unified;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.object.ShObjectPublishStatus;
import com.viglet.shio.object.ShObjectType;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.post.unified.ShPostUnifiedId;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.webhook.ShWebhookEventType;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.webhook.ShPostLifecycleEvent;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Stand-alone writer for the unified post model. No legacy bridge — every
 * create / update / publish / unpublish / delete writes directly against
 * {@code sh_post_unified} and its composite key.
 */
@Service
public class ShPostUnifiedWriteService {

    /** Recorded as the {@code publisher} for transitions fired by the SH15 sweep. */
    private static final String SCHEDULER_ACTOR = "scheduler";

    private final ShFolderRepository shFolderRepository;
    private final ShSiteRepository shSiteRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    /** Self-reference so the scheduled sweep re-enters publish/unpublish through the
     *  transactional proxy instead of a bare {@code this} call (S6809). */
    private final ObjectProvider<ShPostUnifiedWriteService> self;

    public ShPostUnifiedWriteService(ShFolderRepository shFolderRepository,
                                     ShSiteRepository shSiteRepository,
                                     ShPostTypeRepository shPostTypeRepository,
                                     ShPostUnifiedRepository shPostUnifiedRepository,
                                     ObjectMapper objectMapper,
                                     ApplicationEventPublisher eventPublisher,
                                     ObjectProvider<ShPostUnifiedWriteService> self) {
        this.shFolderRepository = shFolderRepository;
        this.shSiteRepository = shSiteRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.self = self;
    }

    private ShFolder resolveContainerFolder(String containerId) {
        return shFolderRepository.findById(containerId).orElseGet(() -> {
            ShSite site = shSiteRepository.findById(containerId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Folder or site not found: " + containerId));
            return shFolderRepository.findByShSiteAndRootFolder(site, (byte) 1).stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Site " + containerId + " has no root folder"));
        });
    }

    @Transactional
    public ShPostUnified create(CreateRequest req) {
        ShFolder folder = resolveContainerFolder(req.folderId());
        ShPostType postType = shPostTypeRepository.findById(req.postTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Post type not found: " + req.postTypeId()));

        ShPostState state = req.state() != null ? req.state() : ShPostState.DRAFT;

        ShPostUnified row = new ShPostUnified();
        row.setState(state);
        row.setShFolder(folder);
        row.setShSite(folder.getShSite());
        row.setShPostType(postType);
        row.setOwner(req.owner());
        row.setDate(Instant.now());
        row.setModifiedDate(Instant.now());
        row.setPublished(state == ShPostState.PUBLISHED);
        row.setPublishStatus(state == ShPostState.PUBLISHED
                ? ShObjectPublishStatus.PUBLISH : ShObjectPublishStatus.DRAFT);
        if (state == ShPostState.PUBLISHED) row.setPublicationDate(Instant.now());

        String dataJson = writeJson(req.data() != null ? req.data() : objectMapper.createObjectNode());
        row.setData(dataJson);

        applyTitleAndSummary(row, req.data(), postType);

        return shPostUnifiedRepository.save(row);
    }

    @Transactional
    public ShPostUnified update(String postId, UpdateRequest req) {
        ShPostState state = req.state() != null ? req.state() : ShPostState.DRAFT;
        ShPostUnified row = shPostUnifiedRepository.findByIdAndState(postId, state)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Post not found: " + postId + " state=" + state));

        if (req.data() != null) {
            row.setData(writeJson(req.data()));
            applyTitleAndSummary(row, req.data(), row.getShPostType());
        }
        row.setModifiedDate(Instant.now());
        if (req.modifier() != null) row.setModifier(req.modifier());
        row.setPublished(state == ShPostState.PUBLISHED);
        row.setPublishStatus(state == ShPostState.PUBLISHED
                ? ShObjectPublishStatus.PUBLISH : ShObjectPublishStatus.DRAFT);

        return shPostUnifiedRepository.save(row);
    }

    @Transactional
    public ShPostUnified publish(String postId, String publisher) {
        ShPostUnified draft = shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + postId));

        shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED)
                .ifPresent(shPostUnifiedRepository::delete);

        ShPostUnified pub = new ShPostUnified();
        pub.setId(postId);
        pub.setState(ShPostState.PUBLISHED);
        pub.setShFolder(draft.getShFolder());
        pub.setShSite(draft.getShSite());
        pub.setShPostType(draft.getShPostType());
        pub.setTitle(draft.getTitle());
        pub.setSummary(draft.getSummary());
        pub.setFurl(draft.getFurl());
        pub.setOwner(draft.getOwner());
        pub.setDate(draft.getDate());
        pub.setModifiedDate(Instant.now());
        pub.setPublisher(publisher);
        pub.setPublicationDate(Instant.now());
        pub.setPublished(true);
        pub.setPublishStatus(ShObjectPublishStatus.PUBLISH);
        pub.setData(draft.getData());
        ShPostUnified saved = shPostUnifiedRepository.save(pub);
        publishLifecycle(ShWebhookEventType.PUBLISH, saved);
        return saved;
    }

    /**
     * Revert the editable {@code DRAFT} row to the last {@code PUBLISHED}
     * snapshot, discarding any unpublished edits (SH14 "restore version"). If no
     * draft row exists, one is re-created from the published row. The public
     * content (the published row) is left untouched, so no webhook fires.
     */
    @Transactional
    public ShPostUnified restoreDraftFromPublished(String postId, String modifier) {
        ShPostUnified published = shPostUnifiedRepository
                .findByIdAndState(postId, ShPostState.PUBLISHED)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No published version to restore from: " + postId));

        ShPostUnified draft = shPostUnifiedRepository
                .findByIdAndState(postId, ShPostState.DRAFT)
                .orElseGet(() -> {
                    ShPostUnified d = new ShPostUnified();
                    d.setId(postId);
                    d.setState(ShPostState.DRAFT);
                    d.setShFolder(published.getShFolder());
                    d.setShSite(published.getShSite());
                    d.setShPostType(published.getShPostType());
                    d.setOwner(published.getOwner());
                    d.setDate(published.getDate());
                    return d;
                });

        draft.setData(published.getData());
        draft.setTitle(published.getTitle());
        draft.setSummary(published.getSummary());
        draft.setFurl(published.getFurl());
        draft.setModifiedDate(Instant.now());
        if (modifier != null) draft.setModifier(modifier);
        draft.setPublished(false);
        draft.setPublishStatus(ShObjectPublishStatus.DRAFT);
        return shPostUnifiedRepository.save(draft);
    }

    /**
     * Set (or clear) the scheduled publish/unpublish timestamps on the editable
     * {@code DRAFT} row (SH15). A {@code null} value clears that schedule. If no
     * draft row exists, one is re-created from the published row so the sweep has
     * something to scan. Returns the updated draft.
     */
    @Transactional
    public ShPostUnified schedule(String postId, Instant publishAt, Instant unpublishAt, String modifier) {
        ShPostUnified draft = shPostUnifiedRepository
                .findByIdAndState(postId, ShPostState.DRAFT)
                .orElseGet(() -> draftFromPublished(postId));

        draft.setPublishScheduledDate(publishAt);
        draft.setUnpublishScheduledDate(unpublishAt);
        draft.setModifiedDate(Instant.now());
        if (modifier != null) draft.setModifier(modifier);
        return shPostUnifiedRepository.save(draft);
    }

    /** Ids of drafts whose scheduled publish instant is now due (SH15 sweep). */
    @Transactional(readOnly = true)
    public List<String> findScheduledPublishDue(Instant now) {
        return shPostUnifiedRepository
                .findByStateAndPublishScheduledDateLessThanEqual(ShPostState.DRAFT, now)
                .stream().map(ShPostUnified::getId).toList();
    }

    /** Ids of drafts whose scheduled unpublish instant is now due (SH15 sweep). */
    @Transactional(readOnly = true)
    public List<String> findScheduledUnpublishDue(Instant now) {
        return shPostUnifiedRepository
                .findByStateAndUnpublishScheduledDateLessThanEqual(ShPostState.DRAFT, now)
                .stream().map(ShPostUnified::getId).toList();
    }

    /**
     * Fire a due scheduled publish: clear the schedule field first (same tx, so it
     * cannot re-fire) then delegate to {@link #publish} — which emits the SH12
     * {@code PUBLISH} webhook exactly as a manual publish would.
     */
    @Transactional
    public void publishScheduled(String postId) {
        shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
                .ifPresent(draft -> {
                    draft.setPublishScheduledDate(null);
                    shPostUnifiedRepository.save(draft);
                });
        self.getObject().publish(postId, SCHEDULER_ACTOR);
    }

    /**
     * Fire a due scheduled unpublish: clear the schedule field then delegate to
     * {@link #unpublish} (a no-op + no webhook if the post is not published).
     */
    @Transactional
    public void unpublishScheduled(String postId) {
        shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
                .ifPresent(draft -> {
                    draft.setUnpublishScheduledDate(null);
                    shPostUnifiedRepository.save(draft);
                });
        self.getObject().unpublish(postId);
    }

    /** Re-create a {@code DRAFT} row from the {@code PUBLISHED} snapshot (shared by restore/schedule). */
    private ShPostUnified draftFromPublished(String postId) {
        ShPostUnified published = shPostUnifiedRepository
                .findByIdAndState(postId, ShPostState.PUBLISHED)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No draft or published version for: " + postId));
        ShPostUnified draft = new ShPostUnified();
        draft.setId(postId);
        draft.setState(ShPostState.DRAFT);
        draft.setShFolder(published.getShFolder());
        draft.setShSite(published.getShSite());
        draft.setShPostType(published.getShPostType());
        draft.setOwner(published.getOwner());
        draft.setDate(published.getDate());
        draft.setData(published.getData());
        draft.setTitle(published.getTitle());
        draft.setSummary(published.getSummary());
        draft.setFurl(published.getFurl());
        draft.setPublished(false);
        draft.setPublishStatus(ShObjectPublishStatus.DRAFT);
        return draft;
    }

    @Transactional
    public void unpublish(String postId) {
        shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED)
                .ifPresent(published -> {
                    // Snapshot the published row before it is removed so the
                    // webhook can still carry its site + url (SH12).
                    ShPostLifecycleEvent event = lifecycleEvent(ShWebhookEventType.UNPUBLISH, published);
                    shPostUnifiedRepository.delete(published);
                    eventPublisher.publishEvent(event);
                });
    }

    @Transactional
    public void delete(String postId) {
        var rows = shPostUnifiedRepository.findById(postId);
        if (rows.isEmpty()) {
            return;
        }
        // Snapshot from any existing row (DRAFT or PUBLISHED) before deletion.
        ShPostLifecycleEvent event = lifecycleEvent(ShWebhookEventType.DELETE, rows.get(0));
        rows.forEach(shPostUnifiedRepository::delete);
        eventPublisher.publishEvent(event);
    }

    private void publishLifecycle(ShWebhookEventType type, ShPostUnified row) {
        eventPublisher.publishEvent(lifecycleEvent(type, row));
    }

    private ShPostLifecycleEvent lifecycleEvent(ShWebhookEventType type, ShPostUnified row) {
        String siteId = row.getShSite() != null ? row.getShSite().getId() : null;
        return new ShPostLifecycleEvent(type, row.getId(), siteId,
                normalizeUrl(row.getFurl()), Instant.now());
    }

    /** Friendly URL normalised to a leading slash; {@code null}/blank stays null. */
    private static String normalizeUrl(String furl) {
        if (furl == null || furl.isBlank()) {
            return null;
        }
        return furl.startsWith("/") ? furl : "/" + furl;
    }

    private void applyTitleAndSummary(ShPostUnified row, JsonNode data, ShPostType postType) {
        if (postType == null || postType.getShPostTypeAttrs() == null || data == null) return;
        StringBuilder title = new StringBuilder();
        StringBuilder summary = new StringBuilder();
        postType.getShPostTypeAttrs().stream()
                .filter(a -> a.getShParentPostTypeAttr() == null)
                .sorted((a, b) -> Integer.compare(a.getOrdinal(), b.getOrdinal()))
                .forEach(attr -> collectTitleSummary(attr, data, title, summary));
        row.setTitle(title.toString());
        row.setSummary(summary.toString());
    }

    private static void collectTitleSummary(ShPostTypeAttr attr, JsonNode data,
                                            StringBuilder title, StringBuilder summary) {
        JsonNode value = data.get(attr.getName());
        if (value == null || value.isNull()) {
            return;
        }
        String text = value.isString() ? value.asString() : value.toString();
        if (attr.getIsTitle() == 1) {
            appendCsv(title, text);
        }
        if (attr.getIsSummary() == 1) {
            appendCsv(summary, text);
        }
    }

    private static void appendCsv(StringBuilder target, String text) {
        if (!target.isEmpty()) {
            target.append(", ");
        }
        target.append(text);
    }

    private String writeJson(JsonNode data) {
        if (data == null) {
            ObjectNode empty = objectMapper.createObjectNode();
            try {
                return objectMapper.writeValueAsString(empty);
            } catch (JacksonException e) {
                throw new IllegalStateException(e);
            }
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize post data", e);
        }
    }

    // Reused helper so existing callers of objectType keep compiling
    @SuppressWarnings("unused")
    private static String postObjectType() {
        return ShObjectType.POST;
    }

    // Convenience factory for API layer
    public ShPostUnifiedId idOf(String postId, ShPostState state) {
        return new ShPostUnifiedId(postId, state);
    }

    public record CreateRequest(String folderId, String postTypeId, JsonNode data,
                                ShPostState state, String owner) {
    }

    public record UpdateRequest(JsonNode data, ShPostState state, String modifier) {
    }
}
