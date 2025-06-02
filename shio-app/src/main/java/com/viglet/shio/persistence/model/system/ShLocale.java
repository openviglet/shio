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
package com.viglet.shio.persistence.model.system;

import java.io.Serial;
import java.io.Serializable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * The persistent class for the ShLocale database table.
 * 
 * @author Alexandre Oliveira
 */
@Setter
@Getter
@Entity
@Table(name = "shLocale")
@NamedQuery(name = "ShLocale.findAll", query = "SELECT l FROM ShLocale l")
public class ShLocale implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@Column(unique = true, nullable = false, length = 5)
	private String initials;

	@Column(nullable = true, length = 255)
	private String en;

	@Column(nullable = true, length = 255)
	private String pt;

	public ShLocale() {

	}

	public ShLocale(String initials, String en, String pt) {
		setInitials(initials);
		setEn(en);
		setPt(pt);
	}

}
