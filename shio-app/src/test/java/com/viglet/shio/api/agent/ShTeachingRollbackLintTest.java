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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH345 — <b>a read-only transaction that throws a teaching exception must not mark itself
 * rollback-only.</b>
 *
 * <h2>The defect this was written from</h2>
 *
 * <p>{@code GET /api/v2/agent/find?site=nope} answered a <b>500</b>, not SH80's teaching 404.
 * {@code ShAgentSiteAccess} is {@code @Transactional(readOnly = true)} and throws
 * {@code UnknownSiteException}; as a <em>participating</em> transaction its interceptor called
 * {@code setRollbackOnly()}, and {@code ShAgentReadService.find} — which declares
 * {@code noRollbackFor} exactly to avoid this — then failed at <b>commit</b> with
 * {@code UnexpectedRollbackException}. The carefully designed refusal, its `fix` and its list of
 * readable sites were all destroyed by a transaction attribute one bean away, and the failure surfaced
 * nowhere near its cause.</p>
 *
 * <p>SH171 diagnosed exactly this shape for {@code ShAddressResolver} and fixed it there; SH249 did
 * the same for {@code ShPageRenderService.render}. Nobody asked which <em>other</em> beans had it —
 * which is SH322's lesson, and why this lint exists rather than a third one-line fix.</p>
 *
 * <h2>Why the rule is safe as an absolute</h2>
 *
 * <p>On a <b>read-only</b> transaction there is nothing to roll back, so marking it rollback-only can
 * only take away a caller's ability to report the failure. {@code noRollbackFor} is therefore always
 * correct there, and its absence is always a latent version of the defect above — a trap that springs
 * the first time somebody composes the read inside another one.</p>
 *
 * <p>A <b>writing</b> transaction is the exact opposite and is deliberately out of scope: a refused
 * batch must roll back, and that is what makes {@code ShAgentBatchService} atomic. So the lint keys on
 * {@code readOnly = true} and says nothing about the others.</p>
 *
 * <h2>The set of teaching exceptions is derived, never listed</h2>
 *
 * <p>Every {@code class X extends ShTeachingException} in the tree, found by scanning. A hand-kept
 * list is what SH260 spent a task removing for helpers and SH300 for DOM attributes, and it fails the
 * same way here: a new refusal type nobody adds to the list is a bean this lint stops covering, with
 * nothing failing.</p>
 *
 * @author Alexandre Oliveira
 */
class ShTeachingRollbackLintTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "viglet", "shio");

    private static final Pattern TEACHING_SUBCLASS = Pattern.compile(
            "class\\s+(\\w+)\\s+extends\\s+ShTeachingException\\b");

    /**
     * A <b>class-level</b> {@code @Transactional(readOnly = true)}, keyed on column 0.
     *
     * <p>Indentation is the signal because it is the one that is right for the wrong-looking reason:
     * a class annotation is unindented and a method annotation is not, throughout this codebase. The
     * alternative — matching any read-only annotation in the file — was tried first and flags
     * {@code ShPostTypeAdminService} and {@code ShRenderPostTypes}, whose read methods are read-only
     * and whose <em>writing</em> methods are the ones that throw. Requiring {@code noRollbackFor}
     * there would be asking a refusal not to roll back a write, which is the opposite of correct.</p>
     *
     * <p>The known limit, therefore: a <b>method-level</b> {@code readOnly = true} that throws is not
     * covered. Narrowing the scope to where the rule is unambiguously right is the trade — a lint
     * with false positives is one people learn to suppress (SH192's rule about the unknown-section
     * scan, applied to a guard instead of a template).</p>
     */
    private static final Pattern READ_ONLY_TRANSACTION = Pattern.compile(
            "(?m)^@Transactional\\s*\\(\\s*readOnly\\s*=\\s*true");

    @Test
    @DisplayName("SH345: a read-only transaction that can throw a teaching exception declares noRollbackFor")
    void everyReadOnlyThrowerDeclaresNoRollbackFor() {
        Map<String, String> sources = sources();
        Set<String> teaching = teachingExceptions(sources);
        List<String> offenders = new ArrayList<>();

        sources.forEach((name, body) -> {
            if (!READ_ONLY_TRANSACTION.matcher(body).find() || body.contains("noRollbackFor")) {
                return;
            }
            List<String> thrown = teaching.stream()
                    .filter(exception -> throwsIt(body, exception))
                    .sorted()
                    .toList();
            if (!thrown.isEmpty()) {
                offenders.add(name + " throws " + thrown);
            }
        });

        assertThat(offenders)
                .as("""
                        a read-only transaction has nothing to roll back, so marking it \
                        rollback-only can only destroy a caller's ability to report the failure — \
                        and when the caller is the one that catches teaching exceptions on purpose \
                        (ShAgentReadService's per-row errors, ShAgentRenderService's fallback), the \
                        result is UnexpectedRollbackException at commit, nowhere near the cause. \
                        That is how GET /agent/find?site=nope answered 500 instead of a teaching 404.

                        Add `noRollbackFor = ShTeachingException.class` to the @Transactional. It \
                        changes nothing else on a read-only path.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the guard is not vacuous: the derived set is real and the pattern matches")
    void theGuardCanFail() {
        Set<String> teaching = teachingExceptions(sources());

        System.out.printf("%n[SH345] %d teaching exception types derived from source: %s%n%n",
                teaching.size(), new TreeSet<>(teaching));

        assertThat(teaching)
                .as("derived from `extends ShTeachingException` — an empty set would make the lint "
                        + "report zero forever, which is the failure every lint here is written "
                        + "against")
                .contains("UnknownSiteException", "DuplicateUrlException")
                .hasSizeGreaterThanOrEqualTo(10);

        assertThat(READ_ONLY_TRANSACTION.matcher("@Transactional(readOnly = true)").find()).isTrue();
        assertThat(READ_ONLY_TRANSACTION.matcher(
                "@Transactional(readOnly = true, noRollbackFor = ShTeachingException.class)").find())
                .as("the annotation still matches when the attribute is present — the presence of "
                        + "noRollbackFor is checked separately, not by failing to match")
                .isTrue();
        assertThat(READ_ONLY_TRANSACTION.matcher("@Transactional").find())
                .as("a writing transaction is deliberately out of scope: a refused batch must roll "
                        + "back, and that is what makes it atomic")
                .isFalse();
        assertThat(READ_ONLY_TRANSACTION.matcher("    @Transactional(readOnly = true)").find())
                .as("indented: a method-level annotation, the declared limit of this lint")
                .isFalse();

        assertThat(throwsIt("throw new ShAgentContextService.UnknownSiteException(selector, all);",
                "UnknownSiteException"))
                .as("""
                        the nested, owner-qualified spelling — this is exactly what the first \
                        version of the lint missed, and it is the bean the whole task came from.""")
                .isTrue();
        assertThat(throwsIt("throw new UnknownSiteException(selector);", "UnknownSiteException"))
                .isTrue();
        assertThat(throwsIt("// mentions UnknownSiteException in prose", "UnknownSiteException"))
                .as("a mention is not a throw — several of these files discuss the refusals they "
                        + "catch rather than raise")
                .isFalse();
    }

    /**
     * Does this source throw that exception, however it spells the type?
     *
     * <p>The qualifier is the point. The first version of this lint matched {@code "throw new " +
     * exception} literally and therefore <b>missed the one bean it was written from</b>:
     * {@code ShAgentSiteAccess} throws {@code ShAgentContextService.UnknownSiteException}, a nested
     * type named through its owner. A lint that does not catch its own motivating case is the
     * under-reporting failure in its purest form, and it was only visible because the answer was
     * already known.</p>
     */
    private static boolean throwsIt(String body, String exception) {
        return Pattern.compile("throw\\s+new\\s+(?:\\w+\\.)*" + Pattern.quote(exception) + "\\s*\\(")
                .matcher(body).find();
    }

    /** Every {@code X extends ShTeachingException} declared anywhere in the tree. */
    private static Set<String> teachingExceptions(Map<String, String> sources) {
        Set<String> names = new TreeSet<>();
        sources.values().forEach(body -> {
            Matcher matcher = TEACHING_SUBCLASS.matcher(body);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        });
        return names;
    }

    private static Map<String, String> sources() {
        try (Stream<Path> files = Files.walk(MAIN)) {
            Map<String, String> byName = new TreeMap<>();
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> byName.put(path.getFileName().toString(), read(path)));
            return byName;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path.toAbsolutePath(), e);
        }
    }
}
