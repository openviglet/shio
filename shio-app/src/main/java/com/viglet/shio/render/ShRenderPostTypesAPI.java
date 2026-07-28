/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.post.type.dto.ShPostTypeDef;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * SH183 — the one call that makes an instance able to render.
 *
 * <h2>Why this endpoint has to exist</h2>
 *
 * <p>{@code PageLayout}, {@code Region} and {@code Theme} are ordinary post-types, so the
 * obvious answer is that an agent creates them with {@code PUT /api/v2/post-type/{name}}
 * like any other. It cannot: they are <b>system</b> types, and {@code upsert} refuses one
 * with a 409 — correctly, since that guard exists so a hand edit cannot reshape a type the
 * product owns. Without a way in, the render model would be reachable only by whoever can
 * restart the JVM, which is law P1's failure with the console swapped for the operator.</p>
 *
 * <p>So: a {@code POST} that creates whichever are absent and returns all three as SH16
 * {@code ShPostTypeDef}s — the same bytes {@code GET /api/v2/post-type/{name}} returns, so
 * there is no second serialization of the model (P10) and {@code shio pull} sees them the
 * moment they exist. It is <b>idempotent</b> and never touches an existing type, which is
 * SH152's provision-never-upsert rule: a curator who added a field to their {@code Region}
 * keeps it. {@code GET} answers "can this instance render?" without writing, so the
 * question costs nothing and needs no dry-run flag.</p>
 *
 * <p>The controller holds no logic and does no mapping: {@code shPostTypeAttrs} is LAZY, so
 * a def built out here would blow up on an <em>existing</em> type and pass on a new one —
 * SH176's defect exactly. The service maps inside its own transaction.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping("/api/v2/render/post-types")
@Tag(name = "Render", description = "The page → layout → region model")
public class ShRenderPostTypesAPI {

    private final ShRenderPostTypes shRenderPostTypes;

    public ShRenderPostTypesAPI(ShRenderPostTypes shRenderPostTypes) {
        this.shRenderPostTypes = shRenderPostTypes;
    }

    /** @return which of the three exist, without creating any. */
    @GetMapping
    public List<ShPostTypeDef> list() {
        return shRenderPostTypes.existing();
    }

    /** Creates whichever are absent. Safe to call every time; returns all three. */
    @PostMapping
    public List<ShPostTypeDef> ensure() {
        return shRenderPostTypes.ensure();
    }

    /**
     * SH289 — adds fields an existing render type is missing.
     *
     * <p>A second verb rather than a smarter {@code ensure}, because the two answer different
     * questions and only one of them is safe to run unattended: {@code ensure} creates what is
     * absent and is called by anything that needs the model, while this <em>changes</em> a type the
     * instance already has. An instance created before SH277 has render types with no title field,
     * where a template can be neither addressed nor titled — the drift is a broken model rather
     * than a stale preference, and until now there was no way to repair it short of editing the
     * database.</p>
     *
     * <p><b>Additive only, and a dry run answers from the same code.</b> A field the type already
     * declares is untouched, so this cannot undo a curator's edit; {@code ?dryRun=true} reports the
     * same lines it would apply, because a plan produced by different code is a plan that can come
     * back greener than the run (SH184's rule for {@code site.upsert}).</p>
     *
     * @return one line per type that is missing something; empty when nothing is
     */
    @PostMapping("/reconcile")
    public List<String> reconcile(
            @RequestParam(name = "dryRun", required = false, defaultValue = "false")
            boolean dryRun) {
        return shRenderPostTypes.reconcile(dryRun);
    }
}
