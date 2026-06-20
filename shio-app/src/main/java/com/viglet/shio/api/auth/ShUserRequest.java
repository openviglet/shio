/*
 * Copyright (C) 2016-2026 the original author or authors.
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
package com.viglet.shio.api.auth;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.viglet.shio.persistence.model.auth.ShGroup;
import com.viglet.shio.persistence.model.auth.ShUser;

/**
 * Request DTO for the user write endpoints ({@code POST}/{@code PUT}
 * {@code /api/v2/user/{username}}), introduced to stop binding the
 * persistent {@link ShUser} entity directly to {@code @RequestBody}
 * (SonarQube {@code java:S4684}).
 *
 * <p>Mirrors exactly the fields the controller persists. The create path
 * ({@code shUserAdd}) saved the whole entity verbatim, so this DTO carries
 * every writable scalar field plus the {@code shGroups} association in the
 * same JSON shape the console/UI sends (a list of group objects keyed by
 * {@code id}, with {@code name}/{@code description} preserved). The update
 * path ({@code shUserUpdate}) reads only {@code firstName}, {@code lastName},
 * {@code email} and {@code shGroups}.</p>
 *
 * @author Alexandre Oliveira
 */
public record ShUserRequest(
        String username,
        String firstName,
        String lastName,
        String email,
        String confirmEmail,
        String password,
        String realm,
        String recoverPassword,
        String lastPostType,
        Integer enabled,
        List<ShGroupRef> shGroups) {

    /** Group reference in the JSON shape posted by the console/UI. */
    public record ShGroupRef(String id, String name, String description) {
        ShGroup toShGroup() {
            ShGroup group = new ShGroup();
            group.setId(id);
            group.setName(name);
            group.setDescription(description);
            return group;
        }
    }

    /** Build a brand-new {@link ShUser} from the payload (create path). */
    ShUser toNewShUser() {
        ShUser shUser = new ShUser();
        shUser.setUsername(username);
        shUser.setFirstName(firstName);
        shUser.setLastName(lastName);
        shUser.setEmail(email);
        shUser.setConfirmEmail(confirmEmail);
        shUser.setPassword(password);
        shUser.setRealm(realm);
        shUser.setRecoverPassword(recoverPassword);
        shUser.setLastPostType(lastPostType);
        if (enabled != null) {
            shUser.setEnabled(enabled);
        }
        shUser.setShGroups(toShGroups());
        return shUser;
    }

    /** Resolve the posted group references into {@link ShGroup} instances. */
    Set<ShGroup> toShGroups() {
        if (shGroups == null) {
            return new HashSet<>();
        }
        Set<ShGroup> groups = new HashSet<>();
        for (ShGroupRef ref : shGroups) {
            if (ref != null) {
                groups.add(ref.toShGroup());
            }
        }
        return groups;
    }
}
