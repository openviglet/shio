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
package com.viglet.shio.graphql.schema.query.type.sites;

import static graphql.Scalars.GraphQLID;
import static graphql.Scalars.GraphQLInt;
import static graphql.schema.FieldCoordinates.coordinates;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLNonNull.nonNull;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.graphql.ShGraphQLConstants;
import com.viglet.shio.website.utils.ShSitesObjectUtils;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLObjectType.Builder;

/**
 * GraphQL Navigation Query Type.
 *
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
@Component
public class ShGraphQLQTObjectURL {

	private final ShSitesObjectUtils shSitesObjectUtils;
	
	public static final String OBJECT_ID = "objectId";
	public static final String SCALE = "scale";
	
	private static final String QUERY_TYPE_NAME = "shObjectURL";

	@Autowired
	public ShGraphQLQTObjectURL(ShSitesObjectUtils shSitesObjectUtils) {
		this.shSitesObjectUtils = shSitesObjectUtils;
	}

	public void createQueryType(Builder queryTypeBuilder,
			graphql.schema.GraphQLCodeRegistry.Builder codeRegistryBuilder, GraphQLObjectType graphQLObjectType) {

		this.createArguments(queryTypeBuilder, graphQLObjectType);

		codeRegistryBuilder.dataFetcher(coordinates(ShGraphQLConstants.QUERY_TYPE, QUERY_TYPE_NAME),
				this.getDataFetcher());
	}

	private void createArguments(Builder queryTypeBuilder, GraphQLObjectType graphQLObjectType) {

		queryTypeBuilder.field(newFieldDefinition().name(QUERY_TYPE_NAME).type(graphQLObjectType)
				.argument(newArgument().name(OBJECT_ID).description("Object ID").type(nonNull(GraphQLID)))
				.argument(newArgument().name(SCALE).description("Scale of Image").type(GraphQLInt)));

	}

	private DataFetcher<Map<String, Object>> getDataFetcher() {
		return dataFetchingEnvironment -> {
			Map<String, Object> result = new HashMap<>();
			String objectId = dataFetchingEnvironment.getArgument(OBJECT_ID);
			Integer scale = dataFetchingEnvironment.getArgument(SCALE);
			if (scale != null) {
				result.put("url", shSitesObjectUtils.generateImageLinkById(objectId, scale));
			} else {
				result.put("url", shSitesObjectUtils.generateObjectLinkById(objectId));
			}
			return result;
		};
	}
}
