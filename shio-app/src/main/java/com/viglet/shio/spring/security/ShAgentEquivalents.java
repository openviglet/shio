/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring.security;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Which agent op covers a console write a session-less caller cannot make (SH1381).
 *
 * <p>A session-less 403 used to name the same five ops whatever was refused, so a device token
 * refused on {@code …/revisions/{v}/restore} was pointed away from {@code post.rollback}, the op that
 * does exactly that. This table is what the refusal reads instead. It is also what
 * {@code ShStatelessReachabilityLintTest} reads for the verbs each controller's agent equivalence
 * covers (SH463), so the inventory and the refusal are one list and cannot disagree.</p>
 *
 * <p>Ordered, most specific first, because a sub-path like {@code /{id}/publish} shares its root and
 * its method with the plain write. A route with no op here (a purge, a translation, a bulk edit) has
 * no agent equivalent, and its refusal keeps the generic text.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentEquivalents {

    private ShAgentEquivalents() {
    }

    /**
     * One console route and the op that does its job by address.
     *
     * @param root    the controller root the SH339 inventory classifies
     * @param method  the HTTP method
     * @param route   the path under the root, {@code *} standing for one segment
     * @param op      the agent op
     * @param example one op, as {@code POST /api/v2/agent/batch} takes it
     */
    public record Equivalent(String root, String method, String route, String op, String example) {

        boolean matches(String method, String path) {
            return this.method.equalsIgnoreCase(method) && pattern().matcher(path).matches();
        }

        private Pattern pattern() {
            String full = root + route;
            StringBuilder regex = new StringBuilder();
            for (String part : full.split("\\*", -1)) {
                if (!regex.isEmpty()) {
                    regex.append("[^/]+");
                }
                regex.append(Pattern.quote(part));
            }
            return Pattern.compile(regex + "/?");
        }
    }

    private static final String POST_ROOT = "/api/v2/post-unified";
    private static final String FOLDER_ROOT = "/api/v2/folder";
    private static final String OBJECT_ROOT = "/api/v2/object";
    private static final String SITE_ROOT = "/api/v2/site";
    private static final String NOTE_ROOT = "/api/v2/note";

    private static final String POST = "\"address\":\"post:<site>/<friendly-url>\"";
    private static final String FOLDER = "\"address\":\"folder:<site>/<path>\"";

    private static String op(String name, String rest) {
        return "{\"op\":\"" + name + "\"," + rest + "}";
    }

    private static final List<Equivalent> TABLE = List.of(
            // post-unified: the sub-paths first, since they share the root and the method.
            new Equivalent(POST_ROOT, "POST", "/*/revisions/*/restore", "post.rollback",
                    op("post.rollback", POST + ",\"revision\":3")),
            new Equivalent(POST_ROOT, "POST", "/*/publish", "post.publish", op("post.publish", POST)),
            new Equivalent(POST_ROOT, "POST", "/*/schedule", "post.publish",
                    op("post.publish", POST + ",\"when\":\"2026-12-01T09:00:00Z\"")),
            new Equivalent(POST_ROOT, "POST", "/*/unpublish", "post.unpublish", op("post.unpublish", POST)),
            new Equivalent(POST_ROOT, "POST", "/*/move", "post.move",
                    op("post.move", POST + ",\"to\":\"folder:<site>/<path>\"")),
            new Equivalent(POST_ROOT, "POST", "/*/untrash", "post.restore", op("post.restore", POST)),
            new Equivalent(POST_ROOT, "POST", "", "post.upsert",
                    op("post.upsert", POST + ",\"type\":\"<PostType>\",\"data\":{}")),
            new Equivalent(POST_ROOT, "PUT", "/*", "post.upsert",
                    op("post.upsert", POST + ",\"type\":\"<PostType>\",\"data\":{}")),
            new Equivalent(POST_ROOT, "PATCH", "/*", "post.upsert",
                    op("post.upsert", POST + ",\"type\":\"<PostType>\",\"data\":{}")),
            new Equivalent(POST_ROOT, "DELETE", "/*", "post.delete", op("post.delete", POST)),
            // folder
            new Equivalent(FOLDER_ROOT, "POST", "/*/untrash", "folder.restore", op("folder.restore", FOLDER)),
            new Equivalent(FOLDER_ROOT, "POST", "/*", "folder.upsert", op("folder.upsert", FOLDER)),
            new Equivalent(FOLDER_ROOT, "PUT", "/*", "folder.upsert", op("folder.upsert", FOLDER)),
            new Equivalent(FOLDER_ROOT, "DELETE", "/*", "folder.delete", op("folder.delete", FOLDER)),
            // object: the console's copy and move gestures, and the two per-object settings.
            new Equivalent(OBJECT_ROOT, "PUT", "/moveto/*", "post.move",
                    op("post.move", POST + ",\"to\":\"folder:<site>/<path>\"")),
            new Equivalent(OBJECT_ROOT, "PUT", "/copyto/*", "post.upsert",
                    op("post.upsert", POST + ",\"type\":\"<PostType>\",\"data\":{}")),
            new Equivalent(OBJECT_ROOT, "PUT", "/*/visibility", "post.visibility",
                    op("post.visibility", POST + ",\"visibility\":\"MEMBERS\"")),
            new Equivalent(OBJECT_ROOT, "PUT", "/*/acl", "folder.acl", op("folder.acl", FOLDER)),
            // site
            new Equivalent(SITE_ROOT, "POST", "/*/untrash", "site.restore",
                    op("site.restore", "\"address\":\"site:<name>\"")),
            new Equivalent(SITE_ROOT, "POST", "", "site.upsert", op("site.upsert", "\"address\":\"site:<name>\"")),
            new Equivalent(SITE_ROOT, "PUT", "/*/locales", "site.upsert",
                    op("site.upsert", "\"address\":\"site:<name>\",\"data\":{\"locales\":{}}")),
            new Equivalent(SITE_ROOT, "PUT", "/*", "site.upsert", op("site.upsert", "\"address\":\"site:<name>\"")),
            new Equivalent(SITE_ROOT, "DELETE", "/*", "site.delete", op("site.delete", "\"address\":\"site:<name>\"")),
            // note
            new Equivalent(NOTE_ROOT, "POST", "/*/resolve", "note.resolve",
                    op("note.resolve", POST + ",\"note\":\"<id>\"")),
            new Equivalent(NOTE_ROOT, "POST", "", "note.add", op("note.add", POST + ",\"body\":\"…\"")));

    /** Every row, in matching order. */
    public static List<Equivalent> all() {
        return TABLE;
    }

    /**
     * The op covering this request, if any. Purge, translate and bulk have none on purpose: a purge
     * past the trash and a bulk edit are the console's, and translation is SH342's open half.
     */
    public static Optional<Equivalent> of(String method, String path) {
        if (method == null || path == null) {
            return Optional.empty();
        }
        if (path.endsWith("/purge") || path.endsWith("/bulk") || path.endsWith("/translate")
                || path.matches(".*/post-unified/[^/]+/restore/?")
                || path.matches(".*/site/[^/]+/locales/?") && "DELETE".equalsIgnoreCase(method)) {
            return Optional.empty();
        }
        return TABLE.stream().filter(row -> row.matches(method, path)).findFirst();
    }

    /** SH463 — the verbs a controller root's agent equivalence covers, read by the inventory lint. */
    public static Set<String> verbsOf(String root) {
        Set<String> verbs = new TreeSet<>();
        TABLE.stream().filter(row -> row.root().equals(root)).forEach(row -> verbs.add(row.method()));
        return verbs;
    }
}
