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
 * What a rename moved (SH163) — the response of
 * {@code POST /api/v2/post-type/{name}/rename}.
 *
 * <p><b>It says what it moved, because that is the difference between a rename and a hope.</b>
 * The workaround this operation replaces was a `PUT` to the new name, which reported a perfectly
 * successful write and left every post behind on a second post-type nobody meant to create. So
 * the response counts the content that came along and names the fields that were pointing at the
 * old name — SH134's rule for posts, one level up.</p>
 *
 * @param from             the name it had
 * @param to               the name it has
 * @param posts            how many {@code sh_post_unified} rows point at it, both states counted
 *                         (the rows themselves never move: they reference the post-type by id)
 * @param referencesUpdated {@code PostType.field} for every {@code Multi Select} whose
 *                         {@code multiselect.postType} named the old type and was rewritten —
 *                         the one place the model stores a post-type <em>by name</em> (SH154)
 * @param notes            what the server cannot rename, in the caller's own vocabulary: a
 *                         project's content files and desired-state documents address a
 *                         post-type by name too, and the server never writes a project file
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShPostTypeRenamed(String from, String to, long posts,
                                List<String> referencesUpdated, List<String> notes) {
    public ShPostTypeRenamed {
        referencesUpdated = referencesUpdated == null ? List.of() : List.copyOf(referencesUpdated);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
