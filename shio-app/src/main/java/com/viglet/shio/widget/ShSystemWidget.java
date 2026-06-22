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
package com.viglet.shio.widget;

/**
 * @author Alexandre Oliveira
 */
public class ShSystemWidget {

	private ShSystemWidget() {
		throw new IllegalStateException("System Widget Constants class");
	}

	public static final String HIDDEN = "Hidden";
	public static final String TEXT = "Text";
	public static final String ACE_JS = "Ace Editor - Javascript";
	public static final String ACE_HTML = "Ace Editor - HTML";
	public static final String HTML_EDITOR = "HTML Editor";
	public static final String FILE = "File";
	public static final String TEXT_AREA = "Text Area";
	public static final String COMBO_BOX = "Combo Box";
	public static final String CONTENT_SELECT = "Content Select";
	public static final String RELATOR = "Relator";
	// SH60: reCAPTCHA widget deprecated (orphaned seed removed; not ported to React).
	public static final String FORM_CONFIGURATION = "Form Configuration";
	public static final String DATE = "Date";
	public static final String MULTI_SELECT = "Multi Select";
	public static final String TAB = "Tab";
	public static final String CHECK_BOX = "Check Box";
}
