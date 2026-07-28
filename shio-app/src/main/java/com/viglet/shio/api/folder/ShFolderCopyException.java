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
 * The way a folder copy is not a copy at all (SH203).
 *
 * <p>SH137 gave <em>move</em> a cycle guard and left <em>copy</em> without one, on the
 * reasoning that a copy needs none — "the new folder is not yet in the tree, so it cannot
 * be its own ancestor". That is true of the folder being created and false of the traversal
 * that fills it: {@code copyFolder} saves the copy first and then walks
 * {@code findByParentFolder(source)}, so when the destination is <em>inside</em> the source
 * the walk reaches a folder whose children now include the copy it just made, copies that,
 * and descends into a subtree it is still growing. It does not terminate.</p>
 *
 * <p>So the two operations refuse the same destination for two different reasons: a move
 * would detach the subtree from the site root, a copy would never finish. Same shape of
 * mistake from the curator's side — the move dialog navigates down from the site root, so
 * the folders inside the one being copied are three clicks away — which is why SH203
 * disables that subtree in the picker and keeps both refusals as the backstop.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShFolderCopyException {

    private ShFolderCopyException() {
        throw new IllegalStateException("Utility class");
    }

    /** A 409: the destination is the folder itself, or somewhere inside it. */
    public static class Cycle extends ShTeachingException {

        public Cycle(String folderName, String destinationName) {
            super(ShProblem.conflict("folder-copy-cycle",
                            "A folder cannot be copied into itself or into its own subtree")
                    .withDetail("'" + destinationName + "' is inside '" + folderName
                            + "', so the copy would recurse into the copies it is making.")
                    .withFix("Pick a destination outside '" + folderName
                            + "' — a sibling, an ancestor, or the site root. To duplicate the "
                            + "folder in place, copy it into its parent instead."));
        }
    }
}
