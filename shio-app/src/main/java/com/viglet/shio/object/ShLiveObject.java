/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.object;

import java.util.Optional;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;

/**
 * The one place that resolves an object id and refuses a trashed folder (SH226).
 *
 * <h2>The invariant</h2>
 *
 * <p>SH55's trash is a {@code deleted_date}, and SH200 made every read in
 * {@code ShFolderRepository} exclude a trashed folder — which covers navigation, the CDA, the
 * sitemap, the content lint and the exchange export in one place. Ids also resolve
 * <b>polymorphically</b> through {@link ShObjectRepository}, and that repository knows nothing
 * about the trash: {@code ShFolder} carries no {@code @SQLRestriction}, unlike
 * {@code ShPostUnified}. So a trashed section is absent from every listing and still openable,
 * writable and creatable-into by its own id.</p>
 *
 * <h2>Why this is a class and not the same three lines a fourth time</h2>
 *
 * <p>Because it already was, twice, and two other call sites were missing. SH200 hand-guarded
 * {@code ShObjectNavigationService} and {@code ShObjectWriteService} with a private {@code live}
 * helper each — identical predicate, two copies — and left
 * {@code ShFolderWriteService#create} resolving its parent unguarded, so a curator could create a
 * live section <em>inside</em> the trash and never see it again.</p>
 *
 * <p>That is the same shape as SH128's {@code ShFolderSite}: compiles, reviews clean, wrong only
 * when the data is read along an axis the author was not thinking about. §VIII.10's rule is a
 * guard <b>per invariant</b>, and this class is half of it —
 * {@code ShLiveObjectInvariantTest} is the other half, and it fails the build when a new read
 * resolves an id through the polymorphic repository without coming through here. Centralising the
 * predicate is what makes the correct form also the shortest one, which is what made SH128's
 * guard teachable rather than annoying.</p>
 *
 * <h2>What it deliberately does not decide</h2>
 *
 * <p>Only the <b>folder</b> trash, because {@link ShObject} has exactly three subclasses —
 * {@code ShFolder}, {@code ShSite}, {@code ShPostType} — and only the first has one. A post never
 * arrives here at all: {@code ShPostUnified} is its own root rather than an {@code ShObject}, and
 * its trash is already enforced by {@code @SQLRestriction} on the mapping, so filtering one here
 * would be a second implementation of a rule that already holds — and a second implementation is
 * what this class exists to remove.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShLiveObject {

    private ShLiveObject() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * The object {@code id} names, or empty when it does not exist or is a trashed folder.
     *
     * @param repository the polymorphic object repository
     * @param id the object id, or {@code null}
     * @return the live object, or {@link Optional#empty()}
     */
    public static Optional<ShObject> of(ShObjectRepository repository, String id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id).filter(ShLiveObject::isLive);
    }

    /**
     * Whether an already-loaded object is live — false only for a trashed folder.
     *
     * <p>Separate from {@link #of} so a caller that holds the entity for another reason is not
     * pushed into a second read, and so the trash's own screens have a predicate to negate rather
     * than a rule to restate.</p>
     *
     * @param object any object, or {@code null}
     * @return {@code true} when it exists and is not a trashed folder
     */
    public static boolean isLive(ShObject object) {
        if (object == null) {
            return false;
        }
        return !(object instanceof ShFolder folder) || !folder.isTrashed();
    }
}
