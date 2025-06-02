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
package com.viglet.shio.exchange;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.viglet.shio.exchange.file.ShFileExchange;
import com.viglet.shio.exchange.folder.ShFolderExchange;
import com.viglet.shio.exchange.post.ShPostExchange;
import com.viglet.shio.exchange.post.type.ShPostTypeExchange;
import com.viglet.shio.exchange.site.ShSiteExchange;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Alexandre Oliveira
 */
@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShExchange {

	@JsonInclude(Include.NON_NULL)
	private List<ShSiteExchange> sites;

	@JsonInclude(Include.NON_NULL)
	private List<ShFolderExchange> folders;

	@JsonInclude(Include.NON_NULL)
	private List<ShPostExchange> posts;

	@JsonInclude(Include.NON_NULL)
	private List<ShPostTypeExchange> postTypes;

	@JsonInclude(Include.NON_NULL)
	private List<ShFileExchange> files;

}
