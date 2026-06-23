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
package com.viglet.shio;

import java.io.IOException;
import java.security.Principal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
/**
 * @author Alexandre Oliveira
 */
@Controller
public class ShIndexContext {

	@GetMapping("/")
	public void index(HttpServletRequest request, HttpServletResponse response, final Principal principal)
			throws IOException {
		if (principal != null) {
			response.sendRedirect("/console");
		} else {
			response.sendRedirect("/login");
		}

	}

	/**
	 * Tenant-prefixed SPA routes ({@code /<tenant>/console/**}). The non-tenant
	 * React contexts (/login, /setup, /console and their sub-paths) are served by
	 * {@code ShStaticResourceConfiguration}'s view controllers + resource handlers
	 * (Turing parity); only the tenant-segment variant — which a static pattern
	 * can't express as cleanly — is forwarded here.
	 */
	@GetMapping({
			"/*/console",
			"/*/console/**"
	})
	public String spa() {
		return "forward:/index.html";
	}
}
