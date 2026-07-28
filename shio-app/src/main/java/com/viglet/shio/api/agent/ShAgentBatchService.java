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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.api.site.ShSiteWriteService;
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
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;
import com.viglet.shio.tenant.ShTenantQuotaService;
import com.viglet.shio.utils.ShStableMap;

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
    public static final String FOLDER_MOVE = "folder.move";
    public static final String FOLDER_DELETE = "folder.delete";
    public static final String POST_UPSERT = "post.upsert";
    public static final String POST_MOVE = "post.move";
    public static final String POST_PUBLISH = "post.publish";
    public static final String POST_UNPUBLISH = "post.unpublish";
    public static final String POST_DELETE = "post.delete";

    /**
     * Creates a site (SH170) — the step the whole product is for, and the only op that took an
     * agent off this surface entirely.
     */
    public static final String SITE_UPSERT = "site.upsert";

    /** The closed op vocabulary, in the order a teaching error lists it. */
    public static final List<String> OPS = List.of(
            SITE_UPSERT, FOLDER_UPSERT, FOLDER_MOVE, FOLDER_DELETE, POST_UPSERT, POST_MOVE,
            POST_PUBLISH, POST_UNPUBLISH, POST_DELETE);

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
    private final ShSiteWriteService shSiteWriteService;
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
                               ShSiteWriteService shSiteWriteService,
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
        this.shSiteWriteService = shSiteWriteService;
        this.shContentAclService = shContentAclService;
        this.shTenantQuotaService = shTenantQuotaService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Where a run's ops came from — which is what its size ceiling is (SH138).
     *
     * <p>Not a field on {@link ShAgentBatch}: it would then be on the wire, and a caller
     * could claim the document ceiling for a hand-written batch. Only
     * {@code ShAgentDesiredStateService} may say {@code DOCUMENT}, because only the server
     * compiles one.</p>
     */
    public enum Source {

        /** The caller wrote these ops. Bounded by {@code shio.agent.batch.max-ops}. */
        REQUEST,

        /**
         * The server compiled these from a desired-state document, so the count includes
         * whatever {@code prune} swept up. Bounded by {@code max-document-ops}.
         */
        DOCUMENT
    }

    /**
     * What a plan may take as already there although the database does not have it yet (SH153).
     *
     * <h2>Why a plan needs to be told this at all</h2>
     *
     * <p>A blueprint brings its own post-types and, since SH151, possibly its own site — so
     * planning its content against the live model fails on the first post with <em>"no
     * post-type named Article"</em>, which is useless for exactly the case blueprints exist
     * for. The original fix was to provision inside the plan's transaction and roll back. It
     * was accurate and it cost a property nobody asked to give up: a plan could no longer run
     * on a read-only credential, a read replica, or a session whose transaction is
     * {@code readOnly}. For a surface whose safety story is "look before you write", that is
     * the wrong constraint.</p>
     *
     * <p>So the caller <em>declares</em> what its own run would create, and the dry run
     * resolves against it. This is the pending-folder idea one resource wider: the session
     * already keeps the folders earlier ops would have made, precisely so "create /blog, then
     * put a post in /blog" does not report a spurious 404.</p>
     *
     * <h2>The two rules that keep it honest</h2>
     *
     * <p><b>It is additive, never a substitute.</b> A post-type that really exists is resolved
     * live, and the declared field names are <em>unioned</em> with the live ones — so a caller
     * that declares a field the instance does not have gets SH168's "a real run will refuse
     * this write" finding unless it also intends to add that field. Declaring cannot make a
     * plan greener than the run it predicts.</p>
     *
     * <p><b>It is honoured on a dry run only.</b> {@code apply} resolves everything live, so a
     * declaration can never stand in for a write that did not happen.</p>
     *
     * @param site      the site this run would create, or {@code null}
     * @param postTypes post-type name → the field names it would declare
     */
    public record Declared(String site, Map<String, Set<String>> postTypes) {

        /** The ordinary case: everything an op names must already be there. */
        public static final Declared NOTHING = new Declared(null, Map.of());

        public Declared {
            postTypes = postTypes == null ? Map.of() : ShStableMap.copyOf(postTypes);
        }

        private boolean declaresSite(String nameOrId) {
            return site != null && nameOrId != null && site.equalsIgnoreCase(nameOrId.trim());
        }

        /** The fields this run would give {@code name}, empty when it declares no such type. */
        private Set<String> fieldsOf(String name) {
            if (name == null) {
                return Set.of();
            }
            String wanted = name.trim();
            for (Map.Entry<String, Set<String>> entry : postTypes.entrySet()) {
                if (wanted.equalsIgnoreCase(entry.getKey())) {
                    return entry.getValue() == null ? Set.of() : entry.getValue();
                }
            }
            return Set.of();
        }

        private boolean declaresPostType(String name) {
            if (name == null) {
                return false;
            }
            String wanted = name.trim();
            return postTypes.keySet().stream().anyMatch(wanted::equalsIgnoreCase);
        }
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
        return runOps(batch, ids, actor, Source.REQUEST, Declared.NOTHING);
    }

    /** As {@link #run(ShAgentBatch, boolean, String)}, for ops of a known {@link Source}. */
    @Transactional
    public ShAgentChangeSet run(ShAgentBatch batch, boolean ids, String actor, Source source) {
        return runOps(batch, ids, actor, source, Declared.NOTHING);
    }

    /**
     * As {@link #run(ShAgentBatch, boolean, String, Source)}, resolving a dry run against
     * what the caller says its own run would create (SH153).
     */
    @Transactional
    public ShAgentChangeSet run(ShAgentBatch batch, boolean ids, String actor, Source source,
                                Declared declared) {
        return runOps(batch, ids, actor, source, declared);
    }

    // The entry points are annotated and none calls another: a self-invocation would
    // bypass the proxy, so the inner annotation would be decoration rather than a transaction.
    private ShAgentChangeSet runOps(ShAgentBatch batch, boolean ids, String actor, Source source,
                                    Declared declared) {
        List<ShAgentBatch.Op> ops = validate(batch, source);
        // SH107 — publishing is a capability, not a default. Checked before anything runs and
        // on a dryRun too: an agent that cannot publish should learn that while planning, not
        // after it has built twelve pages around the assumption.
        requireMayPublish(ops);
        // SH108 — a destructive run takes two calls, and the second one carries the digest of
        // the *plan*. So a batch that could delete is planned here first: what it would really
        // destroy is what needs confirming, and a delete of something already gone is a noop
        // that needs nothing (which is what keeps a document re-runnable).
        if (!batch.dryRun() && ShAgentConfirm.mayDestroy(ops)) {
            ShAgentChangeSet plan = execute(ops, new Session(true, ids, actor, declared));
            String expected = ShAgentConfirm.ofPlan(plan);
            if (expected != null) {
                ShAgentConfirm.require(batch.confirm(), expected, plan);
            }
        }
        ShAgentChangeSet result = execute(ops, new Session(batch.dryRun(), ids, actor, declared));
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

    private List<ShAgentBatch.Op> validate(ShAgentBatch batch, Source source) {
        List<ShAgentBatch.Op> ops = batch == null || batch.ops() == null ? List.of() : batch.ops();
        if (ops.isEmpty()) {
            throw new EmptyBatchException();
        }
        // SH138 — a compiled document is bounded by its own, larger ceiling, and refused with
        // advice it can act on. Held to the hand-written batch's cap it failed with "split the
        // work into batches", which nobody can do to a converge they did not write.
        if (source == Source.DOCUMENT) {
            int max = properties.getBatch().getMaxDocumentOps();
            if (ops.size() > max) {
                throw new DocumentTooLargeException(ops.size(), countDeletes(ops), max);
            }
            return ops;
        }
        int max = properties.getBatch().getMaxOps();
        if (ops.size() > max) {
            throw new BatchTooLargeException(ops.size(), max);
        }
        return ops;
    }

    /** How many of a compiled document's ops destroy something — the half that explains the size. */
    private static int countDeletes(List<ShAgentBatch.Op> ops) {
        return (int) ops.stream()
                .filter(op -> op != null && op.op() != null)
                .filter(op -> DESTRUCTIVE_OPS.contains(op.op().trim().toLowerCase(Locale.ROOT)))
                .count();
    }

    private Change dispatch(ShAgentBatch.Op op, Session session) {
        String name = op.op() == null ? "" : op.op().trim().toLowerCase(Locale.ROOT);
        return switch (name) {
            case SITE_UPSERT -> siteUpsert(op, session);
            case FOLDER_UPSERT -> folderUpsert(op, session);
            case FOLDER_MOVE -> folderMove(op, session);
            case FOLDER_DELETE -> folderDelete(op, session);
            case POST_UPSERT -> postUpsert(op, session);
            case POST_MOVE -> postMove(op, session);
            case POST_PUBLISH -> postPublish(op, session);
            case POST_UNPUBLISH -> postUnpublish(op, session);
            case POST_DELETE -> postDelete(op, session);
            default -> throw new UnknownOpException(op.op());
        };
    }

    // --- site ops ---

    /**
     * Creates the site if it is not there, and <b>fills in what is empty</b> if it is
     * (SH170, SH184).
     *
     * <h2>Why the batch surface needed an eighth op</h2>
     *
     * <p>Seven ops could build everything inside a site and none of them could make one, so an
     * agent asked for "build me a site" found that step one was not here — and landed on
     * {@code POST /api/v2/site}: id-keyed, entity-shaped, and CSRF-gated, which answers a
     * stateless caller with a bodiless 403. Nothing to read, nothing to fix.</p>
     *
     * <h2>Fill what is empty, never replace what somebody chose (SH184)</h2>
     *
     * <p>SH170 made this op refuse to update at all, reasoning that a site's URL and
     * description are console concerns with a human looking at them. That is right about a
     * decision and wrong about a blank: {@code site.url} is the field that gates both visual
     * surfaces, so an agent could build a site, scaffold a front end, deploy it, and still not
     * connect the two without a human opening the console. Nothing about that is a judgement
     * only a person can make — the agent is the one that knows the URL.</p>
     *
     * <p><b>The P7 line runs between filling in what was empty and redirecting what somebody
     * chose</b>, and only the second is a surprise. So an empty {@code url}, {@code description}
     * or {@code furl} is filled and reported as an {@code update} naming which fields moved; a
     * non-empty one is <b>left exactly as it is</b>, and the change note says so rather than
     * reporting a success that did not happen. A caller that means to repoint a live site is
     * asking for a console edit, and being told that is more useful than a silent no-op.</p>
     *
     * <p>Everything stays idempotent: a second run finds the fields filled and reports
     * {@code noop}, which is what makes an SH82 document re-runnable.</p>
     *
     * <p>It creates a <b>blank</b> site (SH151), not a clone of the SH71 bootstrap template:
     * a package applied over somebody else's Welcome page is not what was asked for. And it
     * <b>refuses a scoped credential</b> — a token with an SH2 site allow-list must not create
     * a site outside it, or the allow-list is a list of sites this token may read plus any it
     * invents (§XII).</p>
     */
    private Change siteUpsert(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.SITE);
        String name = address.site();
        Optional<ShSite> existing = siteAccess.find(name);
        if (existing.isPresent()) {
            return siteFill(op, session, address, existing.get());
        }
        requireUnscopedCredential(name);
        if (session.dryRun) {
            return session.change(op, address, Change.CREATE, "a blank site", null);
        }
        shTenantQuotaService.checkCanCreateSite();
        ShSite payload = new ShSite();
        payload.setName(name);
        payload.setDescription(text(op.data(), "description"));
        payload.setUrl(text(op.data(), "url"));
        payload.setFurl(text(op.data(), "furl"));
        ShSite created = shSiteWriteService.createBlank(payload, session.actor);
        return session.change(op, address, Change.CREATE, "a blank site", created.getId());
    }

    /**
     * The SH184 half, delegated to {@link ShSiteWriteService#fillEmpty} — the rule about what
     * an upsert may overwrite is write semantics, and SH245 is the standing lesson about
     * second copies of a verb. All that happens here is reporting.
     *
     * <p>A dry run asks the same question without writing: it reads the site's current values
     * and reports the same {@code filled}/{@code kept} split, which is what lets a plan tell
     * the caller in advance that their URL will not be applied.</p>
     */
    private Change siteFill(ShAgentBatch.Op op, Session session, ShAddress address, ShSite site) {
        Map<String, String> requested = new LinkedHashMap<>();
        for (String field : ShSiteWriteService.FILLABLE) {
            String value = text(op.data(), field);
            if (value != null && !value.isBlank()) {
                requested.put(field, value);
            }
        }
        ShSiteWriteService.ShSiteFill fill = session.dryRun
                ? shSiteWriteService.planFill(site, requested)
                : shSiteWriteService.fillEmpty(site, requested, session.actor);
        if (!fill.changed()) {
            // Naming what was kept matters: without it, "I asked for a new url and got noop"
            // reads as the op having ignored the request rather than having protected a value
            // somebody chose.
            String detail = fill.kept().isEmpty() ? null
                    : "kept the existing " + String.join(", ", fill.kept())
                            + " — an upsert fills what is empty and never replaces a value a "
                            + "human set; change it in the console";
            return session.change(op, address, Change.NOOP, detail, site.getId());
        }
        String detail = "filled " + String.join(", ", fill.filled())
                + (fill.kept().isEmpty() ? ""
                        : "; kept the existing " + String.join(", ", fill.kept()));
        return session.change(op, address, Change.UPDATE, detail, site.getId());
    }

    /** A single string field of an op's {@code data}, or {@code null}. */
    private static String text(JsonNode data, String field) {
        if (data == null || !data.isObject()) {
            return null;
        }
        JsonNode value = data.get(field);
        return value == null || !value.isString() ? null : value.asString();
    }

    /** Refuses site creation on a credential that is restricted to particular sites (SH2/SH106). */
    private static void requireUnscopedCredential(String name) {
        boolean scoped = ShApiTokenContext.current()
                .map(context -> context.siteIds() != null && !context.siteIds().isEmpty())
                .orElse(false);
        if (scoped) {
            throw new SiteScopeException(name);
        }
    }

    /** A 403: a token restricted to particular sites may not invent another one. */
    public static class SiteScopeException extends ShTeachingException {

        public SiteScopeException(String name) {
            super(ShProblem.of("site-scope", HttpStatus.FORBIDDEN,
                            "this credential may not create a site")
                    .withDetail("The token this request authenticated with has a site allow-list, "
                            + "so it can only act inside the sites it was granted — creating '"
                            + name + "' would put it outside them.")
                    .withFix("Apply into one of the sites this token allows, or have an operator "
                            + "issue a credential without an allow-list."));
        }
    }

    // --- folder ops ---

    /**
     * Ensures a folder path exists, creating every missing level. Idempotent by
     * construction: the second run finds everything and reports {@code noop}.
     */
    private Change folderUpsert(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.FOLDER);
        ShSite site = requireSite(address.site(), session);
        // A site this run would create has no root folder to hang anything off, so every
        // level below it is necessarily missing — the same state the loop already models
        // for a folder an earlier op in this run would have created (SH153).
        ShFolder parent = isPending(site) ? null : requireRootFolder(site);
        List<String> created = new ArrayList<>();
        StringBuilder path = new StringBuilder();
        boolean pending = parent == null;

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
            // Nothing to create: either every level was already there, or the address names
            // the root of a site this run would create — which arrives with the site itself.
            return session.change(op, address, Change.NOOP,
                    parent == null ? "the root of the site this run creates" : null,
                    parent == null ? null : parent.getId());
        }
        String detail = created.size() > 1 ? "creates " + String.join(", ", created) : null;
        return session.change(op, address, Change.CREATE, detail, pending ? null : parent.getId());
    }

    /**
     * Re-addresses an existing folder — a rename, a relocation, or both — keeping its id
     * and everything beneath it (SH137).
     *
     * <h2>Why the vocabulary needed this, one level up from SH134</h2>
     *
     * <p>Without it, renaming a section meant {@code folder.upsert} at the new path plus
     * {@code folder.delete} of the old — and that delete cascades over every post beneath
     * it. It is exactly SH134's defect scaled up: there, the obvious route orphaned one
     * post; here it destroys a subtree. SH134 is also what made the gap conspicuous, since
     * an agent that has learned {@code post.move} will reasonably assume the folder verb
     * exists.</p>
     *
     * <h2>No post's URL changes, which is why this needs no confirm token</h2>
     *
     * <p>A friendly URL is unique per site and independent of the folder chain (SH79), so
     * the pages inside a moved section keep the addresses they had. Nothing is destroyed and
     * nothing goes dark, so unlike {@code folder.delete} this is not in
     * {@link #DESTRUCTIVE_OPS} — the whole point is to be the safe verb that replaces the
     * destructive workaround.</p>
     *
     * <h2>The refusals, all four of them</h2>
     *
     * <p>Cross-site (as {@code post.move}: separate trees, ACLs and tenants), an occupied
     * target, a site root, and a destination inside the folder's own subtree. The last two
     * live on the shared writer because the console can reach them too — and its move
     * dialog, which navigates <em>down</em> from the site root, can reach the cycle in
     * three clicks.</p>
     *
     * <p>Idempotence follows {@code post.move}'s rule: source absent but destination
     * present is a {@code noop}, so a document holding a move survives a second apply.</p>
     */
    private Change folderMove(ShAgentBatch.Op op, Session session) {
        ShAddress from = writeAddress(op, ShAddress.Kind.FOLDER);
        ShSite site = requireSite(from.site(), session);
        ShAddress to = folderMoveTarget(op, from);
        if (!to.site().equalsIgnoreCase(from.site())) {
            throw new CrossSiteMoveException(from, to);
        }

        Optional<ShFolder> source = findFolder(site, from.path());
        if (source.isEmpty()) {
            Optional<ShFolder> already = findFolder(site, to.path());
            if (already.isPresent()) {
                return session.change(op, to, Change.NOOP, "already at this address",
                        already.get().getId());
            }
            throw new ShAddressResolver.UnresolvedAddressException(from, List.of());
        }
        ShFolder folder = source.get();

        List<String> target = segments(to.path());
        if (target.isEmpty()) {
            // `folder:<site>/` is the root, and the root is where every address resolves
            // from — a move onto it is the one destination that cannot mean anything.
            throw new MoveTargetOccupiedException(from, to);
        }
        String newName = target.get(target.size() - 1);
        String parentPath = "/" + String.join("/", target.subList(0, target.size() - 1));

        if (ShAddress.normalizePath(from.path()).equalsIgnoreCase(ShAddress.normalizePath(to.path()))) {
            return session.change(op, to, Change.NOOP, null, folder.getId());
        }
        if (findFolder(site, to.path()).isPresent()) {
            throw new MoveTargetOccupiedException(from, to);
        }
        // The destination's parent must exist: a move never creates, so that a typo in the
        // path is reported instead of quietly building a new branch of the tree.
        ShFolder destParent = findFolder(site, parentPath).orElseThrow(() ->
                new ShAddressResolver.UnresolvedAddressException(
                        ShAddress.folder(site.getName(), parentPath), List.of()));

        if (!session.dryRun) {
            shContentAclService.requireWrite(folder.getId());
            shContentAclService.requireWrite(destParent.getId());
            shFolderWriteService.move(folder.getId(), destParent, newName, session.actor);
        } else {
            // A plan must report the refusals a real run would make, or the plan lied. The
            // writer owns them, so they are asked here rather than duplicated.
            shFolderWriteService.checkMovable(folder.getId(), destParent);
        }
        return session.change(op, to, Change.MOVE,
                "the pages inside keep their URLs", folder.getId());
    }

    /**
     * Where a folder move is going. Accepts a full {@code folder:} address or a bare path,
     * defaulting the site to the source's — repeating the site is how a cross-site move
     * gets typed by accident.
     */
    private ShAddress folderMoveTarget(ShAgentBatch.Op op, ShAddress from) {
        String raw = op.to() == null ? null : op.to().trim();
        if (raw == null || raw.isBlank()) {
            throw new MissingOpFieldException("to", op.op(), from, "folder:Blog/archive/2025",
                    "A folder move needs its new address in `to` — the full name chain, "
                            + "since that is what addresses a folder (SH79).");
        }
        ShAddress target = raw.contains(":") ? ShAddress.parse(raw)
                : ShAddress.folder(from.site(), raw);
        if (target.kind() != ShAddress.Kind.FOLDER) {
            throw new WrongAddressKindException(op.op(), target, ShAddress.Kind.FOLDER);
        }
        return target;
    }

    private Change folderDelete(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.FOLDER);
        ShSite site = requireSite(address.site(), session);
        Optional<ShFolder> folder = findFolder(site, address.path());
        if (folder.isEmpty()) {
            return session.change(op, address, Change.NOOP, "already absent", null);
        }
        String id = folder.get().getId();
        if (!session.dryRun) {
            shContentAclService.requireWrite(id);
            shFolderWriteService.delete(id, session.actor);
        }
        // Folder deletion cascades to every post beneath it (ShFolderWriteService), so
        // the plan says so rather than letting the caller discover it afterwards (P7).
        // Since SH200 the cascade goes through the post writer, so the whole subtree lands
        // in SH55's trash — said out loud for the same reason post.delete says it: an agent
        // that believes it destroyed a section will not offer the human the undo.
        return session.change(op, address, Change.DELETE,
                "with all content beneath it, to trash (restorable)", id);
    }

    // --- post ops ---

    private Change postUpsert(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.POST);
        ShSite site = requireSite(address.site(), session);
        Optional<ShPostUnified> existing = findPost(site, address.path());

        if (existing.isPresent()) {
            ShPostUnified post = existing.get();
            if (op.data() == null || sameData(post, op.data())) {
                return session.change(op, address, Change.NOOP, null, post.getId());
            }
            String undeclared = checkDeclaredFields(op, address, session,
                    TypeRef.of(post.getShPostType(), session.declaredFieldsOf(
                            post.getShPostType().getName())),
                    readTree(post.getData()));
            if (!session.dryRun) {
                shContentAclService.requireWrite(post.getId());
                shPostUnifiedWriteService.update(post.getId(),
                        new UpdateRequest(op.data(), ShPostState.DRAFT, session.actor));
            }
            return session.change(op, address, Change.UPDATE, undeclared, post.getId());
        }
        // Only reachable in a dry run — a real run would have found the row above.
        if (session.alreadyPlanned(address)) {
            return session.change(op, address, Change.NOOP, "created earlier in this batch", null);
        }

        TypeRef postType = requirePostType(op, address, session);
        String undeclared = checkDeclaredFields(op, address, session, postType, null);
        ShFolder folder = requireTargetFolder(op, site, address, session);
        session.planPost(address);
        if (session.dryRun || folder == null) {
            return session.change(op, address, Change.CREATE, undeclared, null);
        }
        shContentAclService.requireWrite(folder.getId());
        shTenantQuotaService.checkCanCreatePost();
        ShPostUnified created = shPostUnifiedWriteService.create(new CreateRequest(
                folder.getId(), postType.id(), op.data(), ShPostState.DRAFT,
                session.actor, null, null));
        // The address *is* the identity here, so the furl is written from it rather
        // than derived from a title — two posts with the same headline must still be
        // two addresses, and the next run must find this one again.
        shPostUnifiedWriteService.setFurl(created.getId(), address.path());
        return session.change(op, address, Change.CREATE, null, created.getId());
    }

    /**
     * Refuses a data key the post-type does not declare (SH168).
     *
     * <h2>Why this is a 400 and not a stored value</h2>
     *
     * <p>{@code post.upsert} declares whole state, so a mistyped field name does not add a
     * key — it <em>replaces</em> the one that was meant with one nothing reads. The write
     * reported {@code changed:1}, the console could not render it, {@code shio types} could not
     * type it, and {@code shio verify} was green: the only defect on record that destroyed
     * content without saying anything. Every other unknown-name mistake on this surface is
     * already a 400, and the alternative here is a write that half-happened.</p>
     *
     * <h2>Why it binds new keys only</h2>
     *
     * <p>A post that <em>already</em> holds undeclared keys must stay readable and re-writable:
     * SH152's post-type collisions produced some, and so did older instances' {@code File}
     * posts. Refusing them would mean an agent could no longer fix such a post at all. So the
     * keys already stored are grandfathered, and the {@code unknown-field} lint rule is what
     * reports them — a defect that is visible and fixable beats one that is unwritable.</p>
     *
     * @param declaredIn the post-type to check against
     * @param stored     the post's current data, or {@code null} when it is being created
     */
    private String checkDeclaredFields(ShAgentBatch.Op op, ShAddress address, Session session,
                                       TypeRef declaredIn, JsonNode stored) {
        JsonNode data = op.data();
        if (data == null || !data.isObject() || declaredIn == null) {
            return null;
        }
        Set<String> allowed = declaredIn.fields();
        if (allowed.isEmpty()) {
            // A post-type with no fields declares nothing to check against, and refusing every
            // key would make such a type unusable rather than making the model better.
            return null;
        }
        List<String> unknown = new ArrayList<>();
        for (String key : data.propertyNames()) {
            boolean grandfathered = stored != null && stored.has(key);
            if (!allowed.contains(key) && !grandfathered) {
                unknown.add(key);
            }
        }
        if (unknown.isEmpty()) {
            return null;
        }
        if (!session.dryRun) {
            throw new UndeclaredFieldException(address, declaredIn.name(), unknown,
                    List.copyOf(allowed));
        }
        // On a plan it is a *finding*, not a stop. A plan that died on the first bad field
        // would hide the rest of the report — and the report is the reason to plan: a blueprint
        // whose post-type already exists with fewer fields (SH152) needs to say both which
        // fields are missing and which writes that will break, in one answer.
        return "undeclared field(s) " + String.join(", ", unknown)
                + " — a real run will refuse this write";
    }

    /** Every field name a post-type declares, nested ones included (Tab groups, Relator children). */
    private static Set<String> declaredFieldNamesOf(ShPostType postType) {
        Set<String> names = new LinkedHashSet<>();
        collectFieldNames(postType.getShPostTypeAttrs(), names);
        return names;
    }

    private static void collectFieldNames(Set<ShPostTypeAttr> attrs, Set<String> into) {
        if (attrs == null) {
            return;
        }
        for (ShPostTypeAttr attr : attrs) {
            if (attr.getName() != null && !attr.getName().isBlank()) {
                into.add(attr.getName());
            }
            collectFieldNames(attr.getShPostTypeAttrs(), into);
        }
    }

    /** A 400 naming the fields this post-type has, and the nearest one to what was sent. */
    public static class UndeclaredFieldException extends ShTeachingException {

        public UndeclaredFieldException(ShAddress address, String postType, List<String> unknown,
                                        List<String> allowed) {
            super(ShProblem.badRequest("undeclared-field",
                            "'" + postType + "' does not declare " + join(unknown))
                    .withDetail("A post.upsert declares whole state, so writing a field the "
                            + "post-type has no place for would replace the value it was meant "
                            + "for with one nothing can read. Address: " + address + ".")
                    .withFix("Use one of the declared fields below, or add the field to the "
                            + "post-type first (PUT /api/v2/post-type/" + postType + ").")
                    .withAllowed(allowed)
                    // The nearest declared name, which for a typo is the field that was meant.
                    // Reuses the SH80 matcher rather than a second notion of "close".
                    .withDidYouMeanFrom(unknown.get(0)));
        }

        private static String join(List<String> names) {
            return names.size() == 1 ? "a field named '" + names.get(0) + "'"
                    : "fields named " + String.join(", ", names);
        }
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
        ShSite site = requireSite(from.site(), session);
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
        ShSite site = requireSite(address.site(), session);
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
        ShSite site = requireSite(address.site(), session);
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
        ShSite site = requireSite(address.site(), session);
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

    /**
     * The site an op names — or, on a dry run that declared it, a stand-in for the one this
     * run would create (SH153).
     *
     * <p>The stand-in carries the name and no id, which is what {@link #isPending} keys on:
     * a site with no id has no root folder, no folders and no posts, so every lookup beneath
     * it is empty and every op against it plans as a create. That is exactly true of a site
     * that does not exist yet, and it is reached without writing one.</p>
     */
    private ShSite requireSite(String nameOrId, Session session) {
        Optional<ShSite> live = siteAccess.find(nameOrId);
        if (live.isPresent()) {
            return live.get();
        }
        if (session.declaresSite(nameOrId)) {
            ShSite pending = new ShSite();
            pending.setName(nameOrId.trim());
            // A site arrives with its root folder, so `/` is planned the moment the site is —
            // otherwise a package whose posts sit at the root and which declares no folders of
            // its own would plan as an unresolvable folder address.
            session.planFolder(pending, "/");
            return pending;
        }
        return siteAccess.require(nameOrId);
    }

    /** True for the SH153 stand-in: a site this run would create, which nothing lives in yet. */
    private static boolean isPending(ShSite site) {
        return site != null && site.getId() == null;
    }

    ShFolder requireRootFolder(ShSite site) {
        return siteAccess.rootFolder(site)
                .orElseThrow(() -> new UnresolvableParentException(ShAddress.site(site.getName())));
    }

    /** A folder by its path of names under a site's root. */
    Optional<ShFolder> findFolder(ShSite site, String path) {
        if (isPending(site)) {
            return Optional.empty();
        }
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
        if (isPending(site)) {
            return Optional.empty();
        }
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

    /**
     * A post-type as this op needs to see it: its name, the fields a write may use, and its
     * id — {@code null} for one the run has only <em>declared</em> (SH153).
     *
     * <p>A null id is unreachable on a real write: {@code postUpsert} only reads it after the
     * dry-run branch has returned, and {@link Session} discards the declarations outside a
     * dry run. So the type that stands in for a plan cannot become the type a create is
     * written against.</p>
     */
    private record TypeRef(String name, Set<String> fields, String id) {

        private static TypeRef of(ShPostType live, Set<String> alsoDeclared) {
            Set<String> names = new LinkedHashSet<>(declaredFieldNamesOf(live));
            names.addAll(alsoDeclared);
            return new TypeRef(live.getName(), names, live.getId());
        }
    }

    private TypeRef requirePostType(ShAgentBatch.Op op, ShAddress address, Session session) {
        if (op.type() == null || op.type().isBlank()) {
            throw new MissingOpFieldException("type", op.op(), address,
                    "Article", "A new post needs a post-type; an existing one keeps its own.");
        }
        String name = op.type().trim();
        ShPostType postType = shPostTypeRepository.findByName(name);
        if (postType != null) {
            // Additive, never a substitute: a live type is resolved live, and a declaration
            // only widens what a write may say (SH152's merge is exactly that widening).
            return TypeRef.of(postType, session.declaredFieldsOf(name));
        }
        if (session.declaresPostType(name)) {
            return new TypeRef(name, session.declaredFieldsOf(name), null);
        }
        List<String> allowed = new ArrayList<>(shPostTypeRepository.findAll().stream()
                .map(ShPostType::getName)
                .filter(existing -> existing != null && !existing.isBlank())
                .sorted()
                .toList());
        throw new UnknownPostTypeException(op.type(), allowed);
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
        private final Declared declared;
        private final Set<String> plannedFolders = new HashSet<>();
        private final Set<String> plannedPosts = new HashSet<>();

        private Session(boolean dryRun, boolean ids, String actor, Declared declared) {
            this.dryRun = dryRun;
            this.ids = ids;
            this.actor = actor;
            // Honoured on a dry run only (SH153): on a real run every op resolves live, so a
            // declaration can never stand in for a write that did not happen.
            this.declared = dryRun && declared != null ? declared : Declared.NOTHING;
        }

        /** True when this run would create the named site and the database does not have it. */
        private boolean declaresSite(String nameOrId) {
            return declared.declaresSite(nameOrId);
        }

        private boolean declaresPostType(String name) {
            return declared.declaresPostType(name);
        }

        /** The field names this run would add to {@code postType} — unioned with the live ones. */
        private Set<String> declaredFieldsOf(String postType) {
            return declared.fieldsOf(postType);
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

    /**
     * A 400: a desired-state document compiled to more ops than one run may hold (SH138).
     *
     * <p>Separate from {@link BatchTooLargeException} because the advice is different and the
     * old advice was unusable. A document's op count is not what the caller typed — a
     * three-post document compiles to four hundred ops when {@code prune} finds four hundred
     * undeclared posts — so "split the work into batches" names an action that does not
     * exist for this surface. What the caller can actually do is narrow the scope the prune
     * sweeps, and the count of deletions is what tells them that is the problem.</p>
     *
     * <p>It refuses rather than shortening the plan, which is the whole point: a plan that
     * quietly listed the first 2000 of 5000 deletions would read as a complete review of
     * what is about to be destroyed.</p>
     */
    public static class DocumentTooLargeException extends ShTeachingException {

        public DocumentTooLargeException(int sent, int deletes, int max) {
            super(ShProblem.badRequest("document-too-large",
                            "This document compiles to " + sent + " ops, over the limit of " + max)
                    .withDetail(deletes > 0
                            ? deletes + " of them are deletions, so `prune` is sweeping up more "
                                    + "than this run can hold. The plan is not shortened to fit: a "
                                    + "partial list of deletions reads as the whole list."
                            : "One run is one transaction, so it is bounded even when the caller "
                                    + "did not write the ops itself.")
                    .withFix(deletes > 0
                            ? "Declare fewer folders per document so each run prunes a smaller "
                                    + "subtree, and apply them one at a time — or raise "
                                    + "`shio.agent.batch.max-document-ops` if a run this large is "
                                    + "really intended."
                            : "Split the document by folder and apply each part, or raise "
                                    + "`shio.agent.batch.max-document-ops`.")
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

    /**
     * A 409: something already lives at the address a move is aiming for.
     *
     * <p>Worded per kind (SH137). Both moves share the refusal, and they do not share a
     * consequence: two posts on one URL is a delivery ambiguity, while two folders of one
     * name is an address that resolves to whichever the walk reaches first. An error that
     * described the wrong one would be teaching the caller something false (P6).</p>
     */
    public static class MoveTargetOccupiedException extends ShTeachingException {

        public MoveTargetOccupiedException(ShAddress from, ShAddress to) {
            super(to.kind() == ShAddress.Kind.FOLDER
                    ? ShProblem.of("move-target-occupied", HttpStatus.CONFLICT,
                                    "'" + to + "' is already taken")
                            .withDetail("Moving '" + from + "' there would leave two folders "
                                    + "answering to one name chain, and an address resolves to "
                                    + "whichever the walk reaches first. A move never overwrites.")
                            .withFix("Pick a free name chain, or move the folder at '" + to
                                    + "' out of the way first — folder.delete would take "
                                    + "everything inside it.")
                            .withExample(to.toString())
                    : ShProblem.of("move-target-occupied", HttpStatus.CONFLICT,
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
                            to.kind() == ShAddress.Kind.FOLDER
                                    ? "A move cannot change a folder's site"
                                    : "A move cannot change a post's site")
                    .withDetail("'" + from + "' is in a different site from '" + to + "'. "
                            + "Sites have separate folder trees, separate ACLs and — under "
                            + "multi-tenancy — separate tenants, so re-parenting across one "
                            + "silently is not something this op will do.")
                    .withFix(to.kind() == ShAddress.Kind.FOLDER
                            ? "Recreate the structure in the destination site with "
                                    + "folder.upsert and move the posts across one at a time, "
                                    + "then delete the original — visible steps instead of one "
                                    + "invisible one."
                            : "post.upsert the content into the destination site, then "
                                    + "post.delete the original — two visible steps instead of one "
                                    + "invisible one.")
                    .withExample(to.kind() == ShAddress.Kind.FOLDER
                            ? "folder:" + from.site() + "/archive/2025"
                            : "post:" + from.site() + "/blog/new-url"));
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
