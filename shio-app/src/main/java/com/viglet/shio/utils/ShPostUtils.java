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
package com.viglet.shio.utils;

import java.io.File;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.bean.ShPostTinyBean;
import com.viglet.shio.object.ShObjectType;
import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.object.ShObjectDraft;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.ShPostDraft;
import com.viglet.shio.persistence.model.post.ShPostDraftAttr;
import com.viglet.shio.persistence.model.post.impl.ShPostAttrImpl;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.post.relator.impl.ShRelatorItemImpl;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.reference.ShReference;
import com.viglet.shio.persistence.model.reference.ShReferenceDraft;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.auth.ShGroupRepository;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.persistence.repository.post.ShPostAttrRepository;
import com.viglet.shio.persistence.repository.post.ShPostDraftAttrRepository;
import com.viglet.shio.persistence.repository.post.ShPostDraftRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.persistence.repository.post.relator.ShRelatorItemRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeAttrRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.reference.ShReferenceDraftRepository;
import com.viglet.shio.persistence.repository.reference.ShReferenceRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.widget.ShSystemWidget;

/**
 * Post Utils.
 *
 * @author Alexandre Oliveira
 * @since 0.3.0
 */
@Component
public class ShPostUtils {
	private static final Log logger = LogFactory.getLog(ShPostUtils.class);

	private static final String DATE_FORMAT = "dd/MM/yyyyy";

	@Autowired
	private ShFolderUtils shFolderUtils;
	@Autowired
	private ShPostRepository shPostRepository;
	@Autowired
	private ShPostDraftRepository shPostDraftRepository;
	@Autowired
	private ShPostAttrRepository shPostAttrRepository;
	@Autowired
	private ShPostDraftAttrRepository shPostDraftAttrRepository;
	@Autowired
	private ShFolderRepository shFolderRepository;
	@Autowired
	private ShObjectRepository shObjectRepository;
	@Autowired
	private ShReferenceRepository shReferenceRepository;
	@Autowired
	private ShReferenceDraftRepository shReferenceDraftRepository;
	@Autowired
	private ShRelatorItemRepository shRelatorItemRepository;
	@Autowired
	private ShPostTypeAttrRepository shPostTypeAttrRepository;
	@Autowired
	private ShPostTypeRepository shPostTypeRepository;
	@Autowired
	private ShUserRepository shUserRepository;
	@Autowired
	private ShGroupRepository shGroupRepository;

	@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
	class NoTypes {
	}

	public ShPost getShPostFromObjectId(String objectId) {

		Optional<ShPost> shPostOpt = shPostRepository.findById(objectId);
		if (shPostOpt.isPresent())
			return shPostOpt.get();
		else
			return null;
	}

	public ShPost copy(ShPostImpl shPost, ShFolder shFolderDest) {

		List<ShPostTinyBean> shPostTinyBeans = shPostRepository.findByShFolderTiny(shFolderDest.getId());

		Set<String> titles = new HashSet<>();
		int lowerPosition = 0;
		for (ShPostTinyBean shPostTinyBean : shPostTinyBeans) {
			titles.add(shPostTinyBean.getTitle());
			if (shPostTinyBean.getPosition() < lowerPosition)
				lowerPosition = shPostTinyBean.getPosition();

		}

		ShPost shPostCopy = new ShPost();
		shPostCopy.setDate(new Date());
		shPostCopy.setShFolder(shFolderDest);
		shPostCopy.setShPostType(shPost.getShPostType());
		shPostCopy.setSummary(shPost.getSummary());
		shPostCopy.setPosition(lowerPosition - 1);

		shPostCopy.setTitle(copyTitle(shPost, titles));

		shPostRepository.save(shPostCopy);

		copyAttributes(shPost, shPostCopy);

		return shPostCopy;
	}

