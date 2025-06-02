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
import static graphql.Scalars.GraphQLString;
import static graphql.schema.FieldCoordinates.coordinates;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLNonNull.nonNull;
import static graphql.schema.GraphQLList.list;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.graphql.ShGraphQLConstants;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.website.component.ShQueryComponent;

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
@Slf4j
public class ShGraphQLQTQuery {
	private final ShQueryComponent shQueryComponent;
	private static final String QUERY_TYPE_NAME = "shQuery";

	public static final String POST_TYPE_NAME = "postTypeName";
	public static final String FOLDER_ID = "folderId";
	public static final String POST_ATTR_NAME = "postAttrName";
	public static final String ARRAY_VALUE = "arrayValue";

	@Autowired
	public ShGraphQLQTQuery(ShQueryComponent shQueryComponent) {
		this.shQueryComponent = shQueryComponent;
	}

	public void createQueryType(Builder queryTypeBuilder,
			graphql.schema.GraphQLCodeRegistry.Builder codeRegistryBuilder, GraphQLObjectType graphQLObjectType) {

		this.createArguments(queryTypeBuilder, graphQLObjectType);

		codeRegistryBuilder.dataFetcher(coordinates(ShGraphQLConstants.QUERY_TYPE, QUERY_TYPE_NAME),
				this.getDataFetcher());
	}

	private void createArguments(Builder queryTypeBuilder, GraphQLObjectType graphQLObjectType) {
		queryTypeBuilder.field(newFieldDefinition().name(QUERY_TYPE_NAME).type(list(graphQLObjectType))
				.argument(newArgument().name(POST_TYPE_NAME).description("Post Type Name").type(nonNull(GraphQLString)))
				.argument(newArgument().name(FOLDER_ID).description("Folder Id").type(GraphQLID))
				.argument(newArgument().name(POST_ATTR_NAME).description("Post Attribute Name").type(GraphQLString))
				.argument(newArgument().name(ARRAY_VALUE).description("Array Value").type(list(GraphQLString))));

	}

	private DataFetcher<List<Map<String, Object>>> getDataFetcher() {
		return dataFetchingEnvironment -> {
			List<Map<String, Object>> results = new ArrayList<>();
			String postTypeName = dataFetchingEnvironment.getArgument(POST_TYPE_NAME);
			String folderId = dataFetchingEnvironment.getArgument(FOLDER_ID);
			String postAttrName = dataFetchingEnvironment.getArgument(POST_ATTR_NAME);
			List<String> arrayValue = dataFetchingEnvironment.getArgument(ARRAY_VALUE);
			if (folderId == null && postAttrName == null && arrayValue == null) {
				shQueryComponent.findByPostTypeName(postTypeName).forEach(post -> results.add(postToGraphQL(post)));
			} else if (folderId != null) {
				shQueryComponent.findByFolderName(folderId, postTypeName)
						.forEach(post -> results.add(postToGraphQL(post)));
			} else if (postAttrName != null && arrayValue != null) {
				shQueryComponent.findByPostTypeNameIn(postTypeName, Sets.newHashSet(arrayValue))
						.forEach(post -> results.add(postToGraphQL(post)));
			}

			return results;
		};
	}

	private Map<String, Object> postToGraphQL(Map<String, ShPostAttr> post) {
		Map<String, Object> result = new HashMap<>();
		ObjectMapper mapper = new ObjectMapper();
		String jsonInString;
		try {
			jsonInString = mapper.writerWithView(ShJsonView.ShJsonViewObject.class).writeValueAsString(post);

			Map<String, ShPostAttr> tinyPost = mapper.readValue(jsonInString,
					new TypeReference<Map<String, ShPostAttr>>() {
					});
			tinyPost.remove("__type__");
			result.put("post", tinyPost);
		} catch (JsonProcessingException e) {
			log.error(e.getMessage(), e);
		}

		return result;
	}
}
