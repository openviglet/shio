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
package com.viglet.shio.url;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.HandlerMapping;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.utils.ShFolderUtils;
import com.viglet.shio.utils.ShUtils;

/**
 * @author Alexandre Oliveira
 */
@Controller
public class ShURLScheme {
	@Autowired
	private HttpServletRequest request;
	@Autowired
	private ShFolderUtils shFolderUtils;
	@Autowired
	private ShUtils shUtils;

	private static final String X_SH_SITE = "x-sh-site";
	private static final String X_SH_CONTEXT = "x-sh-context";

	public String get(ShObjectImpl shObject) {
		String shXSiteName = request.getHeader(X_SH_SITE);
		String url = StringUtils.EMPTY;
		if (StringUtils.isNotEmpty(shXSiteName)) {
			var shContext = shUtils.sanitizedString(request.getHeader(X_SH_CONTEXT));
			if (StringUtils.isNotEmpty(shContext)) {
				url = "/".concat(shContext);
			}
		} else {
			String shContext = "sites";
			String shFormat = "default";
			String shLocale = "en-us";
			String shSiteName = null;
			if (shObject instanceof ShSite shSite) {
				shSiteName = shSite.getFurl();
			} else if (shObject instanceof ShFolder shFolder) {
				shSiteName = shFolderUtils.getSite(shFolder).getFurl();
			} else if (shObject instanceof ShPostImpl shPostImpl) {
				ShFolder shFolder = shPostImpl.getShFolder();
				shSiteName = shFolderUtils.getSite(shFolder).getFurl();
			}
			url = getURL(shSiteName, shContext, shFormat, shLocale);

		}
		return url;
	}

	public String get() {
		String shSiteName = shUtils.sanitizedString(request.getHeader(X_SH_SITE));
		String url = null;
		if (StringUtils.isNotEmpty(shSiteName)) {
			url = StringUtils.EMPTY;
		} else {
			String contextURL = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
			String shContext = null;
			String shFormat = null;
			String shLocale = null;
			String[] contexts = contextURL.split("/");

			for (int i = 1; i < contexts.length; i++) {
				switch (i) {
				case 1:
					shContext = contexts[i];
					break;
				case 2:
					shSiteName = contexts[i];
					break;
				case 3:
					shFormat = contexts[i];
					break;
				case 4:
					shLocale = contexts[i];
					break;
				default:
					break;
				}

			}
			url = getURL(shSiteName, shContext, shFormat, shLocale);
		}
		return url;
	}

	private String getURL(String shSiteName, String shContext, String shFormat, String shLocale) {
		return String.format("/%s/%s/%s/%s", shContext, shSiteName, shFormat, shLocale);
	}

	public String generateFolderLink(ShFolder shFolder) {
		String link = get(shFolder);
		link = link + shFolderUtils.folderPath(shFolder, true, false);
		return link;
	}

}
