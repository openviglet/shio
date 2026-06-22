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
 * What kind of principal a content ACL entry grants to (SH54): a single user
 * (by username), a group (by name), or a role (by name).
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public enum ShAclPrincipalType {
    USER,
    GROUP,
    ROLE
}
