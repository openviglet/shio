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
package com.viglet.shio.onstartup.user;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.auth.ShGroup;
import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.repository.auth.ShGroupRepository;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.post.type.ShSystemPostType;

/**
 * User OnStartup
 * 
 * @author Alexandre Oliveira
 */
@Component
public class ShUserOnStartup {
	private final PasswordEncoder passwordEncoder;
	private final ShUserRepository shUserRepository;
	private final ShPostTypeRepository shPostTypeRepository;
	private final ShGroupRepository shGroupRepository;

	@Autowired
	public ShUserOnStartup(PasswordEncoder passwordEncoder, ShUserRepository shUserRepository,
						   ShPostTypeRepository shPostTypeRepository, ShGroupRepository shGroupRepository) {
		this.passwordEncoder = passwordEncoder;
		this.shUserRepository = shUserRepository;
		this.shPostTypeRepository = shPostTypeRepository;
		this.shGroupRepository = shGroupRepository;
	}

	public void createDefaultRows() {

		if (shUserRepository.findAll().isEmpty()) {

			ShGroup shGroup = shGroupRepository.findByName("Administrator");

			Set<ShGroup> shGroups = new HashSet<>();
			shGroups.add(shGroup);

			ShUser shUser = setUser("admin@localhost.local", "Shio", "Administrator", "admin", "admin");

			shUser.setShGroups(shGroups);

			shUserRepository.save(shUser);

			shUser = setUser("sample@localhost.local", "Sample user", "Sample", "sample123", "sample");

			shUserRepository.save(shUser);
		}

	}

	private ShUser setUser(String email, String firstName, String lastName, String password, String username) {

		ShPostType shPostType = shPostTypeRepository.findByName(ShSystemPostType.TEXT);

		ShUser shUser = new ShUser();

		shUser.setEmail(email);
		shUser.setFirstName(firstName);
		shUser.setLastLogin(new Date());
		shUser.setLastName(lastName);
		shUser.setLastPostType(String.valueOf(shPostType.getId()));
		shUser.setLoginTimes(0);
		shUser.setPassword(passwordEncoder.encode(password));
		shUser.setRealm("default");
		shUser.setUsername(username);
		shUser.setEnabled(1);
		
		return shUser;
	}
}
