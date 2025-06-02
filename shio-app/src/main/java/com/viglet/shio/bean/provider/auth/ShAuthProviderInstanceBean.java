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
package com.viglet.shio.bean.provider.auth;

import java.util.HashMap;
import java.util.Map;

import com.viglet.shio.persistence.model.provider.auth.ShAuthProviderInstance;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Alexandre Oliveira
 */
@Setter
@Getter
public class ShAuthProviderInstanceBean extends ShAuthProviderInstance  {

	private Map<String, String> properties = new HashMap<>();


}
