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
package com.viglet.shio.api.preview;

import java.net.MalformedURLException;
import java.net.URL;

import com.viglet.shio.website.ShSitesDetectContextURL;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.website.ShSitesContextURL;
import com.viglet.shio.website.ShSitesContextURLProcess;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/preview")
@Tag( name = "Preview", description = "Preview API")
public class ShPreviewAPI {
	@Autowired
	ShSitesContextURLProcess shSitesContextURLProcess;
	@Autowired
	private ShObjectRepository shObjectRepository;
	@Autowired
	private ShSitesDetectContextURL shSitesDetectContextURL;

	@Operation(summary = "Detect URL")
	@PostMapping("/detect-url")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShObjectImpl shPreviewDetectURL(HttpServletRequest request, HttpServletResponse response)
			throws MalformedURLException {

		URL url = new URL(request.getParameter("url"));
		ShSitesContextURL shSitesContextURL = shSitesContextURLProcess.getContextURL(request, response);
		shSitesContextURL.getInfo().setContextURL(url.getPath());
		shSitesDetectContextURL.detectContextURL(shSitesContextURL);
		return shObjectRepository.findById(shSitesContextURL.getInfo().getObjectId()).orElse(null);
	}
}