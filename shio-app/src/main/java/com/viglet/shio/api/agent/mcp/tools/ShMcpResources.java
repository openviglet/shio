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
import com.viglet.shio.blueprint.ShBlueprintRegistry;
import com.viglet.shio.blueprint.ShBlueprintTerse;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * The MCP resources (SH83, extended by SH93 and SH166): {@code shio://manifest},
 * {@code shio://blueprints}, {@code shio://context}, and the
 * {@code shio://schema/{postType}} and {@code shio://blueprint/{name}} templates.
 *
 * <p>All of them are documents a human might reasonably want to pin into a conversation,
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
    private static final String BLUEPRINT_PREFIX = "shio://blueprint/";
    private static final String FILES_SUFFIX = "/files";

    @Bean
    ShMcpResource shMcpManifestResource(ShAgentManifestService manifestService,
                                        ObjectMapper objectMapper) {
        return resource("shio://manifest", "Shio capability manifest",
                "What this instance can do: version, feature flags, limits, and the endpoint "
                        + "index. Read it before assuming a capability exists.",
                JSON,
                uri -> objectMapper.writeValueAsString(manifestService.manifest()));
    }

    /**
     * The blueprint catalogue (SH92/SH93).
     *
     * <p>A resource rather than a tool because it is a <em>document about what is
     * available</em>, not an action — and because the alternative is worse: an agent that
     * cannot see the catalogue will model a blog by hand, which is the exact cost Block J
     * exists to remove. Terse, and without the per-package parameter schemas: choosing
     * between five packages must not cost five schemas, and the one the agent picks is a
     * cheap second call.</p>
     */
    @Bean
    ShMcpResource shMcpBlueprintsResource(ShBlueprintRegistry registry) {
        return resource("shio://blueprints", "Shio blueprint catalogue",
                "The appliable starting points this instance carries — a whole section (content "
                        + "model + folders + pages) per package, with the parameters each takes. "
                        + "Read it before building a blog, a docs tree or a marketing site by "
                        + "hand; apply one with shio_apply's `blueprint` argument.",
                TEXT,
                uri -> ShBlueprintTerse.catalogue(registry.summaries()));
    }

    /**
     * One blueprint, and — with the {@code /files} suffix — the bytes of its front-end half
     * (SH166).
     *
     * <h2>The hole this closes</h2>
     *
     * <p>{@code shio_apply} has reported a package's carried files since SH93, and an MCP-only
     * caller had no way to read one. The paths were a list of things that exist somewhere else:
     * the bodies travel on {@code GET /agent/blueprints/{name}?include=files}, which a client
     * with no HTTP of its own cannot reach. So the honest reading of SH166 is not only that the
     * files are unsubstituted — it is that for this caller they were unreachable, and "your
     * front end is thirteen files you cannot see" is a worse answer than either option the
     * design weighed.</p>
     *
     * <h2>Why a resource and not a tool argument</h2>
     *
     * <p>Because it is reference material a session pins, which is the test this class applies,
     * and because the alternative is unaffordable: {@code shio_apply} measures 457 against a
     * 460 per-tool ceiling, so the argument that would carry this does not fit, and
     * {@code tools/list} is re-sent every turn (P3). A resource template is one row on
     * {@code resources/templates/list}, read only when somebody asks for it.</p>
     *
     * <h2>Two URIs, one template</h2>
     *
     * <p>{@code shio://blueprint/<name>} is the parameter-by-parameter detail;
     * {@code shio://blueprint/<name>/files} adds the bodies, which are much the larger read
     * and are therefore something the caller asks for rather than something it is handed.
     * They share a bean because {@link ShMcpResource#matches} keys on the literal prefix
     * before the first brace — two beans would both claim {@code shio://blueprint/…} and the
     * one that answered would depend on sort order.</p>
     */
    @Bean
    ShMcpResource shMcpBlueprintResource(ShBlueprintRegistry registry) {
        return resource(BLUEPRINT_PREFIX + "{name}", "Shio blueprint",
                "One package in full: every parameter with its default, the post-types it "
                        + "provisions, and the front-end files it carries. Append /files to the "
                        + "URI for their bodies — the server never writes those into a project "
                        + "and never substitutes them, so each is listed with the {{placeholder}}s "
                        + "you still have to fill in from the parameters above.",
                TEXT,
                uri -> {
                    String path = uri.substring(BLUEPRINT_PREFIX.length());
                    boolean withFiles = path.endsWith(FILES_SUFFIX);
                    String name = withFiles
                            ? path.substring(0, path.length() - FILES_SUFFIX.length())
                            : path;
                    var blueprint = registry.require(name);
                    return withFiles
                            ? ShBlueprintTerse.fileBodies(blueprint)
                            : ShBlueprintTerse.detail(blueprint);
                });
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
