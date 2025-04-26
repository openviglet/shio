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

import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;

/**
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
public class ShPostAttrSpecs {

	private ShPostAttrSpecs() {
		throw new IllegalStateException("ShPostAttrSpecs class");
	}
	
	public static Specification<ShPostAttr> hasShPostTypeAttr(ShPostTypeAttr shPostTypeAttr) {
		return (shPostAttr, query, cb) -> cb.equal(shPostAttr.get("shPostTypeAttr"), shPostTypeAttr);
	}

	public static Specification<ShPostAttr> hasSites(List<ShSite> shSites) {
		return (shPostAttr, query, cb) -> {
			final Path<ShPostImpl> shPost = shPostAttr.<ShPostImpl>get("shPost");

			query.distinct(true);
			Subquery<ShPost> postSubQuery = query.subquery(ShPost.class);
			Root<ShPost> post = postSubQuery.from(ShPost.class);

			final Path<ShSite> shSitePath = post.<ShSite>get("shSite");

			postSubQuery.select(post);
			postSubQuery.where(shSitePath.in(shSites));

			return shPost.in(postSubQuery);
		};
	}

	public static Specification<ShPostAttr> conditionParams(String attrValue, String condition) {
		return new Specification<ShPostAttr>() {

			private static final long serialVersionUID = 1L;

			@Override
			public Predicate toPredicate(Root<ShPostAttr> root, CriteriaQuery<?> query,
					CriteriaBuilder criteriaBuilder) {

				String attrName = "strValue";
				List<Predicate> predicates = ShPostSpecsCommons.predicateAttrCondition(attrName, attrValue, condition,
						root, criteriaBuilder);
				return criteriaBuilder.and(predicates.toArray(new Predicate[predicates.size()]));
			}
		};
	}
}
