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
package com.viglet.shio.url;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

/**
 * @author Alexandre Oliveira
 */

public class ShURLFormatter {

	private ShURLFormatter() {
		throw new IllegalStateException("URL Formatter");
	}

	public static String format(String url) {

		// 1. Covert to Lowercase
		String formattedURL = url.toLowerCase(Locale.ROOT);

		// 2. Convert HTML Symbol Entities to space, for examples: "Ampersand" euro or
		// Ampersand Hash Tag 174
		formattedURL = formattedURL.replaceAll("&.+;", " ");

		// 3. Remove accents
		formattedURL = StringUtils.stripAccents(formattedURL);

		// 4. Convert dot to hyphen
		formattedURL = formattedURL.replace("\\.", "-");

		// 5. Remove all characters that are not a-z or 0-9 or space or _ or hyphen
		formattedURL = formattedURL.replaceAll("[^a-z0-9 _-]", StringUtils.EMPTY);

		// 6. Convert one or more spaces to hyphen
		formattedURL = formattedURL.replaceAll("\\s+", "-");

		// 7. If exist space or hyphen into start or and of string, will be removed
		formattedURL = StringUtils.strip(formattedURL, " -");

		// 8. In 4. Convert dot to -, so I need rollback Folders that have .json in its
		// name.
		if (formattedURL.endsWith("-json"))
			formattedURL = formattedURL.replaceAll("-json$", ".json");

		return formattedURL;
	}
}
