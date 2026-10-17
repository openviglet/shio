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
package com.viglet.shio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.CharacterEncodingFilter;

import tools.jackson.databind.JacksonModule;
import tools.jackson.datatype.hibernate7.Hibernate7Module;

/**
 * Class that can be used to bootstrap and launch a Viglet Shio from a Java
 * main method.
 *
 * @author Alexandre Oliveira
 *
 **/

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class Shio {

	/**
	 * SH680 — {@code run(args)}, and the two characters are the whole defect.
	 *
	 * <p>This called {@code application.run()} with no arguments, so every {@code --key=value} on the
	 * command line was dropped — and dropped <b>silently</b>: no warning, no log line, and the
	 * application starts normally on the defaults. Measured before the fix by launching the jar with
	 * {@code --server.port=8099}: it came up on <b>2710</b>, and the word {@code server.port} appears
	 * zero times in the whole startup log.</p>
	 *
	 * <p>Found while verifying SH676, pointing an instance at a local catalogue with
	 * {@code --shio.marketplace.enabled=true}. It reported <i>"shio.marketplace.enabled is false"</i>.
	 * The property was right and the value was right, and nothing said the argument had been ignored,
	 * which is the defect: <b>the failure is indistinguishable from the feature not working.</b></p>
	 *
	 * <p>{@code java -jar app.jar --server.port=8080} is the most universal thing anyone does with a
	 * Boot jar, so this is wider than one flag: an operator who reaches for it got a running instance
	 * on the wrong port and no reason. The workaround that did work — environment variables, which
	 * bind through relaxed naming — is one nobody has cause to look for.</p>
	 *
	 * <p>{@code ShBootstrapArgsLintTest} is the guard, because no unit test naturally covers this: it
	 * is a property of the process boundary, and the argument-less form compiles, starts and serves.</p>
	 */
	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(Shio.class);
		application.run(args);
	}

	@Bean
	public FilterRegistrationBean<CharacterEncodingFilter> filterRegistrationBean() {
		FilterRegistrationBean<CharacterEncodingFilter> registrationBean = new FilterRegistrationBean<>();
		CharacterEncodingFilter characterEncodingFilter = new CharacterEncodingFilter();
		characterEncodingFilter.setForceEncoding(true);
		characterEncodingFilter.setEncoding("UTF-8");
		registrationBean.setFilter(characterEncodingFilter);
		return registrationBean;
	}

	@Bean
	JacksonModule hibernate7Module() {
		return new Hibernate7Module();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
