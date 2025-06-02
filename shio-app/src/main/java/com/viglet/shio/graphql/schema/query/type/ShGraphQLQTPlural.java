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

import static graphql.Scalars.GraphQLString;
import static graphql.schema.FieldCoordinates.coordinates;
import static graphql.schema.GraphQLInputObjectType.newInputObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.graphql.ShGraphQLConstants;
import com.viglet.shio.graphql.ShGraphQLUtils;
import com.viglet.shio.graphql.schema.ShGraphQLInputObjectField;

import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.repository.post.ShPostRepository;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLObjectType.Builder;

/**
 * GraphQL Query Type Plural.
 *
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
@Component
@Slf4j
public class ShGraphQLQTPlural {
	private final ShPostRepository shPostRepository;
	private final ShGraphQLQTCommons shGraphQLQTCommons;
	private final ShGraphQLUtils shGraphQLUtils;
	private final ShGraphQLInputObjectField shGraphQLInputObjectField;

	@Autowired
	public ShGraphQLQTPlural(ShPostRepository shPostRepository, ShGraphQLQTCommons shGraphQLQTCommons,
							 ShGraphQLUtils shGraphQLUtils, ShGraphQLInputObjectField shGraphQLInputObjectField) {
		this.shPostRepository = shPostRepository;
		this.shGraphQLQTCommons = shGraphQLQTCommons;
		this.shGraphQLUtils = shGraphQLUtils;
		this.shGraphQLInputObjectField = shGraphQLInputObjectField;
	}

	private String getPostTypeNamePlural(ShPostType shPostType) {
		return shGraphQLUtils.normalizedName(shPostType.getNamePlural());
	}

	public void createQueryTypePlural(Builder queryTypeBuilder,
			graphql.schema.GraphQLCodeRegistry.Builder codeRegistryBuilder, ShPostType shPostType,
			GraphQLObjectType graphQLObjectType) {
		if (!StringUtils.isBlank(shPostType.getName()) && !StringUtils.isBlank(shPostType.getNamePlural())) {
			String postTypeNamePlural = this.getPostTypeNamePlural(shPostType);

			GraphQLInputObjectType.Builder postTypeWhereInputBuilder = newInputObject()
					.name(shPostType.getName().replace("-", "_").concat(ShGraphQLConstants.WHERE_INPUT))
					.description("Identifies documents");

			this.whereFieldsPlural(shPostType, postTypeWhereInputBuilder);

			shGraphQLQTCommons.createArguments(queryTypeBuilder, graphQLObjectType, postTypeNamePlural,
					postTypeWhereInputBuilder, true);

			codeRegistryBuilder.dataFetcher(coordinates(ShGraphQLConstants.QUERY_TYPE, postTypeNamePlural),
					this.getPostTypeAllDataFetcherPlural(shPostType));
		}
	}

	private void whereFieldsPlural(ShPostType shPostType, GraphQLInputObjectType.Builder postTypeWhereInputBuilder) {

		String whereInputName = shPostType.getName().replace("-", "_").concat(ShGraphQLConstants.WHERE_INPUT);

		GraphQLTypeReference whereInputRef = GraphQLTypeReference.typeRef(whereInputName);

		shGraphQLInputObjectField.createInputObjectField(postTypeWhereInputBuilder, ShGraphQLConstants.SEARCH,
				GraphQLString, "Contains search across all appropriate fields.");
		shGraphQLInputObjectField.createInputObjectField(postTypeWhereInputBuilder, ShGraphQLConstants.AND,
				whereInputRef, "Logical AND on all given filters.");
		shGraphQLInputObjectField.createInputObjectField(postTypeWhereInputBuilder, ShGraphQLConstants.OR,
				whereInputRef, "Logical OR on all given filters.");
		shGraphQLInputObjectField.createInputObjectField(postTypeWhereInputBuilder, ShGraphQLConstants.NOT,
				whereInputRef, "Logical NOT on all given filters combined by AND.");

		shGraphQLInputObjectField.createInputObjectField(postTypeWhereInputBuilder, ShGraphQLConstants.ID,
				ShGraphQLConstants.FIELD_TYPE_GRAPHQL_ID, "Identifier");
		shGraphQLInputObjectField.createInputObjectField(postTypeWhereInputBuilder, ShGraphQLConstants.TITLE,
				ShGraphQLConstants.FIELD_TYPE_GRAPHQL_STRING, "Title");
		shGraphQLInputObjectField.createInputObjectField(postTypeWhereInputBuilder, ShGraphQLConstants.DESCRIPTION,
				ShGraphQLConstants.FIELD_TYPE_GRAPHQL_STRING, "Description");
		shGraphQLInputObjectField.createInputObjectField(postTypeWhereInputBuilder, ShGraphQLConstants.FURL,
				ShGraphQLConstants.FIELD_TYPE_GRAPHQL_STRING, "Friendly URL");
		shGraphQLInputObjectField.createInputObjectField(postTypeWhereInputBuilder, ShGraphQLConstants.MODIFIER,
				ShGraphQLConstants.FIELD_TYPE_GRAPHQL_STRING, "Modifier");
		shGraphQLInputObjectField.createInputObjectField(postTypeWhereInputBuilder, ShGraphQLConstants.PUBLISHER,
				ShGraphQLConstants.FIELD_TYPE_GRAPHQL_STRING, "Publisher");
		shGraphQLInputObjectField.createInputObjectField(postTypeWhereInputBuilder, ShGraphQLConstants.FOLDER,
				ShGraphQLConstants.FIELD_TYPE_GRAPHQL_STRING, "Folder Name");
		shGraphQLInputObjectField.createInputObjectField(postTypeWhereInputBuilder, ShGraphQLConstants.CREATED_AT,
				ShGraphQLConstants.FIELD_TYPE_GRAPHQL_DATE_TIME, "Created Date");
		shGraphQLInputObjectField.createInputObjectField(postTypeWhereInputBuilder, ShGraphQLConstants.UPDATED_AT,
				ShGraphQLConstants.FIELD_TYPE_GRAPHQL_DATE_TIME, "Updated Date");
		shGraphQLInputObjectField.createInputObjectField(postTypeWhereInputBuilder, ShGraphQLConstants.PUBLISHED_AT,
				ShGraphQLConstants.FIELD_TYPE_GRAPHQL_DATE_TIME, "Published Date");

		for (ShPostTypeAttr shPostTypeAttr : shPostType.getShPostTypeAttrs()) {

			String postTypeAttrName = shGraphQLUtils.normalizedField(shPostTypeAttr.getName());

			shGraphQLInputObjectField.createInputObjectField(postTypeWhereInputBuilder, postTypeAttrName,
					ShGraphQLConstants.FIELD_TYPE_GRAPHQL_STRING, shPostTypeAttr.getDescription());

		}
	}

	private DataFetcher<List<Map<String, String>>> getPostTypeAllDataFetcherPlural(ShPostType shPostType) {

		return dataFetchingEnvironment -> {

			List<Map<String, String>> posts = new ArrayList<>();
			List<String> siteIds = dataFetchingEnvironment.getArgument(ShGraphQLConstants.SITES_ARG);

			Map<String, Object> whereMap = dataFetchingEnvironment.getArgument(ShGraphQLConstants.WHERE_ARG);

			if (whereMap != null) {
				this.whereCondition(shPostType, posts, siteIds, whereMap);
			} else {
				shPostRepository.findByShPostType(shPostType)
						.forEach(shPost -> posts.add(shGraphQLUtils.graphQLAttrsByPost(shPost)));
			}
			return posts;
		};
	}

	private void whereCondition(ShPostType shPostType, List<Map<String, String>> posts, List<String> siteIds,
			Map<String, Object> whereMap) {
		whereMap.entrySet().forEach(whereArgItem -> {

			String arg = whereArgItem.getKey();
            switch (arg) {
                case ShGraphQLConstants.SEARCH -> log.info("GraphQL Search");
                case ShGraphQLConstants.AND -> log.info("GraphQL AND");
                case ShGraphQLConstants.OR -> log.info("GraphQL OR");
                case ShGraphQLConstants.NOT -> log.info("GraphQL NOT");
                default -> {
                    String field = arg;
                    String action = ShGraphQLConstants.CONDITION_EQUAL;
                    if (arg.startsWith("_")
                            && arg.replaceFirst("_", StringUtils.EMPTY).contains(ShGraphQLConstants.CONDITION_SEPARATOR)) {
                        field = String.format("_%s", arg.split(ShGraphQLConstants.CONDITION_SEPARATOR)[1]);
                        action = arg.replaceFirst(field.concat(ShGraphQLConstants.CONDITION_SEPARATOR), StringUtils.EMPTY);

                    } else if (!arg.startsWith("_") && arg.contains(ShGraphQLConstants.CONDITION_SEPARATOR)) {
                        field = arg.split(ShGraphQLConstants.CONDITION_SEPARATOR)[0];
                        action = arg.replaceFirst(field.concat(ShGraphQLConstants.CONDITION_SEPARATOR), StringUtils.EMPTY);
                    }
                    shGraphQLInputObjectField.fieldWhereCondition(shPostType, posts, whereArgItem, field, action, siteIds);
                }
            }
		});
	}
}
