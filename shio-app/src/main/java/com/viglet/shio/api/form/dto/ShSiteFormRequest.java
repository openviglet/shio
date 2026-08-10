/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.form.dto;

/**
 * What an operator sets on a site's form configuration (SH632).
 *
 * @param siteId       the site this configures; not updatable afterwards
 * @param folderUrl    the folder a submission lands in, as a name chain within the site
 * @param allowedTypes comma-separated post-type names this site invites; empty invites nothing
 * @param enabled      whether the site accepts submissions at all
 */
public record ShSiteFormRequest(String siteId, String folderUrl, String allowedTypes,
                                boolean enabled) {
}
