/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object;

import java.util.List;

import com.viglet.shio.persistence.model.object.ShObject;

/**
 * What {@code PUT /api/v2/object/moveto/{dest}} did, including which addresses a visitor can
 * now reach (SH1078).
 *
 * <p>A post answers at its site and its friendly URL, and a move keeps the friendly URL (SH139).
 * So a move inside one site changes no address, and {@code routes} is empty. It is not empty when
 * a post crosses into another site, or when a post that had no URL is given one. Before this, the
 * answer listed the folders it moved and left the caller to run {@code shio verify --delivery} to
 * learn whether anything a visitor uses had changed.</p>
 *
 * @param moved  the folders relocated, as before; posts are not {@code ShObject}s and never
 *               appeared here
 * @param routes one entry per published post whose address changed, in the order the move met
 *               them
 */
public record ShMoveOutcome(List<ShObject> moved, List<RouteChange> routes) {

    /**
     * One published post's address before and after, as SH79 addresses.
     *
     * @param retired where it answered before, or {@code null} when it had no address
     * @param created where it answers now, or {@code null} when it has none
     */
    public record RouteChange(String retired, String created) {
    }
}
