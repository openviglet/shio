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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.ShAgentDesiredStateService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
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
 * <h2>Why the plan no longer writes at all (SH153)</h2>
 *
 * <p>The content planner resolves every post's type against the live model, so a plan for
 * a package that brings its own post-types fails on its first post with "no post-type named
 * Article" — unusable for exactly the case a blueprint exists for. The first answer was to
 * provision the model inside the plan's transaction and mark it rollback-only. It was
 * accurate, and nothing outside the transaction observed it: provisioning a post-type emits
 * no SH51 audit rows, no SH12 webhooks, no lifecycle events and no quota movement, which is
 * why it was defensible where {@code ShAgentBatchService} refuses the same technique for
 * content.</p>
 *
 * <p>What it cost is a property nobody asked to give up: <b>a plan could not run read-only</b>
 * — not on a read-only credential, not on a replica, not inside a {@code readOnly}
 * transaction. For a surface whose whole safety story is "look before you write", that is the
 * wrong constraint to leave in place. So the plan now <em>declares</em> what it would create
 * — the site, and each post-type with the fields it would carry — and the batch planner
 * resolves against that declaration ({@link ShAgentBatchService.Declared}). Same report, no
 * write, and the declaration is honoured on a dry run only, so it can never stand in for an
 * apply that did not happen.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShBlueprintWriteService {

    private final ShPostTypeAdminService shPostTypeAdminService;
    private final ShAgentDesiredStateService shAgentDesiredStateService;
    private final ShAgentBatchService shAgentBatchService;

    public ShBlueprintWriteService(ShPostTypeAdminService shPostTypeAdminService,
                                   ShAgentDesiredStateService shAgentDesiredStateService,
                                   ShAgentBatchService shAgentBatchService) {
        this.shPostTypeAdminService = shPostTypeAdminService;
        this.shAgentDesiredStateService = shAgentDesiredStateService;
        this.shAgentBatchService = shAgentBatchService;
    }

    /**
     * @param site the site this apply created (SH151), or {@code null} when it was already there
     */
    public record Written(List<ShBlueprintApplied.PostType> postTypes, ShAgentChangeSet content,
                          String site) {
    }

    /**
     * Plans both halves, and writes nothing at all (SH153).
     *
     * <p>{@code readOnly} is the assertion, not a hint: if any of this ever starts writing
     * again the transaction itself refuses it, which is a guard no test has to remember.</p>
     */
    @Transactional(readOnly = true)
    public Written plan(List<ShPostTypeDef> defs, ShAgentDesiredState desired, String actor,
                        Options options) {
        // Runs as a dry run, so it checks what a real one would refuse — an SH2-scoped
        // credential inventing a site — and reports the create without making it.
        String site = createSiteIfAsked(desired, options, actor, true);
        List<ShBlueprintApplied.PostType> postTypes = provision(defs, options, true);
        ShAgentChangeSet content = declaresNothing(desired)
                ? empty(true)
                : shAgentDesiredStateService.plan(desired, false, actor,
                        declaredBy(defs, options, site));
        return new Written(postTypes, content, site);
    }

    /** Applies both halves, atomically. */
    @Transactional
    public Written apply(List<ShPostTypeDef> defs, ShAgentDesiredState desired, String actor,
                         Options options) {
        String site = createSiteIfAsked(desired, options, actor, false);
        List<ShBlueprintApplied.PostType> postTypes = provision(defs, options, false);
        ShAgentChangeSet content = declaresNothing(desired)
                ? empty(false)
                : shAgentDesiredStateService.apply(desired, false, actor);
        return new Written(postTypes, content, site);
    }

    /**
     * What the plan may take as already there: the site this run would create, and each
     * post-type with the fields it would end up carrying (SH153).
     *
     * <h2>The three cases, and why they are not one case</h2>
     *
     * <ul>
     *   <li><b>The type is absent</b> — the run provisions it, so every field it declares is
     *       available to the content half. Nested names are included, because
     *       {@code post.upsert}'s SH168 check reads Tab groups and Relator children too.</li>
     *   <li><b>The type exists and {@code mergePostTypes} is on</b> — only the fields a merge
     *       would actually append, asked of the one method that decides that
     *       ({@code mergeableFieldNames}), so the plan cannot promise a field the apply
     *       skips.</li>
     *   <li><b>The type exists and merging is off</b> — <em>nothing</em> is declared. This is
     *       the case it would be easiest to get wrong: declaring the package's fields here
     *       would silence SH168's "a real run will refuse this write" finding, and the plan
     *       would come back green for a run that fails. A plan may never be more optimistic
     *       than the apply it predicts.</li>
     * </ul>
     */
    private ShAgentBatchService.Declared declaredBy(List<ShPostTypeDef> defs,
                                                    Options options, String site) {
        Map<String, Set<String>> types = new LinkedHashMap<>();
        for (ShPostTypeDef def : defs) {
            if (def == null || def.name() == null) {
                continue;
            }
            if (shPostTypeAdminService.get(def.name()).isEmpty()) {
                types.put(def.name(), fieldNames(def.fields()));
            } else if (options.mergePostTypes()) {
                types.put(def.name(),
                        new LinkedHashSet<>(shPostTypeAdminService.mergeableFieldNames(def)));
            }
        }
        // `site` is non-null only when this run would create it, which is exactly when the
        // planner has nothing live to resolve against.
        return new ShAgentBatchService.Declared(site, types);
    }

    /** Every field name a def declares, nested ones included. */
    private static Set<String> fieldNames(List<ShPostTypeAttrDef> fields) {
        Set<String> names = new LinkedHashSet<>();
        collectFieldNames(fields, names);
        return names;
    }

    private static void collectFieldNames(List<ShPostTypeAttrDef> fields, Set<String> into) {
        if (fields == null) {
            return;
        }
        for (ShPostTypeAttrDef field : fields) {
            if (field.name() != null && !field.name().isBlank()) {
                into.add(field.name());
            }
            collectFieldNames(field.fields(), into);
        }
    }

    /**
     * Creates the target site when the caller asked for it and it is not there (SH151).
     *
     * <p>Goes through the batch surface's own {@code site.upsert} rather than calling the write
     * service directly, so one op decides what creating a site means: blank, never an update, and
     * refused for a credential with an SH2 allow-list. A blueprint is not a second answer to that
     * question (P10).</p>
     *
     * <p>Run as a dry run when the caller is planning (SH153), which is what keeps the plan
     * both accurate and read-only: the op still refuses an SH2-scoped credential and still
     * reports the create, and it makes nothing. The name it returns is then what the content
     * plan resolves its addresses against.</p>
     *
     * @return the site name when this call created it (or would), or {@code null}
     */
    private String createSiteIfAsked(ShAgentDesiredState desired, Options options, String actor,
                                     boolean dryRun) {
        if (!options.createSite() || desired == null || desired.site() == null) {
            return null;
        }
        ShAgentChangeSet result = shAgentBatchService.run(new ShAgentBatch(dryRun, List.of(
                new ShAgentBatch.Op(ShAgentBatchService.SITE_UPSERT,
                        ShAddress.site(desired.site()).toString(), null, null, null))),
                false, actor);
        boolean created = result.results().stream().anyMatch(row -> !"noop".equals(row.action()));
        return created ? desired.site() : null;
    }

    /**
     * What this apply is allowed to change beyond the package's own content (SH151, SH152).
     *
     * <p>Both are opt-in, and both are the kind of side effect that has to be asked for rather
     * than inferred: one creates a site, the other adds fields to a post-type the instance
     * already had.</p>
     */
    public record Options(boolean createSite, boolean mergePostTypes) {

        public static final Options NONE = new Options(false, false);
    }

    /**
     * Provisions the model — creating what is missing, touching nothing that exists.
     *
     * <p>{@code provisionFromDef} rather than {@code upsert} on purpose (see
     * {@link ShBlueprintApplied}): a blueprint seeds a model, it does not own one. An
     * upsert would revert a field a human added, and would flip SH66's {@code managed}
     * flag so the console refused to let them add it again.</p>
     */
    private List<ShBlueprintApplied.PostType> provision(List<ShPostTypeDef> defs, Options options,
                                                        boolean dryRun) {
        List<ShBlueprintApplied.PostType> rows = new ArrayList<>(defs.size());
        for (ShPostTypeDef def : defs) {
            // On a plan the question is asked instead of answered by doing it (SH153). Same
            // three outcomes, same order, no rows written — and `missing` is computed the same
            // way, so a plan's row and an apply's row differ only in having happened.
            boolean absent = dryRun
                    ? shPostTypeAdminService.get(def.name()).isEmpty()
                    : shPostTypeAdminService.provisionFromDef(def);
            if (absent) {
                rows.add(ShBlueprintApplied.PostType.created(def.name()));
                continue;
            }
            if (!options.mergePostTypes()) {
                rows.add(ShBlueprintApplied.PostType.existing(def.name(), missingFields(def)));
                continue;
            }
            // SH152 — add what the existing type lacks, and report it as a merge rather than as a
            // provision: the type is now a hybrid of two models and the response has to say so.
            List<String> added = dryRun
                    ? shPostTypeAdminService.mergeableFieldNames(def)
                    : shPostTypeAdminService.mergeMissingFields(def);
            // `missing` is what would *remain* after the merge, which is why a plan reports it
            // empty rather than re-listing `added`: on an apply it is recomputed after the write
            // and comes back empty too, and a row that said "adding sections · missing sections"
            // would describe a merge that failed.
            List<String> remaining = dryRun ? List.of() : missingFields(def);
            rows.add(added.isEmpty()
                    ? ShBlueprintApplied.PostType.existing(def.name(), missingFields(def))
                    : ShBlueprintApplied.PostType.merged(def.name(), added, remaining));
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
