/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site.dto;

/**
 * Request DTO for console site create/update (SonarQube java:S4684 — never bind a
 * persistence entity straight off the wire). Component names mirror the exact JSON
 * field names the {@code ShSite} entity used to bind, so the React console payload
 * still binds unchanged. Only the fields {@code ShSiteWriteService.create/update}
 * read off the entity are mirrored here:
 * <ul>
 *   <li>{@code name} — {@code site.setName(payload.getName())} and the controller's
 *       blank/conflict checks.</li>
 *   <li>{@code description} — {@code site.setDescription(payload.getDescription())}.</li>
 *   <li>{@code url} — {@code site.setUrl(payload.getUrl())}.</li>
 *   <li>{@code furl} — {@code payload.getFurl()} (slugified from name when blank on create;
 *       only applied when present on update).</li>
 *   <li>{@code postTypeLayout} — {@code site.setPostTypeLayout(payload.getPostTypeLayout())}.</li>
 *   <li>{@code searchablePostTypes} — {@code site.setSearchablePostTypes(payload.getSearchablePostTypes())}.</li>
 *   <li>{@code formSuccess} — {@code site.setFormSuccess(payload.getFormSuccess())}.</li>
 *   <li>{@code pageType} — SH1303: the SH1283 page type, missing here for the same reason and with
 *       the same effect: the console's select saved nothing and every console save cleared it.</li>
 *   <li>{@code membersLogin} — SH1302: the SH1299 switch. Missing here, the console could not set
 *       it, and every console save of a site cleared the one an agent had set, because the update
 *       replaces it from this payload.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 */
public record ShSiteRequest(
        String name,
        String description,
        String url,
        String furl,
        String postTypeLayout,
        String searchablePostTypes,
        String formSuccess,
        Boolean membersLogin,
        String pageType) {

    /** The eight-field form, before SH1303 carried the page type. */
    public ShSiteRequest(String name, String description, String url, String furl, String postTypeLayout,
                         String searchablePostTypes, String formSuccess, Boolean membersLogin) {
        this(name, description, url, furl, postTypeLayout, searchablePostTypes, formSuccess, membersLogin, null);
    }

    /** The seven-field form, before SH1302 carried the members sign-in switch. */
    public ShSiteRequest(String name, String description, String url, String furl, String postTypeLayout,
                         String searchablePostTypes, String formSuccess) {
        this(name, description, url, furl, postTypeLayout, searchablePostTypes, formSuccess, null, null);
    }
}
