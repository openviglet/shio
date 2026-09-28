/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Every SPA route tree the console mounts is served by this application (SH926).
 *
 * <h2>The defect, which fourteen tasks reported green over</h2>
 *
 * <p>{@code ShStaticResourceConfiguration.REACT_CONTEXTS} was
 * <code>{"/login", "/setup", "/console"}</code>, and those strings are the whole of what a
 * packaged instance forwards to the React shell. Block AR's bento route tree is a <em>sibling</em>
 * of {@code /console} rather than a child (SH888), so nothing matched it: measured on a running
 * jar, {@code /console} answered 200 and {@code /bento} answered <b>404</b>, in the same second.
 * Fourteen migrated surfaces were unreachable by URL — a typed address, a bookmark, a link in an
 * e-mail, a redirect — on the only deployment anybody runs.</p>
 *
 * <p>Nothing caught it because every live probe in that block ran against the Vite dev server,
 * which serves {@code index.html} for any path that is not a file. That is what a dev server is
 * for. It is also why "the surface renders" and "the surface is reachable" are different claims,
 * and the block only ever checked the first.</p>
 *
 * <h2>Why a request and not a reading</h2>
 *
 * <p>Asserting the contents of the array would restate the configuration in a second place, which
 * is the shape that agrees with itself and with nothing else. This issues the request instead, so
 * it holds whatever the mechanism becomes — a view controller today, a filter or a controller
 * tomorrow.</p>
 *
 * <p>The two shapes are different on purpose. An <b>exact</b> context is a view controller and
 * forwards to {@code /index.html}; a <b>deeper</b> path is the resource handler, whose resolver
 * falls back to the shell and answers 200 with the document. Both are "the SPA is served here",
 * and a route tree missing from the array fails the second before the first — {@code /bento}
 * itself would have passed nothing at all.</p>
 *
 * <p>The other direction — that every top-level tree {@code App.tsx} declares appears in the
 * array — is {@code shio-react/test/spa-contexts.test.mjs}, because that is where the routes are
 * written and where a new one is added.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShSpaContextIsServedTest {

	/**
	 * Every context {@code REACT_CONTEXTS} declares, spelled here as the URLs a browser asks for.
	 *
	 * <p>Written out rather than read from the array by reflection: this is the list somebody
	 * reviewing a diff should see change, and a reader that derives its expectation from the thing
	 * under test cannot fail.</p>
	 */
	private static final List<String> CONTEXTS = List.of(
			"/login", "/setup", "/console", "/bento", "/bento-vocabulary");

	/** One client-side route under each tree — the shape that 404'd before SH926. */
	private static final List<String> DEEP_ROUTES = List.of(
			"/console/content",
			"/console/definitely-not-a-console-route",
			"/bento/content",
			"/bento/admin/review",
			"/bento/definitely-not-a-bento-route");

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	@BeforeAll
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
	}

	/**
	 * SH1183 — the shell this class serves is the test fixture, not a leftover React build.
	 *
	 * <p>{@code src/main/resources/public/} is a build output, git-ignored and absent under
	 * {@code -Dskip.npm=true}. Without a fixture the fallback resolver throws
	 * {@code FileNotFoundException} on CI while a dev box with a stale build stays green, and that
	 * pair kept the Java gate red on CI for every push it ran on. If the fixture goes, this fails
	 * locally too, which is the whole point.</p>
	 */
	@Test
	@DisplayName("SH1183: the SPA shell resolves from the test fixture, not from a React build")
	void theShellIsTheTestFixture() throws Exception {
		ClassPathResource shell = new ClassPathResource("public/index.html");
		assertTrue(shell.exists(),
				"SH1183: public/index.html is not on the test classpath. Restore shio-app/src/test/resources/public/index.html — the React build that would otherwise supply it is skipped on CI.");
		assertTrue(shell.getURL().toString().contains("test-classes"),
				"SH1183: public/index.html resolved from %s, not from the test fixture. This passes on a box with a React build and fails on CI."
						.formatted(shell.getURL()));
	}

	@Test
	@DisplayName("SH926: every SPA context forwards to the React shell")
	void everyContextForwardsToTheShell() throws Exception {
		for (String context : CONTEXTS) {
			MvcResult result = mockMvc.perform(get(context)).andReturn();
			assertEquals("/index.html", result.getResponse().getForwardedUrl(),
					"SH926: GET %s does not forward to the SPA shell. Add it to REACT_CONTEXTS in ShStaticResourceConfiguration — a route tree the React app mounts and this application does not serve is a set of pages that exist only on a dev server."
							.formatted(context));
		}
	}

	@Test
	@DisplayName("SH926: a client-side route under a context is answered with the shell, not a 404")
	void everyClientSideRouteIsAnsweredWithTheShell() throws Exception {
		for (String route : DEEP_ROUTES) {
			int status = mockMvc.perform(get(route)).andReturn().getResponse().getStatus();
			assertNotEquals(404, status,
					"SH926: GET %s answered 404. This is the exact failure the bento tree shipped with: the client-side router never runs, because the document that would boot it is never served."
							.formatted(route));
			assertEquals(200, status,
					"SH926: GET %s answered %d rather than serving the shell".formatted(route, status));
		}
	}

	/**
	 * SH909 — where the root sends a signed-in visitor, which is the server's half of "bento is the
	 * default".
	 *
	 * <p>The client's half is a `Navigate` in App.tsx and a route table full of `/bento` constants;
	 * this is the one a browser hits first, before a line of JavaScript runs. Left at
	 * {@code /console} it would still work — the SPA's legacy redirect would catch it — at the cost
	 * of a round trip and a URL bar that changes twice on the way to the home page.</p>
	 */
	@Test
	@DisplayName("SH909: the root sends a signed-in visitor to /bento, and everyone else to /login")
	void theRootRedirectsToBento() throws Exception {
		MvcResult signedIn = mockMvc.perform(get("/").with(user("admin"))).andReturn();
		assertEquals("/bento", signedIn.getResponse().getRedirectedUrl(),
				"SH909: the root no longer sends a signed-in visitor to the console. Bento is the"
						+ " default now; /console only redirects, so pointing the root at it costs a"
						+ " round trip and shows the reader two URLs on the way to one page.");

		MvcResult anonymous = mockMvc.perform(get("/")).andReturn();
		assertEquals("/login", anonymous.getResponse().getRedirectedUrl(),
				"SH909: an anonymous visitor must still be sent to the login page — the control for"
						+ " the assertion above, and the half that has nothing to do with bento.");
	}

	/**
	 * The vacuity check, and it is not decoration here.
	 *
	 * <p>If the fallback ever became global — one resolver answering the shell for everything —
	 * both assertions above would pass for ever while saying nothing, and a genuinely missing
	 * context would be indistinguishable from a served one. A path under no declared tree must
	 * still be a 404.</p>
	 */
	@Test
	@DisplayName("SH926: a path under no SPA context is still a 404")
	void anUndeclaredPathIsNotServedTheShell() throws Exception {
		int status = mockMvc.perform(get("/definitely-not-an-spa-context/at-all")).andReturn()
				.getResponse().getStatus();
		assertTrue(status == 404 || status == 401,
				"SH926: an undeclared path answered %d. If the SPA shell is now served for every path, the two assertions above prove nothing — they would pass with REACT_CONTEXTS empty."
						.formatted(status));
	}
}
