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
package com.viglet.shio.exchange;

import java.io.File;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.shio.exchange.post.ShPostImport;
import com.viglet.shio.exchange.post.type.ShPostTypeImport;
import com.viglet.shio.exchange.site.ShSiteImport;
import com.viglet.shio.exchange.utils.ShExchangeUtils;

/**
 * @author Alexandre Oliveira
 */
@Component
@Slf4j
public class ShImportExchange {
	private final ShSiteImport shSiteImport;
	private final ShPostTypeImport shPostTypeImport;
	private final ShPostImport shPostImport;
	private final ShExchangeUtils shExchangeUtils;

	@Autowired
	public ShImportExchange(ShSiteImport shSiteImport, ShPostTypeImport shPostTypeImport, ShPostImport shPostImport,
							ShExchangeUtils shExchangeUtils) {
		this.shSiteImport = shSiteImport;
		this.shPostTypeImport = shPostTypeImport;
		this.shPostImport = shPostImport;
		this.shExchangeUtils = shExchangeUtils;
	}


	public ShExchange importFromMultipartFile(MultipartFile multipartFile) {
		log.info("Unzip Package");
		ShExchangeFilesDirs shExchangeFilesDirs = this.extractZipFile(multipartFile);

		if (shExchangeFilesDirs.getExportDir() != null) {
			ShExchange shExchange = shExchangeFilesDirs.readExportFile();
			this.importObjects(new ShExchangeData(shExchange, shExchangeFilesDirs));

			shExchangeFilesDirs.deleteExport();
			return shExchange;
		} else {
			return null;
		}
	}

	private void importObjects(ShExchangeData shExchangeData) {
		ShExchange shExchange = shExchangeData.getShExchange();
		File extractFolder = shExchangeData.getShExchangeFilesDirs().getExportDir();

		if (shExchange != null) {
			if (shExchange.getPostTypes() != null && !shExchange.getPostTypes().isEmpty())
				shPostTypeImport.importPostType(shExchange, false);

			if (shExchange.getSites() != null && !shExchange.getSites().isEmpty()) {
				shSiteImport.importSite(shExchange, extractFolder);
			} else if (shExchange.getFolders() == null && shExchange.getPosts() != null) {
				ShExchangeObjectMap shExchangeObjectMap = shSiteImport.prepareImport(shExchange);
                shExchange.getPosts().forEach(shPostExchange -> shPostImport.createShPost(
						new ShExchangeContext(extractFolder, false), shPostExchange, shExchangeObjectMap));
			}
		}
	}

	public ShExchangeFilesDirs extractZipFile(MultipartFile file) {
		return shExchangeUtils.extractZipFile(file);
	}
}
