/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentBatch.Op;
import com.viglet.shio.api.agent.dto.ShAgentDoc;
import com.viglet.shio.api.agent.dto.ShAgentFound;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/**
 * SH83's content reads ({@code find} / {@code read}) and SH123's field projection.
 *
 * <p>The properties under test: find returns <b>identifiers, not bodies</b> (the reason
 * it is a separate tool at all), reads are <b>DRAFT-preferred</b> so an agent can see
 * its own unpublished work, and a projection naming a field the type does not have is
 * <b>refused</b> rather than answered with an empty document — the one way this feature
 * could cause real damage.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentReadTest {

    @Autowired
    private ShAgentReadService shAgentReadService;
    @Autowired
    private com.viglet.shio.persistence.repository.site.ShSiteRepository shSiteRepository;
    @Autowired
    private com.viglet.shio.persistence.repository.folder.ShFolderRepository shFolderRepository;
    @Autowired
    private com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository shPostTypeRepository;
    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private com.viglet.shio.migration.unified.ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String postTypeName;
    private String base;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            siteName = "read-site-" + UUID.randomUUID();
            site.setName(siteName);
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

            ShWidget widget = entityManager
                    .createQuery("select w from ShWidget w where w.name = 'Text'", ShWidget.class)
                    .getResultStream().findFirst().orElseGet(() -> {
                        ShWidget created = new ShWidget();
                        created.setName("Text");
                        created.setDescription("Text");
                        entityManager.persist(created);
                        return created;
                    });

            ShPostType postType = new ShPostType();
            postTypeName = "ReadArticle" + UUID.randomUUID().toString().substring(0, 8);
            postType.setName(postTypeName);
            postType.setTitle("Read Article");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            persistAttr(postType, widget, "headline", 1, (byte) 1);
            persistAttr(postType, widget, "body", 2, (byte) 0);
        });

        base = "/reading-" + UUID.randomUUID().toString().substring(0, 8);
        tx.executeWithoutResult(status -> shAgentBatchService.run(new ShAgentBatch(false, List.of(
                new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + siteName + base,
                        null, null, null),
                new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + siteName + base + "/deep",
                        null, null, null),
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + base + "/alpha",
                        postTypeName, base, data("Alpha", "The alpha body")),
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + base + "/beta",
                        postTypeName, base + "/deep", data("Beta", "The beta body")))),
                false, "tester"));
    }

    private void persistAttr(ShPostType postType, ShWidget widget, String name, int ordinal, byte title) {
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName(name);
        attr.setLabel(name);
        attr.setOrdinal(ordinal);
        attr.setIsTitle(title);
        attr.setRequired((byte) 0);
        attr.setShWidget(widget);
        attr.setShPostType(postType);
        entityManager.persist(attr);
    }

    private tools.jackson.databind.JsonNode data(String headline, String body) {
        return objectMapper.valueToTree(Map.of("headline", headline, "body", body));
    }

    private String address(String suffix) {
        return "post:" + siteName + base + suffix;
    }

    // --- find ---

    @Test
    void findReturnsAddressesAndTitlesButNoContent() {
        ShAgentFound found = shAgentReadService.find(siteName, null, base, null, null, false);

        assertThat(found.count()).isEqualTo(2);
        assertThat(found.results()).extracting(ShAgentFound.Row::address)
                .containsExactly(address("/alpha"), address("/beta"));
        assertThat(found.results()).allSatisfy(row -> {
            assertThat(row.title()).isNotBlank();
            assertThat(row.state()).isEqualTo("DRAFT");
            // No body field exists on the row at all — that is the point of two tools.
            assertThat(row.id()).isNull();
        });
        assertThat(objectMapper.writeValueAsString(found)).doesNotContain("body");
    }

    @Test
    void aFolderFilterIncludesTheSubtree() {
        // "Everything under /section" is the question actually being asked; matching
        // only the exact folder would silently miss the pages one level down.
        assertThat(shAgentReadService.find(siteName, null, base, null, null, false).results())
                .extracting(ShAgentFound.Row::address).contains(address("/beta"));
        assertThat(shAgentReadService.find(siteName, null, base + "/deep", null, null, false).results())
                .extracting(ShAgentFound.Row::address).containsExactly(address("/beta"));
    }

    @Test
    void findFiltersByPostType() {
        assertThat(shAgentReadService.find(siteName, postTypeName, base, null, null, false).count())
                .isEqualTo(2);
    }

    @Test
    void findMatchesFullText() {
        assertThat(shAgentReadService.find(siteName, null, base, "Alpha", null, false).results())
                .extracting(ShAgentFound.Row::address).containsExactly(address("/alpha"));
    }

    @Test
    void findSaysWhenItCutTheResultSet() {
        ShAgentFound found = shAgentReadService.find(siteName, null, base, null, 1, false);

        assertThat(found.count()).isEqualTo(1);
        assertThat(found.truncated()).isTrue();
        // SH146 — a cut now says how to continue past it, which is what turns it from a dead
        // end into a page. The projection depends on this being present, not on the cap.
        assertThat(found.cursor()).isEqualTo(address("/alpha"));
    }

    @Test
    void aCursorWalksThePagesToTheEndAndThenStops() {
        ShAgentFound first = shAgentReadService.find(siteName, null, base, null, 1, false, null);
        ShAgentFound second = shAgentReadService.find(siteName, null, base, null, 1, false,
                first.cursor());

        assertThat(second.results()).extracting(ShAgentFound.Row::address)
                .containsExactly(address("/beta"));
        // The last page is not truncated and offers no cursor: that absence is how the caller
        // knows the walk finished, rather than having to ask for an empty page to find out.
        assertThat(second.truncated()).isFalse();
        assertThat(second.cursor()).isNull();
    }

    @Test
    void aCompleteResultOffersNoCursorToFollow() {
        ShAgentFound found = shAgentReadService.find(siteName, null, base, null, null, false, null);

        assertThat(found.truncated()).isFalse();
        assertThat(found.cursor()).isNull();
    }

    @Test
    void aCursorThatIsNotAnAddressIsRefusedRatherThanCompared() {
        // Lexicographic comparison against a row number or a timestamp *succeeds* and returns a
        // plausible page, so interpreting one would skip content silently. SH88's rule.
        assertThatThrownBy(() ->
                shAgentReadService.find(siteName, null, base, null, null, false, "200"))
                .isInstanceOf(ShAgentReadService.BadCursorException.class)
                .hasMessageContaining("not a row number and not a timestamp");
        assertThatThrownBy(() ->
                shAgentReadService.find(siteName, null, base, null, null, false,
                        "2026-07-27T10:00:00Z"))
                .isInstanceOf(ShAgentReadService.BadCursorException.class);
        // Blank is "start at the beginning", not a mistake — an empty query param is common.
        assertThat(shAgentReadService.find(siteName, null, base, null, null, false, "  ").count())
                .isEqualTo(2);
    }

    @Test
    void pagingAndFiltersCompose() {
        // The cursor narrows the same result set the filters produced, so a paged walk under a
        // type or folder filter cannot wander outside it.
        ShAgentFound page = shAgentReadService.find(siteName, postTypeName, base, null, 1, false,
                address("/alpha"));

        assertThat(page.results()).extracting(ShAgentFound.Row::address)
                .containsExactly(address("/beta"));
        assertThat(page.truncated()).isFalse();
    }

    @Test
    void anUnknownPostTypeTeachesTheVocabulary() {
        assertThatThrownBy(() ->
                shAgentReadService.find(siteName, "Artcle", null, null, null, false))
                .isInstanceOf(ShAgentBatchService.UnknownPostTypeException.class);
    }

    // --- read ---

    @Test
    void readReturnsTheDraftWithItsDataAndNoAuthoringMetadata() {
        ShAgentDoc doc = shAgentReadService.read(List.of(address("/alpha")), null, false).get(0);

        assertThat(doc.address()).isEqualTo(address("/alpha"));
        assertThat(doc.type()).isEqualTo(postTypeName);
        assertThat(doc.state()).isEqualTo("DRAFT");
        assertThat(doc.folder()).isEqualTo(base);
        assertThat(doc.data().get("headline").asString()).isEqualTo("Alpha");
        // The SH78 standing rule, asserted on the shape rather than trusted.
        String json = objectMapper.writeValueAsString(doc);
        assertThat(json).doesNotContain("owner").doesNotContain("publishStatus").doesNotContain("date");
    }

    @Test
    void readTakesSeveralAddressesInOneCall() {
        List<ShAgentDoc> docs = shAgentReadService.read(
                List.of(address("/alpha"), address("/beta")), null, false);

        assertThat(docs).extracting(ShAgentDoc::address)
                .containsExactly(address("/alpha"), address("/beta"));
    }

    // --- SH172: an address names one post, or it is an id ---

    @Test
    @DisplayName("a post with no friendly URL is addressed by id, not as post:<site>/")
    void aUrllessPostFallsBackToItsId() {
        // How they get there: a File post, or any post created outside the address vocabulary.
        // Three of them used to come back from find as three rows carrying the *same* address.
        List<String> ids = new java.util.ArrayList<>();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (int i = 0; i < 2; i++) {
                ShPostUnified post = new ShPostUnified();
                post.setId(UUID.randomUUID().toString());
                post.setState(ShPostState.DRAFT);
                post.setTitle("no-url-" + i);
                post.setData("{\"headline\":\"no-url-" + i + "\"}");
                // Repositories, not JPQL: `f.shSite.name` would navigate past the id of an
                // ShObject subclass and Hibernate would emit the @TenantId predicate against
                // the subclass table (the SH122 hazard agents.md documents).
                ShSite site = shSiteRepository.findByName(siteName);
                post.setShSite(site);
                post.setShFolder(shFolderRepository.findByShSiteAndRootFolder(site, (byte) 1)
                        .stream().findFirst().orElseThrow());
                post.setShPostType(shPostTypeRepository.findByName(postTypeName));
                post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
                post.setOwner("tester");
                entityManager.persist(post);
                ids.add(post.getId());
            }
        });

        ShAgentFound found = shAgentReadService.find(siteName, postTypeName, null, null, 50, false);
        List<String> urlless = found.results().stream()
                .map(ShAgentFound.Row::address)
                .filter(address -> address.startsWith("id:"))
                .toList();

        assertThat(urlless)
                .as("each URL-less post must name itself — the id is the only thing that does")
                .hasSize(2)
                .doesNotHaveDuplicates()
                .allSatisfy(address -> assertThat(ids).contains(address.substring("id:".length())));
        assertThat(found.notes())
                .as("and the response says the address is a fallback rather than a route")
                .isNotNull()
                .anySatisfy(note -> assertThat(note).contains("no friendly URL"));
    }

    // --- SH171: a multi-address read is not all-or-nothing ---

    @Test
    @DisplayName("one unresolvable address does not take the whole batch read down")
    void abadAddressFailsItsOwnRowOnly() {
        List<ShAgentDoc> docs = shAgentReadService.read(
                List.of(address("/alpha"), address("/no-such-post"), address("/beta")), null, false);

        // Every row the caller asked for comes back, positionally, like /agent/batch's results.
        assertThat(docs).hasSize(3);
        assertThat(docs.get(0).data()).isNotNull();
        assertThat(docs.get(2).data()).isNotNull();

        ShAgentDoc failed = docs.get(1);
        assertThat(failed.address()).isEqualTo(address("/no-such-post"));
        assertThat(failed.data()).isNull();
        assertThat(failed.error()).isNotNull();
        assertThat(failed.error().fix())
                .as("the row teaches its own failure, so the agent does not re-read one by one")
                .isNotBlank();
    }

    @Test
    @DisplayName("a single-address read still 404s — callers depend on that")
    void aSingleBadAddressStillThrows() {
        assertThatThrownBy(() -> shAgentReadService.read(List.of(address("/no-such-post")), null, false))
                .isInstanceOf(ShTeachingException.class);
    }

    // --- SH123: projection ---

    @Test
    void fieldsProjectsDownToWhatWasAskedFor() {
        ShAgentDoc doc = shAgentReadService
                .read(List.of(address("/alpha")), List.of("headline"), false).get(0);

        assertThat(doc.data().propertyNames()).containsExactly("headline");
        // Saying which fields came back removes the "is this all of it?" round trip.
        assertThat(doc.fields()).containsExactly("headline");
    }

    @Test
    void aProjectionIsMateriallyCheaperThanTheWholeDocument() {
        int full = ShTokenBudget.estimate(ShAgentReadTerse.docs(
                shAgentReadService.read(List.of(address("/alpha")), null, false)));
        int projected = ShTokenBudget.estimate(ShAgentReadTerse.docs(
                shAgentReadService.read(List.of(address("/alpha")), List.of("headline"), false)));

        assertThat(projected).isLessThan(full);
    }

    @Test
    void projectingAnUnknownFieldIsRefusedRatherThanAnsweredEmpty() {
        // The one way this feature could do real damage: an empty projection looks
        // exactly like an empty post, and the agent would overwrite content with
        // nothing. So a typo is a teaching error, not a result.
        assertThatThrownBy(() ->
                shAgentReadService.read(List.of(address("/alpha")), List.of("headine"), false))
                .isInstanceOf(ShAgentReadService.UnknownFieldException.class)
                .satisfies(thrown -> assertThat(
                        ((ShAgentReadService.UnknownFieldException) thrown)
                                .toProblemDetail().getProperties())
                        .containsEntry("didYouMean", "headline"));
    }

    @Test
    void aFieldTheTypeHasButThePostHasNotSetIsSimplyAbsent() {
        // Data, not a mistake — the distinction the previous test depends on.
        // Written outside `base` so it cannot perturb the find assertions, which are
        // counts over that folder and would otherwise depend on test order.
        String sparse = "post:" + siteName + "/sparse-" + UUID.randomUUID().toString().substring(0, 8);
        shAgentBatchService.run(new ShAgentBatch(false, List.of(
                new Op(ShAgentBatchService.POST_UPSERT, sparse, postTypeName, "/",
                        objectMapper.valueToTree(Map.of("headline", "Sparse"))))), false, "tester");

        ShAgentDoc doc = shAgentReadService
                .read(List.of(sparse), List.of("headline", "body"), false).get(0);

        assertThat(doc.data().propertyNames()).containsExactly("headline");
    }

    // --- SH619: the moment the read surface could not reach ---

    /**
     * The question that had no answer, asserted as two reads of one address.
     *
     * <p>Every agent read was draft-preferred and took no {@code state}, so the published row's
     * <b>fields</b> were unreachable: "what will change when I publish this" could not be assembled
     * at all — not expensively, not at all. The rendered live page was reachable, so a <i>pixel</i>
     * answer existed; the cheap one, which fields differ, existed only inside the console's own
     * diff dialog, one post at a time, in a browser, to a human.</p>
     *
     * <p>That dialog's task id is deliberately not named here: {@code assertion-debt.test.mjs}
     * counts any id appearing in a suite file, so writing it would mark it asserted by a sentence
     * that asserts nothing about it (SH641).</p>
     *
     * <p>Written as the comparison rather than as two separate reads, because the comparison is the
     * capability. It is also the argument for a parameter over a {@code diff} endpoint (P10): the
     * answer composes out of the read surface instead of becoming a third contract.</p>
     */
    @Test
    @DisplayName("SH619: draft against live is two reads of one address, and they differ")
    void aDraftCanBeComparedWithWhatIsLive() {
        // Outside `base`, for the reason the fixture states one method down: the find
        // assertions are counts over that folder, so a post written into it makes their answer
        // depend on test order. Worth stating rather than just obeying — the warning is in a
        // comment on one post, and the next writer reads the test above it instead.
        String address = "post:" + siteName + "/sh619-live-" + suffix();
        run(new Op(ShAgentBatchService.POST_UPSERT, address, postTypeName, "/",
                data("As published", "The body that went live")));
        run(new Op(ShAgentBatchService.POST_PUBLISH, address, null, null, null));
        run(new Op(ShAgentBatchService.POST_UPSERT, address, postTypeName, "/",
                data("Edited since", "The body nobody has released")));

        ShAgentDoc draft = read(address, null);
        ShAgentDoc live = read(address, "published");

        assertThat(draft.data().get("headline").asString()).isEqualTo("Edited since");
        assertThat(live.data().get("headline").asString())
                .as("""
                        the live row's FIELDS, which nothing on this surface could return. SH545                         added `published`, and that answers WHETHER a live row exists — the right                         field for that question and a different one from this (SH619).""")
                .isEqualTo("As published");
        assertThat(live.state()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("SH991: a schedule an agent set can be listed and read back, and an unscheduled row is unchanged")
    void aScheduleCanBeConfirmed() {
        String marker = "sh991sched" + suffix();
        String address = "post:" + siteName + "/" + marker;
        run(new Op(ShAgentBatchService.POST_UPSERT, address, postTypeName, "/",
                data("Scheduled " + marker, "Later")));
        String quiet = "post:" + siteName + "/" + marker + "-quiet";
        run(new Op(ShAgentBatchService.POST_UPSERT, quiet, postTypeName, "/",
                data("Unscheduled " + marker, "Never")));
        String postId = shAgentReadService.read(List.of(address), null, true).get(0).id();
        java.time.Instant goesLive = java.time.Instant.parse("2031-05-01T09:00:00Z");
        shPostUnifiedWriteService.schedule(postId, goesLive, null, "tester");

        ShAgentFound any = shAgentReadService.find(siteName, null, null, marker, null, false, null,
                false, null, "any");
        assertThat(any.results())
                .extracting(ShAgentFound.Row::address, ShAgentFound.Row::publishAt)
                .as("SH991: the confirmation an agent could not get — the listing names the instant")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(address, goesLive.toString()));
        assertThat(shAgentReadService.find(siteName, null, null, marker, null, false, null, false,
                null, "2031-05-01T00:00:00Z..2031-05-02T00:00:00Z").results()).hasSize(1);
        assertThat(shAgentReadService.find(siteName, null, null, marker, null, false, null, false,
                null, "..2031-05-01T09:00:00Z").results())
                .as("SH991: the upper bound is exclusive, so a range can be walked without overlap")
                .isEmpty();
        assertThat(shAgentReadService.find(siteName, null, null, marker, null, false, null, false,
                null, null).results())
                .as("SH991: without the filter no row carries an instant, so a listing is byte-identical")
                .hasSize(2)
                .allSatisfy(row -> assertThat(row.publishAt()).isNull());

        ShAgentDoc doc = shAgentReadService.read(List.of(address), null, false, false, null, false,
                false, false, true).get(0);
        assertThat(doc.schedule())
                .as("SH991: the schedule names who set it, for the Activity row the sweep writes later")
                .isEqualTo(new ShAgentDoc.Schedule(goesLive.toString(), null, "tester"));
        assertThat(shAgentReadService.read(List.of(address), null, false, false, null, false,
                false, false, false).get(0).schedule())
                .as("SH991: opt-in, like every other rider on this read")
                .isNull();
        assertThatThrownBy(() -> shAgentReadService.find(siteName, null, null, marker, null, false,
                null, false, null, "next week"))
                .isInstanceOf(com.viglet.shio.api.post.ShScheduleWindow.UnknownWindowException.class);
    }

    @Test
    @DisplayName("SH990: find lists the rows of one state, each with its own title")
    void findListsOneState() {
        String marker = "sh990find" + suffix();
        String address = "post:" + siteName + "/" + marker;
        run(new Op(ShAgentBatchService.POST_UPSERT, address, postTypeName, "/",
                data("Live " + marker, "Released")));
        run(new Op(ShAgentBatchService.POST_PUBLISH, address, null, null, null));
        run(new Op(ShAgentBatchService.POST_UPSERT, address, postTypeName, "/",
                data("Edited " + marker, "Not yet")));

        assertThat(shAgentReadService.find(siteName, null, null, marker, null, false, null, false,
                "published").results())
                .extracting(ShAgentFound.Row::title, ShAgentFound.Row::state)
                .as("SH990: the live row's title — which pages a publish would change is a listing")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Live " + marker, "PUBLISHED"));
        assertThat(shAgentReadService.find(siteName, null, null, marker, null, false, null, false,
                "draft").results())
                .extracting(ShAgentFound.Row::title)
                .containsExactly("Edited " + marker);
        assertThat(shAgentReadService.find(siteName, null, null, marker, null, false, null, false,
                null).results())
                .as("SH990: no state is one row per post, draft-preferred, as it always was")
                .hasSize(1);
        assertThatThrownBy(() -> shAgentReadService.find(siteName, null, null, marker, null, false,
                null, false, "rev:1"))
                .isInstanceOf(com.viglet.shio.api.post.ShContentPerspective.RevisionNotServedException.class);
    }

    @Test
    @DisplayName("SH619: state=draft is the editable copy even when a published row exists")
    void draftIsAddressableInItsOwnRight() {
        String address = "post:" + siteName + "/sh619-explicit-" + suffix();
        run(new Op(ShAgentBatchService.POST_UPSERT, address, postTypeName, "/",
                data("First", "First body")));
        run(new Op(ShAgentBatchService.POST_PUBLISH, address, null, null, null));

        assertThat(read(address, "draft").state()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("SH619: asking for a row the post has not got says which, and does not fall back")
    void anAbsentRowIsA404ThatSaysWhich() {
        // The fallback is the tempting answer and the single worst one available: a caller
        // comparing draft against live would be handed the draft twice and conclude nothing
        // changed — which is the exact wrong answer this capability exists to stop being
        // unavoidable.
        String address = "post:" + siteName + "/sh619-draft-only-" + suffix();
        run(new Op(ShAgentBatchService.POST_UPSERT, address, postTypeName, "/",
                data("Only a draft", "Never released")));

        assertThatThrownBy(() -> read(address, "published"))
                .isInstanceOf(ShAgentReadService.NoSuchStateException.class)
                .satisfies(thrown -> assertThat(thrown.getMessage()).contains("published"));
    }

    @Test
    @DisplayName("SH619: an unknown state is refused with the two that exist")
    void anUnknownStateIsRefused() {
        // P6, and not pedantry: falling back to the draft-preferred default on a typo answers a
        // question the caller did not ask, in a shape indistinguishable from a correct answer.
        assertThatThrownBy(() -> read("post:" + siteName + base + "/alpha", "live"))
                .isInstanceOf(com.viglet.shio.api.post.ShContentPerspective.UnknownStateException.class);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void run(Op op) {
        shAgentBatchService.run(new ShAgentBatch(false, List.of(op)), false, "tester");
    }

    private ShAgentDoc read(String address, String state) {
        return shAgentReadService.read(List.of(address), null, false, false, state).get(0);
    }

    // --- teaching errors ---

    @Test
    void readingAFolderExplainsWhatToUseInstead() {
        assertThatThrownBy(() -> shAgentReadService
                .read(List.of("folder:" + siteName + base), null, false))
                .isInstanceOf(ShAgentReadService.NotAPostException.class)
                .satisfies(thrown -> assertThat(thrown.getMessage()).contains("folder"));
    }

    @Test
    void readingNothingSaysHowToFindAnAddress() {
        assertThatThrownBy(() -> shAgentReadService.read(List.of(), null, false))
                .isInstanceOf(ShAgentReadService.NoAddressException.class);
    }
}
