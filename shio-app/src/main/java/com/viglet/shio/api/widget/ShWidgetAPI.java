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
package com.viglet.shio.api.widget;

import java.util.List;
import java.util.Optional;

import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/widget")
@Tag( name = "Widget", description = "Widget API")
public class ShWidgetAPI {

	@Autowired
	private ShWidgetRepository shWidgetRepository;

	@GetMapping
	public List<ShWidget> shWidgetList() {
		return shWidgetRepository.findAll();
	}

	@GetMapping("/{id}")
	public ShWidget shWidgetEdit(@PathVariable String id) {
		Optional<ShWidget> shWidget = shWidgetRepository.findById(id);
		if (shWidget.isPresent()) {
			return shWidget.get();
		}
		return null;
	}

	@PutMapping("/{id}")
	public ShWidget shWidgetUpdate(@PathVariable String id, @RequestBody ShWidget shWidget) {
		Optional<ShWidget> shWidgetOptional = shWidgetRepository.findById(id);
		if (shWidgetOptional.isPresent()) {
			ShWidget shWidgetEdit = shWidgetOptional.get();
			shWidgetEdit.setName(shWidget.getName());
			shWidgetEdit.setType(shWidget.getType());
			shWidgetEdit.setClassName(shWidget.getClassName());
			shWidgetEdit.setDescription(shWidget.getDescription());
			shWidgetEdit.setImplementationCode(shWidget.getImplementationCode());
			shWidgetRepository.save(shWidgetEdit);
			return shWidgetEdit;
		}
		return null;

	}

	@Transactional
	@DeleteMapping("/{id}")
	public boolean shWidgetDelete(@PathVariable String id){
		shWidgetRepository.delete(id);
		return true;
	}

	@PostMapping
	public ShWidget shWidgetAdd(@RequestBody ShWidget shWidget) {
		shWidgetRepository.save(shWidget);
		return shWidget;

	}

}
