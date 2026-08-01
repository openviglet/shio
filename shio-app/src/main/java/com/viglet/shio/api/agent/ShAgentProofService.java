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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.verify.ShAgentRenderService;
import com.viglet.shio.api.agent.verify.ShAgentVerifyService;
import com.viglet.shio.api.agent.verify.dto.ShAgentDigest;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.render.ShPageRenderService;
import com.viglet.shio.render.ShTemplateGraph;

/**
 * SH157 — <b>a write that returns its own proof.</b>
 *
 * <h2>The measurement this exists to move</h2>
 *
 * <p>The SH112 benchmark's verdict on Block M was split: tokens cleared the concept spec's
 * ≈10× target, and <em>tool calls cleared nothing</em> — best 3.3× against ≈6×. The traces
 * say why, and it is not a payload problem. Every canonical task opens with discovery
 * (manifest, then context) and closes with proof (verify), and the actual work in between is
 * one or two calls. The framing <em>is</em> the remaining round trips.</p>
 *
 * <p>So the closing turn is folded into the write. The batch already knows exactly what it
 * touched, and running the SH99 checks over those addresses costs the server almost nothing —
 * and it is strictly better than the separate {@code /agent/verify} it replaces, because the
 * report is scoped to the change instead of to the site.</p>
 *
 * <h2>Attribution, which is the part that could mislead</h2>
 *
 * <p>The risk named when this was designed: a write that reports a pre-existing defect as if
 * it caused it. The findings describe the state those addresses are in <em>now</em>, and for
 * an op that wrote no fields — a {@code publish} on a post nobody edited — every defect it
 * surfaces was already there. That is exactly when a lint attached to a write reads as an
 * accusation, so the report names those addresses rather than leaving the reader to work it
 * out. Cheap, precise, and it keeps the section honest enough to act on.</p>
 *
 * <h2>SH194 — the same argument, one instrument along</h2>
 *
 * <p>Folding the lint into the write removed the closing turn of a <em>correctness</em> loop. An
 * <em>appearance</em> loop has the same shape and was untouched: upsert the layout, render a page,
 * compare. {@code ?digest=true} returns the two SH187 hashes for the pages the run affected, which
 * is one call instead of three — and for a write to a shared layout or region it answers a question
 * the caller could not previously ask at all, because the affected pages are SH193's graph rather
 * than the addresses the batch touched.</p>
 *
 * <h2>Why a service and not a method on the controller</h2>
 *
 * <p>Two callers: the REST endpoints and {@code shio_write}. An MCP tool is a shape over a
 * service and never a place where logic lives (P10) — a copy of this in the tool is how the
 * two surfaces would come to disagree about what a write proves.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShAgentProofService {

    /** Addresses named in the notes before they are elided; five is a readable line. */
    private static final int NAMED = 5;

    /**
     * How many pages a write renders to answer {@code ?digest=}, when the caller names no count.
     *
     * <p>Ten was a guess that read well (SH194), which is what SH266 was filed about. It is still
     * ten and now for a reason: SH264 measured a swept page at <b>6 queries</b> — SH311 caches the
     * presentation chain for the length of the read-only transaction a proof runs in — so the
     * default is ~60 queries behind one write, the same order as the write itself.</p>
     *
     * <p>Both numbers are the operator's now ({@code shio.agent.render.write-digests} and
     * {@code max-write-digests}), and the caller may name its own count: an agent iterating on one
     * layout wants two, a migration verifying a section wants forty. The cut is always reported,
     * because an unreported cut reads as a complete answer (SH138).</p>
     */
    private int maxDigests(Integer requested) {
        int ceiling = Math.max(1, properties.getRender().getMaxWriteDigests());
        if (requested == null || requested <= 0) {
            return Math.min(Math.max(1, properties.getRender().getWriteDigests()), ceiling);
        }
        // Clamped, never refused: a caller asking for more than the instance allows wants as many
        // as it can have, and it learns the real number from the reported cut rather than from a
        // 400 that would tell it nothing the answer does not.
        return Math.min(requested, ceiling);
    }

    private final ShAgentVerifyService shAgentVerifyService;
    private final ShAgentRenderService shAgentRenderService;
    private final ShAddressResolver shAddressResolver;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPageRenderService shPageRenderService;
    private final ShTemplateGraph shTemplateGraph;
    private final ShAgentProperties properties;

    public ShAgentProofService(ShAgentVerifyService shAgentVerifyService,
                               ShAgentRenderService shAgentRenderService,
                               ShAddressResolver shAddressResolver,
                               ShPostUnifiedRepository shPostUnifiedRepository,
                               ShPageRenderService shPageRenderService,
                               ShTemplateGraph shTemplateGraph,
                               ShAgentProperties properties) {
        this.shAgentVerifyService = shAgentVerifyService;
        this.shAgentRenderService = shAgentRenderService;
        this.shAddressResolver = shAddressResolver;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPageRenderService = shPageRenderService;
        this.shTemplateGraph = shTemplateGraph;
        this.properties = properties;
    }

    /**
     * Attaches the lint over what this run touched, when the caller asked for it.
     *
     * <p>Must be called <b>after</b> the write transaction has committed — which is what
     * being outside the write service buys. Inside it, the lint would read the state a
     * rollback is about to discard, and a failure in the read could fail a write that had
     * already succeeded.</p>
     *
     * @param changes the committed change set
     * @param verify  whether the caller asked for proof
     */
    public ShAgentChangeSet attach(ShAgentChangeSet changes, boolean verify) {
        return attach(changes, verify, false);
    }

    /**
     * @param digest SH194 — also return the two SH187 hashes for the pages this run affected
     */
    // Read-only, and its *own* transaction: the write has already committed by the time this runs
    // (which is the whole reason this service is outside the writer), but SH193's graph walks LAZY
    // associations — `post.getShPostType()`, `post.getShSite()` — and a post read with no session
    // open throws `LazyInitializationException` the moment the graph asks what type it is. A fresh
    // read-only transaction is not a weakening of the after-commit rule; it is a second, later
    // read, which is exactly what a proof is.
    @Transactional(readOnly = true)
    public ShAgentChangeSet attach(ShAgentChangeSet changes, boolean verify, boolean digest) {
        return attach(changes, verify, digest, null);
    }

    /**
     * @param pages SH266 — how many affected pages to digest, clamped to the instance's ceiling.
     *              {@code null} takes the configured default, which is what {@code digest=true}
     *              means
     */
    @Transactional(readOnly = true)
    public ShAgentChangeSet attach(ShAgentChangeSet changes, boolean verify, boolean digest,
                                   Integer pages) {
        ShAgentChangeSet linted = attachProof(changes, verify);
        return digest && !changes.dryRun() ? linted.withDigests(digestsOf(changes, pages)) : linted;
    }

    private ShAgentChangeSet attachProof(ShAgentChangeSet changes, boolean verify) {
        // A dry run wrote nothing, so there is nothing it could have proved. Linting the
        // current state here would read as "the plan is clean", which is a claim about a
        // future the plan has not created yet.
        if (!verify || changes.dryRun()) {
            return changes;
        }
        List<String> addresses = postAddresses(changes, row -> true);
        if (addresses.isEmpty()) {
            return changes;
        }
        ShAgentReport report = shAgentVerifyService.verify(null, null, null, null,
                String.join(",", addresses), null);
        return changes.withProof(attributed(report, changes, addresses.size()));
    }

    private static ShAgentReport attributed(ShAgentReport report, ShAgentChangeSet changes,
                                            int scoped) {
        // An op that did not upsert wrote no fields, so a finding against its address was
        // already true before this run. Naming those is the difference between a report and
        // an accusation.
        List<String> untouched = postAddresses(changes,
                row -> row.op() != null && !row.op().endsWith(".upsert"));

        List<String> notes = new ArrayList<>(report.notes() == null ? List.of() : report.notes());
        // Attribution is only worth its tokens when there is something to attribute. A clean
        // proof is the common case — it is what a correct write produces — and two sentences
        // explaining that none of the zero findings were caused by this run is exactly the
        // kind of prose P3 says to stop paying for. The trade this whole section makes is a
        // round trip for a few tokens; spending them on an empty report would waste both.
        if (report.found() > 0) {
            notes.add("scoped to the " + scoped + " address(es) this run touched: these are "
                    + "their findings now, not necessarily defects this run introduced");
            if (!untouched.isEmpty()) {
                notes.add(untouched.size() + " had no fields written by this run ("
                        + String.join(", ", untouched.subList(0, Math.min(NAMED, untouched.size())))
                        + (untouched.size() > NAMED ? ", …" : "")
                        + "), so a finding against those predates it");
            }
        }
        return new ShAgentReport(report.ok(), report.checks(), report.checked(), report.errors(),
                report.warnings(), report.found(), report.truncated(), report.partial(),
                report.scope(), List.copyOf(notes), report.findings());
    }


    /**
     * The pages this run affected, as two hashes each (SH194).
     *
     * <h3>"Affected" is not "touched"</h3>
     *
     * <p>For a page, the two coincide. For a <b>layout or region</b> they do not and the difference
     * is the point: the address the batch wrote is a template, and digesting a template as a page
     * answers nothing — what changed is every page that renders through it. SH193's graph knows
     * which post-types those are, so this is the first caller of that graph that needed it for
     * something other than reporting.</p>
     *
     * <h3>Ordered, capped, and the cut is reported</h3>
     *
     * <p>Sorted by address so two runs over the same change return the same list in the same order
     * (P5) — a caller comparing before and after must not have to sort first. Capped because each
     * digest is a real render (SH249) and a shared region can reach hundreds of pages; the note
     * says what was left out, since an unreported cut reads as a complete answer (SH138).</p>
     */
    private List<ShAgentChangeSet.PageDigest> digestsOf(ShAgentChangeSet changes, Integer pages) {
        int ceiling = maxDigests(pages);
        Set<String> addresses = new TreeSet<>();
        boolean cut = false;
        for (String touched : postAddresses(changes, row -> true)) {
            for (String affected : affectedPages(touched)) {
                if (addresses.size() >= ceiling) {
                    cut = true;
                    break;
                }
                addresses.add(affected);
            }
        }
        List<ShAgentChangeSet.PageDigest> out = new ArrayList<>();
        for (String address : addresses) {
            try {
                ShAgentDigest digest = shAgentRenderService.digest(address, null);
                // SH301 — the count travels only when it is non-zero, so a clean run is byte-identical
                // to the pre-SH301 response and the caller's `?digest=<n>` does not multiply.
                out.add(new ShAgentChangeSet.PageDigest(digest.address(), digest.source(),
                        digest.digest(), digest.appearanceDigest(),
                        digest.markers() != null && digest.markers().any() ? digest.markers() : null));
            } catch (RuntimeException e) {
                // A page that cannot be digested is not a reason to fail a write that already
                // committed — the same rule the lint half follows. It is simply absent, and the
                // caller sees fewer rows than addresses.
                continue;
            }
        }
        if (cut) {
            out.add(new ShAgentChangeSet.PageDigest(
                    "(more affected pages than the " + ceiling + "-page ceiling)", null,
                    null, null));
        } else if (pages != null && pages > ceiling) {
            // SH319 — the narrowing the caller could not otherwise see. The cut row above fires
            // when the *pages* run out; this one when the *request* was reduced, and before it
            // nothing distinguished "three is all there was" from "you asked for two hundred and
            // this instance disagreed". Reported once rather than per row (SH146's shape), and only
            // in the branch where the cut row is absent, so the two can never both claim the tail.
            out.add(new ShAgentChangeSet.PageDigest(
                    "(asked for " + pages + " pages; this instance's ceiling is " + ceiling
                            + ", and all " + out.size() + " affected page(s) are here)",
                    null, null, null));
        }
        return out;
    }

    /**
     * The pages one written address stands for.
     *
     * <p>A page stands for itself. A template stands for the pages that render through it, which is
     * SH193's inversion — and the reason this returns addresses rather than counts is that the
     * caller wants to compare hashes, so it needs to know <em>which</em> page each one belongs
     * to.</p>
     */
    private List<String> affectedPages(String address) {
        Optional<ShPostUnified> post = post(address);
        if (post.isEmpty()) {
            return List.of();
        }
        Optional<ShTemplateGraph.ShTemplateImpact> impact = shTemplateGraph.impactOf(post.get());
        if (impact.isEmpty()) {
            // Not a template, so it is (or is not) a page in its own right; the digest decides,
            // and a post whose type has no layout binding answers with SH249's labelled fallback.
            return List.of(address);
        }
        ShSite site = post.get().getShSite();
        if (site == null || impact.get().postTypes().isEmpty()) {
            return List.of();
        }
        List<String> pages = new ArrayList<>();
        for (ShPostUnified page : shPostUnifiedRepository
                .findByShSite_IdAndStateIn(site.getId(), List.of(ShPostState.DRAFT))) {
            String typeName = page.getShPostType() == null ? null : page.getShPostType().getName();
            if (typeName != null && impact.get().postTypes().contains(typeName)
                    && page.getFurl() != null && !page.getFurl().isBlank()) {
                pages.add(shAddressResolver.addressOf(page).toString());
            }
        }
        return pages;
    }

    private Optional<ShPostUnified> post(String address) {
        try {
            String id = shAddressResolver.resolve(address).id();
            return shPostUnifiedRepository.findById(id).stream()
                    .filter(row -> row.getState() == ShPostState.DRAFT)
                    .findFirst()
                    .or(() -> shPostUnifiedRepository.findById(id).stream().findFirst());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static List<String> postAddresses(ShAgentChangeSet changes,
                                              java.util.function.Predicate<ShAgentChangeSet.Change> keep) {
        return changes.results().stream()
                .filter(row -> row.address() != null && row.address().startsWith("post:"))
                .filter(keep)
                .map(ShAgentChangeSet.Change::address)
                .distinct()
                .toList();
    }
}
