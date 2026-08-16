/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type.dto;

import java.util.List;

/**
 * Lossless, round-trippable description of a post-type's content model (SH16) —
 * the unit the {@code shio} CLI {@code pull}s into version control and
 * {@code push}es back. It is the authoring counterpart to the delivery-only
 * {@code ShCdaPostTypeSchema} (SH11).
 *
 * <p>The {@code system} flag is delivered on {@code pull} (so operators can see
 * which types are read-only) but is honoured, not trusted, on {@code push}: the
 * service refuses to create or overwrite a system post-type regardless of the
 * value sent.</p>
 *
 * <p>The {@code managed} flag (SH66) is <em>delivered</em> on every read, so
 * operators and the console can see which types are owned by code. On a
 * <em>write</em> it is not a data field but a claim about the writer, which is why
 * it is a {@link Boolean} rather than a {@code boolean} — <em>absent</em> and
 * {@code false} have to mean different things (SH117, when the console moved onto
 * this one surface):</p>
 *
 * <ul>
 *   <li><b>absent</b> (or {@code true}) — a code-owned write. This is the CLI
 *       {@code push} and the agent path: the type is marked managed, and a write
 *       to an already-managed type is allowed because the writer <em>is</em> the
 *       owner. The CLI's canonical serialization omits the key, so a hand-written
 *       {@code *.json} never shows as drift and never changes meaning.</li>
 *   <li><b>{@code false}</b> — a hand edit (the console). The type is marked
 *       unmanaged, and a write to a <em>managed</em> type is refused with
 *       {@link com.viglet.shio.api.post.type.ShManagedPostTypeException} rather
 *       than silently winning a race against the repository.</li>
 * </ul>
 *
 * <p>It is a UX guard, not an ACL: any caller may claim either. What it must never
 * do is let one writer's convention overwrite the other's without saying so.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShPostTypeDef(
        String name,
        String title,
        String namePlural,
        String description,
        String workflowPublishEntity,
        boolean system,
        Boolean managed,
        Boolean scratch,
        List<ShPostTypeAttrDef> fields) {

    /** The seven-field form: every caller before SH768 made a type declarable as disposable. */
    public ShPostTypeDef(
            String name,
            String title,
            String namePlural,
            String description,
            String workflowPublishEntity,
            boolean system,
            Boolean managed,
            List<ShPostTypeAttrDef> fields) {
        this(name, title, namePlural, description, workflowPublishEntity, system, managed, null,
                fields);
    }

    /**
     * Whether this write claims code ownership — absent means yes, preserving the
     * SH16 CLI contract that predates the console sharing this surface.
     */
    public boolean claimsCodeOwnership() {
        return managed == null || managed;
    }

    /**
     * Whether this write declares the type disposable (SH768).
     *
     * <p>Unlike {@link #claimsCodeOwnership()}, absent means <b>no</b>. Scratch is never a default:
     * a caller that did not say the word gets a type that is kept, because the failure mode of
     * guessing wrong here is a sweep deleting a model somebody meant.</p>
     *
     * <p>Honoured on <b>create</b> only — see {@code ShPostTypeAdminService#upsert}. The declaration
     * has to be one its creator made at creation, or the flag becomes a way to make a real type
     * sweepable after the fact.</p>
     */
    public boolean declaresScratch() {
        return Boolean.TRUE.equals(scratch);
    }
}
