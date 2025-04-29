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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.UUID;

import com.viglet.shio.utils.ShStaticFileUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.shio.exchange.post.type.ShPostTypeImport;
import com.viglet.shio.exchange.site.ShSiteExchange;
import com.viglet.shio.exchange.site.ShSiteImport;
import com.viglet.shio.persistence.model.site.ShSite;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShCloneExchange {
	private static final Logger logger = LogManager.getLogger(ShCloneExchange.class);
	@Autowired
	private ShSiteImport shSiteImport;
	@Autowired
	private ShPostTypeImport shPostTypeImport;
	@Autowired
	private ShImportExchange shImportExchange;
	@Autowired
	private ShStaticFileUtils shStaticFileUtils;
	@Autowired
	private ResourceLoader resourceloader;

	public ShExchangeData getDefaultTemplateToSite(ShSite shSite) {

		ShExchangeData shExchangeData = null;

		File templateSiteFile = new File(shStaticFileUtils.getTmpDir().getAbsolutePath()
				.concat(File.separator + "template-site-" + UUID.randomUUID() + ".zip"));

		try {
			Resource resource = resourceloader.getResource("classpath:/import/bootstrap-site.zip");

			if (resource.exists()) {
				InputStream is = resource.getInputStream();
				FileUtils.copyInputStreamToFile(is, templateSiteFile);
			} else {
				FileUtils.copyURLToFile(new URL("https://github.com/ShioCMS/bootstrap-site/archive/0.3.7.zip"),
						templateSiteFile);
			}
			shExchangeData = getTemplateAsCloneFromFile(templateSiteFile, shSite);
		} catch (IllegalStateException | IOException e) {
			logger.error(e);
		}
		if (shExchangeData != null && shExchangeData.getShExchange() != null
				&& shExchangeData.getShExchange().getSites() != null) {
			shSite.setId(shExchangeData.getShExchange().getSites().get(0).getId());
		}
		FileUtils.deleteQuietly(templateSiteFile);

		return shExchangeData;
	}

	public ShExchangeData getTemplateAsCloneFromMultipartFile(MultipartFile multipartFile, ShSite shSite) {
		ShExchangeFilesDirs shExchangeFilesDirs = shImportExchange.extractZipFile(multipartFile);

		if (shExchangeFilesDirs.getExportDir() != null) {
			ShExchange shExchange = changeObjectIdsFromExportToClone(shSite, shExchangeFilesDirs);
			return new ShExchangeData(shExchange, shExchangeFilesDirs);
		} else {
			return null;
		}
	}

	public boolean importFromShExchangeData(ShExchangeData shExchangeData) {
		ShExchange shExchange = shExchangeData.getShExchange();
		boolean isOk = false;
		if (hasPostTypes(shExchange)) {
			shPostTypeImport.importPostType(shExchange, true);
			isOk = true;
		}
		if (hasSites(shExchange)) {
			importSiteFromShExchangeData(shExchangeData);
			isOk = true;
		}
		return isOk;
	}

	public ShSite importSiteFromShExchangeData(ShExchangeData shExchangeData) {
		ShSite shSite = null;
		ShExchange shExchange = shExchangeData.getShExchange();
		if (hasSites(shExchange)) {
			shSite = shSiteImport.cloneSite(shExchangeData);
		}
		return shSite;
	}

	private ShExchange changeObjectIdsFromExportToClone(ShSite shSite, ShExchangeFilesDirs shExchangeFilesDirs) {

		ShExchange shExchange = shExchangeFilesDirs.readExportFile();

		if (hasSites(shExchange)) {
			shExchange = shSiteImport.prepareClone(shExchange, shExchangeFilesDirs.getExportDir());
			for (ShSiteExchange shSiteExchange : shExchange.getSites()) {
				shSiteExchange.setDate(new Date());
				if (shSite != null) {

					if (shSite.getId() != null && !shSite.getId().trim().isEmpty())
						shSiteExchange.setId(shSite.getId());
					shSiteExchange.setOwner(shSite.getOwner());
					shSiteExchange.setFurl(shSite.getFurl());
					shSiteExchange.setName(shSite.getName());
					shSiteExchange.setDescription(shSite.getDescription());
					shSiteExchange.setUrl(shSite.getUrl());
				}

			}
			return shExchange;
		} else
			return shExchange;
	}

	private boolean hasSites(ShExchange shExchange) {
		return shExchange != null && shExchange.getSites() != null && !shExchange.getSites().isEmpty();
	}

	private boolean hasPostTypes(ShExchange shExchange) {
		return shExchange != null && shExchange.getPostTypes() != null && !shExchange.getPostTypes().isEmpty();
	}

	public ShExchangeData getTemplateAsCloneFromFile(File file, ShSite shSite) {

		MultipartFile multipartFile = null;
		try {
			FileInputStream input = new FileInputStream(file);
			multipartFile = new MockMultipartFile(file.getName(), IOUtils.toByteArray(input));
		} catch (IOException e) {
			logger.error(e);
		}

		return this.getTemplateAsCloneFromMultipartFile(multipartFile, shSite);
	}

	public ShSite importNewSiteFromTemplateFile(File file) {
		ShExchangeData shExchangeData = this.getNewSiteFromTemplateFile(file);

		ShSite shSite = this.importSiteFromShExchangeData(shExchangeData);

		shExchangeData.getShExchangeFilesDirs().deleteExport();
		FileUtils.deleteQuietly(file);

		return shSite;
	}

	public ShExchangeData getNewSiteFromTemplateFile(File file) {

		return this.getTemplateAsCloneFromFile(file, null);
	}
}
