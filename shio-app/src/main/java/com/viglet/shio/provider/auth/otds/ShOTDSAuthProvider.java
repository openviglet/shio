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
package com.viglet.shio.provider.auth.otds;

import java.util.ArrayList;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.provider.auth.ShAuthenticationProvider;

/**
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Component
public class ShOTDSAuthProvider implements ShAuthenticationProvider {

	private final ShOTDSService shOTDSService;
	private final ObjectFactory<HttpSession> httpSessionFactory;

	private String providerId = null;

	@Autowired
	public ShOTDSAuthProvider(ShOTDSService shOTDSService, ObjectFactory<HttpSession> httpSessionFactory) {
		this.shOTDSService = shOTDSService;
		this.httpSessionFactory = httpSessionFactory;
	}

	@Override
	public void init(String providerId) {
		this.providerId = providerId;
		shOTDSService.init(providerId);
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {

		String name = authentication.getName();
		String password = authentication.getCredentials().toString();

		if (shOTDSService.isAuthorizedUser(name, password)) {
			HttpSession session = httpSessionFactory.getObject();
			session.setAttribute("authProvider", this.providerId);
			return new UsernamePasswordAuthenticationToken(name, password, new ArrayList<>());

		} else {
			return null;
		}

	}

	@Override
	public boolean supports(Class<?> authentication) {
		return authentication.equals(UsernamePasswordAuthenticationToken.class);
	}

	@Override
	public ShUser getShUser(String username) {
		return shOTDSService.getShUser(username);
	}
}