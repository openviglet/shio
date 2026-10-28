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
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.agent.ShAddressResolver;
import com.viglet.shio.api.agent.ShAgentSiteAccess;
import com.viglet.shio.api.agent.dto.ShAgentDesiredState;
import com.viglet.shio.api.agent.memory.ShAgentMemoryService;
import com.viglet.shio.api.agent.memory.dto.ShAgentNoteRequest;
import com.viglet.shio.api.agent.verify.ShAgentVerifyService;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.blueprint.dto.ShBlueprint;
import com.viglet.shio.blueprint.dto.ShBlueprintApplied;
import com.viglet.shio.persistence.model.site.ShSite;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Applying a blueprint (SH93) — the verb, over the format SH92 defines.
 *
 * <h2>What this class actually does</h2>
 *
 * <p>Very little, on purpose. It resolves the parameters, substitutes them into the
 * package, compiles the result into the two shapes the instance already understands — a
 * list of SH16 post-type definitions and one SH82 desired-state document — hands them to
 * the transactional writer, and then asks the blueprint's own promise whether it worked.
 * Every hard part (idempotence, atomicity, the plan, the audit trail, the ACL, the tenant
 * quota) belongs to machinery that shipped in Blocks G and H, which is law P10 read
 * literally: a blueprint is a <em>shape</em> over plan/apply, not a second write path.</p>
 *
 * <h2>Why the content documents are merged into one</h2>
 *
 * <p>A package may split its content across several files for the author's benefit, but
 * they apply as one document. Two documents would be two transactions, so a package could
 * half-apply; and the ordering guarantees the desired-state compiler makes — folders
 * before posts, publishes after every upsert — hold within a document, not across a
 * sequence of them. Merge order is declaration order, so the same package always compiles
 * to the same plan (P5).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShBlueprintService {

    private final ShBlueprintRegistry shBlueprintRegistry;
    private final ShBlueprintWriteService shBlueprintWriteService;
    private final ShAgentSiteAccess shAgentSiteAccess;
    private final ShAddressResolver shAddressResolver;
    private final ShAgentVerifyService shAgentVerifyService;
    private final ShAgentMemoryService shAgentMemoryService;
    private final ShBlueprintCodec codec;
    private final ShBlueprintAssetWriter shBlueprintAssetWriter;

    public ShBlueprintService(ShBlueprintRegistry shBlueprintRegistry,
                              ShBlueprintWriteService shBlueprintWriteService,
                              ShAgentSiteAccess shAgentSiteAccess,
                              ShAddressResolver shAddressResolver,
                              ShAgentVerifyService shAgentVerifyService,
                              ShAgentMemoryService shAgentMemoryService,
                              ShBlueprintCodec codec,
                              ShBlueprintAssetWriter shBlueprintAssetWriter) {
        this.shBlueprintAssetWriter = shBlueprintAssetWriter;
        this.shBlueprintRegistry = shBlueprintRegistry;
        this.shBlueprintWriteService = shBlueprintWriteService;
        this.shAgentSiteAccess = shAgentSiteAccess;
        this.shAddressResolver = shAddressResolver;
        this.shAgentVerifyService = shAgentVerifyService;
        this.shAgentMemoryService = shAgentMemoryService;
        this.codec = codec;
    }

    /** What applying this blueprint would do. Writes nothing that survives. */
    public ShBlueprintApplied plan(String name, String site, JsonNode params, String actor) {
        return run(name, site, params, actor, true, ShBlueprintWriteService.Options.NONE);
    }

    /** Applies it. */
    public ShBlueprintApplied apply(String name, String site, JsonNode params, String actor) {
        return run(name, site, params, actor, false, ShBlueprintWriteService.Options.NONE);
    }

    /** What applying it would do, with the SH151/SH152 opt-ins the caller asked for. */
    public ShBlueprintApplied plan(String name, String site, JsonNode params, String actor,
                                   ShBlueprintWriteService.Options options) {
        return run(name, site, params, actor, true, options);
    }

    /** Applies it, with the SH151/SH152 opt-ins the caller asked for. */
    public ShBlueprintApplied apply(String name, String site, JsonNode params, String actor,
                                    ShBlueprintWriteService.Options options) {
        return run(name, site, params, actor, false, options);
    }

    /**
     * SH1251 — what applying a package the registry does not hold yet would do.
     *
     * <p>For the marketplace's dry run before an install: the package was downloaded and read by
     * {@link ShBlueprintRegistry#readStaged}, and the plan is computed from that same object, so the
     * plan describes the package the confirm will install. Plan only; an apply always goes through
     * the registry, because a package that is not registered is not one this instance carries.</p>
     */
    public ShBlueprintApplied plan(ShBlueprint staged, String site, JsonNode params, String actor,
                                   ShBlueprintWriteService.Options options) {
        return run(staged, site, params, actor, true, options);
    }

    private ShBlueprintApplied run(String name, String site, JsonNode params, String actor,
                                   boolean dryRun, ShBlueprintWriteService.Options options) {
        return run(shBlueprintRegistry.require(name), site, params, actor, dryRun, options);
    }

    private ShBlueprintApplied run(ShBlueprint blueprint, String site, JsonNode params, String actor,
                                   boolean dryRun, ShBlueprintWriteService.Options options) {
        if (site == null || site.isBlank()) {
            throw new MissingSiteException(blueprint.name(), readableSites());
        }
        // Resolved up front so a model-only blueprint (no content, so nothing that would
        // resolve a site later) still refuses an unknown site instead of half-succeeding.
        //
        // SH151: unless the caller asked for it to be created, in which case the name it sent is
        // the name to use — the writer makes the site before either half runs. The refusal stays
        // the default, because "the site you named does not exist" must never be read as
        // permission to make one (P7).
        ShSite target = shAgentSiteAccess.find(site).orElse(null);
        if (target == null && !options.createSite()) {
            // The blueprint's own error rather than the site resolver's, because this one can name
            // the two routes to a site — `createSite`, or a `site.upsert` op — and the resolver
            // knows nothing about either. It still only lists sites this credential can read, so
            // an existing-but-invisible site is a 400 that names what is visible, not a leak.
            throw new MissingSiteException(blueprint.name(), readableSites());
        }
        String siteName = target != null ? target.getName() : site.trim();

        Map<String, String> values =
                ShBlueprintParams.resolve(blueprint.manifest(), params, siteName);
        List<ShPostTypeDef> defs = defs(blueprint, values);
        ShAgentDesiredState desired = desired(blueprint, values, siteName);

        ShBlueprintWriteService.Written written = dryRun
                ? shBlueprintWriteService.plan(defs, desired, actor, options)
                : shBlueprintWriteService.apply(defs, desired, actor, options);
        // SH1252 — after the content apply, which created the folders they land in, and before
        // the verify, so the proof reads a site that has its images.
        List<ShBlueprintApplied.AssetRow> assets = dryRun
                ? shBlueprintAssetWriter.plan(blueprint.assets(), siteName)
                : shBlueprintAssetWriter.apply(blueprint.assets(), siteName, actor);

        return new ShBlueprintApplied(dryRun, blueprint.name(), blueprint.manifest().version(),
                siteName, values, written.postTypes(), written.content(),
                carriedFiles(blueprint), dryRun ? null : verify(blueprint, values, siteName),
                written.site(),
                dryRun ? null : record(blueprint, values, siteName, actor), assets);
    }

    /**
     * Records which package and version this site received (SH155).
     *
     * <h2>Why it runs here, after the writer returned</h2>
     *
     * <p>Outside the apply's transaction, next to {@link #verify}, for the same reason: the
     * content is committed and this is a note *about* it. Which also settles what happens when
     * the note cannot be written — <b>the apply stands</b>. Destroying a site's worth of
     * successful content because a 200-character annotation failed to save is a trade nobody
     * would make on purpose, so the failure is swallowed and the response simply does not
     * claim a record was kept (P9: say what was done, not what was attempted).</p>
     *
     * <h2>Why a re-apply that changed nothing still writes it</h2>
     *
     * <p>Because the row is upsert-by-key, so it cannot accumulate, and because the fact being
     * recorded is <em>"this package at this version was applied here"</em> — a converging
     * re-apply is exactly that, and its date is the answer to "when was this site last
     * confirmed against the package". A record that only moved when content moved would go
     * stale precisely on the sites that are behaving.</p>
     *
     * @return the memory key it wrote, or {@code null} when nothing was recorded
     */
    private String record(ShBlueprint blueprint, Map<String, String> values, String site,
                          String actor) {
        String key = ShBlueprintRecord.keyFor(blueprint.name());
        if (key == null) {
            return null;
        }
        try {
            shAgentMemoryService.remember(new ShAgentNoteRequest(site, key,
                    ShBlueprintRecord.noteFor(blueprint.name(),
                            blueprint.manifest().version(), values)), actor);
            return key;
        } catch (RuntimeException failure) {
            // Includes the case that matters in practice: a credential whose SH2 allow-list
            // does not cover the site by name, which makes the note unfilable but says nothing
            // about whether the content landed.
            return null;
        }
    }

    /** The post-type definitions, with parameters substituted. */
    private List<ShPostTypeDef> defs(ShBlueprint blueprint, Map<String, String> values) {
        List<ShPostTypeDef> defs = new ArrayList<>(blueprint.postTypes().size());
        for (JsonNode tree : blueprint.postTypes()) {
            defs.add(codec.convert(
                    ShBlueprintParams.substitute(tree, values, blueprint.name()),
                    ShPostTypeDef.class));
        }
        return List.copyOf(defs);
    }

    /**
     * The merged desired-state document: the package's folders and posts, in declaration
     * order, addressed to the caller's site and never pruning.
     */
    private ShAgentDesiredState desired(ShBlueprint blueprint, Map<String, String> values,
                                        String site) {
        ObjectNode document = codec.object();
        document.put("site", site);
        ArrayNode folders = document.putArray("folders");
        ArrayNode posts = document.putArray("posts");
        // SH395 — the render model, merged the same way. `postTypes` compiles to `render.provision`
        // and `postTypeLayout` to the site binding, both of which the desired-state service already
        // emits BEFORE the folders and posts: the model exists before anything is written against
        // it, which is the order the traps are about.
        ArrayNode postTypes = codec.array();
        ObjectNode binding = codec.object();
        for (JsonNode raw : blueprint.content()) {
            JsonNode tree = ShBlueprintParams.substitute(raw, values, blueprint.name());
            append(folders, tree.get("folders"));
            append(posts, tree.get("posts"));
            append(postTypes, tree.get("postTypes"));
            JsonNode declaredBinding = tree.get("postTypeLayout");
            if (declaredBinding != null && declaredBinding.isObject()) {
                declaredBinding.propertyNames()
                        .forEach(name -> binding.set(name, declaredBinding.get(name)));
            }
        }
        // Absent rather than empty, both of them: the desired-state service branches on
        // `!isEmpty()`, and an empty array here would be an op that provisions nothing.
        // SH1252 — the folders the package's assets land in, created by the same engine as every
        // other folder, so the plan names them and the asset writer only writes into what exists.
        // Each ancestor too, and none twice: a folder the content already declares is left as the
        // content declared it.
        java.util.Set<String> declared = new java.util.HashSet<>();
        folders.forEach(folder -> declared.add(folder.path("path").asString("")));
        for (ShBlueprint.Asset asset : blueprint.assets()) {
            StringBuilder path = new StringBuilder();
            for (String segment : asset.folder().substring(1).split("/")) {
                path.append('/').append(segment);
                if (declared.add(path.toString())) {
                    folders.addObject().put("path", path.toString());
                }
            }
        }
        if (!postTypes.isEmpty()) {
            document.set("postTypes", postTypes);
        }
        if (!binding.isEmpty()) {
            document.set("postTypeLayout", binding);
        }
        // `prune` is absent, not false-by-omission: see ShBlueprintManifest. A package
        // never decides that content it has not heard of should be deleted.
        return codec.convert(document, ShAgentDesiredState.class);
    }

    private static void append(ArrayNode target, JsonNode source) {
        if (source != null && source.isArray()) {
            source.forEach(target::add);
        }
    }

    /**
     * The blueprint's promise, checked against the instance (SH94).
     *
     * <p>Two questions, one answer. <em>Did the package produce what it said it would?</em>
     * — every URL in {@code verify.posts} resolves. <em>Is what it produced sound?</em> —
     * the SH99 checks over the target site. The first is the package's own claim and the
     * second is the instance's opinion of the result, and a caller applying a blueprint
     * unattended needs both: a package can create every page it promised and still leave
     * a required field empty.</p>
     *
     * <p>Run after the writer's transaction has committed, so it reads what a visitor
     * would read rather than what the transaction hoped for.</p>
     */
    private ShBlueprintApplied.Verified verify(ShBlueprint blueprint, Map<String, String> values,
                                               String site) {
        List<String> promised = blueprint.manifest().verify() == null
                ? List.of() : blueprint.manifest().verify().posts();
        String checks = blueprint.manifest().verify() == null
                ? null : blueprint.manifest().verify().checks();

        List<String> missing = new ArrayList<>();
        for (String url : promised) {
            ShAddress address = ShAddress.post(site,
                    ShBlueprintParams.text(url, values, blueprint.name()));
            if (shAddressResolver.find(address).isEmpty()) {
                missing.add(address.toString());
            }
        }
        ShAgentReport report = shAgentVerifyService.verify(site, checks, null);
        return new ShBlueprintApplied.Verified(missing.isEmpty() && report.ok(),
                missing, report.errors(), report.warnings(), report.checks());
    }

    /**
     * The front-end files the package carries — reported, never written, and named as the
     * templates they are (SH166).
     *
     * <p>These belong in the caller's project, not in the CMS, and the server has no
     * business writing into a working tree it cannot see. So the response names them and
     * {@code shio apply --blueprint} writes them; a caller on MCP alone learns that the
     * package has a front-end half rather than silently getting only the content half
     * (P9: say what was not done).</p>
     *
     * <p>Each row also carries the {@code {{placeholder}}}s still standing in that file,
     * because "not written" was only half of what is untrue about the result. SH119 leaves
     * substitution of carried files to the client, so the bytes a caller fetches from
     * {@code ?include=files} are a template — and a caller who did not know that writes
     * {@code {{projectName}}} into a project and finds out at build time. The names are read
     * from the bytes rather than taken from the manifest's parameter list: a package may
     * declare a parameter its files never use, and reporting that one would send the caller
     * looking for a placeholder that is not there.</p>
     */
    private static List<ShBlueprintApplied.CarriedFile> carriedFiles(ShBlueprint blueprint) {
        return blueprint.files().isEmpty()
                ? null
                : blueprint.files().stream()
                        .map(file -> new ShBlueprintApplied.CarriedFile(file.path(),
                                // distinct: `placeholders` reports every occurrence, and a
                                // README that says {{projectName}} four times has one parameter.
                                ShBlueprintParams.placeholders(file.text()).stream()
                                        .distinct().sorted().toList()))
                        .toList();
    }

    private List<String> readableSites() {
        return shAgentSiteAccess.readable().stream()
                .map(ShSite::getName)
                .filter(name -> name != null && !name.isBlank())
                .sorted()
                .toList();
    }

    /** A 400: no target site. */
    public static class MissingSiteException extends ShTeachingException {

        public MissingSiteException(String blueprint, List<String> sites) {
            super(ShProblem.badRequest("missing-site",
                            "Applying '" + blueprint + "' needs a target site")
                    .withDetail("A blueprint is applied into a site — the package brings a model "
                            + "and content, not a site of its own. It will not create one it was "
                            + "not asked to create: that is the most visible side effect in the "
                            + "system, so it is requested, never inferred (SH151).")
                    .withAllowed(sites)
                    // Names both routes, because which one is available depends on the caller:
                    // a REST or CLI caller sets the flag, and an MCP client uses shio_write —
                    // `site.upsert` is on that tool for free, while a flag on shio_apply would be
                    // schema every session pays for (SH170).
                    .withFix("Set `createSite: true` on this request to create it blank, or make "
                            + "it first with a `site.upsert` op (POST /api/v2/agent/batch, or "
                            + "shio_write over MCP)"
                            + (sites.isEmpty() ? "." : ", or set `site` to one of the listed names."))
                    .withExample("{\"site\":\"" + (sites.isEmpty() ? "Acme" : sites.get(0))
                            + "\", \"createSite\":true}"));
        }
    }
}
