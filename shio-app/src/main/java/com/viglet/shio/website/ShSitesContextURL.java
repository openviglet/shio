/*
 * Copyright (C) 2016-2020 the original author or authors. 
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
package com.viglet.shio.website;

import java.io.Serializable;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Alexandre Oliveira
 */
public class ShSitesContextURL implements Serializable {

	private static final long serialVersionUID = 1L;

	private transient HttpServletRequest request = null;
	private transient HttpServletResponse response = null;
	private ShSitesContextURLInfo info = new ShSitesContextURLInfo();

	public String toString() {
		return String.format(
				"shContext: %s, contextURL: %s, shContextOriginal: %s, siteId: %s, parentFolderId: %s, shFormat: %s, shLocale: %s, objectId:  %s",
				info.getShContext(), info.getContextURL(), info.getContextURLOriginal(), info.getSiteId(),
				info.getParentFolderId(), info.getShFormat(), info.getShLocale(), info.getObjectId());
	}

	public HttpServletRequest getRequest() {
		return request;
	}

	public void setRequest(HttpServletRequest request) {
		this.request = request;
	}

	public HttpServletResponse getResponse() {
		return response;
	}

	public void setResponse(HttpServletResponse response) {
		this.response = response;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}

	public ShSitesContextURLInfo getInfo() {
		return info;
	}

	public void setInfo(ShSitesContextURLInfo info) {
		this.info = info;
	}

}
