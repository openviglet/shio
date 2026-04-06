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
package com.viglet.shio.spring.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * @author Alexandre Oliveira
 */
@Configuration
@EnableWebSecurity
public class ShSecurityConfig {

	private static final String LOGOUT_PATH = "/logout";
	private final ShAuthenticationEntryPoint shAuthenticationEntryPoint;
	PathPatternRequestMatcher.Builder mvc = PathPatternRequestMatcher.withDefaults();

	public ShSecurityConfig(ShAuthenticationEntryPoint shAuthenticationEntryPoint) {
		this.shAuthenticationEntryPoint = shAuthenticationEntryPoint;
	}

	@Bean
	@SuppressWarnings("java:S4502")
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.headers(headers -> headers
				.frameOptions(frame -> frame.sameOrigin()));
		http.cors(Customizer.withDefaults());
		http.securityContext(securityContext -> securityContext
				.securityContextRepository(new HttpSessionSecurityContextRepository()));
		http.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

		CookieCsrfTokenRepository csrfTokenRepository = new CookieCsrfTokenRepository();

		http.csrf(csrf -> csrf
				.csrfTokenRepository(csrfTokenRepository)
				.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
				.ignoringRequestMatchers(
						mvc.matcher("/error/**"),
						mvc.matcher(LOGOUT_PATH)))
				.addFilterAfter(new ShCsrfHeaderFilter(), BasicAuthenticationFilter.class);

		http.httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(shAuthenticationEntryPoint))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(
								mvc.matcher("/error/**"),
								mvc.matcher("/api/v2/csrf"),
								mvc.matcher(LOGOUT_PATH),
								mvc.matcher("/index.html"),
								mvc.matcher("/welcome/**"),
								mvc.matcher("/sites/**"),
								mvc.matcher("/__tur/**"),
								mvc.matcher("/"),
								mvc.matcher("/assets/**"),
								mvc.matcher("/thirdparty/**"),
								mvc.matcher("/fonts/**"),
								mvc.matcher("/favicon.ico"),
								mvc.matcher("/*.png"),
								mvc.matcher("/manifest.json"),
								mvc.matcher("/browserconfig.xml"),
								mvc.matcher("/content/**"),
								mvc.matcher("/preview/**"),
								mvc.matcher("/media/**"),
								mvc.matcher("/console/**"),
								mvc.matcher("/graphql/**"),
								mvc.matcher("/graphiql/**"),
								mvc.matcher("/css/**"),
								mvc.matcher("/js/**"),
								mvc.matcher("/img/**"),
								mvc.matcher("/store/**"),
								mvc.matcher("/file_source/**"),
								mvc.matcher("/template/**"),
								mvc.matcher("/vendor/**"),
								mvc.matcher("/svg/**")
						).permitAll()
						.anyRequest().authenticated());

		http.logout(logout -> logout
				.logoutRequestMatcher(mvc.matcher(HttpMethod.GET, LOGOUT_PATH))
				.invalidateHttpSession(true)
				.clearAuthentication(true)
				.deleteCookies("JSESSIONID", "XSRF-TOKEN")
				.logoutSuccessUrl("/"));

		return http.build();
	}

	@Bean
	public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
		StrictHttpFirewall firewall = new StrictHttpFirewall();
		firewall.setAllowUrlEncodedSlash(true);
		return firewall;
	}
}
