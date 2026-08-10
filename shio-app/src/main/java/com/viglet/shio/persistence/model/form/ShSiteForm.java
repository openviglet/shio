/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.form;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Where a site's form submissions land, and which post-types it invites (SH632).
 *
 * <h2>Site configuration, and never template content</h2>
 *
 * <p>2018's {@code ShFormConfiguration} carried {@code folder} as a <b>template</b> setting. That is
 * the one part of the 2018 answer SH364 does not adopt, and the reason is a sentence: <b>a folder
 * chosen in a template is a folder an attacker chooses</b>, because a template is content and
 * content is what a compromised author edits. So the destination is configuration <em>of the
 * site</em>, reachable by an operator and not by a page.</p>
 *
 * <p>A table keyed by site rather than columns on {@code ShSite}, following
 * {@code site_webhook} — the precedent for the same reason it exists there: per-site delivery
 * configuration that an operator owns, which a site does not need to carry to be a site.</p>
 *
 * <h2>What it deliberately does not hold</h2>
 *
 * <p>No retention policy, no submission store, no notification settings. A submission <b>is a
 * post</b>, so the trash, the export, the ACL and the agent surface already apply to it — that was
 * SH364's third question and this is where it resolves. What a row here says is only: which folder,
 * which types, and whether the site is accepting at all.</p>
 *
 * <p>{@code siteId} is a plain column and not an association. Navigating past {@code .id} of an
 * {@code ShObject} subclass emits the {@code @TenantId} predicate against an alias with no such
 * column, which is valid JPQL that dies at query-prepare time — the same reason
 * {@code ShSiteWebhook} keys by id.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Entity
@Table(name = "sh_site_form")
public class ShSiteForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @TenantId
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;

    /** The site this configures. One row per site is the intent; the API enforces it. */
    @Column(name = "site_id", length = 255)
    private String siteId;

    /**
     * The folder a submission lands in, as a name chain within the site ({@code /contact}).
     *
     * <p>A path rather than a folder id: an id is unreadable in an operator's configuration screen
     * and does not survive an export/import, which is the same argument SH183 makes for resolving a
     * theme by title. The folder must exist — SH633's endpoint refuses rather than creating one,
     * because a public path that can create folders is a public path that can fill a disk.</p>
     */
    @Column(name = "folder_url", length = 1024)
    private String folderUrl;

    /**
     * The post-type names this site invites submissions of, comma-separated.
     *
     * <p>Stored as text and read through {@code ShFormEligibility}, which intersects it with "not a
     * system type" — so a row naming {@code Theme} is inert rather than dangerous. **Empty means the
     * site invites nothing**, which is the direction every default on this path takes (P7): a
     * configuration row that exists but has not been filled in must not be an open door.</p>
     */
    @Column(name = "allowed_types", length = 2048)
    private String allowedTypes;

    /**
     * Whether this site accepts submissions at all.
     *
     * <p>Defaults to {@code false} so creating the row is not the same act as opening the door —
     * an operator configures the folder and the types, looks at it, and then turns it on.</p>
     */
    @Column(name = "enabled")
    private boolean enabled;

    /**
     * Which browser origins may spend this site's form token, comma-separated (SH637).
     *
     * <p>The <b>control</b>, and CORS is not: CORS protects the browser and a non-browser client
     * ignores it, so anything enforced only there is enforced only against callers who were going
     * to behave. This is checked against the request's {@code Origin} in the endpoint, where the
     * tenant is resolved and every caller is subject to it.</p>
     *
     * <p>Per site rather than per instance because a form token names one site — a global list
     * would let any configured origin post to every site's form, which is the scope SH631 spent a
     * task bounding. <b>Empty means no browser origin</b>, not "any": a request with no
     * {@code Origin} at all (curl, a server-to-server call) is unaffected, because that header is
     * something a browser adds and not something a caller can be trusted to omit honestly.</p>
     */
    @Column(name = "allowed_origins", length = 2048)
    private String allowedOrigins;

    @Column(name = "created_date")
    private Instant createdDate;

    @Column(name = "owner", length = 255)
    private String owner;
}
