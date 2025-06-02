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
package com.viglet.shio.provider.exchange.otcs.bean.result;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 */
@Setter
@Getter
public class ShOTCSPropertiesBean extends ShOTCSExternalBean {

	@JsonProperty("advanced_versioning")
	private String advancedVersioning;

	private boolean container;

	@JsonProperty("container_size")
	private int containerSize;

	@JsonProperty("create_date")
	private Date createDate;

	@JsonProperty("create_user_id")
	private int createUserId;

	private List<String> customsidebars;

	private String description;

	@JsonProperty("description_multilingual")
	private Map<String, Object> descriptionMultilingual;
	
	private boolean favorite;

	private int id;

	@JsonProperty("image_folder_id")
	private int imageFolderId;

	private boolean imagebrowseenabled;

	@JsonProperty("main_page_id")
	private int mainPageId;

	@JsonProperty("righmime_typet_id")
	private String mimeType;

	@JsonProperty("modify_date")
	private Date modifyDate;

	@JsonProperty("modify_user_id")
	private int modifyUserId;

	private String name;

	@JsonProperty("name_multilingual")
	private Map<String, Object> nameMultilingual;

	private String owner;

	@JsonProperty("owner_group_id")
	private int ownerGroupId;

	@JsonProperty("owner_user_id")
	private int ownerUserId;

	@JsonProperty("parent_id")
	private int parentId;

	@JsonProperty("permissions_model")
	private String permissionsModel;

	private boolean reserved;

	@JsonProperty("reserved_date")
	private Date reservedDate;

	@JsonProperty("reserved_shared_collaboration")
	private boolean reservedSharedCollaboration;

	@JsonProperty("reserved_user_id")
	private int reservedUserId;

	private int size;

	@JsonProperty("size_formatted")
	private String sizeFormatted;

	private int type;

	@JsonProperty("type_name")
	private String typeName;

	private String versionable;

	@JsonProperty("versions_control_advanced")
	private boolean versionsControlAdvanced;

	@JsonProperty("volume_id")
	private int volumeId;

}
