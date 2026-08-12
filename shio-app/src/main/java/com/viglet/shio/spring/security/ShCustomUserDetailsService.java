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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;

/**
 * The password login's authorities, resolved by the same rule every other login uses (SH681).
 *
 * <h2>Only an OAuth login could be an administrator</h2>
 *
 * <p>{@link ShAuthorityResolver} reads user → groups → roles and adds {@code ROLE_ADMIN} for anyone
 * in the {@code Administrator} group. Exactly two classes referenced it: {@code ShOAuth2UserService}
 * and {@code ShOidcUserService}. This one walked the <em>same tables</em> and built authorities from
 * {@code ShRole} names alone — no group rule, no {@code ROLE_ADMIN} — and on a fresh instance
 * {@code GET /api/v2/role} answers {@code []}, so a password-authenticated user carried <b>no role
 * authorities at all</b>.</p>
 *
 * <p>Every {@code hasRole("ADMIN")} gate was therefore unreachable from a local login: the SH219
 * blueprint reload, the SH22 tenant writes, and whatever is added next. Measured rather than
 * reasoned — {@code POST /api/v2/agent/blueprints/reload} answered <b>403</b> to the seeded
 * {@code admin}, over a console session and over Basic auth, while {@code /api/v2/user/current}
 * reported {@code "admin": true}. That endpoint exists to be called by an administrator and had not
 * been callable by one since SH219.</p>
 *
 * <p>Two things this was not. The OAuth path is not wrong — it works, which is why nobody noticed.
 * And {@code admin: true} is not wrong: the flag is group membership, which is correct. What was
 * missing is the step that turns membership into an authority.</p>
 *
 * <h2>One rule, three logins</h2>
 *
 * <p>The repair was a choice between wiring the resolver in here and seeding the {@code ShRole} rows
 * the group is meant to carry. This is the first, because the second leaves two sources of truth
 * about who is an administrator — the group and the table — free to disagree, and leaves every
 * existing instance broken until it migrates. Delegating means the rule cannot drift between
 * authentication paths, which is how it came to be true on two of three.</p>
 *
 * @author Alexandre Oliveira
 */
@Service("customUserDetailsService")
public class ShCustomUserDetailsService implements UserDetailsService {

	private final ShUserRepository shUserRepository;
	private final ShAuthorityResolver shAuthorityResolver;

	public ShCustomUserDetailsService(ShUserRepository shUserRepository,
			ShAuthorityResolver shAuthorityResolver) {
		this.shUserRepository = shUserRepository;
		this.shAuthorityResolver = shAuthorityResolver;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		ShUser shUser = shUserRepository.findByUsername(username);
		if (null == shUser) {
			throw new UsernameNotFoundException("No user present with username: " + username);
		}
		// The resolver's own shape: it adds into a set rather than returning one, because the OAuth
		// flows merge it with the authorities the provider already granted. A local login starts
		// empty, so what comes back is exactly user → groups → roles, plus ROLE_ADMIN for the group.
		Set<GrantedAuthority> authorities = new LinkedHashSet<>();
		shAuthorityResolver.resolve(username, authorities);
		List<String> roles = new ArrayList<>();
		for (GrantedAuthority authority : authorities) {
			roles.add(authority.getAuthority());
		}
		return new ShCustomUserDetails(shUser, roles);
	}

}
