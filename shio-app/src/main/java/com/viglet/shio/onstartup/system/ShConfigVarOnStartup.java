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
package com.viglet.shio.onstartup.system;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.system.ShConfigVar;
import com.viglet.shio.persistence.repository.system.ShConfigVarRepository;
import com.viglet.shio.property.ShConfigProperties;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShConfigVarOnStartup {
	
	public static final String FIRST_TIME_NAME = "FIRST_TIME";
	private final ShConfigProperties shConfigProperties;
	private final ShConfigVarRepository shConfigVarRepository;

	@Autowired
	public ShConfigVarOnStartup(ShConfigProperties shConfigProperties, ShConfigVarRepository shConfigVarRepository) {
		this.shConfigProperties = shConfigProperties;
		this.shConfigVarRepository = shConfigVarRepository;
	}

	public void createDefaultRows() {

		ShConfigVar shConfigVar = new ShConfigVar();

		if (!shConfigVarRepository.existsByPathAndName(shConfigProperties.getSystem(), FIRST_TIME_NAME)) {

			shConfigVar.setPath(shConfigProperties.getSystem());
			shConfigVar.setName(FIRST_TIME_NAME);
			shConfigVar.setValue("true");
			shConfigVarRepository.save(shConfigVar);

			shConfigVar = new ShConfigVar();
		
			shConfigVar.setPath("/email");
			shConfigVar.setName("HOST");
			shConfigVar.setValue("localhost");

			shConfigVarRepository.save(shConfigVar);
		}
	}

}
