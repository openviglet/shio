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
import static graphql.schema.FieldCoordinates.coordinates;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLNonNull.nonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.website.ShSitesContextURL;
import com.viglet.shio.website.ShSitesContextURLProcess;
import com.viglet.shio.website.ShSitesDetectContextURL;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.viglet.shio.graphql.ShGraphQLConstants;
import com.viglet.shio.graphql.ShGraphQLUtils;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.website.ShContent;
import com.viglet.shio.website.component.ShSitesContent;
import com.viglet.shio.website.utils.ShSitesPageLayoutUtils;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLObjectType.Builder;

/**
 * GraphQL Object From URL Query Type.
 *
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
@Component
public class ShGraphQLQTObjectFromURL {

	@Autowired
	private ShSitesContent shSitesContent;
	@Autowired
	private ShObjectRepository shObjectRepository;
	@Autowired
	private ShGraphQLUtils shGraphQLUtils;
	@Autowired
	private ShSitesPageLayoutUtils shSitesPageLayoutUtils;
	@Autowired
	private ShSitesDetectContextURL shSitesDetectContextURL;
	@Autowired
	private ShSiteRepository shSiteRepository;
	private static final String CONTENT_NAME = "shObjectFromURL";
	private static final String SYSTEM_ATTR = "system";

	public void createQueryType(Builder queryTypeBuilder,
			graphql.schema.GraphQLCodeRegistry.Builder codeRegistryBuilder, GraphQLObjectType graphQLObjectType) {

		this.createArguments(queryTypeBuilder, graphQLObjectType);

		codeRegistryBuilder.dataFetcher(coordinates(ShGraphQLConstants.QUERY_TYPE, CONTENT_NAME),
				this.getDataFetcher());
	}

	private void createArguments(Builder queryTypeBuilder, GraphQLObjectType graphQLObjectType) {

		queryTypeBuilder.field(newFieldDefinition().name(CONTENT_NAME).type(nonNull(graphQLObjectType))
				.argument(newArgument().name("url").description("Site URL").type(nonNull(GraphQLString))));

	}

	private DataFetcher<Map<String, Object>> getDataFetcher() {
		return dataFetchingEnvironment -> {
			Gson gson = new Gson();
			String url = dataFetchingEnvironment.getArgument("url");
			ShContent shContent = shSitesContent.fromURL(url);
			JSONObject system = new JSONObject(gson.toJson(shContent.get(SYSTEM_ATTR)));
			String objectId = system.getString("id");
			Optional<ShObject> shObject = shObjectRepository.findById(objectId);
			if (shObject.isPresent()) {
				Map<String, Object> post = new HashMap<>();
				JSONObject site = new JSONObject(gson.toJson(shContent.get("site")));
				String siteId = site.getJSONObject(SYSTEM_ATTR).getString("id");
				String siteName = site.getJSONObject(SYSTEM_ATTR).getString("title");
				String type = null;

				if (shObject.get() instanceof ShPost shPost) {
					type = shGraphQLUtils.normalizedName(shPost.getShPostType().getName());
				} else if (shObject.get() instanceof ShFolder) {
					type = "folder";
				} else if (shObject.get() instanceof ShSite) {
					type = "site";
				}

				ShPost pageLayout = fromURL(url);

				if (pageLayout != null)
					post.put("pageLayout", pageLayout.getTitle());

				post.put("id", system.getString("id"));
				post.put("locale", "Locale1");
				post.put("context", "Context1");
				post.put("type", type);
				post.put("format", "Format1");
				post.put("siteId", siteId);
				post.put("siteName", siteName);
				post.put("content", shContent);

				return post;
			}
			return null;
		};
	}

	public ShPost fromURL(String url) {
		ShSitesContextURL shSitesContextURL = new ShSitesContextURL();

		shSitesDetectContextURL.detectContextURL(url, shSitesContextURL);

		Optional<ShObject> shObject = shObjectRepository.findById(shSitesContextURL.getInfo().getObjectId());

		Optional<ShSite> shSite = shSiteRepository.findById(shSitesContextURL.getInfo().getSiteId());

		String format = shSitesContextURL.getInfo().getShFormat();

		if (shObject.isPresent() && shSite.isPresent()) {
			if (shObject.get() instanceof ShFolder shFolder) {
				return shSitesPageLayoutUtils.pageLayoutFromFolderAndFolderIndex(shFolder, shSite.get(), format);
			} else if (shObject.get() instanceof ShPostImpl shPostImpl) {
				if (shPostImpl.getShPostType().getName().equals(ShSystemPostType.FOLDER_INDEX)) {
					return shSitesPageLayoutUtils.pageLayoutFromFolderAndFolderIndex(shPostImpl, shSite.get(), format);
				} else {
					return shSitesPageLayoutUtils.pageLayoutFromPost(shPostImpl, shSite.get(), format);
				}

			}
		}
		return null;
	}
}
