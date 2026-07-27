/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet.Change;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.UpdateRequest;
import com.viglet.shio.api.folder.ShFolderWriteService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;
import com.viglet.shio.tenant.ShTenantQuotaService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The batch mutation engine (SH81) — N addressed content operations, applied as one
 * transaction, with a {@code dryRun} that produces the same report and writes nothing.
 *
 * <h2>What this removes</h2>
 *
 * <p>The read-modify-write loop. Against the id-keyed endpoints, "build a 12-page
 * structure" is roughly two dozen calls, each of which returns a UUID the agent must
 * carry to the next one — and carrying UUIDs across a session is the failure mode
 * SH79 was written to end. Here every op names its target by address, so the batch is
 * a <em>statement about content</em> rather than a script over handles, and running it
 * twice is running it once (P5).</p>
 *
 * <h2>Composition, not a third contract</h2>
 *
 * <p>Law P10: nothing here writes to the database directly. Folders go through
 * {@link ShFolderWriteService} and posts through {@link ShPostUnifiedWriteService}, so
 * the SH51 audit trail, the SH12 webhooks, the SH55 trash semantics and the
 * title/summary derivation all behave exactly as they do from the console. Law from
 * §XII, enforced here rather than deferred to SH84: the agent path is <b>never</b> a
 * privilege escalation, so the SH54 content ACL and the SH22 tenant quota are checked
 * on the same objects the console checks them on.</p>
 *
 * <h2>Why dry-run simulates instead of writing-and-rolling-back</h2>
 *
 * <p>Rolling back a real write is easy to implement and wrong: audit rows, lifecycle
 * events and quota counters have already happened by the time the rollback is decided,
 * and a plan that perturbs the instance is not a plan. So a dry run performs no writes
 * at all and keeps the objects earlier ops <em>would</em> have created in a pending set
 * — otherwise "create /blog, then put a post in /blog" would report a spurious 404 on
 * its second line, and the plan would be useless for exactly the multi-step structures
 * this endpoint exists for.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShAgentBatchService {

    public static final String FOLDER_UPSERT = "folder.upsert";
    public static final String FOLDER_DELETE = "folder.delete";
    public static final String POST_UPSERT = "post.upsert";
    public static final String POST_MOVE = "post.move";
    public static final String POST_PUBLISH = "post.publish";
    public static final String POST_UNPUBLISH = "post.unpublish";
    public static final String POST_DELETE = "post.delete";

    /** The closed op vocabulary, in the order a teaching error lists it. */
    public static final List<String> OPS = List.of(
            FOLDER_UPSERT, FOLDER_DELETE, POST_UPSERT, POST_MOVE, POST_PUBLISH, POST_UNPUBLISH,
            POST_DELETE);

    /**
     * The ops that destroy something and therefore need confirming (SH108). Everything else
     * on this surface is recoverable by another write; these two are not.
     */
    public static final List<String> DESTRUCTIVE_OPS = List.of(FOLDER_DELETE, POST_DELETE);

    private static final List<ShPostState> BOTH_STATES =
            List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    private final ShAgentSiteAccess siteAccess;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShFolderWriteService shFolderWriteService;
    private final ShPostUnifiedWriteService shPostUnifiedWriteService;
    private final ShContentAclService shContentAclService;
    private final ShTenantQuotaService shTenantQuotaService;
    private final ObjectMapper objectMapper;
    private final ShAgentProperties properties;

    public ShAgentBatchService(ShAgentSiteAccess siteAccess,
                               ShFolderRepository shFolderRepository,
                               ShPostUnifiedRepository shPostUnifiedRepository,
                               ShPostTypeRepository shPostTypeRepository,
                               ShFolderWriteService shFolderWriteService,
                               ShPostUnifiedWriteService shPostUnifiedWriteService,
                               ShContentAclService shContentAclService,
                               ShTenantQuotaService shTenantQuotaService,
                               ObjectMapper objectMapper,
                               ShAgentProperties properties) {
        this.siteAccess = siteAccess;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shFolderWriteService = shFolderWriteService;
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.shContentAclService = shContentAclService;
        this.shTenantQuotaService = shTenantQuotaService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Runs a batch. One transaction: any op that throws rolls back every op before it,
     * because a half-applied structure costs the caller more than a clean failure.
     *
     * @param batch the ops and the dry-run flag
     * @param ids   include object ids in the result rows (SH121 default: off)
     * @param actor the principal recorded as owner/modifier in the SH51 audit trail
     */
    @Transactional
    public ShAgentChangeSet run(ShAgentBatch batch, boolean ids, String actor) {
        List<ShAgentBatch.Op> ops = validate(batch);
        // SH107 — publishing is a capability, not a default. Checked before anything runs and
        // on a dryRun too: an agent that cannot publish should learn that while planning, not
        // after it has built twelve pages around the assumption.
        requireMayPublish(ops);
        // SH108 — a destructive run takes two calls, and the second one carries the digest of
        // the *plan*. So a batch that could delete is planned here first: what it would really
        // destroy is what needs confirming, and a delete of something already gone is a noop
        // that needs nothing (which is what keeps a document re-runnable).
        if (!batch.dryRun() && ShAgentConfirm.mayDestroy(ops)) {
            ShAgentChangeSet plan = execute(ops, new Session(true, ids, actor));
            String expected = ShAgentConfirm.ofPlan(plan);
            if (expected != null) {
                ShAgentConfirm.require(batch.confirm(), expected, plan);
            }
        }
        ShAgentChangeSet result = execute(ops, new Session(batch.dryRun(), ids, actor));
        // The token rides back on a dryRun only: on a real run it has been spent, and echoing
        // it would invite a caller to cache one and stop planning.
        return batch.dryRun()
                ? new ShAgentChangeSet(true, result.ops(), result.changed(), result.results(),
                        ShAgentConfirm.ofPlan(result))
                : result;
    }

    /** Runs the ops through one session — a plan or a real write, depending on the session. */
    private ShAgentChangeSet execute(List<ShAgentBatch.Op> ops, Session session) {
        List<Change> results = new ArrayList<>(ops.size());
        for (int index = 0; index < ops.size(); index++) {
            try {
                results.add(dispatch(ops.get(index), session));
            } catch (ShTeachingException failure) {
                // Re-thrown, not wrapped: the original problem already teaches what was
                // wrong (allowed values, didYouMean, an example). All this adds is
                // *where* — without which a caller has to guess which of 40 ops failed.
                throw new BatchOpException(failure.problem().withAt("ops[" + index + "]"));
            }
        }
        int changed = (int) results.stream().filter(Change::isChange).count();
        return new ShAgentChangeSet(session.dryRun, ops.size(), changed, List.copyOf(results));
    }

    /**
     * Refuses a publish this credential was not issued (SH107).
     *
     * <p>Only token-authenticated callers are gated: a console session is a human (or the CLI
     * running as one), and the SH54 ACL is what bounds them. The check is here rather than at
     * the publish site so that it fails before the first write, which is the difference between
     * "you cannot publish" and a half-built site with a 403 at the end.</p>
     */
    private static void requireMayPublish(List<ShAgentBatch.Op> ops) {
        if (ShApiTokenContext.currentMayPublish()) {
            return;
        }
        boolean publishes = ops.stream()
                .anyMatch(op -> op != null && op.op() != null
                        && POST_PUBLISH.equals(op.op().trim().toLowerCase(Locale.ROOT)));
        if (publishes) {
            throw new PublishNotPermittedException();
        }
    }

    /** A 403: this token writes drafts but may not make them live (SH107). */
    public static class PublishNotPermittedException extends ShTeachingException {

        public PublishNotPermittedException() {
            super(ShProblem.of("publish-not-permitted", HttpStatus.FORBIDDEN,
                            "this token may not publish")
                    .withDetail("The credential this request authenticated with writes drafts "
                            + "only. Publishing is what changes the live site, so it is granted "
                            + "per token rather than implied by write access.")
                    .withFix("Drop the publish op (the draft is written and a human can publish "
                            + "it), or have an operator grant `mayPublish` on this token.")
                    .withExample("{\"ops\":[{\"op\":\"post.upsert\", …}]}"));
        }
    }

    private List<ShAgentBatch.Op> validate(ShAgentBatch batch) {
        List<ShAgentBatch.Op> ops = batch == null || batch.ops() == null ? List.of() : batch.ops();
        if (ops.isEmpty()) {
            throw new EmptyBatchException();
        }
        int max = properties.getBatch().getMaxOps();
        if (ops.size() > max) {
            throw new BatchTooLargeException(ops.size(), max);
        }
        return ops;
    }

    private Change dispatch(ShAgentBatch.Op op, Session session) {
        String name = op.op() == null ? "" : op.op().trim().toLowerCase(Locale.ROOT);
        return switch (name) {
            case FOLDER_UPSERT -> folderUpsert(op, session);
            case FOLDER_DELETE -> folderDelete(op, session);
            case POST_UPSERT -> postUpsert(op, session);
            case POST_MOVE -> postMove(op, session);
            case POST_PUBLISH -> postPublish(op, session);
            case POST_UNPUBLISH -> postUnpublish(op, session);
            case POST_DELETE -> postDelete(op, session);
            default -> throw new UnknownOpException(op.op());
        };
    }

    // --- folder ops ---

    /**
     * Ensures a folder path exists, creating every missing level. Idempotent by
     * construction: the second run finds everything and reports {@code noop}.
     */
    private Change folderUpsert(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.FOLDER);
        ShSite site = requireSite(address.site());
        ShFolder parent = requireRootFolder(site);
        List<String> created = new ArrayList<>();
        StringBuilder path = new StringBuilder();
        boolean pending = false;

        for (String segment : segments(address.path())) {
            path.append('/').append(segment);
            if (pending) {
                // Everything below the first missing level is necessarily missing too.
                created.add(path.toString());
                session.planFolder(site, path.toString());
                continue;
            }
            Optional<ShFolder> child = childByName(parent, segment);
            if (child.isPresent()) {
                parent = child.get();
                continue;
            }
            created.add(path.toString());
            session.planFolder(site, path.toString());
            if (session.dryRun) {
                pending = true;
                continue;
            }
            shContentAclService.requireWrite(parent.getId());
            ShFolder payload = new ShFolder();
            payload.setName(segment);
            parent = shFolderWriteService.create(parent.getId(), payload, session.actor)
                    .orElseThrow(() -> new UnresolvableParentException(address));
        }

        if (created.isEmpty()) {
            return session.change(op, address, Change.NOOP, null, parent.getId());
        }
        String detail = created.size() > 1 ? "creates " + String.join(", ", created) : null;
        return session.change(op, address, Change.CREATE, detail, pending ? null : parent.getId());
    }

    private Change folderDelete(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.FOLDER);
        ShSite site = requireSite(address.site());
        Optional<ShFolder> folder = findFolder(site, address.path());
        if (folder.isEmpty()) {
            return session.change(op, address, Change.NOOP, "already absent", null);
        }
        String id = folder.get().getId();
        if (!session.dryRun) {
            shContentAclService.requireWrite(id);
            shFolderWriteService.delete(id);
        }
        // Folder deletion cascades to every post beneath it (ShFolderWriteService), so
        // the plan says so rather than letting the caller discover it afterwards (P7).
        return session.change(op, address, Change.DELETE, "with all content beneath it", id);
    }

    // --- post ops ---

    private Change postUpsert(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.POST);
        ShSite site = requireSite(address.site());
        Optional<ShPostUnified> existing = findPost(site, address.path());

        if (existing.isPresent()) {
            ShPostUnified post = existing.get();
            if (op.data() == null || sameData(post, op.data())) {
                return session.change(op, address, Change.NOOP, null, post.getId());
            }
            if (!session.dryRun) {
                shContentAclService.requireWrite(post.getId());
                shPostUnifiedWriteService.update(post.getId(),
                        new UpdateRequest(op.data(), ShPostState.DRAFT, session.actor));
            }
            return session.change(op, address, Change.UPDATE, null, post.getId());
        }
        // Only reachable in a dry run — a real run would have found the row above.
        if (session.alreadyPlanned(address)) {
            return session.change(op, address, Change.NOOP, "created earlier in this batch", null);
        }

        ShPostType postType = requirePostType(op, address);
        ShFolder folder = requireTargetFolder(op, site, address, session);
        session.planPost(address);
        if (session.dryRun || folder == null) {
            return session.change(op, address, Change.CREATE, null, null);
        }
        shContentAclService.requireWrite(folder.getId());
        shTenantQuotaService.checkCanCreatePost();
        ShPostUnified created = shPostUnifiedWriteService.create(new CreateRequest(
                folder.getId(), postType.getId(), op.data(), ShPostState.DRAFT,
                session.actor, null, null));
        // The address *is* the identity here, so the furl is written from it rather
        // than derived from a title — two posts with the same headline must still be
        // two addresses, and the next run must find this one again.
        shPostUnifiedWriteService.setFurl(created.getId(), address.path());
        return session.change(op, address, Change.CREATE, null, created.getId());
    }

    /**
     * Re-addresses an existing post — a new friendly URL, a new folder, or both — while
     * keeping its id (SH134).
     *
     * <h2>Why the vocabulary needed a seventh verb</h2>
     *
     * <p>Create, update and delete cover <em>building</em> a site. Changing one needs a
     * way to say "this page is now at a different address", and the only thing an agent
     * could do without it was upsert at the new address — which creates a second post,
     * orphans the first at its old URL, and loses the original's history and audit
     * trail. That is a data-loss path an agent reaches by following the obvious route,
     * which makes it the batch's problem rather than the caller's.</p>
     *
     * <h2>The idempotence rule, which is what makes it re-runnable</h2>
     *
     * <p>Source absent but destination present is a {@code noop}, not a 404. A move is
     * the one op whose precondition its own success destroys, so a document containing
     * one would fail on the second apply if "I cannot find the source" were an error —
     * and a plan that cannot be re-run is the thing SH82 exists to avoid.</p>
     */
    private Change postMove(ShAgentBatch.Op op, Session session) {
        ShAddress from = writeAddress(op, ShAddress.Kind.POST);
        ShSite site = requireSite(from.site());
        ShAddress to = moveTarget(op, from);
        if (!to.site().equalsIgnoreCase(from.site())) {
            // Cross-site moves are refused rather than half-implemented: the destination
            // has its own root folder, its own ACL and (under SH22) potentially its own
            // tenant, and silently re-parenting across that boundary is the kind of
            // surprise P7 forbids. Copy + delete is the honest two-step.
            throw new CrossSiteMoveException(from, to);
        }
        ShFolder targetFolder = op.folder() == null || op.folder().isBlank()
                ? null : requireExistingFolder(op, site);

        Optional<ShPostUnified> source = findPost(site, from.path());
        if (source.isEmpty()) {
            Optional<ShPostUnified> already = findPost(site, to.path());
            if (already.isPresent()) {
                return session.change(op, to, Change.NOOP, "already at this address",
                        already.get().getId());
            }
            throw new ShAddressResolver.UnresolvedAddressException(from, List.of());
        }
        ShPostUnified post = source.get();
        // Asked before the move, not after: afterwards the published row is at the new
        // address and this lookup finds nothing, so the warning would never be emitted.
        boolean live = state(site, from, ShPostState.PUBLISHED).isPresent();

        boolean sameUrl = ShAddress.normalizePath(nullSafe(post.getFurl()))
                .equalsIgnoreCase(ShAddress.normalizePath(to.path()));
        boolean sameFolder = targetFolder == null
                || (post.getShFolder() != null && targetFolder.getId().equals(post.getShFolder().getId()));
        if (sameUrl && sameFolder) {
            return session.change(op, to, Change.NOOP, null, post.getId());
        }
        if (!sameUrl && findPost(site, to.path()).isPresent()) {
            throw new MoveTargetOccupiedException(from, to);
        }

        if (!session.dryRun) {
            shContentAclService.requireWrite(post.getId());
            if (targetFolder != null) {
                shContentAclService.requireWrite(targetFolder.getId());
            }
            shPostUnifiedWriteService.move(post.getId(), targetFolder,
                    sameUrl ? null : to.path(), session.actor);
        }
        // Both state rows move together, so a published post changes its live URL here
        // and now. Said out loud rather than discovered later (P7).
        return session.change(op, to, Change.MOVE,
                live ? "the live URL changes immediately" : null, post.getId());
    }

    /**
     * Where a move is going. Accepts a full {@code post:} address or a bare URL, and
     * defaults the site to the source's — a move that had to repeat the site would be a
     * cross-site move waiting to be typed by accident.
     */
    private ShAddress moveTarget(ShAgentBatch.Op op, ShAddress from) {
        boolean hasTo = op.to() != null && !op.to().isBlank();
        if (!hasTo && (op.folder() == null || op.folder().isBlank())) {
            throw new MissingOpFieldException("to", op.op(), from, "post:Blog/blog/new-url",
                    "A move needs a destination: a new address in `to`, a new folder in "
                            + "`folder`, or both.");
        }
        if (!hasTo) {
            return from;
        }
        String raw = op.to().trim();
        ShAddress target = raw.contains(":")
                ? ShAddress.parse(raw)
                : ShAddress.post(from.site(), raw);
        if (target.kind() != ShAddress.Kind.POST) {
            throw new WrongAddressKindException(op.op(), target, ShAddress.Kind.POST);
        }
        return target;
    }

    /** The destination folder of a move: it must already exist — a move never creates. */
    private ShFolder requireExistingFolder(ShAgentBatch.Op op, ShSite site) {
        String path = op.folder().startsWith("folder:")
                ? ShAddress.parse(op.folder()).path()
                : ShAddress.normalizePath(op.folder());
        return findFolder(site, path).orElseThrow(() ->
                new ShAddressResolver.UnresolvedAddressException(
                        ShAddress.folder(site.getName(), path), List.of()));
    }

    /**
     * Publishing is reported as a change only when the live rendering would actually
     * differ — a re-run of the same document must not claim to have published again.
     */
    private Change postPublish(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.POST);
        ShSite site = requireSite(address.site());
        // Dry run only: in a real run the draft genuinely exists by now and must really
        // be published — short-circuiting here would silently skip the publish and make
        // apply disagree with its own plan.
        if (session.dryRun && session.alreadyPlanned(address)) {
            return session.change(op, address, Change.PUBLISH, "the draft this batch creates", null);
        }
        ShPostUnified draft = state(site, address, ShPostState.DRAFT).orElse(null);
        Optional<ShPostUnified> published = state(site, address, ShPostState.PUBLISHED);
        if (draft == null) {
            if (published.isPresent()) {
                return session.change(op, address, Change.NOOP, "no draft to publish",
                        published.get().getId());
            }
            throw new ShAddressResolver.UnresolvedAddressException(address, List.of());
        }
        if (published.isPresent() && sameContent(draft, published.get())) {
            return session.change(op, address, Change.NOOP, null, draft.getId());
        }
        if (!session.dryRun) {
            shContentAclService.requireWrite(draft.getId());
            shPostUnifiedWriteService.publish(draft.getId(), session.actor);
        }
        return session.change(op, address, Change.PUBLISH, null, draft.getId());
    }

    private Change postUnpublish(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.POST);
        ShSite site = requireSite(address.site());
        Optional<ShPostUnified> published = state(site, address, ShPostState.PUBLISHED);
        if (published.isEmpty()) {
            return session.change(op, address, Change.NOOP, "not published", null);
        }
        String id = published.get().getId();
        if (!session.dryRun) {
            shContentAclService.requireWrite(id);
            shPostUnifiedWriteService.unpublish(id, session.actor);
        }
        return session.change(op, address, Change.UNPUBLISH, null, id);
    }

    private Change postDelete(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.POST);
        ShSite site = requireSite(address.site());
        Optional<ShPostUnified> post = findPost(site, address.path());
        if (post.isEmpty()) {
            return session.change(op, address, Change.NOOP, "already absent", null);
        }
        String id = post.get().getId();
        if (!session.dryRun) {
            shContentAclService.requireWrite(id);
            shPostUnifiedWriteService.delete(id, session.actor);
        }
        // SH55: this is the reversible trash, not a purge. Say so — an agent that
        // believes it destroyed something will not offer the human the undo.
        return session.change(op, address, Change.DELETE, "to trash (restorable)", id);
    }

    // --- resolution helpers, shared with the desired-state compiler ---

    /**
     * Sites this credential may write to — the same SH2 allow-list the reads use, from
     * the same place ({@link ShAgentSiteAccess}), so a write can never see a site a
     * read would hide.
     */
    ShSite requireSite(String nameOrId) {
        return siteAccess.require(nameOrId);
    }

    ShFolder requireRootFolder(ShSite site) {
        return siteAccess.rootFolder(site)
                .orElseThrow(() -> new UnresolvableParentException(ShAddress.site(site.getName())));
    }

    /** A folder by its path of names under a site's root. */
    Optional<ShFolder> findFolder(ShSite site, String path) {
        Optional<ShFolder> current = siteAccess.rootFolder(site);
        for (String segment : segments(path)) {
            if (current.isEmpty()) {
                return Optional.empty();
            }
            current = childByName(current.get(), segment);
        }
        return current;
    }

    /** A post by its friendly URL under a site, DRAFT-preferred (the editable row). */
    Optional<ShPostUnified> findPost(ShSite site, String furl) {
        Optional<ShPostUnified> draft = state(site, ShAddress.post(site.getName(), furl),
                ShPostState.DRAFT);
        return draft.isPresent()
                ? draft
                : state(site, ShAddress.post(site.getName(), furl), ShPostState.PUBLISHED);
    }

    private Optional<ShPostUnified> state(ShSite site, ShAddress address, ShPostState state) {
        // Both leading-slash spellings, mirroring ShAddressResolver so an address means
        // the same post here as it does on the read side.
        for (String candidate : furlCandidates(address.path())) {
            Optional<ShPostUnified> row = shPostUnifiedRepository
                    .findByShSite_IdAndFurlAndState(site.getId(), candidate, state);
            if (row.isPresent()) {
                return row;
            }
        }
        return Optional.empty();
    }

    /** A folder's direct subfolders, in display order — the desired-state prune walks these. */
    List<ShFolder> childrenOf(ShFolder folder) {
        return shFolderRepository.findByParentFolderOrderByPositionAsc(folder);
    }

    /** A folder's posts, one row per id, DRAFT-preferred — the same rule every read uses. */
    List<ShPostUnified> postsIn(ShFolder folder) {
        Map<String, ShPostUnified> byId = new LinkedHashMap<>();
        for (ShPostUnified row : shPostUnifiedRepository.findByShFolderAndStateIn(folder, BOTH_STATES)) {
            byId.merge(row.getId(), row, (kept, candidate) ->
                    kept.getState() == ShPostState.DRAFT ? kept : candidate);
        }
        return byId.values().stream()
                .sorted(Comparator.comparing((ShPostUnified row) -> nullSafe(row.getFurl()))
                        .thenComparing(ShPostUnified::getId))
                .toList();
    }

    private ShPostType requirePostType(ShAgentBatch.Op op, ShAddress address) {
        if (op.type() == null || op.type().isBlank()) {
            throw new MissingOpFieldException("type", op.op(), address,
                    "Article", "A new post needs a post-type; an existing one keeps its own.");
        }
        ShPostType postType = shPostTypeRepository.findByName(op.type().trim());
        if (postType == null) {
            throw new UnknownPostTypeException(op.type(), shPostTypeRepository.findAll().stream()
                    .map(ShPostType::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .sorted()
                    .toList());
        }
        return postType;
    }

    /**
     * The folder a new post goes in. Returns {@code null} when the folder does not
     * exist yet but an earlier op in this dry run would have created it — the plan is
     * about the end state, not about the order the simulation happened to observe.
     */
    private ShFolder requireTargetFolder(ShAgentBatch.Op op, ShSite site, ShAddress address,
                                         Session session) {
        if (op.folder() == null || op.folder().isBlank()) {
            throw new MissingOpFieldException("folder", op.op(), address, "/blog",
                    "A friendly URL says nothing about which folder a post lives in "
                            + "— they are separate namespaces.");
        }
        String path = op.folder().startsWith("folder:")
                ? ShAddress.parse(op.folder()).path()
                : ShAddress.normalizePath(op.folder());
        Optional<ShFolder> folder = findFolder(site, path);
        if (folder.isPresent()) {
            return folder.get();
        }
        if (session.isPlannedFolder(site, path)) {
            return null;
        }
        throw new ShAddressResolver.UnresolvedAddressException(
                ShAddress.folder(site.getName(), path), List.of());
    }

    // --- comparison ---

    /** True when the stored data is already exactly the requested data (P5). */
    private boolean sameData(ShPostUnified post, JsonNode desired) {
        return readTree(post.getData()).equals(desired);
    }

    /** True when publishing the draft would not change what a visitor sees. */
    private boolean sameContent(ShPostUnified draft, ShPostUnified published) {
        return readTree(draft.getData()).equals(readTree(published.getData()))
                && nullSafe(draft.getTitle()).equals(nullSafe(published.getTitle()))
                && nullSafe(draft.getFurl()).equals(nullSafe(published.getFurl()));
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

    // --- address handling ---

    /**
     * Parses a write address, refusing the lenient {@code site:} form and the
     * {@code id:} escape hatch.
     *
     * <p>Reads may guess which namespace was meant, because a wrong guess costs one
     * retry. A write may not: "upsert this path" that guessed wrong creates real
     * content in the wrong place and nothing reports it (SH79's standing rule). And
     * {@code id:} carries no path, so it cannot express where a post <em>should</em> be
     * — which is the whole content of an upsert.</p>
     */
    private ShAddress writeAddress(ShAgentBatch.Op op, ShAddress.Kind expected) {
        if (op.address() == null || op.address().isBlank()) {
            throw new MissingOpFieldException("address", op.op(), null,
                    expected.name().toLowerCase(Locale.ROOT) + ":<site>/<path>",
                    "Every op names what it acts on by address.");
        }
        ShAddress address = ShAddress.parse(op.address());
        if (address.kind() != expected) {
            throw new WrongAddressKindException(op.op(), address, expected);
        }
        return address;
    }

    private Optional<ShFolder> childByName(ShFolder parent, String name) {
        return shFolderRepository.findByParentFolderOrderByPositionAsc(parent).stream()
                .filter(child -> name.equalsIgnoreCase(nullSafe(child.getName())))
                .findFirst();
    }

    static List<String> segments(String path) {
        if (path == null || "/".equals(path)) {
            return List.of();
        }
        return Arrays.stream(path.split("/")).filter(segment -> !segment.isBlank()).toList();
    }

    private static List<String> furlCandidates(String path) {
        String normalized = ShAddress.normalizePath(path);
        return normalized.length() > 1
                ? List.of(normalized, normalized.substring(1))
                : List.of(normalized);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Per-run state: the flags, the actor, and what earlier ops in this run have
     * already created. The pending sets are maintained in <em>both</em> modes — in a
     * real run they also guard against a document that declares the same address twice.
     */
    private final class Session {

        private final boolean dryRun;
        private final boolean ids;
        private final String actor;
        private final Set<String> plannedFolders = new HashSet<>();
        private final Set<String> plannedPosts = new HashSet<>();

        private Session(boolean dryRun, boolean ids, String actor) {
            this.dryRun = dryRun;
            this.ids = ids;
            this.actor = actor;
        }

        private void planFolder(ShSite site, String path) {
            plannedFolders.add(ShAddress.folder(site.getName(), path).toString().toLowerCase(Locale.ROOT));
        }

        private boolean isPlannedFolder(ShSite site, String path) {
            return plannedFolders.contains(
                    ShAddress.folder(site.getName(), path).toString().toLowerCase(Locale.ROOT));
        }

        private void planPost(ShAddress address) {
            plannedPosts.add(address.toString().toLowerCase(Locale.ROOT));
        }

        private boolean alreadyPlanned(ShAddress address) {
            return plannedPosts.contains(address.toString().toLowerCase(Locale.ROOT));
        }

        private Change change(ShAgentBatch.Op op, ShAddress address, String action,
                              String detail, String id) {
            return new Change(op.op(), address.toString(), action, detail, ids ? id : null);
        }
    }

    // --- teaching errors (SH80) ---

    /** Re-thrown wrapper that adds {@code at: ops[n]} to whatever failed. */
    public static class BatchOpException extends ShTeachingException {

        public BatchOpException(ShProblem problem) {
            super(problem);
        }
    }

    /** A 400 for a batch with nothing in it. */
    public static class EmptyBatchException extends ShTeachingException {

        public EmptyBatchException() {
            super(ShProblem.badRequest("empty-batch", "The batch has no operations")
                    .withDetail("`ops` is required and must hold at least one operation.")
                    .withAllowed(OPS)
                    .withFix("Send at least one op, or use GET /api/v2/agent/context to read instead.")
                    .withExample("{\"ops\":[{\"op\":\"folder.upsert\",\"address\":\"folder:Blog/blog\"}]}"));
        }
    }

    /** A 400 for a batch above the configured ceiling. */
    public static class BatchTooLargeException extends ShTeachingException {

        public BatchTooLargeException(int sent, int max) {
            super(ShProblem.badRequest("batch-too-large", "Batch of " + sent + " ops is too large")
                    .withDetail("A single transaction is capped at " + max + " operations.")
                    .withFix("Split the work into batches of at most " + max
                            + " ops; each one is still atomic on its own.")
                    .withExample(max));
        }
    }

    /** A 400 naming the closed op vocabulary, with the nearest match. */
    public static class UnknownOpException extends ShTeachingException {

        public UnknownOpException(String sent) {
            super(ShProblem.badRequest("unknown-op",
                            "'" + (sent == null ? "" : sent) + "' is not a batch operation")
                    .withDetail("Ops are intent-named and deliberately few.")
                    .withAllowed(OPS)
                    .withDidYouMeanFrom(sent)
                    .withFix("Use one of the listed ops.")
                    .withExample(POST_UPSERT));
        }
    }

    /** A 400 for an op missing a field that op needs. */
    public static class MissingOpFieldException extends ShTeachingException {

        public MissingOpFieldException(String field, String op, ShAddress address,
                                       String example, String why) {
            super(ShProblem.badRequest("missing-op-field",
                            "`" + field + "` is required for " + op
                                    + (address == null ? "" : " on " + address))
                    .withDetail(why)
                    .withFix("Add `" + field + "` to the operation.")
                    .withExample(example));
        }
    }

    /** A 400 for an address whose scheme does not match the op. */
    public static class WrongAddressKindException extends ShTeachingException {

        public WrongAddressKindException(String op, ShAddress sent, ShAddress.Kind expected) {
            super(ShProblem.badRequest("wrong-address-kind",
                            op + " needs a " + expected.name().toLowerCase(Locale.ROOT) + " address")
                    .withDetail("'" + sent + "' names a "
                            + sent.kind().name().toLowerCase(Locale.ROOT)
                            + ". Writes never accept the lenient `site:` form or a bare `id:` — "
                            + "a folder path and a friendly URL are separate namespaces, and "
                            + "guessing which was meant would create content in the wrong place.")
                    .withAllowed(List.of(expected.name().toLowerCase(Locale.ROOT) + ":<site>/<path>"))
                    .withFix("Re-address the op with the `"
                            + expected.name().toLowerCase(Locale.ROOT) + ":` scheme.")
                    .withExample(expected == ShAddress.Kind.POST
                            ? "post:Blog/blog/hello-world" : "folder:Blog/blog"));
        }
    }

    /** A 400 naming the post-types that exist, with the nearest match. */
    public static class UnknownPostTypeException extends ShTeachingException {

        public UnknownPostTypeException(String sent, List<String> allowed) {
            super(ShProblem.badRequest("unknown-post-type", "No post-type named '" + sent + "'")
                    .withDetail("Post-types are referenced by their API name, not their title.")
                    .withAllowed(allowed)
                    .withDidYouMeanFrom(sent)
                    .withFix(allowed.isEmpty()
                            ? "Create the post-type first: PUT /api/v2/post-type/{name}."
                            : "Use one of the listed names, or create a new type with "
                                    + "PUT /api/v2/post-type/{name}.")
                    .withExample(allowed.isEmpty() ? "Article" : allowed.get(0)));
        }
    }

    /** A 409: something already lives at the address a move is aiming for. */
    public static class MoveTargetOccupiedException extends ShTeachingException {

        public MoveTargetOccupiedException(ShAddress from, ShAddress to) {
            super(ShProblem.of("move-target-occupied", HttpStatus.CONFLICT,
                            "'" + to + "' is already taken")
                    .withDetail("Moving '" + from + "' there would leave two posts claiming "
                            + "one URL, and the delivery layer would serve whichever it found "
                            + "first. A move never overwrites.")
                    .withFix("Pick a free address, or delete the post at '" + to + "' first "
                            + "with post.delete (it goes to the restorable trash).")
                    .withExample(to.toString()));
        }
    }

    /** A 400: a move that would cross a site boundary. */
    public static class CrossSiteMoveException extends ShTeachingException {

        public CrossSiteMoveException(ShAddress from, ShAddress to) {
            super(ShProblem.badRequest("cross-site-move",
                            "A move cannot change a post's site")
                    .withDetail("'" + from + "' is in a different site from '" + to + "'. "
                            + "Sites have separate folder trees, separate ACLs and — under "
                            + "multi-tenancy — separate tenants, so re-parenting across one "
                            + "silently is not something this op will do.")
                    .withFix("post.upsert the content into the destination site, then "
                            + "post.delete the original — two visible steps instead of one "
                            + "invisible one.")
                    .withExample("post:" + from.site() + "/blog/new-url"));
        }
    }

    /** A 500-ish 409: the site exists but has no root folder to hang content from. */
    public static class UnresolvableParentException extends ShTeachingException {

        public UnresolvableParentException(ShAddress address) {
            super(ShProblem.of("unresolvable-parent", HttpStatus.CONFLICT,
                            "Cannot place content under '" + address + "'")
                    .withDetail("The site has no root folder, so there is nowhere to create this.")
                    .withFix("Re-create the site, or import it from an exchange package.")
                    .withExample("folder:Blog/blog"));
        }
    }
}
