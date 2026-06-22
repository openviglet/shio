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

import lombok.Getter;
import lombok.Setter;

/**
 * Structured view of the e-mail / SMTP settings stored as {@code ShConfigVar}
 * rows under the {@code /email} path. The console form (SH58) reads and writes
 * this bean instead of touching the key/value rows directly.
 *
 * @author Alexandre Oliveira
 */
@Setter
@Getter
public class ShEmailConfigBean {

	private boolean enabled = false;
	private String host = "";
	private String port = "";
	private String username = "";
	private String password = "";
	private boolean testConnection = false;
	private boolean smtpAuth = false;
	private boolean tlsEnabled = false;
	private boolean tlsRequired = false;
	private boolean sslEnabled = false;
}