	private void copyAttributes(ShPostImpl shPost, ShPost shPostCopy) {
		Set<ShPostAttr> shPostAttrs = shPostAttrRepository.findByShPost(shPost);
		for (ShPostAttrImpl shPostAttr : shPostAttrs) {
			ShPostAttr shPostAttrClone = new ShPostAttr();
			if (shPostAttr.getShPostTypeAttr().getIsTitle() == 1 && shPostAttr.getStrValue() != null) {
				shPostAttrClone.setStrValue(shPostCopy.getTitle());
			} else {
				shPostAttrClone.setStrValue(shPostAttr.getStrValue());
			}
			shPostAttrClone.setDateValue(shPostAttr.getDateValue());
			shPostAttrClone.setIntValue(shPostAttr.getIntValue());
			shPostAttrClone.setArrayValue(shPostAttr.getArrayValue());
			shPostAttrClone.setReferenceObject(shPostAttr.getReferenceObject());
			shPostAttrClone.setShPost(shPostCopy);
			shPostAttrClone.setShPostTypeAttr(shPostAttr.getShPostTypeAttr());
			shPostAttrClone.setType(shPostAttr.getType());
			shPostAttrRepository.save(shPostAttrClone);
		}
	}

	private String copyTitle(ShPostImpl shPost, Set<String> titles) {
		String title = shPost.getTitle();
		if (titles.contains(String.format("Copy of %s", shPost.getTitle()))) {
			int countSameTitle = 0;
			boolean uniqueTitle = false;
			while (!uniqueTitle) {
				title = String.format("Copy (%d) of %s", countSameTitle + 1, shPost.getTitle());
				if (!titles.contains(title) || (countSameTitle > titles.size())) {
					uniqueTitle = true;
				}
				countSameTitle++;
			}
		} else {
			title = String.format("Copy of %s", shPost.getTitle());
		}
		return title;
	}

	public void referencedFile(ShPostAttrImpl shPostAttr, ShPostImpl shPost) {
		if (!shPost.getShPostType().getName().equals(ShSystemPostType.FILE)) {
			this.referencedPost(shPostAttr, shPost);
		}
	}

	public void referencedPost(ShPostAttrImpl shPostAttr, ShPostImpl shPost) {
		if (shPostAttr.getStrValue() == null) {
			shPostAttr.setReferenceObject(null);
		} else {
			removeOldReference(shPostAttr, shPost);
			createNewReference(shPostAttr, shPost);
		}
	}

	private void createNewReference(ShPostAttrImpl shPostAttr, ShPostImpl shPost) {
		try {
			ShObject shObjectReferenced = shObjectRepository.findById(shPostAttr.getStrValue()).orElse(null);
			// Create new reference
			if (shPost != null && shObjectReferenced != null) {
				ShReference shReference = new ShReference();
				shReference.setShObjectFrom((ShObject) shPost);
				shReference.setShObjectTo(shObjectReferenced);
				shReferenceRepository.saveAndFlush(shReference);
				shPostAttr.setReferenceObject(shObjectReferenced);
			}
		} catch (IllegalArgumentException iae) {
			// Re-thing about ignore this
			shPostAttr.setReferenceObject(null);
		}
	}

	private void removeOldReference(ShPostAttrImpl shPostAttr, ShPostImpl shPost) {
		// Two or more attributes with FILE Widget and same file, it cannot remove
		// a valid reference
		// Remove old references
		List<ShReference> shOldReferences = shReferenceRepository.findByShObjectFrom((ShObject) shPost);
		if (!shOldReferences.isEmpty()) {
			for (ShReference shOldReference : shOldReferences) {
				// Find by shPostAttr.getStrValue()
				if (shOldReference.getShObjectTo().getId().equals(shPostAttr.getStrValue())) {
					shReferenceRepository.delete(shOldReference);

				}

				// Find by shPostAttr.getReferenceObject()
				if (shPostAttr.getReferenceObject() != null) {
					ShObjectImpl shObject = shPostAttr.getReferenceObject();
					if (shOldReference.getShObjectTo().getId().equals(shObject.getId())) {
						shReferenceRepository.delete(shOldReference);
					}

				}
			}

		}
	}

	public void referencedObjectDraft(ShPostAttrImpl shPostAttr, ShPostImpl shPost) {
		if (shPostAttr != null && shPostAttr.getShPostTypeAttr() != null
				&& shPostAttr.getShPostTypeAttr().getShWidget() != null) {
			if (shPostAttr.getShPostTypeAttr().getShWidget().getName().equals(ShSystemWidget.FILE)) {
				this.referencedFileDraft(shPostAttr, shPost);
			} else if (shPostAttr.getShPostTypeAttr().getShWidget().getName().equals(ShSystemWidget.CONTENT_SELECT)) {
				this.referencedPostDraft(shPostAttr, shPost);
			}
		}
	}

