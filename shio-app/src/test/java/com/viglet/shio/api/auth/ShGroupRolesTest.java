/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.shio.persistence.model.auth.ShGroup;
import com.viglet.shio.persistence.model.auth.ShRole;
import com.viglet.shio.persistence.repository.auth.ShGroupRepository;
import com.viglet.shio.persistence.repository.auth.ShRoleRepository;

import jakarta.transaction.Transactional;

/**
 * SH1206 &mdash; a group's page listed its members and not the roles it grants, because the group
 * read fills {@code shUsers} and the role owns the other relation. {@code GET /api/v2/group/{id}/roles}
 * answers it, and these hold what the page relies on: the roles that name the group, by name, and a
 * teaching 404 for a group that does not exist.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@Transactional
class ShGroupRolesTest {

    @Autowired
    private ShGroupAPI shGroupAPI;

    @Autowired
    private ShGroupRepository shGroupRepository;

    @Autowired
    private ShRoleRepository shRoleRepository;

    private ShRole role(String name, ShGroup group) {
        ShRole role = new ShRole();
        role.setName(name);
        role.setDescription(name + " fixture");
        role.setShGroups(group == null ? Set.of() : Set.of(group));
        return shRoleRepository.saveAndFlush(role);
    }

    @Test
    void theRolesThatNameAGroupAreItsRolesSortedByName() {
        ShGroup group = new ShGroup();
        group.setName("sh1206-editors");
        group.setDescription("SH1206 fixture");
        group = shGroupRepository.saveAndFlush(group);
        role("sh1206-publisher", group);
        role("sh1206-author", group);
        role("sh1206-unrelated", null);

        List<ShGroupAPI.ShGroupRole> roles = shGroupAPI.shGroupRoles(group.getId());

        assertEquals(List.of("sh1206-author", "sh1206-publisher"), roles.stream().map(ShGroupAPI.ShGroupRole::name).toList(),
                "SH1206: a group's roles are the roles that name it, by name; a role naming another group "
                        + "is not one of them");
    }

    @Test
    void aGroupThatDoesNotExistIsATeaching404() {
        ResponseStatusException refused = assertThrows(ResponseStatusException.class,
                () -> shGroupAPI.shGroupRoles("sh1206-no-such-group"));
        assertEquals(HttpStatus.NOT_FOUND, refused.getStatusCode(), "SH1206: an unknown group must be a 404");
        assertTrue(String.valueOf(refused.getReason()).contains("GET /api/v2/group"),
                "SH1206: the refusal must say where the ids are (P6)");
    }
}
