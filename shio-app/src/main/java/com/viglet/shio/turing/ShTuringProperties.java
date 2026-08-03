/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.turing;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Tunables for indexing published content into Turing (SH84). Bound to
 * {@code shio.turing.*}, mirroring {@link com.viglet.shio.webhook.ShWebhookProperties}.
 *
 * <h2>Off by default, and it has to be</h2>
 *
 * <p>Unlike the SH12 webhook switch, {@link #enabled} defaults to {@code false}. Indexing sends
 * content to <em>another system</em>, and an instance that starts pushing every publish to a
 * URL nobody configured is P7's exact prohibition — never surprise the human. A Shio that has
 * never heard of Turing therefore behaves identically to one built before this existed: no
 * dependency is contacted, no thread is started, no incident is recorded.</p>
 *
 * <h2>Why the site map is configuration and not a column</h2>
 *
 * <p>Which Turing SN site a Shio site indexes into is a <em>deployment</em> fact: the same
 * content tree is staged into one SN site and produced into another, and the two Shio instances
 * are the same content. Putting it in the database would make it content, so an export/import
 * would carry one environment's routing into another. {@link #defaultSite} covers the common
 * single-site case; {@link #sites} names the exceptions, keyed by Shio site name.</p>
 *
 * <p>A site with no mapping and no default is <b>not indexed</b>, and that is deliberate rather
 * than a gap: guessing an SN site name would either create documents in the wrong index or fail
 * once per publish forever. {@code ShTuringIndexService} says which sites are routed on startup.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Component
@ConfigurationProperties("shio.turing")
public class ShTuringProperties {

    /** Master switch. {@code false} so an unconfigured instance contacts nothing. */
    private boolean enabled = false;

    /** Turing's base URL, e.g. {@code http://localhost:2700}. Required when enabled. */
    private String url;

    /** Turing API key, sent by the SDK as the service-to-service credential. */
    private String apiKey;

    /** SN site name for any Shio site not named in {@link #sites}; {@code null} to route none. */
    private String defaultSite;

    /** Shio site name → Turing SN site name, for the sites that differ from {@link #defaultSite}. */
    private Map<String, String> sites = new LinkedHashMap<>();

    /**
     * The {@code source_apps} value stamped on every document.
     *
     * <p>It is what makes a bulk de-index possible ({@code TurSNJobUtils.deleteItemsByType}) and
     * what lets an SN site mixing Shio pages with a crawler's documents tell them apart. A
     * constant would have done; it is a property because two Shio instances feeding one SN site
     * is the case where the distinction starts mattering.
     */
    private String sourceApp = "shio";

    /**
     * Whether a de-index is sent when a post is unpublished.
     *
     * <p>Separate from the delete case because they are different decisions. A delete is
     * unambiguous — the page is gone, and leaving it in the index serves a 404 from search. An
     * unpublish may be a five-minute correction, and an operator who would rather keep search
     * warm through it can say so. Default {@code true}: a search hit that leads nowhere is worse
     * than a gap.
     */
    private boolean deIndexOnUnpublish = true;
}
