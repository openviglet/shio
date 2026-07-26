/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.safety;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.viglet.shio.api.agent.ShAgentProperties;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * The one place Block L's three gates are asked (SH107, SH108, SH110).
 *
 * <h2>Why they live together rather than at their call sites</h2>
 *
 * <p>All three answer the same question in different currencies — <em>may this credential
 * do this, right now?</em> — and all three have to hold on every write path: REST batch,
 * REST apply, every MCP tool, and the blueprint apply that composes over them. A gate
 * implemented at four call sites is a gate that is missing from the fifth, and the fifth is
 * always the one that ships next. {@link com.viglet.shio.api.agent.ShAgentBatchService} is
 * the single choke point every content write already funnels through (law P10), so the
 * policy is asked there and nowhere else.</p>
 *
 * <h2>Draft-by-default is not enforced here — it is structural</h2>
 *
 * <p>SH107 has two halves. The publish gate is a check, and it is below. "Writes land in
 * DRAFT" is not: the batch's {@code post.upsert} passes {@code ShPostState.DRAFT} and has
 * no parameter that could say otherwise, so there is no code path to guard. A check that
 * can only ever pass is worse than no check, because it implies the alternative exists.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShAgentPolicy {

    private final ShAgentProperties properties;
    private final ShAgentBudgetService budget;
    private final ShAgentConfirmService confirm;

    public ShAgentPolicy(ShAgentProperties properties, ShAgentBudgetService budget,
                         ShAgentConfirmService confirm) {
        this.properties = properties;
        this.budget = budget;
        this.confirm = confirm;
    }

    /**
     * SH107 — the publish gate.
     *
     * <p>Checked on both {@code post.publish} and {@code post.unpublish}, which is not
     * over-reach: the grant is about changing what visitors see, and taking a live page
     * down changes that as surely as putting one up. The half-gate — publish denied,
     * unpublish allowed — would leave a bounded agent able to empty a site.</p>
     *
     * <p>Also checked on a dry run. A plan that quietly omitted the refusal would let the
     * agent build the whole document before failing at apply, which is the expensive place
     * to learn it, and would make plan and apply disagree about the same request.</p>
     *
     * @param verb {@code publish} or {@code unpublish}, for the message
     */
    public void requirePublish(String verb) {
        if (!ShAgentSessionContext.grant().mayPublish()) {
            throw new PublishNotPermittedException(verb);
        }
    }

    /** SH110 — count one agent-surface call. */
    public void countCall() {
        budget.countCall();
    }

    /** SH110 — count the content writes a call is about to make. */
    public void countWrites(int writes) {
        budget.countWrites(writes);
    }

    /**
     * SH108 — the destructive-op gate.
     *
     * <p>Given the plan a request would produce, either let it through or refuse with the
     * instructions for getting a token. The plan is passed in rather than recomputed here
     * because only the caller knows how to produce one, and computing it twice would be two
     * chances for the two computations to disagree.</p>
     *
     * @param plan   the change set a dry run of this exact request produces
     * @param token  the {@code confirm} value the caller sent, possibly {@code null}
     */
    public void requireConfirmation(ShAgentChangeSet plan, String token) {
        if (!properties.getConfirm().isRequired() || !confirm.isDestructive(plan)) {
            return;
        }
        ShAgentConfirmService.Verdict verdict = confirm.verify(token, plan);
        if (verdict != ShAgentConfirmService.Verdict.OK) {
            throw new ConfirmationRequiredException(verdict,
                    confirm.destructiveCount(plan), confirm.destructiveAddresses(plan));
        }
    }

    /** Attaches the confirm token to a plan that would destroy something (SH108). */
    public ShAgentChangeSet withConfirmToken(ShAgentChangeSet plan) {
        String token = properties.getConfirm().isRequired() ? confirm.tokenFor(plan) : null;
        return token == null ? plan : plan.withConfirm(token);
    }

    /** A {@code 403}: this credential writes, but it does not publish. */
    public static class PublishNotPermittedException extends ShTeachingException {

        public PublishNotPermittedException(String verb) {
            super(ShProblem.of("publish-not-permitted", HttpStatus.FORBIDDEN,
                            "This credential may write drafts but may not " + verb)
                    .withDetail("Publishing is the only act that changes what visitors see, so "
                            + "it is granted per credential rather than implied by the ability "
                            + "to write. Everything you have written is saved as a draft and is "
                            + "waiting for a human to publish it.")
                    .withAllowed(List.of("folder.upsert", "folder.delete", "post.upsert",
                            "post.move", "post.delete"))
                    .withFix("Leave the content in DRAFT and tell the human what is ready to go "
                            + "live; they can publish it from the console, or re-issue this "
                            + "token with publish permission.")
                    .withExample("GET /api/v2/agent/session"));
        }
    }

    /**
     * A {@code 409}: a destructive write arrived without the token from its own plan.
     *
     * <p><b>The token is not in this response, on purpose.</b> Including it would turn two
     * steps into one call plus a retry and the human would never see the plan — which is
     * the entire content of SH108. What the response does carry is what would be destroyed,
     * so the agent can render exactly that for the human before asking again.</p>
     */
    public static class ConfirmationRequiredException extends ShTeachingException {

        public ConfirmationRequiredException(ShAgentConfirmService.Verdict verdict, int count,
                                             List<String> addresses) {
            super(ShProblem.of("confirmation-required", HttpStatus.CONFLICT,
                            title(verdict, count))
                    .withDetail(detail(verdict) + " Would be deleted: "
                            + String.join(", ", addresses.size() > 10
                                    ? addresses.subList(0, 10) : addresses)
                            + (addresses.size() > 10 ? ", … (" + addresses.size() + " total)" : "")
                            + ".")
                    .withFix("Re-send this exact request with dryRun (or POST /api/v2/agent/plan) "
                            + "to get its `confirm` token, show the human what it deletes, then "
                            + "send it again with that token in `confirm`.")
                    .withExample("{\"confirm\":\"<the confirm value from the plan>\"}"));
        }

        private static String title(ShAgentConfirmService.Verdict verdict, int count) {
            return switch (verdict) {
                case EXPIRED -> "That confirmation has expired";
                case MISMATCH -> "That confirmation is not for this plan";
                default -> "Deleting " + count + " object(s) needs a confirmation";
            };
        }

        private static String detail(ShAgentConfirmService.Verdict verdict) {
            return switch (verdict) {
                case EXPIRED -> "A confirmation is short-lived because its promise is that the "
                        + "plan was just rendered. Plan again.";
                case MISMATCH -> "The content changed between the plan and this call, or the "
                        + "token came from a different request. A confirmation is bound to "
                        + "exactly what it showed, so that an agent cannot destroy something it "
                        + "did not render for the human.";
                default -> "A destructive write takes two steps: a plan the human can read, then "
                        + "the same request carrying that plan's token.";
            };
        }
    }
}
