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
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
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

import lombok.extern.slf4j.Slf4j;

/**
 * @author Alexandre Oliveira
 */

@Slf4j
@Component
public class ShCacheObject {
	private final ShCacheURL shCacheURL;
	private final ShObjectRepository shObjectRepository;
	private final ShPostRepository shPostRepository;
	private final ShFolderUtils shFolderUtils;
	private final ShSitesObjectUtils shSitesObjectUtils;
	private final ShCacheObject shCacheObjectInner;

	public ShCacheObject(ShCacheURL shCacheURL, ShObjectRepository shObjectRepository, ShPostRepository shPostRepository, ShFolderUtils shFolderUtils, ShSitesObjectUtils shSitesObjectUtils, @Lazy ShCacheObject shCacheObject) {
		this.shCacheURL = shCacheURL;
		this.shObjectRepository = shObjectRepository;
		this.shPostRepository = shPostRepository;
		this.shFolderUtils = shFolderUtils;
		this.shSitesObjectUtils = shSitesObjectUtils;
		this.shCacheObjectInner = shCacheObject;
	}

	@Cacheable(value = "shObject", key = "#id", sync = true)
	public List<String> cache(String id) {
		if (log.isDebugEnabled())
			log.debug("Creating the shObject Cache id " + id);
		return new ArrayList<>();
	}

	@CachePut(value = "shObject", key = "#id")
	public List<String> updateCache(String id, ShSitesContextURL shSitesContextURL) {
		List<String> urls = shCacheObjectInner != null ? shCacheObjectInner.cache(id) : Collections.emptyList();
		if (!urls.contains(shSitesContextURL.getInfo().getContextURLOriginal())) {
			if (log.isDebugEnabled())
				log.debug("Adding id: " + id + " and URL: " + shSitesContextURL.getInfo().getContextURLOriginal());
			urls.add(shSitesContextURL.getInfo().getContextURLOriginal());
		}
		return urls;
	}

	public void deleteCache(String id) {
		ShObject shObject = shObjectRepository.findById(id).orElse(null);
		String objectId = id;
		if (shObject instanceof ShFolder shFolder) {
			ShPost shFolderIndex = shPostRepository.findByShFolderAndFurl(shFolder, "index");
			if (shFolderIndex != null) {
				objectId = shFolderIndex.getId();
			}
		} else if (shObject instanceof ShPost shPost) {
			ShFolder parentFolder = shFolderUtils.getParentFolder(shPost);
			this.deleteCache(parentFolder.getId());
		}

		this.deleteDependency(objectId);
		deleteCacheSelf(objectId);

	}

	public void deleteDependency(String id) {
		if (log.isDebugEnabled())
			log.debug("Executing deleteDependency for id: " + id);
		List<String> urls = shCacheObjectInner != null ? shCacheObjectInner.cache(id) : Collections.emptyList();
		for (String url : urls) {
			if (log.isDebugEnabled())
				log.debug("Deleting the page with id: " + id + " and URL: " + url);
			deletePageCache(id, url);
			ShObject shObject = shObjectRepository.findById(id).orElse(null);
			String contextURL = null;
			if (shObject != null && shObject instanceof ShPost shPost && shPost.getFurl().equals("index")) {
				ShFolder shFolder = shFolderUtils.getParentFolder(shPost);
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
	@CacheEvict(value = "page", key = "{#id, #url}")
	public void deletePageCache(String id, String url) {
		if (log.isDebugEnabled()) {
			String sanitizedId = id.replaceAll("[\n\r\t]", "_");
			log.debug(String.format("Deleted cache of id %s, %s", sanitizedId, url));
		}
	}
	@CacheEvict(value = "shObject", key = "#id")
	public void deleteCacheSelf(String id) {
		if (log.isDebugEnabled())
			log.debug("Deleted Cache: ".concat(id));
	}
}