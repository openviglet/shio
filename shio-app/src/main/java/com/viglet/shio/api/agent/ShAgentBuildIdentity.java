/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.GitProperties;
import org.springframework.stereotype.Component;

/**
 * Which build of this code is actually running (SH460).
 *
 * <h2>The failure this exists to name</h2>
 *
 * <p>A conformance run against a live instance asserts two things at once — that the code is
 * right, and that the instance is running <em>that</em> code — and reports one colour for both.
 * SH448's suite went red on two shipped fixes because the JVM had started at 16:05:10 and the
 * classes under test were compiled at 18:47:21, after every commit the run was validating. The
 * CLI half was green in the same run, because it loads from disk. Nothing in the response said
 * so, and telling the two apart took hand probes, {@code target/} timestamps and the JVM's start
 * time from the process table — while two defects that do not exist sat one keystroke from being
 * filed.</p>
 *
 * <h2>Why {@code version} could not answer it</h2>
 *
 * <p>The manifest already carries {@code version}, and on a developer instance it reads
 * {@code dev} — the same string before and after any rebuild, so it distinguishes nothing. The
 * gap was a field, not a comparison.</p>
 *
 * <h2>Both values are captured at construction, and that is the whole trick</h2>
 *
 * <p>Reading the class timestamps when the request arrives would report the compile that happened
 * <em>after</em> this JVM loaded its classes — precisely the stale instance, claiming to be
 * fresh. This bean is a singleton built during startup, so the newest {@code .class} it can see
 * is the build the JVM is running. That is why {@link #builtAt()} is a field and not a method
 * body.</p>
 *
 * <p>{@code commit} comes from Spring Boot's {@link GitProperties}, which reads the
 * {@code git.properties} the build writes into the classpath. It is absent rather than wrong when
 * the file is not there, and it is deliberately the <em>weaker</em> of the two signals: an IDE
 * that compiles into {@code target/classes} on save moves the classes and not that file, which is
 * the exact scenario above. So {@code commit} names the build and {@code builtAt} dates it; a
 * client comparing against a working tree compares the timestamp.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShAgentBuildIdentity {

    private static final Logger logger = LoggerFactory.getLogger(ShAgentBuildIdentity.class);

    private static final String CLASS_SUFFIX = ".class";

    private final String commit;
    private final String builtAt;

    @Autowired
    public ShAgentBuildIdentity(ObjectProvider<GitProperties> gitProperties) {
        this(gitProperties.getIfAvailable());
    }

    /** Direct form, for a test that needs a second reading of the same code source. */
    ShAgentBuildIdentity(GitProperties git) {
        this.commit = git != null ? git.getShortCommitId() : null;
        Instant instant = resolveBuiltAt();
        this.builtAt = instant != null ? instant.truncatedTo(ChronoUnit.SECONDS).toString() : null;
    }

    /** Where {@link #builtAt()} was read from — {@code target/classes} in a build, else a jar. */
    static Path codeSource() throws URISyntaxException {
        CodeSource source = ShAgentBuildIdentity.class.getProtectionDomain().getCodeSource();
        return source == null || source.getLocation() == null
                ? null
                : pathOf(source.getLocation());
    }

    /**
     * The file a {@code CodeSource} location names, including the fat-jar case (SH609).
     *
     * <h3>The shape that returned null</h3>
     *
     * <p>Under {@code java -jar} Boot's location is a <b>nested</b> URL —
     * {@code jar:file:/opt/shio.jar!/BOOT-INF/classes!/} — and {@code Paths.get(uri)} refuses it:
     * the scheme is {@code jar:}, not {@code file:}, so there is no default filesystem provider for
     * it and the call throws. The throw was caught and {@code builtAt} became {@code null} on
     * <em>precisely the deployment the conformance gate insists on</em>.</p>
     *
     * <p>The consequence was not a wrong answer but a gate that was off in the only state anybody
     * runs it in. SH500 falls back to {@code builtAt} on a dirty tree — where the commit hash no
     * longer describes what compiled — so with the field null, {@code SHIO_CONFORMANCE_ALLOW_STALE=1}
     * became the standing incantation, and an escape hatch used on every run is a check deleted
     * slowly.</p>
     *
     * <p>The outer jar has its own {@code lastModifiedTime} and it is the right answer: a repackaged
     * jar is written once, so its timestamp is exactly "when this artifact was produced". Everything
     * before the first {@code !/} is that file, and the separator is Boot's own nesting marker
     * rather than a guess about path syntax.</p>
     */
    static Path pathOf(URL location) throws URISyntaxException {
        String form = location.toString();
        if (!form.startsWith("jar:")) {
            return Paths.get(location.toURI());
        }
        // `java.net.URL` refuses a `jar:` URL without `!/` outright — "no !/ in spec" — so the
        // unnested form cannot reach here through a real location. The bound is kept anyway rather
        // than asserted: a `substring` that could run off the end inside build identity would turn
        // a diagnostic into a startup failure, and there is no test for it because there is no way
        // to construct the input.
        int nested = form.indexOf("!/");
        String outer = nested < 0 ? form.substring(4) : form.substring(4, nested);
        return Paths.get(URI.create(outer));
    }

    /** The abbreviated commit this artifact was built from, or {@code null} outside a build. */
    public String commit() {
        return commit;
    }

    /** ISO-8601 instant the running classes were produced, or {@code null} if unreadable. */
    public String builtAt() {
        return builtAt;
    }

    /**
     * The newest class file under the code source, or the jar's own timestamp.
     *
     * <p>Newest rather than this class's own: the change that makes an instance stale is usually
     * in some other class — the incident above was {@code ShAgentBatchService} — so a single
     * file's timestamp would report a fresh build for a stale JVM. The walk is a startup one-off
     * over a few hundred entries; it never runs again.</p>
     */
    private static Instant resolveBuiltAt() {
        try {
            Path path = codeSource();
            if (path == null) {
                return null;
            }
            if (Files.isDirectory(path)) {
                return newestClassUnder(path);
            }
            return Files.exists(path) ? Files.getLastModifiedTime(path).toInstant() : null;
        } catch (URISyntaxException | IOException | RuntimeException failure) {
            // Absent is honest; a wrong timestamp would be worse than none, because a client
            // comparing it against a working tree would conclude the instance is fresh.
            logger.debug("Could not resolve the running build's timestamp", failure);
            return null;
        }
    }

    private static Instant newestClassUnder(Path root) throws IOException {
        try (Stream<Path> tree = Files.walk(root)) {
            return tree.filter(candidate -> candidate.getFileName().toString().endsWith(CLASS_SUFFIX))
                    .map(ShAgentBuildIdentity::lastModified)
                    .filter(Objects::nonNull)
                    .max(Instant::compareTo)
                    .orElse(null);
        }
    }

    private static Instant lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException failure) {
            return null;
        }
    }
}
