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
package com.viglet.shio.utils;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import com.viglet.shio.api.ShJsonView.ShJsonViewGenericType;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShUtils {

	public String sanitizedString(String string) {
		return (string != null) ? string.replaceAll("[\n\r\t]", "_") : null;
	}

	public static String asJsonString(final Object obj) throws ShUtilsException {
		try {
			return getObjectMapper().writeValueAsString(obj);
		} catch (Exception e) {
			throw new ShUtilsException(e);
		}
	}

	public static String asJsonStringAndView(final Object obj, Class<? extends ShJsonViewGenericType> clazz)
			throws ShUtilsException {
		try {
			ObjectMapper mapper = getObjectMapper();
			mapper = mapper.rebuild().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false).build();
			return mapper.writerWithView(clazz).writeValueAsString(obj);
		} catch (Exception e) {
			throw new ShUtilsException(e);
		}
	}

	private static ObjectMapper getObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper = mapper.rebuild().findAndAddModules().build();
		return mapper;
	}

	public boolean isJSONValid(String json) {
		if (StringUtils.isBlank(json)) {
			return false;
		} else {
			try {
				new JSONObject(json);
			} catch (JSONException ex) {
				try {
					new JSONArray(json);
				} catch (JSONException ex1) {
					return false;
				}
			}
		}
		return true;
	}
}
