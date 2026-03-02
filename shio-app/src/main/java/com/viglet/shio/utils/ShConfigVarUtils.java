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
package com.viglet.shio.utils;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.system.ShConfigVar;
import com.viglet.shio.persistence.repository.system.ShConfigVarRepository;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShConfigVarUtils {

	private final ShConfigVarRepository shConfigVarRepository;

	public ShConfigVarUtils(ShConfigVarRepository shConfigVarRepository) {
		this.shConfigVarRepository = shConfigVarRepository;
	}

	public Map<String, String> getVariablesFromPath(String path) {

		Map<String, String> configVarMap = new HashMap<>();

		for (ShConfigVar shConfigVar : shConfigVarRepository.findByPath(path))
			configVarMap.put(shConfigVar.getName(), shConfigVar.getValue());

		return configVarMap;
	}
}
