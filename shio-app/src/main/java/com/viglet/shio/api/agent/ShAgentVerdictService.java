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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.dto.ShAgentVerdict;
import com.viglet.shio.persistence.model.agent.ShAgentRun;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.review.ShReviewDecision;
import com.viglet.shio.persistence.repository.agent.ShAgentRunRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.review.ShReviewDecisionRepository;

/**
 * What a curator decided about this credential's own runs (SH1002).
 *
 * <h2>Read-only, and that is the whole design</h2>
 *
 * <p>An agent approving its own work is a standing non-goal, and the review surface stays off
 * {@code /agent/**} for exactly that reason — SH109's own javadoc says so and the manifest asserts
 * the approve route is absent from its endpoint index. None of that changes here. What was missing
 * is the other direction: a revert leaves ordinary restore and delete audit rows that look like a
 * curator's own edit, and an approve leaves publish rows, so a session that came back had no way to
 * learn that a page it wrote was rejected, or why. Every correction needed a person to re-explain
 * it, which is a loop with a human in the only part a machine could have done.</p>
 *
 * <h2>Scoped to the credential, never to everything</h2>
 *
 * <p>A run records the principal whose credential it used (SH999), and this answers only about runs
 * matching the caller's. That is a narrower claim than "the review queue, read-only": a rejection's
 * note is written for whoever is going to fix the page, and a credential that did not write it has
 * no business reading what a curator said about somebody else's work.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShAgentVerdictService {

    private final ShAgentRunRepository shAgentRunRepository;
    private final ShReviewDecisionRepository shReviewDecisionRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShAddressResolver shAddressResolver;

    public ShAgentVerdictService(ShAgentRunRepository shAgentRunRepository,
                                 ShReviewDecisionRepository shReviewDecisionRepository,
                                 ShPostUnifiedRepository shPostUnifiedRepository,
                                 ShAddressResolver shAddressResolver) {
        this.shAgentRunRepository = shAgentRunRepository;
        this.shReviewDecisionRepository = shReviewDecisionRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shAddressResolver = shAddressResolver;
    }

    /**
     * Every verdict on this credential's runs, newest first.
     *
     * @param decision {@code approve}, {@code reject} or {@code hold} to narrow; {@code null} for
     *                 all of them. A resumed session almost always wants {@code reject}: the rest
     *                 it can see in the content
     * @param limit    how many, newest first
     */
    @Transactional(readOnly = true)
    public List<ShAgentVerdict> mine(String decision, Integer limit) {
        String actor = ShAgentSessionContext.actor();
        Set<String> sessions = shAgentRunRepository.findByActor(actor).stream()
                .map(ShAgentRun::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (sessions.isEmpty()) {
            return List.of();
        }
        String wanted = decision == null || decision.isBlank()
                ? null : decision.trim().toUpperCase(Locale.ROOT);
        List<ShReviewDecision> rows = shReviewDecisionRepository.findBySessionIn(sessions).stream()
                .filter(row -> wanted == null || wanted.equals(row.getDecision()))
                .sorted(Comparator.comparing(ShReviewDecision::getDecidedAt).reversed())
                .limit(limit == null || limit <= 0 ? 50 : Math.min(limit, 200))
                .toList();
        List<ShAgentVerdict> verdicts = new ArrayList<>(rows.size());
        for (ShReviewDecision row : rows) {
            verdicts.add(new ShAgentVerdict(row.getSession(), addressOf(row.getPostId()),
                    row.getDecision(), row.getNote(), row.getActor(), row.getDecidedAt()));
        }
        return List.copyOf(verdicts);
    }

    /**
     * The page's address, or {@code null} when it is gone.
     *
     * <p>Absent rather than the id, and that is the useful direction: a verdict names a page an
     * agent has to go and fix, so the address is the actionable half — and a rejection whose page
     * has since been deleted is a verdict there is nothing left to do about.</p>
     *
     * <p>ShFolderSite-exempt: the resolver walks the folder chain itself.</p>
     */
    private String addressOf(String postId) {
        return shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
                .or(() -> shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED))
                .map(post -> Optional.ofNullable(shAddressResolver.addressOf(post))
                        .map(Object::toString).orElse(null))
                .orElse(null);
    }
}
