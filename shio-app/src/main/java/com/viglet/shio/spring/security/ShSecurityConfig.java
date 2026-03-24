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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;

/**
 * @author Alexandre Oliveira
 */
@Configuration
@EnableWebSecurity
public class ShSecurityConfig {

	private final ShAuthenticationEntryPoint shAuthenticationEntryPoint;

	public ShSecurityConfig(ShAuthenticationEntryPoint shAuthenticationEntryPoint) {
		this.shAuthenticationEntryPoint = shAuthenticationEntryPoint;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.headers(headers -> headers
				.frameOptions(frame -> frame.sameOrigin())
			)
			.securityContext(context -> context
				.requireExplicitSave(false)
			)
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
			)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(
					"/error/**",
					"/logout",
					"/index.html",
					"/welcome", "/welcome/**",
					"/sites/**",
					"/__tur/**",
					"/",
					"/assets/**",
					"/thirdparty/**",
					"/fonts/**",
					"/favicon.ico",
					"/*.png",
					"/manifest.json",
					"/browserconfig.xml",
					"/content", "/content/**",
					"/preview", "/preview/**",
					"/media", "/media/**",
					"/css/**",
					"/js/**",
					"/img/**",
					"/store/**",
					"/file_source/**",
					"/template/**",
					"/vendor/**",
					"/svg/**"
				).permitAll()
				.anyRequest().authenticated()
			)
			.httpBasic(basic -> basic
				.authenticationEntryPoint(shAuthenticationEntryPoint)
			)
			.cors(cors -> {})
			.csrf(csrf -> csrf
				.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
				.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
			)
			.addFilterAfter(new ShCsrfHeaderFilter(), CsrfFilter.class);

		return http.build();
	}

	@Bean
	public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
		StrictHttpFirewall firewall = new StrictHttpFirewall();
		firewall.setAllowUrlEncodedSlash(true);
		return firewall;
	}
}
