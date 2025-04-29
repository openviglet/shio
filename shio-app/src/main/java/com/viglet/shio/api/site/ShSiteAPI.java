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
package com.viglet.shio.api.site;

import java.io.IOException;

import java.security.Principal;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.viglet.shio.utils.ShIntegrationUtils;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.http.client.ClientProtocolException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.annotation.JsonView;
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.api.folder.ShFolderList;
import com.viglet.shio.bean.ShFolderTinyBean;
import com.viglet.shio.bean.ShPostTypeReport;
import com.viglet.shio.exchange.ShCloneExchange;
import com.viglet.shio.exchange.ShExchangeData;
import com.viglet.shio.exchange.site.ShSiteExport;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.report.ShReportPostType;
import com.viglet.shio.url.ShURLFormatter;
import com.viglet.shio.utils.ShHistoryUtils;
import com.viglet.shio.website.nodejs.ShSitesNodeJS;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Site Rest API
 * 
 * @author Alexandre Oliveira
 * @since 0.3.0
 * 
 */
@RestController
@RequestMapping("/api/v2/site")
@Tag( name = "Site", description = "Site API")
public class ShSiteAPI {
	static final Logger logger = LogManager.getLogger(ShSiteAPI.class);

	@Autowired
	private ShSiteRepository shSiteRepository;
	@Autowired
	private ShFolderRepository shFolderRepository;
	@Autowired
	private ShSiteExport shSiteExport;
	@Autowired
	private ShSitesNodeJS shSitesNodeJS;
	@Autowired
	private ShCloneExchange shCloneExchange;
	@Autowired
	private ShHistoryUtils shHistoryUtils;
	@Autowired
	private ShReportPostType shReportPostType;
	@Autowired
	private ShIntegrationUtils shIntegrationUtils;
	@GetMapping
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public List<ShSite> shSiteList(final Principal principal) {
		if (principal != null) {
			return shSiteRepository.findByOwnerOrOwnerIsNull(principal.getName());
		} else {
			return shSiteRepository.findAll();
		}
	}

	@GetMapping("/{id}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShSite shSiteEdit(@PathVariable String id) {
		return shSiteRepository.findById(id).orElse(null);
	}

	@PutMapping("/{id}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShSite shSiteUpdate(@PathVariable String id, @RequestBody ShSite shSite, Principal principal) {
		Optional<ShSite> shSiteOptional = shSiteRepository.findById(id);
		if (shSiteOptional.isPresent()) {
			ShSite shSiteEdit = shSiteOptional.get();
			shSiteEdit.setDate(new Date());
			shSiteEdit.setName(shSite.getName());
			shSiteEdit.setDescription(shSite.getDescription());
			shSiteEdit.setUrl(shSite.getUrl());
			shSiteEdit.setPostTypeLayout(shSite.getPostTypeLayout());
			shSiteEdit.setSearchablePostTypes(shSite.getSearchablePostTypes());
			shSiteEdit.setFormSuccess(shSite.getFormSuccess());
			shSiteEdit.setFurl(ShURLFormatter.format(shSite.getName()));
			shSiteRepository.save(shSiteEdit);

			shHistoryUtils.commit(shSite, principal, ShHistoryUtils.UPDATE);

			return shSiteEdit;
		}

		return null;

	}

	@DeleteMapping("/{id}")
	@Transactional
	public boolean shSiteDelete(@PathVariable String id, Principal principal) {
		ShSite shSite = shSiteRepository.findById(id).orElse(null);

		Set<ShFolder> shFolders = shFolderRepository.findByShSiteAndRootFolder(shSite, (byte) 1);

		for (ShFolder shFolder : shFolders) {
			try {
				shIntegrationUtils.deleteFolder(shFolder);
			} catch (ClientProtocolException e) {
				logger.error("shSiteDelete ClientProtocolException: ", e);
			} catch (IOException e) {
				logger.error("shSiteDelete IOException: ", e);
			}
		}

		shSiteRepository.delete(id);

		shHistoryUtils.commit(shSite, principal, ShHistoryUtils.DELETE);

		return true;
	}

	
	@PostMapping
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShSite shSiteAdd(@RequestBody ShSite shSite, final Principal principal) {

		shSite.setDate(new Date());
		shSite.setOwner(principal.getName());
		shSite.setFurl(ShURLFormatter.format(shSite.getName()));

		ShExchangeData shExchangeData = shCloneExchange.getDefaultTemplateToSite(shSite);

		shCloneExchange.importFromShExchangeData(shExchangeData);
		
		shExchangeData.getShExchangeFilesDirs().deleteExport();
		
		shHistoryUtils.commit(shSite, principal, ShHistoryUtils.CREATE);

		return shSite;
	}

	@GetMapping("/{id}/folder")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShFolderList shSiteRootFolder(@PathVariable String id) {
		ShSite shSite = shSiteRepository.findById(id).orElse(null);
		Set<ShFolderTinyBean> shFolders = shFolderRepository.findByShSiteAndRootFolderTiny(shSite, (byte) 1);
		ShFolderList shFolderList = new ShFolderList();
		shFolderList.setShFolders(shFolders);
		shFolderList.setShSite(shSite);
		return shFolderList;

	}

	@GetMapping("/{id}/type/count")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	@Cacheable(value = "report", key = "#id", sync = true)
	public List<ShPostTypeReport> shSitePostTypeCount(@PathVariable String id) {
		return shReportPostType.postTypeCountBySite(id);

	}

	@ResponseBody
	@GetMapping(value = "/{id}/export", produces = "application/zip")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public StreamingResponseBody shSiteExport(@PathVariable String id, HttpServletResponse response) {

		return shSiteExport.exportObject(id, response);

	}

	@ResponseBody
	@GetMapping(value = "/{id}/nodejs", produces = "application/zip")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public StreamingResponseBody shSiteNodeJS(@PathVariable String id, HttpServletResponse response) {

		return shSitesNodeJS.exportApplication(id, response);

	}

	@GetMapping("/model")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShSite shSiteStructure() {
		return new ShSite();
	}

}
