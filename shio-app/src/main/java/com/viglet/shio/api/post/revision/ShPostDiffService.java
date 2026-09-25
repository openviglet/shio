/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.revision;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.post.ShContentPerspective;
import com.viglet.shio.api.post.unified.ShConsolePostReadService;
import com.viglet.shio.api.post.unified.ShPostStateNotFoundException;
import com.viglet.shio.api.post.unified.dto.ShConsolePost;

import tools.jackson.databind.JsonNode;

/**
 * SH988 — what changed between two versions of a page, answered once, on the server.
 *
 * <p>The version dialog compared the draft row with the published one and drew two walls of text,
 * and the review queue offered a diff only when a published twin existed, so a created page, the
 * commonest thing an agent writes, had no diff at all. This answers the question for every reader
 * at once: the two sides as whole documents, and the field list with a changed flag, so the console
 * and any other caller cannot disagree about what changed.</p>
 *
 * <p>A side is {@code draft}, {@code published} or a revision, spelled as {@code state=} spells it. {@code published} on a page
 * that was never published is {@code null}, not an error: that is a create, and the console draws
 * the whole of the other side as additions. The field list is the union of both sides' keys in
 * name order (P5), and a field is changed when its two values differ as JSON.</p>
 */
@Service
public class ShPostDiffService {

    public static final String DRAFT = "draft";
    public static final String PUBLISHED = "published";

    /** One field of the comparison. */
    public record Field(String name, boolean changed) {
    }

    /** The comparison: which two sides, each whole, and what changed between them. */
    public record ShPostDiff(String from, String to, JsonNode before, JsonNode after, List<Field> fields) {
    }

    private final ShConsolePostReadService readService;
    private final ShPostRevisionService revisionService;

    public ShPostDiffService(ShConsolePostReadService readService, ShPostRevisionService revisionService) {
        this.readService = readService;
        this.revisionService = revisionService;
    }

    @Transactional(readOnly = true)
    public ShPostDiff diff(String id, String from, String to) {
        String fromSide = from == null || from.isBlank() ? PUBLISHED : from.trim();
        String toSide = to == null || to.isBlank() ? DRAFT : to.trim();
        List<ShConsolePost> rows = readService.versions(id);
        JsonNode before = side(id, fromSide, rows, "from");
        JsonNode after = side(id, toSide, rows, "to");
        return new ShPostDiff(fromSide, toSide, before, after, fields(before, after));
    }

    /**
     * One side, read through the grammar every {@code state=} uses (SH990), so a revision is spelled
     * one way everywhere and a typo gets that grammar's refusal. A release names a moment rather
     * than a version of this page's body, so it is refused here with the sides this does compare.
     */
    private JsonNode side(String id, String side, List<ShConsolePost> rows, String parameter) {
        ShContentPerspective perspective = ShContentPerspective.parse(side);
        if (perspective.revision() != null) {
            int version = perspective.revision();
            return revisionService.row(id, version).map(ShRevisionRow::data)
                    .orElseThrow(() -> ShPostStateNotFoundException.noSuchRevision(id, version,
                            revisionService.rows(id).stream().map(ShRevisionRow::version).toList()));
        }
        if (perspective.row() == null) {
            throw new UnknownSideException(parameter, side);
        }
        String state = perspective.row().name();
        return rows.stream().filter(row -> state.equals(row.state())).findFirst()
                .map(ShConsolePost::data).orElse(null);
    }

    private static List<Field> fields(JsonNode before, JsonNode after) {
        TreeSet<String> names = new TreeSet<>();
        if (before != null) names.addAll(before.propertyNames());
        if (after != null) names.addAll(after.propertyNames());
        List<Field> fields = new ArrayList<>(names.size());
        for (String name : names) {
            JsonNode was = before == null ? null : before.get(name);
            JsonNode is = after == null ? null : after.get(name);
            fields.add(new Field(name, !Objects.equals(was, is)));
        }
        return fields;
    }

    /**
     * A release named as a side. A typo never reaches this: the {@code state=} grammar refuses it
     * with its own allowed list and nearest match. A release is a moment across a site rather than
     * a version of this page, so it is the one valid spelling a page diff does not take.
     */
    public static class UnknownSideException extends ShTeachingException {

        public UnknownSideException(String parameter, String side) {
            super(ShProblem.of("unknown-diff-side", HttpStatus.BAD_REQUEST,
                            "'" + side + "' is not a version of this page")
                    .withDetail("A release names a moment across a site, not one version of a page.")
                    .withAt(parameter)
                    .withFix("Compare the draft, the published row, or a revision by its number;"
                            + " read the page as a release found it with GET /api/v2/agent/read?state=release:<name>.")
                    .withExample("?from=published&to=draft"));
        }
    }
}
