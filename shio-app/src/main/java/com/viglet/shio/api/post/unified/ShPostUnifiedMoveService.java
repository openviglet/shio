/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.ShAgentSessionContext;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.verify.ShPostLinks;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.object.ShFolderSite;
import com.viglet.shio.persistence.model.post.unified.ShPostState;

/**
 * SH1132 — a rename a curator can do.
 *
 * <h2>The gap</h2>
 *
 * <p>A post's friendly URL is its identity (SH81), and the only writer that changes one was
 * {@code post.move} on the agent surface: the console's own {@code PUT /post-unified/{id}} takes
 * {@code data} and a state and nothing else. A curator who wanted {@code /pricing} to become
 * {@code /plans} had to ask an agent, or create a second post and delete the first — which is the
 * silently destructive path {@code post.move} exists to prevent, taking the original's id, its
 * SH51 audit trail, its SH14 history and every inbound reference with it.</p>
 *
 * <h2>Composed, not forked (P10, SH1021's precedent)</h2>
 *
 * <p>This builds one op and hands it to {@link ShAgentBatchService}. A console writer for a rename
 * would define what a move means twice, and the two definitions would drift about the part that is
 * easy to get wrong — which rows move, and what happens to the address left behind.</p>
 *
 * <h2>Both rows move, and the plan is how P7 is kept</h2>
 *
 * <p>§LXVIII.19 left this open: rename the draft alone, or both rows? The writer's own note already
 * answers it. Moving only the draft would leave the live page at the old URL while every read —
 * which is draft-preferred — reported the new one, so <b>the post's identity would depend on who was
 * asking</b>. Both rows move, and the consequence is real: <b>a move of a published post changes the
 * live URL immediately, with no publish step.</b></p>
 *
 * <p>That is the surprise P7 forbids, so it is not sprung: {@code dryRun} defaults to <b>true</b>
 * (SH1021's rule — the request sent by accident is a plan), and the plan says whether the page is
 * live, what address is being vacated, and how many pages link to it. A curator who reads that and
 * proceeds has not been surprised; one who is shown a text field and a Save button has.</p>
 *
 * <h2>The redirect is offered, never assumed</h2>
 *
 * <p>SH1064's {@link ShConsoleRedirects} writes it, through the same batch service, and it reads the
 * live row's URL as the destination — which after the move is the new address, so the composition is
 * exact. Offered rather than automatic because a redirect is a published object at the old address
 * and a curator may be renaming a page precisely because nothing should answer there.</p>
 */
@Service
public class ShPostUnifiedMoveService {

    /**
     * How many inbound links the plan will count before it stops counting.
     *
     * <p>The same cap {@code post.move} reports with, and the same scanner: two counts of "who links
     * here" would eventually disagree, and the one a curator reads before renaming is the one that
     * has to be right.</p>
     */
    private static final int INBOUND_CAP = 50;

    private final ShAgentBatchService shAgentBatchService;
    private final ShConsoleRedirects shConsoleRedirects;
    private final ShContentAclService shContentAclService;
    private final ShPostLinks shPostLinks;
    private final ShPostUnifiedRepository shPostUnifiedRepository;

    public ShPostUnifiedMoveService(ShAgentBatchService shAgentBatchService,
                                    ShConsoleRedirects shConsoleRedirects,
                                    ShContentAclService shContentAclService,
                                    ShPostLinks shPostLinks,
                                    ShPostUnifiedRepository shPostUnifiedRepository) {
        this.shAgentBatchService = shAgentBatchService;
        this.shConsoleRedirects = shConsoleRedirects;
        this.shContentAclService = shContentAclService;
        this.shPostLinks = shPostLinks;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
    }

    /**
     * What a rename would do, and what it did.
     *
     * @param from     the address the post answers at today, draft-preferred
     * @param to       the address asked for, normalized by the op
     * @param live     whether a published row exists — if it does, the live URL moves at once
     * @param vacated  the live address that stops answering, or null when nothing is published
     * @param inbound  how many other pages link to {@code vacated}, capped; null when none
     * @param redirect whether a redirect was asked for, and on a real run whether one was written
     * @param changes  the batch's own change set: an outcome and a problem, per op
     */
    public record MovePlan(String from, String to, boolean live, String vacated, Integer inbound,
                           boolean redirect, ShAgentChangeSet changes) {
    }

