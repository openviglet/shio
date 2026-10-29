/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.system.ShLocaleRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1056 — which locale policy a site has: the one it declared, or the one its content implies.
 *
 * <h2>One resolver, because there were two answers</h2>
 *
 * <p>"What languages is this site in" was answered in three places and never the same way: the
 * render frame published an instance-wide default, verify took the union of the locales it found
 * while scanning, and the console's translations menu offered all forty-five seeded tags on every
 * post. None was wrong — there was no setting to read — and each was a different shape. This is
 * the one read, so a declared policy reaches all of them at once.</p>
 *
 * <h2>Batched, because the callers are listings</h2>
 *
 * <p>{@link #of(List)} exists rather than a loop over {@link #of(ShSite)}: the context pack and
 * the site listing both answer for every site a caller can see, and P2's "one call replaces a
 * session" is not served by a read that costs a query per row. One query resolves the derived
 * union for every undeclared site in the list, and a list of declared ones costs none at all.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShSiteLocaleService {

    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShLocaleRepository shLocaleRepository;
    private final ShAgentIncidentLog incidentLog;
    private final ObjectMapper objectMapper;

    public ShSiteLocaleService(ShPostUnifiedRepository shPostUnifiedRepository,
                               ShLocaleRepository shLocaleRepository,
                               ShAgentIncidentLog incidentLog,
                               ObjectMapper objectMapper) {
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shLocaleRepository = shLocaleRepository;
        this.incidentLog = incidentLog;
        this.objectMapper = objectMapper;
    }

    /** The policy one site reports — declared, or read off its content. */
    @Transactional(readOnly = true)
    public ShSiteLocales of(ShSite site) {
        return of(List.of(site)).get(site.getId());
    }

    /**
     * The policy every one of these sites reports, keyed by site id.
     *
     * <p>Sites with a declared policy are answered from their own column; the rest share one
     * query. A caller holding both kinds pays for the second kind only.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, ShSiteLocales> of(List<ShSite> sites) {
        return resolve(sites, null);
    }

    /**
     * As {@link #of(List)}, deriving an undeclared site's union from these states only (SH1123).
     *
     * <p>The delivery read's variant. A declared policy is the same answer to every reader — it is
     * a decision, not a description — but a derived one describes content, and a reader that can
     * see only published rows must be described only published rows. Otherwise a production
     * front end is told the site offers a language that exists only in a draft.</p>
     *
     * @param readable the states the caller reads, e.g. {@code PUBLISHED} alone for a production
     *                 token
     */
    @Transactional(readOnly = true)
    public Map<String, ShSiteLocales> of(List<ShSite> sites, Collection<ShPostState> readable) {
        return resolve(sites, readable == null ? List.of() : readable);
    }

    /** @param readable null for every state, which is the author's view */
    private Map<String, ShSiteLocales> resolve(List<ShSite> sites, Collection<ShPostState> readable) {
        Map<String, ShSiteLocales> resolved = new LinkedHashMap<>();
        List<String> derive = new ArrayList<>();
        for (ShSite site : sites) {
            Optional<ShSiteLocales> declared = declared(site);
            if (declared.isPresent()) {
                resolved.put(site.getId(), declared.get());
            } else {
                derive.add(site.getId());
            }
        }
        if (!derive.isEmpty()) {
            Map<String, Collection<String>> inContent = contentLocales(derive, readable);
            for (String id : derive) {
                resolved.put(id, ShSiteLocales.derived(ShPostUnifiedWriteService.DEFAULT_LOCALE,
                        inContent.getOrDefault(id, List.of())));
            }
        }
        return resolved;
    }

    /**
     * Validates a requested policy and returns its stored form.
     *
     * <p>The catalogue is read here rather than passed in, because a caller that had to fetch the
     * forty-five known tags in order to write two of them would be paying P2's cost to avoid a
     * join.</p>
     *
     * @param requested the {@code locales} object from a write, or null
     * @return the JSON to store, or empty when the caller sent nothing
     * @throws com.viglet.shio.api.error.ShTeachingException 400 naming what was wrong (P6)
     */
    public Optional<String> declare(JsonNode requested) {
        if (requested == null || requested.isNull()) {
            return Optional.empty();
        }
        if (!requested.isObject()) {
            throw new ShSiteLocales.EmptyEnabledException();
        }
        List<String> enabled = new ArrayList<>();
        JsonNode offered = requested.get(ShSiteLocales.ENABLED_KEY);
        if (offered != null && offered.isArray()) {
            for (JsonNode each : offered) {
                enabled.add(each.isString() ? each.stringValue() : String.valueOf(each));
            }
        }
        Map<String, String> fallback = new LinkedHashMap<>();
        JsonNode chains = requested.get(ShSiteLocales.FALLBACK_KEY);
        if (chains != null && chains.isObject()) {
            for (String from : chains.propertyNames()) {
                JsonNode to = chains.get(from);
                fallback.put(from, to != null && to.isString() ? to.stringValue()
                        : String.valueOf(to));
            }
        }
        // SH1281 — what the switcher calls each locale, validated like the fallback map.
        Map<String, String> labels = new LinkedHashMap<>();
        JsonNode named = requested.get(ShSiteLocales.LABELS_KEY);
        if (named != null && named.isObject()) {
            for (String tag : named.propertyNames()) {
                JsonNode label = named.get(tag);
                labels.put(tag, label != null && label.isString() ? label.stringValue()
                        : String.valueOf(label));
            }
        }
        JsonNode chosen = requested.get(ShSiteLocales.DEFAULT_KEY);
        ShSiteLocales policy = ShSiteLocales.of(
                chosen != null && chosen.isString() ? chosen.stringValue() : null,
                enabled, fallback, labels, known());
        return Optional.of(policy.toJson(objectMapper));
    }

    /** The locale tags this instance knows — the catalogue {@code GET /api/v2/locale} lists. */
    public Collection<String> known() {
        return shLocaleRepository.findAll().stream()
                .map(locale -> locale.getInitials())
                .filter(tag -> tag != null && !tag.isBlank())
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
    }

    /**
     * The stored policy, or empty when there is none — and an incident when there is one that
     * will not parse.
     *
     * <p>Recorded rather than swallowed, because this is the invisible degradation SH270's rule is
     * about: somebody set a policy, the site silently reports the derived union instead, and every
     * surface downstream agrees with each other about the wrong answer. A render that dropped a
     * stylesheet is the same shape and records the same way.</p>
     *
     * <p>Public because the <b>render</b> path wants exactly this and not {@link #of(ShSite)}: a
     * page is composed on every request, and deriving the union there would add a content query to
     * every render to tell a template something it can already get from {@code render.defaultLocale}.
     * A declared policy costs no query at all, which is what makes it affordable on that path.</p>
     */
    public Optional<ShSiteLocales> declared(ShSite site) {
        try {
            return ShSiteLocales.parse(site.getLocales(), objectMapper);
        } catch (JacksonException unreadable) {
            incidentLog.record(ShAgentIncidentLog.Kind.CONTENT,
                    ShAddress.site(site.getName()).toString(),
                    "This site's locale policy is not valid JSON, so it is reporting the locales "
                            + "its content happens to be written in instead of the ones it "
                            + "declares: " + unreadable.getOriginalMessage(),
                    "Rewrite it with site.upsert, as "
                            + "{\"locales\":{\"default\":\"en\",\"enabled\":[\"en\",\"pt-br\"]}}, "
                            + "or clear the field to go back to deriving them. Until then a "
                            + "language switcher shows whatever has been translated so far rather "
                            + "than what the site offers.",
                    com.viglet.shio.api.agent.diagnostics.ShAgentIncidentFrames.of(unreadable),
                    site.getId());
            return Optional.empty();
        }
    }

    /** One query for every undeclared site: {@code [siteId, locale]} rows, grouped. */
    private Map<String, Collection<String>> contentLocales(List<String> siteIds,
                                                           Collection<ShPostState> readable) {
        Map<String, Collection<String>> bySite = new LinkedHashMap<>();
        if (readable != null && readable.isEmpty()) {
            return bySite;
        }
        List<Object[]> rows = readable == null
                ? shPostUnifiedRepository.findDistinctLocalesBySiteIdIn(siteIds)
                : shPostUnifiedRepository.findDistinctLocalesBySiteIdInAndStateIn(siteIds, readable);
        for (Object[] row : rows) {
            String siteId = (String) row[0];
            String locale = (String) row[1];
            bySite.computeIfAbsent(siteId, id -> new TreeSet<>()).add(locale);
        }
        return bySite;
    }
}
