/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.dto;

import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * A declarative statement of what a site's content <em>should</em> be (SH82).
 *
 * <h2>Why a second shape on top of the batch</h2>
 *
 * <p>A batch (SH81) says "do these six things"; a desired state says "this is the
 * site". The difference matters because only the second one is <b>re-runnable</b>:
 * an agent that has to remember what it already did is an agent that will get it
 * wrong after a context compaction. This document is a fact about the world, so
 * applying it twice is applying it once, and the second run's plan is empty.</p>
 *
 * <p>The engine underneath is the same one, deliberately (law P10): this document is
 * <em>compiled</em> into batch ops and then planned or applied by
 * {@code ShAgentBatchService}. SH65 already proved the shape for post-types —
 * declarative source → plan → apply → drift guard — and this is that engine over a
 * second resource type, not a second engine.</p>
 *
 * @param site   site name or id; every path in this document is relative to it
 * @param folders folder paths that must exist, e.g. {@code /blog}. Intermediate
 *                folders are created implicitly, so declaring {@code /a/b/c} is enough
 * @param posts   the posts that must exist, with the values they must hold
 * @param prune   delete posts that live under the declared folders but are not declared
 *                here. Off by default and never implied: prune is the one operation
 *                whose blast radius grows with content the author never mentioned (P7),
 *                which is why the plan lists every deletion before {@code apply} runs
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShAgentDesiredState(String site, List<Folder> folders, List<Post> posts,
                                  boolean prune) {

    /** @param path folder path from the site root, e.g. {@code /blog/2026} */
    public record Folder(String path) {
    }

    /**
     * @param url     the post's friendly URL — its identity in this document, which is
     *                what makes an apply idempotent without storing an id anywhere
     * @param type    post-type name; required only when the post has to be created
     * @param folder  containing folder path; required only when the post has to be
     *                created (a URL says nothing about where a post lives — SH79's
     *                "two namespaces" rule)
     * @param data    field values, keyed by post-type attribute name
     * @param publish publish it after writing. Absent means "leave the publish state
     *                alone", which is not the same as {@code false}: an apply that
     *                silently unpublished live pages because a field was omitted would
     *                be exactly the surprise P7 forbids
     */
    public record Post(String url, String type, String folder, JsonNode data, Boolean publish) {
    }
}
