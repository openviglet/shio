/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.viglet.shio.api.agent.ShAgentProperties;
import com.viglet.shio.api.agent.ShAgentSiteAccess;
import com.viglet.shio.persistence.model.site.ShSite;

/**
 * The one place that fetches a delivery URL on the server's behalf (SH101, SH141).
 *
 * <h2>Why this is a class and not two private methods</h2>
 *
 * <p>SH101 added exactly one server-side fetch — {@code /agent/render?url=} — behind an
 * origin check and an operator's property, and wrote down why both guards exist: an
 * endpoint that fetches arbitrary URLs is a request-forgery surface with a friendly name.
 * SH141 needs the same fetch N times over, to turn "the front end does not serve this"
 * from a warning into an error. Two callers of a security-relevant guard is exactly one
 * caller too many for a private method: the second copy is where the origin check
 * eventually goes missing. So the guard, the client and the timeout live here, and both
 * callers are shapes over it (law P10 applied inside the codebase).</p>
 *
 * <h2>A failure is an outcome, not an exception</h2>
 *
 * <p>{@link #get(URI)} returns the status <em>or</em> the reason it could not be had.
 * SH101 wants an exception ("could not fetch, here is the fix"), because one call that
 * fails has nothing else to say. SH141 wants a value, because a front end that is down
 * must produce <em>one</em> finding and stop the pass — not N exceptions, and not a
 * report that gives up on the content checks it already completed. Returning the outcome
 * lets each caller decide, and keeps the decision out of here.</p>
 *
 * <h2>One client, not one per call</h2>
 *
 * <p>A {@link HttpClient} owns a selector thread and a connection pool. SH101 built one
 * per call, which is invisible at one call and wasteful at fifty, and it also threw away
 * the keep-alive that makes a fifty-page pass affordable. It is created on first use
 * because the overwhelmingly common case is an instance that never fetches anything.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShAgentDeliveryProbe {

    private final ShAgentSiteAccess siteAccess;
    private final ShAgentProperties properties;

    /** Created on first fetch; {@link HttpClient} is thread-safe and reusable. */
    private volatile HttpClient client;

    public ShAgentDeliveryProbe(ShAgentSiteAccess siteAccess, ShAgentProperties properties) {
        this.siteAccess = siteAccess;
        this.properties = properties;
    }

    /** Whether an operator has consented to server-side fetching at all. */
    public boolean enabled() {
        return properties.getRender().isAllowFetch();
    }

    /**
     * GETs a URL, capped at {@link ShAgentProperties.Render#getMaxBytes()}.
     *
     * @param target an absolute http(s) URI, already origin-checked by the caller
     * @return the status and body, or the reason there is neither
     */
    public Outcome get(URI target) {
        return get(target, true);
    }

    /**
     * GETs a URL and discards the body — the form a route proof wants.
     *
     * <p>{@code GET} rather than {@code HEAD} even though only the status is read: plenty
     * of front ends answer {@code HEAD} with a 405 they would never send for a real
     * visitor, and a proof that reports a defect the visitor cannot see is worse than no
     * proof. The body is read and dropped, which costs bandwidth inside the instance's own
     * network and buys an answer that means what it says.</p>
     */
    public Outcome status(URI target) {
        return get(target, false);
    }

    private Outcome get(URI target, boolean withBody) {
        ShAgentProperties.Render config = properties.getRender();
        HttpRequest request = HttpRequest.newBuilder(target)
                .timeout(Duration.ofMillis(config.getTimeoutMillis()))
                .header("Accept", "text/html")
                .GET()
                .build();
        try {
            if (!withBody) {
                HttpResponse<Void> response =
                        client().send(request, HttpResponse.BodyHandlers.discarding());
                return new Outcome(response.statusCode(), "", null);
            }
            HttpResponse<String> response =
                    client().send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body();
            if (body.length() > config.getMaxBytes()) {
                body = body.substring(0, config.getMaxBytes());
            }
            return new Outcome(response.statusCode(), body, null);
        } catch (IOException e) {
            return new Outcome(null, null, message(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(null, null, "interrupted");
        }
    }

    private static String message(IOException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private HttpClient client() {
        HttpClient existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (client == null) {
                client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(properties.getRender().getTimeoutMillis()))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
            }
            return client;
        }
    }

    /** Parses an absolute http(s) URL, or {@code null} when it is not one. */
    public static URI absolute(String url) {
        try {
            URI parsed = new URI(url);
            String scheme = parsed.getScheme() == null
                    ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || parsed.getHost() == null) {
                return null;
            }
            return parsed;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** The URLs of the sites this credential may read — the whole allow-list for a fetch. */
    public List<String> readableOrigins() {
        return siteAccess.readable().stream()
                .map(ShSite::getUrl)
                .filter(url -> url != null && !url.isBlank())
                .toList();
    }

    /**
     * Whether a target belongs to a site this instance hosts <em>and</em> this credential
     * can read.
     *
     * <p>Both halves matter. The first stops the endpoint being a proxy for the server's
     * network position; the second keeps it inside the SH2 allow-list, so the agent path
     * is not a privilege escalation (the Block L invariant) — a scoped token cannot use a
     * digest or a route proof to learn what another tenant's site looks like.</p>
     */
    public boolean hosted(URI target, List<String> origins) {
        return origins.stream().anyMatch(candidate -> sameOrigin(candidate, target));
    }

    /** Host and port must match; the scheme's default port counts as written. */
    public static boolean sameOrigin(String configured, URI target) {
        URI site = absolute(configured == null ? "" : configured.trim());
        if (site == null) {
            return false;
        }
        return site.getHost().equalsIgnoreCase(target.getHost()) && port(site) == port(target);
    }

    private static int port(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /**
     * Joins a site's configured URL and a site-relative path into one absolute URL.
     *
     * <p>Trailing and leading slashes are reconciled here rather than at each call site,
     * because {@code https://site.com/} + {@code /blog} producing {@code //blog} is the
     * defect that makes a route proof report every page as missing.</p>
     *
     * @return {@code null} when the site URL is not an absolute http(s) URL
     */
    public static URI join(String siteUrl, String path) {
        URI base = absolute(siteUrl == null ? "" : siteUrl.trim());
        if (base == null) {
            return null;
        }
        String root = base.toString();
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        String suffix = path == null || path.isBlank() ? "/" : path.trim();
        if (!suffix.startsWith("/")) {
            suffix = "/" + suffix;
        }
        return absolute(root + suffix);
    }

    /**
     * One fetch's result.
     *
     * @param status  the HTTP status, or {@code null} when the request did not complete
     * @param body    the response body when one was asked for; {@code ""} otherwise
     * @param failure why there is no status, in a form worth putting in a finding
     */
    public record Outcome(Integer status, String body, String failure) {

        public boolean served() {
            return status != null && status >= 200 && status < 300;
        }
    }
}
