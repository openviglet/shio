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
import java.time.format.DateTimeParseException;

import com.viglet.shio.api.agent.ShAgentSessionContext;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.gate.ShPublishGateService;
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
    /** SH996 — what Publish would do, answered in the agent's own change-set shape. */
    private final ShConsolePublishPlan shConsolePublishPlan;
    /** SH1021 — the curator's bulk verbs, through the agent's writer. */
    private final ShPostUnifiedBulkService shPostUnifiedBulkService;
    /** SH1064 — the redirect a rename leaves behind, through the agent's own op. */
    private final ShConsoleRedirects shPostUnifiedRedirects;
    private final ShPostUnifiedMoveService shPostUnifiedMoveService;
    /** SH997 — what this site says about content before it reaches visitors. */
    private final ShPublishGateService shPublishGateService;

    public ShPostUnifiedAPI(ShPostUnifiedWriteService shPostUnifiedWriteService,
                            ShConsolePostReadService shConsolePostReadService,
                            ShTenantQuotaService shTenantQuotaService,
                            ShContentAclService shContentAclService,
                            ShPostTypeRepository shPostTypeRepository,
                            ShPostDraftMergeService shPostDraftMergeService,
                            ShConsolePostVersion shConsolePostVersion,
                            ShPostRevisionService shPostRevisionService,
                            ShConsolePublishPlan shConsolePublishPlan,
                            ShPublishGateService shPublishGateService,
                            ShPostUnifiedBulkService shPostUnifiedBulkService,
                            ShConsoleRedirects shPostUnifiedRedirects,
                            ShPostUnifiedMoveService shPostUnifiedMoveService) {
        this.shPostUnifiedRedirects = shPostUnifiedRedirects;
        this.shPostUnifiedMoveService = shPostUnifiedMoveService;
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.shConsolePostReadService = shConsolePostReadService;
        this.shTenantQuotaService = shTenantQuotaService;
        this.shContentAclService = shContentAclService;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shPostDraftMergeService = shPostDraftMergeService;
        this.shConsolePostVersion = shConsolePostVersion;
        this.shPostRevisionService = shPostRevisionService;
        this.shConsolePublishPlan = shConsolePublishPlan;
        this.shPostUnifiedBulkService = shPostUnifiedBulkService;
        this.shPublishGateService = shPublishGateService;
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
            // SH1084 — SH998's waiver, as the publish button takes it: this route publishes too.
            @RequestParam(name = "override", required = false) String override,
            Principal principal) {
        shContentAclService.requireWrite(id);
        boolean publish = Boolean.TRUE.equals(payload.publish());
        if (publish) {
            // SH1084 — save-and-publish asked only for WRITE, so SH995's separation between who
            // edits and who puts it live had a door here. PUBLISH, as the button asks.
            shContentAclService.requirePublish(id);
        }
        // The draft is the subject: a merge writes the draft row, which is also what the editor and
        // the Universal Editor are looking at when they compose one.
        shConsolePostVersion.require(ifMatch, id, ShPostState.DRAFT);
        ShConsolePost written = shConsolePostReadService.written(shPostDraftMergeService
                .merge(id, payload.attrs() == null ? Map.of() : payload.attrs(), publish,
                        principal != null ? principal.getName() : null, override)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + id)));
        return ResponseEntity.ok().eTag(shConsolePostVersion.etagAfterWriting(id)).body(written);
    }

    /**
     * @param attrs   field name → new value; a field not named keeps the value it had
     * @param publish promote the resulting draft in the same call
     */
    public record PatchPayload(Map<String, JsonNode> attrs, Boolean publish) {
    }

    /**
     * Publish the draft (SH654), or — with {@code dryRun} — say what publishing it would do.
     *
     * <h2>SH996: the curator's publish gets the agent's safeguards</h2>
     *
     * <p>The agent's publish has a dry run, a confirm token and a review entry; the console's had a
     * button. So the surface P7 was written <em>for</em> was the one with none of P7's instruments.
     * {@code dryRun=true} answers the same {@code ShAgentChangeSet} the batch surface returns and
     * writes nothing — one contract, two callers — and the routes that come with it are on the read
     * (`include=impact`), so the console can say which URLs move before it arms the button.</p>
     *
     * <p>A real publish is recorded under a {@code console:<curator>:<date>} session, which is what
     * puts it in the review queue: until now a curator who published the wrong draft had the same
     * need an agent run has and no screen for it.</p>
     */
    @PostMapping("/{id}/publish")
    public ResponseEntity<?> publish(
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestParam(name = "dryRun", required = false, defaultValue = "false") boolean dryRun,
            // SH998 — the sanctioned way past a failing gate, and its price is the sentence: a gate
            // with no way past it is one a team removes the day it blocks something urgent.
            @RequestParam(name = "override", required = false) String override,
            // SH1064 — keep the address this publish is about to vacate answering. Off unless the
            // caller asks, and the console asks by default: the dialog already prints which URL
            // stops resolving and how many pages link to it, and until now that paragraph was
            // information with nothing to do about it.
            @RequestParam(name = "redirect", required = false, defaultValue = "false")
            boolean redirect,
            Principal principal) {
        // SH995 — PUBLISH rather than WRITE: a curator who may edit this page is not automatically
        // the one who decides it goes live. The draft is saved either way, which is what the refusal
        // says. Asked on the dry run too: a plan a caller may not carry out is a plan that misleads.
        shContentAclService.requirePublish(id);
        if (dryRun) {
            return ResponseEntity.ok(shConsolePublishPlan.of(id));
        }
        // SH730's rule, and the agent's: the DRAFT is the subject. Publishing is not a content
        // change, so matching against the PUBLISHED row would refuse the case publishing exists for.
        shConsolePostVersion.require(ifMatch, id, ShPostState.DRAFT);
        // SH997 — the same gates the agent's op evaluates. A check the console could publish around
        // would be a rule with a door in it that nobody documented. SH998 — unless a person waived
        // it, in which case what they wrote travels into the audit row beside the publish.
        String waived = shPublishGateService.requirePost(id, override);
        String actor = principal != null ? principal.getName() : null;
        // SH1064 — read before the publish, because afterwards the live row is at the draft's URL
        // and the address that stopped resolving is nobody's. The same order `post.move` asks its
        // own question in, for the same reason.
        String vacated = redirect ? shConsolePublishPlan.vacatedUrl(id) : null;
        ShAgentSessionContext.set(ShAgentSessionContext.consoleSession(actor, Instant.now()));
        try {
            ShConsolePost written = shConsolePostReadService
                    .written(shPostUnifiedWriteService.publish(id, actor, waived));
            if (vacated != null) {
                // Composed (P10, SH1021's precedent): the op the agent surface runs, through the
                // one service that knows what writing a redirect means. A console copy of it is
                // how two surfaces come to disagree about whether a redirect is published.
                shPostUnifiedRedirects.after(id, vacated, actor);
            }
            return ResponseEntity.ok().eTag(shConsolePostVersion.etagAfterWriting(id)).body(written);
        } finally {
            // The thread is pooled, so a session left bound would stamp the next request's audit
            // rows with this curator's day — the leak SH639's sweep had to fix one layer down.
            ShAgentSessionContext.clear();
        }
    }

    /**
     * SH1021 — a verb over a selection, through the writer the agent's batch runs through.
     *
     * <p>The MCP publish tool has taken an array of addresses and promised atomicity since it
     * shipped. The console's batch menu was copy, move and delete, and REST had single-id publish,
     * so a curator could not do what an agent does in one call — and the obvious fix, a publish
     * loop in a console service, is what lets two surfaces drift about what publishing means.</p>
     *
     * <p>So this composes (P10): it builds ops and hands them to {@code ShAgentBatchService}. One
     * transaction, one vocabulary, one set of gates. The answer is the change set that service
     * already returns — an outcome and a problem per item — so a bare 200 over a partly-failed run
     * is not a shape this can return by accident.</p>
     *
     * <p>{@code dryRun} defaults to <b>true</b>. A bulk verb is the one shape where a mistake is
     * multiplied by the size of the selection, so the request a caller sends by accident is a
     * plan.</p>
     */
    @PostMapping("/bulk")
    public ResponseEntity<Object> bulk(@RequestBody BulkPayload payload, Principal principal) {
        boolean dryRun = payload.dryRun() == null || payload.dryRun();
        return ResponseEntity.ok(shPostUnifiedBulkService.run(
                payload.op(), payload.ids(), dryRun, payload.confirm(), payload.when(),
                principal != null ? principal.getName() : null));
    }

    /**
     * @param op      PUBLISH, UNPUBLISH or DELETE
     * @param ids     the posts this acts on
     * @param dryRun  absent means true: a bulk verb multiplies a mistake by the selection
     * @param confirm the token a destructive op needs, from its own dry run (SH108)
     * @param when    an ISO-8601 instant, to schedule instead of acting now (SH342)
     */
    public record BulkPayload(ShPostUnifiedBulkService.Op op, java.util.List<String> ids,
                              Boolean dryRun, String confirm, String when) {
    }

    /**
     * SH1132 — rename this page, through the writer the agent's {@code post.move} runs through.
     *
     * <p>A post's friendly URL is its identity (SH81) and the console had no writer for one: a
     * curator who wanted {@code /pricing} to become {@code /plans} had to ask an agent, or create a
     * second post and delete the first — the silently destructive path {@code post.move} exists to
     * prevent.</p>
     *
     * <p>{@code dryRun} defaults to <b>true</b>, for the reason a bulk verb's does (SH1021) and one
     * more that is specific to this: both state rows move together, so <b>a rename of a published
     * page changes the live URL at once, with no publish step</b>. The plan is how that stops being
     * the surprise P7 forbids — it says whether the page is live, which address is being vacated,
     * and how many pages link to it.</p>
     */
    @PostMapping("/{id}/move")
    public ResponseEntity<Object> move(@PathVariable String id,
                                       @RequestBody MovePayload payload,
                                       @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                       Principal principal) {
        boolean dryRun = payload.dryRun() == null || payload.dryRun();
        // SH985 — conditional, and SH1132 is a write that needs it: a curator renaming a page an
        // agent has already moved would otherwise take the address back without either knowing. A
        // rename is about the post rather than one snapshot, so the draft answers when there is one
        // and the live row otherwise — the same pair `delete` requires, for the same reason.
        //
        // Only on a real run: a plan writes nothing, so there is nothing for a version to protect,
        // and demanding one would make the harmless step the one that needs a fresh read.
        if (!dryRun) {
            shConsolePostVersion.require(ifMatch, id, ShPostState.DRAFT, ShPostState.PUBLISHED);
        }
        return ResponseEntity.ok(shPostUnifiedMoveService.run(id, payload.url(),
                Boolean.TRUE.equals(payload.redirect()), dryRun,
                principal != null ? principal.getName() : null));
    }

    /**
     * @param url      the address the page should answer at
     * @param redirect keep the vacated address answering, through SH1064's writer
     * @param dryRun   absent means true: a rename of a live page moves the live URL at once
     */
    public record MovePayload(String url, Boolean redirect, Boolean dryRun) {
    }

    @PostMapping("/{id}/unpublish")
    public ResponseEntity<Void> unpublish(@PathVariable String id, Principal principal) {
        shContentAclService.requirePublish(id);
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
        // SH995 — a schedule is a publish with a date on it.
        shContentAclService.requirePublish(id);
        try {
            return ResponseEntity.ok(shConsolePostReadService.written(shPostUnifiedWriteService.schedule(
                    id, instantOf(payload.publishAt(), "publishAt"), instantOf(payload.unpublishAt(), "unpublishAt"),
                    principal != null ? principal.getName() : null)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * SH1179 — an instant as either shape a caller has in hand: epoch millis, or the ISO-8601
     * string {@code /agent/find} returns for the same field and the console sends. The endpoint
     * read only millis, so every schedule saved from the console was a 400 the dialog reported as
     * a generic failure. Absent or {@code null} clears; anything else is a teaching 400 (P6).
     */
    static Instant instantOf(JsonNode value, String field) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (value.isIntegralNumber()) {
            return Instant.ofEpochMilli(value.asLong());
        }
        if (value.isString()) {
            try {
                return Instant.parse(value.asString());
            } catch (DateTimeParseException e) {
                throw new ScheduleInstantException(field, value.asString());
            }
        }
        throw new ScheduleInstantException(field, value.toString());
    }

    /**
     * {@code publishAt}/{@code unpublishAt}: an ISO-8601 instant or epoch millis each, and
     * {@code null} clears that side.
     */
    public record SchedulePayload(JsonNode publishAt, JsonNode unpublishAt) {
    }

    /** SH1179 — a schedule instant that is neither shape, refused with both spelled out. */
    static final class ScheduleInstantException extends ShTeachingException {
        ScheduleInstantException(String field, String sent) {
            super(ShProblem.badRequest("schedule-instant", "A schedule time must be an instant")
                    .withDetail("`" + field + "` was " + sent + ", which is neither an ISO-8601 instant"
                            + " nor epoch milliseconds.")
                    .withFix("Send an ISO-8601 instant in UTC, as /agent/find returns it, or epoch"
                            + " milliseconds; send null to clear that side.")
                    .withAt(field)
                    .withExample(Map.of(field, "2026-10-10T12:00:00Z")));
        }
    }
}
