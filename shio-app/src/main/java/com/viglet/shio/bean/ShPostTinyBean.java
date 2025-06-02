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
package com.viglet.shio.bean;

import java.util.Date;

import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Alexandre Oliveira
 */
@Setter
@Getter
public class ShPostTinyBean {

	private String id;

	private String title;

	private String summary;

	private int position;

	private Date date;

	private ShPostType shPostType;

	private String objectType;

	private String publishStatus;

	private boolean published;

	public ShPostTinyBean(ShPostImpl shPost) {
		this.id = shPost.getId();
		this.title = shPost.getTitle();
		this.summary = shPost.getSummary();
		this.position = shPost.getPosition();
		this.date = shPost.getDate();
		this.objectType = shPost.getObjectType();
		this.publishStatus = shPost.getPublishStatus();
		this.published = shPost.isPublished();

		this.shPostType = new ShPostType();
		this.shPostType.setId(shPost.getShPostType().getId());
		this.shPostType.setName(shPost.getShPostType().getName());
		this.shPostType.setTitle(shPost.getShPostType().getTitle());
	}

}
