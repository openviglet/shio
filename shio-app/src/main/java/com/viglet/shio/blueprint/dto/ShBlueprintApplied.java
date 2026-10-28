/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;

/**
 * What applying a blueprint did, or would do (SH93).
 *
 * <h2>Three sections because there are three kinds of outcome</h2>
 *
 * <p>The content half is an SH82 {@link ShAgentChangeSet}, unchanged — a caller that can
 * read a plan can read this, and there is no second vocabulary for the same facts.
 * The model half cannot be, because provisioning a post-type is not a content op: it is
 * reported as its own list so "the Article type already existed" does not have to be
 * inferred from the absence of a row. The files half is neither: those are bytes the
 * <em>server never wrote</em>, and saying so is the difference between an honest report
 * and a claim (P9).</p>
 *
 * <h2>And they are templates, which SH166 made the response say</h2>
 *
 * <p>Saying "the server did not write these" was only half the truth. SH119 split
 * substitution by who knows the answer — the server fills a package's <em>content</em>, the
 * CLI fills its <em>carried files</em>, because a project's npm name is local identity the
 * server has no business being told — so what the response names are files that still say
 * {@code {{projectName}}}. A CLI caller never notices; a caller on MCP alone was told the
 * paths and left to discover the placeholders by reading the source, which is P9 failing on
 * its own terms: the report described what was not done and not what is therefore untrue of
 * the result.</p>
 *
 * <p>So each row now names the placeholders standing in that file. The alternative —
 * substituting server-side, since an apply does send {@code params} — was rejected for a
 * reason worth recording: the bodies do not travel on this response at all. They come from
 * {@code GET /agent/blueprints/{name}?include=files}, a read that has no parameter values to
 * substitute with, and giving it some would put a write's arguments on a cacheable GET
 * (SH116). Naming the placeholders is what lets the caller that fetched them substitute for
 * itself with the {@code params} echoed alongside.</p>
 *
 * <h2>Why the model is provisioned, never overwritten</h2>
 *
 * <p>A blueprint seeds a content model; it does not own it. So a post-type that already
 * exists is left exactly as it is — including any field a human has since added — and
 * reported as {@code exists}. The alternative, upserting, would make re-applying a
 * blueprint silently revert the model to the package's idea of it, which is precisely
 * the surprise P7 forbids. (It would also flip SH66's {@code managed} flag and lock the
 * type in the console, from a package the user never wrote.)</p>
 *
 * @param dryRun    nothing was written — the plan
 * @param blueprint the package's name
 * @param version   the package's version, so a change set records which revision ran
 * @param site      the site it was applied to
 * @param params    the parameter values actually used, defaults included. Echoed because
 *                  a default the caller did not send is still a decision that shaped the
 *                  result, and the next apply has to be able to reproduce it (P5)
 * @param postTypes one row per post-type the package carries
 * @param content   the SH82 change set for the folders, posts and publishes
 * @param files     the front-end files the package carries and the server did not write,
 *                  each naming the {@code {{placeholder}}}s still standing in it (SH166)
 * @param verify    the post-apply proof; {@code null} on a plan, which has nothing to prove
 * @param recordedAs the SH114 memory key this apply wrote the package and version to (SH155),
 *                  or {@code null} on a plan and when the note could not be filed. Reported
 *                  rather than assumed: the record is what makes "which blueprint is this
 *                  site" answerable later, so a caller has to be able to see it was kept
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShBlueprintApplied(boolean dryRun, String blueprint, String version, String site,
                                 java.util.Map<String, String> params,
                                 List<PostType> postTypes, ShAgentChangeSet content,
                                 List<CarriedFile> files, Verified verify, String createdSite,
                                 String recordedAs,
                                 @JsonInclude(Include.NON_EMPTY) List<AssetRow> assets) {

    /** The eleven-field form: pre-SH1252, when a package carried no assets. */
    public ShBlueprintApplied(boolean dryRun, String blueprint, String version, String site,
                              java.util.Map<String, String> params, List<PostType> postTypes,
                              ShAgentChangeSet content, List<CarriedFile> files, Verified verify,
                              String createdSite, String recordedAs) {
        this(dryRun, blueprint, version, site, params, postTypes, content, files, verify,
                createdSite, recordedAs, List.of());
    }

    /**
     * SH1252 — one asset the package writes into the site.
     *
     * @param path   its site path, which is how the package's content references it
     * @param action {@code create}, {@code replace} when the bytes differ from what is there, or
     *               {@code noop} when they are the same, which is every row of a converged re-apply
     * @param bytes  its size, so a plan says what it is about to upload
     */
    public record AssetRow(String path, String action, long bytes) {
    }

    /** The nine-field form: the site was already there, which is every pre-SH151 apply. */
    public ShBlueprintApplied(boolean dryRun, String blueprint, String version, String site,
                              java.util.Map<String, String> params, List<PostType> postTypes,
                              ShAgentChangeSet content, List<CarriedFile> files, Verified verify) {
        this(dryRun, blueprint, version, site, params, postTypes, content, files, verify, null,
                null);
    }

    /** The ten-field form: pre-SH155, when an apply left no record of itself. */
    public ShBlueprintApplied(boolean dryRun, String blueprint, String version, String site,
                              java.util.Map<String, String> params, List<PostType> postTypes,
                              ShAgentChangeSet content, List<CarriedFile> files, Verified verify,
                              String createdSite) {
        this(dryRun, blueprint, version, site, params, postTypes, content, files, verify,
                createdSite, null);
    }

    /**
     * One front-end file the package carries and the server did not write (SH166).
     *
     * <p>{@code placeholders} is what turns a path into an instruction. It lists the
     * {@code {{name}}}s still standing in that file's bytes — always a subset of the
     * package's declared parameters, because {@code ShBlueprintRegistry} refuses to load a
     * package containing one that is not, so every name here has a value in the {@code params}
     * echoed on the same response. {@code null} when the file is literal, so a package with
     * nothing to substitute reads exactly as it did before this existed.</p>
     *
     * @param path         where the file belongs, relative to the project root
     * @param placeholders the parameter names still to substitute, sorted; {@code null} if none
     */
    @JsonInclude(Include.NON_NULL)
    public record CarriedFile(String path, List<String> placeholders) {

        public CarriedFile {
            placeholders = placeholders == null || placeholders.isEmpty()
                    ? null : List.copyOf(placeholders);
        }
    }

    /**
     * One post-type the package carries.
     *
     * <h2>Why {@code missing} exists</h2>
     *
     * <p>Because names collide. A site created through the console already has a
     * {@code Page} post-type (the SH71 bootstrap template ships one with {@code title} and
     * {@code content}), and the section vocabulary's {@code Page} has {@code sections}.
     * Since a blueprint never overwrites, the older type wins — and the package's content
     * would then write a field the type does not declare, which the console cannot render
     * and a human cannot edit. That is exactly the class of silent half-success P9 exists
     * to prevent, so the difference is named rather than discovered.</p>
     *
     * @param name    its API name
     * @param action  {@code create} when the package provisioned it, {@code exists} when
     *                the instance already had one of that name and it was left untouched
     * @param missing fields the package declares that the existing type does not have.
     *                Only ever set alongside {@code exists}; {@code null} when they agree
     */
    @JsonInclude(Include.NON_NULL)
    public record PostType(String name, String action, List<String> missing, List<String> added) {

        public static final String CREATE = "create";
        public static final String EXISTS = "exists";
        /** SH152 — the type was already there and the package's missing fields were added to it. */
        public static final String MERGE = "merge";

        /** A post-type the package created — nothing to reconcile. */
        public static PostType created(String name) {
            return new PostType(name, CREATE, null, null);
        }

        /** One the instance already had, with whatever the package expected and did not find. */
        public static PostType existing(String name, List<String> missing) {
            return new PostType(name, EXISTS,
                    missing == null || missing.isEmpty() ? null : List.copyOf(missing), null);
        }

        /**
         * One the instance already had, whose missing fields this apply added (SH152).
         *
         * <p>{@code merge} is its own action rather than {@code create} on purpose: a merge that
         * read as a provision would hide that the type is now a hybrid of two models, which is
         * exactly what somebody debugging the form six months later needs to know. {@code added}
         * says which fields arrived; anything still in {@code missing} is a nested difference the
         * merge does not touch.</p>
         */
        public static PostType merged(String name, List<String> added, List<String> missing) {
            return new PostType(name, MERGE,
                    missing == null || missing.isEmpty() ? null : List.copyOf(missing),
                    added == null || added.isEmpty() ? null : List.copyOf(added));
        }
    }

    /**
     * The blueprint's promise, checked against the instance after the apply committed.
     *
     * <p>Reported rather than enforced by rollback, and the distinction is deliberate.
     * The content apply is already atomic on its own; re-running a blueprint converges.
     * So the useful answer to "the package promised /pricing and it is not there" is a
     * report the caller can act on — plus the SH99 findings that usually explain why —
     * not an unexplained rollback of work that mostly succeeded.</p>
     *
     * @param ok       every promised post resolves and the lint found no error
     * @param missing  promised posts that do not exist; empty is the good case
     * @param errors   error-severity findings from the SH99 checks over the site
     * @param warnings warning-severity findings
     * @param checks   the check groups that ran
     */
    @JsonInclude(Include.NON_NULL)
    public record Verified(boolean ok, List<String> missing, int errors, int warnings,
                           List<String> checks) {

        public Verified {
            missing = missing == null ? List.of() : List.copyOf(missing);
            checks = checks == null ? List.of() : List.copyOf(checks);
        }
    }
}
