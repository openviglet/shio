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

import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
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
import com.viglet.shio.persistence.repository.auth.ShGroupRepository;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;
import com.viglet.shio.persistence.repository.provider.auth.ShAuthProviderInstanceRepository;
import com.viglet.shio.provider.auth.ShAuthSystemProviderVendor;
import com.viglet.shio.provider.auth.ShAuthenticationProvider;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping(value = "/api/v2/user", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "User", description = "User API")
public class ShUserAPI {
	private static final Log logger = LogFactory.getLog(ShUserAPI.class);
	private final ShUserRepository shUserRepository;
	private final ShGroupRepository shGroupRepository;
	private final ShAuthProviderInstanceRepository shAuthProviderInstanceRepository;
	private final ApplicationContext context;
	private final PasswordEncoder passwordEncoder;
	public ShUserAPI(ShUserRepository shUserRepository,
					 ShGroupRepository shGroupRepository,
					 ShAuthProviderInstanceRepository shAuthProviderInstanceRepository,
					 ApplicationContext context,
					 PasswordEncoder passwordEncoder) {
		this.shUserRepository = shUserRepository;
		this.shGroupRepository = shGroupRepository;
		this.shAuthProviderInstanceRepository = shAuthProviderInstanceRepository;
		this.context = context;
		this.passwordEncoder = passwordEncoder;
	}

	@GetMapping
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public List<ShUser> shUserList() {
		return shUserRepository.findAll();
	}

	private static final String ADMINISTRATOR = "Administrator";
	private static final String PREFERRED_USERNAME = "preferred_username";
	private static final String GIVEN_NAME = "given_name";
	private static final String FAMILY_NAME = "family_name";
	private static final String EMAIL = "email";