	public void referencedObject(ShPostAttrImpl shPostAttr, ShPostImpl shPost) {
		if (shPostAttr != null && shPostAttr.getShPostTypeAttr() != null
				&& shPostAttr.getShPostTypeAttr().getShWidget() != null) {
			if (shPostAttr.getShPostTypeAttr().getShWidget().getName().equals(ShSystemWidget.FILE)) {
				this.referencedFile(shPostAttr, shPost);
			} else if (shPostAttr.getShPostTypeAttr().getShWidget().getName().equals(ShSystemWidget.CONTENT_SELECT)) {
				this.referencedPost(shPostAttr, shPost);
			}
		}
	}

	public void referencedObject(ShPostAttrImpl shPostAttrEdit, ShPostAttrImpl shPostAttr, ShPostImpl shPost) {
		if (shPostAttr != null && shPostAttr.getShPostTypeAttr() != null
				&& shPostAttr.getShPostTypeAttr().getShWidget() != null) {
			if (shPostAttr.getShPostTypeAttr().getShWidget().getName().equals(ShSystemWidget.FILE)) {
				this.referencedFile(shPostAttrEdit, shPostAttr, shPost);
			} else if (shPostAttr.getShPostTypeAttr().getShWidget().getName().equals(ShSystemWidget.CONTENT_SELECT)) {
				this.referencedPost(shPostAttrEdit, shPostAttr, shPost);
			}
		}
	}

	public void referencedFile(ShPostAttrImpl shPostAttrEdit, ShPostAttrImpl shPostAttr, ShPostImpl shPost) {
		if (shPost != null && shPost.getShPostType() != null && shPost.getShPostType().getName() != null
				&& shPost.getShPostType().getName().equals(ShSystemPostType.FILE)) {
			File fileFrom = shFolderUtils.filePath(shPost.getShFolder(), shPostAttrEdit.getStrValue());
			File fileTo = shFolderUtils.filePath(shPost.getShFolder(), shPostAttr.getStrValue());
			if (fileFrom != null && fileTo != null && fileFrom.exists() && !fileFrom.renameTo(fileTo)) {
				logger.error(String.format("Can't rename the file %s", fileFrom.getName()));
			}
		} else {
			this.referencedPost(shPostAttrEdit, shPostAttr, shPost);
		}

	}

	public void referencedFileDraft(ShPostAttrImpl shPostAttr, ShPostImpl shPost) {
		if (!shPost.getShPostType().getName().equals(ShSystemPostType.FILE)) {
			this.referencedPostDraft(shPostAttr, shPost);
		}
	}

	public void referencedPostDraft(ShPostAttrImpl shPostAttr, ShPostImpl shPost) {
		if (shPostAttr.getStrValue() == null) {
			shPostAttr.setReferenceObject(null);
		} else {
			removeOldReferenceDraft(shPostAttr, shPost);
			createNewReferenceDraft(shPostAttr, shPost);
		}
	}

	private void createNewReferenceDraft(ShPostAttrImpl shPostAttr, ShPostImpl shPost) {
		try {
			ShObject shObjectReferenced = shObjectRepository.findById(shPostAttr.getStrValue()).orElse(null);
			// Create new reference
			if (shPost != null && shObjectReferenced != null) {
				ShReferenceDraft shReference = new ShReferenceDraft();
				shReference.setShObjectFrom((ShObjectDraft) shPost);
				shReference.setShObjectTo(shObjectReferenced);
				shReferenceDraftRepository.saveAndFlush(shReference);
				shPostAttr.setReferenceObject(shObjectReferenced);
			}
		} catch (IllegalArgumentException iae) {
			// Re-thing about ignore this
			shPostAttr.setReferenceObject(null);
		}
	}

	private void removeOldReferenceDraft(ShPostAttrImpl shPostAttr, ShPostImpl shPost) {
		// Two or more attributes with FILE Widget and same file, it cannot remove
		// a valid reference
		// Remove old references
		List<ShReferenceDraft> shOldReferences = shReferenceDraftRepository.findByShObjectFrom((ShObjectDraft) shPost);
		if (!shOldReferences.isEmpty()) {
			for (ShReferenceDraft shOldReference : shOldReferences) {
				// Find by shPostAttr.getStrValue()
				if (shOldReference.getShObjectTo().getId().equals(shPostAttr.getStrValue())) {
					shReferenceDraftRepository.delete(shOldReference);

				}

				// Find by shPostAttr.getReferenceObject()
				if (shPostAttr.getReferenceObject() != null) {
					ShObjectImpl shObject = shPostAttr.getReferenceObject();
					if (shOldReference.getShObjectTo().getId().equals(shObject.getId())) {
						shReferenceDraftRepository.delete(shOldReference);
					}
				}
			}
		}
	}

