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
package com.viglet.shio.exchange.site;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.viglet.shio.bean.ShSitePostTypeLayouts;
import com.viglet.shio.bean.ShSitePostTypeLayoutsGeneral;
import com.viglet.shio.exchange.ShExchange;
import com.viglet.shio.exchange.ShExchangeFilesDirs;
import com.viglet.shio.exchange.folder.ShFolderExport;
import com.viglet.shio.exchange.post.type.ShPostTypeExchange;
import com.viglet.shio.exchange.post.type.ShPostTypeExport;
import com.viglet.shio.exchange.utils.ShExchangeUtils;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.utils.ShUtils;

/**
 * Export Site.
 *
 * @author Alexandre Oliveira
 * @since 0.3.0
 */
@Component
@Slf4j
public class ShSiteExport {
	private final ShSiteRepository shSiteRepository;
	private final ShFolderRepository shFolderRepository;
	private final ShUtils shUtils;
	private final ShFolderExport shFolderExport;
	private final ShPostTypeExport shPostTypeExport;
	private final ShPostTypeRepository shPostTypeRepository;
	private final ShExchangeUtils shExchangeUtils;

	@Autowired
	public ShSiteExport(ShSiteRepository shSiteRepository, ShFolderRepository shFolderRepository, ShUtils shUtils,
						ShFolderExport shFolderExport, ShPostTypeExport shPostTypeExport,
						ShPostTypeRepository shPostTypeRepository, ShExchangeUtils shExchangeUtils) {
		this.shSiteRepository = shSiteRepository;
		this.shFolderRepository = shFolderRepository;
		this.shUtils = shUtils;
		this.shFolderExport = shFolderExport;
		this.shPostTypeExport = shPostTypeExport;
		this.shPostTypeRepository = shPostTypeRepository;
		this.shExchangeUtils = shExchangeUtils;
	}

	public StreamingResponseBody exportObject(@PathVariable String id, HttpServletResponse response) {
		ShExchange shExchange = new ShExchange();
		Optional<ShSite> shSiteOptional = shSiteRepository.findById(id);

		ShExchangeFilesDirs shExchangeFilesDirs = new ShExchangeFilesDirs();
		if (shSiteOptional.isPresent() && shExchangeFilesDirs.generate()) {
			ShSite shSite = shSiteOptional.get();

			List<String> rootFoldersUUID = new ArrayList<>();
			Set<ShFolder> rootFolders = shFolderRepository.findByShSiteAndRootFolder(shSite, (byte) 1);

			rootFolders.forEach(shFolder -> rootFoldersUUID.add(shFolder.getId()));

			ShSiteExchange shSiteExchange = new ShSiteExchange();
			shSiteExchange.setId(shSite.getId());
			shSiteExchange.setName(shSite.getName());
			shSiteExchange.setUrl(shSite.getUrl());
			shSiteExchange.setDescription(shSite.getDescription());
			shSiteExchange.setPostTypeLayout(shSite.getPostTypeLayout());
			shSiteExchange.setSearchablePostTypes(shSite.getSearchablePostTypes());
			shSiteExchange.setDate(shSite.getDate());
			shSiteExchange.setRootFolders(rootFoldersUUID);
			shSiteExchange.setOwner(shSite.getOwner());
			shSiteExchange.setFurl(shSite.getFurl());

			List<ShSiteExchange> shSiteExchanges = new ArrayList<>();
			shSiteExchanges.add(shSiteExchange);
			shExchange.setSites(shSiteExchanges);

			ShExchange shExchangeFolder = shFolderExport.shFolderExchangeIterate(rootFolders);

			shExchange.setFolders(shExchangeFolder.getFolders());
			shExchange.setPosts(shExchangeFolder.getPosts());
			shExchange.setPostTypes(exportPostTypes(shSiteExchange, shExchangeFolder));
			exportStaticFiles(shExchangeFilesDirs.getExportDir(), shExchangeFolder);
			return shExchangeUtils.downloadZipFile(String.format("%s_site", shSite.getFurl()), response, shExchange,
					shExchangeFilesDirs);
		} else {
			return null;
		}

	}

	private List<ShPostTypeExchange> exportPostTypes(ShSiteExchange shSiteExchange, ShExchange shExchangeFolder) {
		List<ShPostTypeExchange> shExchangePostTypes = shExchangeFolder.getPostTypes();

		if (shUtils.isJSONValid(shSiteExchange.getPostTypeLayout())) {
			Gson gson = new Gson();
			Type type = new TypeToken<ShSitePostTypeLayoutsGeneral>() {
			}.getType();
			ShSitePostTypeLayoutsGeneral postTypes = gson.fromJson(shSiteExchange.getPostTypeLayout(), type);

			Set<String> shExchangePostTypeMap = new HashSet<>();
			if (shExchangePostTypes != null)
				shExchangePostTypes
						.forEach(shPostTypeExchange -> shExchangePostTypeMap.add(shPostTypeExchange.getName()));
			else
				shExchangePostTypes = new ArrayList<>();

			for (Entry<String, ShSitePostTypeLayouts> postType : postTypes.entrySet()) {
				String postTypeName = postType.getKey();
				if (!shExchangePostTypeMap.contains(postTypeName)) {
					ShPostType shPostType = shPostTypeRepository.findByName(postTypeName);
					if (shPostType != null) {
						shExchangePostTypes.add(shPostTypeExport.exportPostType(shPostType));
					}
				}
			}
		}
		return shExchangePostTypes;
	}

	private void exportStaticFiles(File exportDir, ShExchange shExchange) {
		shExchange.getFiles().forEach(fileExchange -> {
			try {
				File fileSource = fileExchange.getFile();
				File fileDestination = new File(
						exportDir.getAbsolutePath().concat(File.separator + fileExchange.getId()));
				if (fileSource.exists())
					FileUtils.copyFile(fileSource, fileDestination);
				else {
					log.warn("Exporting the file {}, but it does not exist, so it is creating a new empty file to export.", fileSource.getAbsoluteFile());
					if (fileDestination.createNewFile())
						log.debug("File was created {}", fileDestination);
					else
						log.error("File was not created: {}", fileDestination);
				}

			} catch (IOException e) {
				log.error("exportObject: ", e);
			}

		});
	}
}
