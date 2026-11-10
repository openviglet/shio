/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.review;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.viglet.shio.api.agent.ShAgentRunService;
import com.viglet.shio.api.auth.dock.ShDockPreferencesService;
import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentFrames;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.api.review.dto.ShReviewSession;
import com.viglet.shio.persistence.model.agent.ShAgentRun;
import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.persistence.model.tenant.ShTenantStatus;
import com.viglet.shio.persistence.model.webhook.ShWebhookEventType;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;
import com.viglet.shio.property.ShAppProperties;
import com.viglet.shio.schedule.ShScheduleProperties;
import com.viglet.shio.tenant.ShTenantHandoff;
import com.viglet.shio.webhook.ShPostLifecycleEvent;
import com.viglet.shio.webhook.ShWebhookDispatcher;

import lombok.extern.slf4j.Slf4j;

/**
 * A finished agent run tells its reviewers (SH1428).
 *
 * <h2>Why a sweep and not the write</h2>
 *
 * <p>SH998 announced {@code review_requested} from inside the write, once per transaction. An agent
 * run is several transactions: the promo run that created four pages and fixed two of them was three
 * {@code shio_write} calls, so three notices, the first of them counting one page. And a run never
 * says it has ended. So the notice waits until the run has been quiet for {@link #QUIET}, then says
 * once what the queue says (SH1416, SH1425: pages, not writes) with a link that opens that run.</p>
 *
 * <h2>Once per run</h2>
 *
 * <p>{@code sh_agent_run.review_notified_at} records the notice. A run that writes again after it
 * was announced has something new to review and is announced again; one that does not is never
 * announced twice, however many sweeps read it. A run with nothing waiting (all published, held or
 * discarded) is not announced at all. A run quieter than {@link #HORIZON} is history: the first
 * sweep after an upgrade must not page anybody about last month.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Slf4j
@Component
public class ShReviewNotifier {

    /** How long a run must go without a write before it counts as finished. */
    static final Duration QUIET = Duration.ofMinutes(10);

    /** A run quieter than this is history, not news. */
    static final Duration HORIZON = Duration.ofHours(24);

    /** How many recent runs one sweep reads: the queue's own page. */
    private static final int RUNS = 50;

    /** The queue's word for each net kind, in the queue's order (SH1416). */
    private static final Map<String, String> KINDS = new LinkedHashMap<>();

    static {
        KINDS.put("CREATE", "new");
        KINDS.put("UPDATE", "edited");
        KINDS.put("MOVE", "moved");
        KINDS.put("PUBLISH", "published");
        KINDS.put("UNPUBLISH", "unpublished");
        KINDS.put("DELETE", "deleted");
        KINDS.put("RESTORE", "restored");
    }

    private final ShReviewService shReviewService;
    private final ShAgentRunService shAgentRunService;
    private final ShWebhookDispatcher shWebhookDispatcher;
    private final ShScheduleProperties scheduleProperties;
    private final ShAppProperties shAppProperties;
    private final ShTenantRepository shTenantRepository;
    private final ShAgentIncidentLog incidentLog;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final ShUserRepository shUserRepository;
    private final ShDockPreferencesService shDockPreferencesService;

    public ShReviewNotifier(ShReviewService shReviewService, ShAgentRunService shAgentRunService,
                            ShWebhookDispatcher shWebhookDispatcher,
                            ShScheduleProperties scheduleProperties, ShAppProperties shAppProperties,
                            ShTenantRepository shTenantRepository, ShAgentIncidentLog incidentLog,
                            ObjectProvider<JavaMailSender> mailSender, ShUserRepository shUserRepository,
                            ShDockPreferencesService shDockPreferencesService) {
        this.incidentLog = incidentLog;
        this.mailSender = mailSender;
        this.shUserRepository = shUserRepository;
        this.shDockPreferencesService = shDockPreferencesService;
        this.shReviewService = shReviewService;
        this.shAgentRunService = shAgentRunService;
        this.shWebhookDispatcher = shWebhookDispatcher;
        this.scheduleProperties = scheduleProperties;
        this.shAppProperties = shAppProperties;
        this.shTenantRepository = shTenantRepository;
    }

    /** The timer, on the schedule sweep's interval and switches (SH1424). */
    @Scheduled(fixedDelayString = "${shio.schedule.sweep-interval-millis:60000}")
    public void scheduledSweep() {
        if (scheduleProperties.isEnabled() && scheduleProperties.isBackground()) {
            sweep(Instant.now());
        }
    }

    /**
     * Announce every run that finished and still waits, in every tenant (SH639's iteration).
     *
     * @return the sessions announced, for the caller that asked directly
     */
    public List<String> sweep(Instant now) {
        if (!shAppProperties.isMultiTenant()) {
            return sweepCurrentTenant(now);
        }
        List<String> announced = new ArrayList<>();
        for (ShTenant tenant : shTenantRepository.findAllByOrderByNameAsc()) {
            if (tenant.getStatus() == ShTenantStatus.SUSPENDED) {
                continue;
            }
            try {
                ShTenantHandoff.runAs(tenant.getName(), () -> announced.addAll(sweepCurrentTenant(now)));
            } catch (RuntimeException e) {
                // Recorded, not only logged: a notice that never went out is a run nobody reviews,
                // and nobody is watching the log at the hour it fails (SH270).
                log.warn("Review notice sweep failed for tenant {}: {}", tenant.getName(), e.getMessage());
                incidentLog.record(ShAgentIncidentLog.Kind.WEBHOOK, "tenant:" + tenant.getName(),
                        "the review notice sweep failed for this tenant: " + e.getMessage(),
                        "Runs waiting for review were not announced. Open the review queue; the next "
                                + "sweep tries again.", ShAgentIncidentFrames.of(e), null);
            }
        }
        return announced;
    }

    private List<String> sweepCurrentTenant(Instant now) {
        List<ShReviewSession> runs = shReviewService.sessions(RUNS);
        Map<String, Instant> notified = new LinkedHashMap<>();
        for (ShAgentRun run : shAgentRunService.runs(runs.stream().map(ShReviewSession::session).toList())) {
            if (run.getReviewNotifiedAt() != null) {
                notified.put(run.getId(), run.getReviewNotifiedAt());
            }
        }
        List<String> announced = new ArrayList<>();
        for (ShReviewSession run : runs) {
            if (due(run, notified.get(run.session()), now)) {
                announce(run, now);
                shAgentRunService.reviewNotified(run.session(), run.actor(), now);
                announced.add(run.session());
            }
        }
        return announced;
    }

    /** Finished, still waiting, recent, and not already announced since its last write. */
    static boolean due(ShReviewSession run, Instant notifiedAt, Instant now) {
        Instant last = run.lastSeen();
        return run.pending() > 0 && last != null
                && !last.isAfter(now.minus(QUIET))
                && last.isAfter(now.minus(HORIZON))
                && (notifiedAt == null || last.isAfter(notifiedAt));
    }

    /** One notice per site the run touched, each carrying the whole run's sentence and link. */
    private void announce(ShReviewSession run, Instant now) {
        Map<String, String> facts = facts(run, shAppProperties.getUrl());
        for (String siteId : run.sites()) {
            shWebhookDispatcher.dispatch(new ShPostLifecycleEvent(ShWebhookEventType.REVIEW_REQUESTED,
                    null, siteId, null, null, now, facts));
        }
        email(run, facts);
    }

    /**
     * The e-mail half: to the person whose credential the run used, who is the one who asked for it.
     *
     * <p>Not "everyone who may publish": the permission model answers "may this person publish",
     * never "who may", and mailing a site's every publisher about one person's run is the notice a
     * team learns to filter. Skipped when mail is off ({@code shio.review.email}), when the person
     * turned it off in their dock preferences, or when they have no address.</p>
     */
    private void email(ShReviewSession run, Map<String, String> facts) {
        if (!shAppProperties.getReview().isEmail() || run.actor() == null) {
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        ShUser person = shUserRepository.findByUsername(run.actor());
        if (sender == null || person == null || person.getEmail() == null || person.getEmail().isBlank()
                || !shDockPreferencesService.reviewEmail(run.actor())) {
            return;
        }
        SimpleMailMessage message = message(person.getEmail(), shAppProperties.getReview().getEmailFrom(),
                facts);
        try {
            sender.send(message);
        } catch (MailException e) {
            // Recorded, not only logged (SH270): the webhook half went out, and this says which half
            // did not, so an operator whose curator never got the mail can find out why.
            incidentLog.record(ShAgentIncidentLog.Kind.WEBHOOK, "run:" + run.session(),
                    "the review e-mail could not be sent: " + e.getMessage(),
                    "Check spring.mail.* on this instance. The run is still in the review queue.",
                    ShAgentIncidentFrames.of(e), null);
        }
    }

    /** The e-mail itself: the queue's sentence as the subject, the link in the body. */
    static SimpleMailMessage message(String to, String from, Map<String, String> facts) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        if (from != null && !from.isBlank()) {
            message.setFrom(from);
        }
        message.setSubject(facts.get("summary"));
        StringBuilder body = new StringBuilder(facts.get("summary")).append(".\n\n");
        if (facts.containsKey("intent")) {
            body.append("The run said it was for: ").append(facts.get("intent")).append("\n\n");
        }
        body.append("Open it in the review queue: ").append(facts.get("reviewUrl")).append('\n');
        message.setText(body.toString());
        return message;
    }

    /** What the notice says, in the order a receiver reads it (P5: insertion-ordered). */
    static Map<String, String> facts(ShReviewSession run, String instanceUrl) {
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("session", run.session());
        facts.put("summary", summary(run));
        facts.put("pages", String.valueOf(run.pages()));
        facts.put("pending", String.valueOf(run.pending()));
        facts.put("reviewUrl", reviewUrl(instanceUrl, run.session()));
        if (run.intent() != null) {
            facts.put("intent", run.intent());
        }
        if (run.agentName() != null) {
            facts.put("agent", run.agentName());
        }
        return facts;
    }

    /** "4 new pages in quilhara wait for review": the queue's title, as a sentence (SH1425). */
    static String summary(ShReviewSession run) {
        Map<String, Integer> byPage = run.byPage() == null ? Map.of() : run.byPage();
        List<String> parts = new ArrayList<>();
        KINDS.forEach((kind, word) -> {
            Integer count = byPage.get(kind);
            if (count != null && count > 0) {
                parts.add(count + " " + word + (count == 1 ? " page" : " pages"));
            }
        });
        int total = parts.isEmpty() ? run.pending()
                : byPage.values().stream().mapToInt(Integer::intValue).sum();
        String what = parts.isEmpty() ? total + (total == 1 ? " page" : " pages")
                : String.join(", ", parts);
        List<String> sites = run.bySite() == null ? List.of() : List.copyOf(run.bySite().keySet());
        return what + (sites.isEmpty() ? "" : " in " + String.join(", ", sites))
                + (total == 1 ? " waits" : " wait") + " for review";
    }

    /** The queue, opened on this run (SH999's deep link). */
    static String reviewUrl(String instanceUrl, String session) {
        String base = instanceUrl == null ? "" : instanceUrl.replaceAll("/+$", "");
        return base + "/bento/admin/review?session=" + URLEncoder.encode(session, StandardCharsets.UTF_8);
    }
}
