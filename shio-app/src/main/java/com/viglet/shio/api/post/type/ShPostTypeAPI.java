/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.post.type;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.agent.ShAgentBodyEtag;
import com.viglet.shio.api.post.type.dto.ShModelProposal;
import org.springframework.web.bind.annotation.RequestParam;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.api.post.type.dto.ShPostTypeRename;
import com.viglet.shio.api.post.type.dto.ShPostTypeRenamed;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * The post-type API (SH16) — <b>the</b> post-type surface, name-keyed and
 * lossless. It serves the {@code shio} CLI's {@code pull}/{@code push}
 * (post-types as code), the agent path, and — since SH117 — the React console's
 * editor and pickers. It is the authoring counterpart to the read-only CDA schema
 * (SH11, token-gated).
 *
 * <p><b>SH117 retired the second one.</b> There used to be an id-keyed,
 * entity-shaped twin at {@code /api/v2/post/type} for the console, so every
 * caller had to know which of two APIs to use and whether to address a post-type
 * by id or by name — a tax paid on every agent session and a straight violation of
 * P5/P10. One concept now has one surface, addressed the way SH79 addresses
 * everything else: by name.</p>
 *
 * <p>The two write verbs are not redundant. {@code PUT /{name}} is an idempotent
 * upsert, because converging a model that is already declared has to be a no-op
 * for {@code shio push --check} to mean anything; {@code POST} creates and
 * conflicts, because a name collision in the console's "new post-type" form is a
 * mistake to report rather than a model to overwrite. Which of them a write
 * <em>is</em> — code-owned or hand-edited — travels in the body's {@code managed}
 * claim (see {@link ShPostTypeDef}), not in the path.</p>
 *
 * <p>Console-authenticated: it falls under {@code anyRequest().authenticated()}
 * (HTTP Basic or a console session). CSRF is required of session-bearing callers
 * and waived for stateless ones — see {@code ShSecurityConfig}, which is what lets
 * the CLI and the browser share one path safely. System post-types are protected
 * in {@link ShPostTypeAdminService}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@RestController
@RequestMapping("/api/v2/post-type")
@Tag(name = "Post Type", description = "Post-types as code — pull/push the content model (SH16)")
@Slf4j
public class ShPostTypeAPI {

    private final ShPostTypeAdminService shPostTypeAdminService;
    private final ShModelProposalService shModelProposalService;
    private final ShAgentBodyEtag shAgentBodyEtag;

    public ShPostTypeAPI(ShPostTypeAdminService shPostTypeAdminService,
                         ShAgentBodyEtag shAgentBodyEtag,
                          ShModelProposalService shModelProposalService) {
        this.shPostTypeAdminService = shPostTypeAdminService;
        this.shModelProposalService = shModelProposalService;
        this.shAgentBodyEtag = shAgentBodyEtag;
    }

    /**
     * The whole model, losslessly — and conditionally since SH159.
     *
     * <p>This is the largest repeat read outside the agent namespace and the one that changes
     * least: a content model is edited in bursts and then read on every {@code shio push
     * --check}, every console editor load and every session that wants the field list. Its
     * {@code ShPostTypeDef}s carry every {@code widgetSettings} string verbatim, which is what
     * makes them lossless and also what makes them heavy.</p>
     *
     * <p>The tag is over the rendered bytes ({@link ShAgentBodyEtag}), so it cannot disagree
     * with what was sent. Reaching into the agent package for that helper is deliberate and is
     * the direction P10 allows: the mechanism is one implementation of one HTTP behaviour, and
     * a second copy of it here is how the two would come to answer {@code 304} under slightly
     * different conditions.</p>
     */
    @GetMapping
    public ResponseEntity<Object> list(
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return shAgentBodyEtag.json("post-type.list", shPostTypeAdminService.list(), ifNoneMatch);
    }

    @GetMapping("/{name}")
    public ResponseEntity<Object> get(@PathVariable String name,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return shPostTypeAdminService.get(name)
                .map(def -> shAgentBodyEtag.json("post-type." + name, def, ifNoneMatch))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Create a post-type, conflicting on an existing name (SH117). The name comes
     * from the body — there is no id to put in the path, and inventing one would
     * be the id-keyed twin all over again.
     */
    @PostMapping
    public ResponseEntity<ShPostTypeDef> create(@RequestBody ShPostTypeDef body) {
        if (body == null || body.name() == null || body.name().isBlank()) {
            throw new ShMissingPostTypeNameException();
        }
        ShPostTypeDef created = shPostTypeAdminService.create(body);
        log.info("Created post-type '{}' ({} field(s)) via the post-type API",
                created.name(), created.fields() != null ? created.fields().size() : 0);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * What this write would cost, without making it (SH523).
     *
     * <p>A {@code dryRun} on the write an agent was going to make anyway, rather than a
     * {@code /post-type/{name}/proposal} of its own: a new endpoint would be a REST-only capability
     * with no MCP tool behind it, and the tool count is capped (SH262's reasoning at the same
     * fork). The body is the definition being considered, so the same call answers "what would this
     * cost" and "do it" — which is what makes the cost askable at the moment it matters rather than
     * after the push.</p>
     *
     * <p>Also answerable with no body change at all: the two findings about the model <em>as it
     * stands</em> — declared fields nothing reads, stored keys nothing declares — are true of the
     * type today, so sending it back to itself is a legitimate way to ask.</p>
     */
    @PutMapping(value = "/{name}", params = "dryRun")
    public ShModelProposal propose(@PathVariable String name,
                                   @RequestBody ShPostTypeDef body,
                                   @RequestParam(name = "dryRun") boolean dryRun) {
        return shModelProposalService.propose(name, dryRun ? body : null);
    }

    @PutMapping("/{name}")
    public ShPostTypeDef upsert(@PathVariable String name, @RequestBody ShPostTypeDef body) {
        ShPostTypeDef saved = shPostTypeAdminService.upsert(name, body);
        log.info("Upserted post-type '{}' ({} field(s)) via admin API",
                name, saved.fields() != null ? saved.fields().size() : 0);
        return saved;
    }

    /**
     * Rename a post-type, carrying its content with it (SH163).
     *
     * <p>The verb the surface was missing. A `PUT` to a new name creates a <em>second</em>
     * post-type and orphans the first with its id, its fields and every post that points at it —
     * so the console's API-name field was read-only, which was honest and not a feature. This is
     * SH134's treatment for the model: one operation, an occupied target refused, and a response
     * that says what moved.</p>
     *
     * <p>{@code POST} rather than `PUT`, because it is not idempotent by URL: after it succeeds,
     * the same request 400s on an unknown source. Renaming to the current name <em>is</em> a
     * no-op, which is the idempotence that matters for a re-run.</p>
     */
    @PostMapping("/{name}/rename")
    public ShPostTypeRenamed rename(@PathVariable String name, @RequestBody ShPostTypeRename body) {
        ShPostTypeRenamed renamed = shPostTypeAdminService.rename(name, body);
        log.info("Renamed post-type '{}' to '{}' ({} post(s), {} reference(s) retargeted)",
                renamed.from(), renamed.to(), renamed.posts(), renamed.referencesUpdated().size());
        return renamed;
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        return shPostTypeAdminService.delete(name)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
