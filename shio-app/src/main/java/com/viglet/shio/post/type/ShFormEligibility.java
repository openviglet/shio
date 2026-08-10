/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.post.type;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import com.viglet.shio.persistence.model.post.type.ShPostType;

/**
 * Which post-types a form submission may create (SH631).
 *
 * <h2>Why this is a predicate of its own and not a check the endpoint makes</h2>
 *
 * <p>The endpoint SH633 builds accepts a document from the internet. Its severity does not turn on
 * the rate limit, the honeypot or the draft default — every one of those bounds how <em>much</em>
 * damage or how <em>visible</em> it is. It turns on this one question, because
 * {@code PageLayout}, {@code Region} and {@code Theme} are Handlebars, compiled and executed by the
 * renderer: a form that could create one is not a spam problem, it is <b>a stranger writing a
 * template this instance runs</b>.</p>
 *
 * <p>A rule that decides that belongs somewhere a reader can find it and a test can address,
 * not inside a controller among the parameter checks. It is also why the scope is bounded by
 * <em>data</em> rather than by verb (see {@code ShApiTokenScope.FORM}) — "may POST" is not the
 * question; "may create a post of this type here" is.</p>
 *
 * <h2>Two conditions, and the intersection is the point</h2>
 *
 * <p><b>The site's allow-list</b> says which types this site invites submissions for (SH632 gives it
 * a home). <b>Not a system type</b> is the floor underneath it. Either alone is insufficient in a
 * way the other covers: an allow-list alone still lets an operator name {@code Theme} by mistake and
 * that mistake is remote template execution, while the system-type floor alone would let a
 * submission create an {@code Article} on a site that never asked for one.</p>
 *
 * <p>The floor is expressed <b>two ways at once</b>, deliberately. A type is refused if it is
 * flagged {@code system} in the model <em>or</em> if its name is one the platform owns. Belt and
 * braces because they fail differently: the flag is data and an import, a restore or a hand-written
 * row can carry it wrongly, while the name list is code and cannot describe a type this build has
 * not heard of. A submission that satisfied one and not the other is exactly the case worth
 * refusing.</p>
 *
 * @author Alexandre Oliveira
 */
public final class ShFormEligibility {

    private ShFormEligibility() {
    }

    /**
     * Names the platform owns, whatever a row says about them.
     *
     * <p>The three renderer types are the dangerous ones and the rest are here because there is no
     * reason for a form to create them either: a submission that could write a {@code Redirect}
     * could point any URL of the site at anywhere, and one that could write {@code SiteScripts}
     * could add a {@code <script src>} to every page — both of which are the template-execution
     * defect wearing different clothes.</p>
     */
    private static final Set<String> PLATFORM_OWNED = Set.of(
            ShSystemPostType.PAGE_LAYOUT,
            ShSystemPostType.REGION,
            ShSystemPostType.THEME,
            ShSystemPostType.SITE_SCRIPTS,
            ShSystemPostType.REDIRECT,
            ShSystemPostType.FILE);

    /**
     * Whether a form submission may create a post of this type at all.
     *
     * <p>The floor, asked of the type itself and independent of any site. {@code null} is refused:
     * an unresolved type is not an eligible one, and the safe reading of "I could not tell" on a
     * path reachable from a public page is no.</p>
     */
    public static boolean isEligibleType(ShPostType type) {
        if (type == null || type.getName() == null || type.getName().isBlank()) {
            return false;
        }
        if (type.getSystem() != 0) {
            return false;
        }
        return !PLATFORM_OWNED.contains(type.getName());
    }

    /**
     * Whether this site invites submissions of this type — the floor <b>and</b> the allow-list.
     *
     * @param type    the resolved post-type, or {@code null}
     * @param allowed the site's declared list of type names; {@code null} or empty invites nothing
     */
    public static boolean isEligible(ShPostType type, Collection<String> allowed) {
        if (!isEligibleType(type)) {
            return false;
        }
        // An empty allow-list means the site has not opted in, which must read as "no" rather than
        // as "no restriction" — the direction every default on this path takes (SH364, P7).
        return allowed != null && allowed.contains(type.getName());
    }

    /**
     * The names a site may legally put on its allow-list, given what exists.
     *
     * <p>For the teaching error SH633 owes a caller and for the console that edits the list: an
     * operator who typed {@code Theme} should be told what they <em>could</em> have typed (P6),
     * rather than discovering by absence.</p>
     */
    public static Set<String> eligibleNames(Collection<ShPostType> types) {
        Set<String> names = new TreeSet<>();
        if (types == null) {
            return names;
        }
        for (ShPostType type : types) {
            if (isEligibleType(type)) {
                names.add(type.getName());
            }
        }
        return names;
    }
}