	@GetMapping("/current")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShCurrentUser shUserCurrent(HttpSession session) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken)) {
			if (authentication.getPrincipal() instanceof OAuth2User oAuthUser) {
				return oauth2User(authentication, oAuthUser);
			} else {
				return regularUser(session, authentication.getName());
			}
		}
		return null;
	}

	private String firstNonNull(OAuth2User user, String... keys) {
		for (String key : keys) {
			String value = user.getAttribute(key);
			if (value != null) return value;
		}
		return null;
	}

	private ShCurrentUser oauth2User(Authentication authentication, OAuth2User user) {
		String realm = (authentication instanceof OAuth2AuthenticationToken oauthToken)
				? oauthToken.getAuthorizedClientRegistrationId()
				: "oauth2";
		String username = firstNonNull(user, PREFERRED_USERNAME, "login", EMAIL);
		String email = firstNonNull(user, EMAIL);

		String firstName = user.getAttribute(GIVEN_NAME);
		String lastName = user.getAttribute(FAMILY_NAME);
		if (firstName == null && lastName == null) {
			String fullName = firstNonNull(user, "name");
			if (fullName != null) {
				int space = fullName.indexOf(' ');
				firstName = space > 0 ? fullName.substring(0, space) : fullName;
				lastName = space > 0 ? fullName.substring(space + 1) : null;
			}
		}

		ShUser shUser = shUserRepository.findByUsername(username);
		if (shUser == null) {
			shUser = new ShUser();
			shUser.setUsername(username);
			shUser.setFirstName(firstName);
			shUser.setLastName(lastName);
			shUser.setEmail(email);
			shUser.setRealm(realm);
			shUser.setEnabled(1);
			shUserRepository.save(shUser);
		} else {
			shUser.setFirstName(firstName);
			shUser.setLastName(lastName);
			shUser.setEmail(email);
			shUser.setRealm(realm);
			shUserRepository.save(shUser);
		}

		boolean isAdmin = isAdminUser(shUser);

		ShCurrentUser shCurrentUser = new ShCurrentUser();
		shCurrentUser.setUsername(username);
		shCurrentUser.setFirstName(firstName);
		shCurrentUser.setLastName(lastName);
		shCurrentUser.setEmail(email);
		shCurrentUser.setAdmin(isAdmin);
		return shCurrentUser;
	}

	private ShCurrentUser regularUser(HttpSession session, String currentUserName) {
		com.viglet.shio.persistence.model.provider.auth.ShAuthProviderInstance instance = this.getProviderInstance(session);
		ShUser shUser = this.getUserFromAuth(currentUserName, instance);
		boolean isAdmin = this.isAdminFromNative(shUser, instance);
		return setUser(isAdmin, shUser);
	}

	private boolean isAdminUser(ShUser shUser) {
		if (shUser != null) {
			Set<ShGroup> groups = shGroupRepository.findByShUsersIn(List.of(shUser));
			if (groups != null) {
				for (ShGroup shGroup : groups) {
					if (ADMINISTRATOR.equals(shGroup.getName())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private com.viglet.shio.persistence.model.provider.auth.ShAuthProviderInstance getProviderInstance(HttpSession session) {
		String providerId = (String) session.getAttribute("authProvider");
		com.viglet.shio.persistence.model.provider.auth.ShAuthProviderInstance instance = null;
		if (providerId != null)
			instance = shAuthProviderInstanceRepository.findById(providerId).orElse(null);
		return instance;
	}

	private ShUser getUserFromAuth(String currentUserName,
			com.viglet.shio.persistence.model.provider.auth.ShAuthProviderInstance instance) {
		ShUser shUser = null;
		if (isNotNativeAuth(instance)) {
			shUser = loadAuthProvider(currentUserName, instance, shUser);
		} else {
			shUser = shUserRepository.findByUsername(currentUserName);
			if (shUser != null) {
				shUser.setPassword(null);
			}
		}
		return shUser;
	}

	private boolean isAdminFromNative(ShUser shUser,
			com.viglet.shio.persistence.model.provider.auth.ShAuthProviderInstance instance) {
		if (!isNotNativeAuth(instance)) {
			return isAdminUser(shUser);
		}
		return false;
	}

	private boolean isNotNativeAuth(com.viglet.shio.persistence.model.provider.auth.ShAuthProviderInstance instance) {
		return instance != null && !instance.getVendor().getId().equals(ShAuthSystemProviderVendor.NATIVE);
	}

	private ShCurrentUser setUser(boolean isAdmin, ShUser shUser) {
		if (shUser != null) {
			ShCurrentUser shCurrentUser = new ShCurrentUser();
			shCurrentUser.setUsername(shUser.getUsername());
			shCurrentUser.setFirstName(shUser.getFirstName());
			shCurrentUser.setLastName(shUser.getLastName());
			shCurrentUser.setEmail(shUser.getEmail());
			shCurrentUser.setAdmin(isAdmin);
			return shCurrentUser;
		} else {
			return null;
		}
	}

	private ShUser loadAuthProvider(String currentUserName,
			com.viglet.shio.persistence.model.provider.auth.ShAuthProviderInstance instance, ShUser shUser) {
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
			shUser.setShGroups(shGroupRepository.findByShUsersIn(List.of(shUser)));
		}
		return shUser;
	}

	@PutMapping("/{username}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShUser shUserUpdate(@PathVariable String username, @RequestBody ShUserRequest shUser) {
		ShUser shUserEdit = shUserRepository.findByUsername(username);
		if (shUserEdit != null) {
			shUserEdit.setFirstName(shUser.firstName());
			shUserEdit.setLastName(shUser.lastName());
			shUserEdit.setEmail(shUser.email());
			shUserEdit.setShGroups(shUser.toShGroups());
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

	/**
	 * SH1334 — the password is encoded here, with the encoder sign-in checks against. It was saved as
	 * typed, so the account held a readable password and could never sign in with it.
	 */
	@PostMapping("/{username}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShUser shUserAdd(@PathVariable String username, @RequestBody ShUserRequest shUser) {
		ShUser shUserAdd = shUser.toNewShUser();
		if (shUserAdd.getPassword() != null && !shUserAdd.getPassword().isBlank()) {
			shUserAdd.setPassword(passwordEncoder.encode(shUserAdd.getPassword()));
		}
		shUserRepository.save(shUserAdd);

		return shUserAdd;
	}

	@GetMapping("/model")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShUser shUserStructure() {
		return new ShUser();

	}

}
