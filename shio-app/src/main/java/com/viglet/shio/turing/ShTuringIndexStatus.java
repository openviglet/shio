/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.turing;

import java.util.List;

/**
 * SH1063 — where this instance sends what it publishes, and which sites go nowhere.
 *
 * <h2>The routing table is the answer, not the switch</h2>
 *
 * <p>"Indexing is on" is one bit and it is not the question anybody has. The question is whether
 * <em>this</em> site's pages reach a search index, and the failure that costs most is a site that
 * maps to nothing: the listener builds a document, finds no route and sends nothing, and no error
 * is raised anywhere. So the table lists every site an instance holds with the search site it
 * resolves to, and a null there is the finding.</p>
 *
 * @param enabled     configured and pointed somewhere — {@link ShTuringIndexService#active()}
 * @param target      the indexer's URL, so "pointed where?" is answerable; never the API key
 * @param defaultSite the search site a site with no mapping of its own falls back to, or null
 * @param sites       one row per site this instance holds, in name order (P5)
 * @param failures    how many indexing failures the incident window still holds
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShTuringIndexStatus(boolean enabled, String target, String defaultSite,
                                  List<Route> sites, int failures) {

    /**
     * One site's route into the index.
     *
     * @param site       the Shio site's name
     * @param searchSite where its content goes, or null when nothing routes it
     * @param published  how many published pages it has, which is what a broken route costs
     */
    public record Route(String site, String searchSite, long published) {
    }
}
