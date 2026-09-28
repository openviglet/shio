/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.cda.dto.ShCdaField;
import com.viglet.shio.api.cda.dto.ShCdaPost;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH10 CDA write surface ({@link ShCdaWriteService}) — the Universal Editor
 * save-back. Edits land on the {@code DRAFT} row; publishing promotes it; the
 * SH2 per-site allow-list is enforced on the write path; and {@code getEditable}
 * exposes the post-type field schema.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShCdaWriteTest {

    @Autowired
    private ShCdaWriteService shCdaWriteService;
    @Autowired
    private ShCdaWriteAPI shCdaWriteAPI;
    @Autowired
    private com.viglet.shio.api.post.unified.ShPostDraftMergeService shPostDraftMergeService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String draftPostId;       // DRAFT row only
    private String publishedOnlyId;   // PUBLISHED row only
    private String toPublishId;       // DRAFT row, promoted in the publish test

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShPostType postType = new ShPostType();
            postType.setName("PT-WR-" + UUID.randomUUID());
            postType.setTitle("Write Test Post Type");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            ShPostTypeAttr titleAttr = attr(postType, "title", "Title", 0, (byte) 1, (byte) 0);
            ShPostTypeAttr bodyAttr = attr(postType, "body", "Body", 1, (byte) 0, (byte) 1);
            entityManager.persist(titleAttr);
            entityManager.persist(bodyAttr);
            postType.setShPostTypeAttrs(Set.of(titleAttr, bodyAttr));
            entityManager.merge(postType);

            ShSite site = new ShSite();
            site.setName("wr-site-" + UUID.randomUUID());
            site.setUrl("http://wr.example.test");
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

            draftPostId = UUID.randomUUID().toString();
            persistRow(draftPostId, ShPostState.DRAFT, site, root, postType, "Original");

            publishedOnlyId = UUID.randomUUID().toString();
            persistRow(publishedOnlyId, ShPostState.PUBLISHED, site, root, postType, "Live");

            toPublishId = UUID.randomUUID().toString();
            persistRow(toPublishId, ShPostState.DRAFT, site, root, postType, "Pending");
        });
    }

    private ShPostTypeAttr attr(ShPostType postType, String name, String label,
                                int ordinal, byte isTitle, byte isSummary) {
        ShPostTypeAttr a = new ShPostTypeAttr();
        a.setName(name);
        a.setLabel(label);
        a.setOrdinal(ordinal);
        a.setRequired((byte) 0);
        a.setIsTitle(isTitle);
        a.setIsSummary(isSummary);
        a.setShPostType(postType);
        return a;
    }

    private void persistRow(String id, ShPostState state, ShSite site, ShFolder folder,
                            ShPostType postType, String title) {
        ShPostUnified post = new ShPostUnified();
        post.setId(id);
        post.setState(state);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(postType);
        post.setTitle(title);
        post.setPublished(state == ShPostState.PUBLISHED);
        post.setOwner("admin");
        post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        post.setData("{\"title\":\"" + title + "\"}");
        entityManager.persist(post);
    }

    @AfterAll
    void tearDown() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            for (String id : new String[]{draftPostId, publishedOnlyId, toPublishId}) {
                if (id != null) {
                    shPostUnifiedRepository.findById(id).forEach(shPostUnifiedRepository::delete);
                }
            }
        });
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void useToken(ShApiTokenScope scope, Set<String> siteIds) {
        var auth = new UsernamePasswordAuthenticationToken("editor", null, List.of());
        auth.setDetails(new ShApiTokenContext("tok", scope, siteIds, ShApiTokenEnv.PROD));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Map<String, JsonNode> change(String prop, String value) {
        return Map.of(prop, objectMapper.valueToTree(value));
    }

    // -- SH1091: the CDA merge is conditional, so a stateless caller can change one field safely --

    @Test
    void sh1091TheEditableViewAnswersTheTagThePatchIsCheckedAgainst() {
        useToken(ShApiTokenScope.WRITE, Set.of());

        var read = shCdaWriteAPI.editable(draftPostId);
        String etag = read.getHeaders().getETag();
        assertThat(etag).as("SH1091: /editable answers no ETag, so a caller has nothing to send as If-Match")
                .isNotBlank();

        var written = shCdaWriteAPI.patch(draftPostId,
                new com.viglet.shio.api.cda.dto.ShCdaPatch(change("title", "Conditional"), false), etag, null);
        assertThat(written.getStatusCode().is2xxSuccessful()).as("SH1091: the current tag was refused").isTrue();
        assertThat(written.getHeaders().getETag())
                .as("SH1091: a write must answer its new tag, or a second change needs a second read")
                .isNotBlank().isNotEqualTo(etag);
    }

    @Test
    void sh1091APatchCarryingATagTheDraftHasMovedPastIsRefused() {
        useToken(ShApiTokenScope.WRITE, Set.of());

        String stale = shCdaWriteAPI.editable(draftPostId).getHeaders().getETag();
        shCdaWriteAPI.patch(draftPostId,
                new com.viglet.shio.api.cda.dto.ShCdaPatch(change("title", "Moved"), false), stale, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> shCdaWriteAPI.patch(draftPostId,
                        new com.viglet.shio.api.cda.dto.ShCdaPatch(change("title", "Overwrite"), false), stale, null))
                .as("SH1091: a write naming a draft that moved since was applied, which is the lost update "
                        + "this precondition exists to refuse")
                .isInstanceOf(com.viglet.shio.api.agent.ShStaleExpectationException.class);
        assertThat(shPostUnifiedRepository.findByIdAndState(draftPostId, ShPostState.DRAFT))
                .get().extracting(ShPostUnified::getData).asString().contains("Moved").doesNotContain("Overwrite");
    }

    @Test
    void sh1091APostWithNoDraftYetIsCheckedAgainstItsPublishedRow() {
        useToken(ShApiTokenScope.WRITE, Set.of());

        String etag = shCdaWriteAPI.editable(publishedOnlyId).getHeaders().getETag();
        assertThat(etag).as("SH1091: a published-only post has no tag, so its first change cannot be guarded")
                .isNotBlank();
        var written = shCdaWriteAPI.patch(publishedOnlyId,
                new com.viglet.shio.api.cda.dto.ShCdaPatch(change("title", "Seeded"), false), etag, null);
        assertThat(written.getStatusCode().is2xxSuccessful())
                .as("SH1091: the published row's tag was refused for the merge that seeds the draft from it")
                .isTrue();
    }

    @Test
    void patchUpdatesDraftAndRederivesTitle() {
        useToken(ShApiTokenScope.WRITE, Set.of());

        var result = shCdaWriteService.patch(draftPostId, change("title", "Updated"), false, "editor");

        assertThat(result).map(ShCdaPost::title).contains("Updated");
        assertThat(shPostUnifiedRepository.findByIdAndState(draftPostId, ShPostState.DRAFT))
                .get().extracting(ShPostUnified::getData).asString().contains("Updated");
        // No published row was created (save-as-draft only).
        assertThat(shPostUnifiedRepository.findByIdAndState(draftPostId, ShPostState.PUBLISHED)).isEmpty();
    }

    @Test
    void patchOnPublishedOnlyPostSeedsADraft() {
        useToken(ShApiTokenScope.WRITE, Set.of());

        var result = shCdaWriteService.patch(publishedOnlyId, change("title", "Edited"), false, "editor");

        assertThat(result).map(ShCdaPost::title).contains("Edited");
        // The new draft holds the edit; the live published snapshot is untouched.
        assertThat(shPostUnifiedRepository.findByIdAndState(publishedOnlyId, ShPostState.DRAFT))
                .get().extracting(ShPostUnified::getData).asString().contains("Edited");
        assertThat(shPostUnifiedRepository.findByIdAndState(publishedOnlyId, ShPostState.PUBLISHED))
                .get().extracting(ShPostUnified::getTitle).isEqualTo("Live");
    }

    @Test
    void patchWithPublishPromotesDraftToPublished() {
        useToken(ShApiTokenScope.WRITE, Set.of());

        var result = shCdaWriteService.patch(toPublishId, change("title", "Announced"), true, "editor");

        assertThat(result).map(ShCdaPost::published).contains(true);
        assertThat(shPostUnifiedRepository.findByIdAndState(toPublishId, ShPostState.PUBLISHED))
                .get().extracting(ShPostUnified::getTitle).isEqualTo("Announced");
    }

    @Test
    void perSiteAllowListBlocksWritesToOtherSites() {
        useToken(ShApiTokenScope.WRITE, Set.of("some-other-site"));

        var result = shCdaWriteService.patch(draftPostId, change("title", "Hijacked"), false, "editor");

        assertThat(result).isEmpty();
        // The draft was not touched.
        assertThat(shPostUnifiedRepository.findByIdAndState(draftPostId, ShPostState.DRAFT))
                .get().extracting(ShPostUnified::getData).asString().doesNotContain("Hijacked");
    }

    @Test
    void getEditableReturnsTheFieldSchema() {
        useToken(ShApiTokenScope.WRITE, Set.of());

        var editable = shCdaWriteService.getEditable(draftPostId);

        assertThat(editable).isPresent();
        assertThat(editable.get().fields()).extracting(ShCdaField::name)
                .contains("title", "body");
        assertThat(editable.get().fields()).filteredOn(f -> f.name().equals("title"))
                .extracting(ShCdaField::isTitle).containsExactly(true);
    }

    // --- SH335: the merge is the console's too, and needs no token ---

    /**
     * The property that decided the design. The two candidate fixes were a client
     * read-modify-write and a merge verb; the first reintroduces a lost update, and this is what
     * that would have cost — a curator editing the headline would have written back the body they
     * read a minute ago, over whatever a colleague changed in between.
     */
    @Test
    void theMergeLeavesAFieldItWasNotGiven() {
        useToken(ShApiTokenScope.WRITE, Set.of());
        shCdaWriteService.patch(draftPostId, change("summary", "Only the summary"), false, "editor");

        assertThat(shPostUnifiedRepository.findByIdAndState(draftPostId, ShPostState.DRAFT))
                .get().extracting(ShPostUnified::getData).asString()
                .contains("Only the summary")
                .contains("title");
    }

    /**
     * SH335 — the console path holds no API token at all, which is the whole point: the editor
     * used to make a curator mint a WRITE key to save content their session already authorized.
     * The guard moved out to each caller, so the merge itself must not depend on one being there.
     */
    @Test
    void theSharedMergeRunsWithNoTokenInContext() {
        SecurityContextHolder.clearContext();

        var merged = shPostDraftMergeService.merge(draftPostId, change("title", "By a curator"),
                false, "curator");

        assertThat(merged).isPresent();
        assertThat(shPostUnifiedRepository.findByIdAndState(draftPostId, ShPostState.DRAFT))
                .get().extracting(ShPostUnified::getData).asString().contains("By a curator");
    }

    @Test
    void mergingAPostThatDoesNotExistIsEmptyRatherThanAnError() {
        assertThat(shPostDraftMergeService.merge("no-such-post", change("title", "x"), false, "c"))
                .isEmpty();
    }

    /**
     * SH475 — {@code features.0.title}, the address a collection row needed.
     *
     * <p>A row of an SH95 collection is not a post: a {@code Relator} with nested fields holds an
     * inline array of row maps in the section's own {@code data}, so there is no id for an
     * {@code sh-post} and no flat prop that could reach the value. The converted layout emits
     * {@code sh-field="features.{{@index}}.title"} and this is the half that has to understand it,
     * or the editor writes a key called {@code features.0.title} at the top level and the row a
     * curator typed into is unchanged.</p>
     */
    @Test
    void anIndexedPropWritesIntoTheRowAndNotIntoANewKey() {
        SecurityContextHolder.clearContext();
        String data = "{\"title\":\"Grid\",\"features\":[{\"title\":\"One\",\"body\":\"a\"},"
                + "{\"title\":\"Two\",\"body\":\"b\"}]}";
        String postId = seedDraft(data);

        shPostDraftMergeService.merge(postId, change("features.1.body", "edited"), false, "curator");

        JsonNode after = objectMapper.readTree(shPostUnifiedRepository
                .findByIdAndState(postId, ShPostState.DRAFT).orElseThrow().getData());
        assertThat(after.path("features").path(1).path("body").asString())
                .as("the second row's body, in place")
                .isEqualTo("edited");
        assertThat(after.path("features").path(0).path("body").asString())
                .as("and the first row untouched — a merge, not a replacement of the collection")
                .isEqualTo("a");
        assertThat(after.has("features.1.body"))
                .as("""
                        the failure this exists to catch: a flat set would store the path as a KEY, \
                        which reads back as a successful save, renders nothing, and lands as an \
                        SH168 unknown-field on a post the curator did nothing wrong to""")
                .isFalse();
    }

    /**
     * The narrowness is the safety (SH475): this resolver never creates structure, because a path
     * that can create structure is a mistyped prop inventing a shape the post-type does not declare.
     */
    @Test
    void aPathThatNamesNoExistingRowStaysAFlatKey() {
        SecurityContextHolder.clearContext();
        String postId = seedDraft("{\"title\":\"Grid\",\"features\":[{\"title\":\"One\"}]}");

        shPostDraftMergeService.merge(postId, change("features.7.title", "nowhere"), false, "curator");

        JsonNode after = objectMapper.readTree(shPostUnifiedRepository
                .findByIdAndState(postId, ShPostState.DRAFT).orElseThrow().getData());
        assertThat(after.path("features").size())
                .as("no row is invented to receive it")
                .isEqualTo(1);
        assertThat(after.has("features.7.title"))
                .as("it falls through to the flat behaviour every other prop has, so the SH168 lint "
                        + "reports it rather than this method deciding what the model should be")
                .isTrue();
    }

    private String seedDraft(String data) {
        String postId = UUID.randomUUID().toString();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShPostUnified post = new ShPostUnified();
            post.setId(postId);
            post.setState(ShPostState.DRAFT);
            post.setTitle("Row fixture");
            post.setFurl("/rows-" + postId.substring(0, 8));
            post.setDate(Instant.now());
            post.setOwner("admin");
            post.setData(data);
            entityManager.persist(post);
        });
        return postId;
    }

    /**
     * SH324 — the premise every hand-written fixture is modelling, and nothing asserted it.
     *
     * <p>{@code ShPostUnifiedWriteService.publish} gives the published row the <b>draft's id</b>, so
     * a page is two rows sharing one id and one furl. A fixture that mints an id per row instead
     * describes two posts claiming a URL, which is the SH321 defect rather than a published page —
     * and {@code ShTemplateGraphTest} did exactly that for four blocks while the suite stayed
     * green.</p>
     *
     * <p>This is the half of SH324 that is true whichever instrument wins: it pins what a fixture is
     * supposed to look like, so "the fixture is wrong" becomes a claim with something behind it.</p>
     */
    @Test
    void publishingKeepsTheDraftsIdSoOnePageIsOneId() {
        useToken(ShApiTokenScope.WRITE, Set.of());
        shCdaWriteService.patch(toPublishId, change("title", "Two rows, one page"), true, "editor");

        assertThat(shPostUnifiedRepository.findByIdAndState(toPublishId, ShPostState.PUBLISHED))
                .as("a published page is the draft's id in a second row, never a new id")
                .isPresent();
        assertThat(shPostUnifiedRepository.findAll().stream()
                .filter(row -> toPublishId.equals(row.getId()))
                .map(ShPostUnified::getFurl)
                .distinct()
                .toList())
                .as("and both rows carry the one furl, which is what makes them one page")
                .hasSize(1);
    }
}
