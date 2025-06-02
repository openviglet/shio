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
package com.viglet.shio.graphql.schema.object.type.sites;

import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphqlTypeComparatorRegistry.BY_NAME_REGISTRY;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.graphql.schema.query.type.sites.ShGraphQLQTObjectURL;

import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLObjectType.Builder;

/**
 * GraphQL Object Link Object Type.
 *
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
@Component
public class ShGraphQLOTObjectURL {

	private final ShGraphQLQTObjectURL shGraphQLQTObjectURL;

	@Autowired
	public ShGraphQLOTObjectURL(ShGraphQLQTObjectURL shGraphQLQTObjectURL) {
		this.shGraphQLQTObjectURL = shGraphQLQTObjectURL;
	}

	private GraphQLObjectType createWebSiteByURL() {
		Builder builder = newObject().name("ShObjectURL").description("Object URL");

		this.createSiteURLObjectTypeFields(builder);

		return builder.comparatorRegistry(BY_NAME_REGISTRY).build();
	}

	private void createSiteURLObjectTypeFields(Builder builder) {		
		builder.field(newFieldDefinition().name("url").description("url").type(GraphQLString));

	}

	public void createObjectType(Builder queryTypeBuilder,
			graphql.schema.GraphQLCodeRegistry.Builder codeRegistryBuilder) {

		GraphQLObjectType graphQLObjectType = this.createWebSiteByURL();

		shGraphQLQTObjectURL.createQueryType(queryTypeBuilder, codeRegistryBuilder, graphQLObjectType);
	}

}
