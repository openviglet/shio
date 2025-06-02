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
package com.viglet.shio.graphql.playground;

import lombok.Getter;
import org.springframework.stereotype.Component;

import com.viglet.shio.graphql.playground.properties.Cdn;
import com.viglet.shio.graphql.playground.properties.CodeMirror;
import com.viglet.shio.graphql.playground.properties.Endpoint;
import com.viglet.shio.graphql.playground.properties.Props;
import com.viglet.shio.graphql.playground.properties.Static;
import com.viglet.shio.graphql.playground.properties.Subscriptions;

/**
* @author Alexandre Oliveira
* @since 0.3.7
*/
@Component
public class ShGraphiQLProperties {

	@Getter
    private final Endpoint endpoint = new Endpoint();
	private final Static staticFile = new Static();
	@Getter
    private final CodeMirror codeMirror = new CodeMirror();
	@Getter
    private final Props props = new Props();
    @Getter
    private final Subscriptions subscriptions = new Subscriptions();
	@Getter
    private final Cdn cdn = new Cdn();

    public Static getStatic() {
		return staticFile;
	}

    public String getPageTitle() {
        return "Shio CMS Playground";
	}

	public String getMapping() {
        return "/graphiql";
	}

}
