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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.history.ShContentAction;
import com.viglet.shio.api.history.ShContentAuditService;
import com.viglet.shio.object.ShObjectPublishStatus;
import com.viglet.shio.object.ShObjectType;
import com.viglet.shio.object.ShFolderSite;
import com.viglet.shio.render.ShTemplateTitles;
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
import com.viglet.shio.tenant.ShTenantContext;
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

    /**
     * The locale new content gets when the caller names none (SH53), named once (SH348).
     *
     * <p>It was written {@code "en"} in two places — here and in the context pack's
     * {@code Instance} — which is the drift SH260 and SH300 each spent a task removing for a
     * different set. The pack <em>reports</em> the writer's decision, so the writer owns the value:
     * anything comparing a post's locale against "the default" (SH348's read projection) has to read
     * the same constant or the comparison can disagree with what the pack published.</p>
     */
    public static final String DEFAULT_LOCALE = "en";

    /**
     * A post's locale, never blank (SH383).
     *
     * <p>Here rather than at each caller for the reason above, one step further: the constant had one
     * owner and the <b>defaulting</b> did not, so SH348's read projection, the render model and
     * {@code verify}'s translation rule were each about to write their own two-line fallback. Three
     * copies of "absent means the default" is three chances to disagree with what the context pack
     * published, which is the same drift this constant's own comment describes.</p>
     *
     * <p>The column is nullable and a row written before it existed has none, so the reason this is not
     * merely tidy is what an empty value <em>means</em> downstream: {@code <html lang="">} is not
     * "unspecified" to a browser or a screen reader, it asserts that the language is <b>unknown</b>,
     * which is worse than a defaulted-but-valid tag.</p>
     */
    public static String localeOf(ShPostUnified post) {
        String locale = post == null ? null : post.getLocale();
        return locale == null || locale.isBlank() ? DEFAULT_LOCALE : locale.trim();
    }


    /** Recorded as the {@code publisher} for transitions fired by the SH15 sweep. */
    private static final String SCHEDULER_ACTOR = "scheduler";

    private final ShFolderRepository shFolderRepository;
    private final ShSiteRepository shSiteRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ShContentAuditService shContentAuditService;
    /** Self-reference so the scheduled sweep re-enters publish/unpublish through the
     *  transactional proxy instead of a bare {@code this} call (S6809). */
    private final ObjectProvider<ShPostUnifiedWriteService> self;
    /** SH277 — a template's title is its identity, refused here so every surface refuses. */
    private final ShTemplateTitles shTemplateTitles;

    public ShPostUnifiedWriteService(ShFolderRepository shFolderRepository,
                                     ShSiteRepository shSiteRepository,
                                     ShPostTypeRepository shPostTypeRepository,
                                     ShPostUnifiedRepository shPostUnifiedRepository,
                                     ObjectMapper objectMapper,
                                     ApplicationEventPublisher eventPublisher,
                                     ShContentAuditService shContentAuditService,
                                     ObjectProvider<ShPostUnifiedWriteService> self,
                                     ShTemplateTitles shTemplateTitles) {
        this.shFolderRepository = shFolderRepository;
        this.shSiteRepository = shSiteRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.shContentAuditService = shContentAuditService;
        this.self = self;
        this.shTemplateTitles = shTemplateTitles;
    }

    /** Site id of a row, or {@code null} when unresolved (shared by the audit calls). */
    private static String siteIdOf(ShPostUnified row) {
        return row.getShSite() != null ? row.getShSite().getId() : null;
    }

    private ShFolder resolveContainerFolder(String containerId) {
        return shFolderRepository.findById(containerId).orElseGet(() -> {
            ShSite site = shSiteRepository.findById(containerId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Folder or site not found: " + containerId));
            // SH279 — the writer's landing folder was the database's choice too, which is the
            // worst of the eight: a post created at a site id would have landed in whichever root
            // came back, and where a post lives is not a thing to leave to a Set's iteration.
            return shFolderRepository.findRootFolder(site)
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

        // Pre-assign the id so a standalone post can be its own translation group
        // (SH53). VigletAssignableUuidGenerator honours an explicitly-set id.
        String postId = UUID.randomUUID().toString();

        ShPostUnified row = new ShPostUnified();
        row.setId(postId);
        row.setState(state);
        row.setShFolder(folder);
        row.setShSite(ShFolderSite.of(folder));
        row.setShPostType(postType);
        row.setOwner(req.owner());
        row.setDate(Instant.now());
        row.setModifiedDate(Instant.now());
        row.setLocale(req.locale() != null && !req.locale().isBlank() ? req.locale() : DEFAULT_LOCALE);
        row.setTranslationGroupId(req.translationGroupId() != null && !req.translationGroupId().isBlank()
                ? req.translationGroupId() : postId);
        row.setPublished(state == ShPostState.PUBLISHED);
        row.setPublishStatus(state == ShPostState.PUBLISHED
                ? ShObjectPublishStatus.PUBLISH : ShObjectPublishStatus.DRAFT);
        if (state == ShPostState.PUBLISHED) row.setPublicationDate(Instant.now());

        String dataJson = writeJson(req.data() != null ? req.data() : objectMapper.createObjectNode());
        row.setData(dataJson);

        applyTitleAndSummary(row, req.data(), postType);
        // After the title is derived, never before: a template's title is a *field* (SH183's model
        // is content), so there is nothing to check until the write has decided what it will be.
        shTemplateTitles.checkFree(row);

        ShPostUnified saved = shPostUnifiedRepository.save(row);
        shContentAuditService.record(ShContentAction.CREATE, saved.getId(),
                siteIdOf(saved), saved.getTitle(), req.owner());
        return saved;
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
            shTemplateTitles.checkFree(row);
        }
        row.setModifiedDate(Instant.now());
        if (req.modifier() != null) row.setModifier(req.modifier());
        row.setPublished(state == ShPostState.PUBLISHED);
        row.setPublishStatus(state == ShPostState.PUBLISHED
                ? ShObjectPublishStatus.PUBLISH : ShObjectPublishStatus.DRAFT);

        ShPostUnified saved = shPostUnifiedRepository.save(row);
        shContentAuditService.record(ShContentAction.UPDATE, postId,
                siteIdOf(saved), saved.getTitle(), req.modifier());
        return saved;
    }

    /**
     * Set a post's friendly URL on every state row it has (SH81).
     *
     * <p>Needed because an address-native write makes the {@code furl} the post's
     * <em>identity</em>, not a derived label: {@code post.upsert post:Blog/blog/hello}
     * has to land at exactly that URL or the next run of the same document creates a
     * second post instead of updating the first. Applied to both the {@code DRAFT} and
     * {@code PUBLISHED} rows so the address does not change meaning across a publish.</p>
     *
     * @return the rows that were touched, DRAFT first
     */
    @Transactional
    public List<ShPostUnified> setFurl(String postId, String furl) {
        String normalized = normalizeUrl(furl);
        return shPostUnifiedRepository.findById(postId).stream()
                .sorted(Comparator.comparing(row -> row.getState() == ShPostState.DRAFT ? 0 : 1))
                .map(row -> {
                    row.setFurl(normalized);
                    row.setModifiedDate(Instant.now());
                    return shPostUnifiedRepository.save(row);
                })
                .toList();
    }

    /**
     * Move a post: change its folder, its friendly URL, or both, keeping its id (SH134).
     *
     * <h2>Why this is not upsert-somewhere-else</h2>
     *
     * <p>The address-native vocabulary could create, update and delete an address but not
     * <em>change</em> one, and the obvious workaround — upsert at the new address — is
     * silently destructive: it creates a second post and orphans the first, taking the
     * original's id, its SH51 audit trail, its SH14 version history and every inbound
     * reference with it. Keeping the id is the entire content of this operation.</p>
     *
     * <h2>Every state row moves together</h2>
     *
     * <p>The unified model stores {@code DRAFT} and {@code PUBLISHED} as separate rows
     * sharing an id, and both are re-addressed here. Moving only the draft would leave
     * the live page at the old URL while every read (which is draft-preferred) reported
     * the new one — the post's identity would depend on who was asking. The consequence
     * is that a move of a published post <b>changes the live URL immediately, without a
     * publish step</b>. That is what a move means, and the batch reports it in the change
     * detail rather than letting the caller discover it (P7); an agent that wants the
     * live URL to hold still should unpublish first.</p>
     *
     * <p>Inbound references are id-keyed ({@code ShReference} joins {@code ShObject} ids),
     * so they survive a move untouched. URLs written into another post's body as text do
     * not, and nothing here can know which strings were meant as links — SH100's route
     * verification is where that gets caught.</p>
     *
     * @param postId       the post to move
     * @param targetFolder the destination folder, or {@code null} to stay put
     * @param furl         the new friendly URL, or {@code null} to keep the current one
     * @param actor        recorded as the modifier in the audit trail
     * @return the rows that were touched, DRAFT first
     */
    @Transactional
    public List<ShPostUnified> move(String postId, ShFolder targetFolder, String furl, String actor) {
        String normalized = furl == null ? null : normalizeUrl(furl);
        Instant now = Instant.now();
        List<ShPostUnified> moved = shPostUnifiedRepository.findById(postId).stream()
                .sorted(Comparator.comparing(row -> row.getState() == ShPostState.DRAFT ? 0 : 1))
                .map(row -> {
                    if (targetFolder != null) {
                        row.setShFolder(targetFolder);
                        // Walked, never read off the target folder (SH128) — a move into
                        // a nested folder would otherwise blank the post's site.
                        row.setShSite(ShFolderSite.of(targetFolder));
                    }
                    if (normalized != null) {
                        row.setFurl(normalized);
                    }
                    row.setModifiedDate(now);
                    if (actor != null) {
                        row.setModifier(actor);
                    }
                    return shPostUnifiedRepository.save(row);
                })
                .toList();
        if (!moved.isEmpty()) {
            ShPostUnified first = moved.get(0);
            shContentAuditService.record(ShContentAction.UPDATE, postId,
                    siteIdOf(first), first.getTitle(), actor);
        }
        return moved;
    }

    /**
     * Create a translation of an existing post (SH53): a brand-new {@code DRAFT}
     * post (its own id) in the same folder / post-type / site, authored in
     * {@code locale} and sharing the source's {@code translationGroupId} so the
     * two are linked as siblings. Its data is seeded from {@code data} when
     * supplied, otherwise copied verbatim from the source post.
     */
    @Transactional
    public ShPostUnified translate(String sourceId, String locale, JsonNode data, String owner) {
        if (locale == null || locale.isBlank()) {
            throw new IllegalArgumentException("A target locale is required");
        }
        ShPostUnified source = resolveAnyState(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source post not found: " + sourceId));
        String groupId = source.getTranslationGroupId() != null ? source.getTranslationGroupId() : sourceId;
        JsonNode payload = data != null ? data : readTree(source.getData());
        return create(new CreateRequest(
                source.getShFolder() != null ? source.getShFolder().getId() : null,
                source.getShPostType() != null ? source.getShPostType().getId() : null,
                payload, ShPostState.DRAFT, owner, locale, groupId));
    }

    /** All sibling translations of a post's group (SH53), DRAFT-preferred, one per id. */
    @Transactional(readOnly = true)
    public List<ShPostUnified> listTranslations(String postId) {
        ShPostUnified anchor = resolveAnyState(postId).orElse(null);
        if (anchor == null) {
            return List.of();
        }
        String groupId = anchor.getTranslationGroupId() != null ? anchor.getTranslationGroupId() : postId;
        java.util.Map<String, ShPostUnified> byId = new java.util.LinkedHashMap<>();
        for (ShPostUnified row : shPostUnifiedRepository.findByTranslationGroupId(groupId)) {
            byId.merge(row.getId(), row, (existing, candidate) ->
                    existing.getState() == ShPostState.DRAFT ? existing : candidate);
        }
        return List.copyOf(byId.values());
    }

    private Optional<ShPostUnified> resolveAnyState(String postId) {
        Optional<ShPostUnified> draft = shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT);
        return draft.isPresent() ? draft
                : shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED);
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException e) {
            return objectMapper.createObjectNode();
        }
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
        pub.setLocale(draft.getLocale());
        pub.setTranslationGroupId(draft.getTranslationGroupId());
        ShPostUnified saved = shPostUnifiedRepository.save(pub);
        shContentAuditService.record(ShContentAction.PUBLISH, postId,
                siteIdOf(saved), saved.getTitle(), publisher);
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
                    d.setLocale(published.getLocale());
                    d.setTranslationGroupId(published.getTranslationGroupId());
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
        ShPostUnified saved = shPostUnifiedRepository.save(draft);
        shContentAuditService.record(ShContentAction.RESTORE, postId,
                siteIdOf(saved), saved.getTitle(), modifier);
        return saved;
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
        self.getObject().unpublish(postId, SCHEDULER_ACTOR);
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
        draft.setLocale(published.getLocale());
        draft.setTranslationGroupId(published.getTranslationGroupId());
        draft.setPublished(false);
        draft.setPublishStatus(ShObjectPublishStatus.DRAFT);
        return draft;
    }

    @Transactional
    public void unpublish(String postId, String actor) {
        shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED)
                .ifPresent(published -> {
                    // Snapshot the published row before it is removed so the
                    // webhook can still carry its site + url (SH12).
                    ShPostLifecycleEvent event = lifecycleEvent(ShWebhookEventType.UNPUBLISH, published);
                    shContentAuditService.record(ShContentAction.UNPUBLISH, postId,
                            siteIdOf(published), published.getTitle(), actor);
                    shPostUnifiedRepository.delete(published);
                    eventPublisher.publishEvent(event);
                });
    }

    @Transactional
    public void delete(String postId, String actor) {
        var rows = shPostUnifiedRepository.findById(postId);
        if (rows.isEmpty()) {
            return;
        }
        ShPostUnified snapshot = rows.get(0);
        // SH55 — soft-delete (trash): hide the rows via deleted_date and keep them
        // for restore. A reversible trash fires NO lifecycle event, so it has no
        // side effects — the SH12 webhook and SH38 file-byte cleanup run only on a
        // permanent purge (see #purge). The @SQLRestriction hides it from every read.
        Instant now = Instant.now();
        rows.forEach(row -> {
            row.setDeletedDate(now);
            shPostUnifiedRepository.save(row);
        });
        shContentAuditService.record(ShContentAction.DELETE, postId,
                siteIdOf(snapshot), snapshot.getTitle(), actor);
    }

    /** Posts currently in the trash for the active tenant (SH55), one row per id (DRAFT-preferred). */
    @Transactional(readOnly = true)
    public List<ShPostUnified> listTrash() {
        java.util.Map<String, ShPostUnified> byId = new java.util.LinkedHashMap<>();
        for (ShPostUnified row : shPostUnifiedRepository.findTrash(tenantId())) {
            byId.merge(row.getId(), row, (existing, candidate) ->
                    existing.getState() == ShPostState.DRAFT ? existing : candidate);
        }
        return List.copyOf(byId.values());
    }

    /** Restore a trashed post (SH55): clear its {@code deleted_date} so it becomes live again. */
    @Transactional
    public boolean restoreFromTrash(String postId, String actor) {
        if (shPostUnifiedRepository.restoreFromTrash(postId, tenantId()) == 0) {
            return false;
        }
        shPostUnifiedRepository.findById(postId).stream().findFirst().ifPresent(p ->
                shContentAuditService.record(ShContentAction.RESTORE, postId,
                        siteIdOf(p), p.getTitle(), actor));
        return true;
    }

    /**
     * Permanently delete a trashed post (SH55). Fires the {@code DELETE} lifecycle
     * event <i>now</i> (purge is irreversible) so the SH38 file-byte cleanup and
     * SH12 webhook run; the row itself is removed with a native delete that
     * bypasses the soft-delete {@code @SQLRestriction}.
     */
    @Transactional
    public boolean purge(String postId) {
        List<ShPostUnified> rows = shPostUnifiedRepository.findTrashedById(postId, tenantId());
        if (rows.isEmpty()) {
            return false;
        }
        ShPostLifecycleEvent event = lifecycleEvent(ShWebhookEventType.DELETE, rows.get(0));
        shPostUnifiedRepository.purge(postId, tenantId());
        eventPublisher.publishEvent(event);
        return true;
    }

    private static String tenantId() {
        String tenant = ShTenantContext.getTenantId();
        return tenant != null ? tenant : ShTenantContext.DEFAULT_TENANT;
    }

    private void publishLifecycle(ShWebhookEventType type, ShPostUnified row) {
        eventPublisher.publishEvent(lifecycleEvent(type, row));
    }

    private ShPostLifecycleEvent lifecycleEvent(ShWebhookEventType type, ShPostUnified row) {
        String siteId = row.getShSite() != null ? row.getShSite().getId() : null;
        return new ShPostLifecycleEvent(type, row.getId(), siteId,
                normalizeUrl(row.getFurl()), row.getLocale(), Instant.now());
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
        titleAttrs(postType).forEach(attr -> collectTitleSummary(attr, data, title, summary));
        row.setTitle(title.toString());
        row.setSummary(summary.toString());
    }

    /**
     * The title this data <em>would</em> produce for this post-type, without writing anything.
     *
     * <p>Exists so a {@code dryRun} can report the SH277 collision a real run will refuse. The
     * alternative — a second derivation in the planner — is the shape SH245 keeps warning about:
     * a plan and the write it predicts would disagree the first time a title field is added to a
     * type, and the disagreement would read as the plan being right.</p>
     *
     * @return the derived title, never {@code null}; empty when the type declares no title field
     */
    public static String titleOf(ShPostType postType, JsonNode data) {
        if (postType == null || postType.getShPostTypeAttrs() == null || data == null) {
            return "";
        }
        StringBuilder title = new StringBuilder();
        StringBuilder ignored = new StringBuilder();
        titleAttrs(postType).forEach(attr -> collectTitleSummary(attr, data, title, ignored));
        return title.toString();
    }

    /** Top-level attributes in ordinal order — the order a multi-field title is joined in. */
    private static java.util.stream.Stream<ShPostTypeAttr> titleAttrs(ShPostType postType) {
        return postType.getShPostTypeAttrs().stream()
                .filter(a -> a.getShParentPostTypeAttr() == null)
                .sorted((a, b) -> Integer.compare(a.getOrdinal(), b.getOrdinal()));
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
                                ShPostState state, String owner, String locale,
                                String translationGroupId) {
    }

    public record UpdateRequest(JsonNode data, ShPostState state, String modifier) {
    }
}
