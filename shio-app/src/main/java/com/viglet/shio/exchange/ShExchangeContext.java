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
package com.viglet.shio.exchange;

import lombok.Getter;
import lombok.Setter;

import java.io.File;

/**
 * Post Exchange Context
 * 
 * @author Alexandre Oliveira
 * @since 0.3.7
 * 
 */
@Setter
@Getter
public class ShExchangeContext {
	private File extractFolder;
	private boolean isCloned;

	public ShExchangeContext(File extractFolder, boolean isCloned) {
		this.extractFolder = extractFolder;
		this.isCloned = isCloned;
	}

}
