/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.post.unified;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.TenantId;

/**
 * Unified post row — single table backing both DRAFT and PUBLISHED states.
 *
 * <p>Replaces the pair {@code ShPost / ShPostDraft} plus the EAV tables
 * {@code ShPostAttr / ShPostDraftAttr}. All post-type attributes are stored
 * as a JSON document in {@link #data}, eliminating the N-row-per-post
 * explosion of the legacy model and removing the need to duplicate the
 * schema for draft vs published content.</p>
 *
 * <p>The composite primary key {@code (id, state)} allows a single logical
 * post to hold one independent editable draft row alongside the immutable
 * published row — so editors can keep changing the draft while the public
 * site continues serving the last published snapshot.</p>
 *
 * <p>The JSON payload is stored as a CLOB for portability across H2, MySQL
 * and PostgreSQL. On PostgreSQL the column can later be altered to
 * {@code jsonb} and backed by a GIN index without touching the Java code.</p>
 */
@Entity
@Table(name = "sh_post_unified",
        indexes = {
                @Index(name = "idx_post_unified_folder", columnList = "folder_id, state"),
                @Index(name = "idx_post_unified_type", columnList = "post_type_id, state"),
                @Index(name = "idx_post_unified_furl", columnList = "furl, state"),
                // SH719 — declared HERE as well as in the changelog, and the duplication is the
                // point rather than an oversight. The unit suite runs with `spring.liquibase.enabled
                // =false` and builds its schema from this metadata (`ddl-auto=create-drop`), so a
                // constraint that lived only in Liquibase would not exist in the suite at all: the
                // first version of SH719's test inserted a second row at one address and PASSED,
                // against code whose changelog was correct. A guard the tests cannot see is a guard
                // whose regression nothing reports until production.
                @Index(name = "uk_post_unified_url", columnList = "url_key", unique = true)
        })
