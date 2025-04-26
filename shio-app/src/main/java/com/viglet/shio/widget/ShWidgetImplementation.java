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

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.website.ShSitesContextURL;

/**
 * @author Alexandre Oliveira
 */
@Component
public interface ShWidgetImplementation {
	void setTemplate();
	public String render(ShPostTypeAttr shPostTypeAttr, ShObjectImpl shObject);
	public boolean validateForm(HttpServletRequest request, ShPostTypeAttr shPostTypeAttr);
	public void postRender(ShPostImpl shPost, ShSitesContextURL shSitesContextURL) throws IOException;
}
