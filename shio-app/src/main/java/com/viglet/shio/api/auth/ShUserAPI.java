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
package com.viglet.shio.api.auth;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.bean.ShCurrentUser;
import com.viglet.shio.persistence.model.auth.ShGroup;
import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.model.provider.auth.ShAuthProviderInstance;
import com.viglet.shio.persistence.repository.auth.ShGroupRepository;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;
import com.viglet.shio.persistence.repository.provider.auth.ShAuthProviderInstanceRepository;
import com.viglet.shio.provider.auth.ShAuthSystemProviderVendor;
import com.viglet.shio.provider.auth.ShAuthenticationProvider;
import com.viglet.shio.utils.ShUserUtils;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/user")
@Tag(name = "User", description = "User API")
public class ShUserAPI {
	private static final Log logger = LogFactory.getLog(ShUserAPI.class);
	private final PasswordEncoder passwordEncoder;
	private final ShUserRepository shUserRepository;
	private final ShGroupRepository shGroupRepository;
	private final ShAuthProviderInstanceRepository shAuthProviderInstanceRepository;
	private final ApplicationContext context;
	private final ShUserUtils shUserUtils;

	@Autowired
	public ShUserAPI(PasswordEncoder passwordEncoder, ShUserRepository shUserRepository,
					 ShGroupRepository shGroupRepository,
					 ShAuthProviderInstanceRepository shAuthProviderInstanceRepository,
					 ApplicationContext context, ShUserUtils shUserUtils) {
		this.passwordEncoder = passwordEncoder;
		this.shUserRepository = shUserRepository;
		this.shGroupRepository = shGroupRepository;
		this.shAuthProviderInstanceRepository = shAuthProviderInstanceRepository;
		this.context = context;
		this.shUserUtils = shUserUtils;
	}

	@GetMapping
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public List<ShUser> shUserList() {
		return shUserRepository.findAll();
	}

	@GetMapping("/current")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShCurrentUser shUserCurrent(HttpSession session) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof AnonymousAuthenticationToken)) {
			String currentUserName = shUserUtils.getCurrentUsername();
			ShAuthProviderInstance instance = this.getProviderInstance(session);
			ShUser shUser = this.getUserFromAuth(currentUserName, instance);
			boolean isAdmin = this.isAdminFromNative(shUser, instance);

			return setUser(isAdmin, shUser);
		}

		return null;
	}

	private ShAuthProviderInstance getProviderInstance(HttpSession session) {
		String providerId = (String) session.getAttribute("authProvider");
		ShAuthProviderInstance instance = null;
		if (providerId != null)
			instance = shAuthProviderInstanceRepository.findById(providerId).orElse(null);
		return instance;
	}

	private ShUser getUserFromAuth(String currentUserName, ShAuthProviderInstance instance) {
		ShUser shUser = null;
		if (isNotNativeAuth(instance)) {
			shUser = loadAuthProvider(currentUserName, instance, shUser);
		} else {
			shUser = shUserRepository.findByUsername(currentUserName);
			shUser.setPassword(null);

		}
		return shUser;
	}

	private boolean isAdminFromNative(ShUser shUser, ShAuthProviderInstance instance) {
		boolean isAdmin = false;
		if (!isNotNativeAuth(instance) && shUser != null && shUser.getShGroups() != null) {
			for (ShGroup shGroup : shUser.getShGroups()) {
				if (shGroup.getName().equals("Administrator"))
					isAdmin = true;
			}
		}
		return isAdmin;
	}

	private boolean isNotNativeAuth(ShAuthProviderInstance instance) {
		return instance != null && !instance.getVendor().getId().equals(ShAuthSystemProviderVendor.NATIVE);
	}

	private ShCurrentUser setUser(boolean isAdmin, ShUser shUser) {
		if (shUser != null) {
			ShCurrentUser shCurrentUser = new ShCurrentUser();
			shCurrentUser.setUsername(shUser.getUsername());
			shCurrentUser.setFirstName(shUser.getFirstName());
			shCurrentUser.setLastName(shUser.getLastName());
			shCurrentUser.setAdmin(isAdmin);
			return shCurrentUser;
		} else {
			return null;
		}
	}

	private ShUser loadAuthProvider(String currentUserName, ShAuthProviderInstance instance, ShUser shUser) {
		ShAuthenticationProvider shAuthenticationProvider;
		try {
			shAuthenticationProvider = (ShAuthenticationProvider) context
					.getBean(Class.forName(instance.getVendor().getClassName()));

			shAuthenticationProvider.init(instance.getId());

			shUser = shAuthenticationProvider.getShUser(currentUserName);
		} catch (BeansException e) {
			logger.error("shUserCurrent BeansException", e);
		} catch (ClassNotFoundException e) {
			logger.error("shUserCurrent ClassNotFoundException", e);
		}
		return shUser;
	}

	@GetMapping("/{username}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShUser shUserEdit(@PathVariable String username) {
		ShUser shUser = shUserRepository.findByUsername(username);
		if (shUser != null) {
			shUser.setPassword(null);
			List<ShUser> shUsers = new ArrayList<>();
			shUsers.add(shUser);
			shUser.setShGroups(shGroupRepository.findByShUsersIn(shUsers));
		}
		return shUser;
	}

	@PutMapping("/{username}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShUser shUserUpdate(@PathVariable String username, @RequestBody ShUser shUser) {
		ShUser shUserEdit = shUserRepository.findByUsername(username);
		if (shUserEdit != null) {
			shUserEdit.setFirstName(shUser.getFirstName());
			shUserEdit.setLastName(shUser.getLastName());
			shUserEdit.setEmail(shUser.getEmail());
			if (shUser.getPassword() != null && shUser.getPassword().trim().length() > 0) {
				shUserEdit.setPassword(passwordEncoder.encode(shUser.getPassword()));
			}
			shUserEdit.setShGroups(shUser.getShGroups());
			shUserRepository.save(shUserEdit);
		}
		return shUserEdit;
	}

	@Transactional
	@DeleteMapping("/{username}")
	public boolean shUserDelete(@PathVariable String username) {
		shUserRepository.delete(username);
		return true;
	}

	@PostMapping("/{username}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShUser shUserAdd(@PathVariable String username, @RequestBody ShUser shUser) {
		if (shUser.getPassword() != null && shUser.getPassword().trim().length() > 0) {
			shUser.setPassword(passwordEncoder.encode(shUser.getPassword()));
		}

		shUserRepository.save(shUser);

		return shUser;
	}

	@GetMapping("/model")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShUser shUserStructure() {
		return new ShUser();

	}

}
