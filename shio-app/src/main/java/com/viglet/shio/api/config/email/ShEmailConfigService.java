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

import java.util.Map;

import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.system.ShConfigVar;
import com.viglet.shio.persistence.repository.system.ShConfigVarRepository;
import com.viglet.shio.utils.ShConfigVarUtils;

/**
 * Reads/writes the e-mail settings as {@code ShConfigVar} rows under the
 * {@code /email} path (the same path the legacy AngularJS console used).
 *
 * @author Alexandre Oliveira
 */
@Component
public class ShEmailConfigService {

	public static final String EMAIL_PATH = "/email";

	static final String HOST = "HOST";
	static final String PORT = "PORT";
	static final String USERNAME = "USERNAME";
	static final String PASSWORD = "PASSWORD";
	static final String ENABLED = "ENABLED";
	static final String TEST_CONNECTION = "TEST_CONNECTION";
	static final String SMTP_AUTH = "SMTP_AUTH";
	static final String TLS_ENABLED = "TLS_ENABLED";
	static final String TLS_REQUIRED = "TLS_REQUIRED";
	static final String SSL_ENABLED = "SSL_ENABLED";

	private final ShConfigVarRepository shConfigVarRepository;
	private final ShConfigVarUtils shConfigVarUtils;

	public ShEmailConfigService(ShConfigVarRepository shConfigVarRepository, ShConfigVarUtils shConfigVarUtils) {
		this.shConfigVarRepository = shConfigVarRepository;
		this.shConfigVarUtils = shConfigVarUtils;
	}

	public ShEmailConfigBean getEmailConfig() {
		Map<String, String> vars = shConfigVarUtils.getVariablesFromPath(EMAIL_PATH);

		ShEmailConfigBean bean = new ShEmailConfigBean();
		bean.setEnabled(Boolean.parseBoolean(vars.get(ENABLED)));
		bean.setHost(vars.getOrDefault(HOST, ""));
		bean.setPort(vars.getOrDefault(PORT, ""));
		bean.setUsername(vars.getOrDefault(USERNAME, ""));
		bean.setPassword(vars.getOrDefault(PASSWORD, ""));
		bean.setTestConnection(Boolean.parseBoolean(vars.get(TEST_CONNECTION)));
		bean.setSmtpAuth(Boolean.parseBoolean(vars.get(SMTP_AUTH)));
		bean.setTlsEnabled(Boolean.parseBoolean(vars.get(TLS_ENABLED)));
		bean.setTlsRequired(Boolean.parseBoolean(vars.get(TLS_REQUIRED)));
		bean.setSslEnabled(Boolean.parseBoolean(vars.get(SSL_ENABLED)));
		return bean;
	}

	public ShEmailConfigBean saveEmailConfig(ShEmailConfigBean bean) {
		upsert(ENABLED, Boolean.toString(bean.isEnabled()));
		upsert(HOST, bean.getHost());
		upsert(PORT, bean.getPort());
		upsert(USERNAME, bean.getUsername());
		upsert(PASSWORD, bean.getPassword());
		upsert(TEST_CONNECTION, Boolean.toString(bean.isTestConnection()));
		upsert(SMTP_AUTH, Boolean.toString(bean.isSmtpAuth()));
		upsert(TLS_ENABLED, Boolean.toString(bean.isTlsEnabled()));
		upsert(TLS_REQUIRED, Boolean.toString(bean.isTlsRequired()));
		upsert(SSL_ENABLED, Boolean.toString(bean.isSslEnabled()));
		return getEmailConfig();
	}

	private void upsert(String name, String value) {
		ShConfigVar shConfigVar = shConfigVarRepository.findByPathAndName(EMAIL_PATH, name);
		if (shConfigVar == null) {
			shConfigVar = new ShConfigVar();
			shConfigVar.setPath(EMAIL_PATH);
			shConfigVar.setName(name);
		}
		shConfigVar.setValue(value != null ? value : "");
		shConfigVarRepository.saveAndFlush(shConfigVar);
	}
}
