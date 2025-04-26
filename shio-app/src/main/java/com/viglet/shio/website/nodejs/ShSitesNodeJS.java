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
package com.viglet.shio.website.nodejs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

/**
 * Genereate NodeJS Application from Site.
 *
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
@Component
public class ShSitesNodeJS {
	private static final Log logger = LogFactory.getLog(ShSitesNodeJS.class);

	@Autowired
	private ShSiteRepository shSiteRepository;

	public StreamingResponseBody exportApplication(@PathVariable String id, HttpServletResponse response) {
		File userDir = new File(System.getProperty("user.dir"));
		Optional<ShSite> shSiteOptional = shSiteRepository.findById(id);
		if (shSiteOptional.isPresent() && userDir.exists() && userDir.isDirectory()) {
			ShSite shSite = shSiteOptional.get();

			File tmpDir = new File(userDir.getAbsolutePath().concat(File.separator + "store" + File.separator + "tmp"));

			if (!tmpDir.exists())
				tmpDir.mkdirs();

			File siteFile = new File(
					tmpDir.getAbsolutePath().concat(File.separator + shSite.getName() + UUID.randomUUID() + ".zip"));
			try {

				FileUtils.copyURLToFile(new URL("https://github.com/ShioCMS/shio-nodejs/archive/master.zip"), siteFile);
			} catch (Exception e) {
				logger.error("exportApplication: ", e);
			}

			String strDate = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());
			String zipFileName = shSite.getName() + "_NodeJS_" + strDate + ".zip";

			response.addHeader("Content-disposition", "attachment;filename=" + zipFileName);
			response.setContentType("application/octet-stream");
			response.setStatus(HttpServletResponse.SC_OK);

			return new StreamingResponseBody() {
				@Override
				public void writeTo(java.io.OutputStream output) throws IOException {

					try (var fileInputStream = new FileInputStream(siteFile)) {
						IOUtils.copy(fileInputStream, output);
						FileUtils.deleteQuietly(siteFile);

					} catch (IOException ex) {
						logger.error("exportObjectIOException", ex);
					} catch (Exception e) {
						logger.error("exportObjectException", e);
					}
				}
			};
		} else {
			return null;
		}

	}

}
