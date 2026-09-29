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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.transaction.Transactional;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonView;
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.persistence.dto.auth.ShGroupDTO;
import com.viglet.shio.persistence.model.auth.ShGroup;
import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.repository.auth.ShGroupRepository;
import com.viglet.shio.persistence.repository.auth.ShRoleRepository;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/group")
@Tag( name = "Group", description = "Group API")
public class ShGroupAPI {

	private final ShGroupRepository shGroupRepository;
	private final ShUserRepository shUserRepository;
	private final ShRoleRepository shRoleRepository;

	public ShGroupAPI(ShGroupRepository shGroupRepository, ShUserRepository shUserRepository,
			ShRoleRepository shRoleRepository) {
		this.shGroupRepository = shGroupRepository;
		this.shUserRepository = shUserRepository;
		this.shRoleRepository = shRoleRepository;
	}

	/** SH1206 &mdash; one role a group grants, as the console lists it: no back-reference to its groups. */
	public record ShGroupRole(String id, String name, String description) {
	}

	/**
	 * SH1206 &mdash; <b>what a group's members may do.</b>
	 *
	 * <p>The group read fills {@code shUsers} and not {@code shRoles}: the relation is owned by the
	 * role ({@code ShRole.shGroups}) and the group has no getter for its side, so a group's page could
	 * say who is in it and not what that grants. This reads the roles that name the group, sorted by
	 * name, as a small record rather than the entity, so the answer carries no cycle back to the
	 * groups and no lazy collection outside a transaction. It changes nothing a group read returns.</p>
	 */
	@GetMapping("/{id}/roles")
	public List<ShGroupRole> shGroupRoles(@PathVariable String id) {
		ShGroup shGroup = shGroupRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"No group has id " + id + ". GET /api/v2/group lists the groups and their ids."));
		return shRoleRepository.findByShGroupsIn(List.of(shGroup)).stream()
				.sorted(Comparator.comparing(role -> role.getName() == null ? "" : role.getName()))
				.map(role -> new ShGroupRole(role.getId(), role.getName(), role.getDescription()))
				.toList();
	}

	@GetMapping
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public List<ShGroup> shGroupList() {
		return shGroupRepository.findAll();
	}

	@GetMapping("/{id}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShGroup shGroupEdit(@PathVariable String id) {
		ShGroup shGroup = null;
		Optional<ShGroup> shGroupOptional = shGroupRepository.findById(id);
		if (shGroupOptional.isPresent()) {
			shGroup = shGroupOptional.get();
			List<ShGroup> shGroups = new ArrayList<>();
			shGroups.add(shGroup);
			shGroup.setShUsers(shUserRepository.findByShGroupsIn(shGroups));
		}
		return shGroup;
	}

	/**
	 * SH924 &mdash; <b>the path names the row, and it did not used to.</b>
	 *
	 * <p>This read the id out of the request body and ignored its own <code>{id}</code>, so
	 * <code>PUT /v2/group/A</code> carrying <code>{"id": "B"}</code> updated <b>B</b>. The same
	 * line was the create defect's second door: a body with a blank id saved a blank-keyed row from
	 * here too, on a request that looks like an update.</p>
	 *
	 * <p>The resource a request addresses is the one its URL names. Taking the id from the path
	 * closes both, and costs nothing for the console, which has always sent the two in agreement.</p>
	 */
	@PutMapping("/{id}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShGroupDTO shGroupUpdate(@PathVariable String id, @RequestBody ShGroupDTO shGroupDTO) {
		ShGroup shGroup = shGroupDTO.toEntity();
		shGroup.setId(id);
		shGroupDTO.setId(id);
		shGroupRepository.save(shGroup);
		Optional<ShGroup> shGroupOptional = shGroupRepository.findById(shGroup.getId());
		if (shGroupOptional.isPresent()) {
			var shGroupRepos = shGroupOptional.get();
			List<ShGroup> shGroups = new ArrayList<>();
			shGroups.add(shGroup);
			Set<ShUser> shUsers = shUserRepository.findByShGroupsIn(shGroups);
			for (ShUser shUser : shUsers) {
				shUser.getShGroups().remove(shGroupRepos);
				shUserRepository.saveAndFlush(shUser);
			}
			for (ShUser shUser : shGroup.getShUsers()) {
				ShUser shUserRepos = shUserRepository.findByUsername(shUser.getUsername());
				shUserRepos.getShGroups().add(shGroup);
				shUserRepository.saveAndFlush(shUserRepos);
			}
		}
		return shGroupDTO;
	}

	@Transactional
	@DeleteMapping("/{id}")
	public boolean shGroupDelete(@PathVariable String id) {
		shGroupRepository.delete(id);
		return true;
	}

	/**
	 * SH924 &mdash; a blank primary key means "not yet assigned", and the row it used to store was
	 * unreachable.
	 *
	 * <p>A create page sent <code>{id: "", name, description}</code> because its client-side type
	 * made the field required, and the empty string was the honest value for an id the server had
	 * not minted yet. This took it, persisted it verbatim and answered 200. The group then appeared
	 * in <code>GET /v2/group</code> and in the console list, and <b>nothing could address it</b>:
	 * <code>/v2/group/</code> does not match <code>/v2/group/{id}</code>, so it does not route, and
	 * every id-shaped path is a 404. Removing it took a database statement.</p>
	 *
	 * <p><b>Minting rather than refusing</b>, which is a decision and not an oversight. A teaching
	 * 400 under P6 would refuse exactly the shape the console sent until SH904 and the shape any
	 * other client wrote against it, turning a silent defect into a loud regression for callers who
	 * did nothing wrong. Minting makes every one of those requests correct, and there is nothing
	 * left to teach: the id was always the server's to assign. A non-blank id is still honoured, so
	 * a caller that assigns its own key &mdash; the shape every import path uses &mdash; is
	 * unaffected.</p>
	 */
	@PostMapping
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShGroupDTO shGroupAdd(@RequestBody ShGroupDTO shGroupDTO) {

		ShGroup shGroup = shGroupRepository.save(withMintableId(shGroupDTO.toEntity()));
		shGroupDTO.setId(shGroup.getId());
		return shGroupDTO;
	}

	/**
	 * A blank id becomes {@code null}, which is what {@code @VigletAssignableUuidGenerator} mints
	 * for. It honours an id that is explicitly set, and an empty string is "set" to it.
	 */
	private static ShGroup withMintableId(ShGroup shGroup) {
		if (shGroup.getId() != null && shGroup.getId().isBlank()) {
			shGroup.setId(null);
		}
		return shGroup;
	}

	@GetMapping("/model")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShGroup shGroupStructure() {
		return new ShGroup();

	}

}
