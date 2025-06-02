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
package com.viglet.shio.provider.auth.otds.bean;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @author Alexandre Oliveira
 */
@Setter
@Getter
public class ShOTDSUserResponseBean {

	private String userPartitionID;

	private List<ShOTDSValuesBean> values;

	private String location;

	private String description;

	private Object customAttributes;

	private String objectClass;

	private String id;

	private String uuid;

	private String originUUID;

	private String urlId;

	private String name;

}
