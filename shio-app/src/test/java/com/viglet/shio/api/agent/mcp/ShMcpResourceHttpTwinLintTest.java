/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * SH346 — no document is published over MCP alone.
 *
 * <h2>Why this exists rather than "fix the one resource"</h2>
 *
 * <p>SH346's finding was a single resource: {@code shio://ops} carried the write vocabulary and an
 * HTTP-only agent could not read it. Fixing that resource by hand would have left the same question
 * unanswered for the other five, and this repository has a rule for exactly that shape — SH322's,
 * sharpened by SH338: <b>when a review finds a design law is being broken, the instrument that finds
 * every other break lands before the fixes.</b> P1 is the law, and "MCP-only" is one of the two
 * directions it can be broken in; {@code ShAgentP1ConformanceTest} already asks the other (a feature
 * with no agent route), and its {@code Reach} vocabulary has no MCP-only category because until now
 * nothing looked.</p>
 *
 * <h2>What it checks, and why it is a mapping and not a table</h2>
 *
 * <p>Each resource bean declares {@link ShMcpResource#httpTwin()}, and this asserts the declared
 * path is <b>served by a real handler mapping</b>. A test-owned table of "resource → path" would
 * pass forever after the route it names is deleted or moved, which is the failure mode of every
 * inventory that lives beside the thing it describes rather than inside it. Path variable names are
 * normalised away ({@code {name}} and {@code {postType}} are the same route to a caller), and a
 * query string is stripped — {@code ?include=ops} selects the document within the route, so it is
 * information for a reader, not part of the mapping.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
class ShMcpResourceHttpTwinLintTest {

    @Autowired
    private List<ShMcpResource> resources;
    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    @DisplayName("SH346: every MCP resource declares an HTTP twin that is actually routed")
    void noResourceIsMcpOnly() {
        Set<String> routed = routedPatterns();
        List<String> unreachable = new ArrayList<>();

        for (ShMcpResource resource : resources) {
            String twin = resource.httpTwin();
            if (twin == null || twin.isBlank()) {
                unreachable.add(resource.uri() + " declares no HTTP twin");
                continue;
            }
            String path = normalise(twin);
            if (!routed.contains(path)) {
                unreachable.add(resource.uri() + " points at " + twin + ", which nothing serves");
            }
        }

        assertThat(unreachable)
                .as("""
                        a document only an MCP client can read is a P1 break — the operator this \
                        product is designed for is an agent, and an agent with HTTP and no MCP \
                        client is the common case, not the exotic one. Give the resource a REST \
                        twin and declare it; if there genuinely is not one, that is the decision \
                        this list is asking you to make out loud.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH346: the write vocabulary is the resource this instrument was built for")
    void theOpsResourceIsCovered() {
        // Named rather than left to the sweep: SH346 is what the sweep was built to catch, and a
        // rename of the resource or the section that quietly dropped it from the set above would
        // otherwise leave this passing with nothing to say.
        assertThat(resources).anySatisfy(resource -> {
            assertThat(resource.uri()).isEqualTo("shio://ops");
            assertThat(resource.httpTwin()).isEqualTo("/api/v2/agent/context?include=ops");
        });
    }

    /** Every URL pattern this application serves, with variable names normalised away. */
    private Set<String> routedPatterns() {
        Set<String> patterns = new TreeSet<>();
        for (RequestMappingInfo info : requestMappingHandlerMapping.getHandlerMethods().keySet()) {
            info.getPatternValues().forEach(pattern -> patterns.add(normalise(pattern)));
        }
        return patterns;
    }

    private static String normalise(String path) {
        int query = path.indexOf('?');
        String withoutQuery = query < 0 ? path : path.substring(0, query);
        return withoutQuery.replaceAll("\\{[^}]*\\}", "{}");
    }
}
