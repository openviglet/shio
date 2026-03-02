/*
 * Copyright (C) 2016-2021 the original author or authors. 
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
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Alexandre Oliveira
 */
@Slf4j
@Component
public class ShCachePreviewHtml {
		private final ResourceLoader resourceloader;
	public ShCachePreviewHtml(ResourceLoader resourceloader) {
		this.resourceloader = resourceloader;
	}
	@Cacheable(value = "previewMenu", sync = true)
	public String shPreviewMenuFactory() {

		log.debug("Executing shPreviewMenuFactory");

		var html = StringUtils.EMPTY;

		try (var isrObjectJS = resourceloader.getResource("classpath:/preview/preview-menu.html").getInputStream()) {
			html = IOUtils.toString(isrObjectJS, StandardCharsets.UTF_8.name());
		} catch (IOException e) {
			log.error(e.getMessage(), e	);
		}

		return html;
	}

	@Cacheable(value = "previewRegion", sync = true)
	public String shPreviewRegionFactory() {

		log.debug("Executing shPreviewRegionFactory");

		var html = StringUtils.EMPTY;

		try (var isrObjectJS = resourceloader.getResource("classpath:/preview/preview-region.html").getInputStream()) {
			html = IOUtils.toString(isrObjectJS, StandardCharsets.UTF_8.name());
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}

		return html;
	}
}
