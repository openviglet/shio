/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.api.post.revision.ShPostRevisionService;
import com.viglet.shio.api.post.revision.ShRevisionRow;
import com.viglet.shio.api.post.type.ShUnknownPostTypeException;
import com.viglet.shio.api.post.unified.dto.ShConsolePost;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.UpdateRequest;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.tenant.ShTenantQuotaService;

import tools.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The console's post API.
 *
 * <p><b>Every post-shaped response is a {@link ShConsolePost}, never the entity</b> (SH176).
 * Serializing {@code ShPostUnified} was faithful to the storage shape and useless to the
 * form: the LAZY {@code shPostType} arrived as {@code null} and {@code data} arrived as a
 * JSON string. That included the write responses — the edit page rebinds its form from what
 * a save returns — so the reads and the writes had to move together.</p>
 */
@RestController
@RequestMapping("/api/v2/post-unified")
@Tag(name = "PostUnified", description = "Unified post model API")
public class ShPostUnifiedAPI {

    private final ShPostUnifiedWriteService shPostUnifiedWriteService;
    private final ShConsolePostReadService shConsolePostReadService;
    private final ShTenantQuotaService shTenantQuotaService;
    private final ShContentAclService shContentAclService;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShPostDraftMergeService shPostDraftMergeService;
    private final ShConsolePostVersion shConsolePostVersion;
    private final ShPostRevisionService shPostRevisionService;

    public ShPostUnifiedAPI(ShPostUnifiedWriteService shPostUnifiedWriteService,
                            ShConsolePostReadService shConsolePostReadService,
                            ShTenantQuotaService shTenantQuotaService,
                            ShContentAclService shContentAclService,
                            ShPostTypeRepository shPostTypeRepository,
                            ShPostDraftMergeService shPostDraftMergeService,
                            ShConsolePostVersion shConsolePostVersion,
                            ShPostRevisionService shPostRevisionService) {
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.shConsolePostReadService = shConsolePostReadService;
        this.shTenantQuotaService = shTenantQuotaService;
        this.shContentAclService = shContentAclService;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shPostDraftMergeService = shPostDraftMergeService;
        this.shConsolePostVersion = shConsolePostVersion;
        this.shPostRevisionService = shPostRevisionService;
    }

    @PostMapping
    public ShConsolePost create(@RequestBody CreatePayload payload, Principal principal) {
        // SH54 — WRITE on the target folder; SH22/D — tenant plan post quota (402).
        shContentAclService.requireWrite(payload.folderId());
        shTenantQuotaService.checkCanCreatePost();
        return shConsolePostReadService.written(shPostUnifiedWriteService.create(new CreateRequest(
                payload.folderId(),
                resolvePostTypeId(payload),
                payload.data(),
                payload.state() != null ? payload.state() : ShPostState.DRAFT,
                principal != null ? principal.getName() : null,
                payload.locale(),
                payload.translationGroupId(),
                payload.furl())));
    }

    /**
     * SH985 — {@code If-Match} makes this conditional, and the console always sends it.
     *
     * <p>The tag is the one {@link #get} answered with, which is {@code ShAgentPostDigest} in HTTP
     * clothing — the same value the agent's {@code expect} compares, so the two writers are asking
     * one question about one row rather than each keeping a version of their own.</p>
     */
    @PutMapping("/{id}")
    public ResponseEntity<ShConsolePost> update(
            @PathVariable String id,
            @RequestBody UpdatePayload payload,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            Principal principal) {
        shContentAclService.requireWrite(id);
        shConsolePostVersion.require(ifMatch, id,
                payload.state() != null ? payload.state() : ShPostState.DRAFT);
        ShConsolePost written = shConsolePostReadService.written(shPostUnifiedWriteService
                .update(id, new UpdateRequest(
                        payload.data(),
                        payload.state() != null ? payload.state() : ShPostState.DRAFT,
                        principal != null ? principal.getName() : null)));
        // SH985 — the write answers with the version it produced, so a second save needs no read.
        return ResponseEntity.ok().eTag(shConsolePostVersion.etagAfterWriting(id)).body(written);
    }

