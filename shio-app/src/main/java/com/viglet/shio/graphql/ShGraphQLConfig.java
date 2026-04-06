/*
 * Copyright (C) 2016-2020 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.shio.graphql;

import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.viglet.shio.graphql.schema.ShGraphQLSchema;

/**
 * GraphQL Configuration for Spring for GraphQL.
 * Replaces the old graphql-java-spring-boot-starter endpoint and invocation classes.
 *
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
@Configuration
public class ShGraphQLConfig {

	@Bean
	GraphQlSourceBuilderCustomizer graphQlSourceBuilderCustomizer(ShGraphQLSchema shGraphQLSchema) {
		return builder -> builder
				.schemaFactory((typeDefinitionRegistry, runtimeWiring) -> shGraphQLSchema.buildSchema());
	}
}
