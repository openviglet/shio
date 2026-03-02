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
package com.viglet.shio.website.cache.component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShCacheJavascript {
	private static final Logger logger = LogManager.getLogger(ShCacheJavascript.class);

	private final ResourceLoader resourceloader;

	public ShCacheJavascript(ResourceLoader resourceloader) {
		this.resourceloader = resourceloader;
	}

	@Cacheable(value = "javascript", sync = true)
	public StringBuilder shObjectJSFactory() {

		if (logger.isDebugEnabled())
			logger.debug("Executing shObjectJSFactory");

		StringBuilder shObjectJS = new StringBuilder();

		try (InputStream isrObjectJS = resourceloader.getResource("classpath:/js/server-side/shObject.js")
				.getInputStream();
				InputStream isrHandlebars = resourceloader.getResource("classpath:/js/server-side/handlebars.min.js")
						.getInputStream();) {
			shObjectJS.append(IOUtils.toString(isrObjectJS, StandardCharsets.UTF_8.name()));
			shObjectJS.append(IOUtils.toString(isrHandlebars, StandardCharsets.UTF_8.name()));
		} catch (IOException e) {
			logger.error(e);
		}

		return shObjectJS;
	}
}
