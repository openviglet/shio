/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.form;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.blueprint.dto.ShBlueprintManifest;
import com.viglet.shio.persistence.model.form.ShSiteForm;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.token.ShApiToken;
import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.form.ShSiteFormRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.persistence.repository.token.ShApiTokenRepository;
import com.viglet.shio.property.ShAppProperties;
import com.viglet.shio.render.ShSiteHost;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH1387 — an administrator opens a site's forms in one act: the destination, the credential, and
 * the token on every form section that was shipped closed.
 *
 * <p>Opening a form was three calls and an edit: {@code POST /api/v2/site-form}, a FORM token from
 * {@code /api/v2/api-token}, and the token typed into each {@code SectionForm}. A package such as
 * Quilhara ships its forms closed (SH632: a destination a template chooses is one an attacker
 * chooses), so an install from the Marketplace answered nobody until somebody did all four by hand.
 * The package now <em>proposes</em> its destination ({@link ShBlueprintManifest.Forms}), and this is
 * what the administrator's explicit consent does with it.</p>
 *
 * <h2>What it never does</h2>
 *
 * <ul>
 *   <li><b>Overwrite an operator's configuration.</b> A site that already has a form row keeps it as
 *       it is, enabled or not, and the answer says so: the row is the operator's decision.</li>
 *   <li><b>Fill a token somebody already set.</b> Only a form section of a proposed type whose
 *       {@code formToken} is empty is filled.</li>
 *   <li><b>Run as an agent.</b> It is reached only through the Marketplace setup, which is
 *       {@code ROLE_ADMIN}: enabling a form turns an authenticated capability into a public write
 *       path, which {@code ShStatelessReachabilityLintTest} records as the operator's call.</li>
 * </ul>
 */
@Slf4j
@Service
public class ShSiteFormOpener {

    /** The section field that carries the credential, and the one that names the submission's type. */
    static final String TOKEN_FIELD = "formToken";
    static final String TYPE_FIELD = "formType";

    private final ShSiteRepository sites;
    private final ShSiteFormRepository siteForms;
    private final ShApiTokenRepository tokens;
    private final ShPostUnifiedRepository posts;
    private final ShSiteHost siteHost;
    private final ShAppProperties appProperties;
    private final ObjectMapper objectMapper;

