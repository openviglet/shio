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
package com.viglet.shio.api.folder;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
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

import com.fasterxml.jackson.annotation.JsonView;
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.exchange.folder.ShFolderExport;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.spreedsheet.ShSpreadsheet;
import com.viglet.shio.turing.ShTuringIntegration;
import com.viglet.shio.url.ShURLFormatter;
import com.viglet.shio.utils.ShFolderUtils;
import com.viglet.shio.utils.ShHistoryUtils;
import com.viglet.shio.utils.ShObjectUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/folder")
@Tag( name = "Folder", description = "Search API")
public class ShFolderAPI {
	private static final Logger logger = LogManager.getLogger(ShFolderAPI.class);
	@Autowired
	private ShFolderRepository shFolderRepository;
	@Autowired
	private ShFolderUtils shFolderUtils;
	@Autowired
	private ShObjectRepository shObjectRepository;
	@Autowired
	private ShObjectUtils shObjectUtils;
	@Autowired
	private ShTuringIntegration shTuringIntegration;
	@Autowired
	private ShSpreadsheet shSpreadsheet;
	@Autowired
	private ShHistoryUtils shHistoryUtils;
	@Autowired
	private ShFolderExport shFolderExport;
	
	@Operation(summary = "Folder list")
	@GetMapping
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public List<ShFolder> shFolderList() {
		return shFolderRepository.findAll();
	}

	@Operation(summary = "Show a folder")
	@GetMapping("/{id}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ResponseEntity<ShFolder> shFolderGet(@PathVariable String id, Principal principal) {
		if (shObjectUtils.canAccess(principal, id)) {
			return new ResponseEntity<>(shFolderRepository.findById(id).orElse(null), HttpStatus.OK);
		}
		return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
	}

	@Operation(summary = "Update a folder")
	@PutMapping("/{id}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ResponseEntity<ShFolder> shFolderUpdate(@PathVariable String id, @RequestBody ShFolder shFolder,
			Principal principal) {
		if (shObjectUtils.canAccess(principal, id)) {
			Optional<ShFolder> shFolderOptional = shFolderRepository.findById(id);
			if (shFolderOptional.isPresent()) {
				ShFolder shFolderEdit = shFolderOptional.get();

				shFolderEdit.setDate(new Date());
				shFolderEdit.setName(shFolder.getName());
				shFolderEdit.setParentFolder(shFolder.getParentFolder());
				shFolderEdit.setShSite(shFolder.getShSite());
				shFolderEdit.setFurl(ShURLFormatter.format(shFolderEdit.getName()));
				shFolderRepository.saveAndFlush(shFolderEdit);

				shTuringIntegration.indexObject(shFolderEdit);

				shHistoryUtils.commit(shFolder, principal, ShHistoryUtils.UPDATE);

				return new ResponseEntity<>(shFolderEdit, HttpStatus.OK);
			}
		}
		return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

	}

	@Transactional
	@Operation(summary = "Delete a folder")
	@DeleteMapping("/{id}")
	public ResponseEntity<Boolean> shFolderDelete(@PathVariable String id, Principal principal) {
		if (shObjectUtils.canAccess(principal, id)) {
			shFolderRepository.findById(id).ifPresent(shFolder -> {
				try {
					shFolderUtils.deleteFolder(shFolder);
					shHistoryUtils.commit(shFolder, principal, ShHistoryUtils.DELETE);
				} catch (IOException e) {
					logger.error("FolderDeleteException", e);
				}
			});

			return new ResponseEntity<>(true, HttpStatus.OK);
		}
		return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
	}

