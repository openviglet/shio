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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostAttrRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.website.utils.ShSitesPostUtils;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShQueryComponent {


	private final ShFolderRepository shFolderRepository;
	private final ShPostRepository shPostRepository;
	private final ShPostAttrRepository shPostAttrRepository;
	private final ShPostTypeRepository shPostTypeRepository;
	private final ShSitesPostUtils shSitesPostUtils;

	public ShQueryComponent(ShFolderRepository shFolderRepository, ShPostRepository shPostRepository, ShPostAttrRepository shPostAttrRepository, ShPostTypeRepository shPostTypeRepository, ShSitesPostUtils shSitesPostUtils) {
		this.shFolderRepository = shFolderRepository;
		this.shPostRepository = shPostRepository;
		this.shPostAttrRepository = shPostAttrRepository;
		this.shPostTypeRepository = shPostTypeRepository;
		this.shSitesPostUtils = shSitesPostUtils;
	}

	public List<Map<String, ShPostAttr>> findByFolderName(String folderId, String postTypeName) {

		ShPostType shPostType = shPostTypeRepository.findByName(postTypeName);
		ShFolder shFolder = shFolderRepository.findById(folderId).orElse(null);
		List<ShPost> shPostList = shSitesPostUtils
				.getPostsByStage(shPostRepository.findByShFolderAndShPostTypeOrderByPositionAsc(shFolder, shPostType));

		List<Map<String, ShPostAttr>> shPosts = new ArrayList<>();
		for (ShPost shPost : shPostList) {
			Map<String, ShPostAttr> shPostObject = shSitesPostUtils.postToMap(shPost);
			shPosts.add(shPostObject);
		}

		return shPosts;
	}

	public List<Map<String, ShPostAttr>> findByPostTypeName(String postTypeName) {

		ShPostType shPostType = shPostTypeRepository.findByName(postTypeName);
		List<ShPost> shPostList = shPostRepository.findByShPostType(shPostType);

		List<Map<String, ShPostAttr>> shPosts = new ArrayList<>();
		for (ShPost shPost : shPostList) {
			Map<String, ShPostAttr> shPostObject = shSitesPostUtils.postToMap(shPost);
			shPosts.add(shPostObject);
		}

		return shPosts;
	}

	public List<Map<String, ShPostAttr>> findByPostTypeNameIn(String postTypeName, Set<String> arrayValue) {

		ShPostType shPostType = shPostTypeRepository.findByName(postTypeName);
		Set<ShPostAttr> shPostAttrs = shPostAttrRepository.findByArrayValueIn(arrayValue);

		List<ShPostAttr> shPostAttrList = new ArrayList<>();
		shPostAttrList.addAll(shPostAttrs);

		Set<ShPost> shPostList = shPostRepository.findByShPostTypeAndShPostAttrsIn(shPostType, shPostAttrList);

		List<Map<String, ShPostAttr>> shPosts = new ArrayList<>();
		for (ShPost shPost : shPostList) {
			Map<String, ShPostAttr> shPostObject = shSitesPostUtils.postToMap(shPost);
			shPosts.add(shPostObject);
		}

		return shPosts;
	}
}
