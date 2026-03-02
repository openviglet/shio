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
package com.viglet.shio.utils;

import java.security.Principal;
import java.util.Date;

import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;

/**
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Component
public class ShHistoryUtils {


	private final ShHistoryRepository shHistoryRepository;
	private final ShPostUtils shPostUtils;
	private final ShFolderUtils shFolderUtils;

	public static final String CREATE = "CREATE";
	public static final String DELETE = "DELETE";
	public static final String UPDATE = "UPDATE";

	public ShHistoryUtils(ShHistoryRepository shHistoryRepository, ShPostUtils shPostUtils,
			ShFolderUtils shFolderUtils) {
		this.shHistoryRepository = shHistoryRepository;
		this.shPostUtils = shPostUtils;
		this.shFolderUtils = shFolderUtils;
	}

	public void commit(ShObjectImpl shObject, Principal principal, String action) {
		if (shObject != null) {
			String message = null;

			if (action.equals(ShHistoryUtils.CREATE)) {
				message = "Created %s %s.";
			} else if (action.equals(ShHistoryUtils.DELETE)) {
				message = "Deleted %s %s.";
			} else if (action.equals(ShHistoryUtils.UPDATE)) {
				message = "Updated %s %s.";
			}

			ShHistory shHistory = new ShHistory();
			shHistory.setDate(new Date());
			String description = null;
			ShSite shSite = null;

			if (shObject instanceof ShPostImpl shPostImpl) {
				description = String.format(message, shPostImpl.getTitle(), "Post");
				shSite = shPostUtils.getSite(shPostImpl);
			} else if (shObject instanceof ShFolder shFolder) {
				description = String.format(message, shFolder.getName(), "Folder");
				shSite = shFolderUtils.getSite(shFolder);
			} else if (shObject instanceof ShSite shSiteInst) {
				shSite = shSiteInst;
				description = String.format(message, shSite.getName(), "Site");
			}
			shHistory.setDescription(description);
			if (principal != null) {
				shHistory.setOwner(principal.getName());
			}
			shHistory.setShObject(shObject.getId());
			if (shSite != null)
				shHistory.setShSite(shSite.getId());
			shHistoryRepository.saveAndFlush(shHistory);
		}
	}
}
