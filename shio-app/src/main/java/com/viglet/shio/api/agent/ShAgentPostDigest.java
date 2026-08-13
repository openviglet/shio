/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import com.viglet.shio.persistence.model.post.unified.ShPostUnified;

/**
 * A post's declared state as one short value, so a write can be made conditional (SH721).
 *
 * <h2>The gap this fills</h2>
 *
 * <p>SH335 added the console {@code PATCH} because a client-side read-modify-write "reintroduces a
 * lost update on a surface whose premise is several curators on one site". That reasoning stopped
 * at the console door. Measured by SH713: a curator patching one field while an agent runs
 * {@code post.upsert} on the same post gets {@code 200}, the agent gets {@code changed=1}, and the
 * curator's field is <b>gone</b> — with no signal to either party. Neither side could have
 * prevented it, because nothing on the surface expressed <i>only if unchanged</i>.</p>
 *
 * <h2>Why not the digests that already exist</h2>
 *
 * <p>{@code shio_digest} and the SH194 appearance digest are about a rendered <em>page</em>. They
 * move when a layout or a theme changes and stay still when a field a page does not show is
 * rewritten — so both directions are wrong for this question. What a conditional write needs is a
 * hash of the row it is about to overwrite.</p>
 *
 * <h2>What is in it, and why the whole post</h2>
 *
 * <p>Everything {@code post.upsert} declares: type, folder, address, locale and the full
 * {@code data}. <b>Full</b>, even when the read that produced it projected a subset — a caller
 * reading {@code fields=[title]} and then writing with {@code expect} is asserting that the row it
 * overwrites is unchanged, and an upsert overwrites all of it. A digest over the projection would
 * be a promise about the part the caller happened to look at, which is precisely the field a
 * curator edits while nobody is looking.</p>
 *
 * <p>{@code modifiedDate} is deliberately <b>not</b> in it. It is the obvious version stamp and it
 * is the wrong one twice: it is a timestamp, and this codebase already knows that equality against
 * a timestamp column is a promise about fractional-second precision three engines do not make the
 * same way (SH200); and it moves when a write changes nothing, which would refuse a caller whose
 * expectation was correct. Content is the thing being protected, so content is what is hashed —
 * two equal states digest equally, which is P5.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentPostDigest {

    private ShAgentPostDigest() {
    }

    /**
     * @return the digest, or {@code null} when there is no post — which a caller reads as "there is
     *         nothing here to be unchanged", and is why {@code expect} against an absent post is a
     *         mismatch rather than a pass
     */
    public static String of(ShPostUnified post) {
        if (post == null) {
            return null;
        }
        // ShAgentEtag's separator makes ("a","bc") and ("ab","c") distinct, which is the property
        // that matters when the parts are user-controlled strings. Reused rather than re-derived:
        // a second implementation of "hash these parts" is where the careless one grows (SH245).
        return unquoted(ShAgentEtag.of(
                post.getShPostType() == null ? null : post.getShPostType().getName(),
                post.getShFolder() == null ? null : post.getShFolder().getId(),
                post.getFurl(),
                post.getLocale(),
                post.getTranslationGroupId(),
                post.getData()));
    }

    /**
     * Without the quotes {@link ShAgentEtag} adds.
     *
     * <p>That method builds an HTTP entity tag, where the quotes are part of the grammar. This
     * value travels in a JSON field an agent copies from a read into a write, and a caller that
     * has to remember which of the two forms to send is a caller who will send the wrong one.</p>
     */
    private static String unquoted(String etag) {
        return etag == null ? null : etag.replace("\"", "");
    }
}
