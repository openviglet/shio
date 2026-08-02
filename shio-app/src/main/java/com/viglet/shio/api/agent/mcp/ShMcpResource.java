/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp;

/**
 * One MCP resource — a document the client can read by URI (SH75 registry, SH83 set).
 *
 * <h2>Resource or tool?</h2>
 *
 * <p>The distinction is who decides to fetch it. A <b>tool</b> is called by the model
 * when it decides to act; a <b>resource</b> is something the client (or the human) can
 * attach to a conversation. So the manifest, the context pack and a post-type schema
 * are resources — they are reference material a session may want pinned — while
 * "find posts matching X" is a tool. Publishing the same payload both ways would be
 * paying for it twice in the tool list.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public interface ShMcpResource {

    /**
     * The URI, or a URI <em>template</em> when {@link #template()} is true —
     * e.g. {@code shio://manifest} or {@code shio://schema/{postType}}.
     */
    String uri();

    String name();

    String description();

    /** {@code text/plain} for the terse encodings, {@code application/json} otherwise. */
    String mimeType();

    /**
     * Where a caller with no MCP client reads the same document (SH346).
     *
     * <p>Declared rather than inferred, and declared <em>here</em> rather than in a table a test
     * owns, because it is a fact about the resource: publishing a document only over MCP is a P1
     * break, and P1 breaks are the class this repository keeps rediscovering (SH338). SH346 was
     * the second one — {@code shio://ops} had no twin for a block — and the instrument that finds
     * the third is {@code ShMcpResourceHttpTwinLintTest}, which fails the build when a resource
     * bean cannot answer this. Full path including {@code /api/v2}, and a query string when one
     * selects the document ({@code /api/v2/agent/context?include=ops}).</p>
     */
    String httpTwin();

    /** Templates are advertised on {@code resources/templates/list}, not {@code resources/list}. */
    default boolean template() {
        return uri().indexOf('{') >= 0;
    }

    /**
     * Whether this resource answers the given URI. The default handles both cases:
     * exact equality for a fixed URI, and the literal prefix before the first
     * {@code {} for a template — enough for the one-variable-at-the-end shapes this
     * surface uses, and honest about not being a general RFC 6570 matcher.
     */
    default boolean matches(String requested) {
        if (!template()) {
            return uri().equals(requested);
        }
        String prefix = uri().substring(0, uri().indexOf('{'));
        return requested.startsWith(prefix) && requested.length() > prefix.length();
    }

    /**
     * The body.
     *
     * @param requestedUri the URI actually asked for — the same as {@link #uri()} for a
     *                     fixed resource, the expanded form for a template
     * @throws com.viglet.shio.api.error.ShTeachingException when the URI names nothing
     */
    String read(String requestedUri);
}
