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

import static graphql.Scalars.GraphQLID;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphqlTypeComparatorRegistry.BY_NAME_REGISTRY;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.graphql.ShGraphQLConstants;
import com.viglet.shio.graphql.schema.query.type.sites.ShGraphQLQTObjectFromURL;

import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLObjectType.Builder;
import graphql.scalars.ExtendedScalars;
/**
 * GraphQL Object From URL Object Type.
 *
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
@Component
public class ShGraphQLOTObjectFromURL {

	private final ShGraphQLQTObjectFromURL shGraphQLQTObjectFromURL;

	@Autowired
	public ShGraphQLOTObjectFromURL(ShGraphQLQTObjectFromURL shGraphQLQTObjectFromURL) {
		this.shGraphQLQTObjectFromURL = shGraphQLQTObjectFromURL;
	}


	private GraphQLObjectType createWebSiteByURL() {
		Builder builder = newObject().name("ObjectFromURL").description("Object from Site URL");

		this.createSiteURLObjectTypeFields(builder);

		return builder.comparatorRegistry(BY_NAME_REGISTRY).build();
	}
	
	private void createSiteURLObjectTypeFields(Builder builder) {
		builder.field(newFieldDefinition().name(ShGraphQLConstants.ID).description("Identifier").type(GraphQLID));
		builder.field(
				newFieldDefinition().name("type").description("Object Type").type(GraphQLString));
		builder.field(
				newFieldDefinition().name("format").description("Format").type(GraphQLString));
		builder.field(
				newFieldDefinition().name("locale").description("Locale").type(GraphQLString));
		builder.field(
				newFieldDefinition().name("siteId").description("Site Id").type(GraphQLString));
		builder.field(
				newFieldDefinition().name("siteName").description("Site Name").type(GraphQLString));
		builder.field(
				newFieldDefinition().name("context").description("Context").type(GraphQLString));	
		builder.field(
				newFieldDefinition().name("content").description("Content").type(ExtendedScalars.Object));	
		builder.field(
				newFieldDefinition().name("pageLayout").description("Page Layout Name").type(GraphQLString));	
		

	}

	public void createObjectType(Builder queryTypeBuilder,
			graphql.schema.GraphQLCodeRegistry.Builder codeRegistryBuilder) {
		
		GraphQLObjectType graphQLObjectType = this.createWebSiteByURL();

		shGraphQLQTObjectFromURL.createQueryType(queryTypeBuilder, codeRegistryBuilder, graphQLObjectType);
	}

}
