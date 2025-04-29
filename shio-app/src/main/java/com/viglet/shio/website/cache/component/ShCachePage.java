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

import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.impl.ShPostAttrImpl;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.website.ShSitesContextComponent;
import com.viglet.shio.website.ShSitesContextURL;
import com.viglet.shio.website.component.ShSitesPage;
import com.viglet.shio.website.component.ShSitesPageLayout;
import com.viglet.shio.website.utils.ShSitesPostUtils;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShCachePage {
	private static final Log logger = LogFactory.getLog(ShCachePage.class);
	@Autowired
	private ShSitesContextComponent shSitesContextComponent;
	@Autowired
	private ShSitesPage shSitesPage;
	@Autowired
	private ShObjectRepository shObjectRepository;
	@Autowired
	private ShSiteRepository shSiteRepository;
	@Autowired
	private ShCacheObject shCacheObject;
	@Autowired
	private ShSitesPostUtils shSitesPostUtils;

	@Cacheable(value = "page", key = "{#shSitesContextURL.getInfo().getObjectId(), #shSitesContextURL.getInfo().getContextURLOriginal()}", sync = true)
	public ShCachePageBean cache(ShSitesContextURL shSitesContextURL) {

		ShCachePageBean shCachePageBean = new ShCachePageBean();
		

		if (logger.isDebugEnabled())
			logger.debug("Creating the page cache of id: " + shSitesContextURL.getInfo().getObjectId() + " and URL "
					+ shSitesContextURL.getInfo().getContextURLOriginal());
		shCacheObject.updateCache(shSitesContextURL.getInfo().getObjectId(), shSitesContextURL);

		ShSitesPageLayout shSitesPageLayout = new ShSitesPageLayout();
		shSitesPageLayout.setPageCacheKey(shSitesContextURL.getInfo().getContextURLOriginal());
		
		ShSite shSite = shSiteRepository.findById(shSitesContextURL.getInfo().getSiteId()).orElse(null);
		
		String mimeType = updatedMimeType(shSitesContextURL, shCachePageBean, shSitesPageLayout, shSite);

		setContentType(shCachePageBean, mimeType);

		setBody(shSitesContextURL, shCachePageBean, mimeType, shSite, shSitesPageLayout);
		return shCachePageBean;
	}

	private String updatedMimeType(ShSitesContextURL shSitesContextURL, ShCachePageBean shCachePageBean,
			ShSitesPageLayout shSitesPageLayout, ShSite shSite) {
		ShObjectImpl shObject = shObjectRepository.findById(shSitesContextURL.getInfo().getObjectId()).orElse(null);
		String mimeType = "html";
		
		if (shObject instanceof ShFolder) {
			if (logger.isDebugEnabled())
				logger.debug("Is Folder");
			mimeType = setFolderInfo(shSitesContextURL, mimeType, shSite, shSitesPageLayout);

		} else if (shObject instanceof ShPost) {
			if (logger.isDebugEnabled())
				logger.debug("Is Post");
			mimeType = setPostInfo(shSitesContextURL, shCachePageBean, mimeType, shObject, shSite, shSitesPageLayout);
		}
		return mimeType;
	}

	private String setPostInfo(ShSitesContextURL shSitesContextURL, ShCachePageBean shCachePageBean, String mimeType,
			ShObjectImpl shObject, ShSite shSite, ShSitesPageLayout shSitesPageLayout) {
		mimeType = shSitesPage.shPostPage(shSitesPageLayout, shSite, shSitesContextURL, mimeType);

		ShPost shPost = (ShPost) shObject;
		if (shSitesPostUtils.isFolderIndex(shPost)) {		
			String format = shSitesContextURL.getInfo().getShFormat();
			Map<String, ShPostAttr> shFolderIndexMap = shSitesPostUtils.postToMap(shPost);
			setExpirationDate(shCachePageBean, shFolderIndexMap);
			mimeType = contentTypeByFormat(shSitesContextURL, mimeType, format, shFolderIndexMap);

		} else {
			setCustomExpirationDate(shCachePageBean, shSitesPageLayout);
		}
		return mimeType;
	}

	private String contentTypeByFormat(ShSitesContextURL shSitesContextURL, String mimeType, String format,
			Map<String, ShPostAttr> shFolderIndexMap) {
		// Formats
		if (format.equalsIgnoreCase("default")) {
			if (shSitesContextURL.getInfo().getContextURL().endsWith(".json"))
				mimeType = "json";
		} else {
			ShPostAttrImpl shPostAttrFormats = shFolderIndexMap.get("FORMATS");
			List<Map<String, ShPostAttr>> shPostAttrFormatList = shSitesPostUtils
					.relationToMap(shPostAttrFormats);
			if (shPostAttrFormatList != null) {
				for (Map<String, ShPostAttr> shPostAttrFormat : shPostAttrFormatList) {
					if (shPostAttrFormat.get("NAME").getStrValue().equals(format))
						mimeType = shPostAttrFormat.get("MIME_TYPE").getStrValue();
				}
			}
		}
		return mimeType;
	}

	private void setCustomExpirationDate(ShCachePageBean shCachePageBean, ShSitesPageLayout shSitesPageLayout) {
		if (shSitesPageLayout.getCacheTTL() != null) {
			Calendar expirationDate = Calendar.getInstance();
			expirationDate.add(Calendar.MINUTE, shSitesPageLayout.getCacheTTL());
			shCachePageBean.setExpirationDate(expirationDate.getTime());
		}
	}

	private void setExpirationDate(ShCachePageBean shCachePageBean, Map<String, ShPostAttr> shFolderIndexMap) {
		// TTL
		ShPostAttrImpl shPostAttrCacheTTL = shFolderIndexMap.get("CACHE_TTL");
		if (shPostAttrCacheTTL != null && shPostAttrCacheTTL.getStrValue() != null) {
			int minutes = Integer.parseInt(shPostAttrCacheTTL.getStrValue());

			Calendar expirationDate = Calendar.getInstance();
			expirationDate.add(Calendar.MINUTE, minutes);
			shCachePageBean.setExpirationDate(expirationDate.getTime());
		}
	}

	private String setFolderInfo(ShSitesContextURL shSitesContextURL, String mimeType, ShSite shSite,
			ShSitesPageLayout shSitesPageLayout) {
		shSitesPage.shFolderPage(shSitesPageLayout, shSite, shSitesContextURL);
		if (shSitesContextURL.getInfo().getContextURL().endsWith(".json"))
			mimeType = "json";
		return mimeType;
	}

	private void setBody(ShSitesContextURL shSitesContextURL, ShCachePageBean shCachePageBean, String mimeType,
			ShSite shSite, ShSitesPageLayout shSitesPageLayout) {
		String shPageLayoutHTML = null;
		try {
			shPageLayoutHTML = shSitesContextComponent.shPageLayoutFactory(shSitesPageLayout,
					shSitesContextURL.getRequest(), shSite, mimeType);
		} catch (Exception e) {
			logger.error("ShCachePage Error:", e);
		}
		shCachePageBean.setBody(shPageLayoutHTML);
	}

	private void setContentType(ShCachePageBean shCachePageBean, String mimeType) {
		if (mimeType.equals("json"))
			shCachePageBean.setContentType(MediaType.APPLICATION_JSON_VALUE);
		else if (mimeType.equals("xml"))
			shCachePageBean.setContentType(MediaType.APPLICATION_XML_VALUE);
		else
			shCachePageBean.setContentType(MediaType.TEXT_HTML_VALUE);
	}

}