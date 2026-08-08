/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.server.PathContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Every Shio controller route, asserted from outside with no credentials — the general form of
 * {@code ShPreviewIsNotPublicTest} (SH363).
 *
 * <h2>The gap this closes</h2>
 *
 * <p>SH359 was a security claim in prose that nobody checked: {@code ShRenderAPI}'s note said the
 * preview sat under {@code anyRequest().authenticated()} while a {@code permitAll()} entry written
 * years earlier for a different route made every draft anonymous-readable. Both halves read
 * correctly on their own, so review could not see it. {@code ShPreviewIsNotPublicTest} nailed that
 * one route; the shape needs the whole surface.</p>
 *
 * <p>The instrument is the deliverable (SH338), and the honest version is narrow: not "every prose
 * claim is true" but two things a request can answer.</p>
 *
 * <ul>
 *   <li><b>Reachability is asserted, not reasoned.</b> For every governed prefix the
 *       {@link #EXPECTED} table names, an anonymous GET is issued and the outcome checked: a
 *       {@code 401} for a route that must be authenticated, and <em>not</em> a {@code 401} for one
 *       that is public. Spring Security's authorization runs before dispatch, so this holds
 *       whatever the handler's method or existence — a config that restates itself would not.</li>
 *   <li><b>A new mapping fails the build until it is classified.</b> Every request mapping a Shio
 *       controller registers must be covered by a prefix in the table. Mount a controller on a new
 *       path and this test names it and refuses to pass until somebody declares which side of the
 *       line it is on — which is the moment SH359 never had.</li>
 * </ul>
 *
 * <p>The default (non-Keycloak) filter chain is the one under test, matching
 * {@code ShPreviewIsNotPublicTest}: an anonymous caller is refused by {@code ShAuthenticationEntryPoint}
 * with {@code 401}.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAnonymousRouteAccessTest {

	/** Whether a governed prefix is reachable with no credentials. */
	private enum Access {
		/** {@code permitAll()} — an anonymous request is not refused by authentication. */
		PUBLIC,
		/** {@code authenticated()} (or a role) — an anonymous request is refused with {@code 401}. */
		AUTHENTICATED
	}

	/**
	 * A governed prefix, an example concrete path under it, and the anonymous outcome its
	 * classification requires.
	 *
	 * @param prefixPattern a Spring path pattern covering every mapping under the prefix
	 * @param examplePath   a concrete path <em>that resolves to a real handler</em> — the auth layer
	 *                      answers {@code 401} only once a handler matches, so a synthetic path with
	 *                      no mapping would {@code 404} before authorization and prove nothing
	 * @param access        the reachability the classification asserts
	 */
	private record Route(String prefixPattern, String examplePath, Access access) {
	}

	// Every Shio controller route is covered by exactly one line here. To add a controller, add its
	// prefix and declare PUBLIC or AUTHENTICATED — everyControllerRouteIsClassified() fails naming
	// any route this table does not cover, so the declaration is not optional (SH363).
	private static final List<Route> EXPECTED = List.of(
			// -- open (permitAll): reachable with no credentials --
			new Route("/", "/", Access.PUBLIC),
			new Route("/*/console/**", "/acme/console", Access.PUBLIC),
			new Route("/sites/**", "/sites/any-site/html/en/x", Access.PUBLIC),
			new Route("/api/v2/ping", "/api/v2/ping", Access.PUBLIC),
			new Route("/api/v2/csrf", "/api/v2/csrf", Access.PUBLIC),
			new Route("/api/v2/discovery", "/api/v2/discovery", Access.PUBLIC),
			new Route("/api/v2/setup", "/api/v2/setup", Access.PUBLIC),
			new Route("/api/v2/login", "/api/v2/login", Access.PUBLIC),
			// -- governed (authenticated()): a no-credential request is refused with 401 --
			// SH359 lived here: /preview/** must never answer an anonymous caller.
			new Route("/preview/**", "/preview/any-site/any-page", Access.AUTHENTICATED),
			new Route("/mcp", "/mcp", Access.AUTHENTICATED),
			new Route("/api/v2", "/api/v2", Access.AUTHENTICATED),
			new Route("/api/v2/agent/**", "/api/v2/agent/manifest", Access.AUTHENTICATED),
			new Route("/api/v2/api-token/**", "/api/v2/api-token", Access.AUTHENTICATED),
			new Route("/api/v2/cda/**", "/api/v2/cda/site", Access.AUTHENTICATED),
			new Route("/api/v2/config/**", "/api/v2/config/email", Access.AUTHENTICATED),
			new Route("/api/v2/folder/**", "/api/v2/folder/x", Access.AUTHENTICATED),
			new Route("/api/v2/group/**", "/api/v2/group", Access.AUTHENTICATED),
			new Route("/api/v2/history/**", "/api/v2/history/object/x", Access.AUTHENTICATED),
			new Route("/api/v2/import", "/api/v2/import", Access.AUTHENTICATED),
			new Route("/api/v2/locale/**", "/api/v2/locale", Access.AUTHENTICATED),
			new Route("/api/v2/object/**", "/api/v2/object/x/path", Access.AUTHENTICATED),
			new Route("/api/v2/post-type/**", "/api/v2/post-type", Access.AUTHENTICATED),
			new Route("/api/v2/post-unified/**", "/api/v2/post-unified/x", Access.AUTHENTICATED),
			new Route("/api/v2/post/**", "/api/v2/post/xp/x", Access.AUTHENTICATED),
			new Route("/api/v2/preview-token", "/api/v2/preview-token", Access.AUTHENTICATED),
			new Route("/api/v2/provider/**", "/api/v2/provider/auth", Access.AUTHENTICATED),
			new Route("/api/v2/render/**", "/api/v2/render/post-types", Access.AUTHENTICATED),
			new Route("/api/v2/review/**", "/api/v2/review/sessions", Access.AUTHENTICATED),
			new Route("/api/v2/role/**", "/api/v2/role", Access.AUTHENTICATED),
			new Route("/api/v2/site/**", "/api/v2/site/x", Access.AUTHENTICATED),
			new Route("/api/v2/site-webhook/**", "/api/v2/site-webhook/x", Access.AUTHENTICATED),
			new Route("/api/v2/staticfile/**", "/api/v2/staticfile/upload", Access.AUTHENTICATED),
			new Route("/api/v2/system/**", "/api/v2/system/info", Access.AUTHENTICATED),
			new Route("/api/v2/tenant/**", "/api/v2/tenant/current", Access.AUTHENTICATED),
			new Route("/api/v2/user/**", "/api/v2/user", Access.AUTHENTICATED),
			new Route("/api/v2/widget/**", "/api/v2/widget", Access.AUTHENTICATED));

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc secured;

	@BeforeAll
	void setUp() {
		secured = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(springSecurity())
				.build();
	}

	@Test
	@DisplayName("every governed prefix answers an anonymous caller as its classification requires")
	void anonymousAccessMatchesClassification() throws Exception {
		for (Route route : EXPECTED) {
			int status = secured.perform(get(route.examplePath()))
					.andReturn().getResponse().getStatus();
			if (route.access() == Access.AUTHENTICATED) {
				// The SH359 assertion, generalized: a governed route resolves nothing for a caller
				// with no credentials. A 200 here is a draft (or worse) readable by the port.
				assertEquals(401, status,
						route.examplePath() + " must refuse an anonymous caller (SH363)");
			} else {
				// permitAll: the request may 200/302/404/405, but authentication never turns it away.
				assertNotEquals(401, status,
						route.examplePath() + " is public and must be reachable anonymously (SH363)");
			}
		}
	}

	@Test
	@DisplayName("a controller route the table does not classify fails the build (SH363)")
	void everyControllerRouteIsClassified() {
		PathPatternParser parser = new PathPatternParser();
		List<PathPattern> classified = EXPECTED.stream()
				.map(route -> parser.parse(route.prefixPattern()))
				.toList();

		RequestMappingHandlerMapping handlerMapping = webApplicationContext
				.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);

		List<String> unclassified = new ArrayList<>();
		handlerMapping.getHandlerMethods().forEach((info, method) -> {
			// Only Shio's own controllers — Spring's error controller and springdoc are governed
			// elsewhere and are not part of the product's route surface.
			if (!method.getBeanType().getName().startsWith("com.viglet.shio")) {
				return;
			}
			for (String pattern : patternsOf(info)) {
				PathContainer concrete = PathContainer.parsePath(toConcretePath(pattern));
				boolean covered = classified.stream().anyMatch(p -> p.matches(concrete));
				if (!covered) {
					unclassified.add(method.getBeanType().getSimpleName() + " -> " + pattern);
				}
			}
		});

		assertTrue(unclassified.isEmpty(),
				"These controller routes are not declared in the anonymous-access table. Add each to "
						+ "EXPECTED as PUBLIC or AUTHENTICATED so its reachability is asserted (SH363): "
						+ unclassified);
	}

	private static Set<String> patternsOf(RequestMappingInfo info) {
		if (info.getPathPatternsCondition() != null) {
			return info.getPathPatternsCondition().getPatternValues();
		}
		return Set.of();
	}

	/**
	 * A concrete path standing in for a mapping pattern: path variables and {@code **} become a
	 * literal segment so the pattern can be matched against the classification table.
	 */
	private static String toConcretePath(String pattern) {
		String concrete = pattern.replaceAll("\\{[^/{}]+}", "x")
				.replace("/**", "/x");
		return concrete.isEmpty() ? "/" : concrete;
	}
}
