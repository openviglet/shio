/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.turing;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.client.sn.job.TurSNJobUtils;
import com.viglet.turing.commons.sn.field.TurSNFieldName;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends published content to Turing's indexer (SH84).
 *
 * <h2>Composing, not forking (P10)</h2>
 *
 * <p>The wire format is {@code TurSNJobItems} posted to {@code /api/sn/import}, built and sent by
 * {@code turing-java-sdk} — the same artifact and the same call
 * ({@code TurSNJobUtils.importItems}) that Dumont's {@code DumTuringIndexingPlugin} indexes
 * through. Shio therefore speaks the ecosystem's existing contract rather than a second one, and
 * a change to Turing's job vocabulary reaches Shio by bumping a version instead of by somebody
 * remembering.</p>
 *
 * <h2>What "optional" means here</h2>
 *
 * <p>Three independent things can switch a publish off this path, and each of them answers a
 * different question: {@code enabled} (does this instance index at all), a configured
 * {@code url} (does it know where), and a resolved SN site (does <em>this</em> site route
 * anywhere). All three are checked before anything is built, so the disabled case costs no
 * serialization and no allocation — and none of them is an error, because an instance that does
 * not index is not a broken instance.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Slf4j
@Service
public class ShTuringIndexService {

    private final ShTuringProperties properties;
    private final ShTuringDocumentMapper mapper;

    public ShTuringIndexService(ShTuringProperties properties, ShTuringDocumentMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    /** Whether this instance indexes at all — enabled <em>and</em> pointed at something. */
    public boolean active() {
        return properties.isEnabled() && properties.getUrl() != null
                && !properties.getUrl().isBlank();
    }

    /**
     * The Turing SN site a Shio site's content belongs in, or empty when it routes nowhere.
     *
     * <p>Matched case-insensitively: an SN site name and a Shio site name are typed by people into
     * two different systems, and "Cafeteria" not matching "cafeteria" would present as indexing
     * that silently does nothing.</p>
     */
    public Optional<String> snSiteFor(String shioSiteName) {
        if (shioSiteName != null) {
            for (Map.Entry<String, String> entry : properties.getSites().entrySet()) {
                if (shioSiteName.equalsIgnoreCase(entry.getKey())) {
                    return present(entry.getValue());
                }
            }
        }
        return present(properties.getDefaultSite());
    }

    /**
     * The job item that would index this post, or empty when the post is not indexed.
     *
     * <h2>Why building and sending are two methods</h2>
     *
     * <p>Building touches the entity — its site, its post-type, its {@code data} — so it must run
     * on the thread that still holds the Hibernate session and the tenant. Sending must <em>not</em>
     * run there, because a slow Turing would then be a slow publish. One method that did both would
     * have to pick one of those, and the choice that compiles is the one that throws
     * {@code LazyInitializationException} in production and never in a test that has a session open.
     * So the entity stops here and a plain data carrier crosses the boundary.</p>
     */
    public Optional<TurSNJobItem> indexItem(ShPostUnified post) {
        Optional<String> snSite = routed(post);
        if (snSite.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> attributes =
                new HashMap<>(mapper.attributes(post, properties.getSourceApp()));
        // SH914 — the schema travels with the document. A field whose type Turing does not know is
        // a field no range filter can use, and a schema kept in Turing's console as well as here is
        // a schema that will disagree. Empty for a post-type that declares nothing, which is every
        // post-type until somebody declares one, so the wire shape is unchanged for them.
        List<TurSNJobAttributeSpec> specs = mapper.specs(post);
        return Optional.of(specs.isEmpty()
                ? new TurSNJobItem(TurSNJobAction.CREATE, List.of(snSite.get()),
                        locale(post.getLocale()), attributes)
                : new TurSNJobItem(TurSNJobAction.CREATE, List.of(snSite.get()),
                        locale(post.getLocale()), attributes, specs));
    }

    /**
     * The job item that removes a post from the index, by id.
     *
     * <p>Takes the id, site name and locale rather than the post, because the caller that most
     * needs this has none: a purge has already removed the row by the time the
     * {@code AFTER_COMMIT} listener runs. Turing de-indexes by {@code id} alone (its queue reads
     * {@code TurSNFieldName.ID} and falls back to {@code TYPE}), which is why an id is enough.</p>
     */
    public Optional<TurSNJobItem> deIndexItem(String postId, String shioSiteName, String localeTag) {
        Optional<String> snSite = snSiteFor(shioSiteName);
        if (!active() || snSite.isEmpty() || postId == null) {
            return Optional.empty();
        }
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.ID, postId);
        attributes.put(TurSNFieldName.SOURCE_APPS, properties.getSourceApp());
        return Optional.of(new TurSNJobItem(TurSNJobAction.DELETE, List.of(snSite.get()),
                locale(localeTag), attributes));
    }

    /** The SN site this post's content routes to, or empty when it is not indexed. */
    public Optional<String> routed(ShPostUnified post) {
        if (!active() || post == null || post.getShSite() == null) {
            return Optional.empty();
        }
        return snSiteFor(post.getShSite().getName());
    }

    /**
     * Posts one already-built job item. Touches no entity and no repository, which is what makes it
     * safe to call from the executor.
     *
     * @return whether Turing accepted the job
     */
    public boolean send(TurSNJobItem item, String what) {
        TurSNJobItems job = new TurSNJobItems();
        job.add(item);
        boolean sent = TurSNJobUtils.importItems(job, server(), false);
        if (sent) {
            log.debug("Turing accepted the job to {}", what);
        }
        return sent;
    }

    private TurSNServer server() {
        return new TurSNServer(URI.create(properties.getUrl()), null,
                new TurApiKeyCredentials(properties.getApiKey()));
    }

    /**
     * A language tag as a {@link Locale}, defaulting to English.
     *
     * <p>{@code Locale.forLanguageTag} answers the root locale for a tag it cannot parse, and a
     * root locale reaches Turing as an empty language — a document nobody's locale-filtered search
     * would find. English is what {@code TurSNJobItem} itself defaults to, so an unparseable tag
     * lands where a missing one would.</p>
     */
    private static Locale locale(String tag) {
        if (tag == null || tag.isBlank()) {
            return Locale.ENGLISH;
        }
        Locale parsed = Locale.forLanguageTag(tag.replace('_', '-'));
        return parsed.getLanguage().isEmpty() ? Locale.ENGLISH : parsed;
    }

    private static Optional<String> present(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
