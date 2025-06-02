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
package com.viglet.shio.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 * @since 0.3.0
 */
@RestController
@RequestMapping("/api/v2")
@Tag( name = "Heartbeat", description = "Heartbeat")
public class ShAPI {

	private final ShAPIBean shAPIBean;

	@Autowired
	public ShAPI(ShAPIBean shAPIBean) {
		this.shAPIBean = shAPIBean;
	}

	@GetMapping
	public ShAPIBean shApiInfo() {

		shAPIBean.setProduct("Viglet Shio CMS");

		return shAPIBean;
	}
}
