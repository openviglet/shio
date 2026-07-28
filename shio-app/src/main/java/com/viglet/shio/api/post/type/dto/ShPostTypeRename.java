/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type.dto;

/**
 * The body of {@code POST /api/v2/post-type/{name}/rename} (SH163).
 *
 * @param to      the new API name
 * @param managed the same ownership <em>claim</em> every write on this surface carries — absent
 *                or {@code true} is a code-owned rename, {@code false} is a hand edit and is
 *                refused against a code-managed type. It is deliberately not a data field and
 *                deliberately not an ACL: see {@link ShPostTypeDef}. A rename does not change
 *                the flag, it is only tested against it — renaming is not how a type changes
 *                hands.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShPostTypeRename(String to, Boolean managed) {

    /** Absent means yes, exactly as it does on {@link ShPostTypeDef#claimsCodeOwnership()}. */
    public boolean claimsCodeOwnership() {
        return managed == null || managed;
    }
}
