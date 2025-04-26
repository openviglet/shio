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
package com.viglet.shio.api.post;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.ShPostDraft;
import com.viglet.shio.persistence.model.post.ShPostDraftAttr;
import com.viglet.shio.persistence.model.post.impl.ShPostAttrImpl;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.reference.ShReference;
import com.viglet.shio.persistence.model.reference.ShReferenceDraft;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.exchange.post.ShPostExport;
import com.viglet.shio.object.ShObjectPublishStatus;
import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.persistence.repository.post.ShPostAttrRepository;
import com.viglet.shio.persistence.repository.post.ShPostDraftAttrRepository;
import com.viglet.shio.persistence.repository.post.ShPostDraftRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.reference.ShReferenceDraftRepository;
import com.viglet.shio.persistence.repository.reference.ShReferenceRepository;
import com.viglet.shio.persistence.repository.workflow.ShWorkflowTaskRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.turing.ShTuringIntegration;
import com.viglet.shio.url.ShURLFormatter;
import com.viglet.shio.utils.ShHistoryUtils;
import com.viglet.shio.utils.ShObjectUtils;
import com.viglet.shio.utils.ShPostUtils;
import com.viglet.shio.utils.ShStaticFileUtils;
import com.viglet.shio.website.cache.component.ShCacheObject;
import com.viglet.shio.widget.ShSystemWidget;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Post API.
 *
 * @author Alexandre Oliveira
 * @since 0.3.0
 */
@RestController
@RequestMapping("/api/v2/post")
@Tag(name = "Post", description = "Post API")
public class ShPostAPI {

	private static final Log logger = LogFactory.getLog(ShPostAPI.class);

	@Autowired
	private ShPostRepository shPostRepository;
	@Autowired
	private ShPostDraftRepository shPostDraftRepository;
	@Autowired
	private ShPostAttrRepository shPostAttrRepository;
	@Autowired
	private ShPostDraftAttrRepository shPostDraftAttrRepository;
	@Autowired
	private ShUserRepository shUserRepository;
	@Autowired
	private ShStaticFileUtils shStaticFileUtils;
	@Autowired
	private ShReferenceRepository shReferenceRepository;
	@Autowired
	private ShReferenceDraftRepository shReferenceDraftRepository;
	@Autowired
	private ShPostTypeRepository shPostTypeRepository;
	@Autowired
	private ShObjectRepository shObjectRepository;
	@Autowired
	private ShPostUtils shPostUtils;
	@Autowired
	private ShTuringIntegration shTuringIntegration;
	@Autowired
	private ShCacheObject shCacheObject;
	@Autowired
	private ShWorkflowTaskRepository shWorkflowTaskRepository;
	@Autowired
	private ShObjectUtils shObjectUtils;
	@Autowired
	private ShHistoryUtils shHistoryUtils;
	@Autowired
	private ShPostExport shPostExport;

	private final SimpleDateFormat dt = new SimpleDateFormat("dd/MM/yyyyy");

	@GetMapping
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public List<ShPost> shPostList() {
		return shPostRepository.findAll();
	}

	@GetMapping("/post-type/{postTypeName}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public List<ShPost> shPostListByPostType(@PathVariable String postTypeName) {
		ShPostType shPostType = shPostTypeRepository.findByName(postTypeName);
		return shPostRepository.findByShPostType(shPostType);
	}

	/**
	 * Post Edit API
	 * 
	 * @param id        Post Id
	 * @param principal Logged User
	 * @return ShPost
	 */
	@GetMapping("/{id}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ResponseEntity<ShPost> shPostEdit(@PathVariable String id, Principal principal) {
		if (shObjectUtils.canAccess(principal, id)) {
			ShPost shPost = shPostUtils.loadLazyPost(id, false);
			shPostUtils.syncWithPostType(shPost);

			return new ResponseEntity<>(shPost, HttpStatus.OK);
		}
		return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
	}

	@GetMapping("/attr/model")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShPostAttr shPostAttrModel() {
		return new ShPostAttr();
	}

	@Transactional
	@PutMapping("/{id}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ResponseEntity<ShPost> shPostUpdate(@PathVariable String id, @RequestBody ShPost shPost,
			Principal principal) {
		if (shObjectUtils.canAccess(principal, id)) {
			shCacheObject.deleteCache(id);

			this.postSave(shPost);

			shHistoryUtils.commit(shPost, principal, ShHistoryUtils.UPDATE);

			return this.shPostEdit(shPost.getId(), principal);
		}
		return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

	}

