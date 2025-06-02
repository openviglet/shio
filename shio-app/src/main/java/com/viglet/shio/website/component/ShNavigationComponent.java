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
package com.viglet.shio.website.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.viglet.shio.website.utils.ShSitesPostUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShNavigationComponent {
	@Autowired
	private ShSiteRepository shSiteRepository;
	@Autowired
	private ShFolderRepository shFolderRepository;
	@Autowired
	private ShSitesPostUtils shSitesPostUtils;

	public List<ShFolder> navigation(String siteName, boolean home) {
		ShSite shSite = shSiteRepository.findByName(siteName);
		ShFolder homeFolder = shFolderRepository.findByShSiteAndName(shSite, "Home");
		List<ShFolder> shFolders = new ArrayList<>();
		if (home && shSitesPostUtils.isVisiblePage(homeFolder))
			shFolders.add(homeFolder);

		shFolderRepository.findByParentFolderOrderByPositionAsc(homeFolder).forEach(shFolder -> {
			if (shSitesPostUtils.isVisiblePage(shFolder))
				shFolders.add(shFolder);

		});

		return shFolders;
	}

	public List<ShFolder> navigationFolder(String folderId, boolean home) {
		Optional<ShFolder> shFolderOptional = shFolderRepository.findById(folderId);
		if (shFolderOptional.isPresent()) {
			ShFolder shParentFolder = shFolderOptional.get();
			List<ShFolder> shFolders = shFolderRepository.findByParentFolderOrderByPositionAsc(shParentFolder);
			if (home && shSitesPostUtils.isVisiblePage(shParentFolder))
				shFolders.add(shParentFolder);

			return shFolders;
		}
		return Collections.emptyList();
	}
}
