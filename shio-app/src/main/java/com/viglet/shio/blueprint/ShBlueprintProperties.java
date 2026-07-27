/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Where this instance may load blueprint packages from (SH96).
 *
 * <h2>The trust model, which is the whole feature</h2>
 *
 * <p>A blueprint is code the instance runs against its own content: it provisions a model,
 * writes pages, and — with the SH151/SH152 opt-ins — creates a site and edits an existing
 * post-type. So "where may a package come from" is a trust question, and the registry
 * deliberately answered it with <em>the classpath and nothing else</em> until there was
 * one. Shipping a scanned directory or a URL to fetch before that would have been shipping
 * the distribution channel without the trust model.</p>
 *
 * <p>The model this adds is the smallest one that is actually a model: <b>an operator names
 * a directory</b>. Whoever can set this property can already put a jar on the classpath, so
 * it grants no authority that did not exist — it just makes the existing authority usable
 * without repackaging. The person who set it is the person vouching for what is in it, and
 * that is a sentence with a subject, which "packages resolve from npm" is not.</p>
 *
 * <h2>What it deliberately is not</h2>
 *
 * <p><b>No URL fetching, no auto-discovery, no registry resolution.</b> Each of those moves
 * the decision from an operator to a network, and the interesting half of SH96 — a naming
 * convention, a curated index, a signature — is exactly the part that is missing. This is
 * the local half; the community half is still open (SH217).</p>
 *
 * <p><b>Off by default</b>, and a configured directory that is absent is a startup failure
 * rather than an empty registry: an operator who pointed at a path meant it, and a
 * misspelling that silently degraded to "the first-party set" is how somebody ships a site
 * built from the wrong packages.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "shio.blueprint")
public class ShBlueprintProperties {

    /**
     * A directory holding one subdirectory per package, each with a {@code blueprint.json}
     * — the same layout the classpath uses, so a package can be developed in a folder and
     * shipped in a jar without changing.
     *
     * <p>Empty (the default) means the classpath alone.</p>
     */
    private String path;

    /** True when an operator has named a directory to load from. */
    public boolean hasPath() {
        return path != null && !path.isBlank();
    }
}
