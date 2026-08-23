/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.turing;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
     * Whether a published attachment's text is extracted by Turing's OCR API and indexed (SH913).
     *
     * <p>{@code true}, and the consent is switching indexing on: a published file is already public
     * and already sent to Turing by title and URL, so this adds the one field that makes it answer a
     * search. It is a switch rather than a constant because extraction is <em>Turing's</em> cost —
     * the engine behind {@code /api/ocr/file} is its configuration, and an instance whose Turing
     * bills per page may want the metadata without it.</p>
     */
    private boolean extractText = true;

    /** File extensions whose text is asked for. Anything else indexes exactly as it does today. */
    private List<String> extractTypes = new ArrayList<>(List.of(
            "pdf", "doc", "docx", "odt", "rtf", "txt", "md",
            "ppt", "pptx", "odp", "xls", "xlsx", "ods"));

    /**
     * Bytes above which a file is indexed by metadata alone, with an incident. {@code 0} removes
     * the limit. Thirty-two megabytes is well past a prospectus and well short of a video.
     */
    private long extractMaxSize = 32L * 1024 * 1024;

    /**
     * How long Shio waits for Turing to read one file before indexing it without text.
     *
     * <p>Shio's bound, not the SDK's: {@code TurOcr} hardcodes a five-minute socket timeout, and
     * five minutes is far too long to hold the single index worker that keeps publish ordering.</p>
     */
    private Duration extractTimeout = Duration.ofSeconds(60);

    /**
     * How many times one push is attempted in total, the first included. {@code 1} disables
     * retrying and restores SH84's behaviour exactly.
     *
     * <p>Three, because the failure this exists for is a Turing restart — seconds, not minutes —
     * and the retries are spent by the single index worker, so a larger bound buys a longer stall
     * for a case the bounded queue already handles honestly (see {@link ShTuringIndexListener}).</p>
     */
    private int maxAttempts = 3;

    /**
     * The wait before the second attempt, doubled before each attempt after it.
     *
     * <p>With the defaults a push that never succeeds occupies the worker for 1s + 2s before it is
     * given up on. {@code ZERO} retries immediately, which is what the tests use.</p>
     */
    private Duration retryBackoff = Duration.ofSeconds(1);

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
