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
package com.viglet.shio.api.config.email;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * E-mail / SMTP settings console API (SH58). Stores the form as
 * {@code ShConfigVar} rows under the {@code /email} path.
 *
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/config/email")
@Tag(name = "Email Config", description = "Email / SMTP Settings API")
public class ShEmailConfigAPI {

	private final ShEmailConfigService shEmailConfigService;

	public ShEmailConfigAPI(ShEmailConfigService shEmailConfigService) {
		this.shEmailConfigService = shEmailConfigService;
	}

	@Operation(summary = "Get Email / SMTP settings")
	@GetMapping
	public ShEmailConfigBean shEmailConfig() {
		return shEmailConfigService.getEmailConfig();
	}

	@Operation(summary = "Update Email / SMTP settings")
	@PutMapping
	@Transactional
	public ShEmailConfigBean shEmailConfigUpdate(@RequestBody ShEmailConfigBean shEmailConfigBean) {
		return shEmailConfigService.saveEmailConfig(shEmailConfigBean);
	}
}
