/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * The signed-in curator's username, as {@code ShUserAPI.current} resolves the user record by, for
 * both sign-in kinds. One rule for every per-person preference (SH971's dock, SH1024's saved views),
 * so two of them cannot come to file one person's settings under two names.
 */
public final class ShCurrentUsername {

    private ShCurrentUsername() {
    }

    public static String get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.getPrincipal() instanceof OAuth2User user) {
            for (String key : new String[] { "preferred_username", "login", "email" }) {
                Object value = user.getAttribute(key);
                if (value != null) {
                    return value.toString();
                }
            }
        }
        return authentication.getName();
    }
}
