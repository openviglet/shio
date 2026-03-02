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

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeAttrRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShPostTypeUtils {

	private final ShPostTypeRepository shPostTypeRepository;
	private final ShPostTypeAttrRepository shPostTypeAttrRepository;

	public ShPostTypeUtils(ShPostTypeRepository shPostTypeRepository, ShPostTypeAttrRepository shPostTypeAttrRepository) {
		this.shPostTypeRepository = shPostTypeRepository;
		this.shPostTypeAttrRepository = shPostTypeAttrRepository;
	}

	public Map<String, ShPostTypeAttr> toMap(ShPostType shPostType) {

		Set<ShPostTypeAttr> shPostTypeAttrList = shPostType.getShPostTypeAttrs();

		Map<String, ShPostTypeAttr> shPostTypeMap = new HashMap<>();
		for (ShPostTypeAttr shPostTypeAttr : shPostTypeAttrList)
			shPostTypeMap.put(shPostTypeAttr.getName(), shPostTypeAttr);

		return shPostTypeMap;

	}

	public ShPostType clone(ShPostType shPostType) {

		List<ShPostType> shPostTypes = shPostTypeRepository.findAll();

		Set<String> titles = new HashSet<>();
		int lowerPosition = 0;
		for (ShPostType shPostTypeCheck : shPostTypes) {
			titles.add(shPostTypeCheck.getTitle());
			if (shPostTypeCheck.getPosition() < lowerPosition)
				lowerPosition = shPostTypeCheck.getPosition();

		}

		ShPostType shPostTypeClone = new ShPostType();
		shPostTypeClone.setDate(new Date().toInstant());
		shPostTypeClone.setDescription(shPostType.getDescription());
		shPostTypeClone.setModifiedDate(new Date());

		shPostTypeClone.setObjectType(shPostType.getObjectType());
		shPostTypeClone.setOwner(shPostType.getOwner());
		shPostTypeClone.setSystem((byte) 0);

		String title = shPostType.getTitle();
		String name = shPostType.getName();
		String namePlural = shPostType.getNamePlural();
		if (titles.contains(String.format("Clone of %s", shPostType.getTitle()))) {
			int countSameTitle = 0;
			boolean uniqueTitle = false;
			while (!uniqueTitle) {
				title = String.format("Clone (%d) of %s", countSameTitle + 1, shPostType.getTitle());
				name = String.format("Clone%d%s", countSameTitle + 1, shPostType.getName());
				namePlural = String.format("Clone%d%s", countSameTitle + 1, shPostType.getNamePlural());
				if (!titles.contains(title) || (countSameTitle > titles.size())) {
					uniqueTitle = true;
				}
				countSameTitle++;
			}
		} else {
			title = String.format("Clone of %s", shPostType.getTitle());
			name = String.format("Clone%s", shPostType.getName());
			namePlural = String.format("Clone%s", shPostType.getNamePlural());
		}
		shPostTypeClone.setTitle(title);
		shPostTypeClone.setName(name);
		shPostTypeClone.setNamePlural(namePlural);

		shPostTypeRepository.save(shPostTypeClone);

		Set<ShPostTypeAttr> shPostTypeAttrs = shPostTypeAttrRepository.findByShPostType(shPostType);
		for (ShPostTypeAttr shPostTypeAttr : shPostTypeAttrs) {
			ShPostTypeAttr shPostTypeAttrClone = new ShPostTypeAttr();
			shPostTypeAttrClone.setDescription(shPostTypeAttr.getDescription());
			shPostTypeAttrClone.setIsSummary(shPostTypeAttr.getIsSummary());
			shPostTypeAttrClone.setIsTitle(shPostTypeAttr.getIsTitle());
			shPostTypeAttrClone.setLabel(shPostTypeAttr.getLabel());
			shPostTypeAttrClone.setName(shPostTypeAttr.getName());
			shPostTypeAttrClone.setOrdinal(shPostTypeAttr.getOrdinal());
			shPostTypeAttrClone.setRequired(shPostTypeAttr.getRequired());
			shPostTypeAttrClone.setWidgetSettings(shPostTypeAttr.getWidgetSettings());
			shPostTypeAttrClone.setShWidget(shPostTypeAttr.getShWidget());
			shPostTypeAttrClone.setShPostType(shPostTypeClone);

			shPostTypeAttrRepository.save(shPostTypeAttrClone);
		}

		return shPostTypeClone;
	}
}