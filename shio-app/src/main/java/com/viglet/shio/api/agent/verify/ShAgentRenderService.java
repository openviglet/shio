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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAddressResolver;
import com.viglet.shio.api.agent.ShAgentProperties;
import com.viglet.shio.api.agent.ShAgentSiteAccess;
import com.viglet.shio.api.agent.verify.dto.ShAgentDigest;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.widget.ShSystemWidget;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The render digest (SH101) — cheap textual proof that a page came out right.
 *
 * <h2>Two sources, and why both exist</h2>
 *
 * <p>{@code address=} digests the markup <b>Shio holds</b>: the post's rich-text fields.
 * That is the authoring truth, it needs no front end running, and it is the one an agent
 * can use immediately after a write. It answers "did my content land with the structure
 * I intended".</p>
 *
 * <p>{@code url=} fetches a delivery URL and digests the response. That answers the
 * strictly larger question — "does the page a visitor gets have that structure" — and it
 * is the one §IX.3 describes. It is <b>off by default</b> and restricted to the URLs of
 * sites this instance actually hosts, because an endpoint that fetches an arbitrary URL
 * on the server's behalf is a server-side request forgery with a friendly name. Enabling
 * it is one property ({@code shio.agent.render.allow-fetch}); the refusal says so.</p>
 *
 * <h2>Determinism</h2>
 *
 * <p>The digest hashes the outline only — heading levels and text, landmark counts, link
 * hrefs, image sources and whether each has alt. Not word counts (which move with a
 * typo fix) and not the source. So two digests differ exactly when the page's shape
 * differs, which is the comparison the field exists for.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Transactional(readOnly = true)
public class ShAgentRenderService {

    private static final String SOURCE_CONTENT = "content";
    private static final String SOURCE_FETCH = "fetch";

    /** Widgets whose value is markup. A plain Text field is not a page fragment. */
    /** Plain-text widgets whose value is visible prose — a section's heading and its copy. */
    private static final List<String> TEXT_WIDGETS =
            List.of(ShSystemWidget.TEXT, ShSystemWidget.TEXT_AREA);

    private static final List<String> MARKUP_WIDGETS =
            List.of(ShSystemWidget.HTML_EDITOR, ShSystemWidget.ACE_HTML);

    private final ShAddressResolver shAddressResolver;
    private final ShAgentSiteAccess siteAccess;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ObjectMapper objectMapper;
    private final ShAgentProperties properties;

