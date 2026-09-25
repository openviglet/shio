/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.note.ShNoteRelay;
import com.viglet.shio.api.note.ShNoteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.note.ShNote;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1062 — asking the connected agent for a translation, from the Languages board.
 *
 * <p>Shio hosts no model, so the board cannot translate; it can ask. The request is the board's own
 * two existing moves in sequence: the missing cell creates its draft (SH1060), and a {@code request}
 * note on that draft asks for it to be written. The draft already carries the target locale and its
 * translation group, so the note needs nothing the notes vocabulary lacks, and the agent finds it in
 * {@code shio://requests} with the address of the page to write. What is pinned here is the loop a
 * curator watches: the cell moves from missing to requested, the agent sees the request, and the mark
 * goes when the request is resolved.</p>
 *
 * <p>A class of its own rather than more of {@code ShSiteCoverageTest}: a request changes the
 * board's totals, and that test asserts them.</p>
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShTranslationRequestTest {

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ShSiteCoverageService coverage;
    @Autowired
    private ShNoteService noteService;
    @Autowired
    private ShNoteRelay relay;
    @Autowired
    private ShPostUnifiedWriteService write;
    @Autowired
    private ShSiteRepository shSiteRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteId;
    private String siteName;
    private String sourceId;

    @BeforeAll
    void seed() {
        siteName = "sh1062req-" + UUID.randomUUID().toString().substring(0, 8);
        String[] ids = new String[2];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(EPOCH);
            site.setOwner("admin");
            site.setLocales("{\"default\":\"en\",\"enabled\":[\"en\",\"de\"]}");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(EPOCH);
            root.setOwner("admin");
            entityManager.persist(root);
            ids[0] = root.getId();

            ShPostType type = new ShPostType();
            type.setName("Sh1062Req" + UUID.randomUUID().toString().substring(0, 6));
            type.setTitle("SH1062 request page");
            type.setSystem((byte) 0);
            type.setDate(EPOCH);
            type.setOwner("admin");
            entityManager.persist(type);
            ids[1] = type.getId();
        });
        sourceId = write.create(new CreateRequest(ids[0], ids[1],
                objectMapper.readTree("{\"body\":\"careers\"}"), ShPostState.DRAFT, "admin", "en", null,
                "/careers")).getId();
    }

    @Test
    @DisplayName("SH1062: a translation asked of the agent is marked on the board until it is answered")
    void aRequestedTranslationIsMarkedUntilResolved() {
        assertThat(cell().state())
                .as("SH1062: the fixture starts with German missing")
                .isEqualTo(ShSiteCoverage.MISSING);

        // The board's two moves: create the missing draft, then ask for it to be written.
        ShPostUnified draft = write.translate(sourceId, "de", null, "curator");
        // This fixture's type declares no title field, so the draft derives no address of its own;
        // a real page derives one from its title. Given one here, the way a curator would.
        write.setFurl(draft.getId(), "/karriere");
        ShNote request = noteService.add(draft.getId(), null, null, null, "request",
                "Translate this page from en into de.", "curator");

        ShSiteCoverage.Cell asked = cell();
        assertThat(asked.id()).isEqualTo(draft.getId());
        assertThat(asked.requested())
                .as("SH1062: the board cannot tell a draft somebody asked the agent to write from one "
                        + "a curator is writing, so the cell reads as done while nobody has started")
                .isTrue();
        assertThat(draft.getTranslationGroupId())
                .as("SH1062: the draft is what carries the target locale and its source to the agent")
                .isEqualTo(sourceId);

        assertThat(relay.render())
                .as("SH1062: the agent reads open requests from shio://requests; one it cannot see is "
                        + "a request nobody will answer")
                .contains("note=" + request.getId())
                .contains("post:" + siteName + "/karriere")
                .contains(asked.address())
                .contains("Translate this page from en into de.");

        noteService.resolve(request.getId(), true, "agent");
        assertThat(cell().requested())
                .as("SH1062: an answered request still marked would keep the cell saying 'asked' forever")
                .isNull();
    }

    private ShSiteCoverage.Cell cell() {
        return new TransactionTemplate(transactionManager).execute(status ->
                coverage.of(shSiteRepository.findById(siteId).orElseThrow(), 0, 50))
                .rows().getFirst().cells().get("de");
    }
}
