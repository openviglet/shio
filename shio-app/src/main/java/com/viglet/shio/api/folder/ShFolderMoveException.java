/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.folder;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * The three ways a folder move is not a move at all (SH137, SH1137).
 *
 * <p>All three live on the shared writer rather than on either caller, because both
 * surfaces can reach them: the console's move dialog navigates <em>down</em> from the
 * site root, so a curator can walk into a child of the folder they are dragging, and
 * an agent can address any {@code to} it likes.</p>
 *
 * <p><b>{@link CrossSite} is here because the third one was not (SH1137).</b> The agent's
 * {@code folder.move} refused a cross-site address in its own op, and the writer did not — so
 * {@code PUT /api/v2/object/moveto/{id}} carried a folder into another site's root and left every
 * post beneath it pointing at the old {@code shSite}. Found by a run, not by reading. A refusal
 * that lives on one caller is a refusal the other caller does not have.</p>
 *
 * <p>They are {@link ShTeachingException}s so the same refusal renders on the console
 * API and the agent surface without either one translating it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShFolderMoveException {

    private ShFolderMoveException() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * A 409: the destination is the folder itself, or somewhere inside it.
     *
     * <p>This is the one that had to be a guard rather than a review note. Before SH137
     * nothing checked it, and re-parenting a folder into its own subtree does not fail —
     * it succeeds, and produces a cycle: the subtree becomes unreachable from the site
     * root (so every path walk stops finding it) and the recursive delete walks the loop
     * until the stack runs out. A tree that cannot be repaired through the UI that broke
     * it is the worst outcome available here.</p>
     */
    public static class Cycle extends ShTeachingException {

        public Cycle(String folderName, String destinationName) {
            super(ShProblem.conflict("folder-move-cycle",
                            "A folder cannot move into itself or into its own subtree")
                    .withDetail("'" + destinationName + "' is inside '" + folderName
                            + "', so the move would detach the subtree from the site root.")
                    .withFix("Pick a destination outside '" + folderName
                            + "' — a sibling, an ancestor, or the site root."));
        }
    }

    /**
     * A 409: the destination belongs to another site.
     *
     * <h2>What happened without it</h2>
     *
     * <p>The move succeeded. The folder's parent became the new site's root, and every post beneath
     * it kept the old site in {@code shSite} — so delivery, which resolves a page by
     * {@code findByShSite_IdAndFurlAndState}, went on answering it under the <em>old</em> site while
     * the tree showed it under the new one. That is exactly the split the {@code ShFolderSite}
     * invariant exists to prevent, arrived at from the other direction. And because
     * {@code requireUrlFree} never runs for posts beneath a folder, the destination could end up
     * with two posts at one URL.</p>
     *
     * <h2>Why refusing, and not carrying the subtree over</h2>
     *
     * <p>Carrying it over means re-pointing every post row's site, checking each friendly URL
     * against the destination's, and treating every retired address as SH1064 treats a rename. That
     * is a bigger operation than "move", it is the one an export and an import already perform, and
     * getting it half-right is how a site ends up with two pages at one URL and no record of it.
     * Refusing cannot surprise anybody (P7), and it makes the two surfaces agree by construction
     * rather than by each remembering to check.</p>
     */
    public static class CrossSite extends ShTeachingException {

        public CrossSite(String folderName, String from, String to) {
            super(ShProblem.conflict("folder-move-cross-site",
                            "A folder cannot move to another site")
                    .withDetail("'" + folderName + "' is in '" + from + "' and the destination is "
                            + "in '" + to + "'. A site is a separate tree with its own addresses, "
                            + "ACLs and tenant; the pages inside this folder would keep answering "
                            + "under '" + from + "' while the tree showed them under '" + to + "'.")
                    .withFix("Move the folder within '" + from + "', or carry the content across "
                            + "with `shio export` and `shio import`, which re-point every page and "
                            + "check each URL against the destination site.")
                    .withExample("shio export --site " + from + " && shio import"));
        }
    }

    /**
     * A 409: the folder is a site's root.
     *
     * <p>A root folder is what carries {@code shSite} (SH128) and what every address in
     * the site resolves from. Moving it would either orphan the site or nest one site's
     * tree inside another's, and neither is what anybody meant by "move".</p>
     */
    public static class Root extends ShTeachingException {

        public Root(String siteName) {
            super(ShProblem.conflict("root-folder-move",
                            "A site's root folder cannot be moved")
                    .withDetail("It is the folder that carries the site '" + siteName
                            + "' and the one every address in that site resolves from.")
                    .withFix("Move the folders inside it instead, or rename the site."));
        }
    }
}
