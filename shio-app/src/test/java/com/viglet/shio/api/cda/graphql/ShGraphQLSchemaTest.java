/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.api.cda.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.shio.api.cda.ShCdaService;
import com.viglet.shio.api.cda.dto.ShCdaPost;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Unit coverage for the SH4 programmatic GraphQL delivery schema
 * ({@link ShGraphQLSchema}): generated types + interface, name sanitizing, the
 * always-present generic fallback, and query execution (attr extraction + type
 * resolution) against a stubbed {@link ShCdaService}.
 *
 * @author Alexandre Oliveira
 */
@ExtendWith(MockitoExtension.class)
class ShGraphQLSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private ShPostTypeRepository shPostTypeRepository;
    @Mock
    private ShCdaService shCdaService;

    private ShGraphQLSchema schema() {
        return new ShGraphQLSchema(shPostTypeRepository, shCdaService);
    }

    private ShPostType postType(String name, byte system, String... attrNames) {
        ShPostType pt = new ShPostType();
        pt.setName(name);
        pt.setSystem(system);
        int ordinal = 0;
        java.util.Set<ShPostTypeAttr> attrs = new java.util.HashSet<>();
        for (String attrName : attrNames) {
            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setName(attrName);
            attr.setOrdinal(ordinal++);
            attrs.add(attr);
        }
        pt.setShPostTypeAttrs(attrs);
        return pt;
    }

    private ShCdaPost post(String id, String title, String postType, Map<String, String> attrs) {
        ObjectNode node = MAPPER.createObjectNode();
        attrs.forEach(node::put);
        return new ShCdaPost(id, title, null, "/news/hello", postType,
                "siteA", "folderA", true, null, null, null, node);
    }

    @Test
    void buildsInterfaceWithGeneratedTypeAndAttrField() {
        when(shPostTypeRepository.findAll())
                .thenReturn(List.of(postType("News", (byte) 0, "headline", "body")));

        GraphQLSchema gql = schema().buildSchema();

        assertThat(gql.getType(ShGraphQLSchema.CONTENT_INTERFACE)).isInstanceOf(GraphQLInterfaceType.class);
        GraphQLObjectType news = gql.getObjectType("News");
        assertThat(news).isNotNull();
        assertThat(news.getFieldDefinition("headline")).isNotNull();
        assertThat(news.getFieldDefinition("body")).isNotNull();
        assertThat(news.getFieldDefinition("title")).isNotNull(); // inherited common field
        assertThat(gql.getObjectType(ShGraphQLSchema.GENERIC_TYPE)).isNotNull();

        GraphQLObjectType query = gql.getQueryType();
        assertThat(query.getFieldDefinition("post")).isNotNull();
        assertThat(query.getFieldDefinition("postByUrl")).isNotNull();
        assertThat(query.getFieldDefinition("posts")).isNotNull();
    }

    @Test
    void skipsSystemTypesAndSanitizesNames() {
        when(shPostTypeRepository.findAll()).thenReturn(List.of(
                postType("Blog Post", (byte) 0, "Sub Title"),
                postType("Folder", (byte) 1))); // system → no dedicated type

        GraphQLSchema gql = schema().buildSchema();

        assertThat(gql.getObjectType("Folder")).isNull();
        // Spaces are not valid in GraphQL names → coerced to '_' (spec §3).
        GraphQLObjectType blog = gql.getObjectType("Blog_Post");
        assertThat(blog).isNotNull();
        assertThat(blog.getFieldDefinition("sub_Title")).isNotNull(); // "Sub Title" → sub_Title
    }

    @Test
    void emptyPostTypesStillBuildsWithGenericFallback() {
        when(shPostTypeRepository.findAll()).thenReturn(List.of());

        GraphQLSchema gql = schema().buildSchema();

        assertThat(gql.getObjectType(ShGraphQLSchema.GENERIC_TYPE)).isNotNull();
        assertThat(gql.getQueryType().getFieldDefinition("posts")).isNotNull();
    }

    @Test
    void executesQueryResolvingAttrFieldOnGeneratedType() {
        when(shPostTypeRepository.findAll())
                .thenReturn(List.of(postType("News", (byte) 0, "headline")));
        when(shCdaService.getPost("p1"))
                .thenReturn(Optional.of(post("p1", "Hello", "News", Map.of("headline", "Big news"))));

        GraphQL graphQL = GraphQL.newGraphQL(schema().buildSchema()).build();
        ExecutionResult result = graphQL.execute(
                "{ post(id: \"p1\") { title ... on News { headline } } }");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> postData = (Map<String, Object>) data.get("post");
        assertThat(postData).containsEntry("title", "Hello").containsEntry("headline", "Big news");
    }

    @Test
    void unknownPostTypeResolvesToGenericFallback() {
        when(shPostTypeRepository.findAll())
                .thenReturn(List.of(postType("News", (byte) 0, "headline")));
        // A post whose type has no generated object type (e.g. a system type post).
        when(shCdaService.getPost("p2"))
                .thenReturn(Optional.of(post("p2", "Generic", "Folder", Map.of())));

        GraphQL graphQL = GraphQL.newGraphQL(schema().buildSchema()).build();
        ExecutionResult result = graphQL.execute("{ post(id: \"p2\") { __typename title } }");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> postData = (Map<String, Object>) data.get("post");
        assertThat(postData).containsEntry("__typename", ShGraphQLSchema.GENERIC_TYPE)
                .containsEntry("title", "Generic");
    }

    @Test
    void missingAttrResolvesToNullNotError() {
        when(shPostTypeRepository.findAll())
                .thenReturn(List.of(postType("News", (byte) 0, "headline")));
        when(shCdaService.getPost("p3"))
                .thenReturn(Optional.of(post("p3", "No attrs", "News", Map.of())));

        GraphQL graphQL = GraphQL.newGraphQL(schema().buildSchema()).build();
        ExecutionResult result = graphQL.execute("{ post(id: \"p3\") { ... on News { headline } } }");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> postData = (Map<String, Object>) data.get("post");
        assertThat(postData).containsKey("headline");
        assertThat(postData.get("headline")).isNull();
    }

    @Test
    void postsQueryDelegatesToServiceWithDefaults() {
        when(shPostTypeRepository.findAll())
                .thenReturn(List.of(postType("News", (byte) 0, "headline")));
        lenient().when(shCdaService.queryPosts("siteA", null, null, 0, 50))
                .thenReturn(List.of(post("p1", "A", "News", Map.of("headline", "h"))));

        GraphQL graphQL = GraphQL.newGraphQL(schema().buildSchema()).build();
        ExecutionResult result = graphQL.execute(
                "{ posts(siteId: \"siteA\") { title ... on News { headline } } }");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> posts = (List<Map<String, Object>>) data.get("posts");
        assertThat(posts).hasSize(1);
        assertThat(posts.get(0)).containsEntry("headline", "h");
    }
}
