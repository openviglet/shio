/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.form;

import java.util.Optional;

import com.viglet.shio.spring.security.auth.ShApiTokenContext;

/**
 * Who a form submission is recorded as, and how a reader recognises one afterwards (SH970).
 *
 * <p>A submission is a post (SH633), so the audit row it writes is an ordinary {@code CREATE}, and the
 * only thing that tells it apart is the actor. The two halves live here together because the change
 * feed reads the second one: when the spelling and its test are in different classes, one of them
 * can change without the other.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShFormSubmitter {

    /**
     * SH1066 — public, and compile-time constants, because the content query's JPQL is built from
     * them: a {@code submission=true} predicate spelled with its own copy of these is a third place
     * the spelling can change without the other two.
     */
    public static final String TOKEN_PREFIX = "form-token:";
    public static final String UNTOKENED = "form";

    private ShFormSubmitter() {
        // utility
    }

    /**
     * The actor a submission is written as: the token, not a person (SH633).
     *
     * <p>An anonymous submission has no user to attribute, and naming the token is both true and what
     * an operator needs when one form starts producing rubbish. It identifies which credential to
     * revoke without identifying a visitor.</p>
     */
    public static String owner(Optional<ShApiTokenContext> token) {
        return token.map(context -> TOKEN_PREFIX + context.tokenId()).orElse(UNTOKENED);
    }

    /** Whether an audit row's actor is one {@link #owner} wrote. */
    public static boolean isSubmitter(String actor) {
        return actor != null && (actor.equals(UNTOKENED) || actor.startsWith(TOKEN_PREFIX));
    }
}
