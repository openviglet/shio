/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.error.ShDataIntegrityProblem;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.api.site.ShSiteWriteService;

/**
 * SH719 — one live post per address, enforced by the database rather than by a lookup.
 *
 * <h2>The assertion that would have caught it</h2>
 *
 * <p>Measured by SH713's concurrency suite at the smallest concurrency there is: two MCP
 * connections, one {@code post.upsert} each, the same new address. <b>Both answered
 * {@code changed=1}</b>, and the site was left holding two posts on one friendly URL — an address no
 * write can name and no read can resolve, removable only by id.</p>
 *
 * <p>The shape is the ordinary check-then-act: the resolver looks the URL up, finds nothing, and the
 * write inserts. Nothing between those two steps is atomic, so <b>no unit test that runs one thread
 * can reach it</b> — which is exactly why it survived to be found by a live suite, and why the
 * assertion here is not a race. It is the invariant the race violated: <em>a second live row at one
 * address cannot exist</em>. A test that tried to reproduce the interleaving would be flaky in both
 * directions; this one is green only when the second insert is impossible.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShPostAddressUniquenessTest {

    @Autowired
    private ShSiteWriteService shSiteWriteService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private com.viglet.shio.persistence.repository.folder.ShFolderRepository shFolderRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("SH719: a second live post cannot be inserted at an address another one holds")
    void oneAddressHoldsOnePost() {
        ShSite site = site("ut-sh719-");
        String furl = "about-" + UUID.randomUUID().toString().substring(0, 8);
        insert(site, furl, ShPostState.DRAFT);

        assertThatThrownBy(() -> insert(site, furl, ShPostState.DRAFT))
                .as("""
                        This is the whole of SH719. Before it, the second insert SUCCEEDED and both \
                        callers were told they had won — worse than a lost update, which loses one \
                        write: this kept both, destroyed the address, and gave neither caller a \
                        reason to look.""")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("SH719: the two state rows of one post are not a collision with each other")
    void draftAndPublishedOfOnePostCoexist() {
        ShSite site = site("ut-sh719-states-");
        String furl = "dual-" + UUID.randomUUID().toString().substring(0, 8);
        String postId = insert(site, furl, ShPostState.DRAFT);

        // The same post's PUBLISHED row shares its furl by design (SH81: the address does not change
        // meaning across a publish), so `state` is part of the key or publishing would refuse itself.
        assertThat(insert(site, furl, ShPostState.PUBLISHED, postId)).isEqualTo(postId);
        assertThat(ShPostUnified.urlKeyOf(site.getId(), ShPostState.DRAFT, "en", furl, null))
                .isNotEqualTo(ShPostUnified.urlKeyOf(site.getId(), ShPostState.PUBLISHED, "en", furl, null));
    }

    @Test
    @DisplayName("SH719: a trashed post does not reserve the address it used to hold")
    void aTrashedPostReleasesItsAddress() {
        ShSite site = site("ut-sh719-trash-");
        String furl = "released-" + UUID.randomUUID().toString().substring(0, 8);
        String first = insert(site, furl, ShPostState.DRAFT);

        shPostUnifiedWriteService.delete(first, "curator");

        // SH243 is explicit that a live post may take a trashed one's URL and that the RESTORE is
        // what gets refused. A plain unique index would have reversed that shipped decision, which
        // is why the key is NULL for a trashed row rather than the furl.
        assertThat(ShPostUnified.urlKeyOf(site.getId(), ShPostState.DRAFT, "en", furl, Instant.now()))
                .as("a trashed row occupies no address")
                .isNull();
        assertThat(insert(site, furl, ShPostState.DRAFT))
                .as("so the address is free, and SH243's refusal still belongs to the restore")
                .isNotBlank();
    }

    @Test
    @DisplayName("SH719: the refusal tells the loser to retry, which is the one action that works")
    void theRefusalSaysRetryAndNotTheOpposite() {
        var problem = ShDataIntegrityProblem.of(
                new DataIntegrityViolationException(
                        "could not execute statement; Unique index or primary key violation: "
                                + "\"PUBLIC.UK_POST_UNIFIED_URL ON PUBLIC.SH_POST_UNIFIED(URL_KEY)\""),
                "post.upsert post:vilt/about");

        assertThat(problem.slug()).isEqualTo("duplicate-url");
        var body = problem.toProblemDetail();
        assertThat(String.valueOf(body.getProperties().get("fix")))
                .as("""
                        Every other constraint on this surface answers "repeating it unchanged will \
                        be refused again", and here that is the opposite of the truth: the address \
                        now EXISTS, and post.upsert is an upsert. An agent that read the generic \
                        sentence would abandon the one action that works.""")
                .contains("Retry");
        assertThat(body.getDetail()).contains("post.upsert post:vilt/about");
    }

    /** A site with a root folder, which is where the posts below land. */
    private ShSite site(String prefix) {
        ShSite payload = new ShSite();
        payload.setName(prefix + UUID.randomUUID());
        return shSiteWriteService.createBlank(payload, "admin");
    }

    private String insert(ShSite site, String furl, ShPostState state) {
        return insert(site, furl, state, UUID.randomUUID().toString());
    }

    /**
     * A row written through the repository, deliberately: this asserts the <b>database's</b> refusal,
     * and going through a service that checks first would prove the check rather than the constraint
     * — the check is what SH719 showed is not enough.
     */
    private String insert(ShSite site, String furl, ShPostState state, String postId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            ShPostUnified row = new ShPostUnified();
            row.setId(postId);
            row.setState(state);
            row.setShSite(site);
            // Read through the repository, not `site.getShFolders()`: the instance `createBlank`
            // returns was saved before its root folder existed, so its collection is empty.
            row.setShFolder(shFolderRepository.findRootFolder(site).orElseThrow());
            row.setFurl(furl);
            row.setTitle("SH719 fixture");
            row.setLocale("en");
            row.setOwner("admin");
            row.setDate(Instant.now());
            row.setData("{}");
            shPostUnifiedRepository.saveAndFlush(row);
            return row.getId();
        });
    }
}
