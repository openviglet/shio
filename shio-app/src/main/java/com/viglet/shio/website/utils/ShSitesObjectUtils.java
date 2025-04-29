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
package com.viglet.shio.website.utils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.viglet.shio.url.ShURLScheme;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.utils.ShFolderUtils;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShSitesObjectUtils {

	@Autowired
	private ShURLScheme shURLScheme;
	@Autowired
	private ShObjectRepository shObjectRepository;
	@Autowired
	private ShSitesPostUtils shSitesPostUtils;

	public String generateObjectLinkById(String objectId) {
		if (objectId != null) {
			Optional<ShObject> shObjectOptional = shObjectRepository.findById(objectId);
			if (shObjectOptional.isPresent()) {
				ShObjectImpl shObject = shObjectOptional.get();
				if (shObject instanceof ShPostImpl shPostImpl) {
					return shSitesPostUtils.generatePostLink(shPostImpl);
				} else if (shObject instanceof ShFolder shFolder) {
					return shURLScheme.generateFolderLink(shFolder);
				}
			}

		}
		return null;
	}

	public String generateImageLinkById(String objectId, int scale) {
		if (objectId != null) {
			Optional<ShObject> shObjectOptional = shObjectRepository.findById(objectId);
			if (shObjectOptional.isPresent()) {
				ShObjectImpl shObject = shObjectOptional.get();
				if (shObject instanceof ShPostImpl shPostImpl) {
					if (scale == 1) {
						return shSitesPostUtils.generatePostLink(shPostImpl);
					} else {
						return shSitesPostUtils.generatePostLink(shPostImpl).replaceAll("^/store/file_source",
								String.format("/image/scale/%d", scale));
					}
				} else {
					return null;
				}
			}

		}
		return null;
	}

	public String generateObjectLink(ShObjectImpl shObject) {
		return this.generateObjectLinkById(shObject.getId());
	}
}
