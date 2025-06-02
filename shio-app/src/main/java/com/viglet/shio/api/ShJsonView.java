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
package com.viglet.shio.api;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author Alexandre Oliveira
 */
public class ShJsonView implements Serializable {
	@Serial
	private static final long serialVersionUID = 3989499187492868996L;

	public interface ShJsonViewGenericType {}

	public interface ShJsonViewObject extends ShJsonViewGenericType {}

	public interface ShJsonViewReference extends ShJsonViewGenericType {}

	public interface ShJsonViewPostType extends ShJsonViewGenericType {}

	public interface ShJsonViewPost extends ShJsonViewGenericType {}

	public interface ShJsonViewPostTypeAttr extends ShJsonViewGenericType {}
}
