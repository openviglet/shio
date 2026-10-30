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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.viglet.shio.api.agent.ShAgentSessionContext;
import com.viglet.shio.api.history.ShChangedFields;
import com.viglet.shio.api.history.ShContentAction;
import com.viglet.shio.api.history.ShContentAuditService;
import com.viglet.shio.api.post.revision.ShPostRevisionService;
import com.viglet.shio.api.post.ShFieldRules;
import com.viglet.shio.api.post.ShFieldUniqueness;
import com.viglet.shio.api.post.ShRequiredFields;
import com.viglet.shio.persistence.model.post.revision.ShPostRevision;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.post.unified.ShRestoreTitleConflictException;
import com.viglet.shio.api.post.unified.ShRestoreUrlConflictException;
import com.viglet.shio.object.ShObjectPublishStatus;
import com.viglet.shio.object.ShObjectType;
import com.viglet.shio.object.ShFolderSite;
import lombok.extern.slf4j.Slf4j;

import com.viglet.shio.api.post.unified.ShHtmlSanitizer;
import com.viglet.shio.post.ShTranslationStaleness;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.widget.ShSystemWidget;
import com.viglet.shio.render.ShTemplateTitles;
import com.viglet.shio.url.ShFurlNaming;
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
import com.viglet.shio.api.reference.ShObjectReferenceService;
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
@Slf4j
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
    /** SH651 — the one writer of "which URL is free in this site", shared with the copy path. */
    private final ShFurlNaming shFurlNaming;
    /** SH956 — what a rich-text field is allowed to contain, at the boundary every writer crosses. */
    private final ShHtmlSanitizer shHtmlSanitizer;
    /** SH986 — every state a visitor saw, captured in the transaction that made it visible. */
    private final ShPostRevisionService shPostRevisionService;
    /** SH1010 — the one field rule that reads other posts, and the write-time default. */
    private final ShFieldUniqueness shFieldUniqueness;
    /** SH1058 — which post is a translation's source, and which version of it. */
    private final ShTranslationStaleness shTranslationStaleness;

    /**
     * SH1034 — the reverse index, maintained beside every write that touches {@code data}.
     *
     * <p>Called after the save rather than before it, because a row that failed to persist must
     * not leave edges behind, and {@code reindex} is a rewrite so a repeat is free (P5). The
     * paths that do <b>not</b> touch {@code data} — {@code setFurl}, {@code move},
     * {@code schedule} — deliberately do not call it: an address change moves no reference, and a
     * reindex there would be three statements bought for nothing.</p>
     */
    private final ShObjectReferenceService shObjectReferenceService;

    public ShPostUnifiedWriteService(ShFolderRepository shFolderRepository,
                                     ShSiteRepository shSiteRepository,
                                     ShPostTypeRepository shPostTypeRepository,
                                     ShPostUnifiedRepository shPostUnifiedRepository,
                                     ObjectMapper objectMapper,
                                     ApplicationEventPublisher eventPublisher,
                                     ShContentAuditService shContentAuditService,
                                     ObjectProvider<ShPostUnifiedWriteService> self,
                                     ShTemplateTitles shTemplateTitles,
                                     ShFurlNaming shFurlNaming,
                                     ShHtmlSanitizer shHtmlSanitizer,
                                     ShPostRevisionService shPostRevisionService,
                                     ShFieldUniqueness shFieldUniqueness,
                                     ShObjectReferenceService shObjectReferenceService,
                                     ShTranslationStaleness shTranslationStaleness) {
        this.shTranslationStaleness = shTranslationStaleness;
        this.shObjectReferenceService = shObjectReferenceService;
        this.shFieldUniqueness = shFieldUniqueness;
        this.shHtmlSanitizer = shHtmlSanitizer;
        this.shPostRevisionService = shPostRevisionService;
        this.shFolderRepository = shFolderRepository;
        this.shSiteRepository = shSiteRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.shContentAuditService = shContentAuditService;
        this.self = self;
        this.shTemplateTitles = shTemplateTitles;
        this.shFurlNaming = shFurlNaming;
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
        // SH1058 — a translation records which version of the source it was written against. Both
        // doors that create one (the console's translate and the agent's translationOf) arrive here.
        if (!postId.equals(row.getTranslationGroupId())) {
            row.setSourceDigest(sourceDigestOf(row.getTranslationGroupId()));
        }
        row.setPublished(state == ShPostState.PUBLISHED);
        row.setPublishStatus(state == ShPostState.PUBLISHED
                ? ShObjectPublishStatus.PUBLISH : ShObjectPublishStatus.DRAFT);
        if (state == ShPostState.PUBLISHED) row.setPublicationDate(Instant.now());

        // SH956 — cleaned before it is stored, not on the way out. A sanitizer at the render would
        // leave the payload in the database for the next reader that does not have one.
        // SH1010 (second part) — on create only, and only for a field the document does not carry:
        // a default re-applied on update would overwrite a value somebody deliberately cleared,
        // which is how a convenience becomes a data loss.
        JsonNode clean = sanitizedMarkup(shFieldUniqueness.withDefaults(postType, req.data()),
                postType);
        String dataJson = writeJson(clean != null ? clean : objectMapper.createObjectNode());
        row.setData(dataJson);

        applyTitleAndSummary(row, clean, postType);

        // SH651 — and only after the title, because the URL is derived from it. Every other
        // write assigns a furl (folder, site, copy, import); this one did not, so five posts
        // created through the console all reported at their folder's own address and none of
        // them could be opened, verified or delivered. The write answered 201 throughout.
        //
        // A declared furl is identity (SH81) and is taken as given, the way `setFurl` takes
        // it; a derived one is corrected to the first free spelling, which is what a curator
        // typing the same title twice means. A post with no title keeps a blank furl — an
        // uploaded file post never gets one (SH38) and inventing an address is worse than
        // having none.
        // One spelling out of both branches. The first version normalized only the declared
        // one and stored `/taken-address` beside a derived `naive-page-one`; the bare form is
        // the one every other write stores (copy, folder, site) and the one the address
        // projections concatenate — `ShSiteExportService` builds `"post:" + site + "/" + furl`,
        // so a stored leading slash comes out as `post:site//page`. Reading is spelling-blind
        // either way (SH583); this is only about what is written.
        row.setFurl(bareFurl(req.furl() != null && !req.furl().isBlank()
                ? req.furl()
                : derivedFurl(row, postType, req.under())));

        // After the title is derived, never before: a template's title is a *field* (SH183's model
        // is content), so there is nothing to check until the write has decided what it will be.
        shTemplateTitles.checkFree(row);

        // SH1009 — a create straight to PUBLISHED is a publish, and the only one that does not go
        // through `publish` below. A draft is never refused: drafts are where work happens, and a
        // rule that refused a half-written page would be worse than the defect it closes.
        if (state == ShPostState.PUBLISHED) {
            ShRequiredFields.requireComplete(postType, clean, named(row));
        }

        ShPostUnified saved = shPostUnifiedRepository.save(row);
        shObjectReferenceService.reindex(saved);
        shContentAuditService.record(ShContentAction.CREATE, saved.getId(),
                siteIdOf(saved), saved.getTitle(), req.owner());
        reviewRequested(saved);
        return saved;
    }

    @Transactional
    public ShPostUnified update(String postId, UpdateRequest req) {
        ShPostState state = req.state() != null ? req.state() : ShPostState.DRAFT;
        ShPostUnified row = shPostUnifiedRepository.findByIdAndState(postId, state)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Post not found: " + postId + " state=" + state));

        // SH989 — an update that sends no document changed no field, and says so with an empty list
        // rather than leaving the row unrecorded.
        List<String> changed = List.of();
        if (req.data() != null) {
            // SH956 — every write, not only the create: an edit is how a payload arrives at a post
            // that was clean when it was made.
            JsonNode clean = sanitizedMarkup(req.data(), row.getShPostType());
            // The stored document is read before it is overwritten: this row holds both sides of the
            // comparison, so naming the fields costs no query.
            changed = ShChangedFields.between(readTree(row.getData()), clean);
            row.setData(writeJson(clean));
            applyTitleAndSummary(row, clean, row.getShPostType());
            shTemplateTitles.checkFree(row);
            // SH1058 — a translation's own edit is the translator saying the text now matches the
            // source in front of them, so it re-stamps against the source as it is now. A source
            // edit never touches this: that is what makes the translation stale until somebody
            // answers it.
            if (!changed.isEmpty() && isTranslation(row)) {
                String current = sourceDigestOf(row.getTranslationGroupId());
                if (current != null) {
                    row.setSourceDigest(current);
                }
            }
        }
        row.setModifiedDate(Instant.now());
        if (req.modifier() != null) row.setModifier(req.modifier());
        row.setPublished(state == ShPostState.PUBLISHED);
        row.setPublishStatus(state == ShPostState.PUBLISHED
                ? ShObjectPublishStatus.PUBLISH : ShObjectPublishStatus.DRAFT);

        ShPostUnified saved = shPostUnifiedRepository.save(row);
        shObjectReferenceService.reindex(saved);
        if (shPostRevisionService.capturesOnSave()) {
            shPostRevisionService.capture(saved, ShPostRevision.Reason.SAVE, req.modifier());
        }
        shContentAuditService.record(ShContentAction.UPDATE, postId,
                siteIdOf(saved), saved.getTitle(), req.modifier(), changed);
        reviewRequested(saved);
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
     * <p>Inbound references are id-keyed ({@code sh_object_reference} points at ids, SH1034),
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
            reviewRequested(first);
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

    /**
     * SH1058 — the digest of a translation group's source as it reads now, or null.
     *
     * <p>{@link ShTranslationStaleness} owns the rule — which post is the source, and which version
     * of it — because the write that stamps and the readers that compare have to agree, and four
     * copies of a two-part rule is four opinions.</p>
     */
    private String sourceDigestOf(String translationGroupId) {
        return shTranslationStaleness.sourceDigest(translationGroupId).orElse(null);
    }

    /** Whether this row is a translation of another post rather than a source of its own group. */
    public static boolean isTranslation(ShPostUnified post) {
        return ShTranslationStaleness.isTranslation(post);
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

    /**
     * Publishes the draft.
     *
     * <h2>A publish after the scheduled instant is the scheduled publish (SH969)</h2>
     *
     * <p>A schedule that failed stays due, because the sweep clears it in the transaction that rolled
     * back. So when a curator retried by hand, the retry published and the schedule was still there,
     * and the next sweep published the page again under the scheduler's name, with a second webhook.
     * A due schedule is therefore cleared by any publish. A schedule still in the future is left
     * alone: publishing now and publishing again at a later date are both things a curator
     * means.</p>
     */
    @Transactional
    public ShPostUnified publish(String postId, String publisher) {
        return publish(postId, publisher, null);
    }

    /**
     * The same publish, carrying what a person waived to make it happen (SH998).
     *
     * <h2>Both records, because they answer different questions</h2>
     *
     * <p>The waiver goes on the <b>audit row</b>, where somebody asking "what happened on Tuesday"
     * is reading, and on the <b>revision</b>, where somebody asking "what was live, and how did
     * that get there" is reading. One without the other leaves a reader who started at the content
     * with no way to find out a check was overridden, which is most of the way back to no gate at
     * all: a waiver nobody can see looks exactly like a gate that held.</p>
     *
     * @param waived the line naming the gate, its failing checks and the reason, or {@code null}
     */
    @Transactional
    public ShPostUnified publish(String postId, String publisher, String waived) {
        ShPostUnified draft = shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + postId));
        // SH1009 — here, and not on each caller, because this method is what every publish in the
        // product passes through: the console's press, REST's, the agent batch's `post.publish`
        // and the scheduler's sweep. A required field that is empty stops being somebody's work in
        // progress and becomes a page a visitor is served missing what its type says it must have.
        //
        // Unless a person waived, which is SH998's door and this rule must not put a second lock
        // on it: `required-field-empty` is a check a site's gate can declare, so a refusal here
        // that ignored the waiver would make that check the one nothing could ever get past. The
        // waiver is the sanctioned override — recorded on the audit row AND on the revision, and
        // refused to an agent outright — so the page that goes live incomplete goes live with a
        // person's name and their reason attached to it.
        if (waived == null) {
            requireCompleteToPublish(draft);
        }
        if (isDue(draft.getPublishScheduledDate())) {
            draft.setPublishScheduledDate(null);
            shPostUnifiedRepository.save(draft);
        }

        Optional<ShPostUnified> replaced = shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED);
        // SH989 — what this publish changes for a visitor, named before the live row is deleted. It is
        // the one moment the comparison exists: afterwards the draft and the live row are the same
        // document, which is why an approved run could not be described.
        List<String> changed = ShChangedFields.between(
                replaced.map(ShPostUnified::getData).orElse(null), draft.getData());
        replaced.ifPresent(shPostUnifiedRepository::delete);

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
        // SH1058 — the published row says what it was translated from, as the draft did.
        pub.setSourceDigest(draft.getSourceDigest());
        ShPostUnified saved = shPostUnifiedRepository.save(pub);
        // SH1034 — the PUBLISHED row is a second document with its own edges: it has just taken
        // the draft's data, and until this runs the index describes the page as it was last live.
        shObjectReferenceService.reindex(saved);
        // SH986 — the state that just went live, captured in the transaction that made it visible.
        // The row this publish deleted already has a revision of its own, from the publish that put
        // it there; capturing the *incoming* row is what makes "every state a visitor saw" true of
        // the current one too, rather than only of the ones that have since been replaced.
        shPostRevisionService.capture(saved, ShPostRevision.Reason.PUBLISH, publisher, waived);
        shContentAuditService.record(ShContentAction.PUBLISH, postId,
                siteIdOf(saved), saved.getTitle(), publisher,
                detail(scheduledBy(draft, publisher), waived), changed);
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
                    d.setSourceDigest(published.getSourceDigest());
                    return d;
                });

        // SH989 — a revert names what it put back, the same comparison an update makes.
        List<String> changed = ShChangedFields.between(draft.getData(), published.getData());
        draft.setData(published.getData());
        draft.setTitle(published.getTitle());
        draft.setSummary(published.getSummary());
        draft.setFurl(published.getFurl());
        draft.setModifiedDate(Instant.now());
        if (modifier != null) draft.setModifier(modifier);
        draft.setPublished(false);
        draft.setPublishStatus(ShObjectPublishStatus.DRAFT);
        ShPostUnified saved = shPostUnifiedRepository.save(draft);
        shObjectReferenceService.reindex(saved);
        shContentAuditService.record(ShContentAction.RESTORE, postId,
                siteIdOf(saved), saved.getTitle(), modifier, changed);
        return saved;
    }

    /**
     * Put a captured revision back over the draft (SH986).
     *
     * <p>Over the <b>draft</b>, never over the live row. Restoring straight to publication would
     * put bytes on the site that nobody looked at in this session, which is P7 read backwards: the
     * curator asked to go back to a version, and publishing it is the separate, deliberate act the
     * draft-default split exists to keep separate.</p>
     *
     * <p>The restore is itself captured, with reason {@code ROLLBACK}. Without that, going back to
     * version three and then regretting it would leave nothing to come forward to — an undo that
     * destroys what it undid is the defect this table was added for, one level up.</p>
     */
    @Transactional
    public ShPostUnified restoreRevision(String postId, int versionNo, String modifier) {
        ShPostRevision revision = shPostRevisionService.revision(postId, versionNo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No revision " + versionNo + " of post " + postId));

        ShPostUnified draft = shPostUnifiedRepository
                .findByIdAndState(postId, ShPostState.DRAFT)
                .or(() -> shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED)
                        .map(live -> {
                            ShPostUnified d = new ShPostUnified();
                            d.setId(postId);
                            d.setState(ShPostState.DRAFT);
                            d.setShFolder(live.getShFolder());
                            d.setShSite(live.getShSite());
                            d.setShPostType(live.getShPostType());
                            d.setOwner(live.getOwner());
                            d.setDate(live.getDate());
                            d.setTranslationGroupId(live.getTranslationGroupId());
                            d.setSourceDigest(live.getSourceDigest());
                            return d;
                        }))
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

        // The draft as it stands is captured first, or restoring discards it — which would make
        // this the second place in the product where going back destroys what you were on.
        shPostRevisionService.capture(draft, ShPostRevision.Reason.ROLLBACK, modifier);

        List<String> changed = ShChangedFields.between(draft.getData(), revision.getData());
        draft.setData(revision.getData());
        draft.setTitle(revision.getTitle());
        draft.setFurl(revision.getFurl());
        if (revision.getLocale() != null) {
            draft.setLocale(revision.getLocale());
        }
        draft.setModifiedDate(Instant.now());
        if (modifier != null) draft.setModifier(modifier);
        draft.setPublished(false);
        draft.setPublishStatus(ShObjectPublishStatus.DRAFT);
        ShPostUnified saved = shPostUnifiedRepository.save(draft);
        shObjectReferenceService.reindex(saved);
        shContentAuditService.record(ShContentAction.RESTORE, postId,
                siteIdOf(saved), saved.getTitle(), modifier, changed);
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

        // SH991 — the person behind the transition the sweep fires later, and nobody once nothing is
        // left scheduled, so a stale name never attributes a schedule somebody else set afterwards.
        // SH992 — and only someone who set an instant becomes it: cancelling one of two, or saving
        // both unchanged, leaves the other attributed to whoever chose it.
        boolean sets = (publishAt != null && !publishAt.equals(draft.getPublishScheduledDate()))
                || (unpublishAt != null && !unpublishAt.equals(draft.getUnpublishScheduledDate()));
        if (publishAt == null && unpublishAt == null) {
            draft.setScheduleRequestedBy(null);
        } else if (sets || draft.getScheduleRequestedBy() == null) {
            draft.setScheduleRequestedBy(modifier);
        }
        draft.setPublishScheduledDate(publishAt);
        draft.setUnpublishScheduledDate(unpublishAt);
        draft.setModifiedDate(Instant.now());
        if (modifier != null) draft.setModifier(modifier);
        return shPostUnifiedRepository.save(draft);
    }

    /**
     * A draft the SH15 sweep is about to fire, and the site it belongs to (SH204).
     *
     * <p>The site rides along because of <em>where</em> it is cheap. The sweep's failure handler
     * needs it to attribute an incident, and there it costs a query on a path that has just thrown,
     * in a background job where an exception would abandon the rest of the sweep. Here the row is
     * already loaded inside a read transaction that is already open, so it is a field read on an
     * entity the finder had to fetch anyway.</p>
     *
     * @param siteId null when the post has no site — the sweep still fires, and the incident it
     *               may record is then simply unattributed, which is the honest answer
     */
    public record ShScheduledPost(String id, String siteId) {
    }

    /** Drafts whose scheduled publish instant is now due (SH15 sweep). */
    @Transactional(readOnly = true)
    public List<ShScheduledPost> findScheduledPublishDue(Instant now) {
        return shPostUnifiedRepository
                .findByStateAndPublishScheduledDateLessThanEqual(ShPostState.DRAFT, now)
                .stream().map(ShPostUnifiedWriteService::scheduled).toList();
    }

    /** Drafts whose scheduled unpublish instant is now due (SH15 sweep). */
    @Transactional(readOnly = true)
    public List<ShScheduledPost> findScheduledUnpublishDue(Instant now) {
        return shPostUnifiedRepository
                .findByStateAndUnpublishScheduledDateLessThanEqual(ShPostState.DRAFT, now)
                .stream().map(ShPostUnifiedWriteService::scheduled).toList();
    }

    /**
     * SH204 — the pair, resolved while the session is still open.
     *
     * <p>ShFolderSite-exempt: a post carries its own site column, and this is the same resolution
     * {@code lifecycleEvent} already makes one method along for the webhook's event. No folder walk
     * fallback here on purpose — a sweep that is about to fail should not do more work to label the
     * failure, and a post with no site column is one whose incident is honestly unattributed.</p>
     */
    private static ShScheduledPost scheduled(ShPostUnified row) {
        return new ShScheduledPost(row.getId(),
                row.getShSite() == null ? null : row.getShSite().getId());
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
        forgetRequesterWhenIdle(postId);
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
        forgetRequesterWhenIdle(postId);
    }

    /** The audit row's parenthetical, joining the two things a publish can have to say (SH998). */
    private static String detail(String scheduled, String waived) {
        if (scheduled == null) {
            return waived;
        }
        return waived == null ? scheduled : scheduled + "; " + waived;
    }

    /**
     * Tell the outbound edge an agent run left work for a person (SH998).
     *
     * <h2>Once per run per site, and how that is made true</h2>
     *
     * <p>A run is one transaction, so the sites already announced are held as a transaction-scoped
     * resource and unbound when it completes. Without that, a forty-page conversion would fire forty
     * identical notices and an operator would filter the event out inside a week — which is the same
     * failure as not sending it, arrived at more expensively.</p>
     *
     * <p>Published, not dispatched: the drafts have to <em>exist</em> before an approver is told to
     * look at them, so this one waits for the commit. That is the exact opposite of the gate's
     * refusal, and for the exact same reason — each waits for the thing it is about.</p>
     */
    private void reviewRequested(ShPostUnified row) {
        // Only a draft, and only an agent's: the review queue is what an agent run leaves behind. A
        // console session sets the same context on a publish (SH996), which is not a draft write.
        if (row == null || row.getState() != ShPostState.DRAFT) {
            return;
        }
        String session = ShAgentSessionContext.current().orElse(null);
        if (session == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        String siteId = siteIdOf(row);
        Set<String> announced = announcedInThisRun();
        if (!announced.add(session + "/" + siteId)) {
            return;
        }
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("session", session);
        eventPublisher.publishEvent(new ShPostLifecycleEvent(ShWebhookEventType.REVIEW_REQUESTED,
                row.getId(), siteId, normalizeUrl(row.getFurl()), row.getLocale(), Instant.now(),
                facts));
    }

    @SuppressWarnings("unchecked")
    private Set<String> announcedInThisRun() {
        Set<String> held = (Set<String>) TransactionSynchronizationManager.getResource(ANNOUNCED);
        if (held != null) {
            return held;
        }
        Set<String> fresh = new LinkedHashSet<>();
        TransactionSynchronizationManager.bindResource(ANNOUNCED, fresh);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                // The thread is pooled. A binding left behind would make the next run on it believe
                // it had already announced itself — the SH639 leak, one layer up.
                TransactionSynchronizationManager.unbindResourceIfPossible(ANNOUNCED);
            }
        });
        return fresh;
    }

    /**
     * {@code scheduled by <person>} for a transition the sweep fired, or {@code null} for one a person
     * made directly (SH991). Read off the draft, where the schedule and its requester live.
     */
    private static String scheduledBy(ShPostUnified draft, String actor) {
        if (!SCHEDULER_ACTOR.equals(actor) || draft == null || draft.getScheduleRequestedBy() == null) {
            return null;
        }
        return "scheduled by " + draft.getScheduleRequestedBy();
    }

    /** Clears the requester once the draft carries no schedule, after the fired transition named it. */
    private void forgetRequesterWhenIdle(String postId) {
        shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
                .filter(draft -> draft.getPublishScheduledDate() == null
                        && draft.getUnpublishScheduledDate() == null
                        && draft.getScheduleRequestedBy() != null)
                .ifPresent(draft -> {
                    draft.setScheduleRequestedBy(null);
                    shPostUnifiedRepository.save(draft);
                });
    }

    /** A schedule whose instant has arrived — what the sweep would fire now (SH969). */
    private static boolean isDue(Instant scheduled) {
        return scheduled != null && !scheduled.isAfter(Instant.now());
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
        draft.setSourceDigest(published.getSourceDigest());
        draft.setPublished(false);
        draft.setPublishStatus(ShObjectPublishStatus.DRAFT);
        return draft;
    }

    /** Unpublishes the post, and clears a due unpublish schedule for {@link #publish}'s reason (SH969). */
    @Transactional
    public void unpublish(String postId, String actor) {
        String detail = shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
                .map(draft -> scheduledBy(draft, actor)).orElse(null);
        shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
                .filter(draft -> isDue(draft.getUnpublishScheduledDate()))
                .ifPresent(draft -> {
                    draft.setUnpublishScheduledDate(null);
                    shPostUnifiedRepository.save(draft);
                });
        shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED)
                .ifPresent(published -> {
                    // Snapshot the published row before it is removed so the
                    // webhook can still carry its site + url (SH12).
                    ShPostLifecycleEvent event = lifecycleEvent(ShWebhookEventType.UNPUBLISH, published);
                    shContentAuditService.record(ShContentAction.UNPUBLISH, postId,
                            siteIdOf(published), published.getTitle(), actor, detail, null);
                    shPostUnifiedRepository.delete(published);
                    // SH1034 — the PUBLISHED row is gone, so its edges are too. Only that state's:
                    // the draft still references whatever it references, and forgetting both here
                    // would make an unpublish read as "nothing uses this asset".
                    shObjectReferenceService.forget(postId, ShPostState.PUBLISHED);
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
            // SH1034 — a trashed post does not keep an asset alive. `reindex` reads the row's own
            // deletedDate and writes no edges for it, so this is the same call as everywhere else
            // rather than a second rule; `restoreFromTrash` puts them back by saving again.
            shObjectReferenceService.reindex(row);
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

    /**
     * Restore a trashed post (SH55): clear its {@code deleted_date} so it becomes live again.
     *
     * <h2>The restore that could duplicate a URL (SH243)</h2>
     *
     * <p>This cleared {@code deleted_date} with <b>no check at all</b>, and a trashed post keeps
     * its {@code furl}, so the sequence was: clone a page ({@code home} → {@code home-2}), delete
     * the copy, clone again — the second copy took {@code home-2}, because every free-URL finder
     * reads through the {@code @SQLRestriction} and saw nothing holding it — then untrash the
     * first. Two individually correct operations, and the instance now has the
     * {@code duplicate-url} error SH202 was filed to remove.</p>
     *
     * <p>{@code ShFurlNaming} closes the first half by seeing the trash when it derives a name.
     * This closes the second: the URL can be taken by other means — a rename, an explicit
     * {@code furl} on create, a cross-site move — and a restore is the one write that resurrects
     * a claim on an address nobody was holding it against.</p>
     *
     * @return {@code false} when the id is not in the trash
     * @throws ShRestoreUrlConflictException when a live post already answers at this post's URL;
     *         see that class for why refusing beats returning it at {@code -2}, and for what the
     *         refusal costs a folder restore
     */
    @Transactional
    public boolean restoreFromTrash(String postId, String actor) {
        List<ShPostUnified> trashed = shPostUnifiedRepository.findTrashedById(postId, tenantId());
        if (trashed.isEmpty()) {
            return false;
        }
        // SH243 — before the update, not after: this method is called inside a folder restore's
        // transaction (SH200), and a refusal that had already cleared the marker would depend on
        // the rollback to undo a write it knew was wrong.
        restoreConflict(trashed).ifPresent(refusal -> {
            throw refusal;
        });
        if (shPostUnifiedRepository.restoreFromTrash(postId, tenantId()) == 0) {
            return false;
        }
        // SH1034 — restoring is the inverse of the trash's reindex, and it is a bulk UPDATE rather
        // than a save, so nothing else here would put the edges back. Every row, because a post is
        // restored in both states at once.
        //
        // `setDeletedDate(null)` before the reindex, and it is not belt-and-braces: the native
        // UPDATE above bypasses the persistence context, so a row already loaded in this
        // transaction still reads as trashed — and `reindex` writes no edges for a trashed row, on
        // purpose. Without this the restore silently left the asset unreferenced, which is the
        // defect `asset-unused` would then offer to act on. Aligning the entity with what the
        // database now holds is the honest fix; clearing the whole context would reach every
        // caller of this method, including the folder restore that drives it in a loop.
        shPostUnifiedRepository.findById(postId).forEach(row -> {
            row.setDeletedDate(null);
            shObjectReferenceService.reindex(row);
        });
        shPostUnifiedRepository.findById(postId).stream().findFirst().ifPresent(p ->
                shContentAuditService.record(ShContentAction.RESTORE, postId,
                        siteIdOf(p), p.getTitle(), actor));
        return true;
    }

    /**
     * The SH243 refusal, asked <b>without restoring</b> — what a plan has to know.
     *
     * <p>{@code POST /agent/batch} with {@code dryRun} answers "would restore" by reading the
     * trash, on the stated grounds that a plan saying yes about a post nobody trashed is the plan
     * an agent acts on. A plan saying yes about a restore that will be refused is the same defect,
     * and P9's point is that the cheap textual proof has to agree with the write. So the plan asks
     * this rather than re-deriving the rule — the version of this check that lives in two places
     * is the one that disagrees with itself the first time either moves.</p>
     *
     * @throws ShRestoreUrlConflictException when restoring {@code postId} now would collide
     */
    @Transactional(readOnly = true)
    public void requireRestorableUrl(String postId) {
        restoreConflict(shPostUnifiedRepository.findTrashedById(postId, tenantId()))
                .ifPresent(refusal -> {
                    throw refusal;
                });
    }

    /**
     * The same question with no throw, for a caller that <b>reports</b> refusals (SH243).
     *
     * <p>{@code ShReviewService.revert} walks a run's entries and collects a reason per entry it
     * could not undo. It catches {@code RuntimeException} to do that — and catching one thrown by an
     * inner {@code @Transactional} call does not undo the rollback-only mark it left on the shared
     * transaction, so the graceful "skipped" would surface as an {@code UnexpectedRollbackException}
     * at commit: a 500 in place of the one row the queue exists to show. Asking instead of catching
     * is the difference, and it is why this pair exists rather than only the throwing form.</p>
     *
     * @return the refusal's own wording, or empty when the restore would go through
     */
    @Transactional(readOnly = true)
    public Optional<String> restoreUrlConflict(String postId) {
        return restoreConflict(shPostUnifiedRepository.findTrashedById(postId, tenantId()))
                .map(Throwable::getMessage);
    }

    /**
     * The live post standing on a trashed post's URL, as the refusal it would be raised as (SH243).
     *
     * <p>Built and returned rather than thrown so the two callers above can each do the right thing
     * with it, and so both spell the refusal the same way — a second wording for the review queue
     * is a second thing to keep in step with the 409.</p>
     *
     * <p>Every trashed row is asked, not just the first: {@code DRAFT} and {@code PUBLISHED} are
     * two rows of one post and a {@code setFurl} between the two writes could have left them
     * disagreeing, so checking one would restore a published URL nobody checked. The occupancy
     * question is {@code ShFurlNaming}'s — it walks the SH583 spellings, which a check written
     * here would have had to copy for the third time.</p>
     */
    private Optional<ShTeachingException> restoreConflict(List<ShPostUnified> trashed) {
        for (ShPostUnified row : trashed) {
            Optional<ShPostUnified> occupant =
                    shFurlNaming.liveOccupant(siteIdOf(row), row.getFurl(), row.getId());
            if (occupant.isPresent()) {
                return Optional.of(new ShRestoreUrlConflictException(
                        row.getFurl(), occupant.get().getTitle()));
            }
            // SH691 — the other name a copy moves. The URL is checked first because it is the one
            // an address resolves by; a post can collide on both and the address is the refusal
            // worth reading, so the order is the priority rather than an accident.
            Optional<ShPostUnified> sameTitle = liveTitleHolder(row);
            if (sameTitle.isPresent()) {
                return Optional.of(new ShRestoreTitleConflictException(
                        row.getTitle(), folderNameOf(row)));
            }
        }
        return Optional.empty();
    }

    /**
     * A live post in the same folder already spelled the same as this trashed one (SH691).
     *
     * <p>Per <b>folder</b>, not per site, because that is the scope the counter numbers in and the
     * scope a curator reads: the same title in a sibling folder is two ordinary pages. Blank and
     * null titles are never a collision — an uploaded file has no title (SH38), and refusing a
     * restore because two of them are equally untitled would refuse on a name that is not a name.</p>
     *
     * <p>Only {@code DRAFT} and {@code PUBLISHED} count. A trashed neighbour holding the title is
     * the case SH691 exists for: it is not shown to anybody, so it cannot be the row the curator
     * cannot tell this one apart from.</p>
     */
    /** The two states a curator can see. `findByShFolderAndStateIn` is trash-blind, which is
     *  what SH691 wants here: a trashed neighbour is not a row anybody is confused by. */
    private static final java.util.List<ShPostState> LIVE_STATES =
            java.util.List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    private Optional<ShPostUnified> liveTitleHolder(ShPostUnified row) {
        String title = row.getTitle();
        if (title == null || title.isBlank() || row.getShFolder() == null) {
            return Optional.empty();
        }
        return shPostUnifiedRepository
                .findByShFolderAndStateIn(row.getShFolder(), LIVE_STATES).stream()
                .filter(live -> !live.getId().equals(row.getId()))
                .filter(live -> title.equalsIgnoreCase(live.getTitle()))
                .findFirst();
    }

    private String folderNameOf(ShPostUnified row) {
        return row.getShFolder() == null ? null : row.getShFolder().getName();
    }

    /**
     * Permanently delete a trashed post (SH55). Fires the {@code DELETE} lifecycle
     * event <i>now</i> (purge is irreversible) so the SH38 file-byte cleanup and
     * SH12 webhook run; the row itself is removed with a native delete that
     * bypasses the soft-delete {@code @SQLRestriction}.
     */
    /**
     * Permanently deletes a trashed post, and records that somebody did (SH224).
     *
     * <h2>The one write nobody recorded</h2>
     *
     * <p>This fired the {@code DELETE} lifecycle event — so the SH12 webhook went and SH38's
     * listener removed the stored bytes — and wrote <b>no audit row at all</b>. The reversible half
     * of the trash is fully recorded, and the irreversible half recorded nothing, which is
     * backwards: "who destroyed this, and when" is a question about the deletion that
     * <em>cannot be undone</em>.</p>
     *
     * <p>It also left a purge invisible to {@code /agent/changes}, so a caller walking the feed saw
     * a post enter the trash and never learned it was gone for good — the two states a reader most
     * needs to tell apart.</p>
     *
     * <h2>The row is written before the rows are gone</h2>
     *
     * <p>The title and the site come off the entity, so they are read while it still exists. SH88
     * already carries an entry whose object has vanished — it travels with its title and a
     * {@code null} address, and the feed says so in {@code notes} — which is exactly the shape this
     * row needs and the reason it is worth having despite naming something unreachable.</p>
     *
     * @param actor who asked; {@code null} when a cascade did (a folder or site purge), which the
     *              row carries honestly rather than attributing to whoever happened to be nearest
     */
    // One method, not a pair. The first attempt kept a one-argument overload delegating here, and
    // that delegation is a self-invocation: the proxy never applies, so the native purge ran with
    // no transaction and said so. SH358's lint refuses the shape outright, and its advice — collapse
    // rather than annotate both — is what this is.
    @Transactional
    public boolean purge(String postId, String actor) {
        List<ShPostUnified> rows = shPostUnifiedRepository.findTrashedById(postId, tenantId());
        if (rows.isEmpty()) {
            return false;
        }
        ShPostUnified row = rows.get(0);
        String title = row.getTitle();
        String siteId = siteIdOf(row);
        ShPostLifecycleEvent event = lifecycleEvent(ShWebhookEventType.DELETE, row);
        shPostUnifiedRepository.purge(postId, tenantId());
        shObjectReferenceService.forget(postId);
        shContentAuditService.record(ShContentAction.PURGE, postId, siteId, title, actor);
        eventPublisher.publishEvent(event);
        return true;
    }

    /** SH998 — the key the review notice's per-transaction set is bound under. */
    private static final String ANNOUNCED = ShPostUnifiedWriteService.class.getName() + ".reviewed";

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
    /**
     * The address a created post derives from its title, or blank where it should have none
     * (SH651).
     *
     * <p><b>An uploaded file is not a page.</b> SH38 says an asset post has no friendly URL —
     * {@code id:} is the only address it has, which is the whole reason SH172 added that
     * fallback — and {@code ShStaticFileService.upload} reaches this write like any other
     * caller, with a title (the file name) that would otherwise slug into {@code logopng}.
     * The rule lives here rather than in the uploader because a caller that has to remember
     * to opt out is the caller that will forget; the suite caught this one, and the next
     * binary post-type will not have a test already watching.</p>
     */
    private String derivedFurl(ShPostUnified row, ShPostType postType, String under) {
        if (postType != null && ShSystemPostType.FILE.equals(postType.getName())) {
            return "";
        }
        return shFurlNaming.freeFurl(siteIdOf(row), under, row.getTitle());
    }

    /**
     * A furl as it is <b>stored</b> by every write except {@code setFurl}/{@code move}: no
     * leading slash (SH651). Null for blank, which is an absent address rather than the root.
     */
    private static String bareFurl(String furl) {
        if (furl == null || furl.isBlank()) {
            return null;
        }
        String trimmed = furl.strip();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.isBlank() ? null : trimmed;
    }

    /**
     * Refuses a publish whose page is missing a field its type declares required (SH1009).
     *
     * <p>Public because the agent's batch calls it while <b>planning</b>: a dry run that reported a
     * publish the apply would refuse is a plan an agent acts on and then fails, which is the one
     * thing a plan exists to prevent. One evaluation for both, rather than the batch parsing the row
     * a second time and deciding for itself what empty means.</p>
     *
     * <p>Read off the stored row rather than off a request body, which is what makes it true of
     * every writer: a caller that sent only the field it was changing still publishes the whole
     * document, and a check over the request would pass a page whose title was emptied yesterday.</p>
     *
     * <p>Unparseable data is <b>not</b> a required-field refusal. It is a different defect with a
     * different remedy, and reporting it as an empty field would send whoever reads the message to
     * fill in a value that is already there.</p>
     */
    public void requireCompleteToPublish(ShPostUnified draft) {
        JsonNode data = storedData(draft);
        if (data == null) {
            return;
        }
        List<String> empty = ShRequiredFields.missing(draft.getShPostType(), data);
        if (!empty.isEmpty()) {
            throw new ShRequiredFields.RequiredFieldEmptyException(empty, named(draft));
        }
        // SH1010 — presence first, then shape. A page missing its title and holding a bad slug is
        // reported as the missing title: a caller fixes one thing, publishes again and is told the
        // next, which is better than a refusal listing two unrelated kinds of problem at once.
        ShFieldRules.requireValid(draft.getShPostType(), data, objectMapper, named(draft));
        // SH1010 (second part) — last, because it is the only rule here that costs a query, and a
        // page that is already refused for its own shape should not pay for a scan of its siblings.
        List<ShFieldRules.Violation> clashes = shFieldUniqueness.collisions(draft, data);
        if (!clashes.isEmpty()) {
            throw new ShFieldRules.ConstraintViolatedException(clashes, named(draft));
        }
    }

    /**
     * Every reason this stored row would be refused a publish (SH1009, SH1010) — what a plan says.
     *
     * <p>A dry run says what the apply would refuse rather than refusing: the console's publish
     * preview and the batch's plan both follow SH997's shape, where a gate that would stop the
     * publish is a sentence on the change rather than a thrown refusal. A plan that stopped at the
     * first problem would report one op of ten, which is most of what a plan is for.</p>
     */
    public List<String> publishBlockers(ShPostUnified draft) {
        JsonNode data = draft == null ? null : storedData(draft);
        if (data == null) {
            return List.of();
        }
        List<String> blockers = new java.util.ArrayList<>();
        for (String field : ShRequiredFields.missing(draft.getShPostType(), data)) {
            blockers.add("'" + field + "' is required and empty");
        }
        ShFieldRules.violations(draft.getShPostType(), data, objectMapper)
                .forEach(violation -> blockers.add(violation.says()));
        shFieldUniqueness.collisions(draft, data)
                .forEach(violation -> blockers.add(violation.says()));
        return List.copyOf(blockers);
    }

    /** The stored document, or {@code null} when it cannot be read — see the note above. */
    private JsonNode storedData(ShPostUnified draft) {
        try {
            return objectMapper.readTree(draft.getData() == null ? "{}" : draft.getData());
        } catch (JacksonException malformed) {
            // silent-catch: stored data this service cannot parse is not a required-field finding,
            // and the paths that DO report it — verify's `invalid-data` rule and the read that
            // surfaces it — are unchanged by this guard. Refusing here would turn one defect into
            // a message about another one.
            return null;
        }
    }

    /** What to call this page in a refusal: what a person would call it, never a uuid. */
    private static String named(ShPostUnified draft) {
        if (draft.getTitle() != null && !draft.getTitle().isBlank()) {
            return "\"" + draft.getTitle() + "\"";
        }
        return draft.getFurl() != null && !draft.getFurl().isBlank()
                ? draft.getFurl() : "this page";
    }

    private static String normalizeUrl(String furl) {
        if (furl == null || furl.isBlank()) {
            return null;
        }
        return furl.startsWith("/") ? furl : "/" + furl;
    }

    /**
     * Clean every rich-text field this data carries (SH956).
     *
     * <p>Runs here because this service is the one place the console, REST, the agent batch and the
     * Universal Editor's draft merge all pass through — a sanitizer in the rich-text widget would
     * defend the single writer that is easiest to go around.</p>
     *
     * <p><b>{@code HTML Editor} only.</b> {@code Ace Editor - HTML} is what {@code PageLayout} and
     * {@code Region} declare for their {@code HTML} field, and those are Handlebars templates
     * carrying {@code <script>} and a whole document on purpose: an allow-list over them would
     * strip every site's own layout on its next save. A code editor is where writing markup IS the
     * point; a rich-text box is where it is an accident.</p>
     *
     * @return the data to store — the argument itself when nothing was refused, so a clean value
     *         survives byte for byte and no digest moves
     */
    private JsonNode sanitizedMarkup(JsonNode data, ShPostType postType) {
        if (data == null || !data.isObject() || postType == null
                || postType.getShPostTypeAttrs() == null) {
            return data;
        }
        ObjectNode cleaned = null;
        for (ShPostTypeAttr attr : postType.getShPostTypeAttrs()) {
            if (attr.getShWidget() == null
                    || !ShSystemWidget.HTML_EDITOR.equals(attr.getShWidget().getName())) {
                continue;
            }
            JsonNode value = data.get(attr.getName());
            if (value == null || !value.isString()) {
                continue;
            }
            ShHtmlSanitizer.Result result = shHtmlSanitizer.sanitize(value.asString());
            if (!result.changed()) {
                continue;
            }
            if (cleaned == null) {
                cleaned = (ObjectNode) data.deepCopy();
            }
            cleaned.put(attr.getName(), result.html());
            log.info("SH956: sanitized field '{}' on write - removed {}",
                    attr.getName(), result.removed());
        }
        return cleaned != null ? cleaned : data;
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
                // SH722 — a hand-written comparator hides the same defect a Comparator chain
                // does, and this one feeds a write rather than a read.
                .sorted(java.util.Comparator.comparingInt(ShPostTypeAttr::getOrdinal)
                        .thenComparing(attr -> attr.getName() == null ? "" : attr.getName()));
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

    /**
     * @param furl the address to answer at, optional (SH651). Null or blank derives one from
     *             the title, corrected to the first free spelling in the site; a declared one
     *             is identity (SH81) and is normalized but never renumbered.
     */
    public record CreateRequest(String folderId, String postTypeId, JsonNode data,
                                ShPostState state, String owner, String locale,
                                String translationGroupId, String furl, String under) {

        /**
         * Without a parent path, which only the console's pages view sends (SH1333).
         *
         * @param under ignored when {@code furl} is declared; otherwise the derived URL is the
         *              first free one beneath this path
         */
        public CreateRequest(String folderId, String postTypeId, JsonNode data,
                             ShPostState state, String owner, String locale,
                             String translationGroupId, String furl) {
            this(folderId, postTypeId, data, state, owner, locale, translationGroupId, furl, null);
        }

        /** Without an address — what every caller wanted before SH651 gave them a choice. */
        public CreateRequest(String folderId, String postTypeId, JsonNode data,
                             ShPostState state, String owner, String locale,
                             String translationGroupId) {
            this(folderId, postTypeId, data, state, owner, locale, translationGroupId, null);
        }
    }

    public record UpdateRequest(JsonNode data, ShPostState state, String modifier) {
    }
}
