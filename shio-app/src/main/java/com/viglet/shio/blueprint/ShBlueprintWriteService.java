/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import com.viglet.shio.api.agent.ShAgentDesiredStateService;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.dto.ShAgentDesiredState;
import com.viglet.shio.api.post.type.ShPostTypeAdminService;
import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.blueprint.dto.ShBlueprintApplied;

/**
 * The transactional half of applying a blueprint (SH93): provision the model, then
 * converge the content.
 *
 * <h2>One transaction, both halves</h2>
 *
 * <p>Post-types and content commit together. A blueprint whose content failed but whose
 * post-types survived would leave an instance carrying a model nobody asked for and no
 * pages that use it, which is a state the caller then has to clean up by hand — and the
 * usual reason for the failure (a field name the content got wrong) is one the package
 * author fixes and re-applies anyway.</p>
 *
 * <h2>Why the plan writes and then rolls back</h2>
 *
 * <p>The content planner resolves every post's type against the live model, so a plan for
 * a package that brings its own post-types would fail on its first post with "no
 * post-type named Article" — the plan would be unusable for exactly the case a blueprint
 * exists for. So {@code plan} provisions the model inside its transaction, plans the
 * content against it, and marks the transaction rollback-only.</p>
 *
 * <p>This is the technique {@code ShAgentBatchService} deliberately <em>refuses</em> for
 * content, and the difference is worth stating because the contradiction is only
 * apparent. Content writes emit SH51 audit rows, SH12 webhooks, lifecycle events and
 * quota movements — side effects that have already happened by the time a rollback is
 * decided, so a plan that produced them would perturb the instance it claims to only be
 * describing. Provisioning a post-type emits none of those: it inserts rows that the
 * rollback removes, and nothing outside the transaction ever observes them. The content
 * half of the plan is still a true dry run, and writes nothing at all.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShBlueprintWriteService {

    private final ShPostTypeAdminService shPostTypeAdminService;
    private final ShAgentDesiredStateService shAgentDesiredStateService;

    public ShBlueprintWriteService(ShPostTypeAdminService shPostTypeAdminService,
                                   ShAgentDesiredStateService shAgentDesiredStateService) {
        this.shPostTypeAdminService = shPostTypeAdminService;
        this.shAgentDesiredStateService = shAgentDesiredStateService;
    }

    /** What one run of the two halves produced. */
    public record Written(List<ShBlueprintApplied.PostType> postTypes, ShAgentChangeSet content) {
    }

    /**
     * Plans both halves, writes nothing that survives.
     *
     * <p>The rollback is marked at the end rather than by throwing, so the plan is
     * returned normally: a caller asking "what would this do" gets an answer, not an
     * exception it has to interpret as success.</p>
     */
    @Transactional
    public Written plan(List<ShPostTypeDef> defs, ShAgentDesiredState desired, String actor) {
        try {
            List<ShBlueprintApplied.PostType> postTypes = provision(defs);
            ShAgentChangeSet content = declaresNothing(desired)
                    ? empty(true)
                    : shAgentDesiredStateService.plan(desired, false, actor);
            return new Written(postTypes, content);
        } finally {
            // Local rollback-only: Spring rolls this transaction back on return without
            // raising UnexpectedRollbackException, which a global mark would.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }

    /** Applies both halves, atomically. */
    @Transactional
    public Written apply(List<ShPostTypeDef> defs, ShAgentDesiredState desired, String actor) {
        List<ShBlueprintApplied.PostType> postTypes = provision(defs);
        ShAgentChangeSet content = declaresNothing(desired)
                ? empty(false)
                : shAgentDesiredStateService.apply(desired, false, actor);
        return new Written(postTypes, content);
    }

    /**
     * Provisions the model — creating what is missing, touching nothing that exists.
     *
     * <p>{@code provisionFromDef} rather than {@code upsert} on purpose (see
     * {@link ShBlueprintApplied}): a blueprint seeds a model, it does not own one. An
     * upsert would revert a field a human added, and would flip SH66's {@code managed}
     * flag so the console refused to let them add it again.</p>
     */
    private List<ShBlueprintApplied.PostType> provision(List<ShPostTypeDef> defs) {
        List<ShBlueprintApplied.PostType> rows = new ArrayList<>(defs.size());
        for (ShPostTypeDef def : defs) {
            rows.add(shPostTypeAdminService.provisionFromDef(def)
                    ? ShBlueprintApplied.PostType.created(def.name())
                    : ShBlueprintApplied.PostType.existing(def.name(), missingFields(def)));
        }
        return List.copyOf(rows);
    }

    /**
     * Fields the package expects on an existing post-type and does not find.
     *
     * <p>Top-level fields only. A nested Relator child that differs is a real mismatch too,
     * but reporting a field path per level would turn one honest line into a diff, and the
     * line's job is to tell the caller that these two models are not the same model.</p>
     */
    private List<String> missingFields(ShPostTypeDef def) {
        Set<String> live = shPostTypeAdminService.get(def.name())
                .map(existing -> existing.fields().stream()
                        .map(ShPostTypeAttrDef::name)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .orElseGet(LinkedHashSet::new);
        if (live.isEmpty()) {
            return List.of();
        }
        return def.fields() == null ? List.of() : def.fields().stream()
                .map(ShPostTypeAttrDef::name)
                .filter(name -> name != null && !live.contains(name))
                .toList();
    }

    /**
     * A model-only blueprint declares no content, and an empty batch is a 400 by design
     * (SH81) — so it is never sent one. Reported as a change set with zero ops rather
     * than as an absent section, because "there was no content to converge" is an answer.
     */
    private static boolean declaresNothing(ShAgentDesiredState desired) {
        return (desired.folders() == null || desired.folders().isEmpty())
                && (desired.posts() == null || desired.posts().isEmpty());
    }

    private static ShAgentChangeSet empty(boolean dryRun) {
        return new ShAgentChangeSet(dryRun, 0, 0, List.of());
    }
}
