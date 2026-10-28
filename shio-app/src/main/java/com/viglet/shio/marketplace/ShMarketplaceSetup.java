/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.marketplace;

import java.util.List;

import org.springframework.stereotype.Service;

import com.viglet.shio.blueprint.ShBlueprintRegistry;
import com.viglet.shio.blueprint.ShBlueprintService;
import com.viglet.shio.blueprint.ShBlueprintWriteService;
import com.viglet.shio.blueprint.dto.ShBlueprintApplied;
import com.viglet.shio.marketplace.dto.ShMarketplaceItem;

import tools.jackson.databind.JsonNode;

/**
 * SH1251 — install a catalogue package if it is not here yet, then apply it to a site.
 *
 * <p>Turing's marketplace ends an install on a working site; Shio's stopped at a package on disk,
 * and the apply was reachable from the CLI and MCP but not from the console. This is the one flow
 * the console dialog needs, and it composes the two operations that already exist rather than
 * merging them (SH675's reasons hold: an install is auditable on its own and survives an apply that
 * fails, which the next apply can use).</p>
 *
 * <h2>The dry run plans both halves</h2>
 *
 * <p>A package that is not installed has an apply plan too, and it is computed from the downloaded
 * bytes through {@link ShMarketplaceInstaller#staged} and {@link ShBlueprintRegistry#readStaged}:
 * the same package a confirm would install, validated as a load would validate it, and deleted
 * afterwards. A plan that waited for the install would make the curator write a package to see
 * what it does, which is the order P7 exists to reverse.</p>
 */
@Service
public class ShMarketplaceSetup {

    private final ShMarketplaceCatalog catalog;
    private final ShMarketplaceInstaller installer;
    private final ShBlueprintRegistry registry;
    private final ShBlueprintService blueprints;

    public ShMarketplaceSetup(ShMarketplaceCatalog catalog, ShMarketplaceInstaller installer,
                              ShBlueprintRegistry registry, ShBlueprintService blueprints) {
        this.catalog = catalog;
        this.installer = installer;
        this.registry = registry;
        this.blueprints = blueprints;
    }

    /**
     * What the dialog sends.
     *
     * @param site           the site to apply to; with {@code createSite}, the name of a new one
     * @param params         the package's parameters, checked against its declared schema
     * @param createSite     SH151's opt-in, a consent the plan names before it happens
     * @param mergePostTypes SH152's opt-in, likewise
     * @param dryRun         plan both halves and write nothing
     */
    public record Request(String site, JsonNode params, boolean createSite, boolean mergePostTypes,
                          boolean dryRun) {
    }

    /**
     * @param install the install half, or {@code null} when the package was already here
     * @param apply   the apply, or its plan on a dry run
     */
    public record Result(ShMarketplaceInstaller.Installed install, ShBlueprintApplied apply) {
    }

    public Result run(String id, Request request, String actor) {
        List<ShMarketplaceItem> items = catalog.catalogue().items();
        ShMarketplaceItem item = items.stream().filter(each -> each.id().equals(id)).findFirst()
                .orElseThrow(() -> new ShMarketplaceInstaller.UnknownItemException(id,
                        items.stream().map(ShMarketplaceItem::id).toList()));
        boolean needsInstall = !item.builtIn() && !registry.names().contains(item.id());
        ShBlueprintWriteService.Options options =
                new ShBlueprintWriteService.Options(request.createSite(), request.mergePostTypes());

        if (request.dryRun()) {
            if (!needsInstall) {
                return new Result(null, blueprints.plan(id, request.site(), request.params(), actor,
                        options));
            }
            return installer.staged(id, (directory, wouldInstall) -> new Result(wouldInstall,
                    blueprints.plan(registry.readStaged(directory), request.site(),
                            request.params(), actor, options)));
        }

        ShMarketplaceInstaller.Installed installed = needsInstall ? installer.install(id, false) : null;
        // Not rolled back when the apply refuses: the package is valid and installed, and the
        // refusal (an unknown site, a missing parameter) is about the apply. The caller is told it
        // stays by the install half of an earlier dry run, and the next apply can use it.
        return new Result(installed, blueprints.apply(id, request.site(), request.params(), actor,
                options));
    }
}
