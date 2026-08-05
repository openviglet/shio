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
 * The post-type names this system provisions — <b>every one of them has a creator</b> (SH293).
 *
 * <h2>Why eight names left this class</h2>
 *
 * <p>It used to declare thirteen, transcribed from the 2018 model in {@code import/post-types.json}.
 * Eight of them — {@code Photo}, {@code Video}, {@code Quote}, {@code Link}, {@code TextArea},
 * {@code Article}, {@code FolderIndex}, {@code Alias} — were referenced <b>nowhere but here</b>:
 * nothing created them, nothing looked them up, nothing shipped them. A constant like that is not
 * harmless, because it reads as a capability: {@code ShSystemPostType.ARTICLE} in an IDE's
 * completion list says this system knows what an Article is, and it does not. One of them was
 * already wrong and could not have been noticed — the 2018 model declares {@code Links} and the
 * constant said {@code Link}.</p>
 *
 * <p>Deleting them is the honest half of SH293's audit; the other half is that {@code File} is now
 * compared against the 2018 model the way the render three already were
 * ({@code ShFilePostTypeSourceTest}), because {@code ShStaticFileService} really does create it and
 * a field-name drift there breaks uploads rather than rendering. What is <em>not</em> claimed is
 * that this file lists the only post-types an instance holds: everything else is a user's, or an
 * import's, and named by data rather than by a constant.</p>
 *
 * <p>{@code ShSystemPostTypeInventoryTest} keeps it that way — a constant with no creator fails the
 * build, and the one exception is declared there with its reason.</p>
 *
 * @author Alexandre Oliveira
 */
public class ShSystemPostType {

	private ShSystemPostType() {
		throw new IllegalStateException("System Post Type Constants class");
	}

	/**
	 * Looked up, never created — the console's "last post type used" default.
	 *
	 * <p>{@code ShUserOnStartup} reads it to seed {@code ShUser.lastPostType} and tolerates its
	 * absence, which on a fresh install is what happens. Kept rather than deleted because the
	 * lookup is live code; declared in the inventory test rather than left to look like the
	 * others.</p>
	 */
	public static final String TEXT = "Text";

	/** Created on first upload by {@code ShStaticFileService.resolveOrCreateFilePostType} (SH38). */
	public static final String FILE = "File";

	/** The four the renderer provisions on demand — {@code ShRenderPostTypes.ensure} (SH183). */
	public static final String REGION = "Region";
	public static final String THEME = "Theme";
	public static final String PAGE_LAYOUT = "PageLayout";

	/**
	 * SH385 — one source path in, one address out.
	 *
	 * <p>A <b>system</b> type rather than a vocabulary one, unlike SH382's {@code Menu}, and SH249's
	 * test is what decides it: a menu needs no new mechanism because a template reads it, while a
	 * redirect has to be consulted by the <b>router</b> before a page is chosen. That is a new
	 * mechanism and it is the server's, so the server is entitled to know this name.</p>
	 */
	public static final String REDIRECT = "Redirect";

}
