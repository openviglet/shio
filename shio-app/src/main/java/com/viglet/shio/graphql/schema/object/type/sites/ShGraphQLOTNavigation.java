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

import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphqlTypeComparatorRegistry.BY_NAME_REGISTRY;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.graphql.schema.query.type.sites.ShGraphQLQTNavigation;

import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLObjectType.Builder;
import graphql.scalars.ExtendedScalars;

/**
 * GraphQL Navigation Object Type.
 *
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
@Component
public class ShGraphQLOTNavigation {

	private final ShGraphQLQTNavigation shGraphQLQTNavigation;

	@Autowired
	public ShGraphQLOTNavigation(ShGraphQLQTNavigation shGraphQLQTNavigation) {
		this.shGraphQLQTNavigation = shGraphQLQTNavigation;
	}

	private GraphQLObjectType createWebSiteByURL() {
		Builder builder = newObject().name("ShNavigation").description("Navigation Component");

		this.createSiteURLObjectTypeFields(builder);

		return builder.comparatorRegistry(BY_NAME_REGISTRY).build();
	}

	private void createSiteURLObjectTypeFields(Builder builder) {		
		builder.field(newFieldDefinition().name("folders").description("Folders").type(ExtendedScalars.Object));

	}

	public void createObjectType(Builder queryTypeBuilder,
			graphql.schema.GraphQLCodeRegistry.Builder codeRegistryBuilder) {

		GraphQLObjectType graphQLObjectType = this.createWebSiteByURL();

		shGraphQLQTNavigation.createQueryType(queryTypeBuilder, codeRegistryBuilder, graphQLObjectType);
	}

}
