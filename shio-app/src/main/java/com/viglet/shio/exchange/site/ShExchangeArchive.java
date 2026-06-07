/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.exchange.site;

/**
 * A built exchange archive ready to stream (SH69): the suggested download
 * {@code fileName} (e.g. {@code my-site.zip}) and the zipped package bytes.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShExchangeArchive(String fileName, byte[] content) {
}
