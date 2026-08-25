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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.dto.auth.ShGroupDTO;
import com.viglet.shio.persistence.model.auth.ShGroup;
import com.viglet.shio.persistence.repository.auth.ShGroupRepository;

/**
 * SH924 &mdash; a write that succeeds and produces a row nothing can address.
 *
 * <p><b>The boundary the defect crossed is the API, so that is where this asserts.</b> A unit test
 * of the id-minting helper would pass against the broken version of this endpoint, because the
 * defect was never in how an id is generated &mdash; it was that the handler asked for one only
 * when the field was absent, and a create page sending {@code {id: ""}} is not absent.</p>
 *
 * <h2>Why the assertion is "reachable", not "non-blank"</h2>
 *
 * <p>The symptom a curator met was not a blank string in a column. It was a group listed in the
 * console that could not be opened, edited or deleted: {@code /v2/group/} is not
 * {@code /v2/group/{id}} so it does not route, and every id-shaped path answered 404. So the test
 * asks the question the defect actually posed &mdash; <b>can the row this call created be found by
 * its own id?</b> &mdash; which stays true if the minting strategy is ever replaced.</p>
 *
 * <h2>And the second door, which the sweep found rather than the report</h2>
 *
 * <p>{@code shGroupUpdate} read its id from the body and ignored its own {@code {id}} path
 * variable, so it saved a blank-keyed row for the same reason, on a request shaped like an update
 * &mdash; and {@code PUT /v2/group/A} carrying {@code B} updated B. Both are asserted here because
 * both were the same line of reasoning: the id came from whatever the client sent.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
@Transactional
class ShGroupCreateIdTest {

    @Autowired
    private ShGroupAPI shGroupAPI;

    @Autowired
    private ShGroupRepository shGroupRepository;

    private static ShGroupDTO dto(String id, String name) {
        ShGroupDTO shGroupDTO = new ShGroupDTO();
        shGroupDTO.setId(id);
        shGroupDTO.setName(name);
        shGroupDTO.setDescription("SH924 fixture");
        return shGroupDTO;
    }

    @Test
    void blankIdOnCreateIsMintedSoTheRowCanBeReached() {
        ShGroupDTO created = shGroupAPI.shGroupAdd(dto("", "sh924-blank"));

        assertNotNull(created.getId(),
                "SH924: POST /api/v2/group answered with a null id, so the caller cannot address "
                        + "what it just created.");
        assertFalse(created.getId().isBlank(),
                "SH924: POST /api/v2/group stored the blank id it was sent. The row is listed by "
                        + "GET /v2/group and reachable by nothing: /v2/group/ does not route and "
                        + "every id-shaped path is a 404, so it can only be removed at the "
                        + "database. A blank primary key means 'not yet assigned' and the server "
                        + "is what assigns it.");

        Optional<ShGroup> found = shGroupRepository.findById(created.getId());
        assertTrue(found.isPresent(),
                "SH924: the id returned to the caller does not find the row that was created, so "
                        + "the response is telling the client an address that answers 404.");
        assertEquals("sh924-blank", found.get().getName(),
                "SH924: the id came back pointing at a different row");
    }

    @Test
    void anIdTheCallerChoseIsStillHonoured() {
        // The half that makes minting safe to add: a caller assigning its own key - the shape every
        // import path uses - must keep working, or this fix trades one broken caller for another.
        ShGroupDTO created = shGroupAPI.shGroupAdd(dto("sh924-chosen-key", "sh924-chosen"));

        assertEquals("sh924-chosen-key", created.getId(),
                "SH924: a non-blank id the caller supplied was discarded. Minting is for a key the "
                        + "client does not have, not a replacement for one it does.");
        assertTrue(shGroupRepository.findById("sh924-chosen-key").isPresent(),
                "SH924: the chosen key did not persist");
    }

    @Test
    void updateAddressesTheRowItsPathNames() {
        ShGroupDTO created = shGroupAPI.shGroupAdd(dto(null, "sh924-target"));
        String id = created.getId();

        // A body carrying a different id, which is what the old handler would have followed.
        shGroupAPI.shGroupUpdate(id, dto("sh924-not-this-one", "sh924-renamed"));

        assertTrue(shGroupRepository.findById("sh924-not-this-one").isEmpty(),
                "SH924: PUT /api/v2/group/{id} wrote the row named in the BODY rather than the one "
                        + "named in the path, so a client can update a group it did not address - "
                        + "and, with a blank body id, create an unreachable one from an endpoint "
                        + "that looks like an update.");

        Optional<ShGroup> target = shGroupRepository.findById(id);
        assertTrue(target.isPresent(), "SH924: the addressed row disappeared on update");
        assertEquals("sh924-renamed", target.get().getName(),
                "SH924: the addressed row was not the one updated");
    }
}
