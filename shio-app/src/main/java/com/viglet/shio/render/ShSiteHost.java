/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.site.ShSite;

/**
 * SH1354 — a delivered site's address of its own: {@code <site>.<service-domain>}.
 *
 * <p>Delivery was positional only, {@code /sites/<site>/…} on the instance's host, which is right
 * behind somebody's front end and wrong where Shio <em>is</em> the host: nobody prints a path on a
 * shared hostname on a card. With {@code shio.sites.host-domain} set, a request whose host is
 * {@code <label>.<domain>} resolves the site the way the first path segment does, and the path that
 * follows is the site's own (the {@code x-sh-site} form, minus the {@code /sites} prefix).</p>
 *
 * <p><b>Off by default.</b> An empty domain answers nothing, so no existing instance changes. The
 * proxy, the wildcard certificate and the DNS stay infrastructure and outside the product (SH540's
 * line); what the product owes is this resolution, the canonical URL it emits, and a teaching 404
 * for a host no site claims.</p>
 *
 * <p><b>The label is the site's friendly URL, and only one that is a DNS label.</b> A host is
 * lower-cased by every browser, so a furl with an upper-case letter, a dot or an underscore cannot
 * round-trip through one; such a site keeps its {@code /sites} address and no host URL is emitted
 * for it, which is what keeps the emitted address and the resolved one the same set.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShSiteHost {

    /** One DNS label (RFC 1123), lower-case: what a browser will actually send back. */
    private static final Pattern LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");

    /**
     * Read on every call rather than copied (SH1375), so a test can set the domain on the one
     * properties bean and restore it, the SH1063 shape, instead of spending a context on a property.
     */
    private final ShSitesProperties properties;

    @Autowired
    public ShSiteHost(ShSitesProperties properties) {
        this.properties = properties;
    }

    public ShSiteHost(String domain, String scheme) {
        this.properties = new ShSitesProperties();
        this.properties.setHostDomain(domain);
        this.properties.setHostScheme(scheme);
    }

    /** Whether site hosts are configured at all. */
    public boolean enabled() {
        return !domain().isEmpty();
    }

    /** The scheme a site host is reached on, {@code https} unless configured (SH1375). */
    public String scheme() {
        String scheme = properties.getHostScheme();
        return scheme == null || scheme.isBlank() ? "https" : scheme.trim().toLowerCase(Locale.ROOT);
    }

    /** The configured service domain, or the empty string when site hosts are off. */
    public String domain() {
        return normalizeDomain(properties.getHostDomain());
    }

    /**
     * The site label a request's host names, or empty when the host is not a site host.
     *
     * @param serverName the request's host, with or without a port
     */
    public Optional<String> labelOf(String serverName) {
        return labelOf(serverName, domain());
    }

    /** {@link #labelOf(String)} against a given domain, so the rule is testable without a context. */
    static Optional<String> labelOf(String serverName, String domain) {
        if (serverName == null || domain == null || domain.isEmpty()) {
            return Optional.empty();
        }
        String host = serverName.trim().toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(']') < colon) {
            host = host.substring(0, colon);
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        String suffix = "." + domain;
        if (!host.endsWith(suffix)) {
            return Optional.empty();
        }
        String label = host.substring(0, host.length() - suffix.length());
        return LABEL.matcher(label).matches() ? Optional.of(label) : Optional.empty();
    }

    /**
     * The site's own origin, {@code <scheme>://<furl>.<domain>}, or empty when site hosts are off,
     * the site has a front end of its own ({@code site.url} wins: it is the address a human chose),
     * or its furl cannot be a host label.
     */
    public Optional<String> originOf(ShSite site) {
        if (!enabled() || site == null) {
            return Optional.empty();
        }
        if (site.getUrl() != null && !site.getUrl().isBlank()) {
            return Optional.empty();
        }
        String furl = site.getFurl();
        if (furl == null || !LABEL.matcher(furl).matches()) {
            return Optional.empty();
        }
        return Optional.of(scheme() + "://" + furl + "." + domain());
    }

    /**
     * SH1361 — where a visitor sees this page, at the site's own host when it has one: the one
     * composition every surface that emits a published URL calls, so the console's read and the
     * agent's {@code live} field cannot disagree.
     *
     * @param furl the page's friendly URL, or {@code null} for the home
     */
    public String publishedUrl(ShSite site, String furl) {
        return ShSiteFrontEnd.publishedUrl(site, furl, originOf(site).orElse(null));
    }

    private static String normalizeDomain(String domain) {
        if (domain == null) {
            return "";
        }
        String value = domain.trim().toLowerCase(Locale.ROOT);
        while (value.startsWith(".")) {
            value = value.substring(1);
        }
        while (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