    /**
     * SH335 — merge a few fields into the draft, as a session-authenticated curator.
     *
     * <p>The Universal Editor edits two or three fields of a page and had no console verb for it:
     * {@link #update} is whole state (SH168 — an upsert declares everything), so the editor was
     * pointed at the CDA's {@code PATCH} and made a curator mint a {@code WRITE} token to save
     * content the session cookie already authorizes. That is P10 read backwards, and the two ways
     * out were a client read-modify-write or this. This, because the first reintroduces a lost
     * update on a surface whose premise is several curators on one site.</p>
     *
     * <p>Same ACL as every other write here ({@link ShContentAclService#requireWrite}) and the same
     * publish authority as {@link #publish} — no token, so nothing to leave in browser storage and
     * nothing carrying publish rights that SH107 never saw.</p>
     */
    @PatchMapping("/{id}")
    public ResponseEntity<ShConsolePost> patch(
            @PathVariable String id,
            @RequestBody PatchPayload payload,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            Principal principal) {
        shContentAclService.requireWrite(id);
        // The draft is the subject: a merge writes the draft row, which is also what the editor and
        // the Universal Editor are looking at when they compose one.
        shConsolePostVersion.require(ifMatch, id, ShPostState.DRAFT);
        ShConsolePost written = shConsolePostReadService.written(shPostDraftMergeService
                .merge(id, payload.attrs() == null ? Map.of() : payload.attrs(),
                        Boolean.TRUE.equals(payload.publish()),
                        principal != null ? principal.getName() : null)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + id)));
        return ResponseEntity.ok().eTag(shConsolePostVersion.etagAfterWriting(id)).body(written);
    }

    /**
     * @param attrs   field name → new value; a field not named keeps the value it had
     * @param publish promote the resulting draft in the same call
     */
    public record PatchPayload(Map<String, JsonNode> attrs, Boolean publish) {
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ShConsolePost> publish(
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            Principal principal) {
        shContentAclService.requireWrite(id);
        // SH730's rule, and the agent's: the DRAFT is the subject. Publishing is not a content
        // change, so matching against the PUBLISHED row would refuse the case publishing exists for.
        shConsolePostVersion.require(ifMatch, id, ShPostState.DRAFT);
        ShConsolePost written = shConsolePostReadService.written(shPostUnifiedWriteService
                .publish(id, principal != null ? principal.getName() : null));
        return ResponseEntity.ok().eTag(shConsolePostVersion.etagAfterWriting(id)).body(written);
    }

    @PostMapping("/{id}/unpublish")
    public ResponseEntity<Void> unpublish(@PathVariable String id, Principal principal) {
        shContentAclService.requireWrite(id);
        shPostUnifiedWriteService.unpublish(id, principal != null ? principal.getName() : null);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,
                                       @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                       Principal principal) {
        shContentAclService.requireWrite(id);
        // A delete is about the post rather than about one snapshot, so the draft answers when
        // there is one — it is what a curator was looking at — and the live row otherwise.
        shConsolePostVersion.require(ifMatch, id, ShPostState.DRAFT, ShPostState.PUBLISHED);
        shPostUnifiedWriteService.delete(id, principal != null ? principal.getName() : null);
        return ResponseEntity.noContent().build();
    }

    /**
     * SH117 — {@code postType} (the API name) is accepted in place of
     * {@code postTypeId}. The console picks a post-type off the name-keyed
     * {@code /api/v2/post-type} surface and has no id to send; resolving the name
     * here is one lookup on the server, where the id lives, instead of a round trip
     * whose only purpose is to translate a name the caller already knows.
     * {@code postTypeId} still wins when both are present, so every existing caller
     * is untouched.
     */
    /**
     * @param furl the address to answer at, optional (SH651). Omitted, one is derived from
     *             the post's title field and corrected to the first free spelling in the
     *             site — before SH651 nothing derived one and the post was unreachable.
     */
    public record CreatePayload(String folderId, String postTypeId, String postType, JsonNode data,
                                ShPostState state, String locale, String translationGroupId,
                                String furl) {
    }

    /**
     * The post-type id to create under: {@code postTypeId} if given, otherwise
     * {@code postType} resolved by name. A name that resolves to nothing is a
     * teaching 400 carrying the installed catalogue, not a null the writer would
     * fail on later with no idea which of the two fields was wrong.
     */
    private String resolvePostTypeId(CreatePayload payload) {
        if (payload.postTypeId() != null && !payload.postTypeId().isBlank()) {
            return payload.postTypeId();
        }
        String name = payload.postType();
        if (name == null || name.isBlank()) {
            throw new ShUnknownPostTypeException(null, postTypeNames());
        }
        ShPostType postType = shPostTypeRepository.findByName(name);
        if (postType == null) {
            throw new ShUnknownPostTypeException(name, postTypeNames());
        }
        return postType.getId();
    }

    /** The post-type names a caller may use, sorted so an identical failure answers identically (P5). */
    private List<String> postTypeNames() {
        return shPostTypeRepository.findAll().stream()
                .map(ShPostType::getName)
                .filter(name -> name != null && !name.isBlank())
                .sorted()
                .toList();
    }

    public record UpdatePayload(JsonNode data, ShPostState state) {
    }

    /** SH53 — body of {@code POST /{id}/translate}: the target locale + optional seed data. */
    public record TranslatePayload(String locale, JsonNode data) {
    }

    @GetMapping("/folder/{folderId}")
    public List<ShConsolePost> listByFolder(@PathVariable String folderId,
                                            @RequestParam(name = "state", defaultValue = "PUBLISHED") ShPostState state) {
        shContentAclService.requireRead(folderId);
        return shConsolePostReadService.listByFolder(folderId, state);
    }

    /**
     * SH921 — and when there is nothing here, a 404 that says which of the two reasons it is.
     *
     * <p>A read is {@code findByIdAndState}, so a post published without ever being drafted
     * answers nothing for {@code ?state=draft}. That used to be {@code ResponseEntity.notFound()}
     * — an empty body, indistinguishable from an id nobody has ever heard of, leaving the caller
     * to guess between a wrong id, a wrong state and a deleted row. The console guessed too, and
     * rendered an empty form behind two failed requests.</p>
     */
    @GetMapping("/{id}")
    public ResponseEntity<ShConsolePost> get(@PathVariable String id,
                                             @RequestParam(name = "state", defaultValue = "PUBLISHED") ShPostState state) {
        shContentAclService.requireRead(id);
        return shConsolePostReadService.get(id, state)
                // SH985 — the tag the writes below compare, handed over by the read that produced
                // the row, so a caller never has to know how it is computed.
                .map(post -> ResponseEntity.ok().eTag(shConsolePostVersion.etagOf(id, state))
                        .body(post))
                .orElseThrow(() -> {
                    List<ShPostState> available = shConsolePostReadService.statesOf(id);
                    return available.isEmpty()
                            ? ShPostStateNotFoundException.noSuchPost(id)
                            : ShPostStateNotFoundException.wrongState(id, state, available);
                });
    }

    /**
     * The two rows this post has — draft and live (SH14).
     *
     * <p>Left exactly as it was by SH987, which is the point: this answers "what are the two states
     * of this post right now", and the revision history is a different question with a different
     * shape. A caller reading this today is reading what it always meant.</p>
     */
    @GetMapping("/{id}/versions")
    public List<ShConsolePost> versions(@PathVariable String id) {
        return shConsolePostReadService.versions(id);
    }

    /**
     * Every captured revision of this post, newest first and carrying no bodies (SH987).
     *
     * <p>A listing is how a caller chooses which revision to fetch or roll back to, so it answers
     * with numbers, dates, actors and the field names each revision changed — fifty bodies is the
     * page fifty times for a question the metadata answers (P3).</p>
     */
    @GetMapping("/{id}/revisions")
    public List<ShRevisionRow> revisions(@PathVariable String id) {
        shContentAclService.requireRead(id);
        return shPostRevisionService.rows(id);
    }

    /** One revision, with its document. */
    @GetMapping("/{id}/revisions/{version}")
    public ShRevisionRow revision(@PathVariable String id, @PathVariable int version) {
        shContentAclService.requireRead(id);
        return shPostRevisionService.row(id, version)
                .orElseThrow(() -> ShPostStateNotFoundException.noSuchRevision(id, version,
                        shPostRevisionService.rows(id).stream().map(ShRevisionRow::version).toList()));
    }

    /**
     * Put a revision back over the draft (SH987).
     *
     * <p>Over the draft and never over the live row, and it writes a new revision rather than
     * deleting any: a rollback is a write like every other, so the state it replaced is still
     * there to come forward to. Publishing it is the separate deliberate act (P7).</p>
     */
    @PostMapping("/{id}/revisions/{version}/restore")
    public ResponseEntity<ShConsolePost> restoreRevision(
            @PathVariable String id,
            @PathVariable int version,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            Principal principal) {
        shContentAclService.requireWrite(id);
        // SH985 — a rollback is a write, and a write onto a draft somebody else has moved on from
        // is the same surprise as any other stale save.
        shConsolePostVersion.require(ifMatch, id, ShPostState.DRAFT);
        ShConsolePost written = shConsolePostReadService.written(shPostUnifiedWriteService
                .restoreRevision(id, version, principal != null ? principal.getName() : null));
        return ResponseEntity.ok().eTag(shConsolePostVersion.etagAfterWriting(id)).body(written);
    }

    /** SH55 — posts currently in the trash for the active tenant. */
    @GetMapping("/trash")
    public List<ShConsolePost> trash() {
        return shConsolePostReadService.trash();
    }

    /** SH55 — restore a trashed post (clear its soft-delete marker). */
    @PostMapping("/{id}/untrash")
    public ResponseEntity<Void> untrash(@PathVariable String id, Principal principal) {
        shContentAclService.requireWrite(id);
        return shPostUnifiedWriteService.restoreFromTrash(id, principal != null ? principal.getName() : null)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /** SH55 — permanently delete a trashed post (irreversible; fires byte-cleanup + webhook). */
    @DeleteMapping("/{id}/purge")
    public ResponseEntity<Void> purge(@PathVariable String id, Principal principal) {
        shContentAclService.requireWrite(id);
        // SH224 — the actor, because the irreversible half of the trash is the one that most needs
        // attributing. It was the only content transition the audit trail did not carry.
        return shPostUnifiedWriteService.purge(id, principal != null ? principal.getName() : null)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /** SH53 — the sibling translations of this post's group (DRAFT-preferred, one per id). */
    @GetMapping("/{id}/translations")
    public List<ShConsolePost> translations(@PathVariable String id) {
        return shConsolePostReadService.translations(id);
    }

    /** SH53 — create a linked translation of this post in another locale. */
    @PostMapping("/{id}/translate")
    public ResponseEntity<ShConsolePost> translate(@PathVariable String id,
                                                   @RequestBody TranslatePayload payload,
                                                   Principal principal) {
        shContentAclService.requireWrite(id);
        try {
            return ResponseEntity.ok(shConsolePostReadService.written(shPostUnifiedWriteService.translate(
                    id, payload.locale(), payload.data(),
                    principal != null ? principal.getName() : null)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<ShConsolePost> restore(@PathVariable String id, Principal principal) {
        shContentAclService.requireWrite(id);
        try {
            return ResponseEntity.ok(shConsolePostReadService.written(
                    shPostUnifiedWriteService.restoreDraftFromPublished(
                            id, principal != null ? principal.getName() : null)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Set or clear the scheduled publish/unpublish timestamps (SH15). A
     * {@code null} field clears that schedule; the background sweep fires the
     * transition once the instant passes.
     */
    @PostMapping("/{id}/schedule")
    public ResponseEntity<ShConsolePost> schedule(@PathVariable String id,
                                                  @RequestBody SchedulePayload payload,
                                                  Principal principal) {
        shContentAclService.requireWrite(id);
        try {
            return ResponseEntity.ok(shConsolePostReadService.written(shPostUnifiedWriteService.schedule(
                    id, toInstant(payload.publishAt()), toInstant(payload.unpublishAt()),
                    principal != null ? principal.getName() : null)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private static Instant toInstant(Long epochMillis) {
        return epochMillis != null ? Instant.ofEpochMilli(epochMillis) : null;
    }

    /** {@code publishAt}/{@code unpublishAt} arrive as epoch millis in the JSON body. */
    public record SchedulePayload(Long publishAt, Long unpublishAt) {
    }
}
