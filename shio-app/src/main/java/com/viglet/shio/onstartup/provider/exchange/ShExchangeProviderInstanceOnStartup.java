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
package com.viglet.shio.onstartup.provider.exchange;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.provider.exchange.ShExchangeProviderInstance;
import com.viglet.shio.persistence.model.system.ShConfigVar;
import com.viglet.shio.persistence.repository.provider.exchange.ShExchangeProviderInstanceRepository;
import com.viglet.shio.persistence.repository.provider.exchange.ShExchangeProviderVendorRepository;
import com.viglet.shio.persistence.repository.system.ShConfigVarRepository;
import com.viglet.shio.property.ShConfigProperties;
import com.viglet.shio.provider.exchange.ShExchangeSystemProviderVendor;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShExchangeProviderInstanceOnStartup {
	private final ShConfigProperties shConfigProperties;
	private static final String URL = "URL";
	private static final String USERNAME = "USERNAME";
	private static final String PASSWORD = "PASSWORD";

	private final ShConfigVarRepository shConfigVarRepository;
	private final ShExchangeProviderInstanceRepository shExchangeProviderInstanceRepository;
	private final ShExchangeProviderVendorRepository shExchangeProviderVendorRepository;

	@Autowired
	public ShExchangeProviderInstanceOnStartup(ShConfigProperties shConfigProperties,
											   ShConfigVarRepository shConfigVarRepository,
											   ShExchangeProviderInstanceRepository shExchangeProviderInstanceRepository,
											   ShExchangeProviderVendorRepository shExchangeProviderVendorRepository) {
		this.shConfigProperties = shConfigProperties;
		this.shConfigVarRepository = shConfigVarRepository;
		this.shExchangeProviderInstanceRepository = shExchangeProviderInstanceRepository;
		this.shExchangeProviderVendorRepository = shExchangeProviderVendorRepository;
	}

	public void createDefaultRows() {

		if (shExchangeProviderInstanceRepository.findAll().isEmpty()) {
			this.createOTCSInstance();
			this.createOTMMInstance();
		}
	}

	private void createOTCSInstance() {
		ShExchangeProviderInstance shExchangeProviderInstance = setProviderInstance("OpenText Content Services",
				"Sample of OTCS", ShExchangeSystemProviderVendor.OTCS, false);

		String providerInstance = String.format(shConfigProperties.getExchange(), shExchangeProviderInstance.getId());

		this.setConfigVar(providerInstance, "http://localhost/OTCS/cs.exe", "admin", "password");
	}

	private ShExchangeProviderInstance setProviderInstance(String name, String description, String provider,
			boolean isEnabled) {
		ShExchangeProviderInstance shExchangeProviderInstance = new ShExchangeProviderInstance();
		shExchangeProviderInstance.setName(name);
		shExchangeProviderInstance.setDescription(description);
		shExchangeProviderInstance.setVendor(shExchangeProviderVendorRepository.findById(provider).orElse(null));
		shExchangeProviderInstance.setEnabled(isEnabled);

		shExchangeProviderInstanceRepository.save(shExchangeProviderInstance);
		return shExchangeProviderInstance;
	}

	private void setConfigVar(String providerInstance, String url, String username, String password) {
		ShConfigVar shConfigVar = new ShConfigVar();
		shConfigVar.setPath(providerInstance);
		shConfigVar.setName(URL);
		shConfigVar.setValue(url);
		shConfigVarRepository.save(shConfigVar);

		shConfigVar = new ShConfigVar();
		shConfigVar.setPath(providerInstance);
		shConfigVar.setName(USERNAME);
		shConfigVar.setValue(username);
		shConfigVarRepository.save(shConfigVar);

		shConfigVar = new ShConfigVar();
		shConfigVar.setPath(providerInstance);
		shConfigVar.setName(PASSWORD);
		shConfigVar.setValue(password);
		shConfigVarRepository.save(shConfigVar);
	}

	private void createOTMMInstance() {
		ShExchangeProviderInstance shExchangeProviderInstance = setProviderInstance("OpenText Media Management",
				"Sample of OTMM", ShExchangeSystemProviderVendor.OTMM, false);

		String providerInstance = String.format(shConfigProperties.getExchange(), shExchangeProviderInstance.getId());

		this.setConfigVar(providerInstance, "http://localhost:11090", "admin", "password");
	}

}
