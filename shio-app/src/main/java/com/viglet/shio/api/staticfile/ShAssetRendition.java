/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import java.time.Instant;

/**
 * One set of bytes an asset used to have (SH1035).
 *
 * <p>Numbered rather than dated, because the number is what a caller passes back to revert and a
 * timestamp is not addressable — two replaces in the same second are two renditions. The number
 * only ever increases for an asset, so it survives the oldest copies being dropped: after a prune
 * an asset may hold 4, 5 and 6, and 6 is still the most recent.</p>
 *
 * @param number   the rendition's address, unique and increasing per asset
 * @param fileName the name the bytes were stored under, which a replace never changes
 * @param size     bytes on disk
 * @param storedAt when these bytes were superseded
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShAssetRendition(int number, String fileName, long size, Instant storedAt) {
}
