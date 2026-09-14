/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.lint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One law, asked of every path, with each answer falsifiable (SH982/SH983).
 *
 * <h2>Why this is a helper and not a second copy</h2>
 *
 * <p>SH982 asked P7 of every mutating path and SH983 asks P9 of the same set, in the same shape: an
 * axis is either <b>present</b>, naming the file and the token that prove it, or <b>absent</b>,
 * naming the same pair and claiming the token is not there. The second half is what makes a census
 * worth keeping — a path that gains a dry run, or starts returning a digest, fails the build until
 * its row moves — and it is the half that would have been copied approximately.</p>
 *
 * <p>The evidence is a token in a file rather than a behaviour, deliberately: the question is what
 * the code offers, a booted context cannot tell a caller which guards it has, and a census nobody
 * can run while editing is a census nobody runs.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShGuardCensus {

    private ShGuardCensus() {
        // utility
    }

    /**
     * One axis of one law on one path.
     *
     * @param present whether the guard is there
     * @param file    the file that proves it, or would
     * @param token   what that file must contain, or must not
     * @param why     for an absence, the reason — which is the whole value of the row
     */
    public record Axis(boolean present, String file, String token, String why) {
    }

    /** The guard is there, and this file carries the token that proves it. */
    public static Axis yes(String file, String token) {
        return new Axis(true, file, token, null);
    }

    /** The guard is not there, this is why, and the file must not contain the token. */
    public static Axis no(String file, String token, String why) {
        return new Axis(false, file, token, why);
    }

    /**
     * What the source says about one path's axes, as problems a caller asserts empty.
     *
     * @param path  the path being classified, for the message
     * @param axes  axis name → claim
     * @return one line per disagreement between a claim and the tree
     */
    public static List<String> problems(String path, Map<String, Axis> axes) {
        Map<String, String> sources = ShControllerScan.sources();
        List<String> problems = new ArrayList<>();
        axes.forEach((axis, claim) -> {
            String source = sources.get(claim.file());
            if (source == null) {
                problems.add("%s %s: names %s, which is not in the tree"
                        .formatted(path, axis, claim.file()));
                return;
            }
            boolean found = source.contains(claim.token());
            if (claim.present() && !found) {
                problems.add("%s %s: claims %s carries `%s` and it does not"
                        .formatted(path, axis, claim.file(), claim.token()));
            }
            if (!claim.present() && found) {
                problems.add("%s %s: says there is none, and %s now carries `%s` — the guard arrived "
                        .formatted(path, axis, claim.file(), claim.token())
                        + "and this row did not move (SH142)");
            }
            if (!claim.present() && (claim.why() == null || claim.why().isBlank())) {
                problems.add(path + " " + axis + ": absent with no reason");
            }
        });
        return problems;
    }
}
