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
package com.viglet.shio.website.component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.website.utils.ShSitesPostUtils;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShSearchComponent {

	@Autowired
	private ShPostRepository shPostRepository;
	@Autowired
	private ShSitesPostUtils shSitesPostUtils;
	
	public List<Map<String, ShPostAttr>> search(String q) {
		List<Map<String, ShPostAttr>> shPosts = new ArrayList<>();
		for (ShPost shPost : shPostRepository.findByTitle(q)) {
			Map<String, ShPostAttr> shPostObject = shSitesPostUtils.postToMap(shPost);
			shPosts.add(shPostObject);
		}
		return shPosts;
	}
}
