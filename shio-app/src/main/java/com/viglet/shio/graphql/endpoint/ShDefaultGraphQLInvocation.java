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
package com.viglet.shio.graphql.endpoint;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.spring.web.servlet.ExecutionInputCustomizer;
import graphql.spring.web.servlet.GraphQLInvocation;
import graphql.spring.web.servlet.GraphQLInvocationData;
import org.dataloader.DataLoaderRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import com.viglet.shio.graphql.schema.ShGraphQLSchema;

import java.util.concurrent.CompletableFuture;

@Component
public class ShDefaultGraphQLInvocation implements GraphQLInvocation {
	@Autowired(required = false)
	private DataLoaderRegistry dataLoaderRegistry;

	@Autowired
	private ExecutionInputCustomizer executionInputCustomizer;

	@Autowired
	private  ShGraphQLSchema shGraphQLSchema;

	@Override
	public CompletableFuture<ExecutionResult> invoke(GraphQLInvocationData invocationData, WebRequest webRequest) {
		ExecutionInput.Builder executionInputBuilder = ExecutionInput.newExecutionInput()
				.query(invocationData.getQuery()).operationName(invocationData.getOperationName())
				.variables(invocationData.getVariables());
		if (dataLoaderRegistry != null) {
			executionInputBuilder.dataLoaderRegistry(dataLoaderRegistry);
		}
		ExecutionInput executionInput = executionInputBuilder.build();
		CompletableFuture<ExecutionInput> customizedExecutionInput = executionInputCustomizer
				.customizeExecutionInput(executionInput, webRequest);
		shGraphQLSchema.init();
		return customizedExecutionInput.thenCompose(shGraphQLSchema.graphQL()::executeAsync);

	}

}