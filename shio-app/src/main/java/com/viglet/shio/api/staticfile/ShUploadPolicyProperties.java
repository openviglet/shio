/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * What an instance accepts as an upload, per media type (SH951).
 *
 * <p>{@code shio.upload.policy.<media-type>} takes one of {@code allow}, {@code deny} or
 * {@code sanitize}, and {@code shio.upload.default-verdict} decides everything unnamed. A key is
 * the bare media type — {@code shio.upload.policy.text/html=allow} — and {@code image/*} matches
 * a whole family.</p>
 *
 * <h2>The shipped defaults, and the one that had to be settled rather than assumed</h2>
 *
 * <p>{@code text/html} and {@code application/xhtml+xml} are <b>denied</b>: neither is ever
 * legitimately an asset, and a page is content, not a file. SVG is <b>sanitized</b> rather than
 * denied, because a replicated site's logo is very often one and refusing it would make the
 * product worse at its own job.</p>
 *
 * <p><b>JavaScript is denied, and that has a cost worth writing down.</b> A capture does not
 * collect scripts by default — {@code clone-assets.mjs} splits its inventory into
 * {@code DOWNLOADABLE} (image, stylesheet, font, icon, media, manifest) and {@code BEHAVIOUR}
 * (script, data) — so an ordinary replication uploads no JavaScript and is unaffected. But
 * {@code shio clone --scripts} (SH469) uploads a site's own bundle on purpose, and with this
 * default that upload is refused. Nothing in the suites would have caught it: {@code --scripts} is
 * exercised only in {@code planAssets} unit tests and never through a live upload, so the feature
 * would have broken quietly rather than redly. The refusal is a teaching 400 naming the property
 * that re-allows it, which is the difference between a blocked replication and a mystery.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Setter
@Getter
@Component
@ConfigurationProperties("shio.upload")
public class ShUploadPolicyProperties {

    /** What an unnamed media type gets. {@code allow}, because a policy is a list of exceptions. */
    private String defaultVerdict = "allow";

    /** Media type (or {@code family/*}) to verdict. Ordered, so the most specific key can be read. */
    private Map<String, String> policy = new LinkedHashMap<>(Map.of(
            "text/html", "deny",
            "application/xhtml+xml", "deny",
            "text/javascript", "deny",
            "application/javascript", "deny",
            "application/x-javascript", "deny",
            "image/svg+xml", "sanitize"));
}
