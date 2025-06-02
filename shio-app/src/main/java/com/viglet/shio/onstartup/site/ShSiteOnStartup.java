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
package com.viglet.shio.onstartup.site;

import java.net.MalformedURLException;
import java.net.URL;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.utils.ShSiteUtils;

/**
 * @author Alexandre Oliveira
 */
@Component
@Slf4j
public class ShSiteOnStartup {
	private final ShSiteRepository shSiteRepository;
	private final ShSiteUtils shSiteUtils;

	@Autowired
	public ShSiteOnStartup(ShSiteRepository shSiteRepository, ShSiteUtils shSiteUtils) {
		this.shSiteRepository = shSiteRepository;
		this.shSiteUtils = shSiteUtils;
	}

	public void createDefaultRows() {

		if (shSiteRepository.findAll().isEmpty()) {
			try {
				shSiteUtils.importSiteFromResourceOrURL("/import/sample-site.zip",
						new URL("https://github.com/ShioCMS/sample-site/archive/0.3.8.zip"), "sample-site-import");

				shSiteUtils.importSiteFromResourceOrURL("/import/stock-site.zip",
						new URL("https://github.com/ShioCMS/stock-site-import/archive/0.3.8.zip"),
						"sample-site-import");
			} catch (MalformedURLException e) {
				log.error(e.getMessage(), e);
			}
		}
	}


}
