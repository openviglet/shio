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
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.site.ShSite;

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
import jakarta.persistence.Table;

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
                @Index(name = "idx_post_unified_furl", columnList = "furl, state")
        })
@IdClass(ShPostUnifiedId.class)
@JsonIgnoreProperties({"$$_hibernate_interceptor", "hibernateLazyInitializer"})
public class ShPostUnified implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 16, nullable = false)
    private ShPostState state;

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

    private boolean published;

    @Column(name = "publish_status", length = 32)
    private String publishStatus;

    private Instant date;

    private Date modifiedDate;

    private Date publicationDate;

    @Lob
    @Column(name = "data", nullable = false)
    private String data;

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

    public Date getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(Date modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public Date getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(Date publicationDate) {
        this.publicationDate = publicationDate;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
