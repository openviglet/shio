/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.acl;

/**
 * The access level a content ACL entry grants (SH54). {@code PUBLISH} implies {@code WRITE}, which
 * implies {@code READ}.
 *
 * <h2>Why publishing is a level rather than a flag (SH995)</h2>
 *
 * <p>For two blocks this enum stopped at WRITE, and "may put content live" lived only on an API
 * token, whose accessor returns true when no token is in play — which is every console session. So
 * the <b>agent</b>, whose work the review queue exists to gate, was the one actor the product could
 * stop from publishing, and a team had no separation between the person who writes and the person
 * who approves. A third level rather than a parallel flag because it is the same question the other
 * two answer — what may this principal do to this object — and a flag beside an ordered enum is a
 * second authorisation model to keep in step.</p>
 *
 * <p><b>Every existing WRITE grant is migrated to PUBLISH</b> (v2026.3.34): an upgrade must not take
 * a capability away from somebody who had it, so separation is opt-in from the day it exists rather
 * than a surprise the morning after.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public enum ShAclPermission {
    READ,
    WRITE,
    PUBLISH;

    /** True when holding this permission satisfies the {@code required} one (PUBLISH ⊇ WRITE ⊇ READ). */
    public boolean satisfies(ShAclPermission required) {
        return ordinal() >= required.ordinal();
    }
}
