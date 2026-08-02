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
package com.viglet.shio.api.widget.dto;

/**
 * Request payload for creating/updating a {@link com.viglet.shio.persistence.model.widget.ShWidget}.
 * Component names mirror the entity's JSON field names so existing payloads bind unchanged.
 *
 * <p>SH285 removed {@code className} and {@code implementationCode} from it. They were bindable and
 * <em>storable</em> and could not affect anything: both named files this repository does not contain
 * (a widget class package that holds only constants, and a {@code template/widget/**} directory that
 * is gone with the AngularJS UI). An older payload carrying them still binds — an unknown property
 * is ignored, so the surface narrows without breaking a caller that has not noticed yet.</p>
 *
 * @author Alexandre Oliveira
 */
public record ShWidgetRequest(String name, String type, String description) {
}
