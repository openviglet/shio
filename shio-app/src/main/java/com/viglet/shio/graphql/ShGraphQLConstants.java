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
package com.viglet.shio.graphql;

/**
 * GraphQL Constants.
 *
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
public final class ShGraphQLConstants {

	private ShGraphQLConstants() {
        // restrict instantiation
	}
	
	public static final String QUERY_TYPE = "Query";

	public static final String SEARCH = "_search";
	public static final String AND = "AND";
	public static final String OR = "OR";
	public static final String NOT = "NOT";
	public static final String ID = "id";
	public static final String TITLE = "_title";
	public static final String DESCRIPTION = "_description";
	public static final String FURL = "_furl";
	public static final String MODIFIER = "_modifier";
	public static final String PUBLISHER = "_publisher";
	public static final String FOLDER = "_folder";
	public static final String SITE = "_site";
	public static final String CREATED_AT = "_createdAt";
	public static final String UPDATED_AT = "_updatedAt";
	public static final String PUBLISHED_AT = "_publishedAt";

	public static final String CONDITION_EQUAL = "equal";
	public static final String CONDITION_NOT = "not";
	public static final String CONDITION_IN = "in";
	public static final String CONDITION_NOT_IN = "not_in";
	public static final String CONDITION_CONTAINS = "contains";
	public static final String CONDITION_NOT_CONTAINS = "not_contains";
	public static final String CONDITION_STARTS_WITH = "starts_with";
	public static final String CONDITION_NOT_STARTS_WITH = "not_starts_with";
	public static final String CONDITION_ENDS_WITH = "ends_with";
	public static final String CONDITION_NOT_ENDS_WITH = "not_ends_with";

	public static final String CONDITION_LT = "lt";
	public static final String CONDITION_LTE = "lte";
	public static final String CONDITION_GT = "gt";
	public static final String CONDITION_GTE = "gte";

	public static final String STAGE_ARG = "stage";
	public static final String STAGE_ARG_TITLE = "Stage";
	public static final String LOCALES_ARG = "locales";
	public static final String LOCALES_ARG_TITLE = "Locale";
	public static final String SITES_ARG = "sites";
	public static final String SITES_ARG_TITLE = "Site";
	public static final String WHERE_ARG = "where";

	public static final String WHERE_UNIQUE_INPUT = "WhereUniqueInput";
	public static final String WHERE_INPUT = "WhereInput";
	public static final String CONDITION_SEPARATOR = "_";

	public static final String FIELD_TYPE_GRAPHQL_ID = "GraphQLID";
	public static final String FIELD_TYPE_GRAPHQL_STRING = "GraphQLString";
	public static final String FIELD_TYPE_GRAPHQL_DATE_TIME = "DateTime";


}
