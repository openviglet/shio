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

import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.utils.ShFolderUtils;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShSitesContextURLProcessCache {
	private static final Log logger = LogFactory.getLog(ShSitesContextURLProcessCache.class);
	@Autowired
	ShSitesDetectContextURL shSitesDetectContextURL;
	@Autowired
	ShObjectRepository shObjectRepository;
	@Autowired
	ShFolderUtils shFolderUtils;

	@Cacheable(value = "url", key = "{#shSitesContextURL.getInfo().getContextURL(), #shSitesContextURL.getInfo().getContextURLOriginal()}", sync = true)
	public ShSitesContextURLInfo detectContextURL(ShSitesContextURL shSitesContextURL) {
		Date now = new Date();
		if (logger.isDebugEnabled())
			logger.debug("detectContextURL Before: " + shSitesContextURL.toString());
		shSitesDetectContextURL.detectContextURL(shSitesContextURL);
		ShSitesContextURLInfo shSitesContextURLInfo = new ShSitesContextURLInfo();
		shSitesContextURLInfo.setCacheEnabled(shSitesContextURL.getInfo().isCacheEnabled());
		shSitesContextURLInfo.setContextURL(shSitesContextURL.getInfo().getContextURL());
		shSitesContextURLInfo.setContextURLOriginal(shSitesContextURL.getInfo().getContextURLOriginal());
		shSitesContextURLInfo.setShContext(shSitesContextURL.getInfo().getShContext());
		shSitesContextURLInfo.setShFormat(shSitesContextURL.getInfo().getShFormat());
		shSitesContextURLInfo.setShLocale(shSitesContextURL.getInfo().getShLocale());
		shSitesContextURLInfo.setObjectId(shSitesContextURL.getInfo().getObjectId());
		shSitesContextURLInfo.setParentFolderId(shSitesContextURL.getInfo().getParentFolderId());
		shSitesContextURLInfo.setSiteId(shSitesContextURL.getInfo().getSiteId());

		if (shSitesContextURLInfo.getObjectId() != null) {
			setContextFromObject(shSitesContextURL, shSitesContextURLInfo);
		} else {
			shSitesContextURLInfo.setPageAllowGuestUser(true);
			shSitesContextURLInfo.setPageAllowRegisterUser(false);
			shSitesContextURLInfo.setStaticFile(false);
			shSitesContextURLInfo.setShPageGroups(null);
		}

		if (logger.isDebugEnabled()) {
			Date after = new Date();

			logger.debug("detectContextURL After: " + shSitesContextURL.toString());
			logger.debug("URL Time: " + (after.getTime() - now.getTime()));
		}

		return shSitesContextURLInfo;

	}

	private void setContextFromObject(ShSitesContextURL shSitesContextURL,
			ShSitesContextURLInfo shSitesContextURLInfo) {
		shObjectRepository.findById(shSitesContextURLInfo.getObjectId()).ifPresent(shObject -> {
			shSitesContextURLInfo.setStaticFile(isStaticFile(shSitesContextURL, shObject));

			if (shObject instanceof ShPost && shObject.getFurl().equals("index")) {
				ShFolder shFolder = shFolderUtils.getParentFolder(shObject);
				shSitesContextURLInfo.setPageAllowGuestUser(shFolder.isPageAllowGuestUser());
				shSitesContextURLInfo.setPageAllowRegisterUser(shFolder.isPageAllowRegisterUser());

				shSitesContextURLInfo.setShPageGroups(shFolder.getShPageGroups() != null
						? shFolder.getShPageGroups().toArray(new String[shFolder.getShPageGroups().size()])
						: null);
			} else {
				shSitesContextURLInfo.setPageAllowGuestUser(shObject.isPageAllowGuestUser());
				shSitesContextURLInfo.setPageAllowRegisterUser(shObject.isPageAllowRegisterUser());
				shSitesContextURLInfo.setShPageGroups(shObject.getShPageGroups() != null
						? shObject.getShPageGroups().toArray(new String[shObject.getShPageGroups().size()])
						: null);
			}
		});
	}

	private boolean isStaticFile(ShSitesContextURL shSitesContextURL, ShObjectImpl shObject) {
		return shSitesContextURL.getInfo().getObjectId() != null && shObject instanceof ShPost
				&& ((ShPostImpl) shObject).getShPostType().getName().equals(ShSystemPostType.FILE);
	}
}
