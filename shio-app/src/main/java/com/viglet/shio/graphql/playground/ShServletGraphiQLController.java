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

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * @author Andrew Potter
 * @since 0.3.7
 */

@Controller
public class ShServletGraphiQLController extends ShGraphiQLController{

    @PostConstruct
    @Override
    public void onceConstructed() throws IOException {
        super.onceConstructed();
    }

    @GetMapping(value = "${graphiql.mapping:/graphiql}")
    public void graphiql(HttpServletRequest request, HttpServletResponse response,
                         @PathVariable Map<String, String> params) throws IOException {
        response.setContentType("text/html; charset=UTF-8");
        Object csrf = request.getAttribute("_csrf");
        byte[] graphiqlBytes = super.graphiql(request.getContextPath(), params, csrf);
        response.getOutputStream().write(graphiqlBytes);
    }

}
