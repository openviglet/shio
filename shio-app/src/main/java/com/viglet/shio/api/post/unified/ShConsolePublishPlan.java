/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAddressResolver;
import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.gate.ShPublishGateService;
import com.viglet.shio.api.post.ShPostDrift;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * What the console's Publish would do, without doing it (SH996).
 *
 * <h2>One contract, two callers</h2>
 *
 * <p>The answer is {@link ShAgentChangeSet} — the shape {@code POST /agent/batch} returns for a dry
 * run — rather than a console-shaped preview. The console and the agent are asking the same question
 * about the same act, and a second shape would be a second description of publishing to keep in step:
 * the day they disagreed, the surface with the human on it would be the one that was wrong.</p>
 *
 * <p>It reports the same three outcomes the batch path does, and for the same reasons: a draft that
 * matches what is live is a {@code noop} (re-publishing unchanged content must not claim to have
 * published), a post with no draft is a {@code noop} naming why, and everything else is a
 * {@code publish}. What it deliberately does not carry is the routes — those are on
 * {@code /agent/read?include=impact}, which the console already has, and computing them twice would
 * be the P5 split this class exists to avoid.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShConsolePublishPlan {

    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShAddressResolver shAddressResolver;
    private final ShPublishGateService shPublishGateService;
    private final ObjectMapper objectMapper;
    /** SH1009 — the one evaluator of "is this page complete", shared with the writer. */
    private final ShPostUnifiedWriteService shPostUnifiedWriteService;

    public ShConsolePublishPlan(ShPostUnifiedRepository shPostUnifiedRepository,
                                ShAddressResolver shAddressResolver,
                                ShPublishGateService shPublishGateService,
                                ObjectMapper objectMapper,
                                ShPostUnifiedWriteService shPostUnifiedWriteService) {
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shAddressResolver = shAddressResolver;
        this.shPublishGateService = shPublishGateService;
        this.objectMapper = objectMapper;
    }

    /** The gate that would stop this publish, as one sentence, or {@code null} (SH997). */
    private String blockedBy(String postId) {
        return shPublishGateService.evaluatePost(postId).stream()
                .filter(ShPublishGateService.Outcome::blocked)
                .findFirst()
                .map(outcome -> "gate '" + outcome.gate() + "' stops this: "
                        + outcome.failures().get(0).check())
                .orElse(null);
    }

    /**
     * SH1064 — the address this publish is about to stop serving, or {@code null}.
     *
     * <p>The live row and the draft are separate rows with their own {@code furl} (SH81), so a
     * draft whose URL differs from the one on the page visitors see is a publish that moves the
     * live address. That is the {@code stops} the plan already reports; this is the same fact for
     * the writer rather than for the reader, and it is read <b>before</b> the publish because
     * afterwards both rows carry the new URL and the old one belongs to nobody.</p>
     */
    @Transactional(readOnly = true)
    public String vacatedUrl(String postId) {
        String draft = shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
                .map(ShPostUnified::getFurl).orElse(null);
        String live = shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED)
                .map(ShPostUnified::getFurl).orElse(null);
        if (draft == null || live == null || live.isBlank() || live.equalsIgnoreCase(draft)) {
            return null;
        }
        return live;
    }

    /** The plan for publishing one post, by console id. */
    @Transactional(readOnly = true)
    public ShAgentChangeSet of(String postId) {
        Optional<ShPostUnified> draft = shPostUnifiedRepository
                .findByIdAndState(postId, ShPostState.DRAFT);
        Optional<ShPostUnified> live = shPostUnifiedRepository
                .findByIdAndState(postId, ShPostState.PUBLISHED);
        ShPostUnified subject = draft.or(() -> live).orElse(null);
        String address = subject == null ? "id:" + postId
                : shAddressResolver.addressOf(subject).toString();

        ShAgentChangeSet.Change change;
        if (draft.isEmpty()) {
            change = new ShAgentChangeSet.Change(ShAgentBatchService.POST_PUBLISH, address,
                    ShAgentChangeSet.Change.NOOP, "no draft to publish", postId);
        } else if (live.isPresent() && ShPostDrift.sameContent(objectMapper, draft.get(), live.get())) {
            change = new ShAgentChangeSet.Change(ShAgentBatchService.POST_PUBLISH, address,
                    ShAgentChangeSet.Change.NOOP, "already live with this content", postId);
        } else {
            change = new ShAgentChangeSet.Change(ShAgentBatchService.POST_PUBLISH, address,
                    ShAgentChangeSet.Change.PUBLISH,
                    live.isPresent() ? null : "this URL goes live for the first time", postId);
        }
        // SH997 — a plan that could not say which gate would stop it is a plan that promises what the
        // run refuses. Evaluated here rather than in the console, because the gates are the server's.
        String stopped = blockedBy(postId);
        if (stopped != null) {
            change = new ShAgentChangeSet.Change(change.op(), change.address(), change.action(),
                    (change.detail() == null ? "" : change.detail() + "; ") + stopped, postId);
        }
        // SH1009 — and the same for a field the type says must be filled in. The preview is where a
        // curator finds out, which is the whole reason it exists: the alternative is pressing
        // Publish and reading the refusal.
        List<String> incomplete = draft.map(shPostUnifiedWriteService::publishBlockers)
                .orElseGet(List::of);
        if (!incomplete.isEmpty()) {
            change = new ShAgentChangeSet.Change(change.op(), change.address(), change.action(),
                    (change.detail() == null ? "" : change.detail() + "; ")
                            + "this publish is refused: " + String.join("; ", incomplete), postId);
        }
        return new ShAgentChangeSet(true, 1, change.isChange() ? 1 : 0, List.of(change));
    }
}
