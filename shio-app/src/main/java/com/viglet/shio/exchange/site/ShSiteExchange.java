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
package com.viglet.shio.exchange.site;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Alexandre Oliveira
 */
@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShSiteExchange {
	
	private String id;

	private String name;

	@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm'Z'")
	private Date date;

	private String description;

	private String url;

	private String postTypeLayout;
	
	private String searchablePostTypes;
	
	private String owner;
	
	private String furl;
	
	private List<String> rootFolders;

	public ShSiteExchange() {
		super();
	}

}
