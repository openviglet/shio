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

	/**
	 * First widget with the given name — <b>the only way to resolve one</b>.
	 *
	 * <p>The seeded catalog is <em>meant</em> to be unique by name, and nothing enforces it: no
	 * unique index, and the catalog is a shared global row set that an import, a
	 * {@code POST /api/v2/widget} or a cascading write can add to. So "unique by name" is an
	 * assumption, and a reader that dies when it is false is a reader that dies on live data.</p>
	 *
	 * <p>There <b>was</b> a derived {@code findByName} beside this, which throws
	 * {@code IncorrectResultSizeDataAccessException} on a second row. It is deleted rather than
	 * documented (SH753): three of its four call sites had already been moved here, each with a
	 * comment explaining why, and the fourth — {@code ShStaticFileService}, resolving the File
	 * post type on every upload — took the whole upload down with it when a catalog with five rows
	 * named {@code Text} turned up. A method nobody may call is better said by the compiler than by
	 * a comment, so the trap is gone rather than signposted.</p>
	 *
	 * <p>Duplicates are not thereby made harmless, only non-fatal: {@code ShWidgetOnStartup}
	 * reports them at boot, so the state is visible rather than silently resolved to whichever row
	 * came first.</p>
	 */
	ShWidget findFirstByName(String name);

	@SuppressWarnings("unchecked")
	ShWidget save(ShWidget shWidget);

	@Modifying
	@Query("delete from ShWidget p where p.id = ?1")
	void delete(String id);
}
