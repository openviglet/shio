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
package com.viglet.shio.graphql.playground;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

/**
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
class ShPropsLoader {

	private static final String GRAPHIQL_PROPS_PREFIX = "graphiql.props.";
	private static final String GRAPHIQL_PROPS_RESOURCES_PREFIX = GRAPHIQL_PROPS_PREFIX + "resources.";
	private static final String GRAPHIQL_PROPS_VALUES_PREFIX = GRAPHIQL_PROPS_PREFIX + "variables.";

	private final Environment environment;

	ShPropsLoader(Environment environment) {
		this.environment = environment;
	}

	String load() throws IOException {
		ShPropertyGroupReader reader = new ShPropertyGroupReader(environment, GRAPHIQL_PROPS_VALUES_PREFIX);
		Properties props = reader.load();

		ObjectMapper objectMapper = new ObjectMapper();
		loadPropFromResource("defaultQuery").ifPresent(it -> props.put("defaultQuery", it));
		loadPropFromResource("query").ifPresent(it -> props.put("query", it));
		loadPropFromResource("variables").ifPresent(it -> props.put("variables", it));
		return objectMapper.writeValueAsString(props);
	}

	private Optional<String> loadPropFromResource(String prop) throws IOException {
		String property = GRAPHIQL_PROPS_RESOURCES_PREFIX + prop;
		if (environment.containsProperty(property)) {
			String location = environment.getProperty(property);
			if (location != null) {
				Resource resource = new ClassPathResource(location);
				return Optional.of(loadResource(resource));
			}
		}
		return Optional.empty();
	}

	private String loadResource(Resource resource) throws IOException {
		try (InputStream inputStream = resource.getInputStream()) {
			return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
		}
	}

}