@IdClass(ShPostUnifiedId.class)
// SH55 — soft-delete: every Hibernate-generated read of this entity (CDA, console
// navigation, search, listings) automatically excludes trashed rows. Trash
// listing/restore/purge use native queries that bypass this restriction.
@SQLRestriction("deleted_date is null")
@JsonIgnoreProperties({"$$_hibernate_interceptor", "hibernateLazyInitializer"})
public class ShPostUnified implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 16, nullable = false)
    private ShPostState state;

    /**
     * SH22 — tenant discriminator. NOT part of the composite {@code @IdClass}
     * ({@link ShPostUnifiedId}); Hibernate adds {@code tenant_id = ?} as an extra
     * predicate alongside the {@code (id, state)} key. Column lands via
     * {@code v2026.2.4.yaml}; legacy rows are backfilled to 'shio' at startup.
     */
    @TenantId
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_type_id")
    private ShPostType shPostType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private ShFolder shFolder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private ShSite shSite;

    @Column(length = 510)
    private String title;

    @Lob
    private String summary;

    @Column(length = 510)
    private String furl;

    @Column(length = 255)
    private String owner;

    @Column(length = 255)
    private String modifier;

    @Column(length = 255)
    private String publisher;

    private boolean published;

    @Column(name = "publish_status", length = 32)
    private String publishStatus;

    private Instant date;

    private Instant modifiedDate;

    private Instant publicationDate;

    /** When set, the scheduler publishes this draft at/after this instant (SH15). */
    @Column(name = "publish_scheduled_date")
    private Instant publishScheduledDate;

    /** When set, the scheduler unpublishes this post at/after this instant (SH15). */
    @Column(name = "unpublish_scheduled_date")
    private Instant unpublishScheduledDate;

    /**
     * Who set the schedule this row carries (SH991), so the transition the scheduler fires later is
     * attributed to a person as well as to the sweep. Cleared when nothing is left scheduled.
     */
    @Column(name = "schedule_requested_by")
    private String scheduleRequestedBy;

    @Lob
    @Column(name = "data", nullable = false)
    private String data;

    /**
     * SH53 — the language this post is authored in (BCP-47-ish code, e.g.
     * {@code en}, {@code pt-br}), matching a {@link com.viglet.shio.persistence.model.system.ShLocale}
     * {@code initials}. Defaults to {@code en} so every row (including those
     * written by paths that don't set it) carries a locale.
     */
    @Column(name = "locale", length = 16)
    private String locale = "en";

    /**
     * SH53 — groups the sibling translations of one logical piece of content.
     * Standalone/master posts carry their own id here; a translation created via
     * {@code /translate} inherits the source's group id, so all translations of
     * the same content share it. Not part of the {@code (id, state)} key.
     */
    @Column(name = "translation_group_id", length = 36)
    private String translationGroupId;

    /**
     * SH55 — soft-delete (trash) marker. When non-null the post is in the trash:
     * the {@code @SQLRestriction} hides it from every normal read, but the row
     * survives so it can be restored or purged. {@code null} = live.
     */
    @Column(name = "deleted_date")
    private Instant deletedDate;

    /**
     * SH719 — the address this row occupies, as the one value a UNIQUE index can hold.
     *
     * <h2>What it stops</h2>
     *
     * <p>Two connections, one {@code post.upsert} each, the same new address: both answered
     * {@code changed=1}, and the site was left with two posts on one friendly URL — an address no
     * write can name and no read can resolve, removable only by id. The shape is the ordinary
     * check-then-act, and the window is as wide as the lookup; it needs no contention to reach,
     * since one agent retrying a call whose response it never saw is enough. <b>Worse than a lost
     * update</b>, because a lost update loses one write and this keeps both, destroys the address,
     * and tells both callers they won.</p>
     *
     * <h2>Why it is derived here rather than by the writers</h2>
     *
     * <p>Six paths set a furl — create, {@code setFurl}, copy, move, import, restore — and a
     * uniqueness column one of them forgets is a guard that is off exactly where nobody looked. A
     * lifecycle callback runs on all six by construction, which is the same argument
     * {@code ShFolderSite.of} rests on one model over.</p>
     *
     * <h2>Null is the trash, and the exclusion the engines cannot express</h2>
     *
     * <p>SH243 is explicit that a live post may take a <em>trashed</em> post's URL and that the
     * restore is what gets refused, so a trashed row must not reserve its address. A partial index
     * (<code>WHERE deleted_date IS NULL</code>) says exactly that and MariaDB and H2 have none — so
     * the exclusion is carried as NULL, which all three engines treat as distinct under a unique
     * index. A row with no furl is null for the same reason: an uploaded file post has no address
     * (SH38), and "no address" is not one to hold against anybody.</p>
     *
     * <h2>Why a digest</h2>
     *
     * <p>{@code furl} is VARCHAR(510), so a composite index over site + state + furl reaches ~2.4 KB
     * of multi-byte characters — inside MariaDB's 3072-byte limit and uncomfortably near
     * PostgreSQL's ~2704-byte btree row, where a long non-ASCII URL would fail at INSERT, on one
     * engine, in production. 64 hex characters has no such ceiling. The refusal does not read the
     * URL back out of it: the caller sent the address, so the boundary already has it.</p>
     *
     * <p>The <b>site</b> and not the tenant: a site belongs to one tenant and its id is a UUID, so
     * keying on it is tenant-unique without depending on {@code @TenantId} having been populated by
     * the time this callback runs.</p>
     */
    @Column(name = "url_key", length = 64)
    private String urlKey;

    /**
     * Recomputes {@link #urlKey} on every insert and every update — the whole guarantee.
     *
     * <p>{@code getShSite().getId()} does not initialize the lazy proxy: an id is the one property
     * a Hibernate proxy answers without a fetch, which is what keeps this callback from turning
     * every post write into an extra select.</p>
     */
    @PrePersist
    @PreUpdate
    void deriveUrlKey() {
        this.urlKey = urlKeyOf(shSite == null ? null : shSite.getId(), state, locale, furl, deletedDate);
    }

    /**
     * The key a row with this state would occupy, or {@code null} when it occupies no address.
     *
     * <p>Static and public so the SH719 backfill can ask the question without constructing an
     * entity, and so a test can assert the two rows of one post differ — SH245's rule: the second
     * copy of a shared rule is where the careless version grows.</p>
     */
    public static String urlKeyOf(String siteId, ShPostState state, String locale, String furl,
            Instant deletedDate) {
        if (deletedDate != null || siteId == null || furl == null || furl.isBlank()) {
            return null;
        }
        // A separator no component can contain, so two different triples cannot digest to
        // one subject: a site id is a UUID, a state is an enum name, a furl is a URL slug.
        String separator = String.valueOf((char) 0x1F);
        String subject = siteId + separator + state + separator
                + (locale == null ? "" : locale) + separator + furl;
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(subject.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            // SHA-256 is required of every JRE by the platform specification, so this branch is
            // unreachable rather than unhandled. Rethrown rather than degraded: silently writing a
            // null here would turn the uniqueness guarantee off for the whole instance.
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    /** SH719 — read-only; the value is the callback's, never a caller's. */
    public String getUrlKey() {
        return urlKey;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ShPostState getState() {
        return state;
    }

    public void setState(ShPostState state) {
        this.state = state;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public ShPostType getShPostType() {
        return shPostType;
    }

    public void setShPostType(ShPostType shPostType) {
        this.shPostType = shPostType;
    }

    public ShFolder getShFolder() {
        return shFolder;
    }

    public void setShFolder(ShFolder shFolder) {
        this.shFolder = shFolder;
    }

    public ShSite getShSite() {
        return shSite;
    }

    public void setShSite(ShSite shSite) {
        this.shSite = shSite;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getFurl() {
        return furl;
    }

    public void setFurl(String furl) {
        this.furl = furl;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getModifier() { return modifier; }
    public void setModifier(String modifier) { this.modifier = modifier; }

    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public String getPublishStatus() {
        return publishStatus;
    }

    public void setPublishStatus(String publishStatus) {
        this.publishStatus = publishStatus;
    }

    public Instant getDate() {
        return date;
    }

    public void setDate(Instant date) {
        this.date = date;
    }

    public Instant getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(Instant modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public Instant getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(Instant publicationDate) {
        this.publicationDate = publicationDate;
    }

    public Instant getPublishScheduledDate() {
        return publishScheduledDate;
    }

    public void setPublishScheduledDate(Instant publishScheduledDate) {
        this.publishScheduledDate = publishScheduledDate;
    }

    public Instant getUnpublishScheduledDate() {
        return unpublishScheduledDate;
    }

    public void setUnpublishScheduledDate(Instant unpublishScheduledDate) {
        this.unpublishScheduledDate = unpublishScheduledDate;
    }

    public String getScheduleRequestedBy() {
        return scheduleRequestedBy;
    }

    public void setScheduleRequestedBy(String scheduleRequestedBy) {
        this.scheduleRequestedBy = scheduleRequestedBy;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getTranslationGroupId() {
        return translationGroupId;
    }

    public void setTranslationGroupId(String translationGroupId) {
        this.translationGroupId = translationGroupId;
    }

    public Instant getDeletedDate() {
        return deletedDate;
    }

    public void setDeletedDate(Instant deletedDate) {
        this.deletedDate = deletedDate;
    }
}
