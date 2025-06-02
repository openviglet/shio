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

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.exchange.ShExchangeContext;
import com.viglet.shio.exchange.ShExchangeObjectMap;
import com.viglet.shio.exchange.post.ShPostExchange;
import com.viglet.shio.exchange.post.ShPostImport;
import com.viglet.shio.exchange.site.ShSiteExchange;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.url.ShURLFormatter;
import com.viglet.shio.utils.ShUserUtils;

/**
 * @author Alexandre Oliveira
 */
@Component
@Slf4j
public class ShFolderImport {
	private final ShSiteRepository shSiteRepository;
	private final ShFolderRepository shFolderRepository;
	private final ShPostImport shPostImport;
	private final ShUserUtils shUserUtils;

	@Autowired
	public ShFolderImport(ShSiteRepository shSiteRepository, ShFolderRepository shFolderRepository,
						  ShPostImport shPostImport, ShUserUtils shUserUtils) {
		this.shSiteRepository = shSiteRepository;
		this.shFolderRepository = shFolderRepository;
		this.shPostImport = shPostImport;
		this.shUserUtils = shUserUtils;
	}

	public void shFolderImportNested(String shObject, boolean importOnlyFolders,
			ShExchangeObjectMap shExchangeObjectMap, ShExchangeContext shExchangeContext) {
		String username = shUserUtils.getCurrentUsername();
		Map<String, List<String>> shChildObjects = shExchangeObjectMap.getShChildObjects();
		Map<String, Object> shObjects = shExchangeObjectMap.getShObjects();
		if (shChildObjects.containsKey(shObject)) {
			for (String objectId : shChildObjects.get(shObject)) {
				if (shObjects.get(objectId)instanceof ShFolderExchange shFolderExchange) {
					ShFolderExchangeContext context = new ShFolderExchangeContext();
					context.setCloned(shExchangeContext.isCloned());
					context.setExtractFolder(shExchangeContext.getExtractFolder());
					context.setImportOnlyFolders(importOnlyFolders);
					context.setShExchangeObjectMap(shExchangeObjectMap);
					context.setShFolderExchange(shFolderExchange);
					context.setShObject(shObject);
					context.setUsername(username);
					this.createShFolder(context);
				}

				if (!importOnlyFolders && shObjects.get(objectId)instanceof ShPostExchange shPostExchange) {
					shPostImport.createShPost(shExchangeContext, shPostExchange, shExchangeObjectMap);
				}
			}

		}
	}

	public void createShFolder(ShFolderExchangeContext context) {
		Optional<ShFolder> shFolderOptional = shFolderRepository.findById(context.getShFolderExchange().getId());
		ShFolder shFolderChild = shFolderOptional.orElseGet(() -> this.createFolderObject(context.getShFolderExchange(),
				context.getUsername(),
                context.getShObject(), context.getShExchangeObjectMap().getShObjects(), context.isCloned()));

		this.shFolderImportNested(shFolderChild.getId(), context.isImportOnlyFolders(),
				context.getShExchangeObjectMap(),
				new ShExchangeContext(context.getExtractFolder(), context.isCloned()));

	}

	private ShFolder createFolderObject(ShFolderExchange shFolderExchange, String username, String shObject,
			Map<String, Object> shObjects, boolean isCloned) {
		ShFolder shFolderChild;
		shFolderChild = new ShFolder();
		shFolderChild.setId(shFolderExchange.getId());
		shFolderChild.setDate(isCloned ? new Date() : shFolderExchange.getDate());
		shFolderChild.setName(shFolderExchange.getName());
		if (shFolderExchange.getPosition() > 0) {
			shFolderChild.setPosition(shFolderExchange.getPosition());
		}
		if (shFolderExchange.getOwner() != null) {
			shFolderChild.setOwner(shFolderExchange.getOwner());
		} else {
			shFolderChild.setOwner(username);
		}
		if (shFolderExchange.getFurl() != null) {
			shFolderChild.setFurl(shFolderExchange.getFurl());
		} else {
			shFolderChild.setFurl(ShURLFormatter.format(shFolderExchange.getName()));
		}
		this.rootFolderSettings(shFolderExchange, shObject, shObjects, shFolderChild);
		log.info("...... {} Folder ({})", shFolderChild.getName(), shFolderChild.getId());
		shFolderRepository.save(shFolderChild);
		return shFolderChild;
	}

	private void rootFolderSettings(ShFolderExchange shFolderExchange, String shObject, Map<String, Object> shObjects,
			ShFolder shFolderChild) {
		if (shFolderExchange.getParentFolder() != null) {
			ShFolder parentFolder = shFolderRepository.findById(shFolderExchange.getParentFolder()).orElse(null);
			shFolderChild.setParentFolder(parentFolder);
			shFolderChild.setRootFolder((byte) 0);
		} else {
			if (shObjects.get(shObject)instanceof ShSiteExchange shSiteExchange
					&& shSiteExchange.getRootFolders().contains(shFolderExchange.getId())) {
				shFolderChild.setRootFolder((byte) 1);
				ShSite parentSite = shSiteRepository.findById(shSiteExchange.getId()).orElse(null);
				shFolderChild.setShSite(parentSite);
			}
		}
	}
}