	public void referencedPost(ShPostAttrImpl shPostAttrEdit, ShPostAttrImpl shPostAttr, ShPostImpl shPost) {
		if (shPostAttr.getStrValue() == null) {
			shPostAttr.setReferenceObject(null);
		} else {

			removeOldReferenceEdit(shPostAttrEdit, shPost);
			createNewReferenceEdit(shPostAttr, shPost);
		}
	}

	private void createNewReferenceEdit(ShPostAttrImpl shPostAttr, ShPostImpl shPost) {
		try {
			ShObject shObjectReferenced = shObjectRepository.findById(shPostAttr.getStrValue()).orElse(null);

			ShReference shReference = new ShReference();
			shReference.setShObjectFrom((ShObject) shPost);
			shReference.setShObjectTo(shObjectReferenced);
			shReferenceRepository.saveAndFlush(shReference);
			shPostAttr.setReferenceObject(shObjectReferenced);
		} catch (IllegalArgumentException iae) {
			// Re-thing about ignore this
			shPostAttr.setReferenceObject(null);
		}
	}

	private void removeOldReferenceEdit(ShPostAttrImpl shPostAttrEdit, ShPostImpl shPost) {
		// Two or more attributes with FILE Widget and same file, it cannot remove
		// a valid reference
		// Remove old references
		List<ShReference> shOldReferences = shReferenceRepository.findByShObjectFrom((ShObject) shPost);
		if (!shOldReferences.isEmpty()) {
			for (ShReference shOldReference : shOldReferences) {
				if (shPostAttrEdit.getReferenceObject() != null) {
					ShObjectImpl shObject = shPostAttrEdit.getReferenceObject();
					if (shOldReference.getShObjectTo().getId().equals(shObject.getId())) {
						shReferenceRepository.delete(shOldReference);
						break;
					}

				}
			}
		}
	}

	public ShSite getSite(ShPostImpl shPost) {
		return shFolderUtils.getSite(shPost.getShFolder());
	}

	@SuppressWarnings("unchecked")
	public void updateRelatorInfo(ShPostAttrImpl shPostAttr, ShPostImpl shPost) {
		for (ShRelatorItemImpl shRelatorItem : shPostAttr.getShChildrenRelatorItems()) {
			shRelatorItem.setShParentPostAttr(shPostAttr);
			StringBuilder title = new StringBuilder();
			StringBuilder summary = new StringBuilder();

			for (ShPostAttrImpl shChildrenPostAttr : postAttrsSort(
					(Set<ShPostAttrImpl>) shRelatorItem.getShChildrenPostAttrs())) {
				ShPostTypeAttr shPostTypeAttr = shChildrenPostAttr.getShPostTypeAttr();
				title = setPostTitle(shRelatorItem, title, shChildrenPostAttr, shPostTypeAttr);

				this.setPostSummary(shPostAttr, shRelatorItem, title, summary, shChildrenPostAttr, shPostTypeAttr);

				this.updateRelatorInfo(shChildrenPostAttr, shPost);
			}
		}
	}

	private void setPostSummary(ShPostAttrImpl shPostAttr, ShRelatorItemImpl shRelatorItem, StringBuilder title,
			StringBuilder summary, ShPostAttrImpl shChildrenPostAttr, ShPostTypeAttr shPostTypeAttr) {
		if (shPostTypeAttr.getIsSummary() == 1) {
			String widgetName = shPostTypeAttr.getShWidget().getName();
			if (isReferencedWidget(shChildrenPostAttr, widgetName)) {
				this.summaryRelator(title, summary, shChildrenPostAttr);
			} else if (widgetName.equals(ShSystemWidget.DATE)) {
				this.summaryDate(shPostAttr, title, summary);
			} else {
				if (!StringUtils.isEmpty(title.toString()))
					title.append(", ");
				summary.append(shChildrenPostAttr.getStrValue());
			}

			shRelatorItem.setSummary(StringUtils.abbreviate(summary.toString(), 255));
		}
	}

