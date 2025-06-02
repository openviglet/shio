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
package com.viglet.shio.graphql.schema.query.type;

import static graphql.Scalars.GraphQLID;
import static graphql.schema.FieldCoordinates.coordinates;
import static graphql.schema.GraphQLInputObjectType.newInputObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.graphql.ShGraphQLConstants;
import com.viglet.shio.graphql.ShGraphQLUtils;
import com.viglet.shio.graphql.schema.ShGraphQLInputObjectField;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLObjectType.Builder;

/**
 * GraphQL Query Type Unique.
 *
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
@Component
public class ShGraphQLQTUnique {

	private final ShObjectRepository shObjectRepository;
	private final ShGraphQLQTCommons shGraphQLQTCommons;
	private final ShGraphQLUtils shGraphQLUtils;
	private final ShGraphQLInputObjectField shGraphQLInputObjectField;

	@Autowired
	public ShGraphQLQTUnique(ShObjectRepository shObjectRepository, ShGraphQLQTCommons shGraphQLQTCommons,
							 ShGraphQLUtils shGraphQLUtils, ShGraphQLInputObjectField shGraphQLInputObjectField) {
		this.shObjectRepository = shObjectRepository;
		this.shGraphQLQTCommons = shGraphQLQTCommons;
		this.shGraphQLUtils = shGraphQLUtils;
		this.shGraphQLInputObjectField = shGraphQLInputObjectField;
	}

	private String getPostTypeNameUnique(ShPostType shPostType) {
		return shGraphQLUtils.normalizedName(shPostType.getName());
	}

	public void createQueryTypeUnique(Builder queryTypeBuilder,
			graphql.schema.GraphQLCodeRegistry.Builder codeRegistryBuilder, ShPostType shPostType,
			GraphQLObjectType graphQLObjectType) {
		String postTypeName = this.getPostTypeNameUnique(shPostType);

		GraphQLInputObjectType.Builder postTypeWhereUniqueInputBuilder = newInputObject()
				.name(shPostType.getName().replace("-", "_").concat(ShGraphQLConstants.WHERE_UNIQUE_INPUT))
				.description(String.format("References %s record uniquely", shPostType.getName().replace("-", "_")));

		this.whereFieldsUnique(postTypeWhereUniqueInputBuilder);

		shGraphQLQTCommons.createArguments(queryTypeBuilder, graphQLObjectType, postTypeName,
				postTypeWhereUniqueInputBuilder, false);

		codeRegistryBuilder.dataFetcher(coordinates(ShGraphQLConstants.QUERY_TYPE, postTypeName),
				this.getPostTypeAllDataFetcherUnique());
	}

	private void whereFieldsUnique(GraphQLInputObjectType.Builder postTypeWhereInputBuilder) {
		shGraphQLInputObjectField.createInputObjectFieldCondition(postTypeWhereInputBuilder, ShGraphQLConstants.ID,
				null, GraphQLID, "Identifier");
	}

	private DataFetcher<Map<String, String>> getPostTypeAllDataFetcherUnique() {
		return dataFetchingEnvironment -> {
			Map<String, String> post = new HashMap<>();

			Map<String, Object> whereMap = dataFetchingEnvironment.getArgument(ShGraphQLConstants.WHERE_ARG);

			if (whereMap != null) {
				for (Entry<String, Object> whereArgItem : whereMap.entrySet()) { //NOSONAR
					String arg = whereArgItem.getKey();
					if (arg.equals(ShGraphQLConstants.ID)) {
						String objectId = whereMap.get(ShGraphQLConstants.ID).toString();
						ShObject shObject = shObjectRepository.findById(objectId).orElse(null);
						post = shGraphQLUtils.graphQLAttrsByPost((ShPost) shObject);
					}
				}
			}
			return post;
		};
	}
}
