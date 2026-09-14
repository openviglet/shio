/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.api.acl.ShContentAclService.AclEntry;
import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.ShAgentChangeCursor;
import com.viglet.shio.api.agent.ShAgentChangesService;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChanges;
import com.viglet.shio.api.events.ShEventSink;
import com.viglet.shio.api.events.ShEventStreamHub;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.acl.ShAclPermission;
import com.viglet.shio.persistence.model.acl.ShAclPrincipalType;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/**
 * SH969 — a scheduled transition's outcome reaches the feed a curator's screen reads, and a failure
 * says how to try again.
 *
 * <p>The failure is planted the way one happens: a page scheduled to go live at an address another
 * page still serves, so the publish breaks the database's own uniqueness guard (SH719) inside the
 * sweep. Nothing is mocked, which keeps this class in the shared context.</p>
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShScheduleOutcomeFeedTest {

    @Autowired
    private ShPostScheduler scheduler;
    @Autowired
    private ShPostUnifiedWriteService writeService;
    @Autowired
    private ShPostUnifiedRepository postRepository;
    @Autowired
    private ShAgentBatchService batchService;
    @Autowired
    private ShAgentChangesService changesService;
    @Autowired
    private ShAgentIncidentLog incidentLog;
    @Autowired
    private ShEventStreamHub hub;
    @Autowired
    private ShContentAclService aclService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String siteId;
    private String postTypeName;
    private final List<String> opened = new ArrayList<>();

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            siteName = "sh969-" + UUID.randomUUID().toString().substring(0, 8);
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();

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
            postTypeName = "Sh969Page" + UUID.randomUUID().toString().substring(0, 8);
            postType.setName(postTypeName);
            postType.setTitle("SH969 Page");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setName("headline");
            attr.setLabel("Headline");
            attr.setOrdinal(1);
            attr.setIsTitle((byte) 1);
            attr.setShWidget(widget);
            attr.setShPostType(postType);
            entityManager.persist(attr);
        });
    }

    @AfterEach
    void cleanUp() {
        opened.forEach(hub::close);
        opened.clear();
        SecurityContextHolder.clearContext();
    }

    private static String now() {
        return new ShAgentChangeCursor(Instant.now(), "").toString();
    }

    private String address(String url) {
        return "post:" + siteName + "/" + url;
    }

    private String upsert(String url, String folder) {
        return batchService.run(new ShAgentBatch(false, List.of(new ShAgentBatch.Op(
                ShAgentBatchService.POST_UPSERT, address(url), postTypeName, folder,
                objectMapper.valueToTree(Map.of("headline", url))))), true, "sh969-agent")
                .results().get(0).id();
    }

    private void run(String op, String address) {
        batchService.run(new ShAgentBatch(false, List.of(new ShAgentBatch.Op(op, address, null, null,
                null))), false, "curator");
    }

    private void moveDraft(String id, String furl) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var draft = postRepository.findByIdAndState(id, ShPostState.DRAFT).orElseThrow();
            draft.setFurl(furl);
            postRepository.save(draft);
        });
    }

    private void dueNow(String id) {
        writeService.schedule(id, Instant.now().minusSeconds(5), null, "curator");
    }

    private ShAgentChanges feedSince(String cursor) {
        return changesService.changes(siteName, cursor, 100, true, 0);
    }

    private static List<ShAgentChanges.Incident> incidentsAbout(ShAgentChanges feed, String id) {
        return feed.incidents() == null ? List.of()
                : feed.incidents().stream().filter(i -> ("id:" + id).equals(i.subject())).toList();
    }

    @Test
    void sh969AFailedScheduledPublishCarriesARetryAndTheRetryPublishesOnce() {
        String taken = "taken-" + UUID.randomUUID().toString().substring(0, 8);
        String live = upsert(taken, "/");
        run(ShAgentBatchService.POST_PUBLISH, address(taken));
        // The live page's draft moves on; its published row still holds the address.
        moveDraft(live, "/" + taken + "-moved");

        String waiting = upsert("waiting-" + UUID.randomUUID().toString().substring(0, 8), "/");
        moveDraft(waiting, "/" + taken);
        dueNow(waiting);

        String beforeSweep = now();
        scheduler.sweep();

        List<ShAgentChanges.Incident> failures = incidentsAbout(feedSince(beforeSweep), waiting);
        assertThat(failures)
                .as("SH969: the publish that could not happen is in the feed a curator's screen reads,"
                        + " not only in the agent's diagnostics")
                .isNotEmpty();
        ShAgentChanges.Incident failure = failures.get(0);
        assertThat(failure.kind()).isEqualTo("schedule");
        assertThat(failure.retry())
                .as("SH969: a failed scheduled publish says how to try again — the batch op and the"
                        + " address the post has now, so the retry is an ordinary publish")
                .isEqualTo(new ShAgentChanges.Retry(ShAgentBatchService.POST_PUBLISH, address(taken)));

        // The cause goes away, and the curator presses retry.
        writeService.unpublish(live, "curator");
        run(failure.retry().op(), failure.retry().address());

        assertThat(postRepository.findByIdAndState(waiting, ShPostState.PUBLISHED))
                .as("SH969: the retry publishes")
                .isPresent();
        assertThat(postRepository.findByIdAndState(waiting, ShPostState.DRAFT).orElseThrow()
                .getPublishScheduledDate())
                .as("SH969: a publish after the scheduled instant is the scheduled publish, done — a"
                        + " schedule left due would fire it again on the next sweep")
                .isNull();

        String beforeSecondSweep = now();
        scheduler.sweep();
        assertThat(feedSince(beforeSecondSweep).changes())
                .as("SH969: the sweep after a retry publishes nothing a second time, and sends no"
                        + " second webhook for the same page")
                .noneMatch(change -> "PUBLISH".equals(change.action())
                        && address(taken).equals(change.address()));
    }

    @Test
    void sh969AScheduledPublishThatWorksIsAFeedEntryByTheScheduler() {
        String url = "on-time-" + UUID.randomUUID().toString().substring(0, 8);
        String id = upsert(url, "/");
        dueNow(id);

        String beforeSweep = now();
        scheduler.sweep();
        ShAgentChanges feed = feedSince(beforeSweep);

        assertThat(feed.changes())
                .as("SH969: a launch that happened is confirmed where the curator looks, naming the"
                        + " page and when, rather than by opening the page")
                .anySatisfy(change -> {
                    assertThat(change.action()).isEqualTo("PUBLISH");
                    assertThat(change.address()).isEqualTo(address(url));
                    assertThat(change.by()).isEqualTo("scheduler");
                    assertThat(change.at()).isNotBlank();
                });
        assertThat(incidentsAbout(feed, id)).isEmpty();
    }

    @Test
    void sh969AScheduleFailureOnAPostTheReaderCannotReadIsWithheldFromTheirStream() {
        String folder = "private-" + UUID.randomUUID().toString().substring(0, 8);
        String folderId = batchService.run(new ShAgentBatch(false, List.of(new ShAgentBatch.Op(
                ShAgentBatchService.FOLDER_UPSERT, "folder:" + siteName + "/" + folder, null, null,
                null))), true, "sh969-agent").results().get(0).id();
        String url = "secret-" + UUID.randomUUID().toString().substring(0, 8);
        String secret = upsert(url, "/" + folder);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> aclService.replaceAcl(
                folderId, List.of(new AclEntry(ShAclPrincipalType.USER, "bob", ShAclPermission.READ))));

        try {
            String since = now();
            RecordingSink alice = open("alice", since);
            RecordingSink bob = open("bob", since);
            SecurityContextHolder.clearContext();
            incidentLog.record(ShAgentIncidentLog.Kind.SCHEDULE, "id:" + secret,
                    ShPostScheduler.PUBLISH_FAILED + "planted by SH969", null, null, siteId);
            pumpUntilDelivered(bob, secret);

            assertThat(alice.incidents())
                    .as("SH969: a failure about a post alice's ACL excludes does not reach her stream —"
                            + " its retry carries the address the change rows were withheld for")
                    .noneMatch(incident -> ("id:" + secret).equals(incident.subject()));
            assertThat(bob.incidents())
                    .as("SH969: and the reader the folder grants still gets it, retry included")
                    .anySatisfy(incident -> assertThat(incident.retry())
                            .isEqualTo(new ShAgentChanges.Retry(ShAgentBatchService.POST_PUBLISH,
                                    address(url))));
        } finally {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    aclService.replaceAcl(folderId, List.of()));
        }
    }

    /**
     * Passes until the planted failure reaches this stream, or gives up after a second.
     *
     * <p><b>Not a retry around a flaky assertion.</b> The feed holds a row back while any write that
     * stamped earlier is still running (SH147), so a pass taken in that window carries nothing and
     * leaves the cursor where it was — which is the guarantee, and the reason the next pass carries
     * it. A single pass was enough on a quiet instance and not in the full suite, where another test's
     * commit is in flight often enough to matter. Alice's assertion runs after this returns, so she has
     * had exactly the passes bob needed: "withheld" is measured against a stream that did receive it.</p>
     */
    private void pumpUntilDelivered(RecordingSink sink, String postId) {
        for (int pass = 0; pass < 20; pass++) {
            hub.pump();
            if (sink.incidents().stream().anyMatch(i -> ("id:" + postId).equals(i.subject()))) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private RecordingSink open(String username, String since) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", List.of()));
        RecordingSink sink = new RecordingSink();
        opened.add(hub.open(sink, since));
        return sink;
    }

    /** Every incident a stream received. */
    private static final class RecordingSink implements ShEventSink {

        private final List<ShEventStreamHub.Page> pages = new ArrayList<>();

        @Override
        public void send(String id, String name, Object data) {
            pages.add((ShEventStreamHub.Page) data);
        }

        @Override
        public void heartbeat() {
            // An idle pass may send one; these tests read pages.
        }

        @Override
        public void close() {
            // Only a failed pass closes a sink, and none is provoked here.
        }

        List<ShAgentChanges.Incident> incidents() {
            return pages.stream().filter(page -> page.incidents() != null)
                    .flatMap(page -> page.incidents().stream()).toList();
        }
    }
}