	@Transactional
	@PostMapping
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	@CacheEvict(value = { "page", "pageLayout", "region" }, allEntries = true)
	public ResponseEntity<ShPost> shPostAdd(@RequestBody ShPost shPost, Principal principal) {
		if (shObjectUtils.canAccess(principal, shPost.getShFolder().getId())) {
			if (shPost.getShPostType().getName().equals(ShSystemPostType.FILE)) {
				shPost.setPublishStatus("PUBLISH");
				shPost.setPublished(true);
			}

			Optional<ShObject> shObject = shObjectRepository.findById(shPost.getShFolder().getId());
			if (shObject.isPresent()) {
				List<ShObject> shObjects = new ArrayList<>();
				shObjects.add(shObject.get());

				shPost.setShGroups(new HashSet<>(shObject.get().getShGroups()));
				shPost.setShUsers(new HashSet<>(shObject.get().getShUsers()));

				this.postSave(shPost);

				shHistoryUtils.commit(shPost, principal, ShHistoryUtils.CREATE);

				return this.shPostEdit(shPost.getId(), principal);
			} else {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
			}
		}
		return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
	}

	@Transactional
	@DeleteMapping("/{id}")
	public ResponseEntity<Boolean> shPostDelete(@PathVariable String id, Principal principal) {
		if (shObjectUtils.canAccess(principal, id)) {
			shCacheObject.deleteCache(id);

			Optional<ShPost> shPostOptional = shPostRepository.findById(id);

			if (shPostOptional.isPresent()) {
				ShPost shPost = shPostOptional.get();
				Set<ShPostAttr> shPostAttrs = shPostAttrRepository.findByShPost(shPost);

				shTuringIntegration.deindexObject(shPost);

				this.deleteStaticFiles(shPost);

				shPostAttrRepository.deleteAllInBatch(shPostAttrs);

				shReferenceRepository.deleteAllInBatch(shReferenceRepository.findByShObjectFrom(shPost));

				shReferenceRepository.deleteAllInBatch(shReferenceRepository.findByShObjectTo(shPost));

				shReferenceDraftRepository.deleteAllInBatch(shReferenceDraftRepository.findByShObjectTo(shPost));

				shHistoryUtils.commit(shPost, principal, ShHistoryUtils.DELETE);

				shPostRepository.delete(id);

				return new ResponseEntity<>(true, HttpStatus.OK);
			}
		}
		return new ResponseEntity<>(false, HttpStatus.FORBIDDEN);
	}

