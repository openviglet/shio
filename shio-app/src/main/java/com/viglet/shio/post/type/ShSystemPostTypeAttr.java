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
package com.viglet.shio.post.type;

/**
 * @author Alexandre Oliveira
 */
public class ShSystemPostTypeAttr {

	private ShSystemPostTypeAttr() {
		throw new IllegalStateException("System Post Type Attr Constants class");
	}

	public static final String ID = "id";
	public static final String TITLE = "TITLE";
	public static final String DESCRIPTION = "DESCRIPTION";
	public static final String FILE = "FILE";
	public static final String TEXT = "TEXT";
	/**
	 * SH195 — a Theme's design tokens, as W3C Design Tokens (DTCG) JSON.
	 *
	 * <p>Adopted rather than invented: {@code {"color":{"accent":{"$value":"#0b5","$type":"color"}}}}
	 * is what Style Dictionary, Figma and Tokens Studio already export, so a vocabulary this project
	 * made up would be one more dialect for an agent to learn and one fewer tool that can read it.
	 * The runtime form is CSS custom properties on {@code :root} — {@code --color-accent} — which any
	 * stylesheet consumes and which leaves raw {@code CSS} as the escape hatch rather than the
	 * interface.</p>
	 */
	public static final String TOKENS = "TOKENS";

	public static final String CSS = "CSS";
	public static final String JAVASCRIPT = "JAVASCRIPT";
	public static final String HTML = "HTML";
	public static final String PAGE_LAYOUT = "PAGE-LAYOUT";
	public static final String THEME = "THEME";

	/**
	 * SH385 — a {@code Redirect}'s two fields.
	 *
	 * <p>{@code TO} holds an <b>address</b> (a friendly URL in this site, or an absolute URL for a
	 * path that now lives somewhere else). {@code PERMANENT} decides 301 against 302, and it is a
	 * checkbox rather than a status-code field because those are the only two answers anybody wants
	 * and a free-text code would let somebody write 307 into a routing decision.</p>
	 *
	 * <p>There is deliberately no {@code FROM}: a redirect's source is the post's own <b>friendly
	 * URL</b>. See {@code ShRenderPostTypes} for why that is the whole design.</p>
	 */
	/**
	 * SH389 — a site's ordered script declarations, as JSON.
	 *
	 * <p>{@code Ace Editor - Javascript} holding JSON, which is {@code Theme.TOKENS}' precedent for its
	 * reason (SH195): the widget catalogue has no JSON editor and JSON is valid JavaScript. An array,
	 * because the whole meaning of the value is <b>its order</b> — consent has to load before what it
	 * gates, and that is the property four per-layout copies could not keep.</p>
	 */
	public static final String SCRIPTS = "SCRIPTS";

	public static final String TO = "TO";
	public static final String PERMANENT = "PERMANENT";
	public static final String CONTENT = "CONTENT";
	public static final String PHOTO = "PHOTO";
	public static final String VIDEO = "VIDEO";
	public static final String LINK = "LINK";
	public static final String RELATION = "RELATION";
	public static final String CACHED = "CACHED";
}
