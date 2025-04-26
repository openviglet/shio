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
package com.viglet.shio.graphql;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;

import graphql.kickstart.spring.webclient.boot.GraphQLWebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * @author Alexandre Oliveira
 * @since 0.3.8
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ShGraphQLTest {

	@LocalServerPort
	private int randomServerPort;

	@Autowired
	private ObjectMapper objectMapper;

	private GraphQLWebClient graphqlClient;

	@BeforeEach
	void beforeEach() {
		WebClient webClient = WebClient.builder().baseUrl("http://localhost:" + randomServerPort + "/graphql").build();
		graphqlClient = GraphQLWebClient.newInstance(webClient, objectMapper);
	}

	@Test
	@DisplayName("Query Text Post Type List")
	void queryWithoutVariablesSucceeds() {
		Mono<String> response = graphqlClient.post("graphql/texts-test.graphql", null, String.class);
		assertNotNull(response, "response should not be null");
	}
}