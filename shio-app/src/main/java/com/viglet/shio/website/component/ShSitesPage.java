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
package com.viglet.shio.website.component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.shio.bean.ShSitePostTypeLayout;
import com.viglet.shio.bean.ShSitePostTypeLayouts;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.utils.ShPostUtils;
import com.viglet.shio.utils.ShSiteUtils;
import com.viglet.shio.website.ShSitesContextComponent;
import com.viglet.shio.website.ShSitesContextURL;
import com.viglet.shio.website.cache.component.ShCachePreviewHtml;
import com.viglet.shio.website.utils.ShSitesFolderUtils;
import com.viglet.shio.website.utils.ShSitesPostUtils;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShSitesPage {
	private static final Log logger = LogFactory.getLog(ShSitesPage.class);

	private final ShPostRepository shPostRepository;
	private final ShFolderRepository shFolderRepository;
	private final ShSitesFolderUtils shSitesFolderUtils;
	private final ShPostUtils shPostUtils;
	private final ShSitesPostUtils shSitesPostUtils;
	private final ShSiteUtils shSiteUtils;
	private final ShSitesContextComponent shSitesContextComponent;
	private final ShCachePreviewHtml shCachePreviewHtml;
	private static final String THEME = "theme";
	private static final String POSTS = "posts";
	private static final String FOLDERS = "folders";
	private static final String POST = "post";
	private static final String SITE = "site";
	private static final String DEFAULT_FORMAT = "default";
	private static final String IN_CONTEXT_EDITING = "in-context-editing";

	
	public ShSitesPage(ShPostRepository shPostRepository, ShFolderRepository shFolderRepository,
			ShSitesFolderUtils shSitesFolderUtils, ShPostUtils shPostUtils, ShSitesPostUtils shSitesPostUtils,
			ShSiteUtils shSiteUtils, ShSitesContextComponent shSitesContextComponent,
			ShCachePreviewHtml shCachePreviewHtml) {
		this.shPostRepository = shPostRepository;
		this.shFolderRepository = shFolderRepository;
		this.shSitesFolderUtils = shSitesFolderUtils;
		this.shPostUtils = shPostUtils;
		this.shSitesPostUtils = shSitesPostUtils;
		this.shSiteUtils = shSiteUtils;
		this.shSitesContextComponent = shSitesContextComponent;
		this.shCachePreviewHtml = shCachePreviewHtml;
	}

	public String shPostPage(ShSitesPageLayout shSitesPageLayout, ShSite shSite, ShSitesContextURL shSitesContextURL,
			String mimeType) {

		ShPost shPostItem = shPostRepository.findById(shSitesContextURL.getInfo().getObjectId()).orElse(null);
		if (shPostItem != null) {
			if (shSitesPostUtils.isFolderIndex(shPostItem)) {				
				this.shFolderIndexPage(shSitesPageLayout, shSite, shSitesContextURL, shPostItem);
			} else {
				mimeType = postPage(shSitesPageLayout, shSite, shSitesContextURL, mimeType, shPostItem);
			}
		}

		return mimeType;
	}

	private String postPage(ShSitesPageLayout shSitesPageLayout, ShSite shSite, ShSitesContextURL shSitesContextURL,
			String mimeType, ShPost shPostItem) {
		JSONObject postTypeLayout = new JSONObject();

		ShSitePostTypeLayouts shSitePostTypeLayouts = this.getPostTypeLayouts(shSite, shPostItem, postTypeLayout);

		String pageLayoutName = null;
		String format = shSitesContextURL.getInfo().getShFormat();
		if (format == null)
			format = DEFAULT_FORMAT;

		for (ShSitePostTypeLayout shSitePostTypeLayout : shSitePostTypeLayouts) {
			if (shSitePostTypeLayout.getFormat().equals(format)) {
				pageLayoutName = shSitePostTypeLayout.getLayout();
				if (shSitePostTypeLayout.getCacheTTL() != null)
					shSitesPageLayout.setCacheTTL(shSitePostTypeLayout.getCacheTTL());

				if (shSitePostTypeLayout.getMimeType() != null)
					mimeType = shSitePostTypeLayout.getMimeType();
			}
		}

		this.setPageLayout(shSitesPageLayout, shSite, shPostItem, pageLayoutName);

		return mimeType;
	}

	private void setPageLayout(ShSitesPageLayout shSitesPageLayout, ShSite shSite, ShPost shPostItem,
			String pageLayoutName) {
		List<ShPost> shPostPageLayouts = shPostRepository.findByTitle(pageLayoutName);

		Map<String, ShPostAttr> shPostPageLayoutMap = getPageLayoutMap(shSite, shPostPageLayouts);
		if (shPostPageLayoutMap != null) {
			shSitesPageLayout.setId(shPostPageLayoutMap.get(ShSystemPostTypeAttr.ID).getStrValue());
			shSitesPageLayout.setHTML(shPostPageLayoutMap.get(ShSystemPostTypeAttr.HTML).getStrValue());
			shSitesPageLayout.setJavascriptCode(shPostPageLayoutMap.get(ShSystemPostTypeAttr.JAVASCRIPT).getStrValue());

			String shPostThemeId = shPostPageLayoutMap.get(ShSystemPostTypeAttr.THEME).getStrValue();
			Map<String, Object> shThemeAttrs = shSitesContextComponent.shThemeFactory(shPostThemeId);

			Map<String, Object> shSiteItemAttrs = shSiteUtils.toSystemMap(shSite);

			Map<String, Object> shPostItemAttrs = shSitesPostUtils.toSystemMap(shPostItem);

			shPostItemAttrs.put(THEME, shThemeAttrs);
			shPostItemAttrs.put(SITE, shSiteItemAttrs);

			shSitesPageLayout.setShContent(shPostItemAttrs);

		}
	}

	private Map<String, ShPostAttr> getPageLayoutMap(ShSite shSite, List<ShPost> shPostPageLayouts) {
		Map<String, ShPostAttr> shPostPageLayoutMap = null;

		if (shPostPageLayouts != null) {
			for (ShPost shPostPageLayout : shPostPageLayouts) {
				if (shPostUtils.getSite(shPostPageLayout).getId().equals(shSite.getId())) {
					shPostPageLayoutMap = shSitesPostUtils.postToMap(shPostPageLayout);
				}
			}
		}
		return shPostPageLayoutMap;
	}

	private ShSitePostTypeLayouts getPostTypeLayouts(ShSite shSite, ShPost shPostItem, JSONObject postTypeLayout) {
		ShSitePostTypeLayouts shSitePostTypeLayouts = null;
		if (shSite.getPostTypeLayout() != null)
			postTypeLayout = new JSONObject(shSite.getPostTypeLayout());

		ObjectMapper mapper = new ObjectMapper();

		try {
			shSitePostTypeLayouts = mapper.readValue(
					postTypeLayout.get(shPostItem.getShPostType().getName()).toString(), ShSitePostTypeLayouts.class);

		} catch (JsonProcessingException e) {
			logger.error(e);
		}
		return shSitePostTypeLayouts;
	}

	private void shFolderIndexPage(ShSitesPageLayout shSitesPageLayout, ShSite shSite, ShSitesContextURL shSitesContextURL,
			ShPost shPostItem) {
		 ShFolder shFolderItem = shSitesContextComponent.shFolderItemFactory(shPostItem);
		this.shFolderPage(shSitesPageLayout, shSite, shSitesContextURL, shFolderItem);
	}

	public void shFolderPage(ShSitesPageLayout shSitesPageLayout, ShSite shSite, ShSitesContextURL shSitesContextURL) {
		ShFolder shFolderItem = shFolderRepository.findById(shSitesContextURL.getInfo().getObjectId()).orElse(null);
		this.shFolderPage(shSitesPageLayout, shSite, shSitesContextURL, shFolderItem);
	}

	private void shFolderPage(ShSitesPageLayout shSitesPageLayout, ShSite shSite, ShSitesContextURL shSitesContextURL,
			ShFolder shFolderItem) {
		Map<String, Object> shPostItemAttrs = new HashMap<>();
		Map<String, Object> shFolderItemAttrs = shSitesFolderUtils.toSystemMap(shFolderItem);
		Map<String, ShPostAttr> shFolderPageLayoutMap = shSitesContextComponent
				.shFolderPageLayoutMapFactory(shFolderItem, shSite, shSitesContextURL.getInfo().getShFormat());
		if (shFolderPageLayoutMap != null) {
			String id = shFolderPageLayoutMap.get(ShSystemPostTypeAttr.ID).getStrValue();
			if (logger.isDebugEnabled())
				logger.debug("Found Folder PageLayout: " + id);
			shSitesPageLayout.setId(id);
			shSitesPageLayout.setHTML(shFolderPageLayoutMap.get(ShSystemPostTypeAttr.HTML).getStrValue());
			shSitesPageLayout
					.setJavascriptCode(shFolderPageLayoutMap.get(ShSystemPostTypeAttr.JAVASCRIPT).getStrValue());

			String shPostThemeId = shFolderPageLayoutMap.get(ShSystemPostTypeAttr.THEME).getStrValue();

			Map<String, Object> shThemeAttrs = shSitesContextComponent.shThemeFactory(shPostThemeId);
			
			shPostItemAttrs.put(THEME, shThemeAttrs);
			shPostItemAttrs.put(IN_CONTEXT_EDITING, shCachePreviewHtml.shPreviewMenuFactory());

			shFolderItemAttrs.put(THEME, shThemeAttrs);			
			shFolderItemAttrs.put(IN_CONTEXT_EDITING, shCachePreviewHtml.shPreviewMenuFactory());
			
			shFolderItemAttrs.put(POSTS, shSitesContextComponent.shPostItemsFactory(shFolderItem));
			shFolderItemAttrs.put(FOLDERS, shSitesContextComponent.shChildFolderItemsFactory(shFolderItem));
			shFolderItemAttrs.put(POST, shPostItemAttrs);
			shFolderItemAttrs.put(SITE, shSiteUtils.toSystemMap(shSite));

			shSitesPageLayout.setShContent(shFolderItemAttrs);
		}
		else {
			if (logger.isDebugEnabled())
				logger.debug("Not Found Folder PageLayout");
		}
	}
}
