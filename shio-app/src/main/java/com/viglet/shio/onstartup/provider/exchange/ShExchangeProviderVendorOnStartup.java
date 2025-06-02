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

import com.viglet.shio.persistence.model.provider.exchange.ShExchangeProviderVendor;
import com.viglet.shio.persistence.repository.provider.exchange.ShExchangeProviderVendorRepository;
import com.viglet.shio.provider.exchange.ShExchangeSystemProviderVendor;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShExchangeProviderVendorOnStartup {

	private final ShExchangeProviderVendorRepository shExchangeProviderVendorRepository;

	@Autowired
	public ShExchangeProviderVendorOnStartup(ShExchangeProviderVendorRepository shExchangeProviderVendorRepository) {
		this.shExchangeProviderVendorRepository = shExchangeProviderVendorRepository;
	}

	public void createDefaultRows() {

		if (shExchangeProviderVendorRepository.findAll().isEmpty()) {

			ShExchangeProviderVendor shExchangeProviderVendor = new ShExchangeProviderVendor();
			shExchangeProviderVendor.setId(ShExchangeSystemProviderVendor.OTCS);
			shExchangeProviderVendor.setName("OpenText Content Services");
			shExchangeProviderVendor.setDescription("OpenText Content Services");
			shExchangeProviderVendor.setClassName("com.viglet.shio.provider.exchange.otcs.ShOTCSProvider");
			shExchangeProviderVendor.setConfigurationPage("template/config/provider/exchange/otcs/otcs-configuration.html");

			shExchangeProviderVendorRepository.save(shExchangeProviderVendor);

			shExchangeProviderVendor = new ShExchangeProviderVendor();
			shExchangeProviderVendor.setId(ShExchangeSystemProviderVendor.OTMM);
			shExchangeProviderVendor.setName("OpenText Media Management");
			shExchangeProviderVendor.setDescription("OpenText Media Management");
			shExchangeProviderVendor.setClassName("com.viglet.shio.provider.exchange.otmm.ShOTMMProvider");
			shExchangeProviderVendor.setConfigurationPage("template/config/provider/exchange/otmm/otmm-configuration.html");

			shExchangeProviderVendorRepository.save(shExchangeProviderVendor);
		}

	}
}
