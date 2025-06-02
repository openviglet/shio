/*
 * Copyright (C) 2016-2020 Alexandre Oliveira <alexandre.oliveira@viglet.com> 
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

import com.viglet.shio.persistence.model.provider.auth.ShAuthProviderVendor;
import com.viglet.shio.persistence.repository.provider.auth.ShAuthProviderVendorRepository;
import com.viglet.shio.provider.auth.ShAuthSystemProviderVendor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShAuthProviderVendorOnStartup {

	private final ShAuthProviderVendorRepository shAuthProviderVendorRepository;

	@Autowired
	public ShAuthProviderVendorOnStartup(ShAuthProviderVendorRepository shAuthProviderVendorRepository) {
		this.shAuthProviderVendorRepository = shAuthProviderVendorRepository;
	}

	public void createDefaultRows() {

		if (shAuthProviderVendorRepository.findAll().isEmpty()) {
			embedded();
			otds();
		}

	}
	private void embedded() {
		ShAuthProviderVendor shAuthProviderVendor = new ShAuthProviderVendor();
		shAuthProviderVendor.setId(ShAuthSystemProviderVendor.NATIVE);
		shAuthProviderVendor.setName("Shio Native");
		shAuthProviderVendor.setDescription("Shio Native Authentication and Authorization");
		shAuthProviderVendor.setClassName(null);
		shAuthProviderVendor.setConfigurationPage(null);

		shAuthProviderVendorRepository.save(shAuthProviderVendor);
	}
	private void otds() {
		ShAuthProviderVendor shAuthProviderVendor = new ShAuthProviderVendor();
		shAuthProviderVendor.setId(ShAuthSystemProviderVendor.OTDS);
		shAuthProviderVendor.setName("OpenText Directory Services");
		shAuthProviderVendor.setDescription("Sample Directory Services");
		shAuthProviderVendor.setClassName("com.viglet.shio.provider.auth.otds.ShOTDSAuthProvider");
		shAuthProviderVendor.setConfigurationPage("template/config/provider/auth/otds/otds-configuration.html");

		shAuthProviderVendorRepository.save(shAuthProviderVendor);
	}
}
