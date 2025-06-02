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
package com.viglet.shio.website.utils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.shio.bean.ShSitePostTypeLayout;
import com.viglet.shio.bean.ShSitePostTypeLayouts;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.impl.ShPostAttrImpl;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.utils.ShPostUtils;
import com.viglet.shio.website.ShSitesContextURL;
import com.viglet.shio.website.ShSitesContextURLProcess;

/**
 * Page Layout Utils.
 *
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
@Component
public class ShSitesPageLayoutUtils {
	private static final Logger logger = LogManager.getLogger(ShSitesPageLayoutUtils.class);
	private static final String DEFAULT_FORMAT = "default";
	@Autowired
	private ShSitesPostUtils shSitesPostUtils;
	@Autowired
	private ShPostRepository shPostRepository;
	@Autowired
	private ShPostUtils shPostUtils;


	public ShPost pageLayoutFromPost(ShPostImpl shPostItem, ShSite shSite, String format) {
		JSONObject postTypeLayout = new JSONObject();

		if (shSite.getPostTypeLayout() != null)
			postTypeLayout = new JSONObject(shSite.getPostTypeLayout());

		String pageLayoutName = getPageLayoutNamePost(shPostItem, format, postTypeLayout);
		List<ShPost> shPostPageLayouts = shPostRepository.findByTitle(pageLayoutName);

		ShPost shPostPageLayout = null;
		if (shPostPageLayouts != null)
			for (ShPost shPostPageLayoutItem : shPostPageLayouts)
				if (shPostUtils.getSite(shPostPageLayoutItem).getId().equals(shSite.getId()))
					shPostPageLayout = shPostPageLayoutItem;

		return shPostPageLayout;
	}

	private String getPageLayoutNamePost(ShPostImpl shPostItem, String format, JSONObject postTypeLayout) {
		ShSitePostTypeLayouts shSitePostTypeLayouts = null;
		ObjectMapper mapper = new ObjectMapper();
		try {
			shSitePostTypeLayouts = mapper.readValue(
					postTypeLayout.get(shPostItem.getShPostType().getName()).toString(), ShSitePostTypeLayouts.class);
		} catch (JsonProcessingException | JSONException e) {
			logger.error("pageLayoutFromPost Error", e);
		}

		String pageLayoutName = null;

		if (format == null)
			format = DEFAULT_FORMAT;

		pageLayoutName = getPageLayoutName(format, pageLayoutName, shSitePostTypeLayouts);
		return pageLayoutName;
	}

	public ShPost pageLayoutFromFolderAndFolderIndex(ShObjectImpl shObjectItem, ShSite shSite, String format) {
		ShPost shFolderPageLayout = null;
		if (shObjectItem instanceof ShPostImpl shPostImpl) {
			if (logger.isDebugEnabled())
				logger.debug("isPost");
			shFolderPageLayout = folderIndexPageLayout(shPostImpl, format, shFolderPageLayout);
		} else if (shObjectItem instanceof ShFolder shFolder) {
			if (logger.isDebugEnabled())
				logger.debug("isFolder");
			ShPost shFolderIndex = shPostRepository.findByShFolderAndFurl(shFolder, "index");
			if (shFolderIndex != null) {
				shFolderPageLayout = folderIndexPageLayout(shFolderIndex, format, shFolderPageLayout);
			} else {
				shFolderPageLayout = this.defaultFolderPageLayout(shSite, format, shFolderPageLayout);
			}
		} else {
			logger.debug("Object not found because shObjectItem {}, Site Id: {}, Page Layout: {}",
					shObjectItem, shObjectItem.getId(), shSite.getPostTypeLayout());
		}

		return shFolderPageLayout;
	}

	private ShPost folderIndexPageLayout(ShObjectImpl shObjectItem, String format, ShPost shFolderPageLayout) {

		ShPostImpl shSelectedPost = shSitesPostUtils.getPostByStage((ShPost) shObjectItem);
		if (shSelectedPost != null) {
			if (logger.isDebugEnabled())
				logger.debug("Found Post of By Stage");
			Map<String, ShPostAttr> shFolderIndexMap = shSitesPostUtils.postToMap((ShPost) shSelectedPost);
			String shPostFolderPageLayoutId = shFolderIndexMap.get(ShSystemPostTypeAttr.PAGE_LAYOUT).getStrValue();
			shPostFolderPageLayoutId = pageLayoutFromFormat(format, shPostFolderPageLayoutId, shFolderIndexMap);
			shFolderPageLayout = getFolderPageLayout(shFolderPageLayout, shPostFolderPageLayoutId);
		} else {
			if (logger.isDebugEnabled())
				logger.debug("Not Found Post of By Stage");
		}
		return shFolderPageLayout;
	}

	private ShPost getFolderPageLayout(ShPost shFolderPageLayout, String shPostFolderPageLayoutId) {
		if (shPostFolderPageLayoutId != null) {
			shFolderPageLayout = shPostRepository.findById(shPostFolderPageLayoutId).orElse(null);
		}
		return shFolderPageLayout;
	}

	private String pageLayoutFromFormat(String format, String shPostFolderPageLayoutId,
			Map<String, ShPostAttr> shFolderIndexMap) {
		if (!format.equalsIgnoreCase(DEFAULT_FORMAT)) {
			ShPostAttrImpl shPostAttrFormats = shFolderIndexMap.get("FORMATS");
			List<Map<String, ShPostAttr>> shPostAttrFormatList = shSitesPostUtils.relationToMap(shPostAttrFormats);
			if (shPostAttrFormatList != null)
				for (Map<String, ShPostAttr> shPostAttrFormat : shPostAttrFormatList)
					if (shPostAttrFormat.get("NAME").getStrValue().equals(format))
						shPostFolderPageLayoutId = shPostAttrFormat.get("PAGE_LAYOUT").getStrValue();

		}
		return shPostFolderPageLayoutId;
	}

	private ShPost defaultFolderPageLayout(ShSite shSite, String format, ShPost shFolderPageLayout) {
		JSONObject postTypeLayout = new JSONObject(shSite.getPostTypeLayout());

		if (postTypeLayout.has("FOLDER")) {
			String pageLayoutName = getPageLayoutNameFolder(postTypeLayout, format);
			shFolderPageLayout = getFolderPageLayout(shSite, shFolderPageLayout, pageLayoutName);
		}
		return shFolderPageLayout;
	}

	private ShPost getFolderPageLayout(ShSite shSite, ShPost shFolderPageLayout, String pageLayoutName) {
		List<ShPost> shPostPageLayouts = shPostRepository.findByTitle(pageLayoutName);

		if (shPostPageLayouts != null) {
			for (ShPost shPostPageLayout : shPostPageLayouts) {
				if (shPostUtils.getSite(shPostPageLayout).getId().equals(shSite.getId()))
					shFolderPageLayout = shPostPageLayout;
			}
		}
		return shFolderPageLayout;
	}

	private String getPageLayoutNameFolder(JSONObject postTypeLayout, String format) {
		String pageLayoutName = null;
		ObjectMapper mapper = new ObjectMapper();
		ShSitePostTypeLayouts shSitePostTypeLayouts;
		try {
			shSitePostTypeLayouts = mapper.readValue(postTypeLayout.get("FOLDER").toString(),
					ShSitePostTypeLayouts.class);
			if (format == null)
				format = DEFAULT_FORMAT;

			pageLayoutName = getPageLayoutName(format, pageLayoutName, shSitePostTypeLayouts);
		} catch (JsonProcessingException | JSONException e) {
			logger.error(e);
		}

		return pageLayoutName;
	}

	private String getPageLayoutName(String format, String pageLayoutName,
			ShSitePostTypeLayouts shSitePostTypeLayouts) {
		for (ShSitePostTypeLayout shSitePostTypeLayout : shSitePostTypeLayouts) {
			if (shSitePostTypeLayout.getFormat().equals(format))
				pageLayoutName = shSitePostTypeLayout.getLayout();
		}
		return pageLayoutName;
	}
}
