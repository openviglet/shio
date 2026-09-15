/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda.graphql;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.cda.ShCdaService;
import com.viglet.shio.api.cda.dto.ShCdaPost;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;

import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.TypeResolver;
import tools.jackson.databind.JsonNode;

/**
 * Builds the Content Delivery GraphQL schema (SH4) programmatically from the
 * content post-types.
 *
 * <p>One {@code ShContent} interface carries the common post fields; each
 * {@code system=0} post-type becomes an object type implementing it, adding a
 * {@code String} field per {@link ShPostTypeAttr} (keyed by attr {@code name}).
 * A {@code ShGenericPost} type backs system / unmapped post-types. The {@code
 * Query} root ({@code post}/{@code postByUrl}/{@code posts}) delegates to
 * {@link ShCdaService}, so the schema inherits SH2 per-site scoping and the
 * {@code PUBLISHED}-only rule rather than re-implementing them.</p>
 *
 * <p>The schema is built once, at application startup, by the
 * {@link ShGraphQLConfig} customizer. Creating or editing a post-type needs a
 * restart to appear in the schema (documented caveat — see
 * {@code docs/specs/SH4-cda-graphql-schema.md}).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Component
public class ShGraphQLSchema {

    static final String CONTENT_INTERFACE = "ShContent";
    static final String GENERIC_TYPE = "ShGenericPost";
    static final String QUERY_TYPE = "Query";

    private static final byte SYSTEM = 1;

    private static final String FIELD_POST_TYPE = "postType";
    private static final String FIELD_SITE_ID = "siteId";
    /** SH990 — the {@code state=} grammar, as a query argument. */
    private static final String FIELD_STATE = "state";
    private static final String FIELD_FOLDER_ID = "folderId";

    /** Common post fields shared by every content type, with their resolvers. */
    private static final Map<String, Function<ShCdaPost, Object>> COMMON_FIELDS = new LinkedHashMap<>();
    static {
        COMMON_FIELDS.put("id", ShCdaPost::id);
        COMMON_FIELDS.put("title", ShCdaPost::title);
        COMMON_FIELDS.put("summary", ShCdaPost::summary);
        COMMON_FIELDS.put("furl", ShCdaPost::furl);
        COMMON_FIELDS.put(FIELD_POST_TYPE, ShCdaPost::postType);
        COMMON_FIELDS.put(FIELD_SITE_ID, ShCdaPost::siteId);
        COMMON_FIELDS.put(FIELD_FOLDER_ID, ShCdaPost::folderId);
        COMMON_FIELDS.put("published", ShCdaPost::published);
        COMMON_FIELDS.put("date", post -> asText(post.date()));
        COMMON_FIELDS.put("modifiedDate", post -> asText(post.modifiedDate()));
        COMMON_FIELDS.put("publicationDate", post -> asText(post.publicationDate()));
    }

    private final ShPostTypeRepository shPostTypeRepository;
    private final ShCdaService shCdaService;

    public ShGraphQLSchema(ShPostTypeRepository shPostTypeRepository, ShCdaService shCdaService) {
        this.shPostTypeRepository = shPostTypeRepository;
        this.shCdaService = shCdaService;
    }

    @Transactional(readOnly = true)
    public GraphQLSchema buildSchema() {
        GraphQLInterfaceType contentInterface = buildContentInterface();
        GraphQLCodeRegistry.Builder codeRegistry = GraphQLCodeRegistry.newCodeRegistry();

        Set<GraphQLObjectType> objectTypes = new HashSet<>();
        // rawPostTypeName -> generated GraphQL type name (for the TypeResolver).
        Map<String, String> typeNameByPostType = new LinkedHashMap<>();
        Set<String> usedTypeNames = new HashSet<>();

        // Always present: the fallback type for system / unmapped post-types. Also
        // guarantees the interface has an implementor when no content type exists.
        objectTypes.add(buildObjectType(GENERIC_TYPE, List.of(), contentInterface, codeRegistry));
        usedTypeNames.add(GENERIC_TYPE);

        for (ShPostType postType : shPostTypeRepository.findAll()) {
            if (postType.getSystem() == SYSTEM || postType.getName() == null) {
                continue; // system types fall back to ShGenericPost
            }
            String typeName = unique(capitalize(sanitize(postType.getName(), "Type")), usedTypeNames);
            usedTypeNames.add(typeName);
            typeNameByPostType.put(postType.getName(), typeName);
            objectTypes.add(buildObjectType(typeName, attrFields(postType), contentInterface, codeRegistry));
        }

        codeRegistry.typeResolver(CONTENT_INTERFACE, typeResolver(typeNameByPostType));

        GraphQLObjectType queryType = buildQueryType(codeRegistry);

        return GraphQLSchema.newSchema()
                .query(queryType)
                .additionalType(contentInterface)
                .additionalTypes(new HashSet<>(objectTypes))
                .codeRegistry(codeRegistry.build())
                .build();
    }

    // --- schema pieces ---

    private GraphQLInterfaceType buildContentInterface() {
        GraphQLInterfaceType.Builder builder = GraphQLInterfaceType.newInterface().name(CONTENT_INTERFACE);
        COMMON_FIELDS.keySet().forEach(name ->
                builder.field(field -> field.name(name).type(commonFieldType(name))));
        return builder.build();
    }

    /**
     * @param attrFields ordered (label-stable) attr field definitions for this
     *                   type; empty for the generic fallback.
     */
    private GraphQLObjectType buildObjectType(String typeName,
                                              List<AttrField> attrFields,
                                              GraphQLInterfaceType contentInterface,
                                              GraphQLCodeRegistry.Builder codeRegistry) {
        GraphQLObjectType.Builder builder = GraphQLObjectType.newObject()
                .name(typeName)
                .withInterface(contentInterface);

        // Re-declare the interface fields (graphql-java requires implementors to
        // carry them) and wire each to its common resolver.
        COMMON_FIELDS.forEach((name, resolver) -> {
            builder.field(field -> field.name(name).type(commonFieldType(name)));
            codeRegistry.dataFetcher(FieldCoordinates.coordinates(typeName, name),
                    (DataFetcher<Object>) env -> resolver.apply(env.getSource()));
        });

        for (AttrField attr : attrFields) {
            builder.field(field -> field.name(attr.fieldName()).type(Scalars.GraphQLString));
            codeRegistry.dataFetcher(FieldCoordinates.coordinates(typeName, attr.fieldName()),
                    (DataFetcher<Object>) env -> attrValue(env.getSource(), attr.attrName()));
        }
        return builder.build();
    }

    private GraphQLObjectType buildQueryType(GraphQLCodeRegistry.Builder codeRegistry) {
        GraphQLTypeReference contentRef = new GraphQLTypeReference(CONTENT_INTERFACE);

        GraphQLFieldDefinition post = GraphQLFieldDefinition.newFieldDefinition()
                .name("post").type(contentRef)
                .argument(arg("id", new GraphQLNonNull(Scalars.GraphQLID)))
                // SH990 — the REST grammar, so a GraphQL front end previews what a REST one does.
                .argument(arg(FIELD_STATE, Scalars.GraphQLString))
                .build();
        GraphQLFieldDefinition postByUrl = GraphQLFieldDefinition.newFieldDefinition()
                .name("postByUrl").type(contentRef)
                .argument(arg(FIELD_SITE_ID, new GraphQLNonNull(Scalars.GraphQLString)))
                .argument(arg("url", new GraphQLNonNull(Scalars.GraphQLString)))
                .argument(arg(FIELD_STATE, Scalars.GraphQLString))
                .build();
        // Note: scalars / non-null-of-scalar are both GraphQLInputType and
        // GraphQLOutputType, so they are valid argument types.
        GraphQLFieldDefinition posts = GraphQLFieldDefinition.newFieldDefinition()
                .name("posts").type(new GraphQLNonNull(GraphQLList.list(new GraphQLNonNull(contentRef))))
                .argument(arg(FIELD_SITE_ID, Scalars.GraphQLString))
                .argument(arg(FIELD_FOLDER_ID, Scalars.GraphQLString))
                .argument(arg(FIELD_POST_TYPE, Scalars.GraphQLString))
                .argument(argDefault("page", Scalars.GraphQLInt, 0))
                .argument(argDefault("size", Scalars.GraphQLInt, 50))
                .argument(arg(FIELD_STATE, Scalars.GraphQLString))
                .build();

        codeRegistry.dataFetcher(FieldCoordinates.coordinates(QUERY_TYPE, "post"),
                (DataFetcher<Object>) env -> shCdaService.getPost(env.getArgument("id"),
                        env.getArgument(FIELD_STATE)).orElse(null));
        codeRegistry.dataFetcher(FieldCoordinates.coordinates(QUERY_TYPE, "postByUrl"),
                (DataFetcher<Object>) env -> shCdaService
                        .getPostByUrl(env.getArgument(FIELD_SITE_ID), env.getArgument("url"), null,
                                env.getArgument(FIELD_STATE)).orElse(null));
        codeRegistry.dataFetcher(FieldCoordinates.coordinates(QUERY_TYPE, "posts"),
                (DataFetcher<Object>) env -> shCdaService.queryPosts(
                        env.getArgument(FIELD_SITE_ID), env.getArgument(FIELD_FOLDER_ID), env.getArgument(FIELD_POST_TYPE),
                        intArg(env.getArgument("page"), 0), intArg(env.getArgument("size"), 50),
                        env.getArgument(FIELD_STATE)));

        return GraphQLObjectType.newObject().name(QUERY_TYPE)
                .field(post).field(postByUrl).field(posts).build();
    }

    private TypeResolver typeResolver(Map<String, String> typeNameByPostType) {
        return env -> {
            Object source = env.getObject();
            String typeName = GENERIC_TYPE;
            if (source instanceof ShCdaPost post && post.postType() != null) {
                typeName = typeNameByPostType.getOrDefault(post.postType(), GENERIC_TYPE);
            }
            return env.getSchema().getObjectType(typeName);
        };
    }

    // --- attr extraction ---

    private List<AttrField> attrFields(ShPostType postType) {
        Set<String> used = new HashSet<>(COMMON_FIELDS.keySet());
        return postType.getShPostTypeAttrs().stream()
                .filter(attr -> attr.getName() != null && !attr.getName().isBlank())
                // SH722 — the GraphQL schema's field order is published to every client, so an
                // unstable one changes an API contract between two reads of the same instance.
                .sorted(java.util.Comparator.comparingInt(ShPostTypeAttr::getOrdinal)
                        .thenComparing(attr -> attr.getName() == null ? "" : attr.getName()))
                .map(attr -> {
                    String fieldName = unique(decapitalize(sanitize(attr.getName(), "field")), used);
                    used.add(fieldName);
                    return new AttrField(fieldName, attr.getName());
                })
                .toList();
    }

    private static Object attrValue(ShCdaPost post, String attrName) {
        JsonNode attrs = post != null ? post.attrs() : null;
        JsonNode value = attrs != null ? attrs.get(attrName) : null;
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isValueNode() ? value.asString() : value.toString();
    }

    // --- helpers ---

    private static GraphQLOutputType commonFieldType(String name) {
        return switch (name) {
            case "id" -> new GraphQLNonNull(Scalars.GraphQLID);
            case "published" -> Scalars.GraphQLBoolean;
            default -> Scalars.GraphQLString;
        };
    }

    private static GraphQLArgument arg(String name, GraphQLOutputType type) {
        return GraphQLArgument.newArgument().name(name).type((graphql.schema.GraphQLInputType) type).build();
    }

    private static GraphQLArgument argDefault(String name, GraphQLOutputType type, Object defaultValue) {
        return GraphQLArgument.newArgument().name(name)
                .type((graphql.schema.GraphQLInputType) type)
                .defaultValueProgrammatic(defaultValue).build();
    }

    private static int intArg(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static String asText(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    /** Coerce an arbitrary name to the GraphQL name grammar {@code [_A-Za-z][_0-9A-Za-z]*}. */
    private static String sanitize(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            sb.append(Character.isLetterOrDigit(c) || c == '_' ? c : '_');
        }
        String s = sb.toString();
        if (s.isEmpty()) {
            return fallback;
        }
        char first = s.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            s = "_" + s;
        }
        return s;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String decapitalize(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String unique(String candidate, Set<String> used) {
        if (!used.contains(candidate)) {
            return candidate;
        }
        int i = 2;
        while (used.contains(candidate + i)) {
            i++;
        }
        return candidate + i;
    }

    /** A generated object-type field ({@code fieldName}) backed by an attr ({@code attrName}). */
    private record AttrField(String fieldName, String attrName) {
    }
}