    public ShAgentRenderService(ShAddressResolver shAddressResolver,
                                ShAgentSiteAccess siteAccess,
                                ShPostUnifiedRepository shPostUnifiedRepository,
                                ObjectMapper objectMapper,
                                ShAgentProperties properties) {
        this.shAddressResolver = shAddressResolver;
        this.siteAccess = siteAccess;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Digests a post's own markup, or a delivery URL.
     *
     * @param address SH79 {@code post:} address; exclusive with {@code url}
     * @param url     absolute delivery URL; requires {@code shio.agent.render.allow-fetch}
     */
    public ShAgentDigest digest(String address, String url) {
        boolean hasAddress = address != null && !address.isBlank();
        boolean hasUrl = url != null && !url.isBlank();
        if (hasAddress == hasUrl) {
            throw new NoDigestTargetException(hasAddress);
        }
        return hasAddress ? fromContent(address.trim()) : fromUrl(url.trim());
    }

    // --- content ------------------------------------------------------------

    private ShAgentDigest fromContent(String rawAddress) {
        var resolved = shAddressResolver.resolve(rawAddress);
        ShPostUnified post = draftPreferred(resolved.id())
                .orElseThrow(() -> new NotAPageException(rawAddress, resolved.kind()));

        List<String> fields = new ArrayList<>();
        ShHtmlOutline.Outline outline = own(post, fields);
        // SH169 — a page built the first-party way holds no markup of its own: its structure lives
        // in the section posts it names, in the order it names them. So the digest composes the way
        // the page does. Without this, home and pricing both digested as the hash of nothing and
        // were indistinguishable from a blank page — the digest could answer neither of the two
        // questions it exists for.
        outline = outline.merge(composed(post, fields));
        return render(SOURCE_CONTENT, shAddressResolver.addressOf(post).toString(), null, null,
                fields, outline);
    }

    /** A post's own markup fields, in ordinal order — what the digest has always read. */
    private ShHtmlOutline.Outline own(ShPostUnified post, List<String> fields) {
        JsonNode data = data(post);
        ShHtmlOutline.Outline outline = ShHtmlOutline.of(null);
        for (ShPostTypeAttr attr : markupFields(post.getShPostType())) {
            JsonNode value = data.get(attr.getName());
            if (value == null || !value.isString() || value.asString().isBlank()) {
                continue;
            }
            fields.add(attr.getName());
            outline = outline.merge(ShHtmlOutline.of(value.asString()));
        }
        return outline;
    }

    /**
     * The structure of the posts this page references, folded in declared order (SH169).
     *
     * <p><b>An unresolvable section is marked, not skipped.</b> Contributing nothing is the
     * present failure wearing a different hat — the page would digest as if the section were
     * simply absent, and two different mistakes would hash the same. It contributes a heading
     * naming the gap instead, so the digest changes when a reference breaks and a reader can see
     * why. SH154 is what reports it as a defect; this only makes sure the proof does not lie.</p>
     *
     * <p><b>Cost:</b> a page digest now reads N+1 posts. That is inherent — the structure really
     * is somewhere else — and it is the same scoping pressure as SH143.</p>
     */
    private ShHtmlOutline.Outline composed(ShPostUnified post, List<String> fields) {
        JsonNode data = data(post);
        ShHtmlOutline.Outline outline = ShHtmlOutline.of(null);
        String siteId = post.getShSite() == null ? null : post.getShSite().getId();
        for (ShPostTypeAttr attr : referenceFields(post.getShPostType())) {
            List<String> urls = ShPostReferenceFields.urls(data.get(attr.getName()));
            if (urls.isEmpty()) {
                continue;
            }
            fields.add(attr.getName());
            for (String url : urls) {
                Optional<ShPostUnified> section = referenced(siteId, url);
                outline = outline.merge(section.isPresent()
                        ? sectionOutline(section.get())
                        : ShHtmlOutline.of("<h2>[missing section " + url + "]</h2>"));
            }
        }
        return outline;
    }

    /**
     * One referenced post's structure.
     *
     * <h2>Why this is read from the post-type and not from a list of the eight first-party types</h2>
     *
     * <p>A section's structure is not in one HTML field: SH95's components render the section's
     * title as a heading and its text fields as prose. So the mapping is stated once, in terms of
     * what the model says about a field rather than what a type is called — a title field becomes a
     * heading, a markup field contributes its own parsed structure, and any other text field
     * contributes its words. A digest that knew the eight shipped section types by name would stop
     * working the first time somebody wrote a ninth.</p>
     *
     * <p>The synthetic markup is deliberate: it runs the same parser everything else does, so
     * there is one notion of "structure" rather than a second code path that would drift from it.
     * The heading level is the vocabulary's convention (a section heading sits under the page's
     * own h1), stated here once instead of guessed per type.</p>
     */
    private ShHtmlOutline.Outline sectionOutline(ShPostUnified section) {
        JsonNode data = data(section);
        ShHtmlOutline.Outline outline = ShHtmlOutline.of(null);
        for (ShPostTypeAttr attr : textFields(section.getShPostType())) {
            JsonNode value = data.get(attr.getName());
            if (value == null || !value.isString() || value.asString().isBlank()) {
                continue;
            }
            String text = value.asString();
            if (MARKUP_WIDGETS.contains(widgetName(attr))) {
                outline = outline.merge(ShHtmlOutline.of(text));
            } else if (attr.getIsTitle() == 1) {
                outline = outline.merge(ShHtmlOutline.of("<h2>" + escape(text) + "</h2>"));
            } else {
                outline = outline.merge(ShHtmlOutline.of("<p>" + escape(text) + "</p>"));
            }
        }
        return outline;
    }

    /** Multi Select fields that declare they hold post URLs (SH154's setting). */
    private List<ShPostTypeAttr> referenceFields(ShPostType postType) {
        return topLevel(postType).stream()
                .filter(attr -> ShPostReferenceFields.referencesPosts(attr, objectMapper))
                .toList();
    }

    /** Every text-bearing field of a post-type, ordinal-ordered so the outline is stable (P5). */
    private static List<ShPostTypeAttr> textFields(ShPostType postType) {
        return topLevel(postType).stream()
                .filter(attr -> TEXT_WIDGETS.contains(widgetName(attr))
                        || MARKUP_WIDGETS.contains(widgetName(attr)))
                .toList();
    }

    private static List<ShPostTypeAttr> topLevel(ShPostType postType) {
        if (postType == null || postType.getShPostTypeAttrs() == null) {
            return List.of();
        }
        return postType.getShPostTypeAttrs().stream()
                .filter(attr -> attr.getShParentPostTypeAttr() == null)
                .filter(attr -> attr.getName() != null && !attr.getName().isBlank())
                .sorted(Comparator.comparingInt(ShPostTypeAttr::getOrdinal)
                        .thenComparing(ShPostTypeAttr::getName))
                .toList();
    }

    private static String widgetName(ShPostTypeAttr attr) {
        return attr.getShWidget() == null ? "" : attr.getShWidget().getName();
    }

    /** The post a URL names in this site, either state — existence is what matters. */
    private Optional<ShPostUnified> referenced(String siteId, String url) {
        if (siteId == null) {
            return Optional.empty();
        }
        return shPostUnifiedRepository.findByShSite_IdAndFurlAndState(siteId, url, ShPostState.DRAFT)
                .or(() -> shPostUnifiedRepository.findByShSite_IdAndFurlAndState(siteId, url,
                        ShPostState.PUBLISHED));
    }

    /** Escapes the four characters that would otherwise change the synthetic markup's shape. */
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** Rich-text attributes of a post-type, ordinal-ordered (P5). */
    private static List<ShPostTypeAttr> markupFields(ShPostType postType) {
        if (postType == null || postType.getShPostTypeAttrs() == null) {
            return List.of();
        }
        return postType.getShPostTypeAttrs().stream()
                .filter(attr -> attr.getShParentPostTypeAttr() == null)
                .filter(attr -> attr.getName() != null && !attr.getName().isBlank())
                .filter(attr -> attr.getShWidget() != null
                        && MARKUP_WIDGETS.contains(attr.getShWidget().getName()))
                .sorted(Comparator.comparingInt(ShPostTypeAttr::getOrdinal)
                        .thenComparing(ShPostTypeAttr::getName))
                .toList();
    }

    // --- fetch --------------------------------------------------------------

    private ShAgentDigest fromUrl(String url) {
        ShAgentProperties.Render config = properties.getRender();
        if (!config.isAllowFetch()) {
            throw new FetchDisabledException();
        }
        URI target = uri(url);
        requireHostedSite(target);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getTimeoutMillis()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(target)
                .timeout(Duration.ofMillis(config.getTimeoutMillis()))
                .header("Accept", "text/html")
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body();
            if (body.length() > config.getMaxBytes()) {
                body = body.substring(0, config.getMaxBytes());
            }
            // The status travels even on a 404: "the page 404s" is the single most useful
            // thing this call can say, and throwing it away as an error would hide it.
            return render(SOURCE_FETCH, null, target.toString(), response.statusCode(),
                    List.of(), ShHtmlOutline.of(body));
        } catch (IOException e) {
            throw new FetchFailedException(target.toString(), e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchFailedException(target.toString(), "interrupted");
        }
    }

    private static URI uri(String url) {
        try {
            URI parsed = new URI(url);
            String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || parsed.getHost() == null) {
                throw new BadFetchUrlException(url, "Only absolute http(s) URLs can be fetched.");
            }
            return parsed;
        } catch (URISyntaxException e) {
            throw new BadFetchUrlException(url, "That is not a URL.");
        }
    }

