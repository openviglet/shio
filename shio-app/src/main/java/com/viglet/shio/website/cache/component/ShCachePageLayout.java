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

import com.viglet.shio.website.ShSitesRegion;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.website.component.ShSitesPageLayout;
import com.viglet.shio.website.nashorn.ShNashornEngineProcess;

/**
 * @author Alexandre Oliveira
 */

@Slf4j
@Component
public class ShCachePageLayout {
	private ShSitesRegion shSitesRegion;
	private ShNashornEngineProcess shNashornEngineProcess;

	public ShCachePageLayout(ShSitesRegion shSitesRegion, ShNashornEngineProcess shNashornEngineProcess) {
		this.shSitesRegion = shSitesRegion;
		this.shNashornEngineProcess = shNashornEngineProcess;
	}

	public String cache(ShSitesPageLayout shSitesPageLayout, HttpServletRequest request, ShSite shSite,
			String mimeType) {
		if (shSitesPageLayout.getId() != null) {
			if (log.isDebugEnabled()) {
				log.debug(String.format("ShCachePageLayout.cache Key: %s %s", shSitesPageLayout.getId(),
						shSitesPageLayout.getPageCacheKey()));
			}
			try {
				Object renderPageLayout = shNashornEngineProcess.render(
						String.format("Page Layout: %s", shSitesPageLayout.getId()),
						shSitesPageLayout.getJavascriptCode(), shSitesPageLayout.getHTML(), request,
						shSitesPageLayout.getShContent());
				if (renderPageLayout != null) {
					Document documentRegion = shSitesRegion.shRegionFactory(shSitesPageLayout,
							renderPageLayout.toString(), shSite, mimeType, request);
					if (documentRegion != null) {
						return documentRegion.html();
					} else if (log.isDebugEnabled()) {
						log.debug("Region is null");
					}
				} else if (log.isDebugEnabled()) {
					log.debug("Render PageLayout is null");
				}

			} catch (Exception e) {
				log.error("ShCachePageLayout Exception: ", e);
			}
		}
		if (log.isDebugEnabled()) {
			log.debug("Page Layout Id is null");
		}
		return null;
	}
}
