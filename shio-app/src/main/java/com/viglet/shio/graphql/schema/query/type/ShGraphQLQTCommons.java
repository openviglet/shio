/*
 * Copyright (C) 2016-2021 the original author or authors. 
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
package com.viglet.shio.graphql.schema.query.type;

import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLEnumType.newEnum;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLList.list;
import static graphql.schema.GraphQLNonNull.nonNull;
import static graphql.schema.GraphqlTypeComparatorRegistry.BY_NAME_REGISTRY;
import static graphql.language.EnumValue.newEnumValue;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.graphql.ShGraphQLConstants;
import com.viglet.shio.graphql.ShGraphQLUtils;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLObjectType.Builder;

/**
 * GraphQL Query Type Commons.
 *
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
@Component
public class ShGraphQLQTCommons {
	private final ShSiteRepository shSiteRepository;
	private final ShGraphQLUtils shGraphQLUtils;

	@Autowired
	public ShGraphQLQTCommons(ShSiteRepository shSiteRepository, ShGraphQLUtils shGraphQLUtils) {
		this.shSiteRepository = shSiteRepository;
		this.shGraphQLUtils = shGraphQLUtils;
	}

	public void createArguments(Builder queryTypeBuilder, GraphQLObjectType graphQLObjectType, String postTypeName,
			GraphQLInputObjectType.Builder postTypeWhereInputBuilder, boolean isPlural) {
		if (!StringUtils.isBlank(postTypeName)) {
			GraphQLTypeReference siteArgRef = GraphQLTypeReference.typeRef(ShGraphQLConstants.SITES_ARG_TITLE);

			GraphQLInputObjectType postTypeWhereInput = postTypeWhereInputBuilder.comparatorRegistry(BY_NAME_REGISTRY)
					.build();
			
			queryTypeBuilder.field(newFieldDefinition().name(postTypeName)
					.type(nonNull(isPlural ? list(nonNull(graphQLObjectType)) : graphQLObjectType))
					.argument(newArgument().name(ShGraphQLConstants.STAGE_ARG)
							.description(
									"A required enumeration indicating the current content Stage (defaults to DRAFT)")
							.type(nonNull(ShGraphQLConstants.stageEnum)).defaultValueLiteral(newEnumValue("DRAFT").build()))
					.argument(newArgument().name(ShGraphQLConstants.LOCALES_ARG)
							.description("A required array of one or more locales, defaults to the project's default.")
							.type(nonNull(list(ShGraphQLConstants.localeEnum))).defaultValueLiteral(newEnumValue("en").build()))
					.argument(newArgument().name(ShGraphQLConstants.SITES_ARG)
							.description("A required array of one or more sites").type(list(siteArgRef)))
					.argument(newArgument().name(ShGraphQLConstants.WHERE_ARG)
							.description(
									"An optional object type to filter the content based on a nested set of criteria.")
							.type(postTypeWhereInput)));
		}
	}

	public GraphQLEnumType createSiteEnum() {
		final graphql.schema.GraphQLEnumType.Builder siteEnumBuilder = newEnum()
				.name(ShGraphQLConstants.SITES_ARG_TITLE).description("Site Names enumeration");

		siteEnumBuilder.value("All", "all", "Entire sites");

		shSiteRepository.findAll()
				.forEach(shSite -> siteEnumBuilder.value(shGraphQLUtils.normalizedName(shSite.getName()), shSite.getId(), shSite.getDescription()));

		return siteEnumBuilder.comparatorRegistry(BY_NAME_REGISTRY).build();
	}
}
