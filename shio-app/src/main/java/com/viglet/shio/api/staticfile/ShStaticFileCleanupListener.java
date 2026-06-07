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

    public ShStaticFileCleanupListener(ShFileStorageService storage) {
        this.storage = storage;
    }

    @EventListener
    public void onPostLifecycle(ShPostLifecycleEvent event) {
        if (event.type() == ShWebhookEventType.DELETE) {
            storage.delete(event.postId());
        }
    }
}
