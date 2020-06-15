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
package com.viglet.shio.exchange.post.type;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.shio.exchange.ShExchange;
import com.viglet.shio.exchange.ShPostTypeExchange;
import com.viglet.shio.exchange.ShPostTypeFieldExchange;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.utils.ShUtils;

/**
 * Export PostType.
 *
 * @author Alexandre Oliveira
 * @since 0.3.0
 */
@Component
public class ShPostTypeExport {
	private static final Log logger = LogFactory.getLog(ShPostTypeExport.class);

	@Autowired
	private ShPostTypeRepository shPostTypeRepository;
	@Autowired
	private ShUtils shUtils;

	public StreamingResponseBody exportObject(HttpServletResponse response) {
		String folderName = UUID.randomUUID().toString();
		File userDir = new File(System.getProperty("user.dir"));
		if (userDir.exists() && userDir.isDirectory()) {
			File tmpDir = new File(userDir.getAbsolutePath().concat(File.separator + "store" + File.separator + "tmp"));
			if (!tmpDir.exists())
				tmpDir.mkdirs();

			List<ShPostType> shPostTypes = shPostTypeRepository.findAll();

			List<ShPostTypeExchange> postTypeExchanges = new ArrayList<>();

			shPostTypes.forEach(shPostType -> postTypeExchanges.add(this.exportPostType(shPostType)));

			File exportDir = new File(tmpDir.getAbsolutePath().concat(File.separator + folderName));
			if (!exportDir.exists())
				exportDir.mkdirs();

			ShExchange shExchange = new ShExchange();
			if (!postTypeExchanges.isEmpty())
				shExchange.setPostTypes(postTypeExchanges);
			// Object to JSON in file
			ObjectMapper mapper = new ObjectMapper();
			try {
				mapper.writerWithDefaultPrettyPrinter().writeValue(
						new File(exportDir.getAbsolutePath().concat(File.separator + "export.json")), shExchange);
			} catch (IOException e1) {
				logger.error(e1);
			}

			File zipFile = new File(tmpDir.getAbsolutePath().concat(File.separator + folderName + ".zip"));

			shUtils.addFilesToZip(exportDir, zipFile);

			String strDate = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());
			String zipFileName = "PostType_" + strDate + ".zip";

			response.addHeader("Content-disposition", "attachment;filename=" + zipFileName);
			response.setContentType("application/octet-stream");
			response.setStatus(HttpServletResponse.SC_OK);

			return new StreamingResponseBody() {
				@Override
				public void writeTo(java.io.OutputStream output) throws IOException {

					try {
						java.nio.file.Path path = Paths.get(zipFile.getAbsolutePath());
						byte[] data = Files.readAllBytes(path);
						output.write(data);
						output.flush();

						FileUtils.deleteDirectory(exportDir);
						FileUtils.deleteQuietly(zipFile);

					} catch (IOException ex) {
						logger.error(ex);
					}
				}
			};
		} else {
			return null;
		}
	}

	public ShPostTypeExchange exportPostType(ShPostType shPostType) {
		ShPostTypeExchange shPostTypeExchange = new ShPostTypeExchange();
		shPostTypeExchange.setId(shPostType.getId());
		shPostTypeExchange.setName(shPostType.getName());
		shPostTypeExchange.setNamePlural(shPostType.getNamePlural());
		shPostTypeExchange.setLabel(shPostType.getTitle());
		shPostTypeExchange.setDate(shPostType.getDate());
		shPostTypeExchange.setDescription(shPostType.getDescription());

		shPostTypeExchange.setOwner(shPostType.getOwner());
		shPostTypeExchange.setSystem(shPostType.getSystem() == (byte) 1);

		if (!shPostType.getShPostTypeAttrs().isEmpty()) {
			Map<String, ShPostTypeFieldExchange> shPostTypeFieldExchanges = new HashMap<>();
			shPostType.getShPostTypeAttrs().forEach(shPostTypeAttr -> {
				ShPostTypeFieldExchange shPostTypeFieldExchange = this.exportPostTypeField(shPostTypeAttr);
				shPostTypeFieldExchanges.put(shPostTypeAttr.getName(), shPostTypeFieldExchange);
			});
			shPostTypeExchange.setFields(shPostTypeFieldExchanges);
		}

		return shPostTypeExchange;
	}

	public ShPostTypeFieldExchange exportPostTypeField(ShPostTypeAttr shPostTypeAttr) {
		ShPostTypeFieldExchange shPostTypeFieldExchange = new ShPostTypeFieldExchange();
		shPostTypeFieldExchange.setId(shPostTypeAttr.getId());
		shPostTypeFieldExchange.setLabel(shPostTypeAttr.getLabel());
		shPostTypeFieldExchange.setDescription(shPostTypeAttr.getDescription());

		shPostTypeFieldExchange.setOrdinal(shPostTypeAttr.getOrdinal());
		shPostTypeFieldExchange.setRequired(shPostTypeAttr.getRequired() == (byte) 1);
		shPostTypeFieldExchange.setSummary(shPostTypeAttr.getIsSummary() == (byte) 1);
		shPostTypeFieldExchange.setTitle(shPostTypeAttr.getIsTitle() == (byte) 1);
		shPostTypeFieldExchange.setWidget(shPostTypeAttr.getShWidget().getName());
		shPostTypeFieldExchange.setWidgetSettings(shPostTypeAttr.getWidgetSettings());

		if (!shPostTypeAttr.getShPostTypeAttrs().isEmpty()) {
			Map<String, ShPostTypeFieldExchange> shPostTypeFieldExchanges = new HashMap<>();
			shPostTypeAttr.getShPostTypeAttrs().forEach(shPostTypeAttrChild -> {
				ShPostTypeFieldExchange shPostTypeFieldExchangeChild = this.exportPostTypeField(shPostTypeAttrChild);
				shPostTypeFieldExchanges.put(shPostTypeAttrChild.getName(), shPostTypeFieldExchangeChild);
			});
			shPostTypeFieldExchange.setFields(shPostTypeFieldExchanges);
		}
		return shPostTypeFieldExchange;
	}
}
