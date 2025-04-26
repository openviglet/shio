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
package com.viglet.shio.website;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.utils.ShFolderUtils;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShSitesContextURLProcess {
	private static final Log logger = LogFactory.getLog(ShSitesContextURLProcess.class);
	@Autowired
	private ShSiteRepository shSiteRepository;
	@Autowired
	private ShSitesContextComponent shSitesContextComponent;
	@Autowired
	private ShFolderUtils shFolderUtils;
	@Autowired
	private ShSitesContextURLProcessCache shSitesContextURLProcessCache;

	private static final String SITE_HEADER = "x-sh-site";
	private static final String NO_CACHE_HEADER = "x-sh-nocache";
	private static final String FORMAT_PARAM = "sh-format";
	private static final String SEPARATOR = "/";

	public ShSitesContextURL getContextURL(HttpServletRequest request, HttpServletResponse response) {
		ShSitesContextURL shSitesContextURL = new ShSitesContextURL();
		shSitesContextURL.setRequest(request);
		shSitesContextURL.setResponse(response);

		String shXSiteName = request.getHeader(SITE_HEADER);
		shSitesContextURL.getInfo().setCacheEnabled(cacheIsEnabled(request));
		String context = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
		if (shXSiteName != null) {
			String contextOriginal = context.replaceAll("^/sites", StringUtils.EMPTY);
			if (request.getParameter(FORMAT_PARAM) != null) {
				String format = request.getParameter(FORMAT_PARAM);
				shSitesContextURL.getInfo()
						.setContextURL(String.format("/sites/%s/%s/en-us%s", shXSiteName, format, contextOriginal));
				shSitesContextURL.getInfo()
						.setContextURLOriginal(contextOriginal.concat(String.format("?%s=%s", FORMAT_PARAM, format)));
			} else {
				shSitesContextURL.getInfo().setContextURLOriginal(context.replaceAll("^/sites", StringUtils.EMPTY));
				shSitesContextURL.getInfo().setContextURL(String.format("/sites/%s/default/en-us%s", shXSiteName,
						shSitesContextURL.getInfo().getContextURLOriginal()));
			}
		} else {

			if (request.getParameter(FORMAT_PARAM) != null) {
				String[] contexts = context.split(SEPARATOR);
				contexts[3] = request.getParameter(FORMAT_PARAM);
				context = StringUtils.join(contexts, SEPARATOR);
			}

			shSitesContextURL.getInfo().setContextURLOriginal(context);
			shSitesContextURL.getInfo().setContextURL(shSitesContextURL.getInfo().getContextURLOriginal());
		}

		if (request.getParameter(FORMAT_PARAM) != null) {
			shSitesContextURL.getInfo().setShFormat(request.getParameter(FORMAT_PARAM));
		}

		ShSitesContextURLInfo shSitesContextURLInfo = shSitesContextURLProcessCache.detectContextURL(shSitesContextURL);
		shSitesContextURL.setInfo(shSitesContextURLInfo);

		return shSitesContextURL;

	}

	private boolean cacheIsEnabled(HttpServletRequest request) {
		return request.getHeader(NO_CACHE_HEADER) != null && !request.getHeader(NO_CACHE_HEADER).equals("1");
	}

	public void detectContextURL(ShSitesContextURL shSitesContextURL) {

		this.detectContextURL(shSitesContextURL.getInfo().getContextURL(), shSitesContextURL);
	}

	public void detectContextURL(String url, ShSitesContextURL shSitesContextURL) {
		shSitesContextURL.getInfo().setContextURL(url);
		String shSiteName = null;
		String[] contexts = url.split(SEPARATOR);
		for (int i = 1; i < contexts.length; i++) {
			switch (i) {
			case 1:
				shSitesContextURL.getInfo().setShContext(contexts[i]);
				break;
			case 2:
				shSiteName = contexts[i];
				break;
			case 3:
				shSitesContextURL.getInfo().setShFormat(contexts[i]);
				break;
			case 4:
				shSitesContextURL.getInfo().setShLocale(contexts[i]);
				break;
			default:
				break;
			}
		}

		ShSite shSite = shSiteRepository.findByFurl(shSiteName);
		shSitesContextURL.getInfo().setSiteId(shSite.getId());

		List<String> contentPath = shSitesContextComponent
				.contentPathFactory(shSitesContextURL.getInfo().getContextURL());

		String objectName = shSitesContextComponent.objectNameFactory(contentPath);

		ShFolder shFolder = shFolderUtils.folderFromPath(shSite,
				shSitesContextComponent.folderPathFactory(contentPath));
		if (shFolder != null) {
			shSitesContextURL.getInfo().setParentFolderId(shFolder.getId());
		} else {
			logger.info("No folder for " + shSitesContextURL.getInfo().getContextURL());
		}

		ShObjectImpl shObject = shSitesContextComponent.shObjectItemFactory(shSite, shFolder, objectName);
		if (shObject != null) {
			shSitesContextURL.getInfo().setObjectId(shObject.getId());
		}

	}

	public ShSiteRepository getShSiteRepository() {
		return shSiteRepository;
	}

	public void setShSiteRepository(ShSiteRepository shSiteRepository) {
		this.shSiteRepository = shSiteRepository;
	}

}
