/*
 * Copyright (C) 2016-2021 the original author or authors. 
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
package com.viglet.shio.exchange.folder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.viglet.shio.exchange.ShExchange;
import com.viglet.shio.exchange.ShExchangeFilesDirs;
import com.viglet.shio.exchange.file.ShFileExchange;
import com.viglet.shio.exchange.post.ShPostExchange;
import com.viglet.shio.exchange.post.ShPostExport;
import com.viglet.shio.exchange.post.type.ShPostTypeExchange;
import com.viglet.shio.exchange.post.type.ShPostTypeExport;
import com.viglet.shio.exchange.site.ShSiteExchange;
import com.viglet.shio.exchange.utils.ShExchangeUtils;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.utils.ShFolderUtils;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShFolderExport {
	private final ShFolderRepository shFolderRepository;
	private final ShPostRepository shPostRepository;
	private final ShPostExport shPostExport;
	private final ShPostTypeExport shPostTypeExport;
	private final ShExchangeUtils shExchangeUtils;
	private final ShFolderUtils shFolderUtils;

	@Autowired
	public ShFolderExport(ShFolderRepository shFolderRepository, ShPostRepository shPostRepository,
						  ShPostExport shPostExport, ShPostTypeExport shPostTypeExport, ShExchangeUtils shExchangeUtils,
						  ShFolderUtils shFolderUtils) {
		this.shFolderRepository = shFolderRepository;
		this.shPostRepository = shPostRepository;
		this.shPostExport = shPostExport;
		this.shPostTypeExport = shPostTypeExport;
		this.shExchangeUtils = shExchangeUtils;
		this.shFolderUtils = shFolderUtils;
	}

	public ShExchange shFolderExchangeIterate(Set<ShFolder> shFolders) {
		ShExchange shExchange = new ShExchange();
		List<ShFolderExchange> shFolderExchanges = new ArrayList<>();
		List<ShPostExchange> shPostExchanges = new ArrayList<>();
		List<ShFileExchange> files = new ArrayList<>();
		Map<String, ShPostTypeExchange> shPostTypeExchanges = new HashMap<>();
		for (ShFolder shFolder : shFolders) {

			for (ShPost shPost : shPostRepository.findByShFolder(shFolder)) {
				ShPostExchange shPostExchange = exportShPost(files, shPostTypeExchanges, shPost);

				shPostExchanges.add(shPostExchange);
			}
			ShFolderExchange shFolderExchangeChild = new ShFolderExchange();
			shFolderExchangeChild.setId(shFolder.getId());
			shFolderExchangeChild.setDate(shFolder.getDate());
			shFolderExchangeChild.setName(shFolder.getName());
			shFolderExchangeChild.setOwner(shFolder.getOwner());
			shFolderExchangeChild.setFurl(shFolder.getFurl());
			shFolderExchangeChild.setPosition(shFolder.getPosition());
			
			if (shFolder.getParentFolder() != null) {
				shFolderExchangeChild.setParentFolder(shFolder.getParentFolder().getId());
			}
			shFolderExchanges.add(shFolderExchangeChild);
			ShExchange shExchangeChild = this.shFolderExchangeNested(shFolder);
			shFolderExchanges.addAll(shExchangeChild.getFolders());
			shPostExchanges.addAll(shExchangeChild.getPosts());

			for (ShPostTypeExchange shPostTypeExchange : shExchangeChild.getPostTypes()) {
				if (!shPostTypeExchanges.containsKey(shPostTypeExchange.getName())) {
					shPostTypeExchanges.put(shPostTypeExchange.getName(), shPostTypeExchange);
				}

			}
			files.addAll(shExchangeChild.getFiles());
		}
		shExchange.setFolders(shFolderExchanges);
		shExchange.setPosts(shPostExchanges);
		shExchange.setFiles(files);
		shExchange.setPostTypes(new ArrayList<>(shPostTypeExchanges.values()));
		return shExchange;
	}

	private ShPostExchange exportShPost(List<ShFileExchange> files, Map<String, ShPostTypeExchange> shPostTypeExchanges,
			ShPost shPost) {
		ShPostExchange shPostExchange = new ShPostExchange();
		shPostExchange.setId(shPost.getId());
		shPostExchange.setFolder(shPost.getShFolder().getId());
		shPostExchange.setDate(shPost.getDate());
		shPostExchange.setPostType(shPost.getShPostType().getName());
		shPostExchange.setOwner(shPost.getOwner());
		shPostExchange.setFurl(shPost.getFurl());
		shPostExchange.setPosition(shPost.getPosition());

		if (!shPostTypeExchanges.containsKey(shPost.getShPostType().getName())) {
			shPostTypeExchanges.put(shPost.getShPostType().getName(),
					shPostTypeExport.exportPostType(shPost.getShPostType()));
		}
		Map<String, Object> fields = new HashMap<>();

		shPostExport.shPostAttrExchangeIterate(shPost, fields, files);

		shPostExchange.setFields(fields);
		return shPostExchange;
	}

	public ShExchange shFolderExchangeNested(ShFolder shFolder) {
		Set<ShFolder> childFolders = shFolderRepository.findByParentFolder(shFolder);
		return this.shFolderExchangeIterate(childFolders);
	}

	public StreamingResponseBody exportObject(HttpServletResponse response, String id) {
		Optional<ShFolder> shFolder = shFolderRepository.findById(id);
		if (shFolder.isPresent()) {
			
			ShExchangeFilesDirs shExchangeFilesDirs = new ShExchangeFilesDirs();
			if (shExchangeFilesDirs.generate()) {
				Set<ShFolder> folders = new HashSet<>();
				folders.add(shFolder.get());
				ShExchange shExchange = this.shFolderExchangeIterate(folders);
				if (shFolder.get().getParentFolder() == null) {
					ShSite shSite = shFolderUtils.getSite(shFolder.get());
					ShSiteExchange shSiteExchange = new ShSiteExchange();
					shSiteExchange.setId(shSite.getId());
					shSiteExchange.setName(shSite.getName());
					shSiteExchange.setUrl(shSite.getUrl());
					shSiteExchange.setDescription(shSite.getDescription());
					shSiteExchange.setPostTypeLayout(shSite.getPostTypeLayout());
					shSiteExchange.setSearchablePostTypes(shSite.getSearchablePostTypes());
					shSiteExchange.setDate(shSite.getDate());
					shSiteExchange.setRootFolders(Arrays.asList(shFolder.get().getId()));
					shSiteExchange.setOwner(shSite.getOwner());
					shSiteExchange.setFurl(shSite.getFurl());

					List<ShSiteExchange> shSiteExchanges = new ArrayList<>();
					shSiteExchanges.add(shSiteExchange);
					shExchange.setSites(shSiteExchanges);
				}
				
				return shExchangeUtils.downloadZipFile(String.format("%s_folder", shFolder.get().getFurl()), response,
						shExchange, shExchangeFilesDirs);
			}
		}

		return null;
	}
}
