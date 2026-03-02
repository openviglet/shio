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
package com.viglet.shio.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;
import com.viglet.shio.spring.security.auth.IShAuthenticationFacade;

/**
 * User Utils
 * 
 * @author Alexandre Oliveira
 */
@Component
public class ShUserUtils {


	private final ShUserRepository shUserRepository;
	private final IShAuthenticationFacade authenticationFacade;
	private final ShUtils shUtils;
	
	private static final String DEFAULT_ANONYMOUS_USER = "anonymous";

	public ShUserUtils(ShUserRepository shUserRepository, IShAuthenticationFacade authenticationFacade, ShUtils shUtils) {
		this.shUserRepository = shUserRepository;
		this.authenticationFacade = authenticationFacade;
		this.shUtils = shUtils;
	}

	public String getCurrentUsername() {
		Authentication authentication = authenticationFacade.getAuthentication();
		return authentication != null && authentication.getName() != null
				? shUtils.sanitizedString(authentication.getName())
				: DEFAULT_ANONYMOUS_USER;

	}

	public boolean isValidUserAndPassword(String username, String password) {
		BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
		ShUser shUser = shUserRepository.findByUsername(username);
		return (shUser != null && passwordEncoder.matches(password, shUser.getPassword()));
	}
}
