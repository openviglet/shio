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

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Forces the lazy CsrfToken to be loaded and exposes it via response header.
 *
 * @author Alexandre Oliveira
 */
public class ShCsrfHeaderFilter extends OncePerRequestFilter {
	private static final String EXPOSE_HEADERS = "Access-Control-Expose-Headers";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
		if (csrfToken != null) {
			// Force the deferred token to be materialized, triggering the cookie to be set
			String token = csrfToken.getToken();
			response.setHeader(csrfToken.getHeaderName(), token);

			String exposeHeaders = response.getHeader(EXPOSE_HEADERS);
			if (!StringUtils.hasText(exposeHeaders)) {
				response.setHeader(EXPOSE_HEADERS, csrfToken.getHeaderName());
			} else if (!exposeHeaders.contains(csrfToken.getHeaderName())) {
				response.setHeader(EXPOSE_HEADERS, exposeHeaders + ", " + csrfToken.getHeaderName());
			}
		}
		filterChain.doFilter(request, response);
	}
}
