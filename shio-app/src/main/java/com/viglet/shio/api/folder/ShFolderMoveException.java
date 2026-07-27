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
 * The two ways a folder move is not a move at all (SH137).
 *
 * <p>Both live on the shared writer rather than on either caller, because both
 * surfaces can reach them: the console's move dialog navigates <em>down</em> from the
 * site root, so a curator can walk into a child of the folder they are dragging, and
 * an agent can address any {@code to} it likes.</p>
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
