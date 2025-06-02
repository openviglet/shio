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
package com.viglet.shio.exchange.post;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.viglet.shio.utils.ShFolderUtils;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.viglet.shio.exchange.ShExchange;
import com.viglet.shio.exchange.ShExchangeFilesDirs;
import com.viglet.shio.exchange.file.ShFileExchange;
import com.viglet.shio.exchange.relator.ShRelatorExchange;
import com.viglet.shio.exchange.relator.ShRelatorItemExchange;
import com.viglet.shio.exchange.relator.ShRelatorItemExchanges;
import com.viglet.shio.exchange.utils.ShExchangeUtils;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.impl.ShPostAttrImpl;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.post.relator.impl.ShRelatorItemImpl;

import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShPostExport {
	private final ShFolderUtils shFolderUtils;
	private final ShPostRepository shPostRepository;
	private final ShExchangeUtils shExchangeUtils;

	@Autowired
	public ShPostExport(ShFolderUtils shFolderUtils, ShPostRepository shPostRepository, ShExchangeUtils shExchangeUtils) {
		this.shFolderUtils = shFolderUtils;
		this.shPostRepository = shPostRepository;
		this.shExchangeUtils = shExchangeUtils;
	}

	public StreamingResponseBody exportObject(HttpServletResponse response, String id) {
		Optional<ShPost> shPost = shPostRepository.findById(id);
		if (shPost.isPresent()) {
			ShExchangeFilesDirs shExchangeFilesDirs = new ShExchangeFilesDirs();
			if (shExchangeFilesDirs.generate()) {

				ShPostExchange postExchange = this.exportShPostDraft(shPost.get());

				ShExchange shExchange = new ShExchange();

				shExchange.setPosts(Arrays.asList(postExchange));

				return shExchangeUtils.downloadZipFile(String.format("%s_post",shPost.get().getFurl()), response, shExchange, shExchangeFilesDirs);
			}
		}

		return null;
	}

	public ShPostExchange exportShPostDraft(ShPost shPost) {
		ShPostExchange shPostExchange = new ShPostExchange();
		shPostExchange.setId(shPost.getId());
		shPostExchange.setFolder(shPost.getShFolder().getId());
		shPostExchange.setDate(shPost.getDate());
		shPostExchange.setPostType(shPost.getShPostType().getName());
		shPostExchange.setOwner(shPost.getOwner());
		shPostExchange.setFurl(shPost.getFurl());
		shPostExchange.setPosition(shPost.getPosition());

		Map<String, Object> fields = new HashMap<>();

		this.shPostAttrExchangeIterate(shPost, fields);

		shPostExchange.setFields(fields);
		return shPostExchange;
	}

	public void shPostAttrExchangeIterate(ShPost shPost, Map<String, Object> fields) {
		this.shPostAttrExchangeIterate(shPost, fields, null);

	}

	public void shPostAttrExchangeIterate(ShPost shPost, Map<String, Object> fields, List<ShFileExchange> files) {
		shPost.getShPostAttrs().forEach(shPostAttr -> {
			if (this.postAttrIsNotNull(shPostAttr)) {
				if (this.isRelator(shPostAttr)) {
					this.exportRelatorAttrs(shPost, fields, files, shPostAttr);
				} else {
					this.exportAttrs(fields, shPostAttr);
				}

				this.exportStaticFiles(shPost, files, shPostAttr);
			}
		});
	}

	private boolean postAttrIsNotNull(ShPostAttrImpl shPostAttr) {
		return shPostAttr != null && shPostAttr.getShPostTypeAttr() != null;
	}

	private boolean isRelator(ShPostAttrImpl shPostAttr) {
		return shPostAttr.getShPostTypeAttr().getShWidget().getName().equals(ShSystemWidget.RELATOR);
	}

	private void exportAttrs(Map<String, Object> fields, ShPostAttrImpl shPostAttr) {
		if (!shPostAttr.getArrayValue().isEmpty())
			fields.put(shPostAttr.getShPostTypeAttr().getName(), shPostAttr.getArrayValue());
		else if (shPostAttr.getDateValue() != null) {
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
			fields.put(shPostAttr.getShPostTypeAttr().getName(), dateFormat.format(shPostAttr.getDateValue()));
		} else {
			fields.put(shPostAttr.getShPostTypeAttr().getName(), shPostAttr.getStrValue());
		}
	}

	private void exportRelatorAttrs(ShPost shPost, Map<String, Object> fields, List<ShFileExchange> files,
			ShPostAttrImpl shPostAttr) {
		ShRelatorExchange shRelatorExchange = new ShRelatorExchange();
		shRelatorExchange.setId(shPostAttr.getId());
		shRelatorExchange.setName(shPostAttr.getStrValue());
		ShRelatorItemExchanges relators = new ShRelatorItemExchanges();
		for (ShRelatorItemImpl shRelatorItem : shPostAttr.getShChildrenRelatorItems()) {
			ShRelatorItemExchange shRelatorItemExchange = new ShRelatorItemExchange();
			shRelatorItemExchange.setPosition(shRelatorItem.getOrdinal());
			Map<String, Object> relatorFields = new HashMap<>();
			this.shPostAttrExchangeIterate(shPost, relatorFields, files);
			shRelatorItemExchange.setFields(relatorFields);
			relators.add(shRelatorItemExchange);
		}
		shRelatorExchange.setShSubPosts(relators);
		fields.put(shPostAttr.getShPostTypeAttr().getName(), shRelatorExchange);
	}

	private boolean canExportStaticFile(ShPostImpl shPost, List<ShFileExchange> files, ShPostAttrImpl shPostAttr) {
		return files != null && shPostAttr.getShPostTypeAttr().getName().equals(ShSystemPostTypeAttr.FILE)
				&& shPost.getShPostType().getName().equals(ShSystemPostType.FILE);
	}

	private void exportStaticFiles(ShPost shPost, List<ShFileExchange> files, ShPostAttrImpl shPostAttr) {
		if (canExportStaticFile(shPost, files, shPostAttr)) {
			String fileName = shPostAttr.getStrValue();
			File directoryPath = shFolderUtils.dirPath(shPost.getShFolder());
			File file = new File(directoryPath.getAbsolutePath().concat(File.separator + fileName));
			ShFileExchange shFileExchange = new ShFileExchange();
			shFileExchange.setId(shPost.getId());
			shFileExchange.setFile(file);
			files.add(shFileExchange);
		}
	}
}
