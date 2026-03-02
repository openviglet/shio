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
package com.viglet.shio.widget;

import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShHiddenWidget extends ShDefaultWidget {
	ShHiddenWidget(SpringTemplateEngine templateEngine) {
		super(templateEngine);
	}

	@Override
	public void setTemplate() {
		this.template = "widget/hidden/hidden-widget";

	}

	@Override
	public String render(ShPostTypeAttr shPostTypeAttr, ShObjectImpl shObject) {

		String widgetSettings = shPostTypeAttr.getWidgetSettings();
		JSONObject settings = new JSONObject(widgetSettings);
		String defaultValue = settings.getString("defaultValue");

		final Context ctx = new Context();
		ctx.setVariable("shPostTypeAttr", shPostTypeAttr);
		ctx.setVariable("defaultValue", defaultValue);

		return templateEngine.process(this.template, ctx);
	}

}
