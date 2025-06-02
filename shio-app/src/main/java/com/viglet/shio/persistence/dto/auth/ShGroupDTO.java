/*
 * Copyright (C) 2016-2021 the original author or authors. 
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
package com.viglet.shio.persistence.dto.auth;

import java.util.HashSet;
import java.util.Set;

import com.viglet.shio.persistence.model.auth.ShGroup;
import com.viglet.shio.persistence.model.auth.ShRole;
import com.viglet.shio.persistence.model.auth.ShUser;
import lombok.Getter;
import lombok.Setter;

/**
 * The DTO class for the ShGroup database table.
 * 
 * @author Alexandre Oliveira
 */
@Setter
@Getter
public class ShGroupDTO {
	private String id;

	private String name;

	private String description;

	private Set<ShRole> shRoles = new HashSet<>();

	private Set<ShUser> shUsers = new HashSet<>();

    public ShGroup toEntity() {
		ShGroup shGroup = new ShGroup();
		shGroup.setId(this.getId());
		shGroup.setName(this.getName());
		shGroup.setDescription(this.getDescription());
		shGroup.setShUsers(this.getShUsers());
		return shGroup;
	}
}