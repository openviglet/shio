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

import org.springframework.stereotype.Component;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import tools.jackson.databind.JsonNode;

/**
 * SH277 — a template's title is its identity, enforced where it is written.
 *
 * <h2>What SH263 left open</h2>
 *
 * <p>SH263 made a duplicate template title an <b>error</b> in {@code checks=content}: two
 * {@code Region} posts called {@code NAV} mean {@code sh-region="NAV"} resolves to whichever row
 * the fold reached first (SH278 made that deterministic, which makes it consistently wrong rather
 * than correct). What it did not do is stop one being created — so the surface reported a defect it
 * would happily accept again on the next write.</p>
 *
 * <h2>Interactive writes refuse; bulk writes report</h2>
 *
 * <p>This is the distinction SH277 was filed on, and it decides where the check lives rather than
 * how it behaves. It sits on {@code ShPostUnifiedWriteService}, which is the writer the console,
 * the CDA, {@code /agent/batch}, a desired-state apply and a blueprint apply all share — so all of
 * those refuse, and refuse identically (SH201's rule that the two surfaces enforce the same thing
 * and the stricter one is correct).</p>
 *
 * <p>The <b>SH68 exchange import</b> writes rows through the repository directly, so it is
 * unaffected — and that is the intended outcome, not an oversight to fix later. An import of the
 * 2018 {@code bootstrap-site} package that failed because the *package* contains a collision would
 * be worse than one that succeeds and reports it, which is SH189's reasoning about the
 * {@code no-site} case. The report already exists: {@code verify} names both posts with an
 * address each. So the pair is complete without a second mechanism — the bulk path's "report" is
 * SH263's lint, and the reason to say so out loud is that the *absence* of a call here looks like
 * something nobody got round to.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShTemplateTitles {

    private final ShPostUnifiedRepository shPostUnifiedRepository;

    public ShTemplateTitles(ShPostUnifiedRepository shPostUnifiedRepository) {
        this.shPostUnifiedRepository = shPostUnifiedRepository;
    }

    /**
     * Refuses a write that would leave two templates of one type sharing a title in one site.
     *
     * <p>Called after the title has been derived from {@code data}, because a template's title is
     * a <em>field</em> — SH183's model is content — so the collision is not visible until the
     * write has decided what the title is going to be.</p>
     *
     * <p>Silent for everything that is not a {@code PageLayout}, {@code Region} or {@code Theme}:
     * two articles may share a title, and two folders may hold a page of the same name. Only these
     * three are addressed by title by the renderer, which is what makes the duplicate a defect
     * rather than a coincidence.</p>
     *
     * @param row a post about to be saved, with its site, post-type and title already set
     * @throws ShDuplicateTemplateTitleException a teaching 409 naming the holder
     */
    public void checkFree(ShPostUnified row) {
        String typeName = row == null || row.getShPostType() == null
                ? null : row.getShPostType().getName();
        if (typeName == null || !ShRenderPostTypes.NAMES.contains(typeName)
                || row.getShSite() == null || row.getTitle() == null || row.getTitle().isBlank()) {
            return;
        }
        // A trashed template's title is free, and needs no clause here: ShPostUnified carries
        // `@SQLRestriction("deleted_date is null")`, so the finder cannot see one — which is the
        // same thing the renderer sees, and the two must agree or this would refuse a title no
        // `sh-region` could ever resolve to.
        List<String> holders = shPostUnifiedRepository.findIdsBySiteAndPostTypeAndTitle(
                row.getShSite().getId(), row.getShPostType().getId(), row.getTitle());
        for (String holder : holders) {
            // Its own id is not a collision: a template has a DRAFT row and a PUBLISHED row that
            // share both, and an update re-saves one of them. Counting rows instead of comparing
            // ids is the false positive that would make every publish of a template fail — the
            // same trap SH263's lint had to avoid one surface along.
            if (!holder.equals(row.getId())) {
                throw new ShDuplicateTemplateTitleException(typeName, row.getTitle(), holder);
            }
        }
    }

    /**
     * What a plan should say about a write that a real run will refuse (SH168's rule).
     *
     * <p>A dry run performs no write, so {@link #checkFree} never runs and the plan would come
     * back green for a batch whose second op 409s. This asks the same question against the same
     * derivation — {@code ShPostUnifiedWriteService.titleOf} is the writer's own, shared rather
     * than restated — and returns a note instead of throwing, because a plan that died on the
     * first collision would hide every row after it, and the report is the reason to plan.</p>
     *
     * @param postType the type being written, or {@code null} for one this plan would create
     *                 (nothing can hold a title in a type that does not exist yet)
     * @param selfId   the post being updated, so its own row is not read as a collision
     * @return a change note, or {@code null} when the title is free
     */
    public String planCollision(ShPostType postType, JsonNode data, String siteId, String selfId) {
        if (postType == null || siteId == null || data == null
                || !ShRenderPostTypes.NAMES.contains(postType.getName())) {
            return null;
        }
        String title = ShPostUnifiedWriteService.titleOf(postType, data);
        if (title.isBlank()) {
            return null;
        }
        for (String holder : shPostUnifiedRepository.findIdsBySiteAndPostTypeAndTitle(
                siteId, postType.getId(), title)) {
            if (!holder.equals(selfId)) {
                return postType.getName() + " '" + title + "' is already taken by id:" + holder
                        + " — a real run will refuse this write";
            }
        }
        return null;
    }

    /** A 409 that names the template already holding the title (SH80). */
    public static class ShDuplicateTemplateTitleException extends ShTeachingException {

        public ShDuplicateTemplateTitleException(String typeName, String title, String holderId) {
            super(ShProblem.conflict("duplicate-template-title",
                            typeName + " '" + title + "' already exists in this site")
                    .withDetail("A template is addressed by its title — `sh-region=\"" + title
                            + "\"` and a `postTypeLayout` binding both name it — so two "
                            + typeName + " posts called '" + title
                            + "' would make which one renders undefined.")
                    // The holder as an `id:` address rather than a `post:` one, because a template
                    // written through `post.upsert` has a friendly URL and one created by the
                    // console may not (SH172), and an address that is right for only some of them
                    // is worse than the one that is always right.
                    .withFix("Give this one a different title, or edit the existing template at "
                            + "id:" + holderId + " instead.")
                    .withExample("GET /api/v2/agent/read?address=id:" + holderId));
        }
    }
}
