/*
 * Copyright (C) 2016-2023 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.shio.spring.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@Configuration
@EnableWebSecurity
@Profile("production")
@EnableMethodSecurity(securedEnabled = true)
@ComponentScan(basePackageClasses = ShCustomUserDetailsService.class)
public class ShSecurityConfigProduction {
	public static final String ERROR_PATH = "/error/**";
	@Autowired
	private UserDetailsService userDetailsService;

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, MvcRequestMatcher.Builder mvc,
									ShAuthenticationEntryPoint shAuthenticationEntryPoint) throws Exception {
		http.headers(header -> header.frameOptions(
				frameOptions -> frameOptions.disable().cacheControl(HeadersConfigurer.CacheControlConfig::disable)));
		http.cors(Customizer.withDefaults());
		http.headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
		http.csrf(csrf -> csrf
						.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
						.ignoringRequestMatchers(
								mvc.pattern(ERROR_PATH),
								mvc.pattern("/logout"),
								AntPathRequestMatcher.antMatcher("/h2/**")));

			http.httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(shAuthenticationEntryPoint))
					.authorizeHttpRequests(authorizeRequests -> {
						authorizeRequests.requestMatchers(
								mvc.pattern(ERROR_PATH),
								mvc.pattern("/api/discovery"),
								mvc.pattern("/logout"),
								mvc.pattern("/index.html"),
								mvc.pattern("/welcome/**"),
								mvc.pattern("/sites/**"),
								mvc.pattern("/__tur/**"),
								mvc.pattern("/graphql/**"),
								mvc.pattern("/"),
								AntPathRequestMatcher.antMatcher("/assets/**"),
								mvc.pattern("/swagger-resources/**"),
								mvc.pattern("/thirdparty/**"),
								mvc.pattern("/fonts/**"),
								AntPathRequestMatcher.antMatcher("/favicon.ico"),
								AntPathRequestMatcher.antMatcher("/*.png"),
								AntPathRequestMatcher.antMatcher("/manifest.json"),
								mvc.pattern("/browserconfig.xml"),
								mvc.pattern("/console/**")).permitAll();
						authorizeRequests.anyRequest().authenticated();

					});
		return http.build();
	}

	@Bean
	WebSecurityCustomizer webSecurityCustomizer(MvcRequestMatcher.Builder mvc) {
		return web ->
				web.httpFirewall(allowUrlEncodedSlaturHttpFirewall()).ignoring().requestMatchers(mvc.pattern("/h2/**"));
	}

	@Scope("prototype")
	@Bean
	MvcRequestMatcher.Builder mvc(HandlerMappingIntrospector handlerMappingIntrospector) {
		return new MvcRequestMatcher.Builder(handlerMappingIntrospector);
	}

	@Autowired
	public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
		auth.userDetailsService(userDetailsService);
	}

	@Bean(name = "passwordEncoder")
	PasswordEncoder passwordencoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	HttpFirewall allowUrlEncodedSlaturHttpFirewall() {
		// Allow double slash in URL
		StrictHttpFirewall firewall = new StrictHttpFirewall();
		firewall.setAllowUrlEncodedSlash(true);
		return firewall;
	}
	@Bean
	public DefaultWebSecurityExpressionHandler customWebSecurityExpressionHandler() {
        return new DefaultWebSecurityExpressionHandler();
	}
}
