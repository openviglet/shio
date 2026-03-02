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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.viglet.shio.api.folder.ShFolderPath;
import com.viglet.shio.persistence.model.auth.ShGroup;
import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShObjectUtils {

	private final ShFolderUtils shFolderUtils;
	private final ShObjectRepository shObjectRepository;
	private final ShUserRepository shUserRepository;
	private final ShPostUtils shPostUtils;

	public ShObjectUtils(ShFolderUtils shFolderUtils, ShObjectRepository shObjectRepository,
			ShUserRepository shUserRepository, ShPostUtils shPostUtils) {
		this.shFolderUtils = shFolderUtils;
		this.shObjectRepository = shObjectRepository;
		this.shUserRepository = shUserRepository;
		this.shPostUtils = shPostUtils;
	}

	public ShSite getSite(ShObjectImpl shObject) {
		return switch (shObject) {
			case ShPostImpl shPostImpl -> shPostUtils.getSite(shPostImpl);
			case ShFolder shFolder -> shFolderUtils.getSite(shFolder);
			default -> null;
		};
	}

	public boolean canAccess(Principal principal, String shObjectId) {
		ShUser shUser = null;
		ShObjectImpl shObject = shObjectRepository.findById(shObjectId).orElse(null);
		shUser = getShUser(principal, shUser);
		if (shObject != null) {
			if (userHasGroups(shUser)) {
				return isAdministrator(shUser) || hasAccess(shUser, shObject);
			} else {
				return true;
			}
		}

		return false;
	}

	private ShUser getShUser(Principal principal, ShUser shUser) {
		if (principal != null)
			shUser = shUserRepository.findByUsername(principal.getName());
		return shUser;
	}

	private boolean userHasGroups(ShUser shUser) {
		return shUser != null && shUser.getShGroups() != null;
	}

	private boolean isAdministrator(ShUser shUser) {
		boolean fullAccess = false;
		for (ShGroup shGroup : shUser.getShGroups()) {
			if (shGroup.getName().equals("Administrator")) {
				fullAccess = true;
			}
		}
		return fullAccess;
	}

	private boolean hasAccess(ShUser shUser, ShObjectImpl shObject) {
		Set<String> shGroups = new HashSet<>();
		Set<String> shUsers = new HashSet<>();
		shUser.getShGroups().forEach(shGroup -> shGroups.add(shGroup.getName()));
		shUsers.add(shUser.getUsername());
		return (shObjectRepository.countByIdAndShGroupsInOrIdAndShUsersInOrIdAndShGroupsIsNullAndShUsersIsNull(
				shObject.getId(), shGroups, shObject.getId(), shUsers, shObject.getId()) > 0);

	}

	public ShFolderPath objectPath(String id) {
		Optional<ShObject> shObject = shObjectRepository.findById(id);
		if (shObject.isPresent()) {
			if (shObject.get() instanceof ShSite shSite) {
				ShFolderPath shFolderPath = new ShFolderPath();
				shFolderPath.setFolderPath(null);
				shFolderPath.setCurrentFolder(null);
				shFolderPath.setBreadcrumb(null);
				shFolderPath.setShSite(shSite);
				return shFolderPath;
			} else if (shObject.get() instanceof ShFolder shFolder) {
				ShFolderPath shFolderPath = new ShFolderPath();
				String folderPath = shFolderUtils.folderPath(shFolder, true, false);
				List<ShFolder> breadcrumb = shFolderUtils.breadcrumb(shFolder);
				ShSite shSite = breadcrumb.get(0).getShSite();
				shFolderPath.setFolderPath(folderPath);
				shFolderPath.setCurrentFolder(shFolder);
				shFolderPath.setBreadcrumb(breadcrumb);
				shFolderPath.setShSite(shSite);
				return shFolderPath;
			} else if (shObject.get() instanceof ShPostImpl) {
				ShPostImpl shPost = shPostUtils.loadLazyPost(shObject.get().getId(), false);
				ShFolder shFolder = shPost.getShFolder();
				ShFolderPath shFolderPath = new ShFolderPath();
				String folderPath = shFolderUtils.folderPath(shFolder, true, false);
				List<ShFolder> breadcrumb = shFolderUtils.breadcrumb(shFolder);
				ShSite shSite = breadcrumb.get(0).getShSite();
				shFolderPath.setFolderPath(folderPath);
				shFolderPath.setCurrentFolder(shFolder);
				shFolderPath.setBreadcrumb(breadcrumb);
				shFolderPath.setShSite(shSite);
				shFolderPath.setShPost(shPost);
				return shFolderPath;
			}
		}
		return null;
	}
}
