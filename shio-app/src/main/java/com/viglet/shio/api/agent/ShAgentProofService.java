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

import org.springframework.stereotype.Service;

import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.verify.ShAgentVerifyService;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;

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

    private final ShAgentVerifyService shAgentVerifyService;

    public ShAgentProofService(ShAgentVerifyService shAgentVerifyService) {
        this.shAgentVerifyService = shAgentVerifyService;
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