	private boolean isReferencedWidget(ShPostAttrImpl shChildrenPostAttr, String widgetName) {
		return shChildrenPostAttr.getReferenceObject() != null && widgetName.equals(ShSystemWidget.CONTENT_SELECT)
				|| widgetName.equals(ShSystemWidget.FILE);
	}

	private void summaryDate(ShPostAttrImpl shPostAttr, StringBuilder title, StringBuilder summary) {
		SimpleDateFormat dt = new SimpleDateFormat(DATE_FORMAT);
		if (!StringUtils.isEmpty(title.toString()))
			summary.append(", ");
		summary.append(dt.format(shPostAttr.getDateValue()));
	}

	private void summaryRelator(StringBuilder title, StringBuilder summary, ShPostAttrImpl shChildrenPostAttr) {
		ShObjectImpl shObject = ((ShPostAttr) shChildrenPostAttr).getReferenceObject();
		if (shObject != null) {
			if (shObject.getObjectType().equals(ShObjectType.POST)) {
				summaryPost(title, summary, shObject);
			} else if (shObject.getObjectType().equals(ShObjectType.FOLDER)) {
				summaryFolder(title, summary, shObject);
			}
		}
	}

	private void summaryFolder(StringBuilder title, StringBuilder summary, ShObjectImpl shObject) {
		Optional<ShFolder> shFolderReferenced = shFolderRepository.findById(shObject.getId());
		if (shFolderReferenced.isPresent()) {
			if (!StringUtils.isEmpty(title.toString()))
				summary.append(", ");
			summary.append(shFolderReferenced.get().getName());
		}
	}

	private void summaryPost(StringBuilder title, StringBuilder summary, ShObjectImpl shObject) {
		Optional<ShPost> shPostReferenced = shPostRepository.findById(shObject.getId());
		if (shPostReferenced.isPresent()) {
			if (!StringUtils.isEmpty(title.toString()))
				summary.append(", ");
			summary.append(shPostReferenced.get().getTitle());
		}
	}

	private StringBuilder setPostTitle(ShRelatorItemImpl shRelatorItem, StringBuilder title,
			ShPostAttrImpl shChildrenPostAttr, ShPostTypeAttr shPostTypeAttr) {
		if (shPostTypeAttr.getIsTitle() == 1) {
			String widgetName = shPostTypeAttr.getShWidget().getName();

			if (isReferencedWidget(shChildrenPostAttr, widgetName)) {
				this.summaryRelator(title, title, shChildrenPostAttr);
			} else if (widgetName.equals(ShSystemWidget.DATE)) {
				this.summaryDate(shChildrenPostAttr, title, title);
			} else {
				if (!StringUtils.isEmpty(title.toString()))
					title.append(", ");
				title.append(shChildrenPostAttr.getStrValue());
			}

			String titleStr = title.toString();
			if (StringUtils.isEmpty(titleStr) || titleStr.equals("null")) {
				title = new StringBuilder();
				titleStr = shChildrenPostAttr.getId();
				if (StringUtils.isEmpty(titleStr) || titleStr.equals("null")) {
					titleStr = "Untitled";
				}
			}

			shRelatorItem.setTitle(StringUtils.abbreviate(titleStr, 255));
		}
		return title;
	}

	public List<ShPostAttrImpl> postAttrsSort(Set<ShPostAttrImpl> shPostAttrs) {
		List<ShPostAttrImpl> shPostAttrsByOrdinal = new ArrayList<>(shPostAttrs);
		Collections.sort(shPostAttrsByOrdinal, (ShPostAttrImpl o1,
				ShPostAttrImpl o2) -> o1.getShPostTypeAttr().getOrdinal() - o2.getShPostTypeAttr().getOrdinal());
		return shPostAttrsByOrdinal;
	}

	@SuppressWarnings("unchecked")
	public ShPost loadLazyPost(String id, boolean getPostPublished) {
		Optional<ShPost> shPostOptional = shPostRepository.findByIdFull(id);
		Optional<ShPostDraft> shPostDraftOptional = shPostDraftRepository.findByIdFull(id);
		if (!shPostOptional.isPresent())
			return null;

		ShPost shPost = shPostOptional.get();

		if (!getPostPublished && shPost.isPublished() && shPostDraftOptional.isPresent()) {
			ShPost shPostDraft = loadPostDraft(shPostDraftOptional.get());
			if (shPostDraft != null) {
				shPost = shPostDraft;
			}
		} else {
			logger.debug("Get Publish Content");
			shPost.setShPostAttrs(shPostAttrRepository.findByShPostAll(shPost));
			this.loadPostAttribs((Set<ShPostAttr>) shPost.getShPostAttrs());

		}

		return shPost;
	}

