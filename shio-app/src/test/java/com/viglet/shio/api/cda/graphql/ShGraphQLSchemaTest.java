/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.cda.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
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
                "siteA", "folderA", true, null, null, null, "en", java.util.List.of(), node,
                null);
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

    /** SH1016 - a post-type whose one field holds blocks. */
    private ShPostType blocksPostType(String name) {
        ShPostType pt = new ShPostType();
        pt.setName(name);
        pt.setSystem((byte) 0);
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName("body");
        attr.setOrdinal(0);
        com.viglet.shio.persistence.model.widget.ShWidget widget =
                new com.viglet.shio.persistence.model.widget.ShWidget();
        widget.setName(com.viglet.shio.widget.ShSystemWidget.BLOCKS);
        attr.setShWidget(widget);
        pt.setShPostTypeAttrs(new java.util.HashSet<>(java.util.Set.of(attr)));
        return pt;
    }

    private ShCdaPost postWithBlocks(String id, String postType, String blocksJson) {
        ObjectNode node = MAPPER.createObjectNode();
        node.set("body", MAPPER.readTree(blocksJson));
        return new ShCdaPost(id, "Hello", null, "/news/hello", postType,
                "siteA", "folderA", true, null, null, null, "en", java.util.List.of(), node,
                java.util.Map.of("body", "<h2>Why</h2>"));
    }

    @Test
    @DisplayName("SH1016: a blocks field is a union a client narrows, not a string it parses")
    void aBlocksFieldResolvesThroughTheUnion() {
        when(shPostTypeRepository.findAll()).thenReturn(List.of(blocksPostType("Doc")));
        when(shCdaService.getPost("p1", null)).thenReturn(Optional.of(postWithBlocks("p1", "Doc",
                "[{\"key\":\"b1\",\"type\":\"heading\",\"level\":2,\"text\":\"Why\"},"
                        + "{\"key\":\"b2\",\"type\":\"list\",\"ordered\":false,"
                        + "\"items\":[\"one\"]}]")));

        GraphQL graphQL = GraphQL.newGraphQL(schema().buildSchema()).build();
        ExecutionResult result = graphQL.execute("""
                { post(id: "p1") { ... on Doc { body {
                    __typename
                    ... on ShBlockHeading { key level text }
                    ... on ShBlockList { ordered items }
                } } } }""");

        assertThat(result.getErrors())
                .as("SH1016: a blocks field typed as String is what made a headless site parse "
                        + "JSON out of a scalar to find out whether it had a quote or an image")
                .isEmpty();
        Map<String, Object> data = result.getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> postData = (Map<String, Object>) data.get("post");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) postData.get("body");

        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0))
                .as("SH1016: the union narrows on __typename, which is what gives a client the "
                        + "same switch TypeScript gets from the discriminated union")
                .containsEntry("__typename", "ShBlockHeading")
                .containsEntry("key", "b1")
                .containsEntry("level", 2)
                .containsEntry("text", "Why");
        assertThat(blocks.get(1))
                .containsEntry("__typename", "ShBlockList")
                .containsEntry("ordered", false)
                .containsEntry("items", List.of("one"));
    }

    @Test
    @DisplayName("SH1016: the block vocabulary is in the schema whatever content exists")
    void theUnionIsDeclaredEvenWithNoBlocksField() {
        when(shPostTypeRepository.findAll())
                .thenReturn(List.of(postType("News", (byte) 0, "headline")));

        graphql.schema.GraphQLSchema built = schema().buildSchema();

        assertThat(built.getType("ShBlock"))
                .as("""
                        SH1016: building the vocabulary only when some post-type carries a Blocks \
                        field would make the published schema depend on what content happens to \
                        exist, and a schema that changes shape between two instances is not a \
                        contract a client can be generated from.""")
                .isNotNull();
        for (String kind : com.viglet.shio.post.block.ShBlocks.TYPES) {
            String name = "ShBlock" + Character.toUpperCase(kind.charAt(0)) + kind.substring(1);
            assertThat(built.getType(name))
                    .as("SH1016: %s is a registered block type and has no GraphQL type", kind)
                    .isNotNull();
        }
    }

    @Test
    void executesQueryResolvingAttrFieldOnGeneratedType() {
        when(shPostTypeRepository.findAll())
                .thenReturn(List.of(postType("News", (byte) 0, "headline")));
        when(shCdaService.getPost("p1", null))
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
        when(shCdaService.getPost("p2", null))
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
        when(shCdaService.getPost("p3", null))
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
        lenient().when(shCdaService.queryPosts("siteA", null, null, 0, 50, null))
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

    @Test
    void sh990StateReachesTheServiceFromEveryQueryField() {
        // SH990 — the REST grammar, as an argument: a GraphQL front end previews what a REST one does,
        // and the service is where a production token's draft is refused, so passing it through is the
        // schema's whole job here.
        when(shPostTypeRepository.findAll())
                .thenReturn(List.of(postType("News", (byte) 0, "headline")));
        when(shCdaService.getPost("p9", "rev:2"))
                .thenReturn(Optional.of(post("p9", "As it was", "News", Map.of("headline", "then"))));
        when(shCdaService.getPostByUrl("siteA", "/news/hello", null, "published"))
                .thenReturn(Optional.of(post("p9", "Live", "News", Map.of("headline", "now"))));
        when(shCdaService.queryPosts("siteA", null, null, 0, 50, "draft"))
                .thenReturn(List.of(post("p9", "Drafted", "News", Map.of("headline", "soon"))));

        GraphQL graphQL = GraphQL.newGraphQL(schema().buildSchema()).build();
        ExecutionResult result = graphQL.execute("{ "
                + "a: post(id: \"p9\", state: \"rev:2\") { title } "
                + "b: postByUrl(siteId: \"siteA\", url: \"/news/hello\", state: \"published\") { title } "
                + "c: posts(siteId: \"siteA\", state: \"draft\") { title } }");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = result.getData();
        assertThat(data.get("a")).isEqualTo(Map.of("title", "As it was"));
        assertThat(data.get("b")).isEqualTo(Map.of("title", "Live"));
        assertThat(data.get("c")).isEqualTo(List.of(Map.of("title", "Drafted")));
    }
}
