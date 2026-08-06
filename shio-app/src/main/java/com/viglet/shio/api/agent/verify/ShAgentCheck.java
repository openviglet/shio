/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * The check groups {@code /api/v2/agent/verify} can run ({@code checks=}).
 *
 * <p>Grouped rather than per-rule for the same reason the context pack projects by
 * section: the useful request is "skip the ones that need the delivery layer", not
 * "run rules 3, 7 and 11". Every group defaults to on — a verification that quietly
 * skipped a class of defect would be worse than no verification at all, so opting out
 * is explicit.</p>
 *
 * <h2>The one group whose default is an operator's decision (SH141)</h2>
 *
 * <p>{@link #DELIVERY} leaves the process. It is in the default set when — and only when —
 * {@code shio.agent.render.allow-fetch} is on, which is the same property that lets SH101
 * fetch one page. That is not an exception to the rule above so much as an application of
 * it: an operator who has told the instance its front end is fetchable has asked for the
 * proof, and on every other instance the group cannot run at all, so including it would
 * mean a report that fails on the absence of a deployment rather than on a defect.</p>
 *
 * <p>Naming it explicitly while fetching is off is a teaching 400, not an empty pass —
 * a check that silently covered nothing while answering {@code ok} is the same false green
 * SH143 refuses for an unresolvable {@code folder=}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public enum ShAgentCheck {

    /** SH99 — the content is internally consistent: fields, references, assets, furls. */
    CONTENT,

    /**
     * SH141 — the published pages are actually served, proved by fetching them.
     *
     * <p>The strictly larger question {@link #ROUTES} cannot ask: routes resolve links
     * against the content graph, which cannot see a page that exists in the CMS and 404s
     * on the site. Opt-in via {@code shio.agent.render.allow-fetch} — see the class note.</p>
     */
    DELIVERY,

    /** SH100 — the content is reachable: every published furl resolves, links land. */
    ROUTES,

    /**
     * SH516 — the page a reader gets, with every reference on it resolved against its own URL.
     *
     * <p>The gap {@link #ROUTES} and {@link #CONTENT} share rather than a rule either was missing:
     * both resolve a reference against the content graph — does this furl exist, is that
     * {@code File} post addressable — and all of it can be true of a page whose every string still
     * misses. Measured: {@code verify --site VILT} answered <b>ok, 0 errors, 17 warnings</b> over a
     * page with 38 broken links of 39 and 43 broken references, because the page is served four
     * segments deep and its relative strings resolved elsewhere.</p>
     *
     * <p>So this one renders each published page and reads <em>its</em> references — {@code href},
     * {@code src}, {@code srcset}, {@code poster}, and {@code url()} in a stylesheet and in a style
     * attribute — through {@code ShPreviewLinks.referencesIn}, which is the same walk the rewriter
     * uses. A check with a list of its own would go green on the one attribute the rewriter reads
     * and it does not, which is how the six defects before this one survived a green gate.</p>
     *
     * <p>In the default set and it needs no network: the page is composed in this process, which is
     * the same composition the {@code /sites/**} route returns.</p>
     */
    PAGE,

    /**
     * SH103 — recent asynchronous failures (webhooks, transforms, scheduled publishes,
     * 5xx). Included by default because the failures it reports are invisible
     * everywhere else, and on a healthy instance it reports nothing and costs nothing.
     */
    DIAGNOSTICS;

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Lower-case names in declaration order — the {@code allowed} set of the 400. */
    public static List<String> names() {
        return Arrays.stream(values()).map(ShAgentCheck::wireName).toList();
    }

    /**
     * Parses a comma-separated {@code checks=} value.
     *
     * @param raw             blank or {@code all} means every group that can run
     * @param deliveryEnabled whether server-side fetching is configured, which decides
     *                        both whether {@link #DELIVERY} is in the default set and
     *                        whether naming it is a request or a mistake
     * @throws ShTeachingException 400 naming the groups and the nearest match
     */
    public static Set<ShAgentCheck> parse(String raw, boolean deliveryEnabled) {
        if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw.trim())) {
            return defaults(deliveryEnabled);
        }
        EnumSet<ShAgentCheck> checks = EnumSet.noneOf(ShAgentCheck.class);
        for (String token : raw.split(",")) {
            String name = token.trim();
            if (!name.isEmpty()) {
                checks.add(resolve(name));
            }
        }
        if (checks.isEmpty()) {
            return defaults(deliveryEnabled);
        }
        if (checks.contains(DELIVERY) && !deliveryEnabled) {
            throw new DeliveryDisabledException();
        }
        return checks;
    }

    /** Every group that can run here: all of them, less {@link #DELIVERY} when it cannot. */
    public static Set<ShAgentCheck> defaults(boolean deliveryEnabled) {
        EnumSet<ShAgentCheck> all = EnumSet.allOf(ShAgentCheck.class);
        if (!deliveryEnabled) {
            all.remove(DELIVERY);
        }
        return all;
    }

    private static ShAgentCheck resolve(String name) {
        for (ShAgentCheck check : values()) {
            if (check.wireName().equalsIgnoreCase(name)) {
                return check;
            }
        }
        throw new UnknownCheckException(name, names());
    }

    /**
     * A 400 for {@code checks=delivery} on an instance that cannot fetch (SH141).
     *
     * <p>Refusing rather than skipping, for the reason the class note gives: the caller
     * asked to have its routes proved, and an {@code ok} that quietly proved nothing is
     * the most expensive answer this endpoint can give. Names the property, because the
     * fix belongs to an operator and the caller needs to know who to ask.</p>
     */
    public static class DeliveryDisabledException extends ShTeachingException {

        public DeliveryDisabledException() {
            super(ShProblem.badRequest("delivery-disabled",
                            "This instance cannot prove a route from the delivery side")
                    .withDetail("The `delivery` check fetches every published page from the "
                            + "site's own origin, and fetching on the server's behalf is a "
                            + "request-forgery surface, so it is off unless an operator turns "
                            + "it on and gives each site its URL.")
                    .withFix("Set shio.agent.render.allow-fetch=true and configure each site's "
                            + "URL, or run checks=content,routes — which answers the same "
                            + "question against the content graph, as a warning rather than "
                            + "an error.")
                    .withExample("?checks=content,routes"));
        }
    }

    /** A 400 that teaches the check vocabulary (SH80). */
    public static class UnknownCheckException extends ShTeachingException {

        public UnknownCheckException(String sent, Collection<String> allowed) {
            super(ShProblem.badRequest("unknown-check", "Unknown check '" + sent + "'")
                    .withDetail("'" + sent + "' is not a check group of the verify report.")
                    .withAllowed(allowed)
                    .withDidYouMeanFrom(sent)
                    .withFix("Name check groups as a comma-separated list, or omit `checks` "
                            + "to run all of them.")
                    .withExample("?checks=content,routes"));
        }
    }
}
