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
package com.viglet.shio.provider.exchange;

import java.util.List;

import com.viglet.shio.bean.provider.exchange.ShExchangeProviderInstanceBean;
import com.viglet.shio.persistence.model.provider.exchange.ShExchangeProviderInstance;
import com.viglet.shio.persistence.model.system.ShConfigVar;
import com.viglet.shio.persistence.repository.provider.exchange.ShExchangeProviderInstanceRepository;
import com.viglet.shio.persistence.repository.system.ShConfigVarRepository;
import com.viglet.shio.property.ShConfigProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Alexandre Oliveira
 *
 * @since 0.3.6
 */
@Component
public class ShExchangeProviderService {
	private final ShConfigProperties shConfigProperties;
	private final ShConfigVarRepository shConfigVarRepository;
	private final ShExchangeProviderInstanceRepository shExchangeProviderInstanceRepository;

	@Autowired
	public ShExchangeProviderService(ShConfigProperties shConfigProperties, ShConfigVarRepository shConfigVarRepository,
									 ShExchangeProviderInstanceRepository shExchangeProviderInstanceRepository) {
		this.shConfigProperties = shConfigProperties;
		this.shConfigVarRepository = shConfigVarRepository;
		this.shExchangeProviderInstanceRepository = shExchangeProviderInstanceRepository;
	}

	public ShExchangeProviderInstanceBean getShExchangeProviderInstanceBean(String providerId) {
		ShExchangeProviderInstance shExchangeProviderInstance = shExchangeProviderInstanceRepository.findById(providerId)
				.orElse(null);
		ShExchangeProviderInstanceBean shExchangeProviderInstanceBean = new ShExchangeProviderInstanceBean();
		if (shExchangeProviderInstance != null) {
			shExchangeProviderInstanceBean.setId(shExchangeProviderInstance.getId());
			shExchangeProviderInstanceBean.setName(shExchangeProviderInstance.getName());
			shExchangeProviderInstanceBean.setDescription(shExchangeProviderInstance.getDescription());
			shExchangeProviderInstanceBean.setVendor(shExchangeProviderInstance.getVendor());
			shExchangeProviderInstanceBean.setEnabled(shExchangeProviderInstance.getEnabled());

			String providerInstancePath = String.format(shConfigProperties.getExchange(),
					shExchangeProviderInstance.getId());

			List<ShConfigVar> shConfigVars = shConfigVarRepository.findByPath(providerInstancePath);

			for (ShConfigVar shConfigVar : shConfigVars) {
				shExchangeProviderInstanceBean.getProperties().put(shConfigVar.getName(), shConfigVar.getValue());
			}
		}

		return shExchangeProviderInstanceBean;
	}
}
