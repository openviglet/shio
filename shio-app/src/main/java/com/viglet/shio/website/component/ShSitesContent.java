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

import com.viglet.shio.website.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.shio.bean.ShSitePostTypeLayout;
import com.viglet.shio.bean.ShSitePostTypeLayouts;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.utils.ShPostUtils;
import com.viglet.shio.utils.ShSiteUtils;
import com.viglet.shio.website.utils.ShSitesFolderUtils;
import com.viglet.shio.website.utils.ShSitesPostUtils;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShSitesContent {
	private static final Log logger = LogFactory.getLog(ShSitesContent.class);
	private static final String THEME = "theme";
	private static final String POSTS = "posts";
	private static final String FOLDERS = "folders";
	private static final String POST = "post";
	private static final String SITE = "site";
	private static final String DEFAULT_FORMAT = "default";

	@Autowired
	private ShSitesFolderUtils shSitesFolderUtils;
	@Autowired
	private ShSiteUtils shSiteUtils;
	@Autowired
	private ShSitesContextComponent shSitesContextComponent;
	@Autowired
	private ShSitesPostUtils shSitesPostUtils;
	@Autowired
	private ShSitesDetectContextURL shSitesDetectContextURL;
	@Autowired
	private ShObjectRepository shObjectRepository;
	@Autowired
	private ShSiteRepository shSiteRepository;
	@Autowired
	private ShPostUtils shPostUtils;
	@Autowired
	private ShPostRepository shPostRepository;

	public ShContent fromURL(String url) {
		ShSitesContextURL shSitesContextURL = new ShSitesContextURL();

		shSitesDetectContextURL.detectContextURL(url, shSitesContextURL);

		ShObjectImpl shObject = shObjectRepository.findById(shSitesContextURL.getInfo().getObjectId()).orElse(null);

		ShSite shSite = shSiteRepository.findById(shSitesContextURL.getInfo().getSiteId()).orElse(null);

		if (shObject instanceof ShFolder shFolder) {
			return this.fromFolder(shSite, shFolder, shSitesContextURL);
		} else if (shObject instanceof ShPostImpl shPostImpl) {
			return isFolderIndex(shPostImpl) ? this.fromFolderIndex(shSite, shPostImpl, shSitesContextURL)
					: this.fromPost(shSite, shPostImpl, shSitesContextURL);
		}
		return new ShContent();
	}

	private boolean isFolderIndex(ShPostImpl shPostImpl) {
		return shPostImpl.getShPostType().getName().equals(ShSystemPostType.FOLDER_INDEX);
	}

	private ShContent fromFolderIndex(ShSite shSite, ShPostImpl shPostImpl, ShSitesContextURL shSitesContextURL) {

		Map<String, Object> shPostItemAttrs = new HashMap<>();

		Map<String, ShPostAttr> shFolderPageLayoutMap = shSitesContextComponent.shFolderPageLayoutMapFactory(shPostImpl,
				shSite, shSitesContextURL.getInfo().getShFormat());

		ShFolder shFolderItem = shSitesContextComponent.shFolderItemFactory(shPostImpl);

		ShContent shFolderItemAttrs = shSitesFolderUtils.toSystemMap(shFolderItem);
		if (shFolderPageLayoutMap != null) {
			String shPostThemeId = shFolderPageLayoutMap.get(ShSystemPostTypeAttr.THEME).getStrValue();
			Map<String, Object> shThemeAttrs = shSitesContextComponent.shThemeFactory(shPostThemeId);
			shPostItemAttrs.put(THEME, shThemeAttrs);
			shFolderItemAttrs.put(THEME, shThemeAttrs);
		}
		shFolderItemAttrs.put(POSTS, shSitesContextComponent.shPostItemsFactory(shFolderItem));
		shFolderItemAttrs.put(FOLDERS, shSitesContextComponent.shChildFolderItemsFactory(shFolderItem));
		shFolderItemAttrs.put(POST, shPostItemAttrs);
		shFolderItemAttrs.put(SITE, shSiteUtils.toSystemMap(shSite));

		return shFolderItemAttrs;
	}

	private ShContent fromPost(ShSite shSite, ShPostImpl shPostImpl, ShSitesContextURL shSitesContextURL) {

		Map<String, Object> shSiteItemAttrs = shSiteUtils.toSystemMap(shSite);

		ShContent shPostItemAttrs = shSitesPostUtils.toSystemMap(shPostImpl);
		JSONObject postTypeLayout = new JSONObject();

		if (shSite.getPostTypeLayout() != null)
			postTypeLayout = new JSONObject(shSite.getPostTypeLayout());

		ObjectMapper mapper = new ObjectMapper();
		ShSitePostTypeLayouts shSitePostTypeLayouts = null;

		try {
			shSitePostTypeLayouts = mapper.readValue(postTypeLayout.get(shPostImpl.getShPostType().getName()).toString(),
					ShSitePostTypeLayouts.class);
		} catch (JsonProcessingException | JSONException e) {
			logger.error("fromPost Error: ", e);
		}

		String pageLayoutName = null;
		String format = shSitesContextURL.getInfo().getShFormat();
		if (format == null)
			format = DEFAULT_FORMAT;

		for (ShSitePostTypeLayout shSitePostTypeLayout : shSitePostTypeLayouts) {
			if (shSitePostTypeLayout.getFormat().equals(format))
				pageLayoutName = shSitePostTypeLayout.getLayout();
		}
		List<ShPost> shPostPageLayouts = shPostRepository.findByTitle(pageLayoutName);

		Map<String, ShPostAttr> shPostPageLayoutMap = null;

		if (shPostPageLayouts != null) {
			for (ShPost shPostPageLayout : shPostPageLayouts) {
				if (shPostUtils.getSite(shPostPageLayout).getId().equals(shSite.getId()))
					shPostPageLayoutMap = shSitesPostUtils.postToMap(shPostPageLayout);
			}
			if (shPostPageLayoutMap != null) {
				String shPostThemeId = shPostPageLayoutMap.get(ShSystemPostTypeAttr.THEME).getStrValue();
				Map<String, Object> shThemeAttrs = shSitesContextComponent.shThemeFactory(shPostThemeId);
				shPostItemAttrs.put(THEME, shThemeAttrs);
			}
		}

		shPostItemAttrs.put(SITE, shSiteItemAttrs);

		return shPostItemAttrs;

	}

	private ShContent fromFolder(ShSite shSite, ShFolder shFolder, ShSitesContextURL shSitesContextURL) {
		Map<String, Object> shPostItemAttrs = new HashMap<>();
		ShContent shFolderItemAttrs = shSitesFolderUtils.toSystemMap(shFolder);

		Map<String, ShPostAttr> shFolderPageLayoutMap = shSitesContextComponent.shFolderPageLayoutMapFactory(shFolder,
				shSite, shSitesContextURL.getInfo().getShFormat());
		if (shFolderPageLayoutMap != null) {
			String shPostThemeId = shFolderPageLayoutMap.get(ShSystemPostTypeAttr.THEME).getStrValue();

			Map<String, Object> shThemeAttrs = shSitesContextComponent.shThemeFactory(shPostThemeId);

			shPostItemAttrs.put(THEME, shThemeAttrs);
			shFolderItemAttrs.put(THEME, shThemeAttrs);
		}
		shFolderItemAttrs.put(POSTS, shSitesContextComponent.shPostItemsFactory(shFolder));
		shFolderItemAttrs.put(FOLDERS, shSitesContextComponent.shChildFolderItemsFactory(shFolder));
		shFolderItemAttrs.put(POST, shPostItemAttrs);
		shFolderItemAttrs.put(SITE, shSiteUtils.toSystemMap(shSite));

		return shFolderItemAttrs;

	}
}
