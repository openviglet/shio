/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.api.acl.ShContentAclService.AclEntry;
import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.ShAgentChangeCursor;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChanges;
import com.viglet.shio.persistence.model.acl.ShAclPermission;
import com.viglet.shio.persistence.model.acl.ShAclPrincipalType;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH967 — the change feed reaches a screen that is open, and only what that screen may see.
 *
 * <p>The three assertions §LXIII.4 named, each at the hub rather than through a held HTTP
 * connection: a write reaches an open stream, an entry the reader cannot see is withheld, and a
 * reconnect from the last event's id loses nothing. The hub is driven with {@link ShEventStreamHub#pump()}
 * under {@code shio.events.enabled=false} (the test properties), so no background pass races the
 * assertion. The last test is the HTTP boundary: what the endpoint answers before any event.</p>
 *
 * <p>Each stream is opened at an explicit cursor just before its writes rather than at the edge of
 * the feed. The edge is the newest row in the shared datasource, and a class that wrote a row with a
 * fixed future instant would put it past every write this class makes.</p>
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShEventStreamTest {

    @Autowired
    private ShEventStreamHub hub;
    @Autowired
    private ShAgentBatchService batchService;
    @Autowired
    private ShContentAclService aclService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private WebApplicationContext webApplicationContext;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private final List<String> opened = new ArrayList<>();

    /** Every event a stream received, in order. */
    private static final class RecordingSink implements ShEventSink {

        final List<String> ids = new ArrayList<>();
        final List<ShEventStreamHub.Page> pages = new ArrayList<>();

        @Override
        public void send(String id, String name, Object data) {
            assertThat(name).isEqualTo(ShEventStreamHub.CHANGES);
            ids.add(id);
            pages.add((ShEventStreamHub.Page) data);
        }

        @Override
        public void heartbeat() {
            // Not what these tests are about; a pass that finds nothing may send one.
        }

        @Override
        public void close() {
            // The hub closes a sink only after a failed pass, which these tests do not provoke.
        }

        List<String> addresses() {
            return pages.stream().flatMap(page -> page.changes().stream())
                    .map(ShAgentChanges.Change::address).toList();
        }
    }

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            siteName = "sh967-" + UUID.randomUUID().toString().substring(0, 8);
            ShSite site = new ShSite();
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
        });
    }

    @AfterEach
    void closeStreams() {
        opened.forEach(hub::close);
        opened.clear();
        SecurityContextHolder.clearContext();
    }

    private void authAs(String username, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, "x",
                java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList()));
    }

    /** A cursor at this instant, so the stream reports what is written from here on. */
    private static String now() {
        return new ShAgentChangeCursor(Instant.now(), "").toString();
    }

    private String open(RecordingSink sink, String since) {
        String id = hub.open(sink, since);
        opened.add(id);
        return id;
    }

    /**
     * Creates a folder the way an agent does, with no authentication on the writing thread, and
     * returns its id.
     */
    private String writeFolder(String path) {
        SecurityContextHolder.clearContext();
        return batchService.run(new ShAgentBatch(false, List.of(new ShAgentBatch.Op(
                ShAgentBatchService.FOLDER_UPSERT, "folder:" + siteName + "/" + path, null, null,
                null))), true, "sh967-agent").results().get(0).id();
    }

    @Test
    void sh967AWriteReachesAnOpenStream() {
        authAs("curator", "ROLE_ADMIN");
        RecordingSink sink = new RecordingSink();
        open(sink, now());

        String folder = "reaches-" + UUID.randomUUID().toString().substring(0, 8);
        writeFolder(folder);
        hub.pump();

        assertThat(sink.addresses())
                .as("SH967: an agent's write has to reach a screen that is already open — the whole"
                        + " defect was a curator seeing nothing until they navigated")
                .contains("folder:" + siteName + "/" + folder);
        assertThat(sink.pages).allSatisfy(page -> assertThat(page.changes())
                .allSatisfy(change -> assertThat(change.id())
                        .as("SH967: the id is read to apply the ACL and never leaves the stream")
                        .isNull()));
        assertThat(sink.ids).allSatisfy(id -> ShAgentChangeCursor.parse(id));

        int before = sink.pages.size();
        hub.pump();
        assertThat(sink.pages)
                .as("SH967: a second pass with nothing new sends nothing, or a page repeats")
                .hasSize(before);
    }

    @Test
    void sh967AnEntryTheReaderCannotSeeIsWithheld() {
        String restricted = "restricted-" + UUID.randomUUID().toString().substring(0, 8);
        String restrictedId = writeFolder(restricted);
        assertThat(restrictedId).as("the batch reports the folder it created").isNotBlank();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> aclService.replaceAcl(
                restrictedId, List.of(new AclEntry(ShAclPrincipalType.USER, "bob", ShAclPermission.READ))));

        try {
            authAs("alice");
            RecordingSink alice = new RecordingSink();
            String since = now();
            open(alice, since);
            authAs("bob");
            RecordingSink bob = new RecordingSink();
            open(bob, since);

            String inside = restricted + "/child-" + UUID.randomUUID().toString().substring(0, 8);
            String outside = "open-" + UUID.randomUUID().toString().substring(0, 8);
            writeFolder(inside);
            writeFolder(outside);
            hub.pump();

            assertThat(alice.addresses())
                    .as("SH967: a folder under an ACL that grants only bob must not reach alice's"
                            + " screen — the stream reads with the subscriber's rights, and the feed"
                            + " alone applies only site scope")
                    .doesNotContain("folder:" + siteName + "/" + inside)
                    .contains("folder:" + siteName + "/" + outside);
            assertThat(bob.addresses())
                    .as("SH967: and the reader it is granted to still receives it, or 'withheld' is"
                            + " just 'broken'")
                    .contains("folder:" + siteName + "/" + inside);
        } finally {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    aclService.replaceAcl(restrictedId, List.of()));
        }
    }

    @Test
    void sh967AReconnectFromTheLastEventIdLosesNothing() {
        authAs("curator", "ROLE_ADMIN");
        RecordingSink first = new RecordingSink();
        String firstStream = open(first, now());

        String a = "resume-a-" + UUID.randomUUID().toString().substring(0, 8);
        writeFolder(a);
        hub.pump();
        assertThat(first.addresses()).contains("folder:" + siteName + "/" + a);
        String lastEventId = first.ids.get(first.ids.size() - 1);

        // The connection drops; two writes land while nobody is listening.
        hub.close(firstStream);
        String b = "resume-b-" + UUID.randomUUID().toString().substring(0, 8);
        String c = "resume-c-" + UUID.randomUUID().toString().substring(0, 8);
        writeFolder(b);
        writeFolder(c);

        authAs("curator", "ROLE_ADMIN");
        RecordingSink second = new RecordingSink();
        open(second, lastEventId);
        hub.pump();

        assertThat(second.addresses())
                .as("SH967: a reconnect sending Last-Event-ID must receive every write made while it"
                        + " was away, and not the one it had already been sent")
                .contains("folder:" + siteName + "/" + b, "folder:" + siteName + "/" + c)
                .doesNotContain("folder:" + siteName + "/" + a);
    }

    @Test
    void sh967AMalformedLastEventIdIsRefusedBeforeAStreamOpens() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        int openBefore = hub.openStreams();

        mockMvc.perform(get("/api/v2/events").header("Last-Event-ID", "not-a-cursor"))
                .andExpect(status().isBadRequest());
        assertThat(hub.openStreams())
                .as("SH967: a malformed position is the feed's own teaching error, said before a"
                        + " stream exists that nobody would be told about on the pump thread")
                .isEqualTo(openBefore);

        MvcResult result = mockMvc.perform(get("/api/v2/events").header("Last-Event-ID", now()))
                .andExpect(request().asyncStarted())
                .andReturn();
        assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
        assertThat(hub.openStreams())
                .as("SH967: a well-formed position opens exactly one stream")
                .isEqualTo(openBefore + 1);
    }
}
