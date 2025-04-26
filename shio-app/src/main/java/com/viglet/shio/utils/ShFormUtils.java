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
package com.viglet.shio.utils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import jakarta.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.viglet.shio.api.post.ShPostAPI;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeAttrRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.website.ShSitesContextURL;
import com.viglet.shio.website.component.form.ShFormConfiguration;
import com.viglet.shio.widget.ShSystemWidget;
import com.viglet.shio.widget.ShWidgetImplementation;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShFormUtils {
	private static final Log logger = LogFactory.getLog(ShFormUtils.class);
	@Resource
	private ApplicationContext applicationContext;
	@Autowired
	private ShPostTypeAttrRepository shPostTypeAttrRepository;
	@Autowired
	private ShPostAPI shPostAPI;
	@Autowired
	private ShPostTypeRepository shPostTypeRepository;
	@Autowired
	private ShFolderRepository shFolderRepository;
	@Autowired
	private ShPostRepository shPostRepository;
	@Autowired
	private ShObjectRepository shObjectRepository;

	private static final String POST_TYPE_ATTR_PARAM = "__sh-post-type-attr-";
	private static final String POST_TYPE_PARAM = "__sh-post-type";

	private ShPost createPost(ShSitesContextURL shSitesContextURL, ShFormConfiguration shFormConfiguration,
			ShPostType shPostType) {
		Enumeration<String> parameters = shSitesContextURL.getRequest().getParameterNames();
		ShPost shPost = null;
		ShObjectImpl shObject = shObjectRepository.findById(shSitesContextURL.getInfo().getObjectId()).orElse(null);
		if (shFormConfiguration != null || shObject instanceof ShFolder
				|| (shObject instanceof ShPost && ((ShPostImpl) shObject).getTitle().equals("index"))) {
			ShFolder shFolder = null;

			if (shFormConfiguration != null && StringUtils.isNotBlank(shFormConfiguration.getFolder().toString())) {
				shFolder = shFolderRepository.findById(shFormConfiguration.getFolder().toString()).orElse(null);
			} else {
				if (shObject instanceof ShFolder shFolderInst) {
					shFolder = shFolderInst;
				} else {
					if (shObject != null)
						shFolder = ((ShPostImpl) shObject).getShFolder();
				}
			}

			shPost = createPostObject(shSitesContextURL, parameters, shPost, shFolder, shPostType);
		}

		return shPost;
	}

	private ShPost createPostObject(ShSitesContextURL shSitesContextURL, Enumeration<String> parameters, ShPost shPost,
			ShFolder shFolder, ShPostType shPostType) {
		if (shPostType != null) {
			shPost = new ShPost();
			shPost.setDate(new Date());
			shPost.setOwner("anonymous");
			shPost.setShFolder(shFolder);

			shPost.setShPostType(shPostType);
			Set<ShPostAttr> shPostAttrs = getPostAttrs(shSitesContextURL, parameters, shPost, shPostType);
			shPost.setShPostAttrs(shPostAttrs);

			shPostAPI.postSave(shPost);

		}
		return shPost;
	}

	private Set<ShPostAttr> getPostAttrs(ShSitesContextURL shSitesContextURL, Enumeration<String> parameters,
			ShPost shPost, ShPostType shPostType) {
		Set<ShPostAttr> shPostAttrs = new HashSet<>();
		while (parameters.hasMoreElements()) {
			String param = parameters.nextElement();
			String paramValue = shSitesContextURL.getRequest().getParameter(param);

			if (param.startsWith(POST_TYPE_ATTR_PARAM)) {
				String attribute = param.replaceFirst(POST_TYPE_ATTR_PARAM, StringUtils.EMPTY).replace("\\[\\]",
						StringUtils.EMPTY);
				ShPostTypeAttr shPostTypeAttr = shPostTypeAttrRepository.findByShPostTypeAndName(shPostType, attribute);

				getWidget(shPostTypeAttr);

				ShPostAttr shPostAttr = new ShPostAttr();
				shPostAttr.setShPost(shPost);
				shPostAttr.setShPostTypeAttr(shPostTypeAttr);

				if (shPostTypeAttr.getShWidget().getName().equals(ShSystemWidget.CHECK_BOX)) {
					shPostAttr.setArrayValue(setPostAttrArrayValue(shSitesContextURL, param));
				} else {
					shPostAttr.setStrValue(paramValue);
				}

				shPostAttrs.add(shPostAttr);
			}

		}
		return shPostAttrs;
	}

	private Set<String> setPostAttrArrayValue(ShSitesContextURL shSitesContextURL, String param) {
		String[] paramArray = shSitesContextURL.getRequest().getParameterValues(param);

		Set<String> arrayValue = new HashSet<>();
		Collections.addAll(arrayValue, paramArray);
		return arrayValue;

	}

	private void getWidget(ShPostTypeAttr shPostTypeAttr) {
		String className = shPostTypeAttr.getShWidget().getClassName();
		ShWidgetImplementation object;
		try {
			object = (ShWidgetImplementation) Class.forName(className).getDeclaredConstructor().newInstance();
			applicationContext.getAutowireCapableBeanFactory().autowireBean(object);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException | ClassNotFoundException e) {
			logger.error(e);
		}
	}

	public void execute(ShSitesContextURL shSitesContextURL) {

		ShPostType shPostType = shPostTypeRepository
				.findByName(shSitesContextURL.getRequest().getParameter(POST_TYPE_PARAM));
		ShFormConfiguration shFormConfiguration = getFormConfiguration(shPostType);
		ShPost shPost = this.createPost(shSitesContextURL, shFormConfiguration, shPostType);

		for (ShPostTypeAttr shPostTypeAttr : shPostType.getShPostTypeAttrs()) {
			String className = shPostTypeAttr.getShWidget().getClassName();
			ShWidgetImplementation object;
			try {
				object = (ShWidgetImplementation) Class.forName(className).getDeclaredConstructor().newInstance();
				applicationContext.getAutowireCapableBeanFactory().autowireBean(object);
				object.postRender(shPost, shSitesContextURL);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException
					| IOException e) {
				logger.error(e);
			}

		}

		if (shFormConfiguration != null && !shFormConfiguration.isCreatePost())
			shPostRepository.delete(shPost);
	}

	private ShFormConfiguration getFormConfiguration(ShPostType shPostType) {
		ShFormConfiguration shFormConfiguration = null;

		for (ShPostTypeAttr shPostTypeAttr : shPostType.getShPostTypeAttrs()) {
			if (shPostTypeAttr.getShWidget().getName().equals(ShSystemWidget.FORM_CONFIGURATION)) {
				JSONObject formConfiguration = new JSONObject(shPostTypeAttr.getWidgetSettings());
				shFormConfiguration = new ShFormConfiguration(formConfiguration);
			}
		}

		return shFormConfiguration;
	}
}
