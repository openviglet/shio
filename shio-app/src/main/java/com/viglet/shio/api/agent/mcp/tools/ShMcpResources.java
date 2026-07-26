/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp.tools;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.viglet.shio.api.agent.ShAgentContextService;
import com.viglet.shio.api.agent.ShAgentContextTerse;
import com.viglet.shio.api.agent.ShAgentManifestService;
import com.viglet.shio.api.agent.mcp.ShMcpResource;
import com.viglet.shio.api.cda.ShCdaService;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * The MCP resources (SH83): {@code shio://manifest}, {@code shio://context} and the
 * {@code shio://schema/{postType}} template.
 *
 * <p>All three are documents a human might reasonably want to pin into a conversation,
 * which is the test for "resource rather than tool". {@code shio://context} closes the
 * last item SH77 left open — the pack has been reachable over HTTP and from the CLI
 * since SH126, but not as something a client could attach.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Configuration(proxyBeanMethods = false)
public class ShMcpResources {

    private static final String TEXT = "text/plain";
    private static final String JSON = "application/json";
    private static final String SCHEMA_PREFIX = "shio://schema/";

    @Bean
    ShMcpResource shMcpManifestResource(ShAgentManifestService manifestService,
                                        ObjectMapper objectMapper) {
        return resource("shio://manifest", "Shio capability manifest",
                "What this instance can do: version, feature flags, limits, and the endpoint "
                        + "index. Read it before assuming a capability exists.",
                JSON,
                uri -> objectMapper.writeValueAsString(manifestService.manifest()));
    }

    @Bean
    ShMcpResource shMcpContextResource(ShAgentContextService contextService) {
        return resource("shio://context", "Shio context pack",
                "The content model, the sitemap by path and this instance's conventions, in the "
                        + "terse encoding. The same payload shio_context returns.",
                TEXT,
                uri -> ShAgentContextTerse.render(
                        contextService.context(null, null, null, null, null, false)));
    }

    /**
     * One post-type's field schema.
     *
     * <p>Served from the <b>CDA</b> schema (SH11), the same shape the context pack's
     * model section uses — not the lossless authoring definition. An agent writing
     * content needs to know the field names, widgets and cardinality; the verbatim
     * {@code widgetSettings} blob it would also get from {@code /api/v2/post-type} is
     * for the CLI's round-trip, and putting it here would make the cheap answer
     * expensive for every reader who did not need it.</p>
     */
    @Bean
    ShMcpResource shMcpSchemaResource(ShCdaService shCdaService,
                                      ShPostTypeRepository shPostTypeRepository,
                                      ObjectMapper objectMapper) {
        return resource(SCHEMA_PREFIX + "{postType}", "Post-type schema",
                "The fields of one post-type — names, widgets, required and multi-valued flags. "
                        + "Read it before writing content of a type you have not used yet.",
                JSON,
                uri -> {
                    String name = uri.substring(SCHEMA_PREFIX.length());
                    return objectMapper.writeValueAsString(shCdaService.getPostType(name)
                            .orElseThrow(() -> new UnknownSchemaException(name,
                                    shPostTypeRepository.findAll().stream()
                                            .map(ShPostType::getName)
                                            .filter(candidate -> candidate != null && !candidate.isBlank())
                                            .sorted().toList())));
                });
    }

    private static ShMcpResource resource(String uri, String name, String description,
                                          String mimeType, Reader reader) {
        return new ShMcpResource() {

            @Override
            public String uri() {
                return uri;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public String mimeType() {
                return mimeType;
            }

            @Override
            public String read(String requestedUri) {
                return reader.read(requestedUri);
            }
        };
    }

    @FunctionalInterface
    private interface Reader {
        String read(String uri);
    }

    /** A 404 that names the post-types this instance actually has (SH80). */
    public static class UnknownSchemaException extends ShTeachingException {

        public UnknownSchemaException(String sent, List<String> allowed) {
            super(ShProblem.badRequest("unknown-schema", "No post-type named '" + sent + "'")
                    .withDetail("The URI is shio://schema/<postTypeName>, by API name.")
                    .withAllowed(allowed)
                    .withDidYouMeanFrom(sent)
                    .withFix("Use one of the listed names, or read shio://context for the whole model.")
                    .withExample(SCHEMA_PREFIX + (allowed.isEmpty() ? "Article" : allowed.get(0))));
        }
    }
}
