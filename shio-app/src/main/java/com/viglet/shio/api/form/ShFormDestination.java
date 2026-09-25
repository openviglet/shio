/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.form;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;

/**
 * The folder a site's form submissions land in, resolved from its configured path (SH633).
 *
 * <p>One resolver for the two readers that ask: the submission endpoint, which refuses when the
 * folder is gone, and verify (SH1066), which says so before a visitor finds out. Two copies of "walk
 * the name chain from the site's root" would be two answers to whether a destination exists, and the
 * day they disagreed verify would report clean on a form that refuses every submission.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShFormDestination {

    private final ShFolderRepository shFolderRepository;

    public ShFormDestination(ShFolderRepository shFolderRepository) {
        this.shFolderRepository = shFolderRepository;
    }

    /**
     * The configured folder, by name chain within the site, or {@code null} when the path is blank
     * or any segment of it does not exist.
     */
    public ShFolder resolve(ShSite site, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        List<String> names = Arrays.stream(path.split("/")).filter(part -> !part.isBlank()).toList();
        Optional<ShFolder> current = shFolderRepository.findRootFolder(site);
        if (current.isEmpty()) {
            return null;
        }
        ShFolder folder = current.get();
        for (String name : names) {
            ShFolder child = shFolderRepository.findByParentFolderAndName(folder, name);
            if (child == null) {
                return null;
            }
            folder = child;
        }
        return folder;
    }
}
