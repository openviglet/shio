/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring.security.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Service;

import com.viglet.shio.persistence.repository.token.ShApiTokenRepository;

/**
 * Sign out: the console session, and since SH1395 every site-host sign-in the person holds.
 *
 * <p>A site host's sign-in is a {@code MEMBER} token in a cookie the instance cannot clear (it is
 * another host's), so signing out here disables the tokens themselves: the next request on any site
 * host reads as a visitor. Without it a shared computer kept a member's pages open for up to eight
 * hours after they had signed out.</p>
 */
@Slf4j
@Service
public class ShLogoutHandler implements LogoutHandler {

    private final ShApiTokenRepository tokens;

    public ShLogoutHandler(ShApiTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && !(authentication.getDetails() instanceof ShApiTokenContext)) {
            int ended = tokens.disableMemberTokensOf(authentication.getName());
            if (ended > 0) {
                log.info("Ended {} site-host sign-in(s) of {} with their console sign-out", ended,
                        authentication.getName());
            }
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            log.debug("Invalidating session: {}", session.getId());
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
