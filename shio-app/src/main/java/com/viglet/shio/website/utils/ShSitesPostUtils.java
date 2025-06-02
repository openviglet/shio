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
package com.viglet.shio.website.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.url.ShURLScheme;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.ShPostDraft;
import com.viglet.shio.persistence.model.post.ShPostDraftAttr;
import com.viglet.shio.persistence.model.post.impl.ShPostAttrImpl;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.post.relator.ShRelatorItem;
import com.viglet.shio.persistence.model.post.relator.impl.ShRelatorItemImpl;
import com.viglet.shio.persistence.repository.post.ShPostAttrRepository;
import com.viglet.shio.persistence.repository.post.ShPostDraftAttrRepository;
import com.viglet.shio.persistence.repository.post.ShPostDraftRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.property.ShMgmtProperties;
import com.viglet.shio.utils.ShFolderUtils;
import com.viglet.shio.utils.ShPostUtils;
import com.viglet.shio.utils.ShStaticFileUtils;
import com.viglet.shio.website.ShContent;

/**
 * Site Post Utils.
 *
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
@Component
public class ShSitesPostUtils {
	private static final Log logger = LogFactory.getLog(ShSitesPostUtils.class);
	private static final String IS_VISIBLE_PAGE = "IS_VISIBLE_PAGE";
	private static final String NO = "no";
	private static final String HOME = "Home";
	@Autowired
	private ShFolderUtils shFolderUtils;
	@Autowired
	private ShURLScheme shURLScheme;
	@Autowired
	private ShPostRepository shPostRepository;
	@Autowired
	private ShPostDraftRepository shPostDraftRepository;
	@Autowired
	private ShPostAttrRepository shPostAttrRepository;
	@Autowired
	private ShPostDraftAttrRepository shPostDraftAttrRepository;
	@Autowired
	private ShStaticFileUtils shStaticFileUtils;
	@Autowired
	private ShMgmtProperties shMgmtProperties;
	@Autowired
	private ShPostUtils shPostUtils;
	@Autowired
	private ShPostTypeRepository shPostTypeRepository;

	public ShPost getFolderIndex(ShFolder shFolder) {
		ShPostType shPostType = shPostTypeRepository.findByName(ShSystemPostType.FOLDER_INDEX);
		List<ShPost> shFolderIndexPosts = shPostRepository.findByShFolderAndShPostTypeOrderByPositionAsc(shFolder,
				shPostType);
		if (!shFolderIndexPosts.isEmpty())
			return getPostByStage(shFolderIndexPosts.get(0));
		return null;
	}

	public boolean isFolderIndex(ShPost shPost) {
		if (shPost.getShPostType().getName().equals(ShSystemPostType.FOLDER_INDEX)) {
			if (logger.isDebugEnabled())
				logger.debug("Is FolderIndex Post");
			return true;
		} else {
			if (logger.isDebugEnabled())
				logger.debug("Is Not FolderIndex Post");
			return false;
		}

	}

	public ShPost getPostByStage(ShPost shPost) {
		if (shPost != null) {
			return shMgmtProperties.isEnabled() ? this.getMgmtPost(shPost) : this.getLivePost(shPost);
		} else {
			return null;
		}
	}

	private ShPost getLivePost(ShPost shPost) {

		if (logger.isDebugEnabled())
			logger.debug("mgmt is not enabled: ");
		if (shPost.isPublished()) {
			if (logger.isDebugEnabled())
				logger.debug("is Published ");
			return shPost;
		} else {
			if (logger.isDebugEnabled())
				logger.debug("is not Published ");
		}
		return null;
	}

	private ShPost getMgmtPost(ShPost shPost) {
		if (logger.isDebugEnabled())
			logger.debug("mgmt is enabled");
		Optional<ShPostDraft> shPostDraftOptional = shPostDraftRepository.findByIdFull(shPost.getId());
		if (shPostDraftOptional.isPresent()) {
			ShPost shPostDraft = shPostUtils.loadPostDraft(shPostDraftOptional.get());
			if (shPostDraft != null)
				return shPostDraft;
		}
		return shPost;
	}

	public List<ShPost> getPostsByStage(List<ShPost> shPosts) {
		List<ShPost> shSelectedPosts = new ArrayList<>();
		for (ShPost shPost : shPosts) {
			ShPost shSelectedPost = this.getPostByStage(shPost);
			if (shSelectedPost != null)
				shSelectedPosts.add(shSelectedPost);
		}
		return shSelectedPosts;
	}

	public ShPostAttrImpl getPostAttrByStage(ShPostAttrImpl shPostAttr) {
		if (shPostAttr != null) {
			if (shMgmtProperties.isEnabled()) {
				Optional<ShPostDraftAttr> shPostDraftAttrOptional = shPostDraftAttrRepository
						.findById(shPostAttr.getId());
				if (shPostDraftAttrOptional.isPresent()) {
					return shPostUtils.loadPostDraftAttr(shPostDraftAttrOptional.get());
				}
				return shPostAttr;
			} else {
				ShPost shPost = this.getPost(shPostAttr);
				if (shPost != null && shPost.isPublished()) {
					return shPostAttr;
				}
			}

		}
		return null;

	}

	public ShPost getPost(ShPostAttrImpl shPostAttr) {
		if (shPostAttr.getShPost() != null) {
			return (ShPost) shPostAttr.getShPost();
		} else {
			return this.getPostNested(shPostAttr);
		}
	}

	private ShPost getPostNested(ShPostAttrImpl shPostAttr) {
		if (shPostAttr.getShParentRelatorItem() != null
				&& shPostAttr.getShParentRelatorItem().getShParentPostAttr() != null) {
			if (shPostAttr.getShParentRelatorItem().getShParentPostAttr().getShPost() != null)
				return (ShPost) shPostAttr.getShParentRelatorItem().getShParentPostAttr().getShPost();
			else
				return this.getPostNested(shPostAttr.getShParentRelatorItem().getShParentPostAttr());

		}
		return null;
	}

	public Map<String, ShPostAttr> toMap(String postId) {
		Optional<ShPost> shPost = shPostRepository.findById(postId);
		return shPost.isPresent() ? this.postToMap(this.getPostByStage(shPost.get())) : null;
	}

	public JSONObject toJSON(ShPost shPost) {

		return new JSONObject(this.toSystemMap(shPost));
	}

	public ShContent toSystemMap(ShPostImpl shPostImpl) {
		ShContent shPostItemAttrs = new ShContent();

		Map<String, Object> shPostObject = new HashMap<>();
		shPostObject.put("id", shPostImpl.getId());
		shPostObject.put("postTypeName", shPostImpl.getShPostType().getName());
		shPostObject.put("title", shPostImpl.getTitle());
		shPostObject.put("summary", shPostImpl.getSummary());
		shPostObject.put("link", this.generatePostLink(shPostImpl));
		shPostObject.put("parentFolder", shPostImpl.getShFolder().getId());
		for (ShPostAttrImpl shPostAttr : shPostImpl.getShPostAttrs()) {
			if (shPostAttr.getShPostTypeAttr() != null && shPostAttr.getShPostTypeAttr().getName() != null) {
				shPostItemAttrs.put(shPostAttr.getShPostTypeAttr().getName(), shPostAttr.getStrValue());
			}
		}

		shPostItemAttrs.put("system", shPostObject);

		return shPostItemAttrs;
	}

	public Map<String, ShPostAttr> postToMap(ShPost shPost) {

		if (shPost != null) {
			Set<ShPostAttr> shPostAttrList = (shPostAttrRepository.findByShPost(shPost));

			Map<String, ShPostAttr> shPostMap = new HashMap<>();
			ShPostAttr shPostAttrId = new ShPostAttr();
			shPostAttrId.setStrValue(shPost.getId());
			ShPostAttr shPostAttrType = new ShPostAttr();
			shPostAttrType.setStrValue(shPost.getShPostType().getName());
			shPostMap.put("__type__", shPostAttrType);
			shPostMap.put("id", shPostAttrId);
			for (ShPostAttr shPostAttr : shPostAttrList) {
				if (shPostAttr != null && shPostAttr.getShPostTypeAttr() != null)
					shPostMap.put(shPostAttr.getShPostTypeAttr().getName(), shPostAttr);
			}
			return shPostMap;
		} else {
			return Collections.emptyMap();
		}

	}

	@SuppressWarnings("unchecked")
	public List<Map<String, ShPostAttr>> relationToMap(ShPostAttrImpl shPostAttr) {

		if (shPostAttr != null) {
			List<Map<String, ShPostAttr>> relations = new ArrayList<>();

			Set<ShRelatorItem> shRelatorItems = (Set<ShRelatorItem>) shPostAttr.getShChildrenRelatorItems();
			List<ShRelatorItem> shRelatorItemsByOrdinal = new ArrayList<>();
			shRelatorItemsByOrdinal.addAll(shRelatorItems);

			Collections.sort(shRelatorItemsByOrdinal,
					(ShRelatorItem o1, ShRelatorItem o2) -> o1.getOrdinal() - o2.getOrdinal());

			for (ShRelatorItemImpl shRelatorItem : shRelatorItemsByOrdinal) {
				Map<String, ShPostAttr> shRelationMap = new HashMap<>();
				ShPostAttr shPostAttrId = new ShPostAttr();
				shPostAttrId.setStrValue(shRelatorItem.getId());
				shRelationMap.put("id", shPostAttrId);
				for (ShPostAttrImpl shPostAttrRelation : shRelatorItem.getShChildrenPostAttrs()) {
					shRelationMap.put(shPostAttrRelation.getShPostTypeAttr().getName(),
							(ShPostAttr) shPostAttrRelation);
				}
				relations.add(shRelationMap);
			}

			return relations;
		} else {
			return Collections.emptyList();
		}

	}

	public String generatePostLink(ShPostImpl shPostImpl) {
		ShFolder shFolder = shPostImpl.getShFolder();
		String link = null;
		if (shPostImpl.getShPostType().getName().equals(ShSystemPostType.FILE)) {
			link = shStaticFileUtils.getFileSourceBase(true) + "/" + shFolderUtils.getSite(shFolder).getName()
					+ shFolderUtils.folderPath(shFolder, false, true) + shPostImpl.getTitle();
		} else if (isVisiblePage(shPostImpl)) {
			link = shURLScheme.generateFolderLink(shFolder);
			link = link + shPostImpl.getFurl();
		}
		return link;
	}

	public boolean isVisiblePage(ShObjectImpl shObject) {
		ShFolder shFolder = null;
		if (shObject instanceof ShFolder shFolderInst) {
			shFolder = shFolderInst;
			ShPost shFolderIndexPost = getFolderIndex(shFolder);
			if (shFolderIndexPost != null) {
				Map<String, ShPostAttr> shFolderIndexPostMap = postToMap(shFolderIndexPost);
				if (shFolderIndexPostMap.get(IS_VISIBLE_PAGE) != null
						&& shFolderIndexPostMap.get(IS_VISIBLE_PAGE).getStrValue() != null
						&& shFolderIndexPostMap.get(IS_VISIBLE_PAGE).getStrValue().equals(NO)) {
					return false;
				}
			} else {
				return false;
			}
		} else if (shObject instanceof ShPost) {
			ShPostImpl shPost = (ShPostImpl) shObject;
			shFolder = shPost.getShFolder();
		}
		if (shFolder != null) {
			List<ShFolder> breadcrumb = shFolderUtils.breadcrumb(shFolder);
			return breadcrumb.get(0).getName().equals(HOME);
		} else {
			return false;
		}

	}
	public String generatePostLinkById(String postID) {
		if (postID != null) {
			try {
				ShPost shPost = shPostRepository.findById(postID).orElse(null);
				return this.generatePostLink(shPost);

			} catch (IllegalArgumentException exception) {
				return null;
			}
		} else {
			return null;
		}
	}

}
