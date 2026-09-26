/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.auth.view;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.view.ShSavedView;
import com.viglet.shio.persistence.repository.view.ShSavedViewRepository;

/**
 * Saved views (SH1024): one list's named URLs, per person, optionally shared with the tenant.
 *
 * <p>Saving is by name: a second save under a name the person already used replaces the first, so
 * "save" can be pressed twice without making two (P5). Only the owner deletes one, shared or not:
 * sharing offers a view, it does not hand it over.</p>
 */
@Service
public class ShSavedViewService {

    /** The lists that draw a filter bar, and so have a view to save. */
    public static final List<String> LISTS = List.of("browser", "search", "trash", "review");

    private final ShSavedViewRepository repository;
    private final Clock clock;

    public ShSavedViewService(ShSavedViewRepository repository) {
        this.repository = repository;
        this.clock = Clock.systemUTC();
    }

    /** What one person reads for one list: their own views and the shared ones. */
    public record Row(String id, String name, String path, String query, boolean shared, boolean mine) {
    }

    /** What a save sends. */
    public record Save(String list, String name, String path, String query, boolean shared) {
    }

    @Transactional(readOnly = true)
    public List<Row> visible(String owner, String list) {
        requireList(list);
        return repository.visible(list, owner).stream().map(view -> row(view, owner)).toList();
    }

    @Transactional
    public Row save(String owner, Save save) {
        requireList(save.list());
        String name = save.name() == null ? "" : save.name().trim();
        if (name.isEmpty() || name.length() > 100) {
            throw new InvalidViewException("name", "a name of 1 to 100 characters", "Drafts this week");
        }
        String path = save.path() == null ? "" : save.path().trim();
        if (!path.startsWith("/") || path.length() > 512) {
            throw new InvalidViewException("path", "the console path the view opens, starting with /",
                    "/console/content/search");
        }
        String query = save.query() == null ? "" : save.query().trim();
        query = query.startsWith("?") ? query.substring(1) : query;
        if (query.length() > 2048) {
            throw new InvalidViewException("query", "a query string of at most 2048 characters",
                    "state=draft&modifiedSince=2026-09-01");
        }
        ShSavedView view = repository.findByOwnerAndListKeyAndName(owner, save.list(), name)
                .orElseGet(ShSavedView::new);
        if (view.getId() == null) {
            view.setOwner(owner);
            view.setListKey(save.list());
            view.setName(name);
            view.setCreatedAt(Instant.now(clock));
        }
        view.setPath(path);
        view.setQuery(query);
        view.setShared(save.shared());
        return row(repository.save(view), owner);
    }

    @Transactional
    public void delete(String owner, String id) {
        ShSavedView view = repository.findById(id).orElseThrow(() -> new UnknownViewException(id));
        if (!owner.equals(view.getOwner())) {
            throw new NotYoursException(view.getName(), view.getOwner());
        }
        repository.delete(view);
    }

    private static Row row(ShSavedView view, String owner) {
        return new Row(view.getId(), view.getName(), view.getPath(),
                view.getQuery() == null ? "" : view.getQuery(), view.isShared(), owner.equals(view.getOwner()));
    }

    private static void requireList(String list) {
        if (list == null || !LISTS.contains(list)) {
            throw new UnknownListException(list);
        }
    }

    // --- refusals, each an instruction (P6) ---

    /** A list that draws no filter bar has no view to save. */
    public static class UnknownListException extends ShTeachingException {

        public UnknownListException(String sent) {
            super(ShProblem.of("unknown-view-list", HttpStatus.BAD_REQUEST,
                            "No list named '" + (sent == null ? "" : sent) + "' keeps saved views")
                    .withDetail("A saved view belongs to the list whose filter bar saved it.")
                    .withAllowed(LISTS)
                    .withDidYouMeanFrom(sent)
                    .withFix("Send list as one of: " + String.join(", ", LISTS) + ".")
                    .withExample("GET /api/v2/user/current/views?list=browser"));
        }
    }

    /** One field of a save that cannot be stored as sent. */
    public static class InvalidViewException extends ShTeachingException {

        public InvalidViewException(String field, String expected, String example) {
            super(ShProblem.of("invalid-saved-view", HttpStatus.BAD_REQUEST,
                            "A saved view's " + field + " must be " + expected)
                    .withFix("Send " + field + " as " + expected + ".")
                    .withExample(field + ": " + example));
        }
    }

    /** No such view — deleted already, or never saved. */
    public static class UnknownViewException extends ShTeachingException {

        public UnknownViewException(String id) {
            super(ShProblem.of("unknown-saved-view", HttpStatus.NOT_FOUND,
                            "No saved view with id '" + id + "'")
                    .withFix("Read the list's views with GET /api/v2/user/current/views?list=<list>."));
        }
    }

    /** Sharing offers a view to the tenant; it does not hand it over. */
    public static class NotYoursException extends ShTeachingException {

        public NotYoursException(String name, String owner) {
            super(ShProblem.of("saved-view-not-yours", HttpStatus.FORBIDDEN,
                            "'" + name + "' is " + owner + "'s view")
                    .withDetail("A shared view is offered to everyone in the tenant and stays its "
                            + "owner's: only they can delete it.")
                    .withFix("Ask " + owner + " to delete it, or save your own under another name."));
        }
    }
}
