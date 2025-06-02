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
package com.viglet.shio.api.history;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.persistence.model.site.ShSite;
import com.fasterxml.jackson.annotation.JsonView;
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.repository.history.ShHistoryPageableRepository;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/history")
@Tag( name = "History", description = "History API")
public class ShHistoryAPI {

	private final ShObjectRepository shObjectRepository;
	private final ShHistoryRepository shHistoryRepository;
	private final ShHistoryPageableRepository shHistoryPageableRepository;

	@Autowired
	public ShHistoryAPI(ShObjectRepository shObjectRepository, ShHistoryRepository shHistoryRepository,
						ShHistoryPageableRepository shHistoryPageableRepository) {
		this.shObjectRepository = shObjectRepository;
		this.shHistoryRepository = shHistoryRepository;
		this.shHistoryPageableRepository = shHistoryPageableRepository;
	}

	@GetMapping
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public List<ShHistory> shHistoryList() {
		return shHistoryRepository.findAll();
	}

	@GetMapping("/object/{globalId}/{page}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public List<ShHistory> shHistoryByObject(@PathVariable String globalId, @PathVariable int page) {
		Pageable pageable = PageRequest.of(page, 50);
		if (shObjectRepository.findById(globalId).isPresent()) {
			ShObjectImpl shObject = shObjectRepository.findById(globalId).orElse(null);
			if (shObject != null) {
				if (shObject instanceof ShSite shSite) {
					return shHistoryPageableRepository.findByShSiteOrderByDateDesc(shSite.getId(), pageable);
				} else {
					return shHistoryPageableRepository.findByShObjectOrderByDateDesc(shObject.getId(), pageable);
				}
			}
		}
		return Collections.emptyList();
	}

	@GetMapping("/object/{globalId}/count")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public int shHistoryByObjectCount(@PathVariable String globalId) {
		if (shObjectRepository.findById(globalId).isPresent()) {
			ShObjectImpl shObject = shObjectRepository.findById(globalId).orElse(null);
			if (shObject != null) {
				if (shObject instanceof ShSite shSite) {
					return shHistoryRepository.countByShSite(shSite.getId());
				} else {
					return shHistoryRepository.countByShObject(shObject.getId());
				}
			}
		}
		return 0;
	}
}
