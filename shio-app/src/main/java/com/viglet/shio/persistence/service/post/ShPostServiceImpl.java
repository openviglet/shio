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
package com.viglet.shio.persistence.service.post;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.google.common.collect.Sets;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostAttrRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeAttrRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

import static com.viglet.shio.persistence.spec.post.ShPostAttrSpecs.conditionParams;
import static com.viglet.shio.persistence.spec.post.ShPostAttrSpecs.hasShPostTypeAttr;
import static com.viglet.shio.persistence.spec.post.ShPostSpecs.hasSites;
import static com.viglet.shio.persistence.spec.post.ShPostSpecs.hasShPostType;
import static com.viglet.shio.persistence.spec.post.ShPostSpecs.hasSystemAttr;
import static com.viglet.shio.persistence.spec.post.ShPostSpecs.hasPosts;
import static org.springframework.data.jpa.domain.Specification.where;

/**
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
@Service
public class ShPostServiceImpl implements ShPostService {

	private final ShPostRepository shPostRepository;
	private final ShPostAttrRepository shPostAttrRepository;
	private final ShPostTypeAttrRepository shPostTypeAttrRepository;
	private final ShSiteRepository shSiteRepository;

	@Autowired
	public ShPostServiceImpl(ShPostRepository shPostRepository, ShPostAttrRepository shPostAttrRepository, ShPostTypeAttrRepository shPostTypeAttrRepository, ShSiteRepository shSiteRepository) {
		this.shPostRepository = shPostRepository;
		this.shPostAttrRepository = shPostAttrRepository;
		this.shPostTypeAttrRepository = shPostTypeAttrRepository;
		this.shSiteRepository = shSiteRepository;
	}

	public List<ShPost> findByShPostTypeAndAttrNameAndAttrValueAndConditionAndSites(ShPostType shPostType,
			String attrName, String attrValue, String condition, List<String> siteIds) {

		Specification<ShPost> shPostSpecs = where(hasShPostType(shPostType));

		if (!attrName.startsWith("_")) {

			ShPostTypeAttr shPostTypeAttr = shPostTypeAttrRepository.findByShPostTypeAndName(shPostType,
					attrName.toUpperCase());
			Set<ShPostAttr> shPostAttrs = Sets.newHashSet(shPostAttrRepository.findAll(
					where(conditionParams(attrValue, condition)).and(hasShPostTypeAttr(shPostTypeAttr))));

			List<ShPost> shPosts = shPostRepository.findByShPostAttrsIn(shPostAttrs);

			shPostSpecs = shPostSpecs.and(hasPosts(shPosts));
		} else {
	
			shPostSpecs = shPostSpecs.and(hasSystemAttr(attrName, attrValue, condition));
		}
		
		
		if (siteIds != null && !siteIds.isEmpty()) {
			Collection<String> siteCollection = new ArrayList<>(siteIds);

			List<ShSite> shSites = shSiteRepository.findByIdIn(siteCollection);
			shPostSpecs = shPostSpecs.and(hasSites(shSites));
		}

		return shPostRepository.findAll(shPostSpecs);
	}

}