	@Operation(summary = "Create a folder")
	@PostMapping
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ResponseEntity<ShFolder> shFolderAdd(@RequestBody ShFolder shFolder, Principal principal) {
		ShObject shParentObject = null;
		if (shFolder != null) {
			if (shFolder.getRootFolder() == 1 && shFolder.getShSite() != null && shFolder.getShSite().getId() != null)
				shParentObject = shFolder.getShSite();
			else if (shFolder.getParentFolder() != null && shFolder.getParentFolder().getId() != null)
				shParentObject = shFolder.getParentFolder();

			if (shParentObject != null && shObjectUtils.canAccess(principal, shParentObject.getId())) {
				List<ShObject> shObjects = new ArrayList<>();
				shObjects.add(shParentObject);

				shObjectRepository.findById(shParentObject.getId()).ifPresent(shObject -> {
					shFolder.setDate(new Date());
					shFolder.setFurl(ShURLFormatter.format(shFolder.getName()));
					shFolder.setShGroups(new HashSet<>(shObject.getShGroups()));
					shFolder.setShUsers(new HashSet<>(shObject.getShUsers()));
					shFolderRepository.saveAndFlush(shFolder);

					shTuringIntegration.indexObject(shFolder);

					shHistoryUtils.commit(shFolder, principal, ShHistoryUtils.CREATE);

				});
				return new ResponseEntity<>(shFolder, HttpStatus.OK);

			}
		}
		return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

	}

	@Operation(summary = "Create a folder from Parent Object")
	@PostMapping("/object/{objectId}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ResponseEntity<ShFolder> shFolderAddFromParentObject(@RequestBody ShFolder shFolder,
			@PathVariable String objectId, Principal principal) {
		if (shObjectUtils.canAccess(principal, objectId)) {

			shObjectRepository.findById(objectId).ifPresent(shObject -> {
				List<ShObject> shObjects = new ArrayList<>();
				shObjects.add(shObject);

				ShFolder shNewFolder = new ShFolder();
				shNewFolder.setDate(new Date());
				shNewFolder.setName(shFolder.getName());
				shNewFolder.setShGroups(new HashSet<>(shObject.getShGroups()));
				shNewFolder.setShUsers(new HashSet<>(shObject.getShUsers()));
				shNewFolder.setFurl(ShURLFormatter.format(shNewFolder.getName()));

				ShObject shParentObject = shObjectRepository.findById(objectId).orElse(null);
				if (shParentObject instanceof ShFolder shParentFolder) {	
					shNewFolder.setParentFolder(shParentFolder);
					shNewFolder.setRootFolder((byte) 0);
					shNewFolder.setShSite(null);

				} else if (shParentObject instanceof ShSite shSite) {
					shNewFolder.setParentFolder(null);
					shNewFolder.setRootFolder((byte) 1);
					shNewFolder.setShSite(shSite);
				}

				shFolderRepository.save(shNewFolder);

				shHistoryUtils.commit(shNewFolder, principal, ShHistoryUtils.CREATE);
			});

			return new ResponseEntity<>(shFolder, HttpStatus.OK);
		}
		return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
	}

	@Operation(summary = "Folder path")
	@GetMapping("/{id}/path")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShFolderPath shFolderPath(@PathVariable String id) {
		return shObjectUtils.objectPath(id);
	}

	@Operation(summary = "Folder model")
	@GetMapping("/model")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShFolder shFolderStructure() {
		return new ShFolder();

	}

	@Operation(summary = "Export SpreadSheet from Folder")
	@GetMapping("/{id}/spreadsheet")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public void shFolderSpreadSheet(@PathVariable String id, HttpServletResponse response) {
		ShFolder shFolder = shFolderRepository.findById(id).orElse(null);
		if (shFolder != null)
			shSpreadsheet.generate(shFolder, response);
	}
	@ResponseBody
	@GetMapping(value = "/{id}/export", produces = "application/zip")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ResponseEntity<StreamingResponseBody> shPostExport(@PathVariable String id, Principal principal,
			HttpServletResponse response) {
		if (shObjectUtils.canAccess(principal, id)) {
			return new ResponseEntity<>(shFolderExport.exportObject(response, id), HttpStatus.OK);
		} else {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}	
	}
}
