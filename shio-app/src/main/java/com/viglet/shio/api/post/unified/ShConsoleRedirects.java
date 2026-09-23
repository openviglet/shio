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

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * SH1064 — the console's half of "keep the old address answering", composed rather than copied.
 *
 * <h2>Why this is four lines of op-building and not a redirect writer</h2>
 *
 * <p>SH1021 settled the shape one verb over: the console's bulk publish builds ops and hands them
 * to {@link ShAgentBatchService} rather than looping in a console service, because a second
 * implementation is how two surfaces come to mean different things by one word. A redirect has
 * more of that to disagree about than most writes — whether it is published, which status it
 * carries, what happens when a page already answers there — so the console asks for the op the
 * agent asks for, and every one of those decisions has exactly one home.</p>
 *
 * <p>Silent when there is nothing to redirect: a publish that moved no URL calls this with
 * {@code null} and never reaches it, and a post whose site cannot be resolved is a state a
 * redirect cannot be addressed in. Neither is worth failing a publish that has already happened.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShConsoleRedirects {

    /**
     * 301, and this is the one caller that asks for one.
     *
     * <p>SH385 makes 302 the default because a permanent redirect is cached effectively for ever
     * and a wrong one is the hardest routing mistake to take back. The opt-in belongs to whoever
     * is making the claim, and here that is a curator who has read "4 published pages link to
     * /pricing" and ticked a box that says <em>permanent</em> — which is the informed choice
     * SH385's rule was holding the default open for.</p>
     */
    private static final JsonNode PERMANENT = JsonNodeFactory.instance.objectNode()
            .put("permanent", true);

    private final ShAgentBatchService shAgentBatchService;
    private final ShPostUnifiedRepository shPostUnifiedRepository;

    public ShConsoleRedirects(ShAgentBatchService shAgentBatchService,
                              ShPostUnifiedRepository shPostUnifiedRepository) {
        this.shAgentBatchService = shAgentBatchService;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
    }

    /**
     * Writes the redirect a just-published rename left behind.
     *
     * @param postId  the post that has just been published at its new URL
     * @param vacated the URL it answered at until a moment ago
     * @param actor   the curator, recorded as the author of the redirect
     */
    @Transactional
    public void after(String postId, String vacated, String actor) {
        Optional<ShPostUnified> live = shPostUnifiedRepository
                .findByIdAndState(postId, ShPostState.PUBLISHED);
        if (live.isEmpty() || live.get().getShSite() == null || live.get().getFurl() == null) {
            return;
        }
        String site = live.get().getShSite().getName();
        try {
            shAgentBatchService.run(new ShAgentBatch(false, List.of(new ShAgentBatch.Op(
                    ShAgentBatchService.POST_REDIRECT,
                    ShAddress.post(site, vacated).toString(), null, null,
                    ShAddress.post(site, live.get().getFurl()).toString(), PERMANENT))),
                    true, actor);
        } catch (ShTeachingException refused) {
            // The publish committed in its own transaction and has already happened, so the
            // refusal has to say that: a curator told "this failed" about a page that is live is
            // the P7 surprise, and one told nothing at all believes the old URL still answers.
            throw new RedirectNotWrittenException(vacated, refused);
        }
    }

    /** The publish landed and the redirect did not — said in one sentence (SH1064). */
    public static class RedirectNotWrittenException extends ShTeachingException {

        public RedirectNotWrittenException(String vacated, ShTeachingException cause) {
            super(ShProblem.of("redirect-not-written", HttpStatus.CONFLICT,
                            "Published — but '" + vacated + "' was not kept answering")
                    .withDetail("The page is live at its new address. The redirect was refused: "
                            + cause.getMessage())
                    .withFix("Fix what the refusal names and write the redirect on its own with a "
                            + "post.redirect op, or `shio redirects` to see what is missing. "
                            + "Nothing about the publish needs repeating."));
        }
    }
}
