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
package com.viglet.shio.persistence.spec.post;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;

import com.viglet.shio.graphql.ShGraphQLConstants;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.site.ShSite;

/**
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
public class ShPostSpecs {

	private ShPostSpecs() {
		throw new IllegalStateException("ShPostSpecs class");
	}
	
	public static Specification<ShPost> hasShPostType(ShPostType shPostType) {
		return (shPost, query, cb) -> cb.equal(shPost.get("shPostType"), shPostType);
	}

	public static Specification<ShPost> hasSites(List<ShSite> shSites) {
		return (shPost, query, cb) -> {
			query.distinct(true);
			final Path<ShSite> shSitePath = shPost.<ShSite>get("shSite");

			return shSitePath.in(shSites);
		};
	}

	public static Specification<ShPost> hasSystemAttr(String attrName, String attrValue, String condition) {
		return new Specification<ShPost>() {

			private static final long serialVersionUID = 1L;

			@Override
			public Predicate toPredicate(Root<ShPost> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {

				Map<String, String> systemAttrsMap = new HashMap<>();

				systemAttrsMap.put(ShGraphQLConstants.TITLE, "title");
				systemAttrsMap.put(ShGraphQLConstants.DESCRIPTION, "summary");
				systemAttrsMap.put(ShGraphQLConstants.FURL, "furl");
				systemAttrsMap.put(ShGraphQLConstants.MODIFIER, "modifier");
				systemAttrsMap.put(ShGraphQLConstants.PUBLISHER, "publisher");
				systemAttrsMap.put(ShGraphQLConstants.FOLDER, "shFolder");
				systemAttrsMap.put(ShGraphQLConstants.CREATED_AT, "createdAt");
				systemAttrsMap.put(ShGraphQLConstants.UPDATED_AT, "updatedAt");
				systemAttrsMap.put(ShGraphQLConstants.PUBLISHED_AT, "publishedAt");
				
				List<Predicate> predicates = ShPostSpecsCommons.predicateAttrCondition(systemAttrsMap.get(attrName),
						attrValue, condition, root, criteriaBuilder);

				return criteriaBuilder.and(predicates.toArray(new Predicate[predicates.size()]));
			}

		};
	}

	public static Specification<ShPost> hasPosts(List<ShPost> shPosts) {
		return (shPost, query, cb) -> {
			query.distinct(true);
			return shPost.in(shPosts);
		};
	}

}
