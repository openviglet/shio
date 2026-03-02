/*
 * Copyright (C) 2016-2021 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.shio.website.nashorn;

import org.springframework.stereotype.Component;

import com.viglet.shio.property.ShWebsiteProperties;
import com.viglet.shio.website.component.ShGetRelationComponent;
import com.viglet.shio.website.component.ShNavigationComponent;
import com.viglet.shio.website.component.ShQueryComponent;
import com.viglet.shio.website.component.ShSearchComponent;
import com.viglet.shio.website.component.form.ShFormComponent;
import com.viglet.shio.website.utils.ShSitesFolderUtils;
import com.viglet.shio.website.utils.ShSitesObjectUtils;
import com.viglet.shio.website.utils.ShSitesPostUtils;

/**
 * @author Alexandre Oliveira
 * 
 * @since 0.3.9
 */
@Component
public class ShNashornEngineBindings {

	private final ShNavigationComponent shNavigationComponent;
	private final ShQueryComponent shQueryComponent;
	private final ShSearchComponent shSearchComponent;
	private final ShFormComponent shFormComponent;
	private final ShSitesFolderUtils shSitesFolderUtils;
	private final ShSitesObjectUtils shSitesObjectUtils;
	private final ShSitesPostUtils shSitesPostUtils;
	private final ShGetRelationComponent shGetRelationComponent;
	private final ShWebsiteProperties shWebsiteProperties;

	public ShNashornEngineBindings(ShNavigationComponent shNavigationComponent, ShQueryComponent shQueryComponent,
			ShSearchComponent shSearchComponent, ShFormComponent shFormComponent, ShSitesFolderUtils shSitesFolderUtils,
			ShSitesObjectUtils shSitesObjectUtils, ShSitesPostUtils shSitesPostUtils, ShGetRelationComponent shGetRelationComponent,
			ShWebsiteProperties shWebsiteProperties) {
		this.shNavigationComponent = shNavigationComponent;
		this.shQueryComponent = shQueryComponent;
		this.shSearchComponent = shSearchComponent;
		this.shFormComponent = shFormComponent;
		this.shSitesFolderUtils = shSitesFolderUtils;
		this.shSitesObjectUtils = shSitesObjectUtils;
		this.shSitesPostUtils = shSitesPostUtils;
		this.shGetRelationComponent = shGetRelationComponent;
		this.shWebsiteProperties = shWebsiteProperties;
	}

	public ShNavigationComponent getShNavigationComponent() {
		return shNavigationComponent;
	}

	public ShQueryComponent getShQueryComponent() {
		return shQueryComponent;
	}

	public ShSearchComponent getShSearchComponent() {
		return shSearchComponent;
	}

	public ShFormComponent getShFormComponent() {
		return shFormComponent;
	}

	public ShSitesFolderUtils getShSitesFolderUtils() {
		return shSitesFolderUtils;
	}

	public ShSitesObjectUtils getShSitesObjectUtils() {
		return shSitesObjectUtils;
	}

	public ShSitesPostUtils getShSitesPostUtils() {
		return shSitesPostUtils;
	}

	public ShGetRelationComponent getShGetRelationComponent() {
		return shGetRelationComponent;
	}

	public ShWebsiteProperties getShWebsiteProperties() {
		return shWebsiteProperties;
	}

}