	@SuppressWarnings("unchecked")
	public ShPost loadPostDraft(ShPostDraft shPostDraft) {
		logger.debug("Get Draft");
		ShPost shPost = null;
		if (shPostDraft != null) {

			ObjectMapper mapper = new ObjectMapper().addMixIn(ShPostDraft.class, NoTypes.class);

			try {
				shPostDraft.setShPostAttrs(shPostDraftAttrRepository.findByShPostAll(shPostDraft));
				String jsonInString = mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
						.configure(MapperFeature.USE_BASE_TYPE_AS_DEFAULT_IMPL, true)
						.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
						.writerWithView(ShJsonView.ShJsonViewObject.class).writeValueAsString(shPostDraft)
						.replace("\"@type\":\"ShPostDraftAttr\"", "\"@type\":\"ShPostAttr\"");
				shPost = mapper.readValue(jsonInString, ShPost.class);
				this.loadPostDraftAttribs((Set<ShPostAttr>) shPost.getShPostAttrs());

			} catch (JsonProcessingException e) {
				logger.error("loadPostDraft", e);
			}
		}
		return shPost;
	}

	public ShPostAttrImpl loadPostDraftAttr(ShPostDraftAttr shPostDraftAttr) {
		logger.debug("Get Draft");
		ShPostAttrImpl shPostAttr = null;
		if (shPostDraftAttr != null) {

			ObjectMapper mapper = new ObjectMapper();
			try {
				String jsonInString = mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
						.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
						.writerWithView(ShJsonView.ShJsonViewObject.class).writeValueAsString(shPostDraftAttr);
				shPostAttr = mapper.readValue(jsonInString, ShPostAttr.class);

			} catch (JsonProcessingException e) {
				logger.error("loadPostDraftAttr", e);
			}
		}
		return shPostAttr;
	}

	@SuppressWarnings("unchecked")
	private void loadPostAttribs(Set<ShPostAttr> shPostAttrs) {
		for (ShPostAttrImpl shPostAttr : shPostAttrs) {
			shPostAttr.setShChildrenRelatorItems(shRelatorItemRepository.findByShParentPostAttr(shPostAttr));
			shPostAttr.getShPostTypeAttr().setShPostTypeAttrs(
					shPostTypeAttrRepository.findByShParentPostTypeAttr(shPostAttr.getShPostTypeAttr()));
			if (shPostAttr.getReferenceObject() != null) {
				if (shPostAttr.getReferenceObject() instanceof ShPost) {
					shPostAttr.setReferenceObject(
							shPostRepository.findByIdFull(shPostAttr.getReferenceObject().getId()).orElse(null));
				} else if (shPostAttr.getReferenceObject() instanceof ShFolder) {
					shPostAttr.setReferenceObject(
							shFolderRepository.findById(shPostAttr.getReferenceObject().getId()).orElse(null));
				}
			}
			if (!shPostAttr.getShChildrenRelatorItems().isEmpty()) {
				shPostAttr.getShChildrenRelatorItems().forEach(shRelatorItem -> {
					shRelatorItem
							.setShChildrenPostAttrs(shPostAttrRepository.findByShParentRelatorItemJoin(shRelatorItem));
					this.loadPostAttribs((Set<ShPostAttr>) shRelatorItem.getShChildrenPostAttrs());
				});

			}
		}
	}

	@SuppressWarnings("unchecked")
	private void loadPostDraftAttribs(Set<ShPostAttr> shPostAttrs) {
		for (ShPostAttrImpl shPostAttr : shPostAttrs) {
			if (shPostAttr.getReferenceObject() != null) {
				if (shPostAttr.getReferenceObject().getObjectType().equals(ShObjectType.POST)) {
					shPostAttr.setReferenceObject(
							shPostRepository.findByIdFull(shPostAttr.getReferenceObject().getId()).orElse(null));
				} else if (shPostAttr.getReferenceObject().getObjectType().equals(ShObjectType.FOLDER)) {
					shPostAttr.setReferenceObject(
							shFolderRepository.findById(shPostAttr.getReferenceObject().getId()).orElse(null));
				}
			}
			if (!shPostAttr.getShChildrenRelatorItems().isEmpty()) {
				shPostAttr.getShChildrenRelatorItems().forEach(shRelatorItem -> this
						.loadPostDraftAttribs((Set<ShPostAttr>) shRelatorItem.getShChildrenPostAttrs()));

			}
		}
	}

