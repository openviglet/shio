/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.api.post.revision.ShPostRevisionService;
import com.viglet.shio.api.gate.ShPublishGateService;
import com.viglet.shio.api.post.ShPostDrift;
import com.viglet.shio.api.release.ShReleaseRunner;
import com.viglet.shio.api.release.ShReleaseService;
import com.viglet.shio.persistence.model.release.ShRelease;
import com.viglet.shio.api.post.revision.ShRevisionRow;
import com.viglet.shio.api.post.unified.ShPostStateNotFoundException;
import com.viglet.shio.api.site.ShSiteLocaleService;
import com.viglet.shio.api.site.ShSiteLocales;
import com.viglet.shio.api.site.ShSiteWriteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.viglet.shio.turing.ShTuringIndexService;
import com.viglet.shio.turing.ShTuringTextExtractor;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.shio.api.post.unified.ShHtmlSanitizer;
import com.viglet.shio.widget.ShSystemWidget;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.dto.ShAgentResolved;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet.Change;
import com.viglet.shio.api.agent.verify.ShPostLinks;
import com.viglet.shio.api.agent.verify.ShPostReferenceFields;
import com.viglet.shio.api.error.ShDataIntegrityProblem;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.post.block.ShBlocks;
import com.viglet.shio.render.ShTemplateTitles;
import com.viglet.shio.api.post.type.ShPostTypeAdminService;
import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.post.type.ShVocabularyPostTypes;
import com.viglet.shio.render.ShAnnotatedFields;
import com.viglet.shio.render.ShRenderPostTypes;
import com.viglet.shio.render.ShTemplateValidator;
import com.viglet.shio.render.ShTemplateValidator.ShTemplateProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.UpdateRequest;
import com.viglet.shio.api.folder.ShFolderWriteService;
import com.viglet.shio.api.object.ShObjectWriteService;
import com.viglet.shio.object.ShFolderSite;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShPostFieldFlags;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;
import com.viglet.shio.tenant.ShTenantQuotaService;
import com.viglet.shio.url.ShFurlSpellings;
import com.viglet.shio.utils.ShStableMap;
import com.viglet.shio.tenant.ShTenantContext;

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
    /**
     * SH1277 — who may edit a folder and what is below it, replaced whole: `data.acl` is a list of
     * `{group|user|role, permission}`. Governs the console and the agent path, never a delivered
     * page's visitors (content ACL does not gate an anonymous read).
     */
    public static final String FOLDER_ACL = "folder.acl";
    public static final String ACL_KEY = "acl";
    /**
     * SH1285 — who may READ a section's delivered pages: `data.visibility` is `public` or `members`,
     * and `data.groups` the groups a members-only section is for (none: any signed-in user). The
     * visitor's half that {@link #FOLDER_ACL} deliberately is not, kept a separate op because the
     * owner kept it a separate rule.
     */
    public static final String FOLDER_VISIBILITY = "folder.visibility";
    /** SH1300 — the same gate on one post: `data` as {@link #FOLDER_VISIBILITY}. */
    public static final String POST_VISIBILITY = "post.visibility";
    public static final String VISIBILITY_KEY = "visibility";
    public static final String GROUPS_KEY = "groups";

    /**
     * SH244 — duplicating a section, which was the one content verb only the console could do.
     *
     * <h2>Why the folder and not the post</h2>
     *
     * <p>Copy lived at {@code PUT /api/v2/object/copyto/{dest}} and nowhere else. For a
     * <b>post</b> that is close to harmless and worth saying rather than overstating: an agent
     * duplicates a page by reading it and upserting the fields at a new address, and a copy of a
     * post <em>is</em> its field values somewhere else. The gap is the <b>folder</b> — a folder
     * copy buys the whole subtree in one call, and reproducing it over this surface means walking
     * the tree and emitting an upsert per post, which is the discovery-and-write loop Block H
     * exists to remove, in the one case where the server can do it in one traversal.</p>
     *
     * <p>So one op and not two. The vocabulary is paid by every caller that reads it (P3), and
     * {@code post.copy} would be a name on {@code tools/list} forever for a job two ops already
     * do losslessly.</p>
     *
     * <h2>Not destructive, and that is a decision rather than an omission</h2>
     *
     * <p>It creates and never overwrites — an occupied target is refused — so it is not in
     * {@link #DESTRUCTIVE_OPS} and needs no SH108 confirm, for {@code folder.move}'s reason:
     * making the safe verb demand a token is how callers get pushed back to the unsafe pair.</p>
     *
     * <p>Every copied post lands as a <b>draft</b>, and §XVI.4 said so for a reason that turned out
     * not to be true: {@code copyPost} does set {@code published(false)}, but it also reproduced
     * each state row <em>as it found it</em>, so a copy of a live page came out {@code PUBLISHED}
     * with the flag off. The first assertion written here is what said so. It matters more on this
     * surface than on the console's, which is why the writer takes a flag rather than changing:
     * a credential without SH107's {@code mayPublish} could otherwise put a whole section live by
     * copying it, and "the agent path is never a privilege escalation" (SH201) is exactly that
     * case.</p>
     */
    public static final String FOLDER_COPY = "folder.copy";
    public static final String FOLDER_DELETE = "folder.delete";
    public static final String POST_UPSERT = "post.upsert";
    public static final String POST_MOVE = "post.move";

    /**
     * SH1064 — the redirect a rename needs, written by the caller that renamed.
     *
     * <h2>Why a name and not a flag on {@code post.move}</h2>
     *
     * <p>A batch is all-or-nothing and applied in order, so {@code post.move} followed by
     * {@code post.redirect} at the vacated address <em>is</em> "in the same transaction" — the thing
     * the design asked a flag for. A flag would additionally have been a second way to write a
     * {@code Redirect} post, which {@code post.upsert type=Redirect} already does; this name is the
     * one spelling of the whole gesture, and the op the move's own report now points at.</p>
     *
     * <p>Measured before it was added, because the vocabulary is re-sent every turn (P3): an op name
     * costs ~3 est. tokens inside {@code shio_write}'s enum, which is SH977's cheap rung — the tool
     * measured 493 of a 520 ceiling and the list 2964 of 2975, so this spends 3 of the eleven SH1060
     * deliberately left for a task like this one rather than opening a ceiling conversation.</p>
     *
     * <h2>It publishes, and that is the one place it departs from draft-default</h2>
     *
     * <p>Every other write here lands in {@code DRAFT} (P7). A draft redirect routes nobody: the
     * renderer serves published rows, so the op would report success and the old links would go on
     * 404ing — SH767's failure shape, where the content looks authored and the symptom is somebody
     * opening the page. So the row goes live, and the change says so rather than leaving it to be
     * discovered.</p>
     */
    public static final String POST_REDIRECT = "post.redirect";
    public static final String POST_PUBLISH = "post.publish";
    public static final String POST_UNPUBLISH = "post.unpublish";
    public static final String POST_DELETE = "post.delete";

    /**
     * SH222 — the undo the protocol already promised.
     *
     * <p>{@code post.delete} answers {@code "to trash (restorable)"} deliberately: an agent that
     * believes it destroyed something will not offer the human the undo. That string was the
     * <b>only</b> place the trash appeared on this surface — no read listed it, no tool reached it,
     * no CLI command restored it — so the protocol told a caller an undo existed and gave it no way
     * to get there. That inverts P1 on the capability an agent needs most immediately after a
     * mistake, and it is where "the console can do it" is least comforting: the mistake is usually
     * noticed by the agent, seconds later.</p>
     *
     * <p><b>A verb on the existing vocabulary, not a new tool.</b> `shio_write` already carries the
     * addressed write ops, so a restore costs no MCP schema — and the tool budget is paid on every
     * turn of every session (SH132), while a restore happens once in a hundred.</p>
     *
     * <p><b>Addressed by {@code id:}</b>, which is what SH172's fallback is for: a trashed post has
     * no reachable path, so there is nothing else to name it with.</p>
     */
    public static final String POST_RESTORE = "post.restore";

    /** SH222 — the folder half; restores the subtree that went into the trash with it. */
    public static final String FOLDER_RESTORE = "folder.restore";

    /**
     * SH987 — put a captured revision back, as a verb on the vocabulary rather than a tool.
     *
     * <p><b>An op, not a tool</b>, for the reason {@code post.restore} is one: the tool budget is
     * paid on every turn of every session (SH132) and a rollback happens once in a hundred. Being
     * an op is also what gives it the plan, the dry run and the confirm token for free, which a new
     * tool would have had to reinvent — and reinvent slightly differently.</p>
     *
     * <p><b>It restores into {@code DRAFT} and never publishes.</b> P7 as mechanics rather than
     * convention: the caller asked to go back to a version, and putting that version in front of
     * visitors is the separate explicit call the whole draft-default split exists to keep separate.
     * It is also <b>not destructive</b> — it writes a new revision and deletes none, so what it
     * replaced is still there to come forward to, which is why it is not in
     * {@link #DESTRUCTIVE_OPS}.</p>
     */
    public static final String POST_ROLLBACK = "post.rollback";

    /**
     * Creates a site (SH170) — the step the whole product is for, and the only op that took an
     * agent off this surface entirely.
     */
    public static final String SITE_UPSERT = "site.upsert";
    public static final String SITE_DELETE = "site.delete";

    /**
     * SH648 — the site half of the trash, which is the level the undo stopped one short of.
     *
     * <p>A thirteenth name rather than a key on {@code site.upsert}: SH413's rule is that a
     * <em>modifier</em> of an existing op rides it, and a restore is not a way of declaring a
     * site's state — it is {@code post.restore} and {@code folder.restore} one level up, and
     * naming it anything else would make the one vocabulary that is already learned inconsistent
     * at exactly the point a caller reaches for it.</p>
     *
     * <p>The purge, by contrast, <b>does</b> ride {@code site.delete} as {@code data.purge:true}:
     * it is the same gesture with the undo declined, which is precisely the modifier shape.</p>
     */
    public static final String SITE_RESTORE = "site.restore";

    /** SH648 — {@code site.delete} with the trash declined; irreversible, and says so in its plan. */
    public static final String PURGE_KEY = "purge";

    /**
     * SH186 — provisions the three render post-types, which was the last step only REST could take.
     *
     * <p>An MCP-only client has no HTTP of its own, and {@code PUT /api/v2/post-type/{name}} refuses a
     * system type — which is precisely why {@code POST /api/v2/render/post-types} exists. So an agent
     * driving this instance over JSON-RPC could write a layout's HTML and never create the type to
     * write it as: the manifest indexed the endpoint, the client could read that it existed, and
     * calling it was somebody else's job. That is the one gap between "an agent builds the site" and
     * "a human can look at it".</p>
     *
     * <p><b>An op and not a thirteenth tool.</b> {@code tools/list} is paid on every turn of every
     * session (SH132), and this is a once-per-instance step — the same trade SH193 and SH262 made by
     * riding an existing tool. It reaches MCP through {@code shio_write} / {@code shio_apply} for free,
     * and the REST endpoint stays exactly as it was for the console.</p>
     */
    public static final String RENDER_PROVISION = "render.provision";

    /**
     * SH993 — plans a release, or confirms it: {@code release:<site>/<name>}, a named set of posts in
     * one site that goes live as one unit.
     *
     * <p>{@code .upsert} rather than the {@code create} the design named, because every other
     * declare-or-confirm op here is one and a projected release file is re-pushed unchanged: a create
     * that refused an existing release would make that re-push fail. Ops and not a tool, for the
     * ladder's reason — about three tokens each in {@code shio_write}'s enum, where a tool is paid in
     * full on every turn — and because an op gets the plan and the dry run for nothing.</p>
     */
    public static final String RELEASE_UPSERT = "release.upsert";
    /** SH993 — puts the posts {@code data.posts} names into a release that has not run. */
    public static final String RELEASE_ADD = "release.add";
    /** SH993 — takes them out again. */
    public static final String RELEASE_REMOVE = "release.remove";

    /**
     * SH993 — the release goes live: every post in it publishes in one transaction, now or at
     * {@code when}, and {@code cancel} calls a scheduled run off.
     *
     * <p>The widest gesture on the surface, so it takes SH108's confirm token from its own dry run —
     * not because it destroys anything, but because what it puts in front of visitors is a set, and a
     * caller has to have seen the set. Scheduling one confirms nothing: that call moves a date.</p>
     */
    public static final String RELEASE_RUN = "release.run";

    /**
     * SH993 — takes a release out of the listing, or with {@code cancel} puts it back.
     *
     * <p>Not a delete: what a release holds is the record of what went live together, and the thing
     * archiving removes is its place in the pack a reader scrolls, not the record.</p>
     */
    public static final String RELEASE_ARCHIVE = "release.archive";

    /**
     * SH994 — puts a release back, as the unit it went live as.
     *
     * <p>The reverse {@code release.run} promised and did not have: BA4 recorded an anchor per item
     * and declared a ROLLED_BACK state, and nothing reached either — a column written and never read
     * is the shape that rots, because the next reader cannot tell whether it is dead or load-bearing.
     * It takes the same confirm token {@code release.run} does, for the same reason and with the same
     * blast radius, and the same {@code mayPublish} grant: it changes what visitors see.</p>
     */
    public static final String RELEASE_ROLLBACK = "release.rollback";
    /** SH993 — the list of posts {@code release.add} and {@code release.remove} act on. */
    public static final String RELEASE_POSTS_KEY = "posts";

    /**
     * SH1003 — {@code note.add}: say something about a page, where it is about.
     *
     * <p>An op rather than an endpoint of its own, and it is the ladder SH977 describes working:
     * anything expressible as an addressed write costs a name in this enum and no schema. A note IS
     * an addressed write — it hangs on an object and carries a body — so it belongs here rather than
     * in a thirteenth tool.</p>
     */
    public static final String NOTE_ADD = "note.add";

    /** SH1003 — {@code note.resolve}: mark one done, or {@code cancel} to re-open it. */
    public static final String NOTE_RESOLVE = "note.resolve";

    /** The keys {@code note.add} reads from {@code data}. */
    public static final String NOTE_BODY_KEY = "body";
    public static final String NOTE_KIND_KEY = "kind";
    public static final String NOTE_FIELD_KEY = "field";
    public static final String NOTE_REVISION_KEY = "revision";
    public static final String NOTE_PARENT_KEY = "parent";
    public static final String NOTE_ID_KEY = "note";

    /**
     * SH1032 — {@code asset.meta}: what an image says about itself.
     *
     * <h2>Why not {@code post.upsert}</h2>
     *
     * <p>A {@code File} post is a post and its metadata is in {@code data}, so the obvious answer
     * is that this already works. It does not, and the reason is the contract {@code post.upsert}
     * keeps: it declares <b>whole state</b>. An upsert carrying only {@code ALT} would drop
     * {@code FILE} — the name of the bytes — and the asset would still be listed, still be
     * addressable, and point at nothing. A merge flag on {@code post.upsert} would be worse: the
     * whole-state promise is what makes a desired-state document mean anything.</p>
     *
     * <p>So a narrow op that merges three keys and leaves every stamp alone, through
     * {@link com.viglet.shio.api.staticfile.ShStaticFileService#updateMeta} — the one writer of a
     * {@code File} post's metadata, which is what keeps the console and the agent writing the same
     * way and what the one-writer lint asserts.</p>
     */
    public static final String ASSET_META = "asset.meta";

    /** The keys {@code asset.meta} merges. Everything else on the row is a machine's stamp. */
    public static final String ASSET_ALT_KEY = "alt";
    public static final String ASSET_CAPTION_KEY = "caption";
    public static final String ASSET_TAGS_KEY = "tags";

    /**
     * SH413 — reindexing rides {@code site.upsert} rather than being a twelfth op name.
     *
     * <p>It was written as {@code search.reindex} first, and {@code ShAgentOpsReachTest} refused
     * it: the op vocabulary is priced at 600 estimated tokens and a twelfth name took it to 659.
     * That assertion says what to do about it in as many words — "a reason to <b>re-open the
     * surface choice</b>, not to raise the number quietly" — and SH326 had already made the same
     * trade for the same reason, folding a repair verb into {@code render.provision} as
     * {@code data.reconcile} because a vocabulary is paid by every caller that reads it (P3).</p>
     *
     * <p>So {@code data.reindex:true} on {@code site.upsert}: "make this site as declared, and make
     * its search correct", asked of a new site and of an old one — which is the shape SH326 named
     * and costs one documented key instead of a name every {@code tools/list} carries forever.</p>
     */
    public static final String REINDEX_KEY = "reindex";

    /** The closed op vocabulary, in the order a teaching error lists it. */
    public static final List<String> OPS = List.of(
            SITE_UPSERT, SITE_DELETE, FOLDER_UPSERT, FOLDER_MOVE, FOLDER_COPY, FOLDER_DELETE,
            POST_UPSERT, POST_MOVE, POST_REDIRECT, POST_PUBLISH, POST_UNPUBLISH, POST_DELETE,
            POST_RESTORE, POST_ROLLBACK, FOLDER_RESTORE, SITE_RESTORE, RENDER_PROVISION,
            RELEASE_UPSERT,
            RELEASE_ADD, RELEASE_REMOVE, RELEASE_RUN, RELEASE_ARCHIVE, RELEASE_ROLLBACK,
            NOTE_ADD, NOTE_RESOLVE, ASSET_META, FOLDER_ACL, FOLDER_VISIBILITY, POST_VISIBILITY);

    /**
     * The ops that destroy something and therefore need confirming (SH108). Everything else
     * on this surface is recoverable by another write; these three are not.
     *
     * <p>{@code site.delete} (SH463) is the widest of them: it takes a whole site out at once, and
     * until SH648 it was also the only one with no trash behind it. It has one now — the plan line
     * says which of the two it is about to be, because {@code data.purge:true} declines it.</p>
     */
    public static final List<String> DESTRUCTIVE_OPS =
            List.of(SITE_DELETE, FOLDER_DELETE, POST_DELETE);

    private static final List<ShPostState> BOTH_STATES =
            List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    /**
     * How many broken inbound links a move lists (SH140). Ten is enough to act on and small
     * enough that the report of a one-post move cannot outgrow the move; the total is always
     * stated, so the cap is visible rather than silent (SH138).
     */
    private static final int INBOUND_LINK_CAP = 10;

    private final ShAgentSiteAccess siteAccess;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShFolderWriteService shFolderWriteService;
    private final ShPostUnifiedWriteService shPostUnifiedWriteService;
    private final ShSiteWriteService shSiteWriteService;
    /** SH1056 — the one door a locale policy is validated through, on both upsert branches. */
    private final ShSiteLocaleService shSiteLocaleService;
    /** SH244 — the one copy implementation, which the console's `/copyto` also calls (SH245). */
    private final ShObjectWriteService shObjectWriteService;
    private final ShContentAclService shContentAclService;
    private final ShTenantQuotaService shTenantQuotaService;
    /** SH734 — what one credential may write across a window; off unless an operator sets it. */
    private final ShAgentSessionBudget shAgentSessionBudget;
    private final ObjectMapper objectMapper;
    private final ShAgentProperties properties;
    private final ShTemplateValidator shTemplateValidator;
    private final ShTemplateTitles shTemplateTitles;
    private final ShRenderPostTypes shRenderPostTypes;
    /** SH607 — the SH95 vocabulary, so `render.provision` can install what a conversion writes. */
    private final ShVocabularyPostTypes shVocabularyPostTypes;
    /** SH871 — the additive merge `data.fields` rides, which is SH152's and not a second one. */
    private final ShPostTypeAdminService shPostTypeAdminService;
    private final ShPostLinks shPostLinks;
    /** SH413 — the same service SH84's listener indexes through, so there is one mapping. */
    private final ShTuringIndexService shTuringIndexService;
    private final ShTuringTextExtractor shTuringTextExtractor;

    /** SH956 — what a write would strip, so a plan says it before the write happens. */
    private final ShHtmlSanitizer shHtmlSanitizer;
    private final ShConsoleSurfaces shConsoleSurfaces;
    /** SH987 — the page's history, for post.rollback's plan and its refusals. */
    private final ShPostRevisionService shPostRevisionService;
    /** SH993 — what a release is, answered in one place for the ops and the listing. */
    private final ShReleaseService shReleaseService;
    /** SH993 — running a release, shared with the sweep that fires a scheduled one. */
    private final ShReleaseRunner shReleaseRunner;
    /** SH997 — what the site says about content before it reaches visitors. */
    private final ShPublishGateService shPublishGateService;
    private final com.viglet.shio.api.note.ShNoteService shNoteService;
    /** SH1032 — the one writer of a File post's metadata, console and agent alike. */
    private final com.viglet.shio.api.staticfile.ShStaticFileService shStaticFileService;
    /** SH1003 — a note hangs on the object an address names, resolved the one way this product does. */
    private final ShAddressResolver shAddressResolver;

    public ShAgentBatchService(ShHtmlSanitizer shHtmlSanitizer,
                               ShAgentSiteAccess siteAccess,
                               ShFolderRepository shFolderRepository,
                               ShPostUnifiedRepository shPostUnifiedRepository,
                               ShPostTypeRepository shPostTypeRepository,
                               ShFolderWriteService shFolderWriteService,
                               ShPostUnifiedWriteService shPostUnifiedWriteService,
                               ShSiteWriteService shSiteWriteService,
                               ShSiteLocaleService shSiteLocaleService,
                               ShObjectWriteService shObjectWriteService,
                               ShContentAclService shContentAclService,
                               ShTenantQuotaService shTenantQuotaService,
                               ShAgentSessionBudget shAgentSessionBudget,
                               ObjectMapper objectMapper,
                               ShAgentProperties properties,
                               ShTemplateValidator shTemplateValidator,
                               ShTemplateTitles shTemplateTitles,
                               ShRenderPostTypes shRenderPostTypes,
                               ShVocabularyPostTypes shVocabularyPostTypes,
                               ShPostTypeAdminService shPostTypeAdminService,
                               ShPostLinks shPostLinks,
                               ShTuringIndexService shTuringIndexService,
                               ShTuringTextExtractor shTuringTextExtractor,
                               ShConsoleSurfaces shConsoleSurfaces,
                               ShPostRevisionService shPostRevisionService,
                               ShReleaseService shReleaseService,
                               ShReleaseRunner shReleaseRunner,
                               ShPublishGateService shPublishGateService,
                               com.viglet.shio.api.note.ShNoteService shNoteService,
                               com.viglet.shio.api.staticfile.ShStaticFileService shStaticFileService,
                               ShAddressResolver shAddressResolver) {
        this.shHtmlSanitizer = shHtmlSanitizer;
        this.shReleaseService = shReleaseService;
        this.shReleaseRunner = shReleaseRunner;
        this.shPublishGateService = shPublishGateService;
        this.shNoteService = shNoteService;
        this.shStaticFileService = shStaticFileService;
        this.shAddressResolver = shAddressResolver;
        this.shConsoleSurfaces = shConsoleSurfaces;
        this.shPostRevisionService = shPostRevisionService;
        this.siteAccess = siteAccess;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shFolderWriteService = shFolderWriteService;
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.shSiteWriteService = shSiteWriteService;
        this.shSiteLocaleService = shSiteLocaleService;
        this.shObjectWriteService = shObjectWriteService;
        this.shContentAclService = shContentAclService;
        this.shTenantQuotaService = shTenantQuotaService;
        this.shAgentSessionBudget = shAgentSessionBudget;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.shTemplateValidator = shTemplateValidator;
        this.shTemplateTitles = shTemplateTitles;
        this.shRenderPostTypes = shRenderPostTypes;
        this.shVocabularyPostTypes = shVocabularyPostTypes;
        this.shPostTypeAdminService = shPostTypeAdminService;
        this.shPostLinks = shPostLinks;
        this.shTuringIndexService = shTuringIndexService;
        this.shTuringTextExtractor = shTuringTextExtractor;
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
     * <h2>A batch declares its own sites, without being asked (SH329)</h2>
     *
     * <p>The declaration above is something a <em>caller</em> supplies, which was right for a
     * blueprint and wrong for the case underneath it: {@code {site.upsert Demo},
     * {folder.upsert Demo/pages}} with {@code dryRun} failed at {@code ops[1]}, because a plain
     * batch declared nothing and there was no caller to ask. That is the bootstrap case — the
     * first thing any agent does on a new instance — so "look before you write" was unavailable
     * exactly where nothing else could stand in for it. {@link #withSitesFrom} reads the sites
     * out of the run's own ops, so the plan describes its own first step.</p>
     *
     * @param sites     the sites this run would create; empty for the ordinary case
     * @param postTypes post-type name → the field names it would declare
     */
    public record Declared(Set<String> sites, Map<String, Set<String>> postTypes) {

        /** The ordinary case: everything an op names must already be there. */
        public static final Declared NOTHING = new Declared(Set.of(), Map.of());

        public Declared {
            // Insertion-ordered rather than Set.copyOf: P5, and a hash set's order comes from a
            // per-JVM salt. Membership is all this is read for today, and "today" is the part
            // that changes.
            sites = sites == null ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(sites));
            postTypes = postTypes == null ? Map.of() : ShStableMap.copyOf(postTypes);
        }

        /** One site and the types beside it — the blueprint shape (SH153). */
        public static Declared of(String site, Map<String, Set<String>> postTypes) {
            return new Declared(site == null ? Set.of() : Set.of(site), postTypes);
        }

        /**
         * This declaration, plus every site the run would create by its own {@code site.upsert}.
         *
         * <p>Additive for the same reason the record is (a declaration never makes a plan greener
         * than the run it predicts): these sites <em>are</em> what the run creates, read off the
         * ops rather than promised by a caller. A malformed address is left alone — the op that
         * owns it raises the teaching error when it runs, and a second one raised from here would
         * report the same defect twice with less context.</p>
         */
        Declared withSitesFrom(List<ShAgentBatch.Op> ops) {
            Set<String> union = new LinkedHashSet<>(sites);
            for (ShAgentBatch.Op op : ops) {
                if (!SITE_UPSERT.equals(op.op()) || op.address() == null || op.address().isBlank()) {
                    continue;
                }
                try {
                    ShAddress address = ShAddress.parse(op.address());
                    if (address.kind() == ShAddress.Kind.SITE && address.site() != null) {
                        union.add(address.site());
                    }
                } catch (ShTeachingException unparseable) {
                    // Deliberate: see above. The op itself teaches.
                }
            }
            return union.size() == sites.size() ? this : new Declared(union, postTypes);
        }

        /**
         * This declaration, plus every vocabulary type the run would create by its own
         * {@code render.provision} (SH607).
         *
         * <p>The post-type twin of {@link #withSitesFrom}, and it exists for the same reason SH153
         * gave the original: without it a <b>dry run</b> of a document that provisions {@code Page}
         * and then writes one fails on the first post, so planning a conversion into a bare instance
         * — the case the whole thing is for — is the case that cannot be planned. Read off the run's
         * own ops rather than promised by a caller, so it can never make a plan greener than the
         * apply it predicts, and a hand-written batch gets it too.</p>
         *
         * <p>An unknown name is left alone: {@code render.provision} raises the teaching error with
         * the {@code allowed} list when it runs, and a second refusal raised from here would report
         * one defect twice with less context.</p>
         *
         * @param fieldsOf the catalogue, as a lookup — empty for a name it does not carry
         */
        Declared withPostTypesFrom(List<ShAgentBatch.Op> ops,
                                   java.util.function.Function<String, Set<String>> fieldsOf) {
            Map<String, Set<String>> union = new LinkedHashMap<>(postTypes);
            for (ShAgentBatch.Op op : ops) {
                if (!RENDER_PROVISION.equals(op.op())) {
                    continue;
                }
                // SH395 — the render five, which this op ALWAYS ensures whatever `data.types`
                // says. They were missing here, and the consequence was the exact shape SH153
                // fixed one type-family along: a plan that declared a `PageLayout` post refused
                // on the post-type the same plan would have created. An apply worked and a plan
                // did not, which is the worst of the three possible outcomes — `--dry-run` is
                // the call an agent makes to find out whether the real one is safe.
                for (String name : ShRenderPostTypes.NAMES) {
                    Set<String> fields = fieldsOf.apply(name);
                    union.putIfAbsent(name, fields.isEmpty() ? Set.of() : fields);
                }
                for (String name : strings(op.data(), "types")) {
                    Set<String> fields = fieldsOf.apply(name);
                    if (!fields.isEmpty()) {
                        union.putIfAbsent(name, fields);
                    }
                }
                // SH871 — and the fields this run would DECLARE, which is the same argument one
                // level in: a document that declares `Page.b1a2c3dHeading` and then writes it must
                // plan, or `convert --annotate --dry-run` refuses on every field the same document
                // creates. Merged into whatever the catalogue said rather than replacing it — a
                // declaration widens a type, which is all `mergeMissingFields` will do to it.
                for (Map.Entry<String, List<String>> entry : declaredFields(op.data()).entrySet()) {
                    Set<String> merged = new LinkedHashSet<>(
                            union.getOrDefault(entry.getKey(), Set.of()));
                    merged.addAll(entry.getValue());
                    union.put(entry.getKey(), merged);
                }
            }
            // By content and not by size: the loop above may widen a type already in the map, and a
            // size comparison would drop exactly the fields a declaration adds.
            return union.equals(postTypes) ? this : new Declared(sites, union);
        }

        private boolean declaresSite(String nameOrId) {
            if (nameOrId == null) {
                return false;
            }
            String wanted = nameOrId.trim();
            return sites.stream().anyMatch(wanted::equalsIgnoreCase);
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
                                    Declared givenDeclared) {
        List<ShAgentBatch.Op> ops = validate(batch, source);
        // SH329 — a plan must be able to describe its own first step. Applied here rather than at
        // the three entry points so the blueprint path gets it too: a package may carry a
        // `site.upsert` its manifest never declared, and that is the same plan failing the same way.
        // Honoured on a dry run only, by `Session` — the record is a planning aid, never a write.
        // SH607 — and the same for the vocabulary types the run's own `render.provision` installs,
        // so a dry run of a conversion into a bare instance can be planned at all.
        Declared declared = givenDeclared.withSitesFrom(ops)
                .withPostTypesFrom(ops, shVocabularyPostTypes::fieldNames);
        // SH107 — publishing is a capability, not a default. Checked before anything runs and
        // on a dryRun too: an agent that cannot publish should learn that while planning, not
        // after it has built twelve pages around the assumption.
        requireMayPublish(ops);
        // SH108 — a destructive run takes two calls, and the second one carries the digest of
        // the *plan*. So a batch that could delete is planned here first: what it would really
        // destroy is what needs confirming, and a delete of something already gone is a noop
        // that needs nothing (which is what keeps a document re-runnable).
        if (!batch.dryRun() && ShAgentConfirm.mayDestroy(ops)) {
            ShAgentChangeSet plan = execute(ops, new Session(true, ids, batch.links(), actor, declared));
            String expected = ShAgentConfirm.ofPlan(plan);
            if (expected != null) {
                ShAgentConfirm.require(batch.confirm(), expected, plan);
            }
        }
        // SH734 — charged after the plan and before the write, and only on a real run. A dry run
        // costs nothing (a plan writes nothing, and the confirm token above means a destructive run
        // MUST plan first — charging would tax the safe path twice), and charging before the
        // confirm check would spend a caller's window on a run that never happened.
        if (!batch.dryRun()) {
            shAgentSessionBudget.spend(ops.size());
        }
        ShAgentChangeSet result = execute(ops, new Session(batch.dryRun(), ids, batch.links(), actor, declared));
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
                if (!session.dryRun) {
                    // SH544 — not an extra flush, an earlier one. Hibernate auto-flushes before
                    // the *next* op's address query anyway, so a refused insert would otherwise
                    // surface inside op i+1 and be reported as `ops[i+1]`. An `at` that names the
                    // wrong op is worse than no `at` at all (P6), and this is what makes it true.
                    // Any JpaRepository is a handle on the one persistence context.
                    shPostUnifiedRepository.flush();
                }
            } catch (ShTeachingException failure) {
                // Re-thrown, not wrapped: the original problem already teaches what was
                // wrong (allowed values, didYouMean, an example). All this adds is
                // *where* — without which a caller has to guess which of 40 ops failed.
                throw new BatchOpException(failure.problem().withAt("ops[" + index + "]"));
            } catch (DataAccessException refused) {
                // SH544 — the database refusing one op used to leave here as a 500 with Spring's
                // default body, so a 201-page conversion returned nothing that named the op, the
                // address, the column or the length, and diagnosing it meant reading the server
                // log. The batch is one transaction and this op has already marked it
                // rollback-only, so there is nothing to salvage — only something to say.
                throw new BatchOpException(
                        ShDataIntegrityProblem.of(refused, subject(ops.get(index)))
                                .withAt("ops[" + index + "]"));
            }
        }
        int changed = (int) results.stream().filter(Change::isChange).count();
        return new ShAgentChangeSet(session.dryRun, ops.size(), changed, List.copyOf(results));
    }

    /**
     * The op and what it acted on, in the caller's own words (SH544) — {@code post.upsert
     * post:vilt/about}. {@code ops[3]} says <em>which</em> op; this says <em>what</em> it was,
     * which is what an agent holding a 40-op document needs in order to fix the source rather
     * than the request.
     */
    private static String subject(ShAgentBatch.Op op) {
        if (op == null) {
            return null;
        }
        String name = op.op() == null ? "an op" : op.op().trim();
        return op.address() == null || op.address().isBlank() ? name : name + " " + op.address();
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
        // SH993 — a release run publishes a whole set, so it is gated by the same grant. Reading only
        // post.publish here would have made the release the way around the capability.
        boolean publishes = ops.stream()
                .anyMatch(op -> op != null && op.op() != null
                        && List.of(POST_PUBLISH, RELEASE_RUN, RELEASE_ROLLBACK)
                                .contains(op.op().trim().toLowerCase(Locale.ROOT)));
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
            // SH547 — the same shape SH138 gave the size ceiling, applied to the empty case: the
            // error has to know which door it came through. `/agent/apply` compiles a document into
            // ops and hands them here, so an empty document raised the *batch's* refusal — `allowed`
            // listing eleven op names and `example` showing an `ops` array, every field of it
            // describing the endpoint the caller was not using. Following it exactly reproduces the
            // same 400. P6 is not that a 4xx carries fields; it is that following them works.
            throw source == Source.DOCUMENT ? new EmptyDocumentException() : new EmptyBatchException();
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
            case SITE_DELETE -> siteDelete(op, session);
            case SITE_RESTORE -> siteRestore(op, session);
            case FOLDER_UPSERT -> folderUpsert(op, session);
            case FOLDER_MOVE -> folderMove(op, session);
            case FOLDER_ACL -> folderAcl(op, session);
            case FOLDER_VISIBILITY -> folderVisibility(op, session);
            case POST_VISIBILITY -> postVisibility(op, session);
            case FOLDER_COPY -> folderCopy(op, session);
            case FOLDER_DELETE -> folderDelete(op, session);
            case POST_UPSERT -> postUpsert(op, session);
            case POST_MOVE -> postMove(op, session);
            case POST_REDIRECT -> postRedirect(op, session);
            case POST_PUBLISH -> postPublish(op, session);
            case POST_UNPUBLISH -> postUnpublish(op, session);
            case POST_DELETE -> postDelete(op, session);
            case POST_RESTORE -> postRestore(op, session);
            case POST_ROLLBACK -> postRollback(op, session);
            case FOLDER_RESTORE -> folderRestore(op, session);
            case RENDER_PROVISION -> renderProvision(op, session);
            case RELEASE_UPSERT -> releaseUpsert(op, session);
            case RELEASE_ADD -> releaseAdd(op, session);
            case RELEASE_REMOVE -> releaseRemove(op, session);
            case RELEASE_RUN -> releaseRun(op, session);
            case RELEASE_ARCHIVE -> releaseArchive(op, session);
            case RELEASE_ROLLBACK -> releaseRollback(op, session);
            case NOTE_ADD -> noteAdd(op, session);
            case NOTE_RESOLVE -> noteResolve(op, session);
            case ASSET_META -> assetMeta(op, session);
            default -> throw new UnknownOpException(op.op());
        };
    }

    // --- releases (SH993) ---

    /**
     * SH993 — plans a release, or confirms the one there.
     *
     * <p>Idempotent the way {@code folder.upsert} is: a release that exists is a noop unless
     * {@code data.description} says something different, so a document naming it can be re-run.</p>
     */
    private Change releaseUpsert(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.RELEASE);
        String name = ShReleaseService.requireName(address.releaseName());
        ShSite site = requireSite(address.site(), session);
        String description = text(op.data(), "description");
        Optional<ShRelease> existing = isPending(site) ? Optional.empty()
                : shReleaseService.find(site.getId(), name);
        if (existing.isEmpty()) {
            if (session.releasePlanned(address)) {
                return session.change(op, address, Change.NOOP, "the release this batch plans", null);
            }
            session.planRelease(address);
            String id = session.dryRun ? null
                    : shReleaseService.create(site.getId(), name, description, session.actor).getId();
            return session.change(op, address, Change.CREATE, "planned, with no posts yet", id);
        }
        ShRelease release = existing.get();
        if (description == null || description.equals(release.getDescription())) {
            return session.change(op, address, Change.NOOP, null, release.getId());
        }
        if (!session.dryRun) {
            shReleaseService.describe(release, description, session.actor);
        }
        return session.change(op, address, Change.UPDATE, "description", release.getId());
    }

    private Change releaseAdd(ShAgentBatch.Op op, Session session) {
        return releaseMembers(op, session, true);
    }

    private Change releaseRemove(ShAgentBatch.Op op, Session session) {
        return releaseMembers(op, session, false);
    }

    /**
     * SH993 — {@code release.add} and {@code release.remove}: one row for the release, naming the posts
     * that moved, rather than a row per post, because the subject of the op is the set.
     *
     * <p>The posts are resolved before anything is written, so a name that points at nothing fails
     * the batch instead of leaving a release short by one page. A post an earlier op of this dry run
     * creates counts as there, which is SH153's rule for a plan.</p>
     */
    private Change releaseMembers(ShAgentBatch.Op op, Session session, boolean adding) {
        ShAddress address = writeAddress(op, ShAddress.Kind.RELEASE);
        String name = ShReleaseService.requireName(address.releaseName());
        ShSite site = requireSite(address.site(), session);
        List<ShAddress> posts = releasePosts(op, address, site, session);
        Optional<ShRelease> release = isPending(site) ? Optional.empty()
                : shReleaseService.find(site.getId(), name);
        if (release.isEmpty() && !(session.dryRun && session.releasePlanned(address))) {
            shReleaseService.require(site.getId(), site.getName(), name);
        }
        release.ifPresent(ShReleaseService::requireOpen);

        Map<String, String> ids = new LinkedHashMap<>();
        for (ShAddress post : posts) {
            Optional<ShPostUnified> row = state(site, post, ShPostState.DRAFT)
                    .or(() -> state(site, post, ShPostState.PUBLISHED));
            if (row.isPresent()) {
                ids.put(post.toString(), row.get().getId());
            } else if (session.dryRun && session.alreadyPlanned(post)) {
                ids.put(post.toString(), null);
            } else {
                throw new ShAddressResolver.UnresolvedAddressException(post, List.of());
            }
        }
        Set<String> held = release
                .map(r -> (Set<String>) new LinkedHashSet<>(shReleaseService.postIds(r.getId())))
                .orElse(Set.of());
        List<String> moved = ids.entrySet().stream()
                .filter(entry -> adding
                        ? entry.getValue() == null || !held.contains(entry.getValue())
                        : entry.getValue() != null && held.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        String releaseId = release.map(ShRelease::getId).orElse(null);
        int unmoved = ids.size() - moved.size();
        if (moved.isEmpty()) {
            return session.change(op, address, Change.NOOP, adding
                    ? "already holds " + (ids.size() == 1 ? "it" : "all " + ids.size())
                    : "holds none of them", releaseId);
        }
        if (!session.dryRun) {
            List<String> movedIds = moved.stream().map(ids::get).toList();
            // SH696 — a release is a plan to publish these posts, so membership is asked of the same
            // ACL the publish will be: a credential that may not write the post may not put it in a
            // launch either, which would otherwise fail halfway through the run instead.
            for (String moving : movedIds) {
                shContentAclService.requireWrite(moving);
            }
            if (adding) {
                shReleaseService.add(release.orElseThrow(), movedIds, session.actor);
            } else {
                shReleaseService.remove(release.orElseThrow(), movedIds, session.actor);
            }
        }
        String named = String.join(", ", moved.subList(0, Math.min(moved.size(), INBOUND_LINK_CAP)))
                + (moved.size() > INBOUND_LINK_CAP ? " and " + (moved.size() - INBOUND_LINK_CAP)
                        + " more" : "");
        return session.change(op, address, Change.UPDATE, (adding ? "added " : "removed ")
                + moved.size() + ": " + named
                + (unmoved == 0 ? "" : "; " + unmoved + (adding ? " already in it" : " not in it")),
                releaseId);
    }

    /**
     * SH994 — {@code release.rollback}: every page in the release goes back to what it replaced.
     *
     * <p>One change row for the release, like the run, and it is what the confirm token is taken over:
     * the detail counts the pages that go back and the ones that come down, so a release whose items
     * changed since the plan makes the token stale rather than undoing a different set.</p>
     */
    private Change releaseRollback(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.RELEASE);
        String name = ShReleaseService.requireName(address.releaseName());
        ShSite site = requireSite(address.site(), session);
        ShRelease release = shReleaseService.require(site.getId(), site.getName(), name);
        ShReleaseService.requireRollbackable(release);
        ShReleaseRunner.Undo undo = shReleaseRunner.undo(release);
        if (undo.size() == 0) {
            return session.change(op, address, Change.NOOP, "holds no posts", release.getId());
        }
        if (!session.dryRun) {
            for (String going : undo.restores()) {
                shContentAclService.requirePublish(going);
            }
            for (String going : undo.downs()) {
                shContentAclService.requirePublish(going);
            }
            shReleaseRunner.rollback(release, session.actor);
        }
        return session.change(op, address, Change.UPDATE, undo.restores().size() + " back"
                + (undo.downs().isEmpty() ? "" : ", " + undo.downs().size() + " down"),
                release.getId());
    }

    /** SH993 — {@code release.archive}, and {@code cancel:true} to put it back. */
    /**
     * SH1003 — a note on the object an address names.
     *
     * <p>The address is resolved the same way every other op resolves one, so a note hangs on the
     * page a caller can already name rather than on a uuid it would have to look up. A dry run
     * reports the row it would write and writes nothing: a note is a write like any other, and a
     * plan that silently posted comments would be the one surprise this surface must not have.</p>
     */
    private Change noteAdd(ShAgentBatch.Op op, Session session) {
        ShAddress address = noteAddress(op);
        String objectId = shAddressResolver.resolve(address).id();
        JsonNode data = requireData(op, NOTE_BODY_KEY, "the correction itself");
        String body = text(data, NOTE_BODY_KEY);
        if (session.dryRun) {
            return session.change(op, address, Change.UPDATE, "a note", objectId);
        }
        shNoteService.add(objectId, text(data, NOTE_FIELD_KEY), integerOf(data, NOTE_REVISION_KEY),
                text(data, NOTE_PARENT_KEY), text(data, NOTE_KIND_KEY), body, session.actor);
        return session.change(op, address, Change.UPDATE, "a note", objectId);
    }

    /**
     * SH1032 — merge an asset's own metadata, leaving the machine's stamps alone.
     *
     * <p>Every key is optional and a blank one clears it, which is {@code updateMeta}'s rule and
     * therefore the console's: one writer, one meaning. An op that merged on the console's rules
     * and cleared on its own would be the two surfaces disagreeing about what an empty string
     * means, which is the failure this composition exists to prevent.</p>
     */
    private Change assetMeta(ShAgentBatch.Op op, Session session) {
        ShAddress address = assetAddress(op);
        String objectId = shAddressResolver.resolve(address).id();
        JsonNode data = requireData(op, ASSET_ALT_KEY,
                "what the image says about itself — `alt`, and optionally `caption` and `tags`");
        // Before the dry-run branch, not after: a plan that reported "would describe this" about a
        // page is the plan P9 exists to stop being trusted, and the caller finds out at apply time
        // when the batch is half-written. Both branches ask the same question of the same row.
        requireAsset(address, objectId);
        if (session.dryRun) {
            return session.change(op, address, Change.UPDATE, "asset metadata", objectId);
        }
        // The same question post.upsert asks of a page, asked of the asset (SH696). Caught by
        // ShAgentOpAclLintTest rather than by review, which is the point of that lint: a MISSING
        // call has nothing on the screen to look at, so `folder.restore` went without one for as
        // long as it existed. An asset is content a role can be scoped away from like any other.
        shContentAclService.requireWrite(objectId);
        shStaticFileService.updateMeta(objectId, text(data, ASSET_ALT_KEY),
                text(data, ASSET_CAPTION_KEY), stringList(data, ASSET_TAGS_KEY), session.actor);
        return session.change(op, address, Change.UPDATE, "asset metadata", objectId);
    }

    /**
     * The address {@code asset.meta} takes — {@code id:} or {@code post:} (SH1032).
     *
     * <p>{@code id:} is the form that actually reaches an asset, and it is not a convenience: a
     * {@code File} post has <b>no friendly URL</b> — the type's design, and the reason
     * {@code find} addresses assets {@code id:} and {@code ShAgentReadService} exempts them from
     * {@code url-missing}. So {@code writeAddress(POST)}, which every other write uses, could
     * never name one. {@code post:} is accepted beside it for the case a curator gave the asset a
     * URL by hand rather than refused as a kind error, which would read as "assets are not
     * writable" when what is true is "this one has no path".</p>
     */
    private ShAddress assetAddress(ShAgentBatch.Op op) {
        if (op.address() == null || op.address().isBlank()) {
            throw new MissingOpFieldException("address", op.op(), null, "id:<uuid>",
                    "An asset is named by the id a find or an upload answered with.");
        }
        ShAddress address = ShAddress.parse(op.address());
        if (address.kind() != ShAddress.Kind.ID && address.kind() != ShAddress.Kind.POST) {
            throw new WrongAddressKindException(op.op(), address, ShAddress.Kind.ID);
        }
        return address;
    }

    /**
     * That the address names an asset, refused with what to do instead (SH1032).
     *
     * <p>Without this the refusal is {@code updateMeta}'s {@code NoSuchElementException} —
     * "File post not found: &lt;uuid&gt;" — about an id the caller just read back from a find,
     * which reads as an instance fault rather than as the wrong op. P6: the 4xx carries the fix.</p>
     */
    private void requireAsset(ShAddress address, String objectId) {
        boolean isAsset = shPostUnifiedRepository.findByIdAndState(objectId, ShPostState.PUBLISHED)
                .map(row -> row.getShPostType() != null
                        && ShSystemPostType.FILE.equals(row.getShPostType().getName()))
                .orElse(false);
        if (!isAsset) {
            throw new NotAnAssetException(address);
        }
    }

    /**
     * SH1277 — {@code folder.acl}: the grants on one folder, replaced whole.
     *
     * <p>Idempotent the way a desired-state document has to be: grants equal to what the folder
     * already carries are a noop, so a blueprint re-applied changes nothing. Guarded by WRITE on the
     * folder, which is the console's own rule for {@code PUT /object/{id}/acl}, so the agent path
     * grants nothing a curator could not (the non-goal: no privilege escalation). A grant naming a
     * user or group the instance does not have is refused before anything is written, in a dry run
     * too, because a grant to nobody locks the folder against everyone but an administrator.</p>
     */
    private Change folderAcl(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.FOLDER);
        List<ShContentAclService.AclEntry> grants = aclGrants(op);
        ShContentAclService.AclEntry unknown = shContentAclService.unknownPrincipal(grants);
        if (unknown != null) {
            throw new UnknownAclPrincipalException(address, unknown);
        }
        ShSite site = requireSite(address.site(), session);
        String summary = grants.isEmpty() ? "open to everyone" : grants.size() + " grant(s)";
        Optional<ShFolder> folder = isPending(site) ? Optional.empty() : findFolder(site, address.path());
        if (folder.isEmpty()) {
            if (session.dryRun && (isPending(site) || session.isPlannedFolder(site, address.path()))) {
                return session.change(op, address, Change.UPDATE, summary, null);
            }
            throw new UnresolvableParentException(address);
        }
        String id = folder.get().getId();
        if (shContentAclService.hasExactly(id, grants)) {
            return session.change(op, address, Change.NOOP, summary, id);
        }
        if (session.dryRun) {
            return session.change(op, address, Change.UPDATE, summary, id);
        }
        shContentAclService.requireWrite(id);
        shContentAclService.replaceAcl(id, grants);
        return session.change(op, address, Change.UPDATE, summary, id);
    }

    /**
     * SH1285 — {@code folder.visibility}: whether a section's delivered pages are public or for
     * members, replaced whole.
     *
     * <p>{@code folder.acl}'s shape on purpose: idempotent (the same declaration is a noop, so a
     * re-applied blueprint changes nothing), guarded by WRITE on the folder so the agent path grants
     * nothing a curator could not, and a group the instance does not have is refused before anything
     * is written, because a gate for nobody hides the section from everyone but an administrator.</p>
     */
    private Change folderVisibility(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.FOLDER);
        Gate wanted = gateOf(op, address);
        ShSite site = requireSite(address.site(), session);
        Optional<ShFolder> folder = isPending(site) ? Optional.empty() : findFolder(site, address.path());
        if (folder.isEmpty()) {
            if (session.dryRun && (isPending(site) || session.isPlannedFolder(site, address.path()))) {
                return session.change(op, address, Change.UPDATE, wanted.summary(), null);
            }
            throw new UnresolvableParentException(address);
        }
        String id = folder.get().getId();
        if (!session.dryRun) {
            // In the handler, where ShAgentOpAclLintTest reads it: a guard one call down is one it
            // cannot attribute to the op, and a missing one is invisible to review.
            shContentAclService.requireWrite(id);
        }
        return replaceGate(op, session, address, id, wanted);
    }

    /**
     * SH1300 — {@code post.visibility}: one post members-only, or public again, in a folder that
     * stays as it is. The owner chose an op of its own over a reserved key in {@code post.upsert}
     * (access is not a field value) and over widening {@code folder.visibility} (its name would stop
     * saying what it does). Everything after the address is {@code folder.visibility}'s.
     *
     * <p>A post this batch has not written yet plans as an update in a dry run, as a planned folder
     * does; a real run refuses it with the resolver's own teaching 404.</p>
     */
    private Change postVisibility(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.POST);
        Gate wanted = gateOf(op, address);
        Optional<ShAgentResolved> post = shAddressResolver.find(address);
        if (post.isEmpty()) {
            if (session.dryRun) {
                return session.change(op, address, Change.UPDATE, wanted.summary(), null);
            }
            shAddressResolver.resolve(address);
        }
        String id = post.orElseThrow().id();
        if (!session.dryRun) {
            shContentAclService.requireWrite(id);
        }
        return replaceGate(op, session, address, id, wanted);
    }

    /** SH1285 — what a visibility op declares: members-only for these groups (none: anyone signed in), or public. */
    private record Gate(boolean members, List<String> groups) {
        String summary() {
            return members ? (groups.isEmpty() ? "members only" : "members of " + String.join(", ", groups)) : "public";
        }
    }

    /** SH1285 — {@code data.visibility} and {@code data.groups}, refused before anything is read or written. */
    private Gate gateOf(ShAgentBatch.Op op, ShAddress address) {
        JsonNode data = op.data();
        if (data == null || !data.hasNonNull(VISIBILITY_KEY)) {
            throw new MissingOpFieldException("data.visibility", op.op(), null,
                    "{\"visibility\":\"members\",\"groups\":[\"Members\"]}",
                    "A section is public or for members; groups narrow members to those groups.");
        }
        String spelled = data.path(VISIBILITY_KEY).asString("").trim().toLowerCase(Locale.ROOT);
        if (!spelled.equals("public") && !spelled.equals("members")) {
            throw new InvalidVisibilityException(op);
        }
        boolean members = spelled.equals("members");
        List<String> groups = new ArrayList<>();
        if (data.has(GROUPS_KEY)) {
            if (!data.get(GROUPS_KEY).isArray()) {
                throw new InvalidVisibilityException(op);
            }
            data.get(GROUPS_KEY).forEach(group -> groups.add(group.asString("").trim()));
            groups.removeIf(String::isEmpty);
        }
        String unknown = members ? shContentAclService.unknownGroup(groups) : null;
        if (unknown != null) {
            throw new UnknownMemberGroupException(address, unknown);
        }
        return new Gate(members, List.copyOf(groups));
    }

    /** SH1285 — the gate on one object, replaced whole: a noop when it already reads so. The caller guards WRITE. */
    private Change replaceGate(ShAgentBatch.Op op, Session session, ShAddress address, String id, Gate wanted) {
        Optional<List<String>> own = shContentAclService.ownGate(id);
        boolean same = wanted.members()
                ? own.isPresent() && own.get().equals(wanted.groups().stream().sorted().toList())
                : own.isEmpty();
        if (same) {
            return session.change(op, address, Change.NOOP, wanted.summary(), id);
        }
        if (session.dryRun) {
            return session.change(op, address, Change.UPDATE, wanted.summary(), id);
        }
        shContentAclService.replaceGate(id, wanted.members(), wanted.groups());
        return session.change(op, address, Change.UPDATE, wanted.summary(), id);
    }

    /** SH1285 — a {@code folder.visibility} that is not {@code public} or {@code members} plus groups. */
    public static class InvalidVisibilityException extends ShTeachingException {

        public InvalidVisibilityException(ShAgentBatch.Op op) {
            super(ShProblem.badRequest("invalid-visibility",
                            "folder.visibility on " + op.address() + " needs data.visibility public or members")
                    .withDetail("A members-only section answers its delivered pages only to signed-in users, "
                            + "or to the groups data.groups names. public opens it again.")
                    .withAllowed(List.of("public", "members"))
                    .withFix("Send data.visibility \"members\" and, optionally, data.groups as a list of group names.")
                    .withExample("{\"op\":\"folder.visibility\",\"address\":\"folder:Acme/members\","
                            + "\"data\":{\"visibility\":\"members\",\"groups\":[\"Members\"]}}"));
        }
    }

    /** SH1285 — a members-only section for a group the instance does not have. */
    public static class UnknownMemberGroupException extends ShTeachingException {

        public UnknownMemberGroupException(ShAddress address, String group) {
            super(ShProblem.badRequest("unknown-member-group",
                            "No group named '" + group + "' to open " + address + " to")
                    .withDetail("A gate for nobody would hide the section from everyone but an "
                            + "administrator, and nothing would say why.")
                    .withFix("Create the group first (Administration > Groups, or POST /api/v2/group), "
                            + "or leave data.groups out to open it to any signed-in user."));
        }
    }

    /** {@code data.acl} as grants; the vocabulary is {@link ShContentAclService#parseGrants}'s. */
    private static List<ShContentAclService.AclEntry> aclGrants(ShAgentBatch.Op op) {
        JsonNode list = op.data() == null ? null : op.data().get(ACL_KEY);
        if (list == null || !list.isArray()) {
            throw new MissingOpFieldException("data.acl", op.op(), null,
                    "{\"acl\":[{\"group\":\"Administrator\",\"permission\":\"WRITE\"}]}",
                    "A folder's grants are a list; an empty list opens the folder to everyone again.");
        }
        try {
            return ShContentAclService.parseGrants(list);
        } catch (ShContentAclService.MalformedGrantException malformed) {
            throw new MalformedAclGrantException(op, malformed.getMessage());
        }
    }
    /** SH1277 — a grant that does not name exactly one principal and a permission. */
    public static class MalformedAclGrantException extends ShTeachingException {

        public MalformedAclGrantException(ShAgentBatch.Op op, String grant) {
            super(ShProblem.badRequest("malformed-acl-grant",
                            "A grant in " + op.op() + " on " + op.address() + " is not one principal and one permission")
                    .withDetail("Got " + grant + ". Each grant names exactly one of group, user or role, "
                            + "and a permission of READ, WRITE or PUBLISH (WRITE includes READ).")
                    .withAllowed(List.of("group", "user", "role", "permission"))
                    .withFix("Give each grant one of group, user or role, and a permission: "
                            + "{\"group\":\"Administrator\",\"permission\":\"WRITE\"}.")
                    .withExample("{\"op\":\"folder.acl\",\"address\":\"folder:Acme/theme\","
                            + "\"data\":{\"acl\":[{\"group\":\"Administrator\",\"permission\":\"WRITE\"}]}}"));
        }
    }

    /** SH1277 — a grant to a user or group this instance does not have. */
    public static class UnknownAclPrincipalException extends ShTeachingException {

        public UnknownAclPrincipalException(ShAddress address, ShContentAclService.AclEntry grant) {
            super(ShProblem.badRequest("unknown-acl-principal",
                            "No " + grant.principalType().name().toLowerCase(Locale.ROOT) + " named '"
                                    + grant.principalName() + "' to grant " + address + " to")
                    .withDetail("A grant to nobody would lock the folder against everyone but an "
                            + "administrator, and nothing would say why.")
                    .withFix("Create the " + grant.principalType().name().toLowerCase(Locale.ROOT)
                            + " first (Administration > Groups, or POST /api/v2/group), or name one that "
                            + "exists. A fresh instance has the group 'Administrator'."));
        }
    }

    /** SH1032 — {@code asset.meta} against something that is not an asset. */
    public static class NotAnAssetException extends ShTeachingException {

        public NotAnAssetException(ShAddress address) {
            super(ShProblem.badRequest("not-an-asset",
                            "'" + address + "' is not an uploaded asset")
                    .withDetail("asset.meta merges the three keys a File post owns — ALT, CAPTION "
                            + "and TAGS — and only a File post has them. A page's fields belong to "
                            + "its post-type and are written whole by post.upsert.")
                    .withFix("Use post.upsert for a page's own fields, or address the asset: "
                            + "GET /api/v2/agent/find?type=File answers with id: addresses.")
                    .withExample("{\"op\":\"asset.meta\",\"address\":\"id:0f1e2d3c-4b5a-6789-0abc-"
                            + "def012345678\",\"data\":{\"alt\":\"A wide hero\"}}"));
        }
    }

    /** One JSON array of strings, or {@code null} when the key is absent — never an empty list. */
    private static List<String> stringList(JsonNode data, String key) {
        JsonNode value = data == null ? null : data.get(key);
        if (value == null || !value.isArray()) {
            return null;
        }
        List<String> read = new java.util.ArrayList<>();
        value.forEach(each -> {
            if (each != null && !each.isNull()) {
                read.add(each.asString());
            }
        });
        return read;
    }

    /** SH1003 — close a note, or re-open it with {@code cancel}. */
    private Change noteResolve(ShAgentBatch.Op op, Session session) {
        ShAddress address = noteAddress(op);
        String objectId = shAddressResolver.resolve(address).id();
        requireData(op, NOTE_ID_KEY, "which note");
        boolean closing = !cancels(op);
        if (session.dryRun) {
            return session.change(op, address, Change.UPDATE,
                    closing ? "resolve a note" : "re-open a note", objectId);
        }
        shNoteService.resolve(text(op.data(), NOTE_ID_KEY), closing, session.actor);
        return session.change(op, address, Change.UPDATE,
                closing ? "note resolved" : "note re-opened", objectId);
    }

    /**
     * The address a note hangs on — a page or a section, resolved the way every other op resolves
     * one (SH1003).
     *
     * <p>Not restricted to {@code post:} even though a page is the common case: a note on a section
     * is the same statement about a bigger thing, and the resolver already answers both. What it is
     * restricted from is a {@code site:} address, which the writes vocabulary never takes.</p>
     */
    private ShAddress noteAddress(ShAgentBatch.Op op) {
        if (op.address() == null || op.address().isBlank()) {
            throw new MissingOpFieldException("address", op.op(), null,
                    "post:<site>/<url>", "A note hangs on the thing it is about.");
        }
        ShAddress address = ShAddress.parse(op.address());
        if (address.kind() != ShAddress.Kind.POST && address.kind() != ShAddress.Kind.FOLDER
                && address.kind() != ShAddress.Kind.ID) {
            throw new WrongAddressKindException(op.op(), address, ShAddress.Kind.POST);
        }
        return address;
    }

    /**
     * The op's {@code data}, refused when it is absent (SH1003).
     *
     * <p>Checked on a dry run too, which is the point: a key the vocabulary calls required and the
     * service does not is worse than no vocabulary — the caller supplies it, believes it matters,
     * and the day it stops being sent nothing says so.</p>
     */
    private static JsonNode requireData(ShAgentBatch.Op op, String key, String what) {
        JsonNode data = op.data();
        if (data == null || data.get(key) == null || text(data, key) == null) {
            throw new MissingOpFieldException("data." + key, op.op(), null, what,
                    "A note carries " + what + " in `data`.");
        }
        return data;
    }

    /** A number from an op's data, or {@code null} — a note's revision is optional. */
    private static Integer integerOf(JsonNode data, String field) {
        JsonNode value = data == null ? null : data.get(field);
        return value == null || !value.isNumber() ? null : value.asInt();
    }

    private Change releaseArchive(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.RELEASE);
        String name = ShReleaseService.requireName(address.releaseName());
        ShSite site = requireSite(address.site(), session);
        ShRelease release = shReleaseService.require(site.getId(), site.getName(), name);
        boolean archived = release.getArchivedAt() != null;
        if (archived == !cancels(op)) {
            return session.change(op, address, Change.NOOP,
                    archived ? "already archived" : "not archived", release.getId());
        }
        if (!session.dryRun) {
            shReleaseService.archive(release, !cancels(op), session.actor);
        }
        return session.change(op, address, Change.UPDATE,
                cancels(op) ? "back in the listing" : "archived", release.getId());
    }

    /**
     * SH993 — {@code release.run}: the set goes live in one transaction, now or at {@code when}.
     *
     * <p>One change row for the release rather than one per post, and the row says how many went live
     * and how many were already there. That row is also what the confirm token is taken over, so
     * adding a post between the plan and the run makes the token stale rather than launching a page
     * the caller never saw.</p>
     */
    private Change releaseRun(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.RELEASE);
        String name = ShReleaseService.requireName(address.releaseName());
        ShSite site = requireSite(address.site(), session);
        if (session.dryRun && (isPending(site) || session.releasePlanned(address))
                && (isPending(site) || shReleaseService.find(site.getId(), name).isEmpty())) {
            return session.change(op, address, Change.PUBLISH, "the release this batch plans", null);
        }
        ShRelease release = shReleaseService.require(site.getId(), site.getName(), name);
        Instant when = scheduleInstant(op, address);
        if (when != null && cancels(op)) {
            throw new CancelWithWhenException(op.op(), address);
        }
        if (cancels(op)) {
            Instant pending = release.getScheduledAt();
            if (pending == null) {
                return session.change(op, address, Change.NOOP, "no run scheduled", release.getId());
            }
            if (!session.dryRun) {
                shReleaseService.unschedule(release, session.actor);
            }
            return session.change(op, address, Change.UPDATE,
                    "cancelled the run scheduled for " + pending, release.getId());
        }
        if (when != null) {
            ShReleaseService.requireRunnable(release);
            Instant pending = release.getScheduledAt();
            if (pending != null && pending.truncatedTo(ChronoUnit.MILLIS)
                    .equals(when.truncatedTo(ChronoUnit.MILLIS))) {
                return session.change(op, address, Change.NOOP,
                        "already scheduled to run at " + pending, release.getId());
            }
            if (!session.dryRun) {
                shReleaseService.scheduleAt(release, when, session.actor);
            }
            return session.change(op, address, Change.UPDATE, "scheduled to run at " + when
                    + (pending == null ? "" : ", replacing " + pending), release.getId());
        }
        ShReleaseService.requireRunnable(release);
        ShReleaseRunner.Plan plan = shReleaseRunner.plan(release);
        if (plan.size() == 0) {
            return session.change(op, address, Change.NOOP, "holds no posts", release.getId());
        }
        if (!plan.missing().isEmpty()) {
            throw new ReleaseHoldsNothingException(address, plan.missing().size());
        }
        // One transaction is the promise, and the batch ceiling is what one transaction is allowed to
        // be. Refused while planning, with the count, because a launch that fails at post 201 is the
        // half-applied state a release exists to make unreachable.
        int max = properties.getBatch().getMaxOps();
        if (plan.size() > max) {
            throw new ReleaseTooLargeException(address, plan.size(), max);
        }
        if (!session.dryRun) {
            // SH995 — every page this launch puts live is asked for by name.
            for (String going : plan.publishes()) {
                shContentAclService.requirePublish(going);
                // SH997 — and every one of them passes the site's gates, before any of them is
                // published: a launch that stopped halfway is the state a release exists to prevent.
                shPublishGateService.requirePost(going);
            }
            shReleaseRunner.run(release, session.actor);
        }
        return session.change(op, address, Change.PUBLISH, plan.publishes().size() + " live"
                + (plan.unchanged().isEmpty() ? "" : ", " + plan.unchanged().size() + " unchanged"),
                release.getId());
    }

    /**
     * The posts {@code data.posts} names, as addresses in the release's site (SH993).
     *
     * <p>A {@code post:} address, or a bare URL read in the release's own site the way
     * {@code translationOf} reads one. A post in another site is refused: running the release must
     * change one site and nothing else.</p>
     */
    private List<ShAddress> releasePosts(ShAgentBatch.Op op, ShAddress release, ShSite site,
                                         Session session) {
        JsonNode posts = op.data() == null || !op.data().isObject() ? null
                : op.data().get(RELEASE_POSTS_KEY);
        if (posts == null || !posts.isArray() || posts.isEmpty()) {
            throw new MissingOpFieldException("data", op.op(), release,
                    "{\"op\":\"" + op.op() + "\",\"address\":\"" + release
                            + "\",\"data\":{\"posts\":[\"post:" + release.site() + "/about\"]}}",
                    "`data.posts` lists the posts: `post:` addresses, or URLs in the release's site.");
        }
        Map<String, ShAddress> named = new LinkedHashMap<>();
        for (JsonNode node : posts) {
            String raw = node.isString() ? node.asString().trim() : "";
            ShAddress post = raw.startsWith("post:") ? ShAddress.parse(raw)
                    : ShAddress.post(release.site(), raw);
            if (post.kind() != ShAddress.Kind.POST) {
                throw new WrongAddressKindException(op.op(), post, ShAddress.Kind.POST);
            }
            ShSite postSite = requireSite(post.site(), session);
            boolean sameSite = isPending(site) || isPending(postSite)
                    ? site.getName().equalsIgnoreCase(postSite.getName())
                    : site.getId().equals(postSite.getId());
            if (!sameSite) {
                throw new ReleaseSiteMismatchException(release, post);
            }
            named.putIfAbsent(post.toString(), post);
        }
        return List.copyOf(named.values());
    }

    /** A plain integer out of an op's `data`, or the default when it is absent or not a number. */
    private static int number(JsonNode data, String field, int fallback) {
        JsonNode value = data == null ? null : data.get(field);
        return value != null && value.isNumber() ? value.asInt() : fallback;
    }

    // --- search ops (SH413) ---

    /**
     * The default page of posts one call indexes, and the reason there is a page at all.
     *
     * <p>SH84's sender is single-threaded and already reports a queue-full incident when jobs
     * arrive in bulk. A site of ten thousand posts turned into ten thousand queued jobs is
     * therefore not a slow backfill, it is <b>an outage of the publish path</b> — the thing this
     * op exists to help would be broken by the op running. So a call does a page and says how far
     * it got, and the caller decides whether to ask for the next one.</p>
     */
    private static final int REINDEX_PAGE = 200;

    /** The most one call will take, however large a {@code limit} the caller asks for. */
    private static final int REINDEX_MAX = 1000;

    /**
     * Indexes one site's already-published content (SH413).
     *
     * <p>Built on {@link ShTuringIndexService#indexItem}, the same method the {@code AFTER_COMMIT}
     * listener uses, so there is <b>one</b> document mapping and not two that can drift — a
     * backfilled post and a freshly-published one must be the same document in the index, or the
     * search results depend on which path wrote them.</p>
     *
     * <h3>What it reports, and why that is the deliverable</h3>
     *
     * <p>Returning silently would leave the operator exactly where SH84 left them: unable to tell
     * "indexed nothing because it is off", "indexed nothing because this site routes nowhere" and
     * "indexed everything" apart. So the detail names how many were sent, how many the routing
     * skipped, and <b>how many remain</b> — which is what makes the paging usable rather than a
     * silent truncation (P9).</p>
     *
     * <p>{@code data.limit} bounds one call; {@code data.from} is the offset to resume at. Both are
     * plain numbers rather than a cursor, because the set is ordered by the repository and a
     * backfill that has to be exactly-once is a backfill that cannot be re-run — and re-running
     * this one is harmless, since indexing a document twice is indexing it once.</p>
     */
    /** What a reindex did: the line to append, and whether anything actually moved. */
    private record Reindexed(String detail, boolean changed) {
    }

    private Reindexed reindex(ShAgentBatch.Op op, Session session, String name, ShSite target) {
        // Off, or pointed at nothing. Reported rather than refused: an agent running a standard
        // build sequence against an instance that does not index should not have its batch fail,
        // and `searchIndexing` in the manifest is what tells it beforehand (SH413).
        if (!shTuringIndexService.active()) {
            return new Reindexed(", search indexing is off on this instance", false);
        }
        if (shTuringIndexService.snSiteFor(name).isEmpty()) {
            return new Reindexed(", '" + name + "' routes to no search index", false);
        }

        int from = Math.max(number(op.data(), "from", 0), 0);
        int limit = Math.clamp(number(op.data(), "limit", REINDEX_PAGE), 1, REINDEX_MAX);
        Page<ShPostUnified> page = shPostUnifiedRepository.findByShSite_IdAndState(
                target.getId(), ShPostState.PUBLISHED,
                PageRequest.of(from / limit, limit, Sort.by("id")));
        long total = page.getTotalElements();
        long remaining = Math.max(total - (long) from - page.getNumberOfElements(), 0);

        if (page.getNumberOfElements() == 0) {
            return new Reindexed(total == 0 ? ", no published posts to index"
                    : ", nothing to index at offset " + from + " of " + total, false);
        }
        if (session.dryRun) {
            return new Reindexed(", would index " + page.getNumberOfElements()
                    + " published post(s) of " + total
                    + (remaining > 0 ? ", " + remaining + " left after this call" : ""), true);
        }

        int sent = 0;
        int skipped = 0;
        for (ShPostUnified post : page.getContent()) {
            Optional<TurSNJobItem> item = shTuringIndexService.indexItem(post);
            if (item.isEmpty()) {
                skipped += 1;
                continue;
            }
            // SH913 — a reindex is exactly the case an attachment's text is missing for: the
            // library was published before extraction existed, so indexing it by name again would
            // leave it answering nothing. Inline here, unlike the AFTER_COMMIT listener, because
            // this is a bulk op an agent asked for rather than a publish somebody is waiting on.
            shTuringTextExtractor.enrich(item.get(), shTuringTextExtractor.attachmentOf(post));
            if (shTuringIndexService.send(item.get(), "index " + post.getId())) {
                sent += 1;
            } else {
                skipped += 1;
            }
        }
        // `changed` is true even when every send was refused: the op did reach out, and a caller
        // told "noop" about a run that tried and failed would read it as "already indexed".
        return new Reindexed(", indexed " + sent + " of " + page.getNumberOfElements()
                + " published post(s)"
                + (skipped > 0 ? ", " + skipped + " not accepted" : "")
                + " (" + total + " in this site"
                + (remaining > 0 ? ", " + remaining + " left: call again with from="
                        + (from + page.getNumberOfElements()) : ", none left") + ")", true);
    }

    // --- render ops (SH186) ---

    /**
     * Creates the {@code PageLayout} / {@code Region} / {@code Theme} types, idempotently.
     *
     * <p>Addressless, and it is the only op that is. Everything else here acts on a post, a folder or
     * a site; this acts on the <em>instance's model</em>, and inventing an address for it
     * ({@code render:post-types}) would add a fourth address kind for one op — SH79's forms are the
     * vocabulary an agent has to learn, and one of them existing for a single caller is worse than an
     * op whose {@code address} is the instance itself.</p>
     *
     * <p>Delegates to {@link ShRenderPostTypes#ensure()}, which is what the REST endpoint calls — the
     * op is a <em>shape</em> over the existing service and never a second implementation (P10). A
     * {@code dryRun} answers from {@link ShRenderPostTypes#existing()} and writes nothing, so a plan
     * says which of the three are missing without creating them.</p>
     */
    private Change renderProvision(ShAgentBatch.Op op, Session session) {
        List<String> already = shRenderPostTypes.existing().stream()
                .map(ShPostTypeDef::name)
                .toList();
        List<String> missing = ShRenderPostTypes.NAMES.stream()
                .filter(name -> !already.contains(name))
                .toList();
        // SH607 — `data.types` names SH95 vocabulary types this run is about to write against.
        // Resolved before anything is created, so an unknown name refuses the batch instead of
        // half-installing a model: `requireKnown` checks the whole list first.
        List<String> vocabulary = strings(op.data(), "types");
        List<String> vocabularyMissing = shVocabularyPostTypes.missing(vocabulary);
        // The row's address is the op's own when it named one — an agent provisioning while building a
        // site has `site:<name>` in hand and a row that echoes it reads in place. Otherwise the model
        // is the subject and there is no SH79 form for it, so the field is absent rather than invented:
        // a plausible-looking `render:post-types` would be a fourth address kind for one op.
        String address = op.address() == null || op.address().isBlank() ? null : op.address().trim();
        // SH326 — `data.reconcile:true` reaches SH289's repair verb, the half of the render model an
        // agent could not touch. Opt-in and never implied by `ensure`, which is SH289's own division:
        // `ensure` creates what is absent and is safe to call unattended, while this *changes* a type
        // the instance already has. One op rather than a tenth name, because the cost of a vocabulary
        // is paid by every caller that reads it (P3) and the two are one question — "make this
        // instance able to render" — asked of a new instance and of an old one.
        boolean alsoReconcile = flag(op.data(), "reconcile");
        List<String> repairs = alsoReconcile ? shRenderPostTypes.reconcile(true) : List.of();
        // SH871 — `data.fields` declares field NAMES on a type this instance already has (or that
        // this same op is about to create), as plain `Text`, adding only what is missing. It exists
        // because `convert --annotate` computes the exact list of fields a verbatim page's
        // annotations address and had no door to declare them: the run named forty of them, the
        // batch refused the write, and 18 assets had already been uploaded. Failing on a
        // precondition the run itself can satisfy is the shape SH763 was.
        Map<String, List<String>> declare = declaredFields(op.data());
        List<String> declarations = plannedDeclarations(declare, vocabularyMissing);
        if (missing.isEmpty() && repairs.isEmpty() && vocabularyMissing.isEmpty()
                && declarations.isEmpty()) {
            return new Change(op.op(), address, Change.NOOP,
                    nothingToDo(alsoReconcile, vocabulary), null);
        }
        if (session.dryRun) {
            return new Change(op.op(), address, Change.CREATE,
                    planned(missing, repairs, vocabularyMissing, declarations), null);
        }
        if (!missing.isEmpty()) {
            shRenderPostTypes.ensure();
        }
        // SH607 — after the render five, because a vocabulary type is content and the five are the
        // machinery that renders it; a run that failed between the two leaves the cheaper half done.
        List<String> vocabularyCreated = vocabularyMissing.isEmpty()
                ? List.of() : shVocabularyPostTypes.ensure(vocabulary);
        if (alsoReconcile) {
            // Re-run for effect. The plan above was the dry pass, and reading it twice is what
            // keeps the reported lines the ones that were actually applied — `ensure` may have
            // just created a type that then needs nothing.
            repairs = shRenderPostTypes.reconcile(false);
        }
        // SH871 — last, because a type this op just provisioned is the one a declaration is most
        // likely to be about, and the merge reads the live type rather than the catalogue.
        List<String> added = declareFields(declare);
        return new Change(op.op(), address, Change.CREATE,
                applied(missing, repairs, vocabularyCreated, added), null);
    }

    /**
     * The field names {@code data.fields} asks for, per post-type, validated (SH871).
     *
     * <p><b>Names, never definitions</b> — the line SH607 drew for {@code data.types}, one level in.
     * A declaration here says <em>that</em> a field exists, not what it is: every one is added as a
     * plain {@code Text}, optional, appended after the highest existing ordinal. A payload that
     * could carry a widget, {@code required} or {@code widgetSettings} would be a second way to
     * change the content model — the thing this op is explicitly not.</p>
     */
    private static Map<String, List<String>> declaredFields(JsonNode data) {
        if (data == null || !data.isObject()) {
            return Map.of();
        }
        JsonNode node = data.get("fields");
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, List<String>> byType = new LinkedHashMap<>();
        for (String key : node.propertyNames()) {
            JsonNode value = node.get(key);
            if (key == null || key.isBlank() || value == null || !value.isArray()) {
                continue;
            }
            List<String> names = new ArrayList<>();
            for (JsonNode each : value) {
                if (each == null || !each.isString() || each.stringValue().isBlank()) {
                    continue;
                }
                String name = each.stringValue().trim();
                if (!FIELD_NAME.matcher(name).matches()) {
                    throw new UndeclarableFieldException(key.trim(), name);
                }
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
            if (!names.isEmpty()) {
                byType.put(key.trim(), List.copyOf(names));
            }
        }
        return Collections.unmodifiableMap(byType);
    }

    /**
     * A field name this can append to a post-type without inventing an escape (SH871).
     *
     * <p>It becomes a JSON key, a form control's name and a Handlebars path, so the accepted shape
     * is the intersection of all three rather than whatever the column would hold. {@code convert}
     * builds these from a block hash and a role ({@code b1a2c3dHeading}) and cannot produce
     * anything else; the check is here for every other caller.</p>
     */
    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z]\\w{0,62}");

    /**
     * What {@code data.fields} would add, without adding it — the dry run's answer (SH871).
     *
     * <p>A type this same op would create resolves against the catalogue rather than against the
     * database, for {@code Declared.withPostTypesFrom}'s reason: planning a conversion into a bare
     * instance is the case the whole thing is for, and a plan that refused on a type its own first
     * op creates is the defect SH153 closed one family along.</p>
     */
    private List<String> plannedDeclarations(Map<String, List<String>> declare,
                                             List<String> beingCreated) {
        List<String> planned = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : declare.entrySet()) {
            String type = entry.getKey();
            if (beingCreated.contains(type)) {
                Set<String> shipped = shVocabularyPostTypes.fieldNames(type);
                entry.getValue().stream()
                        .filter(name -> !shipped.contains(name))
                        .map(name -> type + "." + name)
                        .forEach(planned::add);
                continue;
            }
            requireDeclarable(type);
            shPostTypeAdminService.mergeableFieldNames(textDef(type, entry.getValue()))
                    .forEach(name -> planned.add(type + "." + name));
        }
        return List.copyOf(planned);
    }

    /** Adds the missing ones and returns what it added, as {@code Type.field} (SH871). */
    private List<String> declareFields(Map<String, List<String>> declare) {
        List<String> added = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : declare.entrySet()) {
            requireDeclarable(entry.getKey());
            shPostTypeAdminService.mergeMissingFields(textDef(entry.getKey(), entry.getValue()))
                    .forEach(name -> added.add(entry.getKey() + "." + name));
        }
        return List.copyOf(added);
    }

    /** The def {@code mergeMissingFields} reads: a name and plain {@code Text} fields (SH871). */
    private static ShPostTypeDef textDef(String type, List<String> fields) {
        List<ShPostTypeAttrDef> attrs = fields.stream()
                .map(name -> new ShPostTypeAttrDef(name, name, null, TEXT_WIDGET,
                        false, false, false, 0, null, null))
                .toList();
        return new ShPostTypeDef(type, type, null, null, null, false, null, attrs);
    }

    /** The widget every declared field gets — see {@link #declaredFields}. */
    private static final String TEXT_WIDGET = "Text";

    /** Refuses a type nothing can append to, before any of the list is applied. */
    private void requireDeclarable(String type) {
        ShPostTypeDef existing = shPostTypeAdminService.get(type).orElse(null);
        if (existing == null || existing.system()) {
            throw new UndeclarableTypeException(type, existing != null,
                    shPostTypeAdminService.list().stream()
                            .filter(def -> !def.system())
                            .map(ShPostTypeDef::name)
                            .sorted()
                            .toList());
        }
    }

    /** A 400: the name would not survive being a field (P6). */
    public static class UndeclarableFieldException extends ShTeachingException {

        public UndeclarableFieldException(String type, String name) {
            super(ShProblem.badRequest("undeclarable-field",
                            "'" + name + "' is not a usable field name")
                    .withDetail("A field name becomes a JSON key, a form control's name and a "
                            + "Handlebars path, so it must start with a letter and hold only "
                            + "letters, digits and underscores (63 max).")
                    .withFix("Send a name matching [A-Za-z][A-Za-z0-9_]* for " + type + ".")
                    .withExample("{\"op\":\"render.provision\",\"data\":{\"fields\":{\""
                            + type + "\":[\"heroHeading\"]}}}"));
        }
    }

    /** A 400: nothing here may append a field to that type (P6). */
    public static class UndeclarableTypeException extends ShTeachingException {

        public UndeclarableTypeException(String type, boolean system, List<String> allowed) {
            super(ShProblem.badRequest("undeclarable-type",
                            system
                                    ? "'" + type + "' is a system post-type"
                                    : "No post-type named '" + type + "'")
                    .withDetail(system
                            ? "A system type is this code's model — adding a field to it would put "
                                    + "a name in it that nothing here writes or reads."
                            : "`data.fields` appends to a type that already exists. Create it "
                                    + "first — with `data.types` when it is an SH95 vocabulary "
                                    + "type, or PUT /api/v2/post-type/" + type + " when it is "
                                    + "yours.")
                    .withAllowed(allowed)
                    .withDidYouMeanFrom(type)
                    .withFix(system
                            ? "Declare the field on a type you own."
                            : "Add \"" + type + "\" to `data.types`, or create the type first.")
                    .withExample("{\"op\":\"render.provision\",\"data\":{\"types\":[\"Page\"],"
                            + "\"fields\":{\"Page\":[\"heroHeading\"]}}}"));
        }
    }

    /**
     * The noop sentence, which has to say what it looked at (SH607).
     *
     * <p>"the render post-types exist" is a true answer to a question the caller may not have asked:
     * a run that named {@code data.types} wants to know about <em>those</em>, and a report that
     * mentions only the render five reads as though the list was ignored — which is exactly what it
     * used to be. It also stopped saying "all three": there have been five since SH385/SH389, and a
     * count in prose is a thing that goes stale silently.</p>
     */
    private static String nothingToDo(boolean alsoReconcile, List<String> vocabulary) {
        String render = alsoReconcile
                ? "the render post-types exist and none is missing a field"
                : "the render post-types exist";
        return vocabulary.isEmpty()
                ? render
                : render + ", and so does " + String.join(", ", vocabulary);
    }

    private static String planned(List<String> missing, List<String> repairs,
                                  List<String> vocabulary, List<String> declarations) {
        List<String> parts = new ArrayList<>();
        if (!missing.isEmpty()) {
            parts.add("would create " + String.join(", ", missing));
        }
        if (!vocabulary.isEmpty()) {
            parts.add("would create " + String.join(", ", vocabulary));
        }
        if (!repairs.isEmpty()) {
            parts.add("would add " + String.join("; ", repairs));
        }
        // SH871 — named and not counted, for the reason the repairs above are: a plan an agent
        // diffs between runs has to say which field appeared, and "40 field(s)" is the report the
        // task was filed about.
        if (!declarations.isEmpty()) {
            parts.add("would declare " + String.join(", ", declarations));
        }
        return String.join("; ", parts);
    }

    private static String applied(List<String> missing, List<String> repairs,
                                  List<String> vocabulary, List<String> declarations) {
        List<String> parts = new ArrayList<>();
        if (!missing.isEmpty()) {
            parts.add("created " + String.join(", ", missing));
        }
        if (!vocabulary.isEmpty()) {
            parts.add("created " + String.join(", ", vocabulary));
        }
        if (!repairs.isEmpty()) {
            parts.add("added " + String.join("; ", repairs));
        }
        if (!declarations.isEmpty()) {
            parts.add("declared " + String.join(", ", declarations));
        }
        return parts.isEmpty() ? "nothing to do" : String.join("; ", parts);
    }

    /**
     * A string-array field of an op's {@code data}, empty when absent (SH607).
     *
     * <p>Non-string entries are dropped rather than refused, for the reason {@link #bindingsOf}
     * gives: a malformed value is simply not a name, and the refusal that matters — a name this
     * instance does not ship — is the catalogue's to make, with the {@code allowed} list attached.</p>
     */
    private static List<String> strings(JsonNode data, String field) {
        if (data == null || !data.isObject()) {
            return List.of();
        }
        JsonNode node = data.get(field);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode entry : node) {
            if (entry != null && entry.isString() && !entry.stringValue().isBlank()) {
                values.add(entry.stringValue().trim());
            }
        }
        return List.copyOf(values);
    }

    /** A boolean field of an op's {@code data}, false when absent or not a boolean. */
    private static boolean flag(JsonNode data, String field) {
        if (data == null || !data.isObject()) {
            return false;
        }
        JsonNode node = data.get(field);
        return node != null && node.isBoolean() && node.booleanValue();
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
        // SH453 — the bindings the create branch used to drop. Read before the dry-run return, so
        // a plan and a run describe the same op: a plan derived from a second reading of the rule
        // is a plan that can disagree with the run it predicts.
        Map<String, String> bindings = bindingsOf(op.data());
        // SH591 — read on the create branch and on no other, which is the guarantee the sweep rests
        // on. `siteFill` never sees it: a flag that could be raised on an existing site would make
        // `shio site rm --scratch` a way to bulk-delete a site somebody chose to keep.
        boolean scratch = flag(op.data(), "scratch");
        // SH1056 — validated before the dry-run return, for this class's own rule: a plan derived
        // from a second reading of the rule is a plan that can disagree with the run it predicts.
        // A policy naming a locale this instance does not know is a 400 at plan time too.
        Optional<String> locales = shSiteLocaleService.declare(
                op.data() == null ? null : op.data().get(ShSiteLocales.FIELD));
        // SH1283 — a new site has only the bindings this op brings, so its page type names one.
        String pageType = text(op.data(), ShSiteWriteService.PAGE_TYPE_FIELD);
        ShSiteWriteService.requirePageTypeBound(pageType, bindings.keySet());
        String detail = createDetail(bindings) + (scratch ? ", declared SCRATCH — disposable, and "
                + "`shio site rm --scratch` may remove it in one confirmation" : "")
                + (locales.isPresent() ? ", with a declared locale policy" : "");
        if (session.dryRun) {
            return session.change(op, address, Change.CREATE, detail, null);
        }
        shTenantQuotaService.checkCanCreateSite();
        ShSite payload = new ShSite();
        payload.setName(name);
        payload.setDescription(text(op.data(), "description"));
        payload.setUrl(text(op.data(), "url"));
        payload.setFurl(text(op.data(), "furl"));
        payload.setLocales(locales.orElse(null));
        payload.setPageType(pageType == null || pageType.isBlank() ? null : pageType.trim());
        payload.setScratch(scratch ? Boolean.TRUE : null);
        String members = membersLogin(op.data());
        payload.setMembersLogin(members == null ? null : Boolean.valueOf(members));
        ShSite created = shSiteWriteService.createBlank(payload, bindings, session.actor);
        // SH413 — a brand-new site has nothing published, so this is almost always a no-op line.
        // Read anyway, because refusing the key on the create branch would make `data.reindex`
        // mean two different things depending on whether the site already existed.
        Reindexed indexed = flag(op.data(), REINDEX_KEY)
                ? reindex(op, session, name, created) : new Reindexed("", false);
        return session.change(op, address, Change.CREATE, detail + indexed.detail(),
                created.getId());
    }

    /**
     * Removes a site and everything in it (SH463).
     *
     * <h2>The asymmetry this ends</h2>
     *
     * <p>{@code site.upsert} shipped in SH170 for a reason that applies twice over here: creating a
     * site over {@code POST /api/v2/site} needs a browser session, so the CLI's own provisioning
     * walked the CSRF dance by hand. Deleting one was left, and the cost turned up as maintenance
     * rather than as a feature request - clearing sixteen scratch sites took the same dance, and
     * SH448's conformance teardown had hand-rolled a session for it and recorded the asymmetry in a
     * code comment ("not this task's to fix"), filed nowhere. A suite that seeds a site per scenario
     * and cannot remove one after a <em>failed</em> run leaves debris on every red run.</p>
     *
     * <h2>Destructive, and the widest gesture on the surface</h2>
     *
     * <p>It carries the SH108 confirm token that {@code folder.delete} and {@code post.delete}
     * carry. Its plan line used to say something theirs could not — <b>there is no trash behind
     * it</b> — because a site delete purged (SH454) and an agent that believed it was restorable
     * would have offered the human an undo that did not exist.</p>
     *
     * <p><b>SH648 gave it one</b>, so the line now names the op that reverses it, and the P7 failure
     * has an exact mirror: an agent that fails to offer the undo that <em>does</em> exist is wrong
     * in the same way and from the other side. Which the caller believes is decided by that one
     * sentence, which is why it is written from the same {@code purge} flag the write branches on
     * rather than from a second reading of the request.</p>
     *
     * <h2>Unscoped credentials only</h2>
     *
     * <p>The same check {@code site.upsert}'s create branch makes, for the sharper reason: a
     * credential scoped to a site list must not be able to remove a member of it. Scoping is an
     * allow-list of what may be <em>read and written</em>, never a grant to destroy the container.</p>
     */
    private Change siteDelete(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.SITE);
        String name = address.site();
        Optional<ShSite> existing = siteAccess.find(name);
        if (existing.isEmpty()) {
            // A delete of something already gone is a noop, which is what makes a teardown safe to
            // run twice - and a teardown that failed once is exactly when it gets run again.
            return session.change(op, address, Change.NOOP, "already absent", null);
        }
        requireUnscopedCredential(name);
        String id = existing.get().getId();
        // SH648 — which of the two gestures this is, decided before the plan line is written so a
        // dry run and the write it predicts cannot disagree about whether there is an undo.
        boolean purge = flag(op.data(), PURGE_KEY);
        if (!session.dryRun) {
            if (purge) {
                shSiteWriteService.purge(id, session.actor);
            } else {
                shSiteWriteService.delete(id, session.actor);
            }
        }
        return session.change(op, address, Change.DELETE, purge
                ? "with every folder and post in it, permanently - `data.purge` declines the trash, "
                        + "so this one has no undo"
                : "with every folder and post in it, to the trash - site.restore brings it back", id);
    }

    /**
     * Brings a trashed site back, with the content that went into the trash with it (SH648).
     *
     * <h2>Why it is addressed by name and not by id</h2>
     *
     * <p>{@code post.restore} and {@code folder.restore} take {@code id:}, because a trashed post
     * has no reachable friendly URL to be named by (SH222). A site is the one level where that is
     * not true: its <b>name is unique and it keeps holding it while it sleeps</b> — that is the
     * whole reason {@code ShSiteNameHeldByTrashException} exists — so {@code site:<name>} still
     * resolves it unambiguously, and it is the form the caller already has in their hand. Which
     * makes this the one restore whose address is the same one the delete took: an agent that
     * trashed a site can undo it without having kept an id.</p>
     *
     * <h2>Unscoped credentials only</h2>
     *
     * <p>The same check {@code site.delete} makes, and the mirror of SH696's argument one level
     * down: a credential scoped to a site list must not be able to add a member to it by
     * resurrection any more than it may remove one. Non-goal: "the agent path is not a privilege
     * escalation".</p>
     */
    private Change siteRestore(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.SITE);
        String name = address.site();
        Optional<ShSite> trashed = shSiteWriteService.nameHeldByTrash(name);
        if (trashed.isEmpty()) {
            return session.change(op, address, Change.NOOP, "not in the trash", null);
        }
        requireUnscopedCredential(name);
        String id = trashed.get().getId();
        // Asked on both branches, so a dry run refuses the furl conflict the write would refuse
        // (SH690) instead of planning a restore that 409s when it runs.
        shSiteWriteService.requireRestorableFurl(trashed.get());
        if (!session.dryRun) {
            shSiteWriteService.restore(id, session.actor);
        }
        return session.change(op, address, Change.UPDATE,
                "from trash, with every folder and post that went in with it", id);
    }

    /**
     * What a create says it did (SH453).
     *
     * <p>"a blank site" was true and became the reason the defect was invisible: the op reported
     * the same sentence whether or not it had been handed bindings, so the one caller who could
     * have noticed — the one who sent them — was told nothing was missing. The bindings are named
     * in the fill branch's own vocabulary ({@code postTypeLayout.<post-type>}), because one op
     * describing one field two ways is a report a reader has to translate.</p>
     */
    private static String createDetail(Map<String, String> bindings) {
        if (bindings.isEmpty()) {
            return "a blank site";
        }
        return "a blank site, binding " + bindings.keySet().stream()
                .map(key -> ShSiteWriteService.LAYOUT_FIELD + "." + key)
                .collect(java.util.stream.Collectors.joining(", "));
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
            String value = ShSiteWriteService.MEMBERS_LOGIN_FIELD.equals(field)
                    ? membersLogin(op.data()) : text(op.data(), field);
            if (value != null && !value.isBlank()) {
                requested.put(field, value);
            }
        }
        // SH1056 — the one fillable field that arrives as an object rather than a string, so it is
        // validated here and handed on as the bytes to store. Everything after this treats it like
        // `url`: filled when the site has no policy, kept and reported when it has one.
        shSiteLocaleService.declare(op.data() == null ? null : op.data().get(ShSiteLocales.FIELD))
                .ifPresent(json -> requested.put(ShSiteLocales.FIELD, json));
        // SH328 — the one edge that makes a site render, and the only op that can write it.
        Map<String, String> bindings = bindingsOf(op.data());
        // SH1283 — against what the site binds now plus what this op binds, before any write.
        if (requested.containsKey(ShSiteWriteService.PAGE_TYPE_FIELD)) {
            java.util.Set<String> bound = shSiteWriteService.boundTypes(site);
            bound.addAll(bindings.keySet());
            ShSiteWriteService.requirePageTypeBound(requested.get(ShSiteWriteService.PAGE_TYPE_FIELD), bound);
        }
        ShSiteWriteService.ShSiteFill fill = session.dryRun
                ? shSiteWriteService.planFill(site, requested, bindings)
                : shSiteWriteService.fillEmpty(site, requested, bindings, session.actor);
        if (!fill.changed()) {
            // Naming what was kept matters: without it, "I asked for a new url and got noop"
            // reads as the op having ignored the request rather than having protected a value
            // somebody chose.
            String detail = fill.kept().isEmpty() ? null
                    : "kept the existing " + String.join(", ", fill.kept())
                            + " — an upsert fills what is empty and never replaces a value a "
                            + "human set; change it in the console";
            // SH413 — the branch this key exists for: a site that already has content, which is
            // exactly the operator SH84's publish-time seam left with an empty index. A reindex
            // makes the op a real change, so it stops being a NOOP when one happened.
            Reindexed indexed = flag(op.data(), REINDEX_KEY)
                    ? reindex(op, session, address.site(), site) : new Reindexed("", false);
            // A reindex that found nothing to send leaves this a noop, because nothing moved. One
            // that sent is an update, even if every send was refused — see `Reindexed`.
            return indexed.changed()
                    ? session.change(op, address, Change.UPDATE,
                            (detail == null ? "no fields to fill" : detail) + indexed.detail(),
                            site.getId())
                    : session.change(op, address, Change.NOOP,
                            (detail == null ? null : detail) == null && !indexed.detail().isEmpty()
                                    ? indexed.detail().substring(2)
                                    : detail + indexed.detail(),
                            site.getId());
        }
        String detail = "filled " + String.join(", ", fill.filled())
                + (fill.kept().isEmpty() ? ""
                        : "; kept the existing " + String.join(", ", fill.kept()));
        Reindexed indexed = flag(op.data(), REINDEX_KEY)
                ? reindex(op, session, address.site(), site) : new Reindexed("", false);
        return session.change(op, address, Change.UPDATE, detail + indexed.detail(), site.getId());
    }

    /**
     * SH328 — {@code data.postTypeLayout} as post-type → layout title.
     *
     * <p>String values only, matching what the renderer reads: a non-string is the SH193 array
     * form, and accepting it here would write a shape {@code ShPageRenderService} skips, which is
     * a binding that looks written and renders nothing. A malformed value is simply not a
     * binding — {@code unused-layout} still reports the layout nobody bound, so the defect stays
     * visible where a caller already looks.</p>
     */
    private static Map<String, String> bindingsOf(JsonNode data) {
        if (data == null || !data.isObject()) {
            return Map.of();
        }
        JsonNode node = data.get(ShSiteWriteService.LAYOUT_FIELD);
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, String> bindings = new LinkedHashMap<>();
        for (String key : node.propertyNames()) {
            JsonNode value = node.get(key);
            if (value != null && value.isString() && !value.stringValue().isBlank()) {
                bindings.put(key, value.stringValue());
            }
        }
        return bindings;
    }

    /**
     * SH1299 — {@code data.membersLogin} as "true"/"false", or null when absent or not a boolean, so
     * it rides {@link ShSiteWriteService#FILLABLE}'s fill-or-keep rule like every other site field.
     */
    private static String membersLogin(JsonNode data) {
        JsonNode value = data == null || !data.isObject() ? null : data.get(ShSiteWriteService.MEMBERS_LOGIN_FIELD);
        return value == null || !value.isBoolean() ? null : Boolean.toString(value.booleanValue());
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

    /**
     * Duplicates a section and everything under it, at an address the caller names (SH244).
     *
     * <h2>{@code to} is the copy's whole name chain, exactly as it is for a move</h2>
     *
     * <p>Not "the folder to copy into". A folder is addressed by its name chain (SH79), so the
     * chain is what says both where the copy lands and what it is called — one field, and the
     * caller knows the address of the thing it just made without a second read (P2). The console
     * numbers its copies instead ({@code "archive (2)"}), which is right for somebody who can
     * look at the result and wrong for a caller that must write the next op against it.</p>
     *
     * <p><b>An occupied target is refused rather than numbered</b>, for the same reason. Numbering
     * is how a re-run of a batch quietly produces a third copy nobody asked for, and a refusal
     * naming the address is the only answer that stays true whichever way the caller meant it.</p>
     *
     * <h2>Crossing sites is allowed here, and refused for a move</h2>
     *
     * <p>{@code folder.move} refuses it because re-parenting across a boundary takes the original
     * with it — separate trees, separate ACLs, separate tenants — and its own teaching error tells
     * the caller to rebuild the structure and move the posts across <em>one at a time</em>. That
     * advice is what this op replaces: a copy detaches nothing, the source is untouched, and both
     * sites are checked against the SH2 allow-list on the way in, so the objection a move has does
     * not exist here. It is also the case a whole section can least afford to do by hand.</p>
     *
     * <h2>What it inherits, and the one bound it cannot state exactly</h2>
     *
     * <p>The naming ({@code SH202}) and the cycle guard ({@code SH203}) are the shared writer's,
     * so the console and this op cannot disagree about them, and the copies are drafts. The tenant
     * quota is <b>exact</b> since SH750: this asked the per-one question while the shared kernel
     * had only that to offer, so a tenant one post below its ceiling could copy a section of fifty
     * and land fifty over it. {@code viglet-core} 2026.3.11 added {@code check(resource, delta)},
     * so the op now charges {@code scope.posts()} — the number it is about to create, which it has
     * already walked for the detail line.</p>
     */
    private Change folderCopy(ShAgentBatch.Op op, Session session) {
        ShAddress from = writeAddress(op, ShAddress.Kind.FOLDER);
        ShSite site = requireSite(from.site(), session);
        ShAddress to = folderCopyTarget(op, from);
        ShSite destSite = to.site().equalsIgnoreCase(from.site())
                ? site
                : requireSite(to.site(), session);

        ShFolder source = findFolder(site, from.path()).orElseThrow(() ->
                new ShAddressResolver.UnresolvedAddressException(from, List.of()));

        List<String> target = segments(to.path());
        if (target.isEmpty() || findFolder(destSite, to.path()).isPresent()) {
            // The empty chain is the site root, which already exists — so it is occupied in the
            // only sense that matters, and the refusal that names the address is the honest one.
            throw new CopyTargetOccupiedException(from, to);
        }
        String newName = target.get(target.size() - 1);
        String parentPath = "/" + String.join("/", target.subList(0, target.size() - 1));
        // A copy never creates the branch it lands on, for folder.move's reason: a typo in the
        // path is reported instead of quietly building a new section of the tree.
        ShFolder destParent = findFolder(destSite, parentPath).orElseThrow(() ->
                new ShAddressResolver.UnresolvedAddressException(
                        ShAddress.folder(destSite.getName(), parentPath), List.of()));

        ShObjectWriteService.CopyScope scope = shObjectWriteService.scopeOf(source.getId());
        String detail = scope.folders() + " folder(s) and " + scope.posts()
                + " post(s), every copy a draft";

        if (session.dryRun) {
            // The plan reports the refusals a real run would make, or the plan lied (SH82).
            shFolderWriteService.checkCopyable(source.getId(), destParent);
            session.planFolder(destSite, to.path());
            return session.change(op, to, Change.CREATE, detail, null);
        }
        shContentAclService.requireWrite(source.getId());
        shContentAclService.requireWrite(destParent.getId());
        // SH750 — charged for what this copy actually creates, not for one. `scope.posts()` is
        // already walked for the detail line above, so the exact number is in hand and the delta
        // check makes it answerable; looping the per-one check would have asked one question N
        // times against a count the copy has not written yet, and refused nothing.
        shTenantQuotaService.checkCanCreatePosts(scope.posts());
        ShFolder copy = shObjectWriteService.copyFolderAs(source.getId(), destParent, newName, true)
                .orElseThrow(() -> new ShAddressResolver.UnresolvedAddressException(from, List.of()));
        return session.change(op, to, Change.CREATE, detail, copy.getId());
    }

    /**
     * Where a folder copy is going. A full {@code folder:} address or a bare path, defaulting the
     * site to the source's — which is {@code folderMoveTarget}'s shape, deliberately: two verbs
     * that read the same field differently is how a caller learns one and gets the other wrong.
     */
    private ShAddress folderCopyTarget(ShAgentBatch.Op op, ShAddress from) {
        String raw = op.to() == null ? null : op.to().trim();
        if (raw == null || raw.isBlank()) {
            throw new MissingOpFieldException("to", op.op(), from, "folder:Blog/archive/2026",
                    "A folder copy needs the copy's own address in `to` — the full name chain, "
                            + "since that is what addresses a folder (SH79). Its last segment is "
                            + "what the copy is called.");
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
        // SH733 — the same modifier site.delete carries (SH648). `purge` trashes first and then
        // removes, because ShFolderWriteService.purge operates ON the trash and refuses a live
        // folder; the two halves are one gesture when the caller has declined the undo.
        boolean purge = flag(op.data(), PURGE_KEY);
        if (!session.dryRun) {
            shContentAclService.requireWrite(id);
            shFolderWriteService.delete(id, session.actor);
            if (purge) {
                shFolderWriteService.purge(id, session.actor);
            }
        }
        // Folder deletion cascades to every post beneath it (ShFolderWriteService), so
        // the plan says so rather than letting the caller discover it afterwards (P7).
        // Since SH200 the cascade goes through the post writer, so the whole subtree lands
        // in SH55's trash — said out loud for the same reason post.delete says it: an agent
        // that believes it destroyed a section will not offer the human the undo.
        return session.change(op, address, Change.DELETE, purge
                ? "with all content beneath it, permanently - `data.purge` declines the trash, so "
                        + "the file bytes are released and the DELETE webhook fires"
                : "with all content beneath it, to trash (restorable)", id);
    }

    // --- post ops ---

    /**
     * SH721 — {@code expect} means <i>only if unchanged</i>, and this is where it is enforced.
     *
     * <p>Absent, nothing happens and the write stays last-write-wins, which is what keeps this
     * surface cheap: the guarantee is opt-in because most writes are the only writer. Present, the
     * post's current {@link ShAgentPostDigest} has to equal what the caller read, and a post that
     * is no longer there fails it as well — {@code null} is not "unchanged", it is a different
     * state, and it is the one a caller is least likely to have intended.</p>
     */
    private void requireExpectedState(ShAgentBatch.Op op, ShAddress address, ShPostUnified current) {
        String expected = op.expect();
        if (expected == null || expected.isBlank()) {
            return;
        }
        String actual = ShAgentPostDigest.of(current);
        if (!expected.equals(actual)) {
            throw new ShStaleExpectationException(address.toString(), expected, actual);
        }
    }

    private Change postUpsert(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.POST);
        ShSite site = requireSite(address.site(), session);
        Optional<ShPostUnified> existing = findPost(site, address.path());
        String declaredFurl = declaredFurl(op, address);

        // SH721 — asked before anything is decided, and on BOTH branches of `dryRun`: a plan that
        // said "update" about a post somebody has since rewritten is the plan P9 exists to stop
        // being trusted. The absent case refuses too, because a create is not the op the caller
        // planned: they read a post, and re-creating what somebody deleted is its own surprise.
        requireExpectedState(op, address, existing.orElse(null));

        if (existing.isPresent()) {
            ShPostUnified post = existing.get();
            // SH767 — before `sameData`, which is what makes a re-send of the address form a NOOP
            // rather than a write of the same bytes under a different spelling.
            ShAgentBatch.Op resolved = withResolvedRelations(op, address, post.getShPostType());
            // SH467 — the declared spelling is honoured on *create* and nowhere else, exactly as
            // `locale` and `translationOf` are, and here the reason is sharper than theirs: a
            // post's file path in `shio/content/**` **is** its URL, so `/x` and `/x/` are one
            // filename and the projection structurally cannot carry the slash. An update that
            // re-spelled would therefore be un-done by the next `shio push` — the collapsed URL
            // that a pull wrote would arrive as a re-spelling back. Changing an existing post's
            // canonical URL is `post.move`'s gesture, not a routine apply's side effect (P7).
            if (resolved.data() == null || sameData(post, resolved.data())) {
                return session.change(op, address, Change.NOOP, null, post.getId());
            }
            TypeRef updating = TypeRef.of(post.getShPostType(), session.declaredFieldsOf(
                    post.getShPostType().getName()));
            String undeclared = note(note(
                    note(checkDeclaredFields(resolved, address, session, updating,
                            readTree(post.getData())),
                            // SH1015 — before the write, so a blocks document this instance cannot
                            // store never becomes a blank region on somebody's page.
                            checkBlocks(resolved, address, session, updating)),
                    checkTemplates(resolved, address, session, post.getShPostType().getName())),
                    note(planTemplateTitle(resolved, session, post.getShPostType(), site,
                            post.getId()),
                            noteSanitized(resolved, post.getShPostType())));
            if (!session.dryRun) {
                shContentAclService.requireWrite(post.getId());
                // SH1015 — canonicalized after the checks accepted it, so the row holds the one
                // spelling and no reader has to normalize what it reads.
                shPostUnifiedWriteService.update(post.getId(),
                        new UpdateRequest(withCanonicalBlocks(resolved, updating).data(),
                                ShPostState.DRAFT, session.actor));
            }
            return session.change(op, address, Change.UPDATE, undeclared, post.getId());
        }

        // Only reachable in a dry run — a real run would have found the row above.
        if (session.alreadyPlanned(address)) {
            return session.change(op, address, Change.NOOP, "created earlier in this batch", null);
        }

        TypeRef postType = requirePostType(op, address, session);
        // SH767 — the same resolution on create. `livePostType` is null for a type this batch is
        // about to provision, and then there is nothing to read the relation declarations from: the
        // value is stored as sent, which is what it did before, and `verify` still reports it.
        ShAgentBatch.Op created0 = withResolvedRelations(op, address, livePostType(postType));
        String undeclared = note(note(
                note(checkDeclaredFields(created0, address, session, postType, null),
                        checkBlocks(created0, address, session, postType)),
                checkTemplates(created0, address, session, postType.name())),
                note(planTemplateTitle(created0, session, livePostType(postType), site, null),
                        noteSanitized(created0, livePostType(postType))));
        ShFolder folder = requireTargetFolder(op, site, address, session);
        session.planPost(address);
        if (session.dryRun || folder == null) {
            return session.change(op, address, Change.CREATE, undeclared, null);
        }
        shContentAclService.requireWrite(folder.getId());
        shTenantQuotaService.checkCanCreatePost();
        ShPostUnified created = shPostUnifiedWriteService.create(new CreateRequest(
                // SH1015 — the same canonicalization on create, or a post's first write is
                // the one that stores an uncanonical document.
                folder.getId(), postType.id(),
                withCanonicalBlocks(created0, postType).data(), ShPostState.DRAFT,
                // SH348 — the locale is honoured on create and nowhere else. The writer defaults a
                // blank one, so absent still means DEFAULT_LOCALE and nothing changes for a
                // monolingual tree.
                session.actor, op.locale(), translationGroupFor(op, address, session)));
        // The address *is* the identity here, so the furl is written from it rather
        // than derived from a title — two posts with the same headline must still be
        // two addresses, and the next run must find this one again. SH467 keeps the
        // identity and stores the *declared* spelling of it, which the address drops.
        shPostUnifiedWriteService.setFurl(created.getId(), declaredFurl);
        return session.change(op, address, Change.CREATE, null, created.getId());
    }

    /**
     * SH767 — an address in a relation field is resolved to the URL the relation stores.
     *
     * <h2>What was being stored</h2>
     *
     * <p>{@code Page.sections} holds site-relative friendly URLs, and SH79 makes
     * {@code post:<site>/<url>} the currency of every other part of this protocol. So writing the
     * relation the way the rest of the surface is written:</p>
     *
     * <pre>{@code "sections": ["post:VigR2/sections/vb-top", …]}</pre>
     *
     * <p>was accepted, reported {@code changed: true}, and stored <b>verbatim</b>. The renderer
     * resolved none of them and the page served with no sections at all. Measured on a replica: the
     * post exists, its state is PUBLISHED, the route answers 200 — <b>it looks authored</b>, and the
     * only symptom is a curator opening the page and finding the content gone.</p>
     *
     * <h2>Resolve rather than refuse, because that is what the op already does one field over</h2>
     *
     * <p>An op's {@code folder} takes either form and has since it shipped:</p>
     *
     * <pre>{@code op.folder().startsWith("folder:") ? ShAddress.parse(op.folder()).path() : …}</pre>
     *
     * <p>A relation is the same question about a different field, so it gets the same answer.
     * Purely syntactic — {@code post:<site>/<url>} carries the URL, no lookup required — which is
     * what makes it safe inside a batch that creates the target three ops later, and idempotent: a
     * second write of the address form normalizes to the same URL and is a NOOP (P5).</p>
     *
     * <h2>And what is refused, because no normalization could make it mean this</h2>
     *
     * <p>Another site's post, a {@code folder:} or {@code site:}, and {@code id:} — the last
     * because a relation stores URLs and SH79's whole point is that no address a human writes
     * carries a UUID. Each is a 400 naming the form the field wants (P6). A value with no scheme is
     * left exactly as sent: it is a URL, and whether it resolves is {@code verify}'s question.</p>
     */
    /**
     * SH1015 — a Blocks field is stored in its one spelling.
     *
     * <p>P5 is only worth something at the boundary where content enters: canonicalizing on the way
     * out would leave the stored bytes varying while every read normalized them, so two pulls of an
     * unchanged post could still differ. Normalizing here means the row holds the canonical form and
     * every reader — the projection, the digest, the merge's base fingerprint — is looking at the
     * same bytes the writer meant.</p>
     *
     * <p>It runs after {@link #checkBlocks}, so nothing is canonicalized that was not first accepted:
     * sorting the keys of a document this instance would refuse is work done for a write that is
     * about to fail.</p>
     */
    private ShAgentBatch.Op withCanonicalBlocks(ShAgentBatch.Op op, TypeRef declaredIn) {
        JsonNode data = op.data();
        if (data == null || !data.isObject() || declaredIn == null
                || declaredIn.blockFields().isEmpty()) {
            return op;
        }
        tools.jackson.databind.node.ObjectNode rewritten = null;
        for (String field : declaredIn.blockFields()) {
            JsonNode value = data.get(field);
            if (value == null || !value.isArray()) {
                continue;
            }
            JsonNode canonical = ShBlocks.canonical(value);
            if (canonical.toString().equals(value.toString())) {
                continue;
            }
            if (rewritten == null) {
                rewritten = objectMapper.createObjectNode().setAll(
                        (tools.jackson.databind.node.ObjectNode) data);
            }
            rewritten.set(field, canonical);
        }
        if (rewritten == null) {
            return op;
        }
        return new ShAgentBatch.Op(op.op(), op.address(), op.type(), op.folder(), op.to(),
                rewritten, op.when(), op.locale(), op.translationOf(), op.url(), op.expect());
    }

    private ShAgentBatch.Op withResolvedRelations(ShAgentBatch.Op op, ShAddress address,
                                                  ShPostType postType) {
        JsonNode data = op.data();
        if (data == null || !data.isObject() || postType == null) {
            return op;
        }
        List<ShPostTypeAttr> attrs = postType.getShPostTypeAttrs() == null
                ? List.of() : List.copyOf(postType.getShPostTypeAttrs());
        tools.jackson.databind.node.ObjectNode rewritten = null;
        for (ShPostTypeAttr attr : attrs) {
            String field = attr.getName();
            if (field == null || field.isBlank()
                    || !ShPostReferenceFields.referencesPosts(attr, objectMapper)) {
                continue;
            }
            JsonNode value = data.get(field);
            if (value == null || !value.isArray()) {
                continue;
            }
            tools.jackson.databind.node.ArrayNode resolved = objectMapper.createArrayNode();
            boolean changed = false;
            for (JsonNode entry : value) {
                String text = entry.isString() ? entry.asString().trim() : null;
                String url = text == null ? null : relationUrl(text, address, field);
                if (url == null) {
                    resolved.add(entry);
                    continue;
                }
                changed |= !url.equals(text);
                resolved.add(url);
            }
            if (!changed) {
                continue;
            }
            if (rewritten == null) {
                rewritten = objectMapper.createObjectNode().setAll(
                        (tools.jackson.databind.node.ObjectNode) data);
            }
            rewritten.set(field, resolved);
        }
        if (rewritten == null) {
            return op;
        }
        return new ShAgentBatch.Op(op.op(), op.address(), op.type(), op.folder(), op.to(),
                rewritten, op.when(), op.locale(), op.translationOf(), op.url(), op.expect());
    }

    /**
     * One relation value as the URL the field stores, or {@code null} to leave it alone.
     *
     * @throws RelationAddressException when the value is an address no URL in this site can mean
     */
    private String relationUrl(String value, ShAddress address, String field) {
        int colon = value.indexOf(':');
        if (colon <= 0) {
            // No scheme: it is already a URL. Whether it resolves is `verify`'s question, not this
            // one — refusing here would make a page unwritable until every section exists, which is
            // the opposite of how a conversion builds one.
            return null;
        }
        String scheme = value.substring(0, colon).toLowerCase(java.util.Locale.ROOT);
        if (ShAddress.Kind.schemes().stream().noneMatch(scheme::equals)) {
            // Not one of ours. A Multi Select value is free text, and guessing that `note:x` was
            // meant as an address is how a lint starts reporting somebody's tag.
            return null;
        }
        ShAddress parsed = ShAddress.parse(value);
        if (parsed.kind() != ShAddress.Kind.POST) {
            throw new RelationAddressException(field, value, address,
                    "`" + scheme + ":` does not name a post"
                            + (parsed.kind() == ShAddress.Kind.ID
                                    // SH79's rule, which is the reason this one is refused rather
                                    // than resolved: an id would resolve perfectly well and would
                                    // put a UUID in a content file, where a human has to read it.
                                    ? ", and a relation stores URLs so that no address anybody "
                                            + "writes carries a UUID (SH79)." : "."));
        }
        if (!parsed.site().equalsIgnoreCase(address.site())) {
            throw new RelationAddressException(field, value, address,
                    "'" + parsed.site() + "' is not the site this post is in ('"
                            + address.site() + "'), and a relation resolves inside one site.");
        }
        return parsed.path();
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
        // SH872 — a key annotated by the markup THIS WRITE carries is declared by that markup, the
        // storage SH868 proposed and SH870 renders. Read off the op's own data rather than the
        // stored row: a `shio push` of a content file whose page a curator has edited sends both
        // halves at once, and grandfathering would only cover the second push.
        Set<String> annotated = ShAnnotatedFields.of(data);
        List<String> unknown = new ArrayList<>();
        for (String key : data.propertyNames()) {
            boolean grandfathered = stored != null && stored.has(key);
            if (!allowed.contains(key) && !grandfathered && !annotated.contains(key)) {
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

    /**
     * SH192 — refuses a template that will not compile, at the moment it is written.
     *
     * <h2>Why here and not at view time</h2>
     *
     * <p>Nothing validated a layout or a region, so an unclosed {@code {{#each}}} or a call to
     * a helper that does not exist was accepted, stored, published — and discovered as a blank
     * page. Every iteration then cost a guess, which is the loop this block exists to make
     * cheap. A renderer that only reports failure when somebody looks has moved the error to
     * the least useful moment.</p>
     *
     * <p>It follows {@link #checkDeclaredFields} exactly, and for the same reason: a real run
     * <b>refuses</b>, and a plan <b>reports</b>. A plan that stopped on the first broken
     * template would hide the rest of the report, and the report is the reason to plan.</p>
     *
     * @param typeName the post-type being written; only the render types hold templates, so
     *                 everything else short-circuits and pays nothing
     */
    private String checkTemplates(ShAgentBatch.Op op, ShAddress address, Session session,
                                  String typeName) {
        JsonNode data = op.data();
        if (data == null || !data.isObject() || typeName == null) {
            return null;
        }
        List<String> problems = new ArrayList<>();
        for (String field : data.propertyNames()) {
            if (!shTemplateValidator.isTemplateField(typeName, field)) {
                continue;
            }
            JsonNode value = data.get(field);
            if (value == null || !value.isString()) {
                continue;
            }
            for (ShTemplateProblem problem : shTemplateValidator.validate(value.asString())) {
                problems.add(field + ": " + problem.describe());
            }
        }
        if (problems.isEmpty()) {
            return null;
        }
        if (!session.dryRun) {
            throw new InvalidTemplateException(address, typeName, problems);
        }
        return "invalid template — a real run will refuse this write: "
                + String.join("; ", problems);
    }

    /**
     * SH277 — the collision note a plan owes, for the write a real run refuses.
     *
     * <p>The refusal itself lives on {@code ShPostUnifiedWriteService}, so it applies to the
     * console and the CDA too; this only covers the half a dry run would otherwise answer green,
     * and it deliberately asks the *same* component rather than repeating the query.</p>
     */
    private String planTemplateTitle(ShAgentBatch.Op op, Session session, ShPostType postType,
                                     ShSite site, String selfId) {
        if (!session.dryRun || site == null) {
            return null;
        }
        return shTemplateTitles.planCollision(postType, op.data(), site.getId(), selfId);
    }

    /** The live entity behind a {@link TypeRef}, or {@code null} for one a plan would create. */
    private ShPostType livePostType(TypeRef ref) {
        return ref == null || ref.id() == null ? null : shPostTypeRepository.findById(ref.id())
                .orElse(null);
    }

    /** Joins two optional change notes without producing a stray separator. */
    /**
     * What the write path would take out of this op's rich text (SH956).
     *
     * <p>SH956 put an allow-list on every write. Silently: an agent whose markup changed shape
     * learns nothing from a 200 and finds out on the page. A plan is where it can be told
     * <em>before</em> the write, which is the whole point of having one — so this runs the same
     * sanitizer the write path runs and reports what it would refuse.</p>
     *
     * <p>The same sanitizer, not a second reader of the same rule: two answers to "what is allowed
     * here" is how a plan comes to disagree with the run it predicts (SH245).</p>
     */
    private String noteSanitized(ShAgentBatch.Op op, ShPostType postType) {
        if (op.data() == null || !op.data().isObject() || postType == null
                || postType.getShPostTypeAttrs() == null) {
            return null;
        }
        List<String> notes = new ArrayList<>();
        for (ShPostTypeAttr attr : postType.getShPostTypeAttrs()) {
            if (attr.getShWidget() == null
                    || !ShSystemWidget.HTML_EDITOR.equals(attr.getShWidget().getName())) {
                continue;
            }
            JsonNode value = op.data().get(attr.getName());
            if (value == null || !value.isString()) {
                continue;
            }
            ShHtmlSanitizer.Result result = shHtmlSanitizer.sanitize(value.asString());
            if (result.changed()) {
                notes.add("`" + attr.getName() + "` loses " + String.join(", ", result.removed())
                        + " to the content allow-list");
            }
        }
        return notes.isEmpty() ? null : String.join("; ", notes);
    }

    private static String note(String first, String second) {
        if (first == null) {
            return second;
        }
        return second == null ? first : first + "; " + second;
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

    /** SH767 — a 400 naming the form a relation field stores, for an address it cannot mean. */
    public static class RelationAddressException extends ShTeachingException {

        public RelationAddressException(String field, String sent, ShAddress address, String why) {
            super(ShProblem.badRequest("relation-address",
                            "Field '" + field + "' cannot hold '" + sent + "'")
                    .withDetail(why + " This field holds friendly URLs of posts in this site, in "
                            + "render order — a `post:` address for this site is accepted and "
                            + "stored as its URL. Address: " + address + ".")
                    .withFix("Send the post's friendly URL (`/sections/hero`) or its address in "
                            + "this site (`post:" + address.site() + "/sections/hero`).")
                    .withExample("{\"op\":\"post.upsert\",\"address\":\"" + address
                            + "\",\"data\":{\"" + field + "\":[\"/sections/hero\"]}}"));
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
     * SH192 — a 400 carrying the line, the column and a caret under the offending character.
     *
     * <p>That is FSP's {@code regionError} output, which this product had in 2019 and lost.
     * The position is the whole point: "unclosed block" without it means reading the template
     * again, and the alternative to refusing at all is a blank page discovered later, which
     * costs a guess per save.</p>
     */
    public static class InvalidTemplateException extends ShTeachingException {

        public InvalidTemplateException(ShAddress address, String postType, List<String> problems) {
            super(ShProblem.badRequest("template-invalid",
                            "The template in this '" + postType + "' will not compile")
                    .withDetail("A layout or region is compiled at render time, so a template "
                            + "that does not parse is a blank page nobody sees until they look. "
                            + "Address: " + address + ".\n" + String.join("\n", problems))
                    .withFix("Fix the template at the position above and write it again. "
                            + "Nothing was saved."));
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
        // SH730 — checked against the post at `from`, and before the already-moved branch below:
        // if somebody else moved it while this plan was running, that IS the concurrent change
        // `expect` was asked about, and reporting it as an idempotent noop would hide exactly the
        // event the caller opted in to hear about.
        requireExpectedState(op, from, source.orElse(null));
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

        // SH140 — asked before the write, for the same reason `live` is: afterwards the old path
        // is nobody's address and the scan cannot tell a link this move broke from one that was
        // always a front-end route. A dry run asks it too, because "which pages will this break"
        // is exactly what a plan is for (P7).
        ShAgentChangeSet.InboundLinks inbound = sameUrl || !live
                ? null : inboundLinks(site, from, to, post.getId());

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
                live ? "the live URL changes immediately" : null, post.getId(), inbound);
    }

    /**
     * The pages this move is about to break, or {@code null} when there are none (SH140).
     *
     * <p>Capped at {@link #INBOUND_LINK_CAP}: an agent that has just moved a page needs the
     * addresses, and a site where two hundred pages link to one URL needs the count plus enough
     * addresses to start — the whole list would be a response bigger than the write that caused
     * it (P3), and the run that follows the fix reports whatever is left.</p>
     */
    private ShAgentChangeSet.InboundLinks inboundLinks(ShSite site, ShAddress from, ShAddress to,
                                                       String postId) {
        ShPostLinks.Inbound inbound =
                shPostLinks.linkingTo(site, from.path(), postId, INBOUND_LINK_CAP);
        if (!inbound.any()) {
            return null;
        }
        // SH1064 — the redirect comes first in the fix because it is the one repair that works on
        // the links this instance cannot see: a bookmark, somebody else's page, a search result.
        // Repointing the N pages here leaves all of those 404ing, which is why "no writer ever
        // creates the redirect" was a defect rather than a preference.
        String fix = "Keep '" + from.path() + "' answering with a post.redirect op in this same "
                + "batch, after the move: {\"op\":\"post.redirect\",\"address\":\"" + from
                + "\",\"to\":\"" + to + "\"}. Or repoint those links at " + to + " — they are "
                + "authored URLs, not references, so nothing rewrote them.";
        return new ShAgentChangeSet.InboundLinks(from.path(), inbound.addresses(), inbound.found(),
                inbound.truncated()
                        ? fix + " " + inbound.found() + " pages link to it; "
                                + inbound.addresses().size() + " are listed — re-run the move's "
                                + "verify (or GET /agent/verify?checks=routes) for the rest."
                        : fix);
    }

    /**
     * SH1064 — keeps an address answering after the page behind it went somewhere else.
     *
     * <h2>The address is the OLD path, which is the whole shape of it</h2>
     *
     * <p>SH385 gave {@code Redirect} no {@code from} field: a redirect's source <em>is</em> its own
     * friendly URL, so this op writes a post at the address the reader still has in their bookmark,
     * and {@code to} names where they end up. That also means the order inside a batch is the
     * gesture: {@code post.move} vacates {@code /pricing}, then {@code post.redirect} occupies it —
     * and because a batch is all-or-nothing, the rename and the redirect land together or not at
     * all. Run the other way round the path is still taken, and the refusal below says so.</p>
     *
     * <h2>Idempotent, like every other op here</h2>
     *
     * <p>A redirect already pointing where this one would point is a {@code noop}; one pointing
     * somewhere else is an {@code update}. A <b>page</b> at that address is refused rather than
     * overwritten — that is somebody's content, and a redirect shadowing a real page is SH99's
     * {@code duplicate-url} error one layer down.</p>
     */
    private Change postRedirect(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.POST);
        ShSite site = requireSite(address.site(), session);
        String to = redirectTarget(op, address);
        boolean permanent = redirectPermanent(op);

        Optional<ShPostUnified> existing = findPost(site, address.path());
        requireExpectedState(op, address, existing.orElse(null));
        if (existing.isPresent()) {
            ShPostUnified post = existing.get();
            String typeName = post.getShPostType() == null ? null : post.getShPostType().getName();
            if (!ShSystemPostType.REDIRECT.equals(typeName)) {
                throw new RedirectPathOccupiedException(address, typeName);
            }
            JsonNode desired = redirectData(post.getTitle(), to, permanent);
            if (sameData(post, desired)) {
                return session.change(op, address, Change.NOOP, null, post.getId());
            }
            if (!session.dryRun) {
                shContentAclService.requireWrite(post.getId());
                shPostUnifiedWriteService.update(post.getId(),
                        new UpdateRequest(desired, ShPostState.DRAFT, session.actor));
                shContentAclService.requirePublish(post.getId());
                shPostUnifiedWriteService.publish(post.getId(), session.actor);
            }
            return session.change(op, address, Change.UPDATE, liveRedirect(permanent), post.getId());
        }

        ShPostType redirectType = shPostTypeRepository.findByName(ShSystemPostType.REDIRECT);
        if (redirectType == null) {
            throw new RedirectTypeMissingException(address);
        }
        // The site root, always. Where a redirect is filed in the console tree is not a decision
        // an agent has to make, and the `folder` key it would need was 3 est. tokens on every
        // session's copy of the vocabulary — which is the trade SH346's ceiling asked for when
        // this op breached it (P3).
        ShFolder folder = requireRootFolder(site);
        session.planPost(address);
        if (session.dryRun || folder == null) {
            return session.change(op, address, Change.CREATE, liveRedirect(permanent), null);
        }
        shContentAclService.requireWrite(folder.getId());
        shTenantQuotaService.checkCanCreatePost();
        ShPostUnified created = shPostUnifiedWriteService.create(new CreateRequest(
                folder.getId(), redirectType.getId(),
                redirectData(address.path() + " -> " + to, to, permanent),
                ShPostState.DRAFT, session.actor, op.locale(), null));
        shPostUnifiedWriteService.setFurl(created.getId(), declaredFurl(op, address));
        shContentAclService.requirePublish(created.getId());
        shPostUnifiedWriteService.publish(created.getId(), session.actor);
        return session.change(op, address, Change.CREATE, liveRedirect(permanent), created.getId());
    }

    /** Said on every redirect row: it is live now, and which status a visitor gets. */
    private static String liveRedirect(boolean permanent) {
        return "published immediately — a draft redirect routes nobody; "
                + (permanent ? "301 permanent" : "302 temporary (data.permanent:true for 301)");
    }

    /**
     * A redirect's destination: {@code to}, as a path or an absolute URL.
     *
     * <p>A {@code post:} address is accepted and reduced to its URL, because that is the currency
     * the rest of this surface is written in (SH79) and {@code TO} stores a path. An absolute URL
     * passes through untouched — a page that now lives on another host is the case the field's
     * own rule already allows, and the one {@code verify} declines to check.</p>
     */
    private String redirectTarget(ShAgentBatch.Op op, ShAddress address) {
        if (op.to() == null || op.to().isBlank()) {
            throw new MissingOpFieldException("to", op.op(), address, "post:Blog/plans",
                    "A redirect needs somewhere to send the reader: `to` takes an address, a "
                            + "site-relative path, or an absolute URL.");
        }
        String raw = op.to().trim();
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw;
        }
        return raw.contains(":") ? ShAddress.parse(raw).path() : ShAddress.normalizePath(raw);
    }

    /**
     * 302 unless the caller asks for permanence, which is SH385's rule and not a new one.
     *
     * <p>The first draft of this op inverted it — a rename is not coming back, so 301 — and that is
     * the argument SH385 already weighed and declined: <b>a permanent redirect is cached by
     * browsers effectively for ever, and a wrong one is the hardest routing mistake to take back.</b>
     * Two defaults for one field, differing by which door wrote it, is also the split P5 exists to
     * stop. So {@code data.permanent:true} is the opt-in here exactly as the Check Box is in the
     * console, and the caller that ticks it is the one making the claim.</p>
     */
    private boolean redirectPermanent(ShAgentBatch.Op op) {
        JsonNode data = op.data();
        JsonNode permanent = data == null ? null : data.get(ShSystemPostTypeAttr.PERMANENT
                .toLowerCase(java.util.Locale.ROOT));
        if (permanent == null && data != null) {
            permanent = data.get(ShSystemPostTypeAttr.PERMANENT);
        }
        return permanent != null && ShPostFieldFlags.isTrue(
                permanent.isBoolean() ? permanent.asBoolean() : permanent.asString());
    }

    /** The two fields a {@code Redirect} declares, plus the title every post needs. */
    private JsonNode redirectData(String title, String to, boolean permanent) {
        return objectMapper.createObjectNode()
                .put(ShSystemPostTypeAttr.TITLE, title)
                .put(ShSystemPostTypeAttr.TO, to)
                .put(ShSystemPostTypeAttr.PERMANENT, permanent);
    }

    /** A page already answers there, and a redirect would shadow it (SH1064). */
    public static class RedirectPathOccupiedException extends ShTeachingException {
        public RedirectPathOccupiedException(ShAddress address, String postType) {
            super(ShProblem.of("redirect-path-occupied", HttpStatus.CONFLICT,
                            "A page already answers at '" + address + "'")
                    .withDetail("A redirect's source is its own friendly URL, so this op would "
                            + "need the address that '" + (postType == null ? "a post" : postType)
                            + "' holds. Two posts on one URL is SH99's duplicate-url error, and "
                            + "the page would win or lose by accident.")
                    .withFix("Move or delete that page first — post.move it out of the way in the "
                            + "same batch, before this op — or point the redirect at a different "
                            + "old address."));
        }
    }

    /** This instance has no {@code Redirect} post-type yet (SH1064). */
    public static class RedirectTypeMissingException extends ShTeachingException {
        public RedirectTypeMissingException(ShAddress address) {
            super(ShProblem.badRequest("redirect-type-missing",
                            "This instance has no 'Redirect' post-type")
                    .withDetail("Redirect is one of the system types the render model provisions, "
                            + "and nothing has provisioned it here, so there is no type to write "
                            + address + " as.")
                    .withFix("Run a render.provision op (POST /api/v2/agent/batch): "
                            + "{\"op\":\"render.provision\",\"address\":\"" + address.site()
                            + "\"} — it seeds the system types, then re-run this batch."));
        }
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
     * SH987 — put a captured revision back over the draft.
     *
     * <p>The plan reads the revision rather than answering "would roll back", which is SH690's
     * rule: a plan that says yes about a revision the write will not find is the plan an agent
     * acts on. So a missing number is refused <em>in the dry run</em>, with the numbers that do
     * exist, and the caller finds out before it has confirmed anything.</p>
     *
     * <p>{@code expect} is honoured against the draft, which is the row this overwrites — a
     * rollback is a write like every other, and one onto a draft somebody else has moved on from
     * is the same surprise as any other stale write.</p>
     */
    private Change postRollback(ShAgentBatch.Op op, Session session) {
        ShAddress address = stateAddress(op);
        // Before resolving anything: a missing key is a fact about the op, and checking it after
        // the address means a caller that got both wrong is told about the address — which is the
        // error it can do least about, since the numbers it needs come from reading that address.
        if (op.revision() == null) {
            // The shared missing-key refusal rather than one of its own (SH332): a caller learns
            // "you left out a key" once, in one shape, across the whole vocabulary.
            throw new MissingOpFieldException("revision", POST_ROLLBACK, address,
                    "{\"op\":\"" + POST_ROLLBACK + "\",\"address\":\"" + address
                            + "\",\"revision\":3}",
                    "A rollback names which captured version to put back. Read them first: "
                            + "GET /api/v2/agent/read?address=" + address + "&include=revisions "
                            + "lists each number with when it was captured, who by and what it "
                            + "changed.");
        }
        ShPostUnified row;
        if (address.kind() == ShAddress.Kind.ID) {
            row = shPostUnifiedRepository.findById(address.path()).stream().findFirst()
                    .orElseThrow(() -> new ShAddressResolver.UnresolvedAddressException(
                            address, List.of()));
        } else {
            ShSite site = requireSite(address.site(), session);
            row = state(site, address, ShPostState.DRAFT)
                    .or(() -> state(site, address, ShPostState.PUBLISHED))
                    .orElseThrow(() -> new ShAddressResolver.UnresolvedAddressException(
                            address, List.of()));
        }
        // The plan reads it, so applying holds no surprises (SH690).
        shPostRevisionService.row(row.getId(), op.revision())
                .orElseThrow(() -> ShPostStateNotFoundException.noSuchRevision(row.getId(),
                        op.revision(), shPostRevisionService.rows(row.getId()).stream()
                                .map(ShRevisionRow::version).toList()));
        ShPostUnified draft = shPostUnifiedRepository
                .findByIdAndState(row.getId(), ShPostState.DRAFT).orElse(null);
        requireExpectedState(op, address, draft);
        if (session.dryRun) {
            return session.change(op, address, Change.UPDATE,
                    "restore revision " + op.revision() + " into the draft", row.getId());
        }
        shContentAclService.requireWrite(row.getId());
        shPostUnifiedWriteService.restoreRevision(row.getId(), op.revision(), session.actor);
        return session.change(op, address, Change.UPDATE,
                "restored revision " + op.revision() + " into the draft", row.getId());
    }

    /**
     * Publishing is reported as a change only when the live rendering would actually
     * differ — a re-run of the same document must not claim to have published again.
     */
    private Change postPublish(ShAgentBatch.Op op, Session session) {
        ShAddress address = stateAddress(op);
        if (address.kind() == ShAddress.Kind.ID) {
            return publishById(op, address, session, true);
        }
        ShSite site = requireSite(address.site(), session);
        // Dry run only: in a real run the draft genuinely exists by now and must really
        // be published — short-circuiting here would silently skip the publish and make
        // apply disagree with its own plan.
        if (session.dryRun && session.alreadyPlanned(address)) {
            // SH992 — the plan reports a schedule as the run will: a draft this batch creates is
            // scheduled rather than published, and has nothing yet to cancel.
            Change planned = scheduled(op, address, session, null, true);
            return planned != null ? planned
                    : session.change(op, address, Change.PUBLISH, "the draft this batch creates", null);
        }
        ShPostUnified draft = state(site, address, ShPostState.DRAFT).orElse(null);
        Optional<ShPostUnified> published = state(site, address, ShPostState.PUBLISHED);
        // SH730 — the DRAFT is the subject, deliberately. Publishing is not a content change, so an
        // `expect` matched against the PUBLISHED row would refuse the very case publishing exists
        // for; and the draft is what an ordinary read returns, so the digest a caller already holds
        // is the one that works here without a second, state-scoped read.
        requireExpectedState(op, address, draft);
        if (draft == null) {
            if (published.isPresent()) {
                return session.change(op, address, Change.NOOP, cancels(op)
                        ? "no publish scheduled" : "no draft to publish", published.get().getId());
            }
            throw new ShAddressResolver.UnresolvedAddressException(address, List.of());
        }
        // SH342 — a scheduled publish, which is the same op with a `when`. It comes *before* the
        // no-drift check on purpose: "the draft already matches what is published" makes publishing
        // now a noop, and says nothing about a transition asked for at a later instant.
        Change scheduled = scheduled(op, address, session, draft, true);
        if (scheduled != null) {
            return scheduled;
        }
        if (published.isPresent() && sameContent(draft, published.get())) {
            return session.change(op, address, Change.NOOP, null, draft.getId());
        }
        // SH1009 — a plan says what the apply would refuse, in SH997's shape: a sentence on the
        // change rather than a thrown refusal, because a plan that stopped at the first problem
        // would report one op of ten. The apply itself is refused inside `publish`, on the same
        // evaluation, so the two cannot promise different things.
        List<String> incomplete = shPostUnifiedWriteService.publishBlockers(draft);
        if (session.dryRun && !incomplete.isEmpty()) {
            return session.change(op, address, Change.PUBLISH,
                    "this publish is refused: " + String.join("; ", incomplete), draft.getId());
        }
        if (!session.dryRun) {
            // SH995 — PUBLISH, not WRITE: this is the call that changes what visitors see, and until
            // now anybody who could edit the page could also put it live.
            shContentAclService.requirePublish(draft.getId());
            // SH997 — and what the site says about the content itself, evaluated here rather than in
            // the caller so no surface can publish around it.
            shPublishGateService.requirePost(draft.getId());
            shPostUnifiedWriteService.publish(draft.getId(), session.actor);
        }
        return session.change(op, address, Change.PUBLISH, null, draft.getId());
    }

    /**
     * The translation group a created post joins, or {@code null} (SH348).
     *
     * <p>The op names an <b>address</b> and the server resolves it to the group — SH150's shape, and
     * for its reason: an agent must not have to invent an identifier the server owns, and a group id
     * in a file is the UUID Block I exists to remove. The target's group is used rather than its id,
     * so translating a translation joins the same group instead of starting a second one.</p>
     *
     * <p>Two refusals, both teaching. An <b>unresolvable</b> target is a 400 naming it, because the
     * alternative — creating an unlinked post and reporting success — is the silent-drop failure SH331
     * spent a task removing. And a post <b>cannot be a translation of itself</b>: that is the one
     * shape the anchor convention cannot express, so it is refused rather than stored as a cycle
     * nothing can read back.</p>
     */
    private String translationGroupFor(ShAgentBatch.Op op, ShAddress address, Session session) {
        if (op.translationOf() == null || op.translationOf().isBlank()) {
            return null;
        }
        String raw = op.translationOf().trim();
        ShAddress target = raw.startsWith("post:")
                ? ShAddress.parse(raw)
                : ShAddress.post(address.site(), raw);
        if (target.toString().equalsIgnoreCase(address.toString())) {
            throw new BadTranslationTargetException(address, raw, true);
        }
        ShSite site = requireSite(target.site(), session);
        ShPostUnified anchor = state(site, target, ShPostState.DRAFT)
                .or(() -> state(site, target, ShPostState.PUBLISHED))
                .orElseThrow(() -> new BadTranslationTargetException(address, raw, false));
        String group = anchor.getTranslationGroupId();
        return group == null || group.isBlank() ? anchor.getId() : group;
    }

    /**
     * The op's {@code when}, or {@code null} for "now" (SH342).
     *
     * <p>Two refusals, and both are decisions rather than validation. An unparseable instant is a
     * teaching 400 because a caller that believes it scheduled something and did not has no way to
     * find out — the publish simply never happens. And an instant <b>in the past</b> is refused
     * rather than treated as "now": {@code post.publish} with no {@code when} already means now, so
     * accepting a past date would make two different calls mean the same thing and would silently
     * swallow the mistake this actually catches, which is a timezone or a stale timestamp.</p>
     */
    private Instant scheduleInstant(ShAgentBatch.Op op, ShAddress address) {
        if (op.when() == null || op.when().isBlank()) {
            return null;
        }
        Instant when;
        try {
            when = Instant.parse(op.when().trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new BadScheduleException(op.op(), address, op.when(), false);
        }
        if (when.isBefore(Instant.now())) {
            throw new BadScheduleException(op.op(), address, op.when(), true);
        }
        return when;
    }

    /** Whether the op names an instant at all, before {@link #scheduleInstant} judges it (SH992). */
    private static boolean hasWhen(ShAgentBatch.Op op) {
        return op.when() != null && !op.when().isBlank();
    }

    /** Whether the op asks to clear its transition's pending instant (SH992). */
    private static boolean cancels(ShAgentBatch.Op op) {
        return Boolean.TRUE.equals(op.cancel());
    }

    /**
     * SH992 — the op's schedule set or cleared, or {@code null} when it asked for the transition now.
     *
     * <p>One helper for both ops and both address forms, because the defect was a handler and its
     * twin disagreeing: {@code postPublish} read {@code when} and {@code postUnpublish} bound it and
     * never looked, so a takedown dated for December happened at once and reported success.</p>
     *
     * <p>Both instants live on the draft, and the one the op does not name is carried through: the
     * SH342 publish path passed {@code null} for the unpublish instant, so scheduling a launch quietly
     * cancelled the takedown already set for it. A repeat of the pending instant is a noop (P5), and
     * a dry run returns the row the run does, because a plan must not promise what the run refuses.</p>
     *
     * @param draft the draft row, or {@code null} for one this batch has only planned — in which case
     *              nothing is pending and nothing is written, since only a dry run gets here without it
     */
    private Change scheduled(ShAgentBatch.Op op, ShAddress address, Session session,
                             ShPostUnified draft, boolean publishing) {
        Instant when = scheduleInstant(op, address);
        if (when == null && !cancels(op)) {
            return null;
        }
        if (when != null && cancels(op)) {
            throw new CancelWithWhenException(op.op(), address);
        }
        String verb = publishing ? "publish" : "unpublish";
        String id = draft == null ? null : draft.getId();
        Instant publishAt = draft == null ? null : draft.getPublishScheduledDate();
        Instant unpublishAt = draft == null ? null : draft.getUnpublishScheduledDate();
        Instant pending = publishing ? publishAt : unpublishAt;
        if (when == null) {
            if (pending == null) {
                return session.change(op, address, Change.NOOP, "no " + verb + " scheduled", id);
            }
            if (!session.dryRun) {
                // SH995 — a schedule is a publish with a date on it, so it asks the same permission.
                shContentAclService.requirePublish(id);
                shPostUnifiedWriteService.schedule(id, publishing ? null : publishAt,
                        publishing ? unpublishAt : null, session.actor);
            }
            return session.change(op, address, Change.UPDATE,
                    "cancelled the " + verb + " scheduled for " + pending, id);
        }
        if (pending != null && pending.truncatedTo(ChronoUnit.MILLIS)
                .equals(when.truncatedTo(ChronoUnit.MILLIS))) {
            return session.change(op, address, Change.NOOP,
                    "already scheduled to " + verb + " at " + pending, id);
        }
        if (!session.dryRun) {
            shContentAclService.requirePublish(id);
            shPostUnifiedWriteService.schedule(id, publishing ? when : publishAt,
                    publishing ? unpublishAt : when, session.actor);
        }
        return session.change(op, address, Change.UPDATE, "scheduled to " + verb + " at " + when
                + (pending == null ? "" : ", replacing " + pending), id);
    }

    private Change postUnpublish(ShAgentBatch.Op op, Session session) {
        ShAddress address = stateAddress(op);
        if (address.kind() == ShAddress.Kind.ID) {
            return publishById(op, address, session, false);
        }
        ShSite site = requireSite(address.site(), session);
        if (session.dryRun && session.alreadyPlanned(address)) {
            Change planned = scheduled(op, address, session, null, false);
            if (planned != null) {
                return planned;
            }
        }
        Optional<ShPostUnified> published = state(site, address, ShPostState.PUBLISHED);
        ShPostUnified draft = state(site, address, ShPostState.DRAFT).orElse(null);
        // SH992 — before "not published", on purpose: a page whose launch is itself scheduled can
        // carry its takedown too, which is an embargo window, and calling one off needs no live page.
        if (hasWhen(op) || cancels(op)) {
            if (draft == null && published.isEmpty() && hasWhen(op)) {
                throw new ShAddressResolver.UnresolvedAddressException(address, List.of());
            }
            if (hasWhen(op) && published.isPresent()) {
                requireDraftBehind(op, address, draft, published.get());
            }
            return scheduled(op, address, session, draft, false);
        }
        if (published.isEmpty()) {
            return session.change(op, address, Change.NOOP, "not published", null);
        }
        // SH462 - see requireDraftBehind: unpublish deletes the published row, so a post with
        // no draft behind it would be destroyed rather than withdrawn.
        requireDraftBehind(op, address,
                state(site, address, ShPostState.DRAFT).orElse(null), published.get());
        String id = published.get().getId();
        if (!session.dryRun) {
            // SH995 — taking a page down is the same act as putting it up, from the other side.
            shContentAclService.requirePublish(id);
            shPostUnifiedWriteService.unpublish(id, session.actor);
        }
        return session.change(op, address, Change.UNPUBLISH, null, id);
    }

    private Change postDelete(ShAgentBatch.Op op, Session session) {
        ShAddress address = writeAddress(op, ShAddress.Kind.POST);
        ShSite site = requireSite(address.site(), session);
        Optional<ShPostUnified> post = findPost(site, address.path());
        // SH730 — a delete is the widest thing that can happen to a post, so it is the op where a
        // stale plan costs most: the row it removes may have been rewritten since it was read.
        requireExpectedState(op, address, post.orElse(null));
        if (post.isEmpty()) {
            return session.change(op, address, Change.NOOP, "already absent", null);
        }
        String id = post.get().getId();
        // SH733 — an agent could fill the trash and never empty it, and purge is what fires the
        // DELETE lifecycle event: without it `ShStaticFileCleanupListener` never releases an
        // uploaded file's bytes and the SH12 webhook never tells anybody the post went.
        boolean purge = flag(op.data(), PURGE_KEY);
        if (!session.dryRun) {
            shContentAclService.requireWrite(id);
            shPostUnifiedWriteService.delete(id, session.actor);
            if (purge) {
                shPostUnifiedWriteService.purge(id, session.actor);
            }
        }
        // SH55: the default is the reversible trash. Say which it was — an agent that believes it
        // destroyed something will not offer the human the undo, and one that believes it kept an
        // undo it declined will offer one that is not there.
        return session.change(op, address, Change.DELETE, purge
                ? "permanently - `data.purge` declines the trash, so the file bytes are released "
                        + "and the DELETE webhook fires"
                : "to trash (restorable)", id);
    }

    /**
     * SH222 — brings a trashed post back.
     *
     * <h2>No confirm token, and that is a decision rather than an omission</h2>
     *
     * <p>SH108's rule is that a <em>destructive</em> op needs the plan it was shown. A restore is
     * not destructive: nothing is lost by running it, and the thing it undoes is itself reversible.
     * It is not free either — it can bring back content a human deliberately removed — so it is
     * <b>audited</b> ({@code ShContentAction.RESTORE}, which SH200 already records) and it answers
     * with what came back. A token here would put a round trip in front of the one call somebody
     * makes in a hurry, to protect against an act the next delete undoes.</p>
     */
    private Change postRestore(ShAgentBatch.Op op, Session session) {
        String id = trashedId(op);
        if (session.dryRun) {
            // The dry run reads the trash rather than answering "would restore": a plan that said
            // yes about a post nobody trashed is the plan an agent acts on.
            if (shPostUnifiedRepository.findTrashedById(id, ShTenantContext.getTenantId()).isEmpty()) {
                return session.change(op, ShAddress.id(id), Change.NOOP, "not in the trash", id);
            }
            // SH243 — and a plan that says yes about a restore the write will refuse is the same
            // defect. Refused in the plan, the way folderMove refuses an occupied target before it
            // consults session.dryRun: the point of a plan is that applying it holds no surprises.
            shPostUnifiedWriteService.requireRestorableUrl(id);
            return session.change(op, ShAddress.id(id), Change.UPDATE, "from trash", id);
        }
        shContentAclService.requireWrite(id);
        return shPostUnifiedWriteService.restoreFromTrash(id, session.actor)
                ? session.change(op, ShAddress.id(id), Change.UPDATE, "from trash", id)
                // NOOP rather than a refusal: an id that is not in the trash is either already
                // restored or was purged, and both are states the caller wanted to reach.
                : session.change(op, ShAddress.id(id), Change.NOOP, "not in the trash", id);
    }

    /**
     * SH222 — the folder half, which brings its subtree's posts back with it (SH200).
     *
     * <h2>The plan reads the trash too (SH690)</h2>
     *
     * <p>This branch answered {@code UPDATE "from trash, with its subtree"} unconditionally, one
     * method below the post half that reads the trash and says why. So a {@code dryRun} promised a
     * section restore in three cases the write does not deliver: the id is not a trashed folder, its
     * <b>parent</b> is still trashed (SH200's refusal), or a post beneath it has had its friendly URL
     * taken — SH243's 409, which refuses the whole subtree. An agent that trusts the plan spends the
     * confirm token and gets a different answer, and P9's claim is that the cheap textual proof
     * stands in for a human round trip: a plan that can be wrong in three ways is not that proof.</p>
     *
     * <p>The parent-trashed case keeps its <b>own</b> wording rather than folding into "not in the
     * trash", because it is a different instruction — the caller has something to do about it — and
     * {@code ShReviewService.revertFolder} already prints that distinction; the sentence is its.</p>
     *
     * <p>All three are read <b>before</b> {@code session.dryRun} is consulted, which is what makes
     * them one answer instead of two that drift: the apply path reaches the same two NOOPs and the
     * same 409 through the checks it already ran, and the plan reaches them through the writer's own
     * {@code requireRestorableUrls} rather than by re-deriving the rule here.</p>
     */
    private Change folderRestore(ShAgentBatch.Op op, Session session) {
        String id = trashedId(op);
        Optional<ShFolder> trashed = shFolderRepository.findAnyById(id).filter(ShFolder::isTrashed);
        if (trashed.isEmpty()) {
            return session.change(op, ShAddress.id(id), Change.NOOP, "not in the trash", id);
        }
        ShFolder parent = trashed.get().getParentFolder();
        if (parent != null && parent.isTrashed()) {
            return session.change(op, ShAddress.id(id), Change.NOOP,
                    "its parent folder is in the trash — restore that instead", id);
        }
        shFolderWriteService.requireRestorableUrls(id);
        if (session.dryRun) {
            return session.change(op, ShAddress.id(id), Change.UPDATE, "from trash, with its subtree", id);
        }
        // SH696 — the guard every other mutating op runs, and the only one that was missing. A
        // restore brings back a whole subtree (SH200), so a credential scoped to part of a tree
        // could resurrect a section it could not have deleted; `folderDelete` has asked since
        // SH200 and `postRestore` since SH222. Non-goal: "the agent path is not a privilege
        // escalation".
        shContentAclService.requireWrite(id);
        return shFolderWriteService.restore(id, session.actor)
                ? session.change(op, ShAddress.id(id), Change.UPDATE, "from trash, with its subtree", id)
                : session.change(op, ShAddress.id(id), Change.NOOP, "not in the trash", id);
    }

    /**
     * The id a restore names, which is the only form a trashed object has (SH222).
     *
     * <p>A trashed post has no reachable friendly URL, so {@code post:<site>/<path>} cannot name it
     * — SH172's {@code id:} fallback exists for exactly this. Refused with the reason rather than
     * resolved leniently: a caller passing a path here has misread what the trash is, and silently
     * looking one up would restore whichever live post happens to share it.</p>
     */
    private static String trashedId(ShAgentBatch.Op op) {
        String raw = op.address() == null ? "" : op.address().trim();
        if (raw.isEmpty()) {
            // The same refusal every other op gives for a missing address, rather than a bespoke
            // one: `ShAgentOpsContractTest` checks that a key the vocabulary calls required is
            // enforced, and a caller who forgot the address has the same problem here as anywhere.
            throw new MissingOpFieldException("address", op.op(), null,
                    "id:<uuid>", "Every op names what it acts on by address.");
        }
        if (!raw.startsWith("id:")) {
            throw new TrashAddressException(raw);
        }
        return raw.substring("id:".length()).trim();
    }

    /** A 400: a restore was addressed by path, which a trashed object does not have. */
    public static class TrashAddressException extends ShTeachingException {

        public TrashAddressException(String sent) {
            super(ShProblem.badRequest("trash-address",
                            "'" + sent + "' does not name something in the trash")
                    .withDetail("A trashed post or folder has no reachable path, so it is named by "
                            + "id. List the trash with GET /api/v2/agent/find?trash=true, which "
                            + "answers with the id: addresses a restore takes.")
                    .withFix("Use the `id:` address from the trash listing.")
                    .withExample("id:0f1e2d3c-4b5a-6789-0abc-def012345678"));
        }
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
        // SH329 — not `siteAccess.require`: that raises the read-scoping fix, and there is no
        // `?site=` on this endpoint.
        throw ShAgentContextService.UnknownSiteException.onWrite(nameOrId,
                siteAccess.readable().stream().map(ShSite::getName).toList());
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
        // SH583 — the shared generator. This used to be a private copy of ShAddressResolver's
        // private copy, which is how a write and the read that follows it come to disagree.
        for (String candidate : ShFurlSpellings.candidates(address.path())) {
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
    private record TypeRef(String name, Set<String> fields, String id, Set<String> blockFields) {

        private static TypeRef of(ShPostType live, Set<String> alsoDeclared) {
            Set<String> names = new LinkedHashSet<>(declaredFieldNamesOf(live));
            names.addAll(alsoDeclared);
            // SH1015 — which of those fields hold blocks, read here rather than looked up again at
            // check time: the type is already loaded, and a second read is a second answer.
            return new TypeRef(live.getName(), names, live.getId(), blockFieldNamesOf(live));
        }
    }

    /** SH1015 — the declared fields whose widget is {@code Blocks}. */
    private static Set<String> blockFieldNamesOf(ShPostType postType) {
        Set<String> names = new LinkedHashSet<>();
        collectBlockFieldNames(postType.getShPostTypeAttrs(), names);
        return names;
    }

    private static void collectBlockFieldNames(Set<ShPostTypeAttr> attrs, Set<String> into) {
        if (attrs == null) {
            return;
        }
        for (ShPostTypeAttr attr : attrs) {
            if (attr.getName() != null && attr.getShWidget() != null
                    && ShSystemWidget.BLOCKS.equals(attr.getShWidget().getName())) {
                into.add(attr.getName());
            }
            collectBlockFieldNames(attr.getShPostTypeAttrs(), into);
        }
    }

    /**
     * SH1015 — a Blocks field holds a document this instance can store, or the write is refused.
     *
     * <p>Follows {@link #checkDeclaredFields} exactly, and for its reason: a real run refuses and a
     * plan reports, so a `shio push` of several posts says everything wrong with them in one answer
     * rather than dying on the first.</p>
     *
     * <p>Validated on the way <b>in</b> and not on the way out. A malformed blocks document that
     * reached storage would be discovered by whatever renders it, which is a blank region on
     * somebody's page — the shape SH168 refused for an undeclared key, for the same reason.</p>
     */
    private String checkBlocks(ShAgentBatch.Op op, ShAddress address, Session session,
                               TypeRef declaredIn) {
        JsonNode data = op.data();
        if (data == null || !data.isObject() || declaredIn == null
                || declaredIn.blockFields().isEmpty()) {
            return null;
        }
        List<String> bad = new ArrayList<>();
        for (String field : declaredIn.blockFields()) {
            JsonNode value = data.get(field);
            if (value == null) {
                continue;
            }
            try {
                ShBlocks.require(field, value);
            } catch (ShBlocks.ShInvalidBlocksException e) {
                if (!session.dryRun) {
                    throw e;
                }
                bad.add(e.getMessage());
            }
        }
        return bad.isEmpty() ? null : String.join("; ", bad);
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
            // SH1015 — no live type, so nothing declares a widget: a batch provisioning its own
            // post-type has no Blocks field to check until the type exists, and guessing one from
            // the declaration would validate against a model this instance has not accepted yet.
            return new TypeRef(name, session.declaredFieldsOf(name), null, Set.of());
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
        // SH993 moved the body to ShPostDrift: a release asks the same question over a whole set, and
        // two implementations would let one surface call a page changed while the other calls it live.
        return ShPostDrift.sameContent(objectMapper, draft, published);
    }

    private JsonNode readTree(String json) {
        // SH993 — one decision about unparseable stored data, in ShPostDrift, which is also where
        // the drift comparison reads it. Two copies of it would be two silent catches (SH270).
        return ShPostDrift.tree(objectMapper, json);
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
    /**
     * SH462 — the address the two state verbs take: {@code post:} always, and {@code id:} for an
     * object that <b>can hold no {@code post:} address</b>.
     *
     * <h2>The dead end this opens</h2>
     *
     * <p>A {@code File} post is never given a friendly URL ({@code ShStaticFileService.upload} does
     * not assign one, which is why SH172 needed the {@code id:} fallback in the first place), so
     * {@code /agent/find} reports a site's assets as {@code id:<uuid>} — correctly, because that is
     * the only address they have. Every write then refused that form, and the refusal's own
     * {@code fix} said "re-address the op with the `post:` scheme": advice that <em>cannot be
     * followed</em> for this object. And because a batch is atomic, one such op failed the whole
     * run at {@code ops[0]}, so a replication that tried to be thorough about its assets published
     * nothing at all.</p>
     *
     * <h2>What SH79 is actually protecting, and why this does not break it</h2>
     *
     * <p>The rule is that a write must not take a UUID <em>instead of</em> an address, because a
     * UUID is the working currency this whole surface exists to remove and because
     * {@code folder:}/{@code post:} are separate namespaces a guess could confuse. Neither applies
     * here: an object with no friendly URL has no address to prefer, and there is no second
     * namespace to be confused with. So the exemption is written as exactly that condition — the
     * post's {@code furl} is blank — and a post that <em>does</em> have one is still refused,
     * now naming the address it should have been given.</p>
     *
     * <p>Publish and unpublish only. {@code post.upsert} and {@code post.move} are unchanged and
     * must be: they take or change an address, and an object with none cannot be their subject.</p>
     */
    private ShAddress stateAddress(ShAgentBatch.Op op) {
        if (op.address() == null || op.address().isBlank()) {
            throw new MissingOpFieldException("address", op.op(), null, "post:<site>/<path>",
                    "Every op names what it acts on by address.");
        }
        ShAddress address = ShAddress.parse(op.address());
        if (address.kind() != ShAddress.Kind.POST && address.kind() != ShAddress.Kind.ID) {
            throw new WrongAddressKindException(op.op(), address, ShAddress.Kind.POST);
        }
        return address;
    }

    /**
     * A state change on an object addressed by id (SH462).
     *
     * <p>The publish half is almost always a {@code NOOP}, and that is the outcome worth having
     * rather than a shortcoming: an uploaded {@code File} is written {@code PUBLISHED} by
     * {@code upload} itself, so an agent that publishes its assets for safety's sake is <b>told
     * they are already live</b> and its batch survives — which is the whole of the defect. The
     * transition is still real when there is a draft, because a {@code File} post can reach that
     * state by other routes (a {@code post.upsert} naming the type, an unpublish).</p>
     *
     * @param publishing {@code true} for post.publish, {@code false} for post.unpublish
     */
    private Change publishById(ShAgentBatch.Op op, ShAddress address, Session session,
                               boolean publishing) {
        String id = address.id();
        ShPostUnified draft = shPostUnifiedRepository.findByIdAndState(id, ShPostState.DRAFT)
                .orElse(null);
        ShPostUnified published = shPostUnifiedRepository.findByIdAndState(id, ShPostState.PUBLISHED)
                .orElse(null);
        ShPostUnified any = draft != null ? draft : published;
        if (any == null) {
            throw new ShAddressResolver.UnresolvedAddressException(address, List.of());
        }
        // The exemption is the *absence of an address*, not the type — so it is asked of the post
        // rather than of a list of type names somebody has to keep in step.
        if (any.getFurl() != null && !any.getFurl().isBlank()) {
            throw new AddressableByUrlException(op.op(), address, any);
        }
        // Site access, the same question `requireSite` asks of a `post:` address — an id names an
        // object without saying whose, so it must be asked here rather than skipped.
        ShSite site = ShFolderSite.of(any.getShFolder());
        requireSite(site == null ? null : site.getName(), session);

        // SH992 — the `id:` form schedules on the same terms as an address, or it would be the one
        // path where a dated publish still happened now.
        if (hasWhen(op) || cancels(op)) {
            if (draft == null) {
                if (!cancels(op) && !publishing) {
                    requireDraftBehind(op, address, null, published);
                }
                return session.change(op, address, Change.NOOP, cancels(op)
                        ? "no " + (publishing ? "publish" : "unpublish") + " scheduled"
                        : "no draft to publish", published.getId());
            }
            return scheduled(op, address, session, draft, publishing);
        }
        if (publishing) {
            if (draft == null) {
                return session.change(op, address, Change.NOOP, "no draft to publish",
                        published.getId());
            }
            if (published != null && sameContent(draft, published)) {
                return session.change(op, address, Change.NOOP, null, draft.getId());
            }
            if (!session.dryRun) {
                shContentAclService.requirePublish(draft.getId());
                shPublishGateService.requirePost(draft.getId());
                shPostUnifiedWriteService.publish(draft.getId(), session.actor);
            }
            return session.change(op, address, Change.PUBLISH, null, draft.getId());
        }
        if (published == null) {
            return session.change(op, address, Change.NOOP, "not published", null);
        }
        requireDraftBehind(op, address, draft, published);
        if (!session.dryRun) {
            shContentAclService.requirePublish(published.getId());
            shPostUnifiedWriteService.unpublish(published.getId(), session.actor);
        }
        return session.change(op, address, Change.UNPUBLISH, null, published.getId());
    }

    /**
     * SH462 - a withdrawal that would DESTROY the object is refused.
     *
     * <h2>Found by making the op reachable</h2>
     *
     * <p>{@code ShPostUnifiedWriteService.unpublish} hard-deletes the {@code PUBLISHED} row and
     * keeps the draft, which is exactly right for the post it was written for: a post reaches
     * {@code PUBLISHED} through {@code post.publish}, which leaves the draft behind, so there is
     * always something to fall back to. An uploaded {@code File} has <b>no draft</b> - SH38 writes
     * one row and it is published - so withdrawing it deletes the object outright, not even into
     * the trash {@code post.delete} uses. Nothing could reach that before SH462 gave the op an
     * address for it, which is what makes this a finding <em>of</em> the change rather than a
     * regression <em>in</em> it.</p>
     *
     * <p>Guarded on <b>both</b> address forms rather than only the new one: a {@code PUBLISHED} row
     * with no draft is a shape an exchange import can produce for an ordinary post too, and a guard
     * covering only the path this task opened would leave the older one destroying content by the
     * same mechanism. P7, and the surprise here is unrecoverable.</p>
     */
    private void requireDraftBehind(ShAgentBatch.Op op, ShAddress address,
                                    ShPostUnified draft, ShPostUnified published) {
        if (draft == null) {
            throw new NothingToFallBackToException(op.op(), address, published);
        }
    }

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

    /**
     * The {@code furl} a {@code post.upsert} stores — the spelling {@code op.url()} declares, or
     * the address's own path when it declares none (SH467).
     *
     * <h2>Why the address cannot answer this</h2>
     *
     * <p>{@code ShAddress} collapses a trailing slash, deliberately: it is a value with equality,
     * and the render digest hashes it, so {@code post:S/x} and {@code post:S/x/} being two
     * addresses for one post is the P5 identity split. But a source that serves
     * {@code /model-catalog/} with a 200 and 301s {@code /model-catalog} to it means the slash,
     * and a replica storing the collapsed form mirrors it exactly backwards — a document-relative
     * {@code ./app/main.js} then resolves to {@code /app/main.js} instead of
     * {@code /model-catalog/app/main.js}. So the identity collapses and the <em>spelling</em>
     * travels on the write, beside {@code folder}, {@code locale} and {@code translationOf}.</p>
     *
     * <h2>The guard, and why it is silent</h2>
     *
     * <p>A declared URL that is not a spelling of {@code address} is ignored rather than refused.
     * The two are not independent inputs an agent chose — {@code apply} derives the address
     * <em>from</em> the URL one line earlier, so they can only disagree by a slash, and the one
     * way to reach a genuine disagreement is a hand-built batch op that named both. Refusing that
     * would be a 400 on the field's own compatibility default: every caller before SH467 sends no
     * {@code url} at all, and half of them would be a blank string away from it.
     * {@link ShFurlSpellings#candidates} is what decides "is a spelling of", which is the reason
     * SH583 landed first — this is the fifth reader, and it would otherwise have been a fifth
     * private rule.</p>
     */
    private static String declaredFurl(ShAgentBatch.Op op, ShAddress address) {
        String declared = op.url();
        if (declared == null || declared.isBlank()) {
            return address.path();
        }
        String stored = ShFurlSpellings.stored(declared);
        return ShFurlSpellings.candidates(address.path()).contains(stored)
                ? stored
                : address.path();
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
        /** SH981 — whether this caller asked for the console's links on each row. */
        private final boolean links;
        private final String actor;
        private final Declared declared;
        private final Set<String> plannedFolders = new HashSet<>();
        private final Set<String> plannedPosts = new HashSet<>();
        /** SH993 — releases an earlier op in this dry run creates. */
        private final Set<String> plannedReleases = new HashSet<>();

        private Session(boolean dryRun, boolean ids, boolean links, String actor,
                        Declared declared) {
            this.dryRun = dryRun;
            this.ids = ids;
            this.links = links;
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

        private void planRelease(ShAddress address) {
            plannedReleases.add(address.toString().toLowerCase(Locale.ROOT));
        }

        private boolean releasePlanned(ShAddress address) {
            return plannedReleases.contains(address.toString().toLowerCase(Locale.ROOT));
        }

        private Change change(ShAgentBatch.Op op, ShAddress address, String action,
                              String detail, String id) {
            return new Change(op.op(), address.toString(), action, detail, ids ? id : null)
                    .withCuratorUrl(curatorUrl(op, id));
        }

        /**
         * Where the console shows what this op wrote (SH981), or {@code null}.
         *
         * <p>From the declared route rather than a string built here, and keyed on the op's own
         * subject: a folder op lands on the browser at that folder, a post op on its editor. A
         * delete has a link to nothing, so it gets none — the page it named is gone.</p>
         */
        private String curatorUrl(ShAgentBatch.Op op, String id) {
            // SH981 — asked for, not given. Measured: a link per row took the build-a-site
            // benchmark 800 est. tokens over its ceiling, on a task that writes sixty times and
            // hands over once. The caller that wants the handoff is the one about to make it, which
            // is why `shio publish` and `shio push --content` ask and a blueprint apply does not.
            // SH993 — a release has no console page yet, and a post link built from its id would be
            // a link to nothing.
            if (!links || id == null || op.op() == null || op.op().endsWith(".delete")
                    || op.op().startsWith("release.")) {
                return null;
            }
            return shConsoleSurfaces.curatorUrl(op.op().startsWith("folder.") ? "folder"
                    : op.op().startsWith("site.") ? "site" : "post", id);
        }

        /**
         * The same row carrying what the op broke elsewhere (SH140). Not gated on {@code ids}:
         * that flag is about whether the caller wants UUIDs, and these are addresses.
         */
        private Change change(ShAgentBatch.Op op, ShAddress address, String action, String detail,
                              String id, ShAgentChangeSet.InboundLinks inboundLinks) {
            return new Change(op.op(), address.toString(), action, detail, ids ? id : null,
                    inboundLinks).withCuratorUrl(curatorUrl(op, id));
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

    /**
     * A 400 for a desired-state document that declares nothing to converge to (SH547).
     *
     * <p>Every teaching field names the <b>document's</b> keys, because that is the grammar this
     * caller is using. The {@code fix} also names {@code /agent/batch}, which is the one thing the
     * old message got right by accident and the reason this refusal is not merely a reworded
     * {@code empty-batch}: an empty document is either a caller who declared nothing or a caller who
     * sent an ops batch to the wrong endpoint — {@code ops} is not a key here, so Jackson drops it
     * and what arrives is a document with no folders and no posts. Both readers are answered in one
     * round trip, which is the cost P6 exists to remove and the cost this one charged twice.</p>
     */
    public static class EmptyDocumentException extends ShTeachingException {

        public EmptyDocumentException() {
            super(ShProblem.badRequest("empty-document",
                            "The document declares no folders and no posts")
                    .withDetail("POST /api/v2/agent/apply takes a desired-state document: `site` "
                            + "plus the `folders` and `posts` that should exist. Neither list holds "
                            + "anything, so there is nothing to converge to.")
                    .withAllowed(List.of("site", "folders", "posts", "moves", "prune", "confirm",
                            "createSite", "postTypeLayout", "scratch"))
                    .withFix("Declare at least one folder or post. If you meant to send an `ops` "
                            + "array, that is a different endpoint: POST /api/v2/agent/batch.")
                    .withExample("{\"site\":\"Blog\",\"folders\":[{\"path\":\"/blog\"}],"
                            + "\"posts\":[{\"url\":\"/blog/hello\",\"type\":\"Article\","
                            + "\"folder\":\"/blog\",\"data\":{\"title\":\"Hello\"}}]}"));
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
    /**
     * A {@code translationOf} that names nothing, or names this post (SH348).
     *
     * <p>One exception, two messages, for {@code BadScheduleException}'s reason: the caller's mistake
     * has one shape — a link that cannot be made — and what differs is why. The self-reference case is
     * the interesting one, because storing it would produce a file that says it is a translation of
     * itself, which the anchor convention reads as "I am the original" and would silently lose.</p>
     */
    public static class BadTranslationTargetException extends ShTeachingException {

        public BadTranslationTargetException(ShAddress subject, String sent, boolean self) {
            super(ShProblem.badRequest("bad-translation-target",
                            self ? "`translationOf` names this post" : "`translationOf` names no post")
                    .withDetail(self
                            ? "'" + sent + "' is the post being written. A post is the original or a "
                                    + "translation of another one; a link to itself is the one shape "
                                    + "this cannot express."
                            : "'" + sent + "' does not resolve to a post. Declaring the original in "
                                    + "the same document is enough — it is created first — but it has "
                                    + "to exist by the time this row runs.")
                    .withFix(self
                            ? "Drop `translationOf` on the original and put it on the translation."
                            : "Check the URL, or push the post it translates first. "
                                    + "GET /api/v2/agent/find?site=<site> lists what is there.")
                    .withExample("{\"op\":\"post.upsert\",\"address\":\"" + subject
                            + "\",\"locale\":\"pt-br\",\"translationOf\":\"/about\"}"));
        }
    }

    /**
     * A {@code when} that is not a future instant (SH342).
     *
     * <p>One exception with two messages rather than two classes: the caller's mistake is the same
     * shape — a timestamp that will not schedule anything — and what differs is which half of it was
     * wrong. A past instant is the interesting case, because the tempting behaviour is to treat it as
     * "now" and the cost of that is silence: a stale timestamp or a timezone slip would publish
     * immediately and look like exactly what was asked for.</p>
     */
    public static class BadScheduleException extends ShTeachingException {

        public BadScheduleException(String op, ShAddress address, String sent, boolean past) {
            super(ShProblem.badRequest("bad-schedule",
                            past ? "`when` is in the past" : "`when` is not an ISO-8601 instant")
                    .withDetail(past
                            ? "'" + sent + "' has already passed, so nothing would ever fire. "
                                    + "Publishing now is what this op means with no `when` at all, "
                                    + "so a past instant is more likely a timezone or a stale "
                                    + "timestamp than a request."
                            : "'" + sent + "' could not be read as an instant. It must be UTC "
                                    + "ISO-8601, which is the form /agent/changes and the audit "
                                    + "trail already use.")
                    .withFix(past
                            ? "Send a future instant, or drop `when` to publish immediately."
                            : "Use an instant like 2026-08-01T09:00:00Z.")
                    .withExample("{\"op\":\"" + op + "\",\"address\":\""
                            + (address == null ? "post:Blog/blog/hello" : address.toString())
                            + "\",\"when\":\"2026-08-01T09:00:00Z\"}"));
        }
    }

    /**
     * SH992 — {@code when} and {@code cancel} on one op.
     *
     * <p>Refused rather than resolved by precedence, since either reading loses something the caller
     * meant: honouring the instant ignores a request to clear, and honouring the cancel drops a date
     * the caller took the trouble to send.</p>
     */
    public static class CancelWithWhenException extends ShTeachingException {

        public CancelWithWhenException(String op, ShAddress address) {
            super(ShProblem.badRequest("cancel-with-when", "`cancel` and `when` on one op")
                    .withDetail("`when` sets the " + op + " instant and `cancel` clears it, so the "
                            + "op asks for both.")
                    .withFix("Send `when` to schedule or reschedule, or `cancel` alone to call it off.")
                    .withExample("{\"op\":\"" + op + "\",\"address\":\""
                            + (address == null ? "post:Blog/blog/hello" : address.toString())
                            + "\",\"cancel\":true}"));
        }
    }

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

    /** SH993 — a release naming a post whose draft is gone, which a run cannot publish. */
    public static class ReleaseHoldsNothingException extends ShTeachingException {

        public ReleaseHoldsNothingException(ShAddress release, int missing) {
            super(ShProblem.conflict("release-holds-a-deleted-post",
                            missing + " post(s) in " + release + " no longer exist")
                    .withDetail("A post was trashed or deleted after it was put in this release, so "
                            + "the run cannot publish the set it was confirmed as.")
                    .withFix("Take them out with release.remove, or restore them, then run it again. "
                            + "GET /api/v2/agent/find?site=" + release.site() + "&release="
                            + release.releaseName() + " lists what it holds.")
                    .withExample("{\"op\":\"release.remove\",\"address\":\"" + release
                            + "\",\"data\":{\"posts\":[\"post:" + release.site() + "/gone\"]}}"));
        }
    }

    /** SH993 — a release bigger than one transaction, refused while planning. */
    public static class ReleaseTooLargeException extends ShTeachingException {

        public ReleaseTooLargeException(ShAddress release, int posts, int max) {
            super(ShProblem.badRequest("release-too-large",
                            release + " holds " + posts + " posts and one run is capped at " + max)
                    .withDetail("A release goes live in one transaction, so it cannot be larger than "
                            + "one batch. Split above this and the atomicity is gone, which is the "
                            + "thing a release is for.")
                    .withFix("Plan two releases, or raise shio.agent.batch.max-ops on the instance.")
                    .withExample(max));
        }
    }

    /** SH993 — a post named for a release in another site. */
    public static class ReleaseSiteMismatchException extends ShTeachingException {

        public ReleaseSiteMismatchException(ShAddress release, ShAddress post) {
            super(ShProblem.badRequest("release-site-mismatch",
                            "'" + post + "' is not in " + release.site())
                    .withDetail("A release holds posts from its own site, so that running it changes "
                            + "one site and nothing else.")
                    .withFix("Name posts in " + release.site() + ", or plan a release in "
                            + post.site() + " for this one.")
                    .withExample("{\"op\":\"release.add\",\"address\":\"" + release
                            + "\",\"data\":{\"posts\":[\"post:" + release.site() + "/about\"]}}"));
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

    /**
     * SH462 — an {@code id:} on a state verb, for a post that has a {@code post:} address.
     *
     * <p>The exemption exists for objects that <b>cannot</b> be addressed by URL, so a post that can
     * is refused — and the refusal is worth its own class because it is the one place on this surface
     * that can hand the caller the exact address it should have sent, rather than the scheme it
     * should have used. {@code WrongAddressKindException} says "use `post:`"; this says
     * "use `post:Blog/hello-world`".</p>
     */
    public static class AddressableByUrlException extends ShTeachingException {

        public AddressableByUrlException(String op, ShAddress sent, ShPostUnified post) {
            super(ShProblem.badRequest("addressable-by-url",
                            op + " takes an id: only for a post with no URL")
                    .withDetail("'" + sent + "' names a post that has a friendly URL, so it has a "
                            + "post: address and SH79's rule applies to it: a write names content "
                            + "by address, never by the UUID the address exists to replace. An "
                            + "id: is accepted here only for an object that can hold no address "
                            + "at all - a File post, which is never given a URL.")
                    .withFix("Re-address the op with this post's own address.")
                    .withExample(addressOf(post)));
        }

        private static String addressOf(ShPostUnified post) {
            ShSite site = post == null ? null : ShFolderSite.of(post.getShFolder());
            return ShAddress.post(site == null ? null : site.getName(),
                    post == null ? null : post.getFurl()).toString();
        }
    }

    /**
     * SH462 — a withdrawal with nothing behind it, which would be a deletion.
     *
     * <p>Refused rather than performed, and refused rather than quietly turned into a
     * {@code post.delete}: those are different acts with different reversibility (delete is the SH55
     * trash and restorable; this would not be), and an op that silently did the more destructive one
     * is the shape P7 exists to forbid. The message names the one op that <em>does</em> mean it.</p>
     */
    public static class NothingToFallBackToException extends ShTeachingException {

        public NothingToFallBackToException(String op, ShAddress sent, ShPostUnified published) {
            super(ShProblem.badRequest("nothing-to-fall-back-to",
                            op + " would delete '" + sent + "', not withdraw it")
                    .withDetail("Unpublishing removes the published row and leaves the draft behind. "
                            + "This object has no draft - an uploaded File is written published and "
                            + "never has one - so there is nothing to fall back to and the row is all "
                            + "there is. Removing it would destroy the object outright, and not into "
                            + "the trash.")
                    .withAllowed(List.of(POST_DELETE, POST_UPSERT))
                    .withFix("If you meant to remove it, use post.delete, which trashes it and can "
                            + "be undone. If you meant to take it off the public site while keeping "
                            + "it, there is no such state for an object with no draft: re-upload it "
                            + "when it is wanted again.")
                    .withExample(POST_DELETE + " " + sent
                            + (published == null ? "" : " (" + published.getTitle() + ")")));
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

    /**
     * A 409: something already lives at the address a copy would land on (SH244).
     *
     * <p>Its own refusal rather than {@link MoveTargetOccupiedException} because the two have
     * different fixes and P6 is about the fix landing, not about a status code. A move's advice is
     * to get the occupant out of the way, since a move has one destination; a copy's is to pick a
     * different name, because the caller chose the name and the source is still where it was. The
     * detail also says what a copy will <b>not</b> do — number the name — since that is exactly
     * what the console does with the same gesture, and a caller who has seen {@code "archive (2)"}
     * appear has every reason to expect it here.</p>
     */
    public static class CopyTargetOccupiedException extends ShTeachingException {

        public CopyTargetOccupiedException(ShAddress from, ShAddress to) {
            super(ShProblem.of("copy-target-occupied", HttpStatus.CONFLICT,
                            "'" + to + "' is already taken")
                    .withDetail("Copying '" + from + "' there would leave two folders answering "
                            + "to one name chain, and an address resolves to whichever the walk "
                            + "reaches first. The console numbers a clone; this op does not, "
                            + "because a caller that cannot read the result has to know the "
                            + "address of what it made.")
                    .withFix("Give `to` a free name chain. The last segment is what the copy is "
                            + "called, so changing only that renames it in place.")
                    .withExample(to + "-2"));
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
                            // SH244 — this used to say "recreate the structure with folder.upsert
                            // and move the posts across one at a time", which was the honest advice
                            // while a section could only be duplicated from the console. It is now
                            // one op, and a fix naming the harder route is a fix that costs the
                            // caller a loop it does not have to run (P6).
                            ? "folder.copy the section into the destination site, then "
                                    + "folder.delete the original — two visible steps instead of "
                                    + "one invisible one."
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
