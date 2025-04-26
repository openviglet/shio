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
package com.viglet.shio.website.component.form;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.widget.ShSystemWidget;
import com.viglet.shio.widget.ShWidgetImplementation;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShFormComponent {
	private static final Log logger = LogFactory.getLog(ShFormComponent.class);
	@Autowired
	private ShPostTypeRepository shPostTypeRepository;
	@Resource
	private ApplicationContext applicationContext;
	@Autowired
	private SpringTemplateEngine templateEngine;
	@Autowired
	private ShObjectRepository shObjectRepository;

	public String byPostType(String shPostTypeName, String shObjectId, HttpServletRequest request) {
		final Context ctx = new Context();
		ShFormConfiguration shFormConfiguration = null;
		ShObjectImpl shObject = shObjectRepository.findById(shObjectId).orElse(null);
		ShPostType shPostType = shPostTypeRepository.findByName(shPostTypeName);
		CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());

		List<String> fields = new ArrayList<>();

		List<ShPostTypeAttr> postTypeAttrByOrdinal = new ArrayList<>(shPostType.getShPostTypeAttrs());

		Collections.sort(postTypeAttrByOrdinal,
				(ShPostTypeAttr o1, ShPostTypeAttr o2) -> o1.getOrdinal() - o2.getOrdinal());

		for (ShPostTypeAttr shPostTypeAttr : postTypeAttrByOrdinal) {
			String className = shPostTypeAttr.getShWidget().getClassName();

			try {
				ShWidgetImplementation object = (ShWidgetImplementation) Class.forName(className)
						.getDeclaredConstructor().newInstance();
				applicationContext.getAutowireCapableBeanFactory().autowireBean(object);
				fields.add(object.render(shPostTypeAttr, shObject));
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException
					| ClassNotFoundException e) {
				logger.error(e);
			}

			if (shPostTypeAttr.getShWidget().getName().equals(ShSystemWidget.FORM_CONFIGURATION)) {
				JSONObject formConfiguration = new JSONObject(shPostTypeAttr.getWidgetSettings());
				shFormConfiguration = new ShFormConfiguration(formConfiguration);
			}
		}

		String token = null;
		if (csrf != null) {
			token = csrf.getToken();
		}

		String method = "POST";
		if (shFormConfiguration != null) {
			method = shFormConfiguration.getMethod().toString();
		}
		ctx.setVariable("token", token);
		ctx.setVariable("shPostType", shPostType);
		ctx.setVariable("shPostTypeAttrs", shPostType.getShPostTypeAttrs());
		ctx.setVariable("fields", fields);
		ctx.setVariable("method", method);

		return templateEngine.process("form", ctx);
	}
}
