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

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentDoc;
import com.viglet.shio.api.agent.dto.ShAgentFound;
import com.viglet.shio.api.cda.ShCdaService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH545 — the probe, as an assertion.
 *
 * <h2>Why the probe and not a unit test of the field</h2>
 *
 * <p>Three reads disagreed and only one was wrong. A page answered 404 on the public route, was
 * published, and answered 200 — so the publish landed. Publishing it again answered {@code noop} —
 * so the server knew. And {@code /agent/read} and {@code /agent/find} reported {@code state: DRAFT}
 * for it, for every page of the site converted beside it, and for fixtures that had been serving
 * publicly for weeks. A test of the field alone cannot tell a <em>misreported state</em> from a
 * <em>route that serves drafts</em>; only running the whole sequence can, which is why the sequence
 * is what is asserted here. {@link ShCdaService} with no token reads {@code PUBLISHED} only (SH5),
 * so it is the 404/200 at the service boundary.</p>
 *
 * <h2>What `state` means, and why it could not be fixed in place</h2>
 *
 * <p>{@code publish()} writes a second row and leaves the draft alone, and every agent read is
 * DRAFT-preferred so the agent can see its own unpublished work. So {@code state} is <b>which row
 * this is</b> — a true and nearly useless fact — and the question a curator cares about had no word
 * at all. Repointing {@code state} at publication would have made it lie about where {@code data}
 * came from, so the fix is a second field: after publishing, {@code state} is still {@code DRAFT}
 * and that is correct.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-published-state;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class ShAgentPublishedStateTest {

    private static final String SITE = "Sh545Site";
    private static final String ADDRESS = "post:" + SITE + "/about";
    private static final String URL = "/about";

    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShAgentReadService shAgentReadService;
    @Autowired
    private ShCdaService shCdaService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteId;

    @BeforeAll
    void seedASiteWithOneUnpublishedPage() {
        siteId = new TransactionTemplate(transactionManager).execute(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(SITE);
            site.setDate(when);
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName(SITE);
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType type = new ShPostType();
            type.setName("Sh545Page");
            type.setTitle("Sh545 Page");
            type.setSystem((byte) 0);
            type.setDate(when);
            type.setOwner("admin");
            entityManager.persist(type);

            ShPostUnified draft = new ShPostUnified();
            draft.setId("sh545-about");
            draft.setState(ShPostState.DRAFT);
            draft.setShSite(site);
            draft.setShFolder(root);
            draft.setShPostType(type);
            draft.setTitle("About");
            draft.setFurl(URL);
            draft.setLocale("en");
            draft.setOwner("admin");
            draft.setDate(when);
            draft.setData("{\"title\":\"About\"}");
            entityManager.persist(draft);
            return site.getId();
        });
    }

    @Test
    @Order(1)
    @DisplayName("SH545: before the publish the route serves nothing and no read claims it is live")
    void anUnpublishedPageReadsAsUnpublished() {
        assertThat(shCdaService.getPostByUrl(siteId, URL))
                .as("the public route has nothing to serve — this is the 404 half of the probe")
                .isEmpty();

        assertThat(readOne().published())
                .as("/agent/read must not report a page as live before it is — and absence is the "
                        + "answer, grounded in the pack's own first convention (writes land in DRAFT)")
                .isNull();
        assertThat(findOne().published()).isNull();
        assertThat(readWithLinks().liveUrl())
                .as("SH1304: no published address for a page nobody can see — it would only be a 404")
                .isNull();
        assertThat(readWithLinks().previewUrl())
                .as("while the curator's preview answers for the draft, which is what it is for")
                .isEqualTo("/preview/" + SITE + URL);
    }

    @Test
    @Order(2)
    @DisplayName("SH545: after the publish the route serves it and both reads say published=true")
    void aPublishedPageReadsAsPublished() {
        var changes = shAgentBatchService.run(
                new ShAgentBatch(false, List.of(publish())), false, "ut-sh545");
        assertThat(changes.changed()).as("the publish landed").isEqualTo(1);

        assertThat(shCdaService.getPostByUrl(siteId, URL))
                .as("the public route serves it now — the 200 half of the probe, and the half that "
                        + "proves the route is not simply serving drafts")
                .isPresent();

        ShAgentDoc doc = readOne();
        assertThat(doc.published())
                .as("this is the whole of SH545: before it, every published page on every site "
                        + "reported DRAFT and an agent could confirm a publish only by calling the "
                        + "public route and reading the state off an HTTP code")
                .isTrue();
        assertThat(doc.state())
                .as("and `state` is still DRAFT, correctly: publish keeps the draft and the read "
                        + "prefers it, which is exactly why one word could not answer both questions")
                .isEqualTo(ShPostState.DRAFT.name());
        assertThat(findOne().published())
                .as("/agent/find agrees, and pays no query for it — the walk already fetched both "
                        + "state rows and dedupe was discarding the published one")
                .isTrue();
        assertThat(readWithLinks().liveUrl())
                .as("SH1304: once live, `links` carries where a visitor sees it — /sites/**, the route "
                        + "that serves PUBLISHED alone, and not the banner-wrapped preview")
                .isEqualTo("/sites/" + SITE + "/default/en-us" + URL);
        assertThat(doc.liveUrl())
                .as("and only when asked: a read without `links` is byte-identical to the one before")
                .isNull();
        assertThat(ShAgentReadTerse.docs(List.of(readWithLinks())))
                .as("SH1304: the terse line carries it too, beside the preview it is the other half of")
                .contains("live=/sites/" + SITE + "/default/en-us" + URL);
    }

    @Test
    @Order(3)
    @DisplayName("SH545: a repeat publish is a noop, and the read still says published=true")
    void aRepeatPublishIsANoopAndTheStateSurvivesIt() {
        var changes = shAgentBatchService.run(
                new ShAgentBatch(false, List.of(publish())), false, "ut-sh545");

        assertThat(changes.results().getFirst().toString())
                .as("the server knows it is already published — the third read in the probe, and "
                        + "the one that proves the disagreement was in the report, not the write")
                .contains("noop");
        assertThat(readOne().published()).isTrue();
    }

    @Test
    @Order(4)
    @DisplayName("SH545: the terse rendering carries it too, next to the state it disambiguates")
    void theTerseRenderingCarriesPublication() {
        String terse = ShAgentReadTerse.docs(List.of(readOne()));

        assertThat(terse)
                .as("the model reads the terse form; a field only the JSON carries is not on the "
                        + "surface the tokens are spent on. A bare marker, like `truncated` — the "
                        + "JSON omits it for a draft too, so the two forms agree")
                .contains("state=DRAFT published");
        assertThat(ShAgentReadTerse.found(shAgentReadService.find(SITE, null, null, null, 10, false)))
                .contains("published");
    }

    @Test
    @Order(5)
    @DisplayName("SH545: unpublishing is visible the same way")
    void withdrawingAPageReportsItAgain() {
        shAgentBatchService.run(new ShAgentBatch(false, List.of(
                new ShAgentBatch.Op(ShAgentBatchService.POST_UNPUBLISH, ADDRESS,
                        null, null, null, null))), false, "ut-sh545");

        assertThat(shCdaService.getPostByUrl(siteId, URL))
                .as("the route stops serving it")
                .isEmpty();
        assertThat(readOne().published())
                .as("and the read says so, which is the same defect facing the other way: a "
                        + "withdrawal an agent cannot read back is one it cannot confirm either")
                .isNull();
    }

    private ShAgentBatch.Op publish() {
        return new ShAgentBatch.Op(ShAgentBatchService.POST_PUBLISH, ADDRESS,
                null, null, null, null);
    }

    private ShAgentDoc readOne() {
        return shAgentReadService.read(List.of(ADDRESS), List.of(), false).getFirst();
    }

    private ShAgentDoc readWithLinks() {
        return shAgentReadService.read(List.of(ADDRESS), List.of(), false, false, null, false, true)
                .getFirst();
    }

    private ShAgentFound.Row findOne() {
        ShAgentFound found = shAgentReadService.find(SITE, null, null, null, 10, false);
        return found.results().stream()
                .filter(row -> ADDRESS.equals(row.address()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("find did not return " + ADDRESS));
    }
}