	/**
	 * Sync with Post Type
	 * 
	 * @param shPost Post Object
	 */
	public void syncWithPostType(ShPostImpl shPost) {
		if (shPost != null) {
			Set<ShPostAttr> shPostAttrs = new HashSet<>();

			Map<String, ShPostAttr> shPostAttrMap = this.postAttrMap(shPost);
			Map<String, ShPostTypeAttr> shPostTypeAttrMap = this.postTypeAttrMap(shPost);

			this.postAttrInPostType(shPostAttrs, shPostAttrMap, shPostTypeAttrMap);
			this.postAttrNotInPostType(shPostAttrs, shPostAttrMap, shPostTypeAttrMap);

			shPost.setShPostAttrs(shPostAttrs);
		}
	}

	/**
	 * Convert ShPostTypeAttr List in Map
	 * 
	 * @param shPost Post Object
	 * @return
	 */
	private Map<String, ShPostTypeAttr> postTypeAttrMap(ShPostImpl shPost) {
		Map<String, ShPostTypeAttr> shPostTypeAttrMap = new HashMap<>();
		if (shPost != null) {
			ShPostType shPostType = shPostTypeRepository.findByName(shPost.getShPostType().getName());
			shPostType.getShPostTypeAttrs()
					.forEach(shPostTypeAttr -> shPostTypeAttrMap.put(shPostTypeAttr.getId(), shPostTypeAttr));
		}
		return shPostTypeAttrMap;
	}

	private Map<String, ShPostTypeAttr> postTypeAttrMap(ShPostTypeAttr shPostTypeAttr) {
		Map<String, ShPostTypeAttr> shPostTypeAttrMap = new HashMap<>();
		Set<ShPostTypeAttr> shPostTypeAttrs = shPostTypeAttrRepository.findByShParentPostTypeAttr(shPostTypeAttr);
		if (shPostTypeAttrs != null) {
			shPostTypeAttrs.forEach(
					shPostTypeAttrChild -> shPostTypeAttrMap.put(shPostTypeAttrChild.getId(), shPostTypeAttrChild));
		}
		return shPostTypeAttrMap;
	}

	/**
	 * Convert ShPostAttr List in Map
	 * 
	 * @param shPost Post Object
	 * @return Post Attribute Map
	 */
	private Map<String, ShPostAttr> postAttrMap(ShPostImpl shPost) {

		Map<String, ShPostAttr> shPostAttrMap = new HashMap<>();
		if (shPost != null) {
			shPost.getShPostAttrs().forEach(shPostAttr -> shPostAttrMap
					.put(((ShPostAttr) shPostAttr).getShPostTypeAttr().getId(), (ShPostAttr) shPostAttr));

		}
		return shPostAttrMap;
	}

	private Map<String, ShPostAttr> postAttrMap(ShRelatorItemImpl shRelatorItem) {

		Map<String, ShPostAttr> shPostAttrMap = new HashMap<>();
		if (shRelatorItem.getShChildrenPostAttrs() != null) {
			shRelatorItem.getShChildrenPostAttrs().forEach(
					shPostAttr -> shPostAttrMap.put(shPostAttr.getShPostTypeAttr().getId(), (ShPostAttr) shPostAttr));
		}
		return shPostAttrMap;
	}

	/**
	 * Add new PostAttrs that not contain into Post
	 * 
	 * @param shPostAttrs       List of PostAttr Object
	 * @param shPostAttrMap     PostAttrMap Object
	 * @param shPostTypeAttrMap PostTypeAttrMap Object
	 */
	private void postAttrNotInPostType(Set<ShPostAttr> shPostAttrs, Map<String, ShPostAttr> shPostAttrMap,
			Map<String, ShPostTypeAttr> shPostTypeAttrMap) {
		for (ShPostTypeAttr shPostTypeAttr : shPostTypeAttrMap.values()) {
			String postTypeAttrId = shPostTypeAttr.getId();
			if (!shPostAttrMap.containsKey(postTypeAttrId)) {
				ShPostAttr shPostAttrSync = new ShPostAttr();
				shPostAttrSync.setShPostTypeAttr(shPostTypeAttr);
				shPostAttrs.add(shPostAttrSync);

			}
		}

		shPostAttrs.forEach(this::postAttrNotInPostTypeNested);
	}

