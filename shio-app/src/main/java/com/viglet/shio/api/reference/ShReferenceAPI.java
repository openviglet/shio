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
package com.viglet.shio.api.reference;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.reference.ShReference;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.persistence.repository.post.ShPostAttrRepository;
import com.viglet.shio.persistence.repository.reference.ShReferenceRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/reference")
@Tag( name = "Reference", description = "Reference API")
public class ShReferenceAPI {

	private final ShReferenceRepository shReferenceRepository;
	private final ShObjectRepository shObjectRepository;
	private final ShPostAttrRepository shPostAttrRepository;

	@Autowired
	public ShReferenceAPI(ShReferenceRepository shReferenceRepository, ShObjectRepository shObjectRepository,
						  ShPostAttrRepository shPostAttrRepository) {
		this.shReferenceRepository = shReferenceRepository;
		this.shObjectRepository = shObjectRepository;
		this.shPostAttrRepository = shPostAttrRepository;
	}

	@GetMapping
	@JsonView({ ShJsonView.ShJsonViewReference.class })
	public List<ShReference> shReferenceList() {
		return shReferenceRepository.findAll();
	}

	@GetMapping("/from/{fromId}")
	@JsonView({ ShJsonView.ShJsonViewReference.class })
	public List<ShReference> shReferenceFrom(@PathVariable String fromId) {
		Optional<ShObject> shObject = shObjectRepository.findById(fromId);
		if (shObject.isPresent())
			return shReferenceRepository.findByShObjectFrom(shObject.get());
		return Collections.emptyList();
	}

	@GetMapping("/to/{toId}")
	@JsonView({ ShJsonView.ShJsonViewReference.class })
	public List<ShReference> shReferenceTo(@PathVariable String toId) {
		Optional<ShObject> shObject = shObjectRepository.findById(toId);
		if (shObject.isPresent())
			return shReferenceRepository.findByShObjectTo(shObject.get());
		return Collections.emptyList();
	}

	@PostMapping("/to/{toId}/replace/{otherId}")
	@JsonView({ ShJsonView.ShJsonViewReference.class })
	public List<ShReference> shReferenceToReplace(@PathVariable String toId, @PathVariable String otherId) {
		Optional<ShObject> shObject = shObjectRepository.findById(toId);
		Optional<ShObject> shObjectOther = shObjectRepository.findById(otherId);
		if (shObject.isPresent() && shObjectOther.isPresent()) {
			List<ShReference> shReferences = shReferenceRepository.findByShObjectTo(shObject.get());
			shReferences.forEach(shReference -> {
				if (shReference != null) {
					this.setReferenceToPostAttr(shObject.get(), shObjectOther.get(), shReference);
					shReference.setShObjectTo(shObjectOther.get());
					shReferenceRepository.saveAndFlush(shReference);
				}
			});

			return shReferenceRepository.findByShObjectTo(shObject.get());
		} else {
			return Collections.emptyList();
		}
	}

	private void setReferenceToPostAttr(ShObjectImpl shObject, ShObject shObjectOther, ShReference shReference) {
		if (shReference.getShObjectFrom() instanceof ShPost) {
			ShPostImpl shPost = (ShPostImpl) shReference.getShObjectFrom();
			shPost.getShPostAttrsNonDraft().forEach(shPostAttr -> {
				ShObject shObjectReference = shPostAttr.getReferenceObject();
				if (shObject.getId() != null && shObjectReference.getId().equals(shObject.getId())) {
					shPostAttr.setReferenceObject(shObjectOther);
					shPostAttrRepository.saveAndFlush(shPostAttr);
				}
			});
		}
	}
}
