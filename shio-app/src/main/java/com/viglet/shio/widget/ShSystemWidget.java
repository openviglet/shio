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
	/**
	 * SH252 — a stylesheet is not an HTML document.
	 *
	 * <p>A {@code Theme}'s {@code CSS} field was declared {@code Ace Editor - HTML} because that was
	 * the closest thing seeded, which cost three things: the console gave a stylesheet an HTML editor,
	 * SH191's projection gave it a {@code .html} sidecar, and — the one that is a defect rather than an
	 * inconvenience — {@code ACE_HTML} is in the verify service's {@code MARKUP_WIDGETS}, so the SH100
	 * routes check and the SH101 digest <b>parsed a stylesheet as HTML</b> looking for links and
	 * images.</p>
	 */
	public static final String ACE_CSS = "Ace Editor - CSS";
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
	/**
	 * SH1015 — an ordered array of typed blocks, where {@link #HTML_EDITOR} is one opaque
	 * string. The HTML widget stays for imported bodies: `shio convert` produces HTML, and
	 * SH956's sanitizer already guards that path.
	 */
	public static final String BLOCKS = "Blocks";
}
