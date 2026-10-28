/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.marketplace.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import tools.jackson.databind.JsonNode;

/**
 * One catalogue entry (SH646) — a blueprint package somebody can install.
 *
 * <h2>The shape follows Turing's, and the two additions are the reason this is not a copy</h2>
 *
 * <p>Turing's marketplace already runs this format in production with a console behind it, so
 * {@code id} … {@code readmeUrl} are taken as they are rather than re-litigated. A format with a
 * working consumer is worth more than a format that is marginally better shaped.</p>
 *
 * <p>Two fields a Turing recipe does not need, and a blueprint does:</p>
 *
 * <ul>
 *   <li>{@code params} — the JSON Schema the package takes. Carrying it in the <em>listing</em>
 *       means a console can draw the install form, and a caller can see what it will be asked,
 *       <b>without downloading the package first</b>. That is P2 read at catalogue level: the
 *       one call has to be enough to choose.</li>
 *   <li>{@code requires} — the SH151/SH152 opt-ins the package needs, as plain names
 *       ({@code createSite}, {@code editPostType}). A package that will alter a post-type the
 *       curator already has is precisely the thing P7 says must not be a surprise, and a
 *       consent asked at install time is only possible if the catalogue said so first.</li>
 * </ul>
 *
 * <h2>{@code installed} is about this instance, not about the package</h2>
 *
 * <p>Whether a package is already present is a fact about the reader's instance, so the author
 * cannot declare it and a catalogue file never carries it — the server stamps it by asking the
 * registry. The same reasoning that keeps {@code origin} out of a blueprint manifest: a package
 * that could describe its own standing could claim to be first-party.</p>
 *
 * @param id          the package name, which must equal the {@code name} in its manifest
 * @param title       human-readable name
 * @param description one paragraph, what applying it gives you
 * @param version     the package's own version
 * @param author      who published it
 * @param category    a coarse bucket for filtering
 * @param tags        finer filters
 * @param icon        an icon name the console resolves; never a URL, so a catalogue cannot
 *                    make a browser fetch from a host the operator did not name
 * @param downloadUrl the package zip; absent for a package that shipped with the instance,
 *                    because there is nothing to download
 * @param readmeUrl   the long-form description; proxied rather than fetched by the browser
 * @param params      the JSON Schema of the package's parameters, or null
 * @param requires    opt-in names the package needs before it may be applied
 * @param builtIn     true when this package shipped with the instance
 * @param installed   true when the registry already has a package under this name
 * @param screenshots SH1253 — up to {@link #MAX_SCREENSHOTS} image URLs showing what the package
 *                    builds. Proxied like the README and for its reason: the instance fetches
 *                    only a URL its own catalogue declares, so a catalogue still cannot make a
 *                    browser reach a host the operator did not name. Omitted when empty
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShMarketplaceItem(String id, String title, String description, String version,
                                String author, String category, List<String> tags, String icon,
                                String downloadUrl, String readmeUrl, JsonNode params,
                                List<String> requires, boolean builtIn, boolean installed,
                                @JsonInclude(Include.NON_EMPTY) List<String> screenshots) {

    /** The category the first-party packages are listed under. */
    public static final String FIRST_PARTY = "first-party";

    /** Who publishes the packages that ship with the instance. */
    public static final String VIGLET = "Viglet";

    /** SH1253 — a card and a dialog's worth; a catalogue is not a gallery host. */
    public static final int MAX_SCREENSHOTS = 4;

    /** Every key a catalogue file may carry — anything else is a typo, and typos are refusals. */
    public static final List<String> KEYS = List.of("id", "title", "description", "version",
            "author", "category", "tags", "icon", "downloadUrl", "readmeUrl", "params", "requires",
            "screenshots");

    public ShMarketplaceItem {
        tags = tags == null ? List.of() : List.copyOf(tags);
        requires = requires == null ? List.of() : List.copyOf(requires);
        screenshots = screenshots == null ? List.of()
                : List.copyOf(screenshots.subList(0, Math.min(screenshots.size(), MAX_SCREENSHOTS)));
    }

    /** The pre-SH1253 form: an entry with no screenshots. */
    public ShMarketplaceItem(String id, String title, String description, String version,
                             String author, String category, List<String> tags, String icon,
                             String downloadUrl, String readmeUrl, JsonNode params,
                             List<String> requires, boolean builtIn, boolean installed) {
        this(id, title, description, version, author, category, tags, icon, downloadUrl, readmeUrl,
                params, requires, builtIn, installed, List.of());
    }

    /** The same entry, with this instance's answer to "do I already have it?" stamped on. */
    public ShMarketplaceItem asInstalled(boolean present) {
        return new ShMarketplaceItem(id, title, description, version, author, category, tags,
                icon, downloadUrl, readmeUrl, params, requires, builtIn, present, screenshots);
    }
}
