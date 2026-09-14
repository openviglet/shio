/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.form;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.form.ShSiteForm;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.form.ShSiteFormRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.post.type.ShFormEligibility;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Where a rendered form posts (SH633).
 *
 * <h2>Ordinary, because SH631 and SH632 decided everything that was not</h2>
 *
 * <p>The scope is bounded ({@code FORM} carries {@code ROLE_FORM} alone and may use a non-GET method
 * only under this prefix) and the destination is owned by the site ({@code sh_site_form}). What is
 * left is one {@code POST}: a {@code DRAFT} post in the configured folder, or a refusal. There is no
 * submissions subsystem here and that is the argument — <b>a submission is a post</b>, so the trash,
 * the export, the ACL, the review queue and the agent surface already apply to it.</p>
 *
 * <h2>Four properties, none of them new</h2>
 *
 * <ul>
 *   <li><b>{@code DRAFT} only, always.</b> Never a parameter. SH7's review queue is the moderation
 *       step, so a submission is visible to a curator and invisible to a visitor, and nothing here
 *       has to decide what "approved" means.</li>
 *   <li><b>The per-token bucket the CDA already has.</b> A form token is an API token, so
 *       {@code ShApiTokenFilter} has already spent a token from {@code shio.cda.rate-limit} before
 *       this method runs. Nothing to add — what this needed was a tight default, because the caller
 *       is the internet rather than one integrator.</li>
 *   <li><b>A honeypot.</b> The cheapest spam control that costs an author nothing and a visitor
 *       nothing: a field rendered hidden, which a submission must leave empty.</li>
 *   <li><b>Classified {@code AUTHENTICATED}</b> in {@code ShAnonymousRouteAccessTest}. Not a
 *       formality: this is the one endpoint whose whole purpose is to be reachable from a public
 *       page, so it is where a future reader is likeliest to reason it "should" be allow-listed.</li>
 * </ul>
 *
 * <h2>Which refusals may say why</h2>
 *
 * <p>The caller is a browser nobody controls, so P6's teaching errors are bounded here in a way they
 * are nowhere else on the agent surface. A <b>configuration</b> refusal is a message to the operator
 * who set this up and says everything — the site invites nothing, the folder is missing, the type is
 * not on the list, here is what is. A <b>honeypot</b> refusal says nothing useful, deliberately: an
 * error naming the field is a free description of how to evade it. It answers exactly as a success
 * does, so a bot cannot tell the difference at all.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping("/api/v2/cda/form")
@Tag(name = "Form Submission", description = "Where a rendered form posts — one DRAFT post per submission")
@Slf4j
public class ShFormSubmissionAPI {

    /**
     * The field a bot fills in and a person never sees.
     *
     * <p>Fixed rather than configurable: a name a site could choose is a name a site could choose
     * badly, and this one is only useful while the markup that renders it and the check that reads
     * it agree. It is stripped from the stored data — a submission's fields are what the author
     * asked for, and keeping this one would put a spam-control artefact in every content record.</p>
     */
    public static final String HONEYPOT_FIELD = "shio_hp";

    private final ShSiteFormRepository shSiteFormRepository;
    private final ShSiteRepository shSiteRepository;
    private final ShFolderRepository shFolderRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShPostUnifiedWriteService writeService;
    private final ObjectMapper objectMapper;

