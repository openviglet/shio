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
package com.viglet.shio.persistence.repository.widget;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.widget.ShWidget;

/**
 * @author Alexandre Oliveira
 */
@Repository
public interface ShWidgetRepository extends JpaRepository<ShWidget, String> {

	List<ShWidget> findAll();

	Optional<ShWidget> findById(String id);
	
	ShWidget findByName(String name);

	/**
	 * First widget with the given name. The seeded catalog is unique by name, but
	 * resolving by name (SH16 post-type push) must not blow up if duplicates exist
	 * — {@link #findByName} throws {@code IncorrectResultSizeDataAccessException}
	 * when more than one row matches.
	 */
	ShWidget findFirstByName(String name);

	@SuppressWarnings("unchecked")
	ShWidget save(ShWidget shWidget);

	@Modifying
	@Query("delete from ShWidget p where p.id = ?1")
	void delete(String id);
}