    /**
     * The URL must belong to a site this instance hosts and this credential can read.
     *
     * <p>Both halves matter. The first stops the endpoint being a proxy for the server's
     * network position; the second keeps it inside the SH2 allow-list, so the agent path
     * is not a privilege escalation (the Block L invariant) — a scoped token cannot use
     * a digest to prove what another tenant's site looks like.</p>
     */
    private void requireHostedSite(URI target) {
        List<ShSite> readable = siteAccess.readable();
        List<String> configured = readable.stream()
                .map(ShSite::getUrl)
                .filter(url -> url != null && !url.isBlank())
                .toList();
        boolean hosted = configured.stream().anyMatch(candidate -> sameOrigin(candidate, target));
        if (!hosted) {
            throw new UnhostedUrlException(target.toString(), configured);
        }
    }

    private static boolean sameOrigin(String configured, URI target) {
        try {
            URI site = new URI(configured.trim());
            if (site.getHost() == null) {
                return false;
            }
            return site.getHost().equalsIgnoreCase(target.getHost()) && port(site) == port(target);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static int port(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    // --- shaping ------------------------------------------------------------

    private ShAgentDigest render(String source, String address, String url, Integer status,
                                 List<String> fields, ShHtmlOutline.Outline outline) {
        List<ShAgentDigest.Heading> headings = outline.headings().stream()
                .map(heading -> new ShAgentDigest.Heading(heading.level(), heading.text()))
                .toList();
        List<ShAgentDigest.Link> links = outline.links().stream()
                .map(link -> new ShAgentDigest.Link(link.href(), internal(link.href()), link.text()))
                .toList();
        List<ShAgentDigest.Image> images = outline.images().stream()
                .map(image -> new ShAgentDigest.Image(image.src(), image.alt()))
                .toList();
        return new ShAgentDigest(source, address, url, status,
                fields.isEmpty() ? null : List.copyOf(fields),
                headings, outline.landmarks(), links, images, outline.words(),
                digestOf(headings, outline.landmarks(), links, images));
    }

    private static boolean internal(String href) {
        return href != null && href.startsWith("/") && !href.startsWith("//");
    }

    /**
     * {@code d:} + the first 12 hex of SHA-256 over the canonical outline. Short enough
     * to carry in a report, long enough to trust — the same shape as the context pack's.
     */
    static String digestOf(List<ShAgentDigest.Heading> headings, java.util.Map<String, Integer> landmarks,
                           List<ShAgentDigest.Link> links, List<ShAgentDigest.Image> images) {
        StringBuilder canonical = new StringBuilder();
        headings.forEach(heading -> canonical.append('h').append(heading.level()).append(' ')
                .append(heading.text()).append('\n'));
        landmarks.forEach((name, count) -> canonical.append('l').append(' ').append(name)
                .append(' ').append(count).append('\n'));
        links.forEach(link -> canonical.append('a').append(' ').append(link.href()).append('\n'));
        images.forEach(image -> canonical.append('i').append(' ').append(image.src())
                .append(' ').append(image.alt() == null ? "-" : "alt").append('\n'));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("d:");
            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private java.util.Optional<ShPostUnified> draftPreferred(String postId) {
        var draft = shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT);
        return draft.isPresent()
                ? draft
                : shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED);
    }

    private JsonNode data(ShPostUnified post) {
        String json = post.getData();
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode parsed = objectMapper.readTree(json);
            return parsed.isObject() ? parsed : objectMapper.createObjectNode();
        } catch (JacksonException e) {
            return objectMapper.createObjectNode();
        }
    }

    // --- teaching errors (SH80) ---------------------------------------------

    /** A 400: neither target, or both. */
    public static class NoDigestTargetException extends ShTeachingException {

        public NoDigestTargetException(boolean both) {
            super(ShProblem.badRequest("no-digest-target",
                            both ? "Pass either `address` or `url`, not both"
                                 : "Nothing to digest")
                    .withDetail(both
                            ? "`address` digests the markup Shio holds; `url` digests what a "
                                    + "visitor gets. They answer different questions, so the call "
                                    + "picks one."
                            : "A digest needs a page: a post address, or a delivery URL.")
                    .withFix("Send ?address=post:<site>/<url> for the authoring view, or "
                            + "?url=https://… for the delivered page.")
                    .withExample("?address=post:Blog/blog/hello-world"));
        }
    }

    /** A 400: the address resolved, but not to a post. */
    public static class NotAPageException extends ShTeachingException {

        public NotAPageException(String address, String kind) {
            super(ShProblem.badRequest("not-a-page",
                            "'" + address + "' is a " + kind + ", not a post")
                    .withDetail("A digest summarises one page's structure; a folder has none.")
                    .withFix("Pick a post with GET /api/v2/agent/find?folder=… and digest that.")
                    .withExample("?address=post:Blog/blog/hello-world"));
        }
    }

    /** A 400 that says which property turns fetching on, rather than pretending it 404s. */
    public static class FetchDisabledException extends ShTeachingException {

        public FetchDisabledException() {
            super(ShProblem.badRequest("fetch-disabled", "Fetching a delivery URL is disabled")
                    .withDetail("An endpoint that fetches URLs on the server's behalf is a "
                            + "request-forgery surface, so it is off unless an operator turns it on.")
                    .withFix("Set shio.agent.render.allow-fetch=true (and give each site its URL), "
                            + "or digest the authoring view with ?address=post:<site>/<url>, which "
                            + "needs no front end running.")
                    .withExample("?address=post:Blog/blog/hello-world"));
        }
    }

    /** A 400 for a URL this instance does not host. */
    public static class UnhostedUrlException extends ShTeachingException {

        public UnhostedUrlException(String sent, List<String> allowed) {
            super(ShProblem.badRequest("unhosted-url", "Refusing to fetch " + sent)
                    .withDetail("Only the origins of sites this credential can read may be "
                            + "fetched — otherwise this endpoint is a proxy for the server's "
                            + "network position.")
                    .withAllowed(allowed)
                    .withFix(allowed.isEmpty()
                            ? "No readable site has a URL configured. Set the site's URL first, "
                                    + "or digest the authoring view with ?address=."
                            : "Fetch a path under one of the listed origins.")
                    .withExample(allowed.isEmpty() ? "?address=post:Blog/blog/hello-world"
                            : "?url=" + allowed.get(0) + "/blog/hello-world"));
        }
    }

    /** A 400 for a malformed URL. */
    public static class BadFetchUrlException extends ShTeachingException {

        public BadFetchUrlException(String sent, String why) {
            super(ShProblem.badRequest("bad-fetch-url", "Cannot fetch '" + sent + "'")
                    .withDetail(why)
                    .withFix("Send an absolute URL including the scheme.")
                    .withExample("?url=https://example.com/blog/hello-world"));
        }
    }

    /**
     * A 400, not a 502: from the caller's side the actionable fact is that the front end
     * is not answering, and a fix it can act on ("start it, or digest the content
     * instead") is worth more than the correct gateway status.
     */
    public static class FetchFailedException extends ShTeachingException {

        public FetchFailedException(String url, String why) {
            super(ShProblem.badRequest("fetch-failed", "Could not fetch " + url)
                    .withDetail(why == null ? "The request did not complete." : why)
                    .withFix("Check the front end is running and reachable from this instance, "
                            + "or digest the authoring view with ?address=post:<site>/<url>.")
                    .withExample("?address=post:Blog/blog/hello-world"));
        }
    }
}
