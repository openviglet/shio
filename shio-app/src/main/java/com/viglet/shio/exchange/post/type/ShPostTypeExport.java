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
package com.viglet.shio.exchange.post.type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.viglet.shio.exchange.ShExchange;
import com.viglet.shio.exchange.ShExchangeFilesDirs;
import com.viglet.shio.exchange.utils.ShExchangeUtils;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;

/**
 * Export PostType.
 *
 * @author Alexandre Oliveira
 * @since 0.3.0
 */
@Component
public class ShPostTypeExport {
	private final ShPostTypeRepository shPostTypeRepository;
	private final ShExchangeUtils shExchangeUtils;

	@Autowired
	public ShPostTypeExport(ShPostTypeRepository shPostTypeRepository, ShExchangeUtils shExchangeUtils) {
		this.shPostTypeRepository = shPostTypeRepository;
		this.shExchangeUtils = shExchangeUtils;
	}

	public StreamingResponseBody exportObject(HttpServletResponse response) {
		ShExchangeFilesDirs shExchangeFilesDirs = new ShExchangeFilesDirs();
		if (shExchangeFilesDirs.generate()) {
			
			List<ShPostTypeExchange> postTypeExchanges = new ArrayList<>();

			shPostTypeRepository.findAll().forEach(shPostType -> postTypeExchanges.add(this.exportPostType(shPostType)));

			ShExchange shExchange = new ShExchange();
		
			shExchange.setPostTypes(postTypeExchanges);

			return shExchangeUtils.downloadZipFile("PostType", response, shExchange, shExchangeFilesDirs);
		} else {
			return null;
		}
	}

	public ShPostTypeExchange exportPostType(ShPostType shPostType) {
		ShPostTypeExchange shPostTypeExchange = new ShPostTypeExchange();
		shPostTypeExchange.setId(shPostType.getId());
		shPostTypeExchange.setName(shPostType.getName());
		shPostTypeExchange.setNamePlural(shPostType.getNamePlural());
		shPostTypeExchange.setLabel(shPostType.getTitle());
		shPostTypeExchange.setDate(shPostType.getDate());
		shPostTypeExchange.setDescription(shPostType.getDescription());

		shPostTypeExchange.setOwner(shPostType.getOwner());
		shPostTypeExchange.setSystem(shPostType.getSystem() == (byte) 1);

		if (!shPostType.getShPostTypeAttrs().isEmpty()) {
			Map<String, ShPostTypeFieldExchange> shPostTypeFieldExchanges = new HashMap<>();
			shPostType.getShPostTypeAttrs().forEach(shPostTypeAttr -> {
				ShPostTypeFieldExchange shPostTypeFieldExchange = this.exportPostTypeField(shPostTypeAttr);
				shPostTypeFieldExchanges.put(shPostTypeAttr.getName(), shPostTypeFieldExchange);
			});
			shPostTypeExchange.setFields(shPostTypeFieldExchanges);
		}

		return shPostTypeExchange;
	}

	public ShPostTypeFieldExchange exportPostTypeField(ShPostTypeAttr shPostTypeAttr) {
		ShPostTypeFieldExchange shPostTypeFieldExchange = new ShPostTypeFieldExchange();
		shPostTypeFieldExchange.setId(shPostTypeAttr.getId());
		shPostTypeFieldExchange.setLabel(shPostTypeAttr.getLabel());
		shPostTypeFieldExchange.setDescription(shPostTypeAttr.getDescription());

		shPostTypeFieldExchange.setOrdinal(shPostTypeAttr.getOrdinal());
		shPostTypeFieldExchange.setRequired(shPostTypeAttr.getRequired() == (byte) 1);
		shPostTypeFieldExchange.setSummary(shPostTypeAttr.getIsSummary() == (byte) 1);
		shPostTypeFieldExchange.setTitle(shPostTypeAttr.getIsTitle() == (byte) 1);
		shPostTypeFieldExchange.setWidget(shPostTypeAttr.getShWidget().getName());
		shPostTypeFieldExchange.setWidgetSettings(shPostTypeAttr.getWidgetSettings());

		if (!shPostTypeAttr.getShPostTypeAttrs().isEmpty()) {
			Map<String, ShPostTypeFieldExchange> shPostTypeFieldExchanges = new HashMap<>();
			shPostTypeAttr.getShPostTypeAttrs().forEach(shPostTypeAttrChild -> {
				ShPostTypeFieldExchange shPostTypeFieldExchangeChild = this.exportPostTypeField(shPostTypeAttrChild);
				shPostTypeFieldExchanges.put(shPostTypeAttrChild.getName(), shPostTypeFieldExchangeChild);
			});
			shPostTypeFieldExchange.setFields(shPostTypeFieldExchanges);
		}
		return shPostTypeFieldExchange;
	}
}
