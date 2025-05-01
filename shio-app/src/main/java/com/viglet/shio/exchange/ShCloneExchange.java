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
import java.net.URI;
import java.util.Date;
import java.util.UUID;

import com.viglet.shio.utils.ShStaticFileUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
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
@Slf4j
public class ShCloneExchange {
	private final ShSiteImport shSiteImport;
	private final ShPostTypeImport shPostTypeImport;
	private final ShImportExchange shImportExchange;
	private final ShStaticFileUtils shStaticFileUtils;
	private final ResourceLoader resourceloader;

	@Autowired
	public ShCloneExchange(ShSiteImport shSiteImport,
						   ShPostTypeImport shPostTypeImport,
						   ShImportExchange shImportExchange,
						   ShStaticFileUtils shStaticFileUtils,
						   ResourceLoader resourceloader) {
		this.shSiteImport = shSiteImport;
		this.shPostTypeImport = shPostTypeImport;
		this.shImportExchange = shImportExchange;
		this.shStaticFileUtils = shStaticFileUtils;
		this.resourceloader = resourceloader;
	}

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
				FileUtils.copyURLToFile(URI.create("https://github.com/ShioCMS/bootstrap-site/archive/0.3.7.zip").toURL(),
						templateSiteFile);
			}
			shExchangeData = getTemplateAsCloneFromFile(templateSiteFile, shSite);
		} catch (IllegalStateException | IOException e) {
			log.error(e.getMessage(), e);
		}
		if (shExchangeData != null && shExchangeData.getShExchange() != null
				&& shExchangeData.getShExchange().getSites() != null) {
			shSite.setId(shExchangeData.getShExchange().getSites().getFirst().getId());
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

	public void importFromShExchangeData(ShExchangeData shExchangeData) {
		ShExchange shExchange = shExchangeData.getShExchange();
		if (hasPostTypes(shExchange)) {
			shPostTypeImport.importPostType(shExchange, true);
		}
		if (hasSites(shExchange)) {
			importSiteFromShExchangeData(shExchangeData);
		}
	}

	public void importSiteFromShExchangeData(ShExchangeData shExchangeData) {
		ShExchange shExchange = shExchangeData.getShExchange();
		if (hasSites(shExchange)) {
			shSiteImport.cloneSite(shExchangeData);
		}
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
			log.error(e.getMessage(), e);
		}
		return this.getTemplateAsCloneFromMultipartFile(multipartFile, shSite);
	}

	public void importNewSiteFromTemplateFile(File file) {
		ShExchangeData shExchangeData = this.getNewSiteFromTemplateFile(file);
		shExchangeData.getShExchangeFilesDirs().deleteExport();
		FileUtils.deleteQuietly(file);

	}

	public ShExchangeData getNewSiteFromTemplateFile(File file) {

		return this.getTemplateAsCloneFromFile(file, null);
	}
}
