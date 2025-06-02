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
package com.viglet.shio.graphql;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.CaseFormat;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.impl.ShPostAttrImpl;
import com.viglet.shio.utils.ShObjectUtils;

/**
 * GraphQL Utils.
 *
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
@Component
public class ShGraphQLUtils {

	private final ShObjectUtils shObjectUtils;

	@Autowired
	public ShGraphQLUtils(ShObjectUtils shObjectUtils) {
		this.shObjectUtils = shObjectUtils;
	}

	public String normalizedField(String object) {
		return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, object.toLowerCase().replace(" ", "_").replace("-", "_"));
	}

	public String normalizedName(String name) {
		if (StringUtils.isNotEmpty(name)) {
			char[] c = name.replace(" ","_").replace("-", "_").toCharArray();
			c[0] = Character.toLowerCase(c[0]);
			return  StringUtils.remove(WordUtils.capitalizeFully(new String(c), '_'), "_");
		} else {
			return StringUtils.EMPTY;
		}
	}

	public Map<String, String> graphQLAttrsByPost(ShPost shPost) {

		Map<String, String> shPostAttrMap = new HashMap<>();
		if (shPost != null) {
			shPostAttrMap.put(ShGraphQLConstants.ID, shPost.getId());
			shPostAttrMap.put(ShGraphQLConstants.TITLE, shPost.getTitle());
			shPostAttrMap.put(ShGraphQLConstants.DESCRIPTION, shPost.getSummary());
			shPostAttrMap.put(ShGraphQLConstants.FURL, shPost.getFurl());
			shPostAttrMap.put(ShGraphQLConstants.MODIFIER, shPost.getOwner());
			shPostAttrMap.put(ShGraphQLConstants.PUBLISHER, shPost.getPublisher());
			shPostAttrMap.put(ShGraphQLConstants.FOLDER, shPost.getShFolder().getName());
			shPostAttrMap.put(ShGraphQLConstants.SITE, shObjectUtils.getSite(shPost).getName());
			for (ShPostAttrImpl shPostAttr : shPost.getShPostAttrs()) {
				String postTypeAttrName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL,
						shPostAttr.getShPostTypeAttr().getName().toLowerCase().replace("-", "_"));
				shPostAttrMap.put(postTypeAttrName, shPostAttr.getStrValue());
			}
		}
		return shPostAttrMap;
	}

}
