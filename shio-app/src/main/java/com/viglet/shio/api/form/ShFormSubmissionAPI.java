/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.form;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
import com.viglet.shio.persistence.repository.form.ShSiteFormRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.api.site.ShSiteLocaleService;
import com.viglet.shio.api.site.ShSiteLocales;
import com.viglet.shio.post.type.ShFormEligibility;
import com.viglet.shio.render.ShPageRenderService;
import com.viglet.shio.render.ShSiteFrontEnd;
import com.viglet.shio.render.ShSitesAPI;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;
import com.viglet.shio.spring.security.auth.ShApiTokenFilter;

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

    /** SH1382 — the post-type a script-free form names, in a hidden field of the body. */
    public static final String TYPE_FIELD = "shio_type";

    /** SH1382 — the query parameter a script-free caller is sent back with, and its two values. */
    public static final String RESULT_PARAM = "shio_form";
    public static final String RESULT_SENT = "sent";
    public static final String RESULT_ERROR = "error";

    /** Fields of a urlencoded body that steer the submission and are never stored. */
    private static final Set<String> RESERVED_FIELDS =
            Set.of(TYPE_FIELD, HONEYPOT_FIELD, ShApiTokenFilter.FORM_TOKEN_FIELD);

    private final ShSiteFormRepository shSiteFormRepository;
    private final ShSiteRepository shSiteRepository;
    private final ShFormDestination shFormDestination;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShPostUnifiedWriteService writeService;
    private final ObjectMapper objectMapper;
    private final ShSiteLocaleService shSiteLocaleService;
    private final ShPageRenderService shPageRenderService;

    public ShFormSubmissionAPI(ShSiteFormRepository shSiteFormRepository,
                               ShSiteRepository shSiteRepository,
                               ShFormDestination shFormDestination,
                               ShPostTypeRepository shPostTypeRepository,
                               ShPostUnifiedWriteService writeService,
                               ObjectMapper objectMapper,
                               ShSiteLocaleService shSiteLocaleService,
                               ShPageRenderService shPageRenderService) {
        this.shSiteFormRepository = shSiteFormRepository;
        this.shSiteRepository = shSiteRepository;
        this.shFormDestination = shFormDestination;
        this.shPostTypeRepository = shPostTypeRepository;
        this.writeService = writeService;
        this.objectMapper = objectMapper;
        this.shSiteLocaleService = shSiteLocaleService;
        this.shPageRenderService = shPageRenderService;
    }

    /** A submission with no page behind it, as a server-side caller sends one. */
    public ResponseEntity<Object> submit(String siteName, String origin, ShFormSubmission body) {
        return submit(siteName, origin, null, body);
    }

    @PostMapping("/{siteName}")
    public ResponseEntity<Object> submit(@PathVariable String siteName,
                                         @RequestHeader(value = "Origin", required = false) String origin,
                                         @RequestHeader(value = "Referer", required = false) String referer,
                                         @RequestBody ShFormSubmission body) {
        Outcome outcome = accept(siteName, origin, body, referer);
        if (outcome.status() != 200) {
            return refuse(outcome.status(), outcome.title(), outcome.fix());
        }
        return ResponseEntity.ok(outcome.id() == null
                ? Map.of("received", true)
                : Map.of("received", true, "id", outcome.id(), "state", ShPostState.DRAFT.name()));
    }

    /**
     * The same submission from a page with no script (SH1382).
     *
     * <p>A plain {@code <form method="post">} sends {@code application/x-www-form-urlencoded} and
     * follows whatever comes back, so a JSON answer would leave the visitor staring at it. This
     * caller gets a {@code 303} back to the page it came from with {@code shio_form=sent} or
     * {@code shio_form=error} in the query, which a template reads to say thanks or try again. The
     * honeypot answers {@code sent}, as it answers a success on the JSON path.</p>
     *
     * <p>Three field names are reserved and never stored: {@link #TYPE_FIELD}, the honeypot and the
     * token field the filter already read. Every other field is the submission.</p>
     */
    @PostMapping(value = "/{siteName}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> submitForm(@PathVariable String siteName,
                                           @RequestHeader(value = "Origin", required = false) String origin,
                                           @RequestHeader(value = "Referer", required = false) String referer,
                                           @RequestParam MultiValueMap<String, String> params) {
        ObjectNode data = objectMapper.createObjectNode();
        params.forEach((name, values) -> {
            if (RESERVED_FIELDS.contains(name) || values == null || values.isEmpty()) {
                return;
            }
            if (values.size() == 1) {
                data.put(name, values.getFirst());
            } else {
                values.forEach(data.putArray(name)::add);
            }
        });
        Outcome outcome = accept(siteName, origin,
                new ShFormSubmission(params.getFirst(TYPE_FIELD), data, params.getFirst(HONEYPOT_FIELD)), referer);
        if (outcome.status() != 200) {
            log.info("Form submission to '{}' refused ({}): {}", siteName, outcome.status(), outcome.title());
        }
        String back = backTo(siteName, origin, referer,
                outcome.status() == 200 ? RESULT_SENT : RESULT_ERROR);
        return ResponseEntity.status(303).location(URI.create(back)).build();
    }

    /**
     * Where the visitor is sent back to, with the result marker appended.
     *
     * <p>The full {@code Referer} only when its origin is the browser's {@code Origin} and the site
     * accepts that origin, so this endpoint is never an open redirect to wherever a forged header
     * points. Otherwise the {@code Referer}'s path alone, which stays on this instance, and with no
     * usable {@code Referer} the site's own front end.</p>
     */
    private String backTo(String siteName, String origin, String referer, String result) {
        String target = null;
        URI ref = parse(referer);
        if (ref != null && ref.getScheme() != null && ref.getHost() != null) {
            String refOrigin = ref.getScheme() + "://" + ref.getRawAuthority();
            ShSite site = shSiteRepository.findByName(siteName);
            Optional<ShSiteForm> config = site == null ? Optional.empty()
                    : shSiteFormRepository.findFirstBySiteIdOrderByIdAsc(site.getId());
            boolean trusted = origin != null && refOrigin.equalsIgnoreCase(origin.trim())
                    && config.isPresent() && originAllowed(config.get(), origin);
            target = withoutQueryMarker(ref, trusted);
        }
        if (target == null || !target.startsWith("/") && !target.startsWith("http")) {
            ShSite site = shSiteRepository.findByName(siteName);
            target = site == null ? "/" : ShSiteFrontEnd.of(site).baseUrl();
        }
        if (target.startsWith("//")) {
            target = "/";
        }
        return target + (target.contains("?") ? "&" : "?") + RESULT_PARAM + "=" + result;
    }

    /** The URI as a string, a previous result marker dropped, absolute or path-only. */
    private static String withoutQueryMarker(URI ref, boolean absolute) {
        String path = ref.getRawPath() == null || ref.getRawPath().isEmpty() ? "/" : ref.getRawPath();
        StringBuilder query = new StringBuilder();
        if (ref.getRawQuery() != null) {
            for (String pair : ref.getRawQuery().split("&")) {
                if (!pair.isEmpty() && !pair.startsWith(RESULT_PARAM + "=")) {
                    query.append(query.isEmpty() ? "" : "&").append(pair);
                }
            }
        }
        String tail = path + (query.isEmpty() ? "" : "?" + query);
        return absolute ? ref.getScheme() + "://" + ref.getRawAuthority() + tail : tail;
    }

    private static URI parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new URI(value.trim());
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** What one submission came to, before either caller's answer is shaped from it. */
    private record Outcome(int status, String title, String fix, String id) {
        static Outcome refused(int status, String title, String fix) {
            return new Outcome(status, title, fix, null);
        }
    }

    /**
     * SH1414 — the language a submission is written in.
     *
     * <p>A form on a Portuguese page landed as an English draft, labelled EN in the console, so a
     * curator could not tell which language to answer in. The page it was sent from says: the locale
     * segment of a {@code /sites/<site>/<format>/<locale>/...} address in the {@code Referer}, taken
     * only when the site has that locale, since the header is the stranger's to write. Otherwise the
     * site's declared default, and otherwise none, which the write service turns into its own.</p>
     */
    String localeOf(ShSite site, String referer) {
        URI ref = parse(referer);
        String path = ref == null ? null : ref.getRawPath();
        if (path != null && path.startsWith(SITES_PREFIX + "/")) {
            String tag = ShSitesAPI.Address.of(path.substring(SITES_PREFIX.length()), null, null).selectedLocale();
            if (tag != null) {
                Optional<String> known = shPageRenderService.localesOf(site.getId(), ShPostState.PUBLISHED)
                        .stream().filter(tag::equalsIgnoreCase).findFirst();
                if (known.isPresent()) {
                    return known.get();
                }
            }
        }
        return shSiteLocaleService.declared(site).map(ShSiteLocales::defaultLocale).orElse(null);
    }

    /** The public delivery route's prefix, whose address carries the page's locale (SH1414). */
    private static final String SITES_PREFIX = "/sites";

    private Outcome accept(String siteName, String origin, ShFormSubmission body, String referer) {
        ShSite site = shSiteRepository.findByName(siteName);
        if (site == null) {
            return Outcome.refused(404, "No site named '" + siteName + "'",
                    "The path segment is the site's name. A form posts to the site it is rendered "
                            + "in, so this is the same name that appears in its preview URL.");
        }

        // The token names one site and this is it. Belt and braces with the allow-list the filter
        // already applies: a form token that reached another site's endpoint would be posting into
        // content it was never issued for, and that is worth refusing twice rather than once.
        Optional<ShApiTokenContext> token = ShApiTokenContext.current();
        if (token.isPresent() && !token.get().allowsSite(site.getId())) {
            return Outcome.refused(403, "This token is not for '" + siteName + "'",
                    "A FORM token names the one site it may submit to. Issue one for this site "
                            + "rather than widening an existing token.");
        }

        Optional<ShSiteForm> configured = shSiteFormRepository.findFirstBySiteIdOrderByIdAsc(site.getId());
        if (configured.isEmpty() || !configured.get().isEnabled()) {
            // One answer for "never configured" and "configured and switched off", because they are
            // the same fact to a submitter and the difference is the operator's business.
            return Outcome.refused(403, "'" + siteName + "' is not accepting form submissions",
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
            return Outcome.refused(400, "'" + body.type() + "' is not a post-type this site invites",
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
            return Outcome.refused(403, "'" + origin + "' is not an origin this site accepts forms from",
                    "Add it to allowedOrigins on /api/v2/site-form (comma-separated, scheme and "
                            + "host as the browser sends them, e.g. https://www.acme.com). Empty "
                            + "allows no browser origin, which is the safe default rather than an "
                            + "oversight.");
        }

        ShFolder folder = shFormDestination.resolve(site, config.getFolderUrl());
        if (folder == null) {
            return Outcome.refused(500, "This site's form folder does not exist",
                    "'" + config.getFolderUrl() + "' is configured on /api/v2/site-form and no "
                            + "folder in '" + siteName + "' has that path. Create it, or correct "
                            + "the configuration. A submission never creates a folder — a public "
                            + "path that can is a public path that can fill a disk.");
        }

        if (trippedHoneypot(body)) {
            // Deliberately indistinguishable from a success: an error naming the field is a free
            // description of how to evade it. Nothing is written and nothing says so.
            log.debug("Form submission to '{}' tripped the honeypot", siteName);
            return new Outcome(200, null, null, null);
        }

        ShPostUnified created = writeService.create(new CreateRequest(
                folder.getId(), postType.getId(), fields(body),
                // Never a parameter. The moderation step is SH7's review queue, and a submission a
                // stranger could publish is not a submission.
                ShPostState.DRAFT,
                // SH1414 — in the language of the page that held the form.
                ShFormSubmitter.owner(token), localeOf(site, referer), null));

        log.info("Form submission accepted for site '{}' as {} '{}' by {}",
                siteName, body.type(), created.getId(), created.getOwner());
        return new Outcome(200, null, null, created.getId());
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
