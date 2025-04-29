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
package com.viglet.shio.turing;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.shio.object.ShObjectType;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.utils.ShObjectUtils;
import com.viglet.shio.utils.ShPostTypeUtils;
import com.viglet.shio.utils.ShUtils;
import com.viglet.shio.website.utils.ShSitesObjectUtils;
import com.viglet.shio.website.utils.ShSitesPostUtils;
import com.viglet.shio.widget.ShSystemWidget;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShTuringIntegration {
	static final Logger logger = LogManager.getLogger(ShTuringIntegration.class);
	private static final String TURING_SERVER = "http://localhost:2700";
	private static final boolean TURING_ENABLED = true;

	@Autowired
	private ShSitesPostUtils shSitesPostUtils;
	@Autowired
	private ShPostTypeRepository shPostTypeRepository;
	@Autowired
	private ShUtils shUtils;
	@Autowired
	private ShPostRepository shPostRepository;
	@Autowired
	private ShPostTypeUtils shPostTypeUtils;
	@Autowired
	private ShObjectUtils shObjectUtils;
	@Autowired
	private ShSitesObjectUtils shSitesObjectUtils;
	@Autowired
	private ShFolderRepository shFolderRepository;

	public void indexObject(ShObjectImpl shObject) {
		ShSite shSite = shObjectUtils.getSite(shObject);

		if (TURING_ENABLED && hasSearchablePostTypes(shSite)) {
			if (isFolderIndex(shObject))
				shObject = ((ShPostImpl) shObject).getShFolder();

			String objectTypeName = getObjectTypeName(shObject);
			String objectName = getObjectName(shObject);

			logger.info("Preparing to index {}...", objectName);

			this.sendServerToIndex(shObject, objectName, objectTypeName, shSite);

		}
	}

	private boolean hasSearchablePostTypes(ShSite shSite) {
		return shSite != null && StringUtils.isNotBlank(shSite.getSearchablePostTypes());
	}

	private boolean isFolderIndex(ShObjectImpl shObject) {
		return shObject instanceof ShPost
				&& ((ShPostImpl) shObject).getShPostType().getName().equals(ShSystemPostType.FOLDER_INDEX);
	}

	private String getObjectTypeName(ShObjectImpl shObject) {
		String objectTypeName = null;
		if (shObject instanceof ShPostImpl shPostImpl)
			objectTypeName = shPostImpl.getShPostType().getName();
		else if (shObject instanceof ShFolder)
			objectTypeName = ShObjectType.FOLDER;
		return objectTypeName;
	}

	private String getObjectName(ShObjectImpl shObject) {
		String objectName = null;
		if (shObject instanceof ShPostImpl shPostImpl)
			objectName = shPostImpl.getTitle();
		else if (shObject instanceof ShFolder shFolder)
			objectName = shFolder.getName();
		return objectName;
	}

	private void sendServerToIndex(ShObjectImpl shObject, String objectName, String objectTypeName, ShSite shSite) {
		if (this.isSearchable(objectTypeName, shSite)) {
			TurSNJobItems turSNJobItems = new TurSNJobItems();
			TurSNJobItem turSNJobItem = this.toTurSNJobItem(shObject);
			if (turSNJobItem != null) {
				turSNJobItem.setTurSNJobAction(TurSNJobAction.CREATE);
				turSNJobItems.add(turSNJobItem);
			}

			this.sendServer(turSNJobItems, shSite);
			logger.info("Sent to index queue: {}", objectName);
		}
	}

	public boolean isSearchable(String objectTypeName, ShSite shSite) {
		JSONObject searchablePostTypes = new JSONObject(shSite.getSearchablePostTypes());

		return (objectTypeName != null && searchablePostTypes.has(objectTypeName)
				&& searchablePostTypes.getBoolean(objectTypeName));
	}

	public void deindexObject(ShObjectImpl shObject) {
		ShSite shSite = shObjectUtils.getSite(shObject);

		if (TURING_ENABLED && hasSearchablePostTypes(shSite)) {
			String objectTypeName = getObjectTypeName(shObject);
			if (shObject instanceof ShFolder)
				this.desindexChildObjects(shObject);
			if (this.isSearchable(objectTypeName, shSite)) {
				TurSNJobItems turSNJobItems = new TurSNJobItems();
				TurSNJobItem turSNJobItem = new TurSNJobItem();
				Map<String, Object> attributes = new HashMap<>();
				attributes.put("id", shObject.getId());
				turSNJobItem.setAttributes(attributes);
				turSNJobItem.setTurSNJobAction(TurSNJobAction.DELETE);
				turSNJobItems.add(turSNJobItem);

				this.sendServer(turSNJobItems, shSite);
			}
		}
	}

	private void desindexChildObjects(ShObjectImpl shObject) {
		ShFolder shFolder = (ShFolder) shObject;
		this.deindexInBatch(shPostRepository.findByShFolder(shFolder));
		shFolderRepository.findByParentFolder(shFolder).forEach(this::deindexObject);
	}

	private void deindexInBatch(Iterable<ShPost> shPosts) {
		for (ShPost shPost : shPosts) {
			this.deindexObject(shPost);
		}
	}

	private TurSNJobItem toTurSNJobItem(ShObjectImpl shObject) {
		try {
			TurSNJobItem turSNJobItem = new TurSNJobItem();
			Map<String, Object> attributes = new HashMap<>();

			addGenericAttributes(shObject, attributes);

			if (shObject instanceof ShPost shPost)
				addPost(shPost, attributes);
			else if (shObject instanceof ShFolder shFolder)
				addFolder(shFolder, attributes);

			// If URL is null, is a invisible page
			if (attributes.get("url") != null) {
				turSNJobItem.setAttributes(attributes);
				return turSNJobItem;
			} else {
				return null;
			}
		} catch (Exception e) {
			logger.error(e);
		}

		return null;
	}

	private void addPost(ShObjectImpl shObject, Map<String, Object> attributes) {
		if (shObject instanceof ShPost shPost) {
			shPostTypeRepository.findById(shPost.getShPostType().getId()).ifPresent(shPostType -> {
				addDefaultAttributes(attributes, shPost);
				addAssociationAttributes(attributes, shPost, shPostType);
				addAdditionalAttributes(attributes, shPost, shPostType);
			});
		}
	}

	private void addGenericAttributes(ShObjectImpl shObject, Map<String, Object> attributes) {
		attributes.put("id", shObject.getId());
		attributes.put("publication_date", formatDateToTuring(shObject.getDate()));
		attributes.put("url", shSitesObjectUtils.generateObjectLink(shObject));
	}

	private String formatDateToTuring(Date date) {
		TimeZone tz = TimeZone.getTimeZone("UTC");
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
		df.setTimeZone(tz);
		return df.format(date);
	}

	private void addFolder(ShObjectImpl shObject, Map<String, Object> attributes) {
		ShFolder shFolder = (ShFolder) shObject;
		attributes.put("title", shFolder.getName());
		ShPost shFolderIndexPost = shSitesPostUtils.getFolderIndex(shFolder);
		if (shFolderIndexPost != null) {
			Map<String, ShPostAttr> shFolderIndexPostMap = shSitesPostUtils.postToMap(shFolderIndexPost);
			if (shFolderIndexPostMap.containsKey(ShSystemPostTypeAttr.DESCRIPTION)) {
				attributes.put("abstract", shFolderIndexPostMap.get(ShSystemPostTypeAttr.DESCRIPTION).getStrValue());
			}
		}
	}

	private boolean isAdditionalField(ShPostTypeAttr shPostTypeAttr) {
		if (shUtils.isJSONValid(shPostTypeAttr.getWidgetSettings())) {
			JSONObject settings = new JSONObject(shPostTypeAttr.getWidgetSettings());
			if (settings.has(ShTuringSearchSettings.SEARCH)) {
				JSONObject searchSettings = settings.getJSONObject(ShTuringSearchSettings.SEARCH);
				if (searchSettings.has(ShTuringSearchSettings.IS_ADDITIONAL_FIELD)
						&& (searchSettings.getInt(ShTuringSearchSettings.IS_ADDITIONAL_FIELD) == 1)) {
					return true;
				}
			}
		}
		return false;

	}

	private boolean isSamePostTypeField(ShPostTypeAttr shPostTypeAttr) {
		if (shUtils.isJSONValid(shPostTypeAttr.getWidgetSettings())) {
			JSONObject settings = new JSONObject(shPostTypeAttr.getWidgetSettings());
			if (settings.has(ShTuringSearchSettings.SEARCH)) {
				JSONObject searchSettings = settings.getJSONObject(ShTuringSearchSettings.SEARCH);
				if (searchSettings.has(ShTuringSearchSettings.IS_SAME_POSTTYPE_FIELD)
						&& (searchSettings.getInt(ShTuringSearchSettings.IS_SAME_POSTTYPE_FIELD) == 1)) {
					return true;
				}
			}
		}
		return false;

	}

	private boolean hasCustomFieldName(ShPostTypeAttr shPostTypeAttr) {
		if (shUtils.isJSONValid(shPostTypeAttr.getWidgetSettings())) {
			JSONObject settings = new JSONObject(shPostTypeAttr.getWidgetSettings());
			if (settings.has(ShTuringSearchSettings.SEARCH)) {
				JSONObject searchSettings = settings.getJSONObject(ShTuringSearchSettings.SEARCH);
				if (searchSettings.has(ShTuringSearchSettings.CUSTOM_FIELD_NAME)
						&& StringUtils.isNotBlank(searchSettings.getString(ShTuringSearchSettings.CUSTOM_FIELD_NAME))) {
					return true;
				}
			}
		}
		return false;

	}

	private String getCustomFieldName(ShPostTypeAttr shPostTypeAttr) {
		if (shUtils.isJSONValid(shPostTypeAttr.getWidgetSettings())) {
			JSONObject settings = new JSONObject(shPostTypeAttr.getWidgetSettings());
			if (settings.has(ShTuringSearchSettings.SEARCH)) {
				JSONObject searchSettings = settings.getJSONObject(ShTuringSearchSettings.SEARCH);
				if (searchSettings.has(ShTuringSearchSettings.CUSTOM_FIELD_NAME)
						&& StringUtils.isNotBlank(searchSettings.getString(ShTuringSearchSettings.CUSTOM_FIELD_NAME))) {
					return searchSettings.getString(ShTuringSearchSettings.CUSTOM_FIELD_NAME);
				}
			}
		}
		return null;

	}

	private boolean hasAssociation(ShPostTypeAttr shPostTypeAttr) {
		if (shUtils.isJSONValid(shPostTypeAttr.getWidgetSettings())) {
			JSONObject settings = new JSONObject(shPostTypeAttr.getWidgetSettings());
			if (settings.has(ShTuringSearchSettings.SEARCH)) {
				JSONObject searchSettings = settings.getJSONObject(ShTuringSearchSettings.SEARCH);
				if (searchSettings.has(ShTuringSearchSettings.ASSOCIATION)
						&& StringUtils.isNotBlank(searchSettings.getString(ShTuringSearchSettings.ASSOCIATION))) {
					return true;
				}
			}
		}
		return false;

	}

	private String getAssociation(ShPostTypeAttr shPostTypeAttr) {
		if (shUtils.isJSONValid(shPostTypeAttr.getWidgetSettings())) {
			JSONObject settings = new JSONObject(shPostTypeAttr.getWidgetSettings());
			if (settings.has(ShTuringSearchSettings.SEARCH)) {
				JSONObject searchSettings = settings.getJSONObject(ShTuringSearchSettings.SEARCH);
				if (searchSettings.has(ShTuringSearchSettings.ASSOCIATION)
						&& StringUtils.isNotBlank(searchSettings.getString(ShTuringSearchSettings.ASSOCIATION))) {
					return searchSettings.getString(ShTuringSearchSettings.ASSOCIATION);
				}
			}
		}
		return null;

	}

	private void addAdditionalAttributes(Map<String, Object> attributes, ShPostImpl shPost, ShPostType shPostType) {
		Map<String, ShPostTypeAttr> shPostTypeMap = shPostTypeUtils.toMap(shPostType);
		shPost.getShPostAttrs().forEach(shPostAttr -> {
			ShPostTypeAttr shPostTypeAttr = shPostTypeMap.get(((ShPostAttr) shPostAttr).getShPostTypeAttr().getName());
			if (isAdditionalField(shPostTypeAttr)) {
				String attributeName = ((ShPostAttr) shPostAttr).getShPostTypeAttr().getName().toLowerCase();
				if (!isSamePostTypeField(shPostTypeAttr) && hasCustomFieldName(shPostTypeAttr))
					attributeName = getCustomFieldName(shPostTypeAttr);
				if (shPostTypeAttr.getShWidget().getName().equals(ShSystemWidget.MULTI_SELECT)) {
					Set<String> multiValue = new HashSet<>();
					((ShPostAttr) shPostAttr).getArrayValue().forEach(multiSelectId -> {
						ShPost shPostMultiSelect = shPostRepository.findById(multiSelectId).orElse(null);
						if (shPostMultiSelect != null)
							multiValue.add(shPostMultiSelect.getTitle());
					});
					attributes.put(attributeName, multiValue);
				} else {
					attributes.put(attributeName, ((ShPostAttr) shPostAttr).getStrValue());
				}
			}
		});
	}

	private void addAssociationAttributes(Map<String, Object> attributes, ShPost shPost, ShPostType shPostType) {
		Map<String, ShPostAttr> shPostMap = shSitesPostUtils.postToMap(shPost);
		shPostType.getShPostTypeAttrs().forEach(shPostTypeAttr -> {
			if (hasAssociation(shPostTypeAttr)) {
				if (shPostTypeAttr.getShWidget().getName().equals(ShSystemWidget.FILE)) {
					String shPostFileId = shPostMap.get(shPostTypeAttr.getName()).getStrValue();
					if (StringUtils.isNotBlank(shPostFileId)) {
						ShPost shFilePost = shPostRepository.findById(shPostFileId).orElse(null);
						attributes.put(getAssociation(shPostTypeAttr), shSitesPostUtils.generatePostLink(shFilePost));
					}
				} else if (shPostTypeAttr.getShWidget().getName().equals(ShSystemWidget.DATE)) {
					attributes.put(getAssociation(shPostTypeAttr),
							formatDateToTuring(shPostMap.get(shPostTypeAttr.getName()).getDateValue()));
				} else {
					attributes.put(getAssociation(shPostTypeAttr),
							shPostMap.get(shPostTypeAttr.getName()).getStrValue());
				}
			}
		});
	}

	private void addDefaultAttributes(Map<String, Object> attributes, ShPost shPost) {
		attributes.put("type", shPost.getShPostType().getTitle());
		attributes.put("title", shPost.getTitle());
		attributes.put("abstract", shPost.getSummary());

		if (shPost.getShPostType().getName().equals(ShSystemPostType.FILE)) {
			attributes.put("image", shSitesPostUtils.generatePostLink(shPost));
		}
	}

	private void sendServer(TurSNJobItems turSNJobItems, ShSite shSite) {

		try {
			ObjectMapper mapper = new ObjectMapper();
			String jsonResult = mapper.writeValueAsString(turSNJobItems);
			Charset utf8Charset = StandardCharsets.UTF_8;
			Charset customCharset = StandardCharsets.UTF_8;

			ByteBuffer inputBuffer = ByteBuffer.wrap(jsonResult.getBytes());

			// decode UTF-8
			CharBuffer data = utf8Charset.decode(inputBuffer);

			// encode
			ByteBuffer outputBuffer = customCharset.encode(data);

			byte[] outputData;

			outputData = new String(outputBuffer.array()).getBytes(StandardCharsets.UTF_8);

			String jsonUTF8 = new String(outputData);

			try (CloseableHttpClient client = HttpClients.createDefault()) {
				String serviceAPI = "%s/api/sn/%s/import";

				HttpPost httpPost = new HttpPost(String.format(serviceAPI, TURING_SERVER, shSite.getName()));
				if (logger.isDebugEnabled())
					logger.debug(jsonUTF8);

				StringEntity entity = new StringEntity(jsonUTF8, StandardCharsets.UTF_8.name());
				httpPost.setEntity(entity);
				httpPost.setHeader("Accept", "application/json");
				httpPost.setHeader("Content-type", "application/json");
				httpPost.setHeader("Accept-Encoding", StandardCharsets.UTF_8.name());

				try (CloseableHttpResponse response = client.execute(httpPost)) {
					logger.debug("Closed connection");
				}
			}
		} catch (IOException e) {
			logger.error(e);
		}

	}
}
