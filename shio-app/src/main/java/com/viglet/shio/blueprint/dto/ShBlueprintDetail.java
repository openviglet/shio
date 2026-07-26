/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * One blueprint, described (SH92): the manifest plus what it adds up to.
 *
 * <p>The manifest is served verbatim because its {@code params} schema is the contract a
 * caller has to satisfy, and paraphrasing a schema is how a caller ends up sending
 * something that almost fits. The counts are the part the manifest cannot state — they
 * are properties of the referenced documents, and they are what turns "is this the right
 * starting point" into a decision rather than a download.</p>
 *
 * <p>{@code files} carries bodies only when the caller asked for them
 * ({@code ?include=files}). The bodies are the largest thing a blueprint holds and the
 * only reader that needs them is a CLI about to write them into a project.</p>
 *
 * @param manifest  the package's {@code blueprint.json}, verbatim
 * @param postTypes the post-type names it provisions, in declared order
 * @param folders   folders its content declares
 * @param posts     posts its content declares
 * @param files     the carried front-end files with their bodies, or {@code null}
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShBlueprintDetail(ShBlueprintManifest manifest, List<String> postTypes,
                                int folders, int posts, List<ShBlueprint.File> files) {

    /** Describe a loaded blueprint; {@code withFiles} includes the file bodies. */
    public static ShBlueprintDetail of(ShBlueprint blueprint, boolean withFiles) {
        return new ShBlueprintDetail(blueprint.manifest(), blueprint.postTypeNames(),
                blueprint.declaredFolders(), blueprint.declaredPosts(),
                withFiles && !blueprint.files().isEmpty() ? blueprint.files() : null);
    }
}
