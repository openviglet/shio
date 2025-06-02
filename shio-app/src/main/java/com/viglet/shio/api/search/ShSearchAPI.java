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
package com.viglet.shio.api.search;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.api.post.ShPostWithBreadcrumb;
import com.viglet.shio.object.ShObjectType;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.turing.ShTuringIntegration;
import com.viglet.shio.utils.ShFolderUtils;
import com.viglet.shio.utils.ShPostUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/search")
@Tag( name = "Search", description = "Search for Shio Objects")
@Slf4j
public class ShSearchAPI {
	private final ShSiteRepository shSiteRepository;
	private final ShPostTypeRepository shPostTypeRepository;
	private final ShPostRepository shPostRepository;
	private final ShFolderRepository shFolderRepository;
	private final ShPostUtils shPostUtils;
	private final ShFolderUtils shFolderUtils;
	private final ShTuringIntegration shTuringIntegration;

	@Autowired
	public ShSearchAPI(ShSiteRepository shSiteRepository, ShPostTypeRepository shPostTypeRepository,
					   ShPostRepository shPostRepository, ShFolderRepository shFolderRepository,
					   ShPostUtils shPostUtils, ShFolderUtils shFolderUtils, ShTuringIntegration shTuringIntegration) {
		this.shSiteRepository = shSiteRepository;
		this.shPostTypeRepository = shPostTypeRepository;
		this.shPostRepository = shPostRepository;
		this.shFolderRepository = shFolderRepository;
		this.shPostUtils = shPostUtils;
		this.shFolderUtils = shFolderUtils;
		this.shTuringIntegration = shTuringIntegration;
	}

	@Operation(summary = "Search for Shio Objects")
	@GetMapping
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public List<ShPostWithBreadcrumb> shSearch(@RequestParam(value = "q") String q) {
		List<ShPostWithBreadcrumb> searchResults = new ArrayList<>();
		for (ShPost shPost : shPostRepository.findByTitle(q)) {
			ShPostImpl shPostLazy = shPostUtils.loadLazyPost(shPost.getId(), false);
			List<ShFolder> breadcrumb = shFolderUtils.breadcrumb(shPostLazy.getShFolder());
			ShSite shSite = breadcrumb.get(0).getShSite();
			ShPostWithBreadcrumb shPostWithBreadcrumb = new ShPostWithBreadcrumb();
			shPostWithBreadcrumb.setShPost(shPostLazy);
			shPostWithBreadcrumb.setBreadcrumb(breadcrumb);
			shPostWithBreadcrumb.setShSite(shSite);
			searchResults.add(shPostWithBreadcrumb);
		}

		return searchResults;
	}

	@GetMapping("/type/{objectName}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public List<ShPostWithBreadcrumb> shSearchBytType(@PathVariable String objectName) {

		ShPostType shPostType = shPostTypeRepository.findByName(objectName);
		List<ShPostWithBreadcrumb> searchResults = new ArrayList<>();
		for (ShPost shPost : shPostRepository.findByShPostType(shPostType)) {
			ShPostImpl shPostLazy = shPostUtils.loadLazyPost(shPost.getId(), false);
			List<ShFolder> breadcrumb = shFolderUtils.breadcrumb(shPostLazy.getShFolder());
			ShSite shSite = breadcrumb.get(0).getShSite();
			ShPostWithBreadcrumb shPostWithBreadcrumb = new ShPostWithBreadcrumb();
			shPostWithBreadcrumb.setShPost(shPostLazy);
			shPostWithBreadcrumb.setBreadcrumb(breadcrumb);
			shPostWithBreadcrumb.setShSite(shSite);
			searchResults.add(shPostWithBreadcrumb);
		}

		return searchResults;
	}

	@Operation(summary = "Indexing by Post Type")
	@GetMapping("/indexing/{siteName}/{objectName}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public boolean shSearchIndexing(@PathVariable String siteName, @PathVariable String objectName) {
		ShSite shSite = shSiteRepository.findByName(siteName);
		if (shSite != null) {
			if (objectName.equals(ShObjectType.FOLDER)) {
				return folderIndexing(siteName);
			} else {
				postIndexing(siteName, objectName);
				return true;
			}
		}
		return false;
	}

	private void postIndexing(String siteName, String objectName) {
		log.info("Trying to Index posts of {}", objectName);
		ShPostType shPostType = shPostTypeRepository.findByName(objectName);
		if (shPostType != null) {
			log.info("Indexing posts of {}", shPostType.getName());
			for (ShPost shPost : shPostRepository.findByShPostType(shPostType)) {
				if (shPostUtils.getSite(shPost).getName().equals(siteName)) {
					log.info("Indexing {} post", shPost.getTitle());
					shTuringIntegration.indexObject(shPost);
				}

			}
			log.info("Indexed.");

		}
	}

	private boolean folderIndexing(String siteName) {
		log.info("Trying to Index Folders");
		for (ShFolder shFolder : shFolderRepository.findAll()) {
			if (shFolderUtils.getSite(shFolder).getName().equals(siteName)) {
				log.info(String.format("Indexing %s folder", shFolder.getName()));
				shTuringIntegration.indexObject(shFolder);
			}
		}
		log.info("Indexed.");
		return true;
	}
}
