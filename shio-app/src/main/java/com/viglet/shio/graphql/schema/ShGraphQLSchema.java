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
package com.viglet.shio.graphql.schema;

import com.viglet.shio.graphql.ShGraphQLConstants;
import com.viglet.shio.graphql.schema.object.type.ShGraphQLOTPostType;
import com.viglet.shio.graphql.schema.object.type.sites.ShGraphQLOTNavigation;
import com.viglet.shio.graphql.schema.object.type.sites.ShGraphQLOTObjectFromURL;
import com.viglet.shio.graphql.schema.object.type.sites.ShGraphQLOTObjectURL;
import com.viglet.shio.graphql.schema.object.type.sites.ShGraphQLOTQuery;
import com.viglet.shio.graphql.schema.query.type.ShGraphQLQTCommons;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;

import graphql.GraphQL;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLObjectType.Builder;
import graphql.schema.GraphQLSchema;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphQLCodeRegistry.newCodeRegistry;
import static graphql.schema.GraphqlTypeComparatorRegistry.BY_NAME_REGISTRY;

/**
 * GraphQL Schema.
 *
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
@Component
public class ShGraphQLSchema {

	private final ShPostTypeRepository shPostTypeRepository;
	private final ShGraphQLOTPostType shGraphQLOTPostType;
	private final ShGraphQLOTObjectFromURL shGraphQLOTObjectFromURL;
	private final ShGraphQLOTNavigation shGraphQLOTNavigation;
	private final ShGraphQLOTQuery shGraphQLOTQuery;
	private final ShGraphQLOTObjectURL shGraphQLOTObjectURL;
	private final ShGraphQLQTCommons shGraphQLQTCommons;

	private GraphQL graphQL;

	@Autowired
	public ShGraphQLSchema(ShPostTypeRepository shPostTypeRepository, ShGraphQLOTPostType shGraphQLOTPostType,
						   ShGraphQLOTObjectFromURL shGraphQLOTObjectFromURL,
						   ShGraphQLOTNavigation shGraphQLOTNavigation, ShGraphQLOTQuery shGraphQLOTQuery,
						   ShGraphQLOTObjectURL shGraphQLOTObjectURL, ShGraphQLQTCommons shGraphQLQTCommons) {
		this.shPostTypeRepository = shPostTypeRepository;
		this.shGraphQLOTPostType = shGraphQLOTPostType;
		this.shGraphQLOTObjectFromURL = shGraphQLOTObjectFromURL;
		this.shGraphQLOTNavigation = shGraphQLOTNavigation;
		this.shGraphQLOTQuery = shGraphQLOTQuery;
		this.shGraphQLOTObjectURL = shGraphQLOTObjectURL;
		this.shGraphQLQTCommons = shGraphQLQTCommons;
	}

	private GraphQLSchema loadSchema() {
		Builder queryTypeBuilder = newObject().name(ShGraphQLConstants.QUERY_TYPE);
		graphql.schema.GraphQLCodeRegistry.Builder codeRegistryBuilder = newCodeRegistry();
		for (ShPostType shPostType : shPostTypeRepository.findAll())
			shGraphQLOTPostType.createObjectTypes(queryTypeBuilder, codeRegistryBuilder, shPostType);

		shGraphQLOTObjectFromURL.createObjectType(queryTypeBuilder, codeRegistryBuilder);

		shGraphQLOTNavigation.createObjectType(queryTypeBuilder, codeRegistryBuilder);

		shGraphQLOTObjectURL.createObjectType(queryTypeBuilder, codeRegistryBuilder);

		shGraphQLOTQuery.createObjectType(queryTypeBuilder, codeRegistryBuilder);

		GraphQLObjectType queryType = queryTypeBuilder.comparatorRegistry(BY_NAME_REGISTRY).build();

		return GraphQLSchema.newSchema().additionalType(shGraphQLQTCommons.createSiteEnum()).query(queryType)
				.codeRegistry(codeRegistryBuilder.build()).build();

	}

	@PostConstruct
	public void init() {
		GraphQLSchema graphQLSchema = this.loadSchema();
		this.graphQL = GraphQL.newGraphQL(graphQLSchema).build();
	}

	@Bean
	public GraphQL graphQL() {
		return graphQL;
	}

}
