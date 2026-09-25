/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

/**
 * SH1092 — a draft report says which rules never ran on it.
 *
 * <p>The route and markup rules read published rows only, by design: a draft's broken link is work
 * in progress. A {@code state=draft} run therefore reported {@code errors: 0} over links, images and
 * rich text it never looked at, and its one note described the indexes instead. Now it names the
 * skipped rules, and this pins two things: the note appears on a draft run of routes and nowhere
 * else, and every rule it names is one the service really emits, so the list cannot name a rule that
 * was renamed or retired and go on reading as true.</p>
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShVerifyDraftNoteTest {

    @Autowired
    private ShAgentVerifyService verifyService;
    @Autowired
    private ShSiteRepository shSiteRepository;

    /** A site of this test's own, so the run is scoped rather than instance-wide (SH318's lint). */
    private String siteName;

    @BeforeAll
    void seed() {
        siteName = "sh1092note-" + UUID.randomUUID().toString().substring(0, 8);
        ShSite site = new ShSite();
        site.setName(siteName);
        site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        site.setOwner("admin");
        shSiteRepository.saveAndFlush(site);
    }

    @Test
    @DisplayName("SH1092: a draft run of routes names the rules that did not run on it")
    void aDraftReportSaysWhatItSkipped() {
        ShAgentReport draft = verifyService.verify(siteName, "routes", 10, null, null, null, "draft");
        assertThat(draft.notes())
                .as("SH1092: a draft report read errors 0 over links and images it never checked")
                .anySatisfy(note -> assertThat(note)
                        .contains("did not run on these drafts")
                        .contains("link-broken").contains("image-no-alt").contains("html-unsafe"));

        ShAgentReport published = verifyService.verify(siteName, "routes", 10, null, null, null, "published");
        assertThat(published.notes()).noneMatch(note -> note.contains("did not run on these drafts"));
        ShAgentReport content = verifyService.verify(siteName, "content", 10, null, null, null, "draft");
        assertThat(content.notes())
                .as("SH1092: the skipped rules are routes', so a content-only run has nothing to confess")
                .noneMatch(note -> note.contains("did not run on these drafts"));
    }

    @Test
    @DisplayName("SH1092: every rule the note names is one the service emits")
    void theNamedRulesAreReal() throws IOException {
        String source = Files.readString(Path.of("src", "main", "java", "com", "viglet", "shio", "api",
                "agent", "verify", "ShAgentVerifyService.java"));
        for (String rule : ShAgentVerifyService.PUBLISHED_ONLY_RULES) {
            assertThat(source)
                    .as("SH1092: the draft note names '%s', which no finding in the service emits", rule)
                    .containsPattern("Finding\\.(error|warning)\\(\\s*check,\\s*\"" + rule + "\"");
        }
    }
}
