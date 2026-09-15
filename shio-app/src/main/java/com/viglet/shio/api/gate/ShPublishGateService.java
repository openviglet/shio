/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.gate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.api.agent.verify.ShAgentVerifyService;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.agent.ShAddressResolver;
import com.viglet.shio.object.ShFolderSite;
import com.viglet.shio.persistence.model.gate.ShPublishGate;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.gate.ShPublishGateRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The checks that stand between content and visitors (SH997).
 *
 * <h2>What a gate is, and what it deliberately is not</h2>
 *
 * <p>An ordered list of checks with severities, declared per site and evaluated <b>where the publish
 * happens</b> — so no surface can bypass it, which is the whole reason it is here rather than in the
 * CLI. It is not a workflow engine: there is no state machine, no assignee and no queue, because an
 * orchestrator inside Shio would be a third contract over the CDA and the console (P10) and the
 * operator driving this instance already is one.</p>
 *
 * <h2>Three checks, and the one that cannot be here</h2>
 *
 * <p>{@code verify} and {@code links-resolve} run the address-scoped verification over the draft —
 * what a publish would put live — and {@code render-digest} proves the page still renders. The design
 * also named {@code audit}, and it <b>cannot be a server-side gate</b>: `shio audit` drives a browser
 * per viewport, and "the instance does not run a browser" is a standing non-goal. It stays a check in
 * `shio gate run`, where the browser already is, and the vocabulary here refuses the name with that
 * sentence rather than accepting a word that would silently pass.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShPublishGateService {

    /** A check a gate may name, and what it means here. */
    public enum Check {
        /** Every rule the address-scoped verify runs, over the draft this publish would put live. */
        VERIFY("verify"),
        /** Verify narrowed to links: the rule that says a page will not 404 its own references. */
        LINKS_RESOLVE("links-resolve"),
        /** The page renders: a template that throws is a blank page a visitor sees before anybody. */
        RENDER_DIGEST("render-digest");

        private final String wire;

        Check(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }

        static Optional<Check> of(String sent) {
            String text = sent == null ? "" : sent.trim().toLowerCase(Locale.ROOT);
            return List.of(values()).stream().filter(check -> check.wire.equals(text)).findFirst();
        }

        static List<String> names() {
            return List.of(values()).stream().map(Check::wire).toList();
        }
    }

    /** One declared check: what to run, and whether failing it stops the publish. */
    public record Step(Check check, String severity) {

        /** True when this step's failure refuses the publish rather than reporting it. */
        public boolean blocking() {
            return ShAgentReport.ERROR.equals(severity);
        }
    }

    /** What a gate found about one address: the steps that failed, in the order declared. */
    public record Outcome(String gate, List<Failure> failures) {

        public boolean blocked() {
            return failures.stream().anyMatch(Failure::blocking);
        }
    }

    /** One failing step, with the sentence the refusal carries. */
    public record Failure(String check, String severity, String detail) {

        public boolean blocking() {
            return ShAgentReport.ERROR.equals(severity);
        }
    }

    private final ShPublishGateRepository shPublishGateRepository;
    private final ShAgentVerifyService shAgentVerifyService;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShAddressResolver shAddressResolver;
    private final ObjectMapper objectMapper;

    public ShPublishGateService(ShPublishGateRepository shPublishGateRepository,
                                ShAgentVerifyService shAgentVerifyService,
                                ShPostUnifiedRepository shPostUnifiedRepository,
                                ShAddressResolver shAddressResolver,
                                ObjectMapper objectMapper) {
        this.shPublishGateRepository = shPublishGateRepository;
        this.shAgentVerifyService = shAgentVerifyService;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shAddressResolver = shAddressResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * The gates of the post's own site, asked about the post's own address (SH997).
     *
     * <p>Every publish path calls this one method — the agent's op, the console's button, a release
     * run — because a gate that one surface evaluates and another does not is worse than no gate: it
     * is a rule with a door in it that nobody documented.</p>
     */
    @Transactional(readOnly = true, noRollbackFor = ShTeachingException.class)
    public void requirePost(String postId) {
        subject(postId).ifPresent(post -> {
            ShSite site = ShFolderSite.of(post.getShFolder());
            if (site != null) {
                require(site.getName(), site.getId(),
                        shAddressResolver.addressOf(post).toString());
            }
        });
    }

    /** What this site's gates say about one post, without deciding — the dry run's half (SH996). */
    @Transactional(readOnly = true)
    public List<Outcome> evaluatePost(String postId) {
        return subject(postId).map(post -> {
            ShSite site = ShFolderSite.of(post.getShFolder());
            return site == null ? List.<Outcome>of()
                    : evaluate(site.getName(), site.getId(),
                            shAddressResolver.addressOf(post).toString());
        }).orElse(List.of());
    }

    /**
     * The row a gate is about: the draft, which is what a publish would put live.
     *
     * <p>ShFolderSite-exempt: the site is read through {@code ShFolderSite.of} one line down, which
     * is the rule, not around it.</p>
     */
    private Optional<ShPostUnified> subject(String postId) {
        return shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT);
    }

    /** The steps a stored definition declares, refusing a name nothing runs. */
    public List<Step> steps(ShPublishGate gate) {
        JsonNode checks = objectMapper.readTree(gate.getDefinition()).get("checks");
        List<Step> steps = new ArrayList<>();
        if (checks == null || !checks.isArray()) {
            return steps;
        }
        for (JsonNode node : checks) {
            String name = node.isString() ? node.asString() : text(node, "check");
            String severity = node.isString() ? ShAgentReport.ERROR : text(node, "severity");
            steps.add(new Step(Check.of(name).orElseThrow(() -> new UnknownCheckException(name)),
                    ShAgentReport.WARNING.equals(severity) ? ShAgentReport.WARNING : ShAgentReport.ERROR));
        }
        return steps;
    }

    /** The site's enabled gates, in name order, so two instances evaluate in the same order (P5). */
    @Transactional(readOnly = true)
    public List<ShPublishGate> enabledFor(String siteId) {
        return siteId == null ? List.of()
                : shPublishGateRepository.findBySiteIdAndEnabledTrueOrderByNameAsc(siteId);
    }

    /**
     * What this site's gates say about publishing one address, without deciding anything.
     *
     * <p>Separate from {@link #require} because a dry run needs the same answer without the throw —
     * a plan that could not say which gate would stop it would be a plan that promises what the run
     * refuses (SH996's rule, one layer up).</p>
     */
    @Transactional(readOnly = true)
    public List<Outcome> evaluate(String siteName, String siteId, String address) {
        List<Outcome> outcomes = new ArrayList<>();
        for (ShPublishGate gate : enabledFor(siteId)) {
            List<Failure> failures = new ArrayList<>();
            for (Step step : steps(gate)) {
                failed(siteName, address, step).ifPresent(failures::add);
            }
            if (!failures.isEmpty()) {
                outcomes.add(new Outcome(gate.getName(), List.copyOf(failures)));
            }
        }
        return List.copyOf(outcomes);
    }

    /**
     * Refuses the publish when a gate's blocking check failed (SH997).
     *
     * <p>The refusal names the gate, the check and the address, because a 409 that says only "a gate
     * failed" sends a curator to read a YAML file to find out which sentence was about them.</p>
     */
    @Transactional(readOnly = true, noRollbackFor = ShTeachingException.class)
    public void require(String siteName, String siteId, String address) {
        for (Outcome outcome : evaluate(siteName, siteId, address)) {
            if (outcome.blocked()) {
                throw new GateFailedException(outcome, address);
            }
        }
    }

    /** One step against one address, or empty when it passed. */
    private Optional<Failure> failed(String siteName, String address, Step step) {
        // The gate's names are the curator's, and verify's are its own groups: `routes` is SH100's
        // "every published furl resolves, links land", `page` is the render. Mapped here rather than
        // renamed there, because the report's vocabulary is a published contract.
        String checks = switch (step.check()) {
            case LINKS_RESOLVE -> "routes";
            case RENDER_DIGEST -> "page";
            case VERIFY -> null;
        };
        // The draft is the subject: a gate is asked about what this publish would put live, not about
        // what is live now — which is the version that is already past it.
        ShAgentReport report = shAgentVerifyService.verify(siteName, checks, null, null, address,
                null, "draft");
        // A blocking step is about errors: a warning is "probably wrong, and not provably so from
        // inside the CMS" (SH141's own words), which is not a thing to stop a launch over. A
        // warning-severity step reports either, because reporting is all it does.
        boolean failed = step.blocking() ? report.errors() > 0
                : report.errors() > 0 || report.warnings() > 0;
        if (!failed) {
            return Optional.empty();
        }
        String first = report.findings() == null || report.findings().isEmpty() ? null
                : report.findings().get(0).fix();
        return Optional.of(new Failure(step.check().wire(), step.severity(),
                report.errors() + " error(s), " + report.warnings() + " warning(s)"
                        + (first == null ? "" : ": " + first)));
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isString() ? null : value.asString();
    }

    /** A 400 for a check nothing here runs. */
    public static class UnknownCheckException extends ShTeachingException {

        public UnknownCheckException(String sent) {
            super(ShProblem.badRequest("unknown-gate-check",
                            "'" + (sent == null ? "" : sent) + "' is not a publish check")
                    .withDetail("A gate names checks this instance can run at the moment of the "
                            + "publish. `audit` is deliberately not one of them: it drives a browser "
                            + "per viewport, and the instance does not run a browser — run it in CI "
                            + "with `shio gate run`.")
                    .withAllowed(Check.names())
                    .withDidYouMeanFrom(sent)
                    .withFix("Use one of the checks above, or move this one into CI.")
                    .withExample("{\"checks\":[{\"check\":\"links-resolve\",\"severity\":\"error\"}]}"));
        }
    }

    /** A 409 naming the gate, the check and the page that failed it. */
    public static class GateFailedException extends ShTeachingException {

        public GateFailedException(Outcome outcome, String address) {
            super(ShProblem.conflict("publish-gate-failed",
                            "gate '" + outcome.gate() + "' stops " + address)
                    .withDetail(outcome.failures().stream()
                            .map(failure -> failure.check() + " (" + failure.severity() + "): "
                                    + failure.detail())
                            .reduce((left, right) -> left + "; " + right)
                            .orElse("a check failed"))
                    .withFix("Fix what the check reports — GET /api/v2/agent/verify?address="
                            + address + "&state=draft is the same report — and publish again.")
                    .withExample("GET /api/v2/agent/verify?address=" + address + "&state=draft"));
        }
    }

    /** Stores a gate as declared, by site and name. */
    @Transactional
    public ShPublishGate upsert(String siteId, String name, String definition, boolean enabled,
                                String actor) {
        Instant now = Instant.now();
        ShPublishGate gate = shPublishGateRepository.findBySiteIdAndName(siteId, name)
                .orElseGet(() -> {
                    ShPublishGate created = new ShPublishGate();
                    created.setSiteId(siteId);
                    created.setName(name);
                    created.setCreatedAt(now);
                    return created;
                });
        gate.setDefinition(definition);
        gate.setEnabled(enabled);
        gate.setActor(actor);
        gate.setModifiedAt(now);
        ShPublishGate saved = shPublishGateRepository.save(gate);
        // Parsed after the write so a definition naming a check nothing runs is refused rather than
        // stored — a gate that cannot be evaluated is a gate that silently passes.
        steps(saved);
        return saved;
    }
}
