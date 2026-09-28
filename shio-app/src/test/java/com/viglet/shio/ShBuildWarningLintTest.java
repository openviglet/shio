/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * SH271 — the two build warnings that are <em>checkable</em> fail the build instead of scrolling past.
 *
 * <h2>Why this exists</h2>
 *
 * <p>SH250's fix was named by the build's own output, on every run, for as long as anyone can
 * remember — and what made it a task was a <b>flake</b>, not the message. Measuring the same log again
 * found four warning families still in that state, and two of them were not the two SH271 was filed
 * for:</p>
 *
 * <table>
 *   <caption>Measured on a clean run</caption>
 *   <tr><th>warning</th><th>what happened to it</th></tr>
 *   <tr><td>logback: "this appender no longer admits a layout as a sub-component"</td>
 *       <td>fixed — the sibling config was already right; guarded below</td></tr>
 *   <tr><td>Maven: {@code 'build.plugins.plugin.version'} for {@code maven-resources-plugin} is
 *       missing, "future Maven versions might no longer support building such malformed projects"</td>
 *       <td>fixed by pinning 3.3.1, the version already binding; guarded below</td></tr>
 *   <tr><td>javac: "uses or overrides a deprecated API … recompile with -Xlint:deprecation"</td>
 *       <td>fixed ({@code textNode} → {@code stringNode}), and {@code showDeprecation} is on so the
 *       next one names itself the first time</td></tr>
 *   <tr><td>JVM: "Sharing is only supported for boot loader classes because bootstrap classpath has
 *       been appended"</td>
 *       <td><b>accepted deliberately</b> — see below</td></tr>
 * </table>
 *
 * <h2>The one that stays, and why silencing it would have been the wrong trade</h2>
 *
 * <p>It is a direct consequence of SH250: {@code -javaagent:mockito-core} appends to the bootstrap
 * classpath, so class-data sharing narrows to boot classes and the JVM says so. The only ways to
 * remove the line are {@code -Xshare:off} — which gives up the sharing that still works, to make a
 * message go away — or giving up the agent, which was the fix for a flake. So it is documented in
 * {@code build.md} as expected output with a cause, which is the SH275 pattern: a condition written
 * down as accepted is not the same as one nobody has looked at.</p>
 *
 * <p>Nothing here can assert that line, because a test runs <em>inside</em> the JVM whose startup
 * printed it. That asymmetry is the point of this class: it guards what is checkable and says plainly
 * what is not, rather than implying the log is clean because a test passed.</p>
 *
 * @author Alexandre Oliveira
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShBuildWarningLintTest {

    /** {@code <plugin>…</plugin>}, non-greedy so each declaration is its own match. */
    private static final Pattern PLUGIN = Pattern.compile("<plugin>(.*?)</plugin>", Pattern.DOTALL);
    private static final Pattern ARTIFACT = Pattern.compile("<artifactId>([^<]+)</artifactId>");
    private static final Pattern XML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    /**
     * Config without prose.
     *
     * <p>Both of these tests failed on their own first run against a file that was already correct,
     * because a comment <em>explaining</em> the fix contains the markup the fix removed. A lint that
     * reads comments is reading the wrong document, and the failure is the worst kind — it accuses the
     * one file somebody has just got right.</p>
     */
    private static String withoutComments(String xml) {
        return XML_COMMENT.matcher(xml).replaceAll("");
    }

    private static Path repositoryRoot() {
        // Surefire runs with the module directory as its working directory.
        return Path.of("").toAbsolutePath().getParent();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("SH271's lint cannot read " + path.toAbsolutePath(), e);
        }
    }

    /** Keyed by the path a reader would open — both files are called {@code pom.xml}. */
    private static Map<String, Path> poms() {
        Path root = repositoryRoot();
        return Map.of("pom.xml", root.resolve("pom.xml"),
                "shio-app/pom.xml", root.resolve("shio-app").resolve("pom.xml"));
    }

    @Test
    @DisplayName("every declared plugin is pinned to a version")
    void everyDeclaredPluginIsPinnedToAVersion() {
        Map<String, String> unpinned = new TreeMap<>();

        new TreeMap<>(poms()).forEach((label, pom) -> {
            String xml = withoutComments(read(pom));
            // A plugin declared inside a <profile> takes the version from the same POM's main <build>
            // section, which is how Maven merges them by groupId:artifactId — that is why Maven itself
            // says nothing about the jacoco declaration in the `coverage` profile. Pinned anywhere in
            // the file counts as pinned, or this lint would contradict the model it is guarding.
            List<String> pinnedHere = new ArrayList<>();
            Matcher scan = PLUGIN.matcher(xml);
            while (scan.find()) {
                Matcher artifact = ARTIFACT.matcher(scan.group(1));
                if (scan.group(1).contains("<version>") && artifact.find()) {
                    pinnedHere.add(artifact.group(1));
                }
            }

            Matcher plugins = PLUGIN.matcher(xml);
            while (plugins.find()) {
                String declaration = plugins.group(1);
                Matcher artifact = ARTIFACT.matcher(declaration);
                String name = artifact.find() ? artifact.group(1) : "<no artifactId>";
                if (declaration.contains("<version>") || pinnedHere.contains(name)) {
                    continue;
                }
                unpinned.put(label + " -> " + name, "declared with no <version>");
            }
        });

        assertThat(unpinned)
                .as("""
                        SH271: Maven called this build malformed on every run and nobody read it - \
                        "'build.plugins.plugin.version' ... is missing" followed by "future Maven \
                        versions might no longer support building such malformed projects". That is a \
                        warning on a timer, and the version it silently picked (3.3.1) came from the \
                        Maven distribution rather than from this repository, so a wrapper upgrade \
                        could have changed a resource-copy behaviour this build depends on.

                        Fix: add <version> to the plugin above. Pin what runs today - an upgrade is \
                        its own decision, with its own reason.""")
                .isEmpty();
    }

    @Test
    @DisplayName("no logback appender uses a layout where logback wants an encoder")
    void noLogbackAppenderUsesALayout() {
        Path resources = repositoryRoot().resolve("shio-app").resolve("src").resolve("main")
                .resolve("resources");
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> files = Files.list(resources)) {
            files.filter(file -> file.getFileName().toString().startsWith("logback"))
                    .filter(file -> file.getFileName().toString().endsWith(".xml"))
                    .sorted()
                    .forEach(file -> {
                        if (withoutComments(read(file)).contains("<layout")) {
                            offenders.add(file.getFileName().toString());
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertThat(offenders)
                .as("""
                        SH271: logback wraps a <layout> in a LayoutWrappingEncoder and prints three \
                        lines saying so, at every startup - which trains a reader to skim exactly the \
                        part of the output where the next real message will appear. The output is \
                        identical either way, so this costs nothing to keep right.

                        Fix: replace <layout class="...PatternLayout"> with <encoder> and keep the \
                        pattern text.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the compiler is configured to name a deprecated API rather than withhold it")
    void theCompilerNamesTheDeprecatedApi() {
        String pom = read(repositoryRoot().resolve("shio-app").resolve("pom.xml"));

        assertThat(pom)
                .as("""
                        SH271: javac reported "uses or overrides a deprecated API" and then withheld \
                        the method name behind "recompile with -Xlint:deprecation" - a warning that \
                        costs a second build to read is a warning nobody reads. The one it was hiding \
                        was a Jackson 3 rename.

                        Fix: keep <showDeprecation>true</showDeprecation> on maven-compiler-plugin.""")
                .contains("<showDeprecation>true</showDeprecation>");
    }

    /**
     * SH1181 — the deprecations the build names, held by their spelling, since one came back.
     *
     * <p>{@code showDeprecation} makes javac name each one, and that is all it does: SH271 fixed
     * {@code textNode} → {@code stringNode} and it was back in two tests, beside three
     * {@code isTextual()}, two {@code asText()} and a Testcontainers 1.x container, all printed as
     * warnings on every build that nobody failed on. So the replaced forms fail here instead, each
     * with the call that replaces it.</p>
     */
    private static final Map<Pattern, String> REPLACED = Map.of(
            Pattern.compile("\\.isTextual\\("), "isString() (Jackson 3)",
            Pattern.compile("\\.asText\\("), "asString() (Jackson 3)",
            Pattern.compile("\\.textNode\\("), "stringNode() (Jackson 3)",
            Pattern.compile("org\\.testcontainers\\.containers\\.(MariaDB|PostgreSQL|MySQL)Container"),
            "org.testcontainers.<db>.<Db>Container (Testcontainers 2)");

    @Test
    @DisplayName("SH1181: no source uses an API the build warns is deprecated and replaced")
    void noSourceUsesAReplacedApi() {
        Path src = repositoryRoot().resolve("shio-app").resolve("src");
        Map<String, String> found = new TreeMap<>();
        int[] read = {0};
        try (Stream<Path> files = Files.walk(src)) {
            files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> !file.getFileName().toString().equals("ShBuildWarningLintTest.java"))
                    .forEach(file -> {
                        read[0]++;
                        // Code only: a comment explaining the rename names the old call, and a lint
                        // that fired on it would accuse the file that just got it right (SH613).
                        String code = ShSourceText.code(read(file));
                        for (Map.Entry<Pattern, String> rule : REPLACED.entrySet()) {
                            if (rule.getKey().matcher(code).find()) {
                                found.merge(src.relativize(file).toString(), "use " + rule.getValue(),
                                        (a, b) -> a + "; " + b);
                            }
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(read[0]).as("SH1181: the walk read no sources, so the check below is vacuous")
                .isGreaterThan(500);
        assertThat(found)
                .as("SH1181: javac warns on these on every build and nothing failed; replace each")
                .isEmpty();
    }

    @Test
    @DisplayName("SH1181: the resources plugin forces the copy with changeDetection, not overwrite")
    void theResourcesPluginUsesChangeDetection() {
        String pom = withoutComments(read(repositoryRoot().resolve("shio-app").resolve("pom.xml")));
        assertThat(pom)
                .as("SH1181: <overwrite> is deprecated in maven-resources-plugin 3.5.0 and warned on every "
                        + "build; changeDetection=always is its own 'equivalent of overwrite=true', and the "
                        + "forced copy is load-bearing (index.html must reach target/classes/public)")
                .doesNotContain("<overwrite>")
                .contains("<changeDetection>always</changeDetection>");
    }

    @Test
    @DisplayName("SH275: the Mockito agent is still an agent, not a self-attachment")
    void mockitoIsStillLoadedAsAnAgent() {
        String pom = withoutComments(read(repositoryRoot().resolve("shio-app").resolve("pom.xml")));

        assertThat(pom)
                .as("""
                        SH275's judgement pass over the dependency-premise table found this row                         already stale and unwatched: the premise was "Mockito self-attaches without                         a -javaagent", which SH250 made false by adding the agent — and nothing                         noticed, because nothing was watching. The live premise is the successor,                         and it is load-bearing by the table's own bar (does it expiring change                         BEHAVIOUR?): remove this argLine and Mockito goes back to attaching to a                         running JVM, where instrumentation succeeds or fails depending on what has                         already been loaded. That is the SH250 flake — ShTenantSuspendedAccessTest                         failing six of six once and passing on every re-run — and a flake is worse                         than a break, because it teaches everybody that red might mean nothing.

                        `@{argLine}` and not `${argLine}`: late replacement, so a future JaCoCo                         argLine is appended to rather than overwritten.""")
                .contains("<argLine>@{argLine} -javaagent:${org.mockito:mockito-core:jar}</argLine>");
        assertThat(pom)
                .as("""
                        and the plugin that resolves that path: `${org.mockito:mockito-core:jar}` is                         maven-dependency-plugin's `properties` goal output. Without the execution the                         property never resolves, the agent path is a literal string, and the JVM                         fails to start rather than falling back — which is at least loud, but the                         argLine above would still be present and this guard would still pass on it                         alone.""")
                .contains("<id>resolve-agent-paths</id>");
    }
}
