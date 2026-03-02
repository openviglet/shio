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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.repository.post.ShPostAttrRepository;
import com.viglet.shio.website.utils.ShSitesPostUtils;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShGetRelationComponent {
	private final ShPostAttrRepository shPostAttrRepository;
	private final ShSitesPostUtils shSitesPostUtils;

	public ShGetRelationComponent(ShPostAttrRepository shPostAttrRepository, ShSitesPostUtils shSitesPostUtils) {
		this.shPostAttrRepository = shPostAttrRepository;
		this.shSitesPostUtils = shSitesPostUtils;
	}

	public List<Map<String, ShPostAttr>> findByPostAttrId(String postAttrId) {
		Optional<ShPostAttr> shPostAttr = shPostAttrRepository.findById(postAttrId);

		return shPostAttr.isPresent()
				? shSitesPostUtils.relationToMap(shSitesPostUtils.getPostAttrByStage(shPostAttr.get()))
				: null;
	}
}
