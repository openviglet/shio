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
import java.util.Optional;
import java.util.Set;

import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
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
import com.viglet.shio.persistence.dto.auth.ShGroupDTO;
import com.viglet.shio.persistence.model.auth.ShGroup;
import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.repository.auth.ShGroupRepository;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/group")
@Tag( name = "Group", description = "Group API")
public class ShGroupAPI {

	@Autowired
	private ShGroupRepository shGroupRepository;
	@Autowired
	private ShUserRepository shUserRepository;

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

	@PutMapping("/{id}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShGroupDTO shGroupUpdate(@PathVariable String id, @RequestBody ShGroupDTO shGroupDTO) {
		ShGroup shGroup = shGroupDTO.toEntity();
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

	@PostMapping
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShGroupDTO shGroupAdd(@RequestBody ShGroupDTO shGroupDTO) {

		ShGroup shGroup = shGroupRepository.save(shGroupDTO.toEntity());
		shGroupDTO.setId(shGroup.getId());
		return shGroupDTO;
	}

	@GetMapping("/model")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShGroup shGroupStructure() {
		return new ShGroup();

	}

}
