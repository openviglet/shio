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

import org.springframework.stereotype.Component;

import com.viglet.shio.api.acl.ShContentAclService;

/**
 * SH1021 — the permission a bulk verb needs, asked once for the whole selection.
 *
 * <h2>Why before the batch rather than only inside it</h2>
 *
 * <p>The batch's own ops check per row, and that is the check that must be there — it is what makes
 * the two surfaces agree. This is the earlier one, and it exists for what the caller sees: without
 * it a curator with no publish right gets a change set in which every item is refused, and on a dry
 * run a <em>plan they could not carry out</em>. SH995's rule is that a curator who may edit a page
 * is not automatically the one who decides it goes live, and a plan that misleads about that is
 * worse than no plan at all.</p>
 *
 * <p>It asks about every id before answering, so a selection of forty where the last is refused
 * fails as one refusal rather than after thirty-nine writes.</p>
 */
@Component
public class ShContentAclBulkGuard {

    private final ShContentAclService shContentAclService;

    public ShContentAclBulkGuard(ShContentAclService shContentAclService) {
        this.shContentAclService = shContentAclService;
    }

    public void requireAll(ShPostUnifiedBulkService.Op op, List<String> ids) {
        for (String id : ids) {
            // PUBLISH for the two that change what a visitor sees, WRITE for the one that removes
            // the content itself — the same split the single-id endpoints make.
            if (op == ShPostUnifiedBulkService.Op.DELETE) {
                shContentAclService.requireWrite(id);
            } else {
                shContentAclService.requirePublish(id);
            }
        }
    }
}
