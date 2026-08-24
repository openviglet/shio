/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH921 — the 404 from {@code GET /api/v2/post-unified/{id}} says which of the two it is.
 *
 * <h2>The assertion that would have caught it, at the boundary it crossed</h2>
 *
 * <p>The boundary is the HTTP response, so that is what this reads: not that the service returns
 * an empty {@code Optional}, which was always true and was never the defect, but that the body
 * the caller receives distinguishes <em>this post has no draft</em> from <em>no such post</em>.
 * Until SH921 both were {@code 404} with <b>zero bytes</b>, and a caller holding an id that
 * {@code /agent/find} had just handed them had to guess between a wrong id, a wrong state and a
 * deleted row.
 *
 * <h2>Why the fixture is a post with no draft rather than a static file</h2>
 *
 * The browser check hit this on a static file, and SH921 was filed saying a static file is not a
 * post-unified row. <b>That was wrong.</b> Asked as {@code ?state=published} the very same id
 * answers {@code 200} — it is a post-unified row, it simply has no {@code DRAFT}. The defect is
 * general: any post published without ever being drafted answers nothing for a draft read, and
 * static files are merely the population where that is always true. So the fixture is the general
 * case, and a test built around `File` would have fixed one symptom of it.
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShPostStateNotFoundTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private Principal principal;

    /** A post that exists only as PUBLISHED — the shape a bare 404 could not describe. */
    private String publishedOnlyId;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn("admin");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("sh921-" + UUID.randomUUID());
            site.setDescription("SH921");
            site.setUrl("http://example.test");
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            root.setOwner("admin");
            entityManager.persist(root);

            ShWidget text = entityManager.find(ShWidget.class,
                    shWidgetRepository.findFirstByName(ShSystemWidget.TEXT).getId());

            ShPostType postType = new ShPostType();
            postType.setName("SH921-" + UUID.randomUUID());
            postType.setTitle("SH921 post type");
            postType.setDescription("SH921");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");

            ShPostTypeAttr title = new ShPostTypeAttr();
            title.setName(ShSystemPostTypeAttr.TITLE);
            title.setLabel("Title");
            title.setDescription("Title");
            title.setOrdinal(1);
            title.setRequired((byte) 1);
            title.setIsTitle((byte) 1);
            title.setIsSummary((byte) 0);
            title.setShWidget(text);
            title.setShPostType(postType);
            Set<ShPostTypeAttr> attrs = new HashSet<>();
            attrs.add(title);
            postType.setShPostTypeAttrs(attrs);
            entityManager.persist(postType);

            // Persisted straight to PUBLISHED, which is exactly what a static file and an
            // imported post look like: there is no draft row and there never was one.
            ShPostUnified post = new ShPostUnified();
            post.setShFolder(root);
            post.setShPostType(postType);
            post.setState(ShPostState.PUBLISHED);
            // `data` is NOT NULL in the schema, so a fixture that leaves it out fails at commit
            // with a constraint violation several frames from the test that wanted the row.
            post.setData("{\"" + ShSystemPostTypeAttr.TITLE + "\":\"SH921 published only\"}");
            post.setTitle("SH921 published only");
            post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            post.setOwner("admin");
            entityManager.persist(post);
            publishedOnlyId = post.getId();
        });
    }

    @Test
    void aDraftReadOfAPublishedOnlyPostTeachesRatherThanReturningNothing() throws Exception {
        mockMvc.perform(get("/api/v2/post-unified/" + publishedOnlyId + "?state=DRAFT")
                        .principal(principal))
                .andExpect(status().isNotFound())
                // The body is the whole point. Before SH921 this was zero bytes.
                .andExpect(jsonPath("$.type").value(
                        "https://shio.viglet.com/errors/post-state-not-found"))
                // It has to say the post EXISTS, or the caller still cannot tell the two apart.
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString(publishedOnlyId)))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("exists")))
                // P6: every 4xx carries a way forward.
                .andExpect(jsonPath("$.allowed[0]").value("published"))
                .andExpect(jsonPath("$.didYouMean").value("published"))
                .andExpect(jsonPath("$.fix").value(
                        org.hamcrest.Matchers.containsString("state=published")));
    }

    @Test
    void thePublishedReadStillWorks() throws Exception {
        // The control. A 404 that teaches is worth nothing if it is now answering for a state
        // that does exist — and `findByIdAndState` is one line away from that mistake.
        mockMvc.perform(get("/api/v2/post-unified/" + publishedOnlyId + "?state=PUBLISHED")
                        .principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publishedOnlyId));
    }

    @Test
    void anIdNobodyHasHeardOfIsADifferentAnswer() throws Exception {
        // The other half. If both cases produced the same slug, the caller would be exactly as
        // stuck as before — the body would be longer and say nothing more.
        mockMvc.perform(get("/api/v2/post-unified/" + UUID.randomUUID() + "?state=DRAFT")
                        .principal(principal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(
                        "https://shio.viglet.com/errors/no-such-post"))
                .andExpect(jsonPath("$.fix").value(
                        org.hamcrest.Matchers.containsString("staticfile")));
    }
}
