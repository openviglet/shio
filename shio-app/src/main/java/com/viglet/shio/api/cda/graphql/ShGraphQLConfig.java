/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda.graphql;

import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Replaces the placeholder {@code graphql/schema.graphqls} with the schema that
 * {@link ShGraphQLSchema} generates from the content post-types (SH4).
 *
 * <p>Spring for GraphQL still requires a schema resource on the classpath (the
 * placeholder), but {@code schemaFactory} ignores the parsed SDL and the
 * default runtime wiring and substitutes our fully programmatic schema.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Configuration(proxyBeanMethods = false)
public class ShGraphQLConfig {

    @Bean
    GraphQlSourceBuilderCustomizer shCdaGraphQlSchemaCustomizer(ShGraphQLSchema shGraphQLSchema) {
        return builder -> builder.schemaFactory((typeDefinitionRegistry, runtimeWiring) ->
                shGraphQLSchema.buildSchema());
    }
}