	private void postAttrNotInPostTypeNested(ShPostAttrImpl shPostAttr) {
		if (shPostAttr.getShChildrenRelatorItems() != null) {
			for (ShRelatorItemImpl shRelatorItem : shPostAttr.getShChildrenRelatorItems()) {
				if (shRelatorItem.getShChildrenPostAttrs() != null) {
					Map<String, ShPostTypeAttr> shPostTypeAttrChildMap = this
							.postTypeAttrMap(shPostAttr.getShPostTypeAttr());
					Map<String, ShPostAttr> shPostAttrChildMap = this.postAttrMap(shRelatorItem);

					for (ShPostTypeAttr shPostTypeAttr : shPostTypeAttrChildMap.values()) {
						String postTypeAttrId = shPostTypeAttr.getId();
						if (!shPostAttrChildMap.containsKey(postTypeAttrId)) {
							ShPostAttr shPostAttrSync = new ShPostAttr();
							shPostAttrSync.setShPostTypeAttr(shPostTypeAttr);
							shRelatorItem.getShChildrenPostAttrsNonDraft().add(shPostAttrSync);
						}
					}

					shRelatorItem.getShChildrenPostAttrs().forEach(this::postAttrNotInPostTypeNested);

				}
			}
		}
	}

	/**
	 * Add only PostAttr that contains in Post Type
	 *
	 * @param shPostAttrs       List of PostAttr Object
	 * @param shPostAttrMap     PostAttrMap Object
	 * @param shPostTypeAttrMap PostTypeAttrMap Object
	 */
	private void postAttrInPostType(Set<ShPostAttr> shPostAttrs, Map<String, ShPostAttr> shPostAttrMap,
			Map<String, ShPostTypeAttr> shPostTypeAttrMap) {
		for (ShPostAttr shPostAttr : shPostAttrMap.values()) {
			String postTypeAttrId = shPostAttr.getShPostTypeAttr().getId();
			if (shPostTypeAttrMap.containsKey(postTypeAttrId)) {
				shPostAttrs.add(shPostAttr);
			}
			this.postAttrInPostTypeNested(shPostAttr);
		}
	}

	private void postAttrInPostTypeNested(ShPostAttrImpl shPostAttr) {
		if (shPostAttr.getShChildrenRelatorItems() != null) {
			for (ShRelatorItemImpl shRelatorItem : shPostAttr.getShChildrenRelatorItems()) {
				Set<ShPostAttr> shChildrenPostAttrs = new HashSet<>();
				if (shRelatorItem.getShChildrenPostAttrs() != null) {
					Map<String, ShPostTypeAttr> shPostTypeAttrChildMap = this
							.postTypeAttrMap(shPostAttr.getShPostTypeAttr());
					for (ShPostAttrImpl shPostAttrRelator : shRelatorItem.getShChildrenPostAttrs()) {
						String postTypeAttrRelatorId = shPostAttrRelator.getShPostTypeAttr().getId();
						if (shPostTypeAttrChildMap.containsKey(postTypeAttrRelatorId)) {
							shChildrenPostAttrs.add((ShPostAttr) shPostAttrRelator);
							this.postAttrInPostTypeNested(shPostAttrRelator);
						}
					}
				}
				shRelatorItem.setShChildrenPostAttrs(shChildrenPostAttrs);
			}
		}
	}

	public boolean allowPublish(ShPostType shPostType, Principal principal) {
		if (principal == null || (shPostType != null && StringUtils.isEmpty(shPostType.getWorkflowPublishEntity()))) {
			return true;
		} else {
			ShUser shUser = shUserRepository.findByUsername(principal.getName());
			List<ShUser> shUsers = new ArrayList<>();
			shUsers.add(shUser);

			return isPublished(shPostType, shUsers);
		}

	}

	private boolean isPublished(ShPostType shPostType, List<ShUser> shUsers) {
		return shPostType != null && !StringUtils.isEmpty(shPostType.getWorkflowPublishEntity())
				&& shGroupRepository.countByNameAndShUsersIn(shPostType.getWorkflowPublishEntity(), shUsers) > 0;
	}

}
