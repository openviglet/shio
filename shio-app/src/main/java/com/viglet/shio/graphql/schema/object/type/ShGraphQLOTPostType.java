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
package com.viglet.shio.graphql.schema.object.type;

import static graphql.Scalars.GraphQLID;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphqlTypeComparatorRegistry.BY_NAME_REGISTRY;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.graphql.ShGraphQLConstants;
import com.viglet.shio.graphql.ShGraphQLUtils;
import com.viglet.shio.graphql.schema.query.type.ShGraphQLQTPlural;
import com.viglet.shio.graphql.schema.query.type.ShGraphQLQTUnique;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;

import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLObjectType.Builder;

/**
 * GraphQL Object Type.
 *
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
@Component
public class ShGraphQLOTPostType {

	private final ShGraphQLUtils shGraphQLUtils;
	private final ShGraphQLQTUnique shGraphQLQTUnique;
	private final ShGraphQLQTPlural shGraphQLQTPlural;

	@Autowired
	public ShGraphQLOTPostType(ShGraphQLUtils shGraphQLUtils, ShGraphQLQTUnique shGraphQLQTUnique, ShGraphQLQTPlural shGraphQLQTPlural) {
		this.shGraphQLUtils = shGraphQLUtils;
		this.shGraphQLQTUnique = shGraphQLQTUnique;
		this.shGraphQLQTPlural = shGraphQLQTPlural;
	}

	private GraphQLObjectType createObjectType(ShPostType shPostType) {
		Builder builder = newObject().name(shPostType.getName().replace("-", "_")).description(shPostType.getDescription());

		this.createObjectTypeFields(shPostType, builder);

		return builder.comparatorRegistry(BY_NAME_REGISTRY).build();
	}

	private void createObjectTypeFields(ShPostType shPostType, Builder builder) {
		builder.field(newFieldDefinition().name(ShGraphQLConstants.ID).description("Identifier").type(GraphQLID));
		builder.field(newFieldDefinition().name(ShGraphQLConstants.TITLE).description("System Title").type(GraphQLString));
		builder.field(newFieldDefinition().name(ShGraphQLConstants.DESCRIPTION).description("System Description")
				.type(GraphQLString));
		builder.field(
				newFieldDefinition().name(ShGraphQLConstants.FURL).description("Friendly URL").type(GraphQLString));
		builder.field(
				newFieldDefinition().name(ShGraphQLConstants.MODIFIER).description("Modifier").type(GraphQLString));
		builder.field(
				newFieldDefinition().name(ShGraphQLConstants.PUBLISHER).description("Publisher").type(GraphQLString));
		builder.field(
				newFieldDefinition().name(ShGraphQLConstants.FOLDER).description("Folder Name").type(GraphQLString));
		builder.field(
				newFieldDefinition().name(ShGraphQLConstants.SITE).description("Site Name").type(GraphQLString));
		
		for (ShPostTypeAttr shPostTypeAttr : shPostType.getShPostTypeAttrs()) {
			String postTypeAttrName = shGraphQLUtils.normalizedField(shPostTypeAttr.getName());
			builder.field(newFieldDefinition().name(postTypeAttrName).description(shPostTypeAttr.getDescription())
					.type(GraphQLString));
		}
	}
	
	public void createObjectTypes(Builder queryTypeBuilder,
			graphql.schema.GraphQLCodeRegistry.Builder codeRegistryBuilder, ShPostType shPostType) {

		GraphQLObjectType graphQLObjectType = this.createObjectType(shPostType);
		
		shGraphQLQTUnique.createQueryTypeUnique(queryTypeBuilder, codeRegistryBuilder, shPostType, graphQLObjectType);

		shGraphQLQTPlural.createQueryTypePlural(queryTypeBuilder, codeRegistryBuilder, shPostType, graphQLObjectType);

	}

}
