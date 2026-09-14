/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAgentPostDigest;
import com.viglet.shio.api.agent.ShStaleExpectationException;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

/**
 * SH985 — one concurrency contract, honoured by both writers.
 *
 * <h2>The half that was missing</h2>
 *
 * <p>SH721 gave the agent {@code expect}, and its reasoning was about a curator: "a curator
 * patching one field while an agent runs {@code post.upsert} gets 200, the agent gets
 * {@code changed=1}, and the curator's field is gone". The fix protected one direction. The console
 * kept sending {@code PUT} and {@code PATCH} with no version at all, so the same collision with the
 * roles swapped still ended with the agent's write silently discarded — and the agent is the writer
 * whose work has a review queue and an audit row, which is what makes that the worse direction.</p>
 *
 * <h2>The same digest, not a second one</h2>
 *
 * <p>The entity tag here is {@link ShAgentPostDigest} in HTTP clothing. A second version stamp
 * would be a second answer to "has this changed", and the two would disagree the first time a field
 * was added to one of them. So a console caller and an agent are comparing the same value, and an
 * agent that read through {@code /agent/read} can hand its digest to a console write.</p>
 *
 * <h2>Opt-in on the wire, always sent by the console</h2>
 *
 * <p>An absent {@code If-Match} is last-write-wins, exactly as an absent {@code expect} is — a
 * mandatory header would refuse every caller written before today, including the CDA's own tests,
 * and P6 says a refusal must be actionable rather than merely correct. The guarantee comes from the
 * console holding the tag from its load and sending it on save, which is where the defect was.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShConsolePostVersion {

    /** What a caller sends to mean "whatever is there now" — RFC 9110's own wildcard. */
    private static final String ANY = "*";

    private final ShPostUnifiedRepository shPostUnifiedRepository;

    public ShConsolePostVersion(ShPostUnifiedRepository shPostUnifiedRepository) {
        this.shPostUnifiedRepository = shPostUnifiedRepository;
    }

    /**
     * The entity tag for one row, quoted, or {@code null} when there is no such row.
     *
     * @param id    the post
     * @param state which snapshot the caller read
     */
    @Transactional(readOnly = true)
    public String etagOf(String id, ShPostState... states) {
        return quoted(subject(id, states));
    }

    /**
     * The tag a write answers with, so a second save needs no second read.
     *
     * <p>Without it the console would hold the tag from its load, save once, and be refused on the
     * next save by the write it made itself — a guard that fires on its own writer is worse than
     * none, because the only way out a caller would find is to stop sending the header.</p>
     *
     * <p>Spelled out rather than delegating to {@link #etagOf}: a {@code @Transactional} method
     * calling another one on {@code this} goes around the proxy, and this codebase has a lint
     * about exactly that pair.</p>
     */
    @Transactional(readOnly = true)
    public String etagAfterWriting(String id) {
        return quoted(subject(id, ShPostState.DRAFT, ShPostState.PUBLISHED));
    }

    private static String quoted(Optional<ShPostUnified> row) {
        return row.map(ShAgentPostDigest::of).map(digest -> "\"" + digest + "\"").orElse(null);
    }

    /**
     * Refuse a write whose {@code If-Match} names a row that has changed since it was read.
     *
     * <p><b>Any</b> of the named states may match, not the first one that exists. The writes that
     * name a single state are unaffected; the one that names two is the delete, which is about the
     * post rather than about a snapshot — and a caller holding the tag of the live row for a post
     * that also has a draft has still proved it read the post as it is now. Matching only the
     * draft there would refuse a correct caller, which is the failure that teaches people to stop
     * sending the header.</p>
     *
     * @param ifMatch  the header, absent or blank for an unconditional write
     * @param id       the post being written
     * @param subjects the rows the write is about, the first being the one a mismatch reports
     */
    @Transactional(readOnly = true)
    public void require(String ifMatch, String id, ShPostState... subjects) {
        if (ifMatch == null || ifMatch.isBlank() || ANY.equals(ifMatch.trim())) {
            return;
        }
        String expected = unquoted(ifMatch);
        for (ShPostState state : subjects.length == 0 ? ShPostState.values() : subjects) {
            String digest = shPostUnifiedRepository.findByIdAndState(id, state)
                    .map(ShAgentPostDigest::of).orElse(null);
            if (expected.equals(digest)) {
                return;
            }
        }
        throw ShStaleExpectationException.consolePost(id, expected,
                subject(id, subjects).map(ShAgentPostDigest::of).orElse(null));
    }

    private Optional<ShPostUnified> subject(String id, ShPostState... subjects) {
        for (ShPostState state : subjects.length == 0 ? ShPostState.values() : subjects) {
            Optional<ShPostUnified> row = shPostUnifiedRepository.findByIdAndState(id, state);
            if (row.isPresent()) {
                return row;
            }
        }
        return Optional.empty();
    }

    /**
     * The tag's value, without the quotes or the weak marker HTTP wraps it in.
     *
     * <p>Sending back what a {@code GET} handed over is the one thing a caller cannot get wrong, so
     * the quoted form is accepted; so is the bare digest, which is what an agent holds, because a
     * caller that has to know which of two spellings to send will send the other one.</p>
     */
    private static String unquoted(String ifMatch) {
        String value = ifMatch.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        return value.replace("\"", "");
    }
}
