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
package com.viglet.shio.api.post.xp;

import java.security.Principal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.bean.xp.ShPostXP;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.property.ShMgmtProperties;
import com.viglet.shio.utils.ShObjectUtils;
import com.viglet.shio.utils.ShPostUtils;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Post API.
 *
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
@RestController
@RequestMapping("/api/v2/post/xp")
@Tag( name = "Post XP", description = "Post XP API")
public class ShPostXPAPI {
	private static final Log logger = LogFactory.getLog(ShPostXPAPI.class);

	private final ShPostUtils shPostUtils;
	private final ShObjectUtils shObjectUtils;
	private final ShMgmtProperties shMgmtProperties;

	@Autowired
	public ShPostXPAPI(ShPostUtils shPostUtils, ShObjectUtils shObjectUtils, ShMgmtProperties shMgmtProperties) {
		this.shPostUtils = shPostUtils;
		this.shObjectUtils = shObjectUtils;
		this.shMgmtProperties = shMgmtProperties;
	}

	/**
	 * Post XP Edit API
	 * 
	 * @param id Post Id
	 * @param principal Logged User
	 * @return ResponseEntity Post Object
	 */
	@GetMapping("/{id}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ResponseEntity<ShPostXP> shPostEdit(@PathVariable String id, Principal principal) {
		if (shObjectUtils.canAccess(principal, id)) {
			if (logger.isDebugEnabled())
				logger.debug("Mgmt: " + shMgmtProperties.isEnabled());
			ShPostXP shPostXP = new ShPostXP();

			ShPostImpl shPost = shPostUtils.loadLazyPost(id, false);
			shPostUtils.syncWithPostType(shPost);

			shPostXP.setShPost(shPost);
			shPostXP.setAllowPublish(shPostUtils.allowPublish(shPost.getShPostType(), principal));
			return new ResponseEntity<>(shPostXP, HttpStatus.OK);
		}
		return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
	}
}
