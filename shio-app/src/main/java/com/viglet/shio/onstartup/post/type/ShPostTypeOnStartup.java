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
package com.viglet.shio.onstartup.post.type;

import java.io.IOException;
import java.io.InputStreamReader;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.shio.exchange.ShExchange;
import com.viglet.shio.exchange.post.type.ShPostTypeImport;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShPostTypeOnStartup {

	@Autowired
	private ShPostTypeRepository shPostTypeRepository;
	@Autowired
	private ResourceLoader resourceloader;
	@Autowired
	private ShPostTypeImport shPostTypeImport;

	public void createDefaultRows() throws IOException {

		if (shPostTypeRepository.findAll().isEmpty()) {

			InputStreamReader isr = new InputStreamReader(
					resourceloader.getResource("classpath:/import/post-types.json").getInputStream());

			ObjectMapper mapper = new ObjectMapper();

			ShExchange shExchange = mapper.readValue(isr, ShExchange.class);
			shPostTypeImport.importPostType(shExchange, false);

		}

	}
}