    public ShFormSubmissionAPI(ShSiteFormRepository shSiteFormRepository,
                               ShSiteRepository shSiteRepository,
                               ShFolderRepository shFolderRepository,
                               ShPostTypeRepository shPostTypeRepository,
                               ShPostUnifiedWriteService writeService,
                               ObjectMapper objectMapper) {
        this.shSiteFormRepository = shSiteFormRepository;
        this.shSiteRepository = shSiteRepository;
        this.shFolderRepository = shFolderRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.writeService = writeService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{siteName}")
    public ResponseEntity<Object> submit(@PathVariable String siteName,
                                         @RequestHeader(value = "Origin", required = false) String origin,
                                         @RequestBody ShFormSubmission body) {
        ShSite site = shSiteRepository.findByName(siteName);
        if (site == null) {
            return refuse(404, "No site named '" + siteName + "'",
                    "The path segment is the site's name. A form posts to the site it is rendered "
                            + "in, so this is the same name that appears in its preview URL.");
        }

        // The token names one site and this is it. Belt and braces with the allow-list the filter
        // already applies: a form token that reached another site's endpoint would be posting into
        // content it was never issued for, and that is worth refusing twice rather than once.
        Optional<ShApiTokenContext> token = ShApiTokenContext.current();
        if (token.isPresent() && !token.get().allowsSite(site.getId())) {
            return refuse(403, "This token is not for '" + siteName + "'",
                    "A FORM token names the one site it may submit to. Issue one for this site "
                            + "rather than widening an existing token.");
        }

        Optional<ShSiteForm> configured = shSiteFormRepository.findFirstBySiteIdOrderByIdAsc(site.getId());
        if (configured.isEmpty() || !configured.get().isEnabled()) {
            // One answer for "never configured" and "configured and switched off", because they are
            // the same fact to a submitter and the difference is the operator's business.
            return refuse(403, "'" + siteName + "' is not accepting form submissions",
                    "An operator enables this per site: POST /api/v2/site-form with the folder "
                            + "submissions land in and the post-types it invites, then set enabled.");
        }
        ShSiteForm config = configured.get();

        // Read before the honeypot is judged, so a misconfigured site tells its operator the truth
        // rather than looking like a bot to itself.
        List<String> allowed = allowedTypes(config);
        ShPostType postType = body.type() == null || body.type().isBlank()
                ? null : shPostTypeRepository.findByName(body.type());
        if (!ShFormEligibility.isEligible(postType, allowed)) {
            return refuse(400, "'" + body.type() + "' is not a post-type this site invites",
                    "This site invites: " + String.join(", ", eligible(allowed))
                            + ". A system post-type is never eligible whatever the list says — "
                            + "PageLayout, Region and Theme are templates this instance executes.");
        }

        // SH637 — the control, and CORS is not it. CORS protects the browser and a non-browser
        // client ignores it entirely, so an origin allowed only there is allowed only against
        // callers who were going to behave. Checked here, where the tenant is resolved and every
        // caller is subject to it, and after the configuration refusals above so an operator whose
        // site is misconfigured is told that rather than this.
        if (!originAllowed(config, origin)) {
            return refuse(403, "'" + origin + "' is not an origin this site accepts forms from",
                    "Add it to allowedOrigins on /api/v2/site-form (comma-separated, scheme and "
                            + "host as the browser sends them, e.g. https://www.acme.com). Empty "
                            + "allows no browser origin, which is the safe default rather than an "
                            + "oversight.");
        }

        ShFolder folder = folderOf(site, config.getFolderUrl());
        if (folder == null) {
            return refuse(500, "This site's form folder does not exist",
                    "'" + config.getFolderUrl() + "' is configured on /api/v2/site-form and no "
                            + "folder in '" + siteName + "' has that path. Create it, or correct "
                            + "the configuration. A submission never creates a folder — a public "
                            + "path that can is a public path that can fill a disk.");
        }

        if (trippedHoneypot(body)) {
            // Deliberately indistinguishable from a success: an error naming the field is a free
            // description of how to evade it. Nothing is written and nothing says so.
            log.debug("Form submission to '{}' tripped the honeypot", siteName);
            return ResponseEntity.ok(Map.of("received", true));
        }

        ShPostUnified created = writeService.create(new CreateRequest(
                folder.getId(), postType.getId(), fields(body),
                // Never a parameter. The moderation step is SH7's review queue, and a submission a
                // stranger could publish is not a submission.
                ShPostState.DRAFT,
                ShFormSubmitter.owner(token), null, null));

        log.info("Form submission accepted for site '{}' as {} '{}' by {}",
                siteName, body.type(), created.getId(), created.getOwner());
        return ResponseEntity.ok(Map.of(
                "received", true,
                "id", created.getId(),
                "state", ShPostState.DRAFT.name()));
    }

    /** The submitted fields, minus the honeypot — a spam artefact is not content. */
    private JsonNode fields(ShFormSubmission body) {
        ObjectNode data = objectMapper.createObjectNode();
        if (body.data() != null && body.data().isObject()) {
            data.setAll((ObjectNode) body.data());
        }
        data.remove(HONEYPOT_FIELD);
        return data;
    }

    /**
     * Whether this site accepts a submission from this browser origin (SH637).
     *
     * <p>A request with <b>no</b> {@code Origin} passes: that header is something a browser adds,
     * so its absence means a server-to-server caller — curl, an integration — and refusing those
     * would break the documented request while stopping nobody, since a caller who wanted to lie
     * about it simply would not send one. What the header is good for is the case it is honest in:
     * a browser cannot forge it, so a page on an origin this site has not named cannot spend the
     * token that is sitting in its markup.</p>
     *
     * <p>Compared exactly, after trimming. No suffix matching and no wildcards: {@code acme.com}
     * accepting {@code evil-acme.com} is the classic origin-check defect, and a list an operator
     * types is short enough that every entry can be spelled in full.</p>
     */
    private static boolean originAllowed(ShSiteForm config, String origin) {
        if (origin == null || origin.isBlank()) {
            return true;
        }
        String allowed = config.getAllowedOrigins();
        if (allowed == null || allowed.isBlank()) {
            return false;
        }
        for (String entry : allowed.split(",")) {
            if (entry.trim().equalsIgnoreCase(origin.trim())) {
                return true;
            }
        }
        return false;
    }

    /** Whether the hidden field carries anything, at the top level or inside the fields. */
    private static boolean trippedHoneypot(ShFormSubmission body) {
        if (filled(body.honeypot())) {
            return true;
        }
        JsonNode inData = body.data() == null ? null : body.data().get(HONEYPOT_FIELD);
        return inData != null && inData.isString() && filled(inData.asString());
    }

    private static boolean filled(String value) {
        return value != null && !value.isBlank();
    }

    /** The configured names, split and trimmed; an empty list invites nothing (SH632). */
    private static List<String> allowedTypes(ShSiteForm config) {
        if (config.getAllowedTypes() == null || config.getAllowedTypes().isBlank()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String name : config.getAllowedTypes().split(",")) {
            if (!name.isBlank()) {
                names.add(name.trim());
            }
        }
        return names;
    }

    /**
     * The configured names that are actually eligible, for the refusal to print.
     *
     * <p>Filtered rather than echoed: a site whose list says {@code Theme} must not be told it
     * invites {@code Theme}, because the endpoint will never accept one and printing it would send
     * an operator looking for a bug that is their configuration.</p>
     */
    private List<String> eligible(List<String> allowed) {
        List<String> names = new ArrayList<>();
        for (String name : allowed) {
            if (ShFormEligibility.isEligibleType(shPostTypeRepository.findByName(name))) {
                names.add(name);
            }
        }
        return names.isEmpty() ? List.of("(nothing — this site's allow-list names no usable type)") : names;
    }

    /** The configured folder, by name chain within the site, or {@code null}. */
    private ShFolder folderOf(ShSite site, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        List<String> names = Arrays.stream(path.split("/")).filter(part -> !part.isBlank()).toList();
        Optional<ShFolder> current = shFolderRepository.findRootFolder(site);
        if (current.isEmpty()) {
            return null;
        }
        ShFolder folder = current.get();
        for (String name : names) {
            ShFolder child = shFolderRepository.findByParentFolderAndName(folder, name);
            if (child == null) {
                return null;
            }
            folder = child;
        }
        return folder;
    }

    /** A refusal that teaches (P6) — every field an operator needs to fix their configuration. */
    private static ResponseEntity<Object> refuse(int status, String title, String fix) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("received", false);
        body.put("title", title);
        body.put("fix", fix);
        return ResponseEntity.status(status).body(body);
    }

    /**
     * One submission.
     *
     * @param type     the post-type name this form is about — the same name {@code {{#form type=}}}
     *                 renders, which is the only thing the template knows and the only thing it may
     * @param data     the submitted fields
     * @param honeypot the hidden field, which must be empty; also accepted inside {@code data}
     */
    public record ShFormSubmission(String type, JsonNode data, String honeypot) {
    }
}
