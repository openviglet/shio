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
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.dto.ShAgentManifest;

/**
 * SH460 — the manifest can say which build answered, and the answer is the one that is running.
 *
 * <h2>Why the second assertion is the one that matters</h2>
 *
 * <p>The defect this task closes is not "the manifest omits a timestamp". It is that a red
 * conformance run could not name the stale side, and the reason it could not is subtle enough to
 * be re-broken by a plausible simplification: reading the class timestamps <em>when the request
 * arrives</em> is one line shorter and reports the compile that happened <b>after</b> this JVM
 * loaded its classes — a stale instance claiming to be fresh, which is worse than the silence it
 * replaced.</p>
 *
 * <p>So the test does what the incident did: it makes the code source newer than the running
 * build, and asserts the reported timestamp <b>did not move</b>. The third assertion is what
 * stops the second from passing vacuously — a fresh reading of the same tree <em>does</em> see
 * the change, so "it did not move" means the value was captured at startup, not that the walk is
 * blind.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentBuildIdentityTest {

    /** Not a loadable class name (no package path, a hyphen): nothing will try to link it. */
    private static final String PROBE = "sh460-staleness-probe.class";

    @Autowired
    private ShAgentBuildIdentity shAgentBuildIdentity;

    @Autowired
    private ShAgentManifestService shAgentManifestService;

    @Test
    @DisplayName("the running build dates itself, and the manifest carries it")
    void theManifestCarriesTheBuildTimestamp() {
        ShAgentManifest manifest = shAgentManifestService.manifest();

        assertThat(manifest.builtAt())
                .as("without this a conformance run cannot tell a broken fix from a stale JVM; "
                        + "`version` reads `dev` on every developer instance and answers nothing")
                .isNotBlank();
        assertThat(Instant.parse(manifest.builtAt()))
                .as("a build that has not happened yet is a clock problem, not an identity")
                .isBefore(Instant.now().plus(1, ChronoUnit.HOURS));
    }

    @Test
    @DisplayName("the identity survives ?include=identity — the cheapest read must still say it")
    void theBuildIdentitySurvivesEveryProjection() {
        ShAgentManifest projected = ShAgentManifestSection.project(
                shAgentManifestService.manifest(),
                EnumSet.of(ShAgentManifestSection.IDENTITY));

        assertThat(projected.builtAt()).isEqualTo(shAgentBuildIdentity.builtAt());
        assertThat(projected.commit()).isEqualTo(shAgentBuildIdentity.commit());
        assertThat(projected.features()).isNull();
    }

    @Test
    @DisplayName("recompiling under a running JVM does not make it report itself fresh")
    void theTimestampIsCapturedAtStartupRatherThanPerRequest() throws IOException, URISyntaxException {
        Path root = ShAgentBuildIdentity.codeSource();
        assertThat(root).as("this suite runs from target/classes; a jar would not be writable").isNotNull();
        assertThat(Files.isDirectory(root)).isTrue();

        String reported = shAgentBuildIdentity.builtAt();
        Path probe = root.resolve(PROBE);
        try {
            Files.writeString(probe, "SH460");
            Files.setLastModifiedTime(probe, FileTime.from(Instant.now().plus(2, ChronoUnit.HOURS)));

            assertThat(shAgentBuildIdentity.builtAt())
                    .as("the classes moved under a JVM that had already loaded them — the running "
                            + "build is unchanged and the identity must not claim otherwise")
                    .isEqualTo(reported);
            assertThat(new ShAgentBuildIdentity((GitProperties) null).builtAt())
                    .as("and the walk does see the tree, so the assertion above is not vacuous")
                    .isNotEqualTo(reported);
        } finally {
            Files.deleteIfExists(probe);
        }
    }

    @Test
    @DisplayName("SH609: a Boot fat-jar CodeSource resolves to the jar, not to null")
    void aFatJarLocationResolves(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        // The shape that returned null. Under `java -jar` Boot's CodeSource is a NESTED URL, and
        // `Paths.get(uri)` refuses it: the scheme is `jar:`, so there is no filesystem provider and
        // the call throws. The throw was caught and `builtAt` became null on precisely the
        // deployment the conformance gate insists on — so SH500's dirty-tree fallback had nothing
        // to fall back to, and SHIO_CONFORMANCE_ALLOW_STALE=1 became the standing incantation.
        Path jar = dir.resolve("shio.jar");
        Files.writeString(jar, "not really a jar, and it does not need to be");

        java.net.URL nested = java.net.URI.create(
                "jar:" + jar.toUri() + "!/BOOT-INF/classes!/").toURL();

        Path resolved = ShAgentBuildIdentity.pathOf(nested);

        assertThat(resolved)
                .as("""
                        everything before the first `!/` is the outer jar, and its own                         lastModifiedTime is exactly "when this artifact was produced" — a                         repackaged jar is written once (SH609)""")
                .isEqualTo(jar);
        assertThat(Files.getLastModifiedTime(resolved).toInstant())
                .as("and it is readable, which is the whole point: an absent timestamp turns the "
                        + "gate off in the only state anybody runs it in")
                .isNotNull();
    }

    @Test
    @DisplayName("SH609: an exploded classes directory is unchanged")
    void anExplodedLocationStillResolves(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        // The half that must not break — this is what a Maven build serves, and it is where the
        // newest-class walk happens.
        assertThat(ShAgentBuildIdentity.pathOf(dir.toUri().toURL())).isEqualTo(dir);
    }


}
