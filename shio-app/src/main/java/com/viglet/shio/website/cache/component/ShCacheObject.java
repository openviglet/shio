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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.utils.ShFolderUtils;
import com.viglet.shio.website.ShSitesContextURL;
import com.viglet.shio.website.utils.ShSitesObjectUtils;

/**
 * @author Alexandre Oliveira
 */


@Component
public class ShCacheObject {
	private static final Log logger = LogFactory.getLog(ShCacheObject.class);
	@Autowired
	ShCachePage shCachePage;
	@Autowired
	ShCacheURL shCacheURL;
	@Autowired
	ShCacheObject shCacheObject;
	@Autowired
	ShObjectRepository shObjectRepository;
	@Autowired
	ShPostRepository shPostRepository;
	@Autowired
	ShFolderUtils shFolderUtils;
	@Autowired
	ShSitesObjectUtils shSitesObjectUtils;
	
	@Cacheable(value = "shObject", key = "#id", sync = true)
	public List<String> cache(String id) {
		if (logger.isDebugEnabled())
			logger.debug("Creating the shObject Cache id " + id);
		return new ArrayList<>();
	}

	@CachePut(value = "shObject", key = "#id")
	public List<String> updateCache(String id, ShSitesContextURL shSitesContextURL) {
		List<String> urls = shCacheObject.cache(id);
		if (!urls.contains(shSitesContextURL.getInfo().getContextURLOriginal())) {
			if (logger.isDebugEnabled())
				logger.debug("Adding id: " + id + " and URL: " + shSitesContextURL.getInfo().getContextURLOriginal());
			urls.add(shSitesContextURL.getInfo().getContextURLOriginal());
		}
		return urls;
	}

	public void deleteCache(String id) {
		ShObject shObject = shObjectRepository.findById(id).orElse(null);
		String objectId = id;
		if (shObject instanceof ShFolder) {
			ShPost shFolderIndex = shPostRepository.findByShFolderAndFurl((ShFolder) shObject, "index");
			if (shFolderIndex != null) {
				objectId = shFolderIndex.getId();
			}
		} else if (shObject instanceof ShPost) {
			ShFolder shFolder = shFolderUtils.getParentFolder(shObject);
			this.deleteCache(shFolder.getId());
		}

		this.deleteDependency(objectId);
		shCacheObject.deleteCacheSelf(objectId);

	}

	public void deleteDependency(String id) {
		if (logger.isDebugEnabled())
			logger.debug("Executing deleteDependency for id: " + id);
		List<String> urls = shCacheObject.cache(id);
		for (String url : urls) {
			if (logger.isDebugEnabled())
				logger.debug("Deleting the page with id: " + id + " and URL: " + url);
			shCachePage.deleteCache(id, url);
			ShObject shObject = shObjectRepository.findById(id).orElse(null);
			String contextURL = null;
			if (shObject instanceof ShPost && shObject.getFurl().equals("index")) {
				ShFolder shFolder = shFolderUtils.getParentFolder(shObject);
				contextURL = shSitesObjectUtils.generateObjectLinkById(shFolder.getId());
			} else {
				contextURL = shSitesObjectUtils.generateObjectLinkById(id);
			}

			/**
			 * If the URL doesn't end with slash,remove the slash of contextURL
			 */
			if (!url.endsWith("/"))
				contextURL = contextURL.trim().replaceFirst(".$", StringUtils.EMPTY);

			shCacheURL.deleteCache(contextURL, url);
		}

	}

	@CacheEvict(value = "shObject", key = "#id")
	public void deleteCacheSelf(String id) {
		if (logger.isDebugEnabled())
			logger.debug("Deleted Cache: ".concat(id));
	}
}