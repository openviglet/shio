/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * An entity tag covers every parameter that changes the bytes (SH229).
 *
 * <h2>Why this is a lint and not a test</h2>
 *
 * <p>SH116 wrote the rule down clearly — a tag identifies a <em>representation</em>, so
 * {@code format}, {@code ids} and {@code fields} all belong in it — and SH157 then found
 * {@code include} missing from the context pack's own tag, three blocks later. Two projections of
 * one unchanged pack shared a tag off the same digest, so a caller asking for
 * {@code include=model} could revalidate against a full pack's cached copy and <b>keep a pack with
 * no sitemap in it</b>. Nothing failed, and nothing could have: any single request is internally
 * consistent, so the defect is only visible across two requests that never meet in one test. SH174
 * then derived the same guard by hand for the manifest, and SH159 sidestepped it entirely by
 * hashing the rendered body — which is exact, and is also an admission that getting the
 * discriminator list right by hand is not something to keep doing.</p>
 *
 * <h2>What it checks</h2>
 *
 * <p>Every endpoint taking {@code If-None-Match} falls in one of three buckets, and the bucket is
 * derived from the source rather than declared:</p>
 *
 * <ol>
 *   <li><b>{@link ShAgentBodyEtag}</b> — the tag is over the rendered bytes, so it covers every
 *       parameter by construction. Nothing to check, and this is the shape to prefer.</li>
 *   <li><b>A hand-built {@link ShAgentEtag#of}</b> — every {@code @RequestParam} must reach the
 *       tag's arguments, or be named in {@link #EXEMPT} with a reason.</li>
 *   <li><b>Anything else</b> — the whole endpoint must be named in {@link #ENDPOINT_EXEMPT}. A tag
 *       minted by another mechanism is not wrong, but it is not something this test can read.</li>
 * </ol>
 *
 * <h2>The digest is deliberately opaque</h2>
 *
 * <p>Coverage is traced through local assignments, so {@code Representation.of(format)} stored in
 * {@code representation} and used as {@code representation.name()} counts. <b>A {@code digest()}
 * call counts for nothing</b>, and that is the load-bearing decision here rather than a
 * limitation. It is what makes the lint reproduce SH157: {@code include} <em>did</em> flow into
 * the pack, and the pack's digest was in the tag — but the digest is taken before the projection
 * is applied, so the flow was real and the coverage was not. A lint that trusted dataflow into a
 * digest would have called that bug covered.</p>
 *
 * <p>The cost is that parameters genuinely covered <em>through</em> the digest have to be
 * exempted by hand. That is the right way round: an exemption is a sentence somebody wrote and a
 * reviewer can disagree with, while a silent inference is neither.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
// Code only: every read below is a declaration — an @RequestParam, an ShAgentEtag.of(...) call, an
// assignment. A javadoc paragraph explaining why a parameter is NOT in a tag is exactly the
// sentence this lint would otherwise mistake for the tag containing it (SH613).
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShAgentEtagCoverageLintTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /**
     * Parameters that reach a hand-built tag through a {@code digest()}, keyed {@code method.param}.
     *
     * <p>Each one is a claim that the parameter changes the pack's <b>content</b>, and so moves the
     * digest that is already in the tag. That claim is checkable by a reader and is why these are
     * sentences rather than a bare list.</p>
     */
    private static final Map<String, String> EXEMPT = Map.of(
            "context.site", "selects which sites are in the pack, so it changes the rows the "
                    + "digest is taken over",
            "context.depth", "bounds how far the sitemap walk descends, so it changes the nodes "
                    + "the digest is taken over",
            "context.from", "moves where the walk starts, so it changes the nodes the digest is "
                    + "taken over",
            // The case SH229 named as the interesting one, and it is interesting because the
            // answer is not "it does not change the bytes" — it does. It truncates the walk, and
            // the truncation removes NODES, which are content. What it must not do is move the
            // tag when the instance state is unchanged and only the caller's ceiling differs,
            // which is why the Budget record itself is attached after the digest is taken.
            "context.budget", "truncates the walk, and the nodes it drops are content the digest "
                    + "is taken over — while the Budget record describing the ceiling is attached "
                    + "after the digest, so a narrower ceiling over identical content is the same "
                    + "representation");

    /**
     * Endpoints whose tag comes from neither {@link ShAgentEtag} nor {@link ShAgentBodyEtag}.
     *
     * <p>An endpoint here is out of this lint's reach, not out of the rule. The entry has to say
     * where the tag does come from, so "unreadable by this test" never quietly becomes
     * "unchecked by anything".</p>
     */
    private static final Map<String, String> ENDPOINT_EXEMPT = Map.of(
            "ShStaticFileAPI.serve", "the tag is the image transform cache's own (SH42), minted "
                    + "from the full ShImageTransformSpec — so w/h/format/crop are in it by "
                    + "construction, and `sig` authorises the request rather than selecting a "
                    + "representation");

    private static final Pattern CONDITIONAL = Pattern.compile(
            "public\\s+ResponseEntity<[^>]*>\\s+(\\w+)\\s*\\(([^{]*?)\\)\\s*\\{", Pattern.DOTALL);
    private static final Pattern REQUEST_PARAM = Pattern.compile(
            "@RequestParam\\s*(?:\\([^)]*\\))?\\s*(?:final\\s+)?[\\w.<>\\[\\], ?]+?\\s+(\\w+)\\s*(?:,|$)");
    private static final Pattern TAG_CALL = Pattern.compile("ShAgentEtag\\.of\\((.*?)\\);",
            Pattern.DOTALL);
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "^\\s*(?:final\\s+)?[\\w.<>\\[\\], ?]+\\s+(\\w+)\\s*=\\s*(.+?);\\s*$");
    /** A digest call, whatever it is named. Removed before an argument is read for identifiers. */
    private static final Pattern DIGEST = Pattern.compile(
            "\\w+(?:\\.\\w+)*\\.digest\\(\\s*\\)|\\b\\w*[Dd]igest\\w*\\([^()]*\\)");
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[a-z]\\w*\\b");

    @Test
    @DisplayName("SH229: every conditional endpoint's tag covers every parameter, or says why not")
    void everyConditionalEndpointCoversItsParameters() throws IOException {
        List<String> uncovered = new ArrayList<>();
        List<String> handBuilt = new ArrayList<>();
        int endpoints = 0;

        for (Path file : sources()) {
            String source = ShSourceText.code(Files.readString(file, StandardCharsets.UTF_8));
            String type = file.getFileName().toString().replace(".java", "");
            for (Endpoint endpoint : conditionalEndpoints(source, type)) {
                endpoints++;
                if (ENDPOINT_EXEMPT.containsKey(endpoint.key())) {
                    continue;
                }
                if (endpoint.body().contains("shAgentBodyEtag.")) {
                    // Bucket 1: the tag is the body's hash. Every parameter that changed the bytes
                    // changed the hash, which is the whole point of SH159 and the reason this
                    // lint's hardest bucket is also its smallest.
                    continue;
                }
                Matcher tag = TAG_CALL.matcher(endpoint.body());
                if (!tag.find()) {
                    uncovered.add(endpoint.key() + " — takes If-None-Match and builds no tag this "
                            + "lint can read; add it to ENDPOINT_EXEMPT with the mechanism, or "
                            + "use ShAgentBodyEtag");
                    continue;
                }
                handBuilt.add(endpoint.key());
                Set<String> covered = covered(endpoint, tag.group(1));
                for (String param : endpoint.params()) {
                    if (covered.contains(param) || EXEMPT.containsKey(endpoint.key(param))) {
                        continue;
                    }
                    uncovered.add(endpoint.key(param));
                }
            }
        }

        System.out.printf("%nSH229 — %d conditional endpoint(s), %d with a hand-built tag: %s%n"
                        + "  %d parameter exemption(s), %d endpoint exemption(s)%n%n",
                endpoints, handBuilt.size(), handBuilt, EXEMPT.size(), ENDPOINT_EXEMPT.size());

        assertThat(uncovered)
                .as("""
                        A parameter that changes the bytes and is not in the entity tag lets one \
                        projection revalidate against another's cache — the caller keeps the wrong \
                        representation and every layer answers correctly (SH157 shipped exactly \
                        this, on `include`).

                        Three ways to clear an entry, best first:
                          1. Build the tag with ShAgentBodyEtag, which hashes the rendered bytes \
                        and cannot be wrong about this.
                          2. Add the parameter to the ShAgentEtag.of(...) arguments.
                          3. If it reaches the tag through a digest() — meaning it changes the \
                        CONTENT the digest is taken over — add it to EXEMPT with the sentence \
                        that says so. A digest counts for nothing here on purpose: `include` \
                        flowed into the pack in SH157 and was still missing from the tag, because \
                        the projection is applied after the digest is taken.""")
                .isEmpty();
    }

    // --- source reading ---

    private record Endpoint(String type, String method, List<String> params, String body) {

        String key() {
            return type + "." + method;
        }

        String key(String param) {
            return method + "." + param;
        }
    }

    /**
     * Every source that mentions the header, rather than every file named {@code *API.java}.
     *
     * <p>The narrower filter was the first draft and it is the failure this lint exists to
     * prevent, one level up: a controller that did not happen to be named {@code …API} would have
     * been skipped in silence, and a lint with a blind spot reads exactly like a lint that found
     * nothing. The header is the definition of a conditional read, so the header is the filter.</p>
     */
    private static List<Path> sources() throws IOException {
        try (Stream<Path> walk = Files.walk(MAIN)) {
            return walk.filter(path -> path.toString().endsWith(".java"))
                    .filter(ShAgentEtagCoverageLintTest::mentionsTheHeader)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private static boolean mentionsTheHeader(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).contains("IF_NONE_MATCH");
        } catch (IOException e) {
            throw new IllegalStateException("unreadable source: " + file, e);
        }
    }

    /** The methods that take {@code If-None-Match} — the definition of a conditional read. */
    private static List<Endpoint> conditionalEndpoints(String source, String type) {
        List<Endpoint> found = new ArrayList<>();
        Matcher method = CONDITIONAL.matcher(source);
        while (method.find()) {
            String signature = method.group(2);
            if (!signature.contains("IF_NONE_MATCH")) {
                continue;
            }
            List<String> params = new ArrayList<>();
            Matcher param = REQUEST_PARAM.matcher(signature);
            while (param.find()) {
                params.add(param.group(1));
            }
            found.add(new Endpoint(type, method.group(1), params,
                    body(source, method.end())));
        }
        return found;
    }

    /** From the opening brace to the matching close, so one method's tag is never another's. */
    private static String body(String source, int from) {
        int depth = 1;
        for (int at = from; at < source.length() && depth > 0; at++) {
            char character = source.charAt(at);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(from, at);
                }
            }
        }
        return source.substring(from);
    }

    // --- coverage ---

    /**
     * The parameters that reach the tag's arguments, through local assignments.
     *
     * <p>One pass in source order is enough because Java requires a local to be assigned above its
     * use, so a name's taint is always complete by the time anything reads it.</p>
     */
    private static Set<String> covered(Endpoint endpoint, String arguments) {
        Map<String, Set<String>> taint = new LinkedHashMap<>();
        endpoint.params().forEach(param -> taint.put(param, Set.of(param)));

        for (String line : endpoint.body().split("\\R")) {
            Matcher assignment = ASSIGNMENT.matcher(ShSourceText.codeLine(line));
            if (!assignment.find()) {
                continue;
            }
            Set<String> from = identifiers(assignment.group(2)).stream()
                    .flatMap(name -> taint.getOrDefault(name, Set.of()).stream())
                    .collect(LinkedHashSet::new, Set::add, Set::addAll);
            if (!from.isEmpty()) {
                taint.computeIfAbsent(assignment.group(1), key -> new LinkedHashSet<>())
                        .addAll(from);
            }
        }

        Set<String> covered = new TreeSet<>();
        identifiers(arguments).forEach(name -> covered.addAll(taint.getOrDefault(name, Set.of())));
        return covered;
    }

    /** Identifiers in an expression, with every {@code digest()} call removed first. */
    private static List<String> identifiers(String expression) {
        List<String> names = new ArrayList<>();
        Matcher identifier = IDENTIFIER.matcher(DIGEST.matcher(expression).replaceAll(" "));
        while (identifier.find()) {
            names.add(identifier.group());
        }
        return names;
    }
}