    public ShSiteFormOpener(ShSiteRepository sites, ShSiteFormRepository siteForms, ShApiTokenRepository tokens,
                            ShPostUnifiedRepository posts, ShSiteHost siteHost, ShAppProperties appProperties,
                            ObjectMapper objectMapper) {
        this.sites = sites;
        this.siteForms = siteForms;
        this.tokens = tokens;
        this.posts = posts;
        this.siteHost = siteHost;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * What opening did, or on a plan would do.
     *
     * @param folder        where a submission is filed
     * @param types         the post types a submission may be
     * @param origins       the browser origins allowed to submit: the instance's own, the site's host
     * @param opened        whether this answer wrote anything
     * @param configuration {@code created} or {@code kept} (the site already had one, left as it is);
     *                      on a plan, what opening would do
     * @param enabled       whether the site's configuration accepts submissions afterwards
     * @param sections      how many closed form sections were (or would be) given the token; on a plan
     *                      for a site that does not exist yet, {@code null}
     */
    public record Opening(String folder, List<String> types, List<String> origins, boolean opened,
                          String configuration, boolean enabled, Integer sections) {
    }

    /** What {@link #open} would do for this site, writing nothing. */
    @Transactional(readOnly = true)
    public Opening plan(String siteName, ShBlueprintManifest.Forms forms) {
        ShSite site = siteName == null ? null : sites.findByName(siteName);
        if (site == null) {
            return new Opening(forms.folder(), forms.types(), origins(null), false, "created", true, null);
        }
        Optional<ShSiteForm> existing = siteForms.findFirstBySiteIdOrderByIdAsc(site.getId());
        return new Opening(forms.folder(), forms.types(), origins(site), false,
                existing.isPresent() ? "kept" : "created",
                existing.map(ShSiteForm::isEnabled).orElse(true), closedSections(site, forms).size());
    }

    /** Open the site's forms with the package's proposed destination; see the class note for limits. */
    @Transactional
    public Opening open(String siteName, ShBlueprintManifest.Forms forms, String actor) {
        ShSite site = sites.findByName(siteName);
        if (site == null) {
            throw new IllegalStateException("no site named '" + siteName + "' to open forms on");
        }
        List<String> origins = origins(site);
        Optional<ShSiteForm> existing = siteForms.findFirstBySiteIdOrderByIdAsc(site.getId());
        ShSiteForm config = existing.orElseGet(() -> {
            ShSiteForm created = new ShSiteForm();
            created.setSiteId(site.getId());
            created.setFolderUrl(forms.folder());
            created.setAllowedTypes(String.join(",", forms.types()));
            created.setAllowedOrigins(String.join(",", origins));
            created.setEnabled(true);
            created.setOwner(actor == null ? "system" : actor);
            created.setCreatedDate(Instant.now());
            return siteForms.saveAndFlush(created);
        });
        String token = formTokenOf(site, actor);
        List<ShPostUnified> closed = closedSections(site, forms);
        for (ShPostUnified section : closed) {
            ObjectNode data = (ObjectNode) objectMapper.readTree(section.getData());
            data.put(TOKEN_FIELD, token);
            section.setData(objectMapper.writeValueAsString(data));
            posts.save(section);
        }
        log.info("Opened the forms of site '{}' -> folder '{}', types {}, {} section(s), configuration {}, by {}",
                site.getName(), config.getFolderUrl(), config.getAllowedTypes(), closed.size(),
                existing.isPresent() ? "kept" : "created", actor);
        return new Opening(forms.folder(), forms.types(), origins, true,
                existing.isPresent() ? "kept" : "created", config.isEnabled(), closed.size());
    }

    /** The site's enabled FORM token, or a new one: one standing credential per site, revocable in the console. */
    private String formTokenOf(ShSite site, String actor) {
        for (ShApiToken token : tokens.findByEphemeralFalseOrderByTitleAsc()) {
            if (token.getScope() == ShApiTokenScope.FORM && token.isEnabled()
                    && token.getSiteIds() != null && token.getSiteIds().contains(site.getId())) {
                return token.getToken();
            }
        }
        ShApiToken token = new ShApiToken();
        token.setTitle("form:" + site.getName());
        token.setDescription("The site's forms, opened from the Marketplace (SH1387)");
        token.setToken(UUID.randomUUID().toString().replace("-", "").substring(0, 25));
        token.setOwner(actor == null ? "system" : actor);
        token.setCreatedDate(Instant.now());
        token.setEnabled(true);
        token.setScope(ShApiTokenScope.FORM);
        token.setEnv(ShApiTokenEnv.PROD);
        token.setSiteIds(Set.of(site.getId()));
        tokens.saveAndFlush(token);
        return token.getToken();
    }

    /** Every draft and published row of a form section of a proposed type whose token is empty. */
    private List<ShPostUnified> closedSections(ShSite site, ShBlueprintManifest.Forms forms) {
        List<ShPostUnified> closed = new ArrayList<>();
        for (ShPostUnified post : posts.findByShSite_IdAndStateIn(site.getId(),
                List.of(ShPostState.DRAFT, ShPostState.PUBLISHED))) {
            String raw = post.getData();
            if (raw == null || !raw.strip().startsWith("{") || !raw.contains("\"" + TYPE_FIELD + "\"")) {
                continue;
            }
            JsonNode data = objectMapper.readTree(raw);
            if (!data.isObject() || !data.path(TYPE_FIELD).isString()
                    || !forms.types().contains(data.path(TYPE_FIELD).asString())) {
                continue;
            }
            JsonNode token = data.get(TOKEN_FIELD);
            if (token == null || token.isNull() || (token.isString() && token.asString().isBlank())) {
                closed.add(post);
            }
        }
        return closed;
    }

    /**
     * Where the site's pages are read from: the instance itself ({@code shio.url}, for {@code /sites}
     * and the preview), the site's own host when it has one (SH1354), and its own front end's origin.
     */
    private List<String> origins(ShSite site) {
        Set<String> origins = new LinkedHashSet<>();
        originOf(appProperties.getUrl()).ifPresent(origins::add);
        if (site != null) {
            siteHost.originOf(site).ifPresent(origins::add);
            originOf(site.getUrl()).ifPresent(origins::add);
        }
        return List.copyOf(origins);
    }

    private static Optional<String> originOf(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        java.util.regex.Matcher origin = java.util.regex.Pattern.compile("(?i)^(https?://[^/?#\\s]+)").matcher(url.trim());
        return origin.find() ? Optional.of(origin.group(1).toLowerCase(java.util.Locale.ROOT)) : Optional.empty();
    }
}
