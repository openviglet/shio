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
package com.viglet.shio.persistence.model.object.impl;


import java.time.Instant;
import java.util.Date;
import java.util.Set;

import com.viglet.shio.persistence.model.post.impl.ShPostAttrImpl;

/**
 * The interface class for the ShObject.
 * 
 * @author Alexandre Oliveira
 * @since 0.3.7
 * 
 */
public interface ShObjectImpl {

	String getId();

	void setId(String id);

	String getOwner();

	void setOwner(String owner);

	String getFurl();

	void setFurl(String furl);

	Instant getDate();

	void setDate(Instant date);

	String getModifier();

	void setModifier(String modifier);

	String getPublisher();

	void setPublisher(String publisher);

	Date getModifiedDate();

	void setModifiedDate(Date modifiedDate);

	Date getPublicationDate();

	void setPublicationDate(Date publicationDate);

	int getPosition();

	void setPosition(int position);

	String getObjectType();

	void setObjectType(String objectType);

	Set<? extends ShPostAttrImpl> getShPostAttrRefs(); //NOSONAR

	void setShPostAttrRefs(Set<? extends ShPostAttrImpl> shPostAttrRefs);

	boolean isPublished();

	void setPublished(boolean published);

	String getPublishStatus();

	void setPublishStatus(String publishStatus);

	Set<String> getShUsers();

	void setShUsers(Set<String> shUsers);

	Set<String> getShGroups();

	void setShGroups(Set<String> shGroups);

	boolean isPageAllowRegisterUser();

	void setPageAllowRegisterUser(boolean pageAllowRegisterUser);

	boolean isPageAllowGuestUser();

	void setPageAllowGuestUser(boolean pageAllowGuestUser);

	Set<String> getShPageGroups();

	void setShPageGroups(Set<String> shPageGroups);

}