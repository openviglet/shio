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

import java.util.HashMap;
import java.util.Map;

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
public class ShComboBoxWidget extends ShDefaultWidget {

	ShComboBoxWidget(SpringTemplateEngine templateEngine) {
		super(templateEngine);
	}

	@Override
	public void setTemplate() {
		this.template = "widget/combo-box/combo-box-widget";

	}

	@Override
	public String render(ShPostTypeAttr shPostTypeAttr, ShObjectImpl shObject) {

		String widgetSettings = shPostTypeAttr.getWidgetSettings();
		JSONObject settings = new JSONObject(widgetSettings);
		String[] choicesArray = settings.getString("choices").split("\n");
		Map<String, String> choices = new HashMap<>();
		for (String choice : choicesArray) {
			String[] choiceKV = choice.split(":");
			String name = choiceKV[0].trim();
			String label = choiceKV[1].trim();
			choices.put(name, label);
		}

		final Context ctx = new Context();
		ctx.setVariable("shPostTypeAttr", shPostTypeAttr);
		ctx.setVariable("choices", choices);

		return templateEngine.process(this.template, ctx);
	}
}