	@ResponseBody
	@GetMapping(value = "/{id}/export", produces = "application/zip")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ResponseEntity<StreamingResponseBody> shPostExport(@PathVariable String id, Principal principal,
			HttpServletResponse response) {
		if (shObjectUtils.canAccess(principal, id)) {
			return new ResponseEntity<>(shPostExport.exportObject(response, id), HttpStatus.OK);
		} else {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
	}

	private void deleteStaticFiles(ShPost shPost) {
		Set<ShPostAttr> shPostAttrs = shPostAttrRepository.findByShPost(shPost);
		if (shPost.getShPostType().getName().equals(ShSystemPostType.FILE) && !shPostAttrs.isEmpty()) {
			File file = shStaticFileUtils.filePath(shPost.getShFolder(), shPostAttrs.iterator().next().getStrValue());
			if (file != null && file.exists()) {
				try {
					Files.delete(file.toPath());
				} catch (IOException e) {
					logger.error("shPostDelete: ", e);
				}
			}
		}
	}

	public void postSave(ShPost shPost) {
		// Get PostAttrs before save, because JPA Lazy
		@SuppressWarnings("unchecked")
		Set<ShPostAttr> shPostAttrs = (Set<ShPostAttr>) shPost.getShPostAttrs();

		StringBuilder title = new StringBuilder();
		StringBuilder summary = new StringBuilder();

		List<ShPostAttr> shPostAttrsByOrdinal = new ArrayList<>(shPostAttrs);

		Collections.sort(shPostAttrsByOrdinal, (ShPostAttr o1, ShPostAttr o2) -> o1.getShPostTypeAttr().getOrdinal()
				- o2.getShPostTypeAttr().getOrdinal());
		this.titleAndSummary(title, summary, shPostAttrsByOrdinal);

		shPost.setDate(new Date());
		shPost.setTitle(title.toString());
		shPost.setSummary(summary.toString());
		shPost.setShSite(shPostUtils.getSite(shPost));
		shPost.setModifiedDate(new Date());

		if (shPost.getPublicationDate() == null)
			shPost.setFurl(ShURLFormatter.format(title.toString()));

		shPostAttrs.forEach(shPostAttr -> {
			shPostAttr.setShPost(shPost);
			this.updateRelatorParent(shPostAttr, shPost);

		});

		if (shPost.getPublishStatus() != null) {
			if (shPost.getPublishStatus().equals(ShObjectPublishStatus.PUBLISH))
				this.postPublishSave(shPost);
			else if (shPost.getPublishStatus().equals(ShObjectPublishStatus.UNPUBLISH))
				this.postUnpublishSave(shPost);
			else if (shPost.getPublishStatus().equals(ShObjectPublishStatus.DRAFT))
				this.postDraftSave(shPost);

		} else {
			if (shPost.isPublished())
				this.postDraftSave(shPost);
			else
				this.postUnpublishSave(shPost);

		}

		this.lastPostTypeUsed(shPost);

	}

	private void lastPostTypeUsed(ShPost shPost) {
		ShUser shUser = shUserRepository.findByUsername("admin");
		shUser.setLastPostType(String.valueOf(shPost.getShPostType().getId()));
		shUserRepository.saveAndFlush(shUser);
	}

	private void titleAndSummary(StringBuilder title, StringBuilder summary, List<ShPostAttr> shPostAttrsByOrdinal) {
		shPostAttrsByOrdinal.forEach(shPostAttr -> {
			this.setTitle(title, shPostAttr);
			this.setSummary(summary, shPostAttr);
			if (shPostAttr != null)
				shPostAttr.setReferenceObject(null);
		});
	}

	private void setSummary(StringBuilder summary, ShPostAttr shPostAttr) {
		if (shPostAttr.getShPostTypeAttr().getIsSummary() == 1) {
			if (!StringUtils.isEmpty(summary.toString()))
				summary.append(", ");
			if (shPostAttr.getShPostTypeAttr().getShWidget().getName().equals(ShSystemWidget.DATE)) {
				summary.append(dt.format(shPostAttr.getDateValue()));
			} else {
				summary.append(StringUtils.abbreviate(shPostAttr.getStrValue(), 255));
			}
		}
	}

	private void setTitle(StringBuilder title, ShPostAttr shPostAttr) {
		if (shPostAttr.getShPostTypeAttr().getIsTitle() == 1) {
			if (!StringUtils.isEmpty(title.toString()))
				title.append(", ");
			if (shPostAttr.getShPostTypeAttr().getShWidget().getName().equals(ShSystemWidget.DATE)) {
				title.append(dt.format(shPostAttr.getDateValue()));
			} else {
				title.append(StringUtils.abbreviate(shPostAttr.getStrValue(), 255));
			}
		}
	}

	public void postPublishSave(ShPost shPost) {
		shPost.setPublicationDate(new Date());
		shPost.setPublished(true);

		shPostRepository.saveAndFlush(shPost);

		this.postReferenceSave(shPost);

		this.postDraftDelete(shPost.getId());

		shWorkflowTaskRepository.deleteAllInBatch(shWorkflowTaskRepository.findByShObject(shPost));

		shTuringIntegration.indexObject(shPost);

	}

	public void postUnpublishSave(ShPost shPost) {
		shPost.setPublished(false);

		shPostRepository.saveAndFlush(shPost);

		this.postReferenceSave(shPost);

		this.postDraftDelete(shPost.getId());

		shWorkflowTaskRepository.deleteAllInBatch(shWorkflowTaskRepository.findByShObject(shPost));

		shTuringIntegration.deindexObject(shPost);
	}

	private void postDraftDelete(String id) {
		Optional<ShPostDraft> shPostOptional = shPostDraftRepository.findById(id);

		if (shPostOptional.isPresent()) {
			ShPostDraft shPost = shPostOptional.get();

			Set<ShPostDraftAttr> shPostAttrs = shPostDraftAttrRepository.findByShPost(shPost);

			shPostDraftAttrRepository.deleteAllInBatch(shPostAttrs);

			shReferenceDraftRepository.deleteAllInBatch(shReferenceDraftRepository.findByShObjectFrom(shPost));

			shPostDraftRepository.delete(id);
		}
	}

	public void postDraftSave(ShPost shPost) {
		if (shPost.getId() != null && shPost.isPublished()) {
			shPostRepository.findById(shPost.getId()).ifPresent(shPostEdit -> {
				ShPostDraft shPostDraft = convertPost2Draft(shPost);
				@SuppressWarnings("unchecked")
				Set<ShPostDraftAttr> shPostAttrs = (Set<ShPostDraftAttr>) shPostDraft.getShPostAttrs();
				shPostAttrs.forEach(shPostAttr -> {
					shPostAttr.setShPost(shPostDraft);
					this.updateRelatorParent(shPostAttr, shPostDraft);
				});

				shPostDraftRepository.saveAndFlush(shPostDraft);
				this.postReferenceSave(shPostDraft);
				shPostEdit.setPublishStatus("DRAFT");
				shPostRepository.saveAndFlush(shPostEdit);

			});
		} else {
			this.postUnpublishSave(shPost);
		}
	}

	private ShPostDraft convertPost2Draft(ShPost shPost) {
		ShPostDraft shPostDraft = null;
		ObjectMapper mapper = new ObjectMapper();
		try {
			String jsonInString = mapper.writeValueAsString(shPost)
					.replace("\"@type\":\"ShPost\"", "\"@type\":\"ShPostDraft\"")
					.replace("\"@type\":\"ShPostAttr\"", "\"@type\":\"ShPostDraftAttr\"");
			shPostDraft = mapper.readValue(jsonInString, ShPostDraft.class);

		} catch (JsonProcessingException e) {
			logger.error("convertPost2Draft JsonProcessingException:", e);
		}

		return shPostDraft;
	}

	private void updateRelatorParent(ShPostAttrImpl shPostAttr, ShPostImpl shPost) {
		shPostAttr.getShChildrenRelatorItems().forEach(shRelatorItem -> {
			shRelatorItem.setShParentPostAttr(shPostAttr);
			shRelatorItem.getShChildrenPostAttrs().forEach(shChildrenPostAttr -> {
				shChildrenPostAttr.setShParentRelatorItem(shRelatorItem);
				this.updateRelatorParent(shChildrenPostAttr, shPost);
			});
		});
	}

	private void postReferenceSave(ShPostImpl shPostImpl) {

		// Delete all old references to recreate in next step
		if (shPostImpl instanceof ShPost shPost) {
			List<ShReference> shOldReferences = shReferenceRepository.findByShObjectFrom(shPost);
			shReferenceRepository.deleteAllInBatch(shOldReferences);

			shPostImpl.getShPostAttrs().forEach(shPostAttr -> {
				shPostUtils.referencedObject(shPostAttr, shPostImpl);
				this.nestedReferenceSave(shPostAttr, shPostImpl);
			});
		} else if (shPostImpl instanceof ShPostDraft shPostDraft) {
			List<ShReferenceDraft> shOldReferences = shReferenceDraftRepository.findByShObjectFrom(shPostDraft);
			shReferenceDraftRepository.deleteAllInBatch(shOldReferences);

			shPostImpl.getShPostAttrs().forEach(shPostAttr -> {
				shPostUtils.referencedObjectDraft(shPostAttr, shPostImpl);
				this.nestedReferenceSaveDraft(shPostAttr, shPostImpl);
			});

		}
		shPostImpl.getShPostAttrs().forEach(shPostAttr -> shPostUtils.updateRelatorInfo(shPostAttr, shPostImpl));

		if (shPostImpl instanceof ShPost shPost) {
			shPostRepository.saveAndFlush(shPost);
		} else if (shPostImpl instanceof ShPostDraft shPostDraft) {
			shPostDraftRepository.saveAndFlush(shPostDraft);
		}
	}

	private void nestedReferenceSave(ShPostAttrImpl shPostAttr, ShPostImpl shPost) {
		shPostAttr.getShChildrenRelatorItems()
				.forEach(shRelatorItem -> shRelatorItem.getShChildrenPostAttrs().forEach(shChildrenPostAttr -> {
					shPostUtils.referencedObject(shChildrenPostAttr, shPost);
					this.nestedReferenceSave(shChildrenPostAttr, shPost);
				}));
	}

	private void nestedReferenceSaveDraft(ShPostAttrImpl shPostAttr, ShPostImpl shPost) {
		shPostAttr.getShChildrenRelatorItems()
				.forEach(shRelatorItem -> shRelatorItem.getShChildrenPostAttrs().forEach(shChildrenPostAttr -> {
					shPostUtils.referencedObjectDraft(shChildrenPostAttr, shPost);
					this.nestedReferenceSaveDraft(shChildrenPostAttr, shPost);
				}));
	}
}
