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

import static graphql.Scalars.GraphQLString;
import static graphql.Scalars.GraphQLID;
import static graphql.Scalars.GraphQLBoolean;
import static graphql.schema.FieldCoordinates.coordinates;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLNonNull.nonNull;
import graphql.language.BooleanValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.graphql.ShGraphQLConstants;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.website.component.ShNavigationComponent;

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
public class ShGraphQLQTNavigation {

	private final ShNavigationComponent shNavigationComponent;
	private static final String QUERY_TYPE_NAME = "shNavigation";
	
	public static final String SITE_NAME = "siteName";
	public static final String FOLDER_ID = "folderId";
	public static final String IS_HOME = "isHome";

	@Autowired
	public ShGraphQLQTNavigation(ShNavigationComponent shNavigationComponent) {
		this.shNavigationComponent = shNavigationComponent;
	}

	public void createQueryType(Builder queryTypeBuilder,
			graphql.schema.GraphQLCodeRegistry.Builder codeRegistryBuilder, GraphQLObjectType graphQLObjectType) {

		this.createArguments(queryTypeBuilder, graphQLObjectType);

		codeRegistryBuilder.dataFetcher(coordinates(ShGraphQLConstants.QUERY_TYPE, QUERY_TYPE_NAME),
				this.getDataFetcher());
	}

	private void createArguments(Builder queryTypeBuilder, GraphQLObjectType graphQLObjectType) {

		queryTypeBuilder.field(newFieldDefinition().name(QUERY_TYPE_NAME).type(nonNull(graphQLObjectType))
				.argument(newArgument().name(SITE_NAME).description("Site Name").type(GraphQLString))
				.argument(newArgument().name(FOLDER_ID).description("Folder ID").type(GraphQLID))
				.argument(newArgument().name(IS_HOME).description("Show Home Folder").type(nonNull(GraphQLBoolean))
						.defaultValueLiteral(new BooleanValue(true))));

	}

	private DataFetcher<Map<String, Object>> getDataFetcher() {
		return dataFetchingEnvironment -> {
			Map<String, Object> result = new HashMap<>();
			String siteName = dataFetchingEnvironment.getArgument(SITE_NAME);
			String folderId = dataFetchingEnvironment.getArgument(FOLDER_ID);
			boolean home = dataFetchingEnvironment.getArgument(IS_HOME);
			List<ShFolder> shFolders = null;
			if (siteName != null) {
				shFolders = shNavigationComponent.navigation(siteName, home);
			} else if (folderId != null) {
				shFolders = shNavigationComponent.navigationFolder(folderId, home);
			}
			result.put("folders", shFolders);
			return result;
		};
	}
}
