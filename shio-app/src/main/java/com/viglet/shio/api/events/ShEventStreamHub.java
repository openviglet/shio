/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.events;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.api.agent.ShAgentChangeCursor;
import com.viglet.shio.api.agent.ShAgentChangesService;
import com.viglet.shio.api.agent.dto.ShAgentChanges;
import com.viglet.shio.tenant.ShTenantContext;
import com.viglet.shio.tenant.ShTenantHandoff;

import lombok.extern.slf4j.Slf4j;

/**
 * The change feed, pushed to the streams that are open (SH967).
 *
 * <h2>A shape over the feed, not a second feed</h2>
 *
 * <p>Every event is a page of {@link ShAgentChangesService} — the same rows, the same ordering
 * guard, the same site scope an agent's {@code GET /api/v2/agent/changes} reads (P10). The stream
 * adds three things and no others: a pump that reads for each open stream instead of waiting to be
 * polled, the content ACL (SH54) applied to each entry, and an SSE {@code id} that is the feed's own
 * cursor, so a reconnect sending {@code Last-Event-ID} resumes <em>exactly</em> where the last event
 * ended. A page is one event rather than an entry per event for that reason: the feed's cursor
 * positions a page, and an id that positioned less would have to be invented.</p>
 *
 * <h2>Whose rights a pass reads with</h2>
 *
 * <p>The pump runs on a scheduler thread, which has no request, no session and no tenant — and two
 * of the checks this depends on fail <em>open</em> on such a thread: the feed treats "no token" as
 * an unrestricted console read, and the ACL grants anything to no authentication. So the tenant and
 * the {@link Authentication} are captured on the request thread when the stream opens, and every
 * pass for that stream runs with both bound and both restored afterwards ({@link ShTenantHandoff}
 * for the tenant, which is what {@code ShOffThreadTenantLintTest} looks for). A pass never reads with
 * anybody's rights but the subscriber's.</p>
 *
 * <h2>What a reader cannot see is withheld, and the cursor still moves</h2>
 *
 * <p>An entry whose object the subscriber may not read is dropped from the page; the cursor
 * advances past it all the same, because a position that stopped at a withheld row would stop the
 * stream there for ever. A page whose every entry was withheld sends nothing, so a reconnect from
 * the last id the client saw re-reads those rows — and withholds them again.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Slf4j
@Component
public class ShEventStreamHub {

    /** The SSE event name for a page of the feed. */
    public static final String CHANGES = "changes";

    /** How an incident names a post it has only the id of. */
    private static final String ID_SUBJECT = "id:";

    private final ShAgentChangesService changesService;
    private final ShContentAclService aclService;
    private final ShEventsProperties properties;
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public ShEventStreamHub(ShAgentChangesService changesService, ShContentAclService aclService,
                            ShEventsProperties properties) {
        this.changesService = changesService;
        this.aclService = aclService;
        this.properties = properties;
    }

    /**
     * Opens a stream for the caller on the current request thread.
     *
     * @param sink  where this stream's events go
     * @param since a cursor to resume after — {@code Last-Event-ID} or {@code since=}; blank starts
     *              at the edge of the feed, so a new screen is told about what happens next rather
     *              than about everything that already did
     * @return the subscription's handle, for {@link #close(String)}
     */
    public String open(ShEventSink sink, String since) {
        return open(sink, since, null);
    }

    /**
     * As {@link #open(ShEventSink, String)}, scoped to one site the way {@code site=} scopes the
     * feed — which also refuses, here and not on the pump thread, a site the caller cannot read.
     */
    public String open(ShEventSink sink, String since, String site) {
        String scope = site == null || site.isBlank() ? null : site.trim();
        String cursor;
        if (since != null && !since.isBlank()) {
            // Refused here, with the feed's own teaching error, before any stream exists — a
            // malformed id discovered on the pump thread would have nobody to tell.
            ShAgentChangeCursor.parse(since);
            cursor = since.trim();
            if (scope != null) {
                changesService.changes(scope, cursor, 1, false, 0);
            }
        } else {
            cursor = changesService.changes(scope, null, 1, false, 0).cursor();
        }
        String id = UUID.randomUUID().toString();
        subscriptions.put(id, new Subscription(id, sink,
                SecurityContextHolder.getContext().getAuthentication(), ShTenantContext.getTenantId(),
                scope, cursor));
        return id;
    }

    /** Forgets a stream; the sink is not touched, since this is called when it already ended. */
    public void close(String id) {
        subscriptions.remove(id);
    }

    /** How many streams are open. */
    public int openStreams() {
        return subscriptions.size();
    }

    @Scheduled(fixedDelayString = "${shio.events.poll-millis:1000}")
    void scheduledPump() {
        if (properties.isEnabled() && !subscriptions.isEmpty()) {
            pump();
        }
    }

    /**
     * One pass over every open stream. Synchronised so the scheduler and a caller never run a pass
     * over the same stream at once, which would send its page twice.
     */
    public synchronized void pump() {
        // SH1077 — one memo per tenant per pass. Entries written into one folder share a chain, and
        // ten streams held by one curator share their principals, so what each stream used to re-read
        // is read once. Per tenant because every row it holds is tenant-scoped, and per pass because
        // a memo that lived longer would keep answering after an ACL changed.
        Map<String, ShContentAclService.ReadMemo> memos = new HashMap<>();
        for (Subscription subscription : List.copyOf(subscriptions.values())) {
            ShContentAclService.ReadMemo memo = memos.computeIfAbsent(
                    Objects.toString(subscription.tenant(), ""), tenant -> aclService.readMemo());
            ShTenantHandoff.runAs(subscription.tenant(),
                    () -> withAuthentication(subscription.authentication(),
                            () -> pass(subscription, memo)));
        }
    }

    private void pass(Subscription subscription, ShContentAclService.ReadMemo memo) {
        try {
            boolean sent = false;
            for (int page = 0; page < Math.max(1, properties.getMaxPagesPerPass()); page++) {
                ShAgentChanges changes = changesService.changes(subscription.site(),
                        subscription.cursor(), properties.getPageLimit(), true, 0);
                List<ShAgentChanges.Change> visible = changes.changes().stream()
                        .filter(change -> change.id() == null || aclService.canRead(change.id(), memo))
                        .map(ShEventStreamHub::withoutId)
                        .toList();
                List<ShAgentChanges.Incident> incidents = changes.incidents() == null
                        ? List.of()
                        : changes.incidents().stream()
                                .filter(incident -> readable(incident, memo)).toList();
                if (!visible.isEmpty() || !incidents.isEmpty()) {
                    subscription.sink().send(changes.cursor(), CHANGES,
                            new Page(visible, incidents.isEmpty() ? null : incidents));
                    sent = true;
                }
                subscription.moveTo(changes.cursor());
                if (!changes.more()) {
                    break;
                }
            }
            if (sent) {
                subscription.touch();
            } else if (subscription.idleMillis() >= properties.getHeartbeatMillis()) {
                subscription.sink().heartbeat();
                subscription.touch();
            }
        } catch (IOException gone) {
            // The client went away between two passes, which is the normal end of a stream.
            subscriptions.remove(subscription.id());
        } catch (RuntimeException failure) {
            // Closed rather than retried: a pass that fails every second logs every second, and the
            // client reconnects with Last-Event-ID — which re-checks the session, the one thing a
            // persistent failure here most often means has changed.
            log.warn("SH967: closing event stream {} after a failed pass: {}", subscription.id(),
                    failure.toString());
            subscriptions.remove(subscription.id());
            subscription.sink().close();
        }
    }

    /**
     * SH969 — an incident about a post is withheld from a reader who may not read the post.
     *
     * <p>A failed scheduled publish names its post by id and, since SH969, carries the post's address
     * and a retry. Sent to a reader the folder's ACL excludes, that would be the address and title
     * the change rows were withheld for, arriving by the other array. Every other subject — a URL, a
     * request path, a tenant — is not content and passes.</p>
     */
    private boolean readable(ShAgentChanges.Incident incident, ShContentAclService.ReadMemo memo) {
        String subject = incident.subject();
        return subject == null || !subject.startsWith(ID_SUBJECT)
                || aclService.canRead(subject.substring(ID_SUBJECT.length()), memo);
    }

    /**
     * The id is asked for so the ACL can be applied, and removed before the entry leaves: the feed
     * does not show ids unless a caller asks (SH121), and this stream does not take that request.
     */
    private static ShAgentChanges.Change withoutId(ShAgentChanges.Change change) {
        return new ShAgentChanges.Change(change.action(), change.subject(), change.address(),
                change.at(), change.by(), change.what(), change.site(), null, change.affects(),
                change.submission(), change.fields());
    }

    private static void withAuthentication(Authentication authentication, Runnable body) {
        SecurityContext held = SecurityContextHolder.getContext();
        SecurityContext bound = SecurityContextHolder.createEmptyContext();
        bound.setAuthentication(authentication);
        SecurityContextHolder.setContext(bound);
        try {
            body.run();
        } finally {
            SecurityContextHolder.setContext(held);
        }
    }

    /** One event's payload: the entries this reader may see, and the incidents in that range. */
    @JsonInclude(Include.NON_NULL)
    public record Page(List<ShAgentChanges.Change> changes, List<ShAgentChanges.Incident> incidents) {
    }

    private static final class Subscription {

        private final String id;
        private final ShEventSink sink;
        private final Authentication authentication;
        private final String tenant;
        private final String site;
        private volatile String cursor;
        private volatile long lastSentNanos = System.nanoTime();

        Subscription(String id, ShEventSink sink, Authentication authentication, String tenant,
                     String site, String cursor) {
            this.id = id;
            this.sink = sink;
            this.authentication = authentication;
            this.tenant = tenant;
            this.site = site;
            this.cursor = cursor;
        }

        String site() {
            return site;
        }

        String id() {
            return id;
        }

        ShEventSink sink() {
            return sink;
        }

        Authentication authentication() {
            return authentication;
        }

        String tenant() {
            return tenant;
        }

        String cursor() {
            return cursor;
        }

        void moveTo(String next) {
            cursor = next;
        }

        void touch() {
            lastSentNanos = System.nanoTime();
        }

        long idleMillis() {
            return (System.nanoTime() - lastSentNanos) / 1_000_000;
        }
    }
}
