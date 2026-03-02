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
package com.viglet.shio.onstartup.provider.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.provider.auth.ShAuthSystemProviderVendor;
import com.viglet.shio.persistence.model.provider.auth.ShAuthProviderInstance;
import com.viglet.shio.persistence.model.system.ShConfigVar;
import com.viglet.shio.persistence.repository.provider.auth.ShAuthProviderInstanceRepository;
import com.viglet.shio.persistence.repository.provider.auth.ShAuthProviderVendorRepository;
import com.viglet.shio.persistence.repository.system.ShConfigVarRepository;
import com.viglet.shio.property.ShConfigProperties;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShAuthProviderInstanceOnStartup {
	private final ShConfigProperties shConfigProperties;
	private final ShConfigVarRepository shConfigVarRepository;
	private final ShAuthProviderInstanceRepository shAuthProviderInstanceRepository;
	private final ShAuthProviderVendorRepository shAuthProviderVendorRepository;

	@Autowired
	public ShAuthProviderInstanceOnStartup(ShConfigProperties shConfigProperties,
										   ShConfigVarRepository shConfigVarRepository,
										   ShAuthProviderInstanceRepository shAuthProviderInstanceRepository,
										   ShAuthProviderVendorRepository shAuthProviderVendorRepository) {
		this.shConfigProperties = shConfigProperties;
		this.shConfigVarRepository = shConfigVarRepository;
		this.shAuthProviderInstanceRepository = shAuthProviderInstanceRepository;
		this.shAuthProviderVendorRepository = shAuthProviderVendorRepository;
	}

	public void createDefaultRows() {

		if (shAuthProviderInstanceRepository.findAll().isEmpty()) {
			this.createEmbeddedInstance();
		}
	}

	private void createEmbeddedInstance() {
		ShAuthProviderInstance shAuthProviderInstance = new ShAuthProviderInstance();
		shAuthProviderInstance.setName("Shio Native");
		shAuthProviderInstance.setDescription("Shio Native Authentication and Authorization");
		shAuthProviderInstance.setVendor(shAuthProviderVendorRepository.findById(ShAuthSystemProviderVendor.NATIVE).orElse(null));
		shAuthProviderInstance.setEnabled(true);
	
		shAuthProviderInstanceRepository.save(shAuthProviderInstance);
	}

}