    /**
     * Plan or carry out a rename.
     *
     * @param dryRun absent means true, for the reason a bulk verb's does (SH1021)
     */
    @Transactional
    public MovePlan run(String id, String url, boolean redirect, boolean dryRun, String actor) {
        if (url == null || url.isBlank()) {
            throw new NoUrlException();
        }
        // Before anything is built, so a caller who may not write this page gets one refusal rather
        // than a plan they could not carry out — SH1021's reason, one post instead of a selection.
        shContentAclService.requireWrite(id);

        String from = urlOf(id, ShPostState.DRAFT).or(() -> urlOf(id, ShPostState.PUBLISHED))
                .orElse(null);
        String vacated = urlOf(id, ShPostState.PUBLISHED).orElse(null);
        boolean live = vacated != null;
        /*
         * Addressed as `post:<site>/<furl>`, not as `id:` — and the difference is a refusal, not a
         * style. A write on the agent surface takes only the two addressing forms that say where
         * content IS (agents.md's SH79 rule); `id:` is the lenient read form, and the batch refuses
         * it for a write because a folder path and a friendly URL are separate namespaces and
         * guessing which was meant would create content in the wrong place. The bulk verbs
         * (SH1021) may use `id:` because publish, unpublish and delete do not address a
         * destination. A move does, so it resolves here — where the id and the row are both to
         * hand — rather than asking the batch to relax a guard that is right.
         */
        ShPostUnified subject = shPostUnifiedRepository.findByIdAndState(id, ShPostState.DRAFT)
                .or(() -> shPostUnifiedRepository.findByIdAndState(id, ShPostState.PUBLISHED))
                .orElseThrow(NoSuchPostException::new);
        ShSite site = ShFolderSite.of(subject.getShFolder());
        if (site == null || from == null) {
            throw new NoSuchPostException();
        }
        String address = ShAddress.post(site.getName(), from).toString();
        // The destination is `to`, an address, not a bare `url`: the op moves a post between
        // addresses and a folder change is the same verb, so one field carries both.
        String destination = ShAddress.post(site.getName(), url).toString();

        ShAgentChangeSet changes;
        ShAgentSessionContext.set(ShAgentSessionContext.consoleSession(actor, Instant.now()));
        try {
            changes = shAgentBatchService.run(new ShAgentBatch(dryRun, List.of(new ShAgentBatch.Op(
                    ShAgentBatchService.POST_MOVE, address, null, null, destination, null,
                    null, null, null, null, null, null)), null, false), true, actor);

            // Only after the move, and only on a real run. The destination is the live row's URL,
            // which the move has just set — so this has to read the world the move left behind.
            if (!dryRun && redirect && live) {
                shConsoleRedirects.after(id, vacated, actor);
            }
        } finally {
            // The thread is pooled, so a session left bound stamps the next request's audit rows
            // with this curator's day.
            ShAgentSessionContext.clear();
        }
        return new MovePlan(from, url, live, vacated, inboundTo(id, vacated), redirect, changes);
    }

    private Optional<String> urlOf(String id, ShPostState state) {
        return shPostUnifiedRepository.findByIdAndState(id, state)
                .map(ShPostUnified::getFurl)
                .filter(furl -> furl != null && !furl.isBlank());
    }

    /**
     * How many other pages link to the address being vacated.
     *
     * <p>The OLD one, because that is what breaks: a link to the address the page is moving away
     * from is the link that stops working. The same choice {@code routesOf} makes for the publish
     * plan, and for the same sentence.</p>
     */
    private Integer inboundTo(String id, String vacated) {
        if (vacated == null) {
            return null;
        }
        ShSite site = shPostUnifiedRepository.findByIdAndState(id, ShPostState.PUBLISHED)
                .map(post -> ShFolderSite.of(post.getShFolder()))
                .orElse(null);
        if (site == null) {
            return null;
        }
        int found = shPostLinks.linkingTo(site, vacated, id, INBOUND_CAP).found();
        return found == 0 ? null : found;
    }

    /** A page with no address is one this cannot move: there is nothing to move it from. */
    public static class NoSuchPostException extends ShTeachingException {
        public NoSuchPostException() {
            super(ShProblem.of("no-such-post", org.springframework.http.HttpStatus.NOT_FOUND,
                            "There is no page here to rename.")
                    .withDetail("A rename addresses the page by where it is now, so a post with no "
                            + "site or no friendly URL has no address to move from.")
                    .withFix("Check the post id, or give the post a URL first."));
        }
    }

    /** A rename to nothing is a caller mistake, and saying so beats normalizing it to the root. */
    public static class NoUrlException extends ShTeachingException {
        public NoUrlException() {
            super(ShProblem.badRequest("no-url", "This rename names no address.")
                    .withDetail("A post's friendly URL is its identity (SH81). An empty one would "
                            + "normalize to the site root, which is a different page and almost "
                            + "certainly not what was meant.")
                    .withFix("Send `url` with the address the page should answer at.")
                    .withExample("{\"url\":\"/plans\",\"dryRun\":true}"));
        }
    }
}
