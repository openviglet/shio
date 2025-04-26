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

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.website.ShSitesContextComponent;
import com.viglet.shio.website.component.ShSitesPageLayout;
import com.viglet.shio.website.utils.ShSitesPostUtils;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShCacheRegion {
	static final Logger logger = LogManager.getLogger(ShCacheRegion.class.getName());
	@Autowired
	private ShSitesPostUtils shSitesPostUtils;
	@Autowired
	private ShSitesContextComponent shSitesContextComponent;

	@Cacheable(value = "region", key = "{#root.methodName, #regionName, #shSite.getId()}", sync = true)
	public String templateScopeCache(String regionName, ShSitesPageLayout shSitesPageLayout, ShSite shSite,
			StringBuilder shObjectJS, String mimeType, HttpServletRequest request) {
		return shSitesContextComponent.regionProcess(regionName, shSitesPageLayout, shSite, mimeType,
				request);
	}

	@Cacheable(value = "region", key = "{#root.methodName, #regionName, #siteId}", sync = true)
	public boolean isCached(String regionName, String siteId) {
		ShPost shRegion = shSitesContextComponent.getRegion(regionName, siteId);
		if (shRegion != null) {
			Map<String, ShPostAttr> shRegionPostMap = shSitesPostUtils.postToMap(shRegion);
			if (shRegionPostMap.get(ShSystemPostTypeAttr.CACHED) != null && shRegionPostMap.get(ShSystemPostTypeAttr.CACHED).getStrValue() != null) {				
				if (logger.isDebugEnabled()) 
					logger.debug("Region must be cached");
				return shRegionPostMap.get(ShSystemPostTypeAttr.CACHED).getStrValue().equals("yes");
			}
		}
		if (logger.isDebugEnabled()) 
			logger.debug("Region should not be cached");

		return false;

	}

}
