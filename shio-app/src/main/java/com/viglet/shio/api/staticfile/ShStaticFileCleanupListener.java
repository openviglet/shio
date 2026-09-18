/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.webhook.ShWebhookEventType;
import com.viglet.shio.webhook.ShPostLifecycleEvent;

/**
 * Removes a post's stored bytes when the post is deleted (SH38).
 *
 * <p>Listens for the {@link ShPostLifecycleEvent} that
 * {@code ShPostUnifiedWriteService} already publishes on delete and clears the
 * id-keyed file_source directory. {@link ShFileStorageService#delete} is a no-op
 * for non-file posts (no directory exists), so this needs no post-type check and
 * is safe for every delete.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Component
public class ShStaticFileCleanupListener {

    private final ShFileStorageService storage;
    /** SH1035 — the superseded copies go with the asset; nothing else would ever collect them. */
    private final ShAssetRenditionStore renditions;

    public ShStaticFileCleanupListener(ShFileStorageService storage,
            ShAssetRenditionStore renditions) {
        this.storage = storage;
        this.renditions = renditions;
    }

    @EventListener
    public void onPostLifecycle(ShPostLifecycleEvent event) {
        if (event.type() == ShWebhookEventType.DELETE) {
            storage.delete(event.postId());
            // SH1035 — and its history. The renditions live outside `file_source`, so deleting the
            // asset's directory does not reach them: without this they would be the one thing a
            // purge leaves on disk, in a directory no surface lists, for an asset that no longer
            // exists. `asset-unused` and `shio assets prune` would then free less than they said.
            renditions.forget(event.postId());
        }
    }
}
