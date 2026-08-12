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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH680 — a {@code main} that takes arguments must pass them to {@code run}.
 *
 * <h2>The defect, measured</h2>
 *
 * <p>{@code Shio.main} built a {@code SpringApplication} and called {@code application.run()} — no
 * {@code args}. Every {@code --key=value} on the command line was dropped, and dropped
 * <b>silently</b>. Measured: the jar launched with {@code --server.port=8099} came up on
 * <b>2710</b>, and {@code server.port} appears zero times in the startup log.</p>
 *
 * <p>It was found sideways, while verifying SH676 — an instance pointed at a local catalogue with
 * {@code --shio.marketplace.enabled=true} reported <i>"shio.marketplace.enabled is false"</i>. The
 * property was right and the value was right, so the whole session read as a broken feature.</p>
 *
 * <h2>Why a lint, and why textual</h2>
 *
 * <p>The fix is two characters and <b>no unit test naturally covers it</b>: the argument-less form
 * compiles, starts, serves every request and passes every suite in this repository. It is a property
 * of the process boundary, and the only behavioural test would be launching a JVM and reading a port
 * — which is a slow, flaky assertion about Spring rather than about this code.</p>
 *
 * <p>So: read the source and require that a {@code run(} inside a {@code main(String[] args)} carries
 * them. It is cheap, it names the file, and it fails the moment somebody writes the argument-less
 * form again — which is the only thing that can go wrong here, and did.</p>
 *
 * @author Alexandre Oliveira
 */
class ShBootstrapArgsLintTest {

    private static final Path MAIN = Paths.get("src", "main", "java");

    /** The parameter name a `main` declares, so the check asserts against what this file calls it. */
    private static final Pattern MAIN_METHOD =
            Pattern.compile("static\\s+void\\s+main\\s*\\(\\s*String\\s*(?:\\[\\]\\s*(\\w+)|(\\w+)\\s*\\[\\])");

    /** Any `…run(` call — the argument list is what this is about, so it is captured, not skipped. */
    private static final Pattern RUN_CALL = Pattern.compile("\\.run\\s*\\(([^)]*)\\)");

    @Test
    @DisplayName("SH680: a bootstrap that takes command-line arguments passes them to run")
    void mainPassesItsArgumentsToRun() throws IOException {
        String source = Files.readString(
                MAIN.resolve(Paths.get("com", "viglet", "shio", "Shio.java")), StandardCharsets.UTF_8);

        Matcher main = MAIN_METHOD.matcher(source);
        assertThat(main.find())
                .as("the bootstrap declares a main(String[] …) — if this file moved, move this with it")
                .isTrue();
        String parameter = main.group(1) != null ? main.group(1) : main.group(2);

        // Only what follows `main`, so a `run(` in an unrelated method above cannot satisfy it.
        String body = source.substring(main.end());
        Matcher run = RUN_CALL.matcher(body);
        assertThat(run.find())
                .as("the bootstrap calls run(...) — this lint guards its arguments, so it has to "
                        + "find the call at all (a reader that matched nothing would be green)")
                .isTrue();

        assertThat(run.group(1).trim())
                .as("""
                        `run()` with no arguments drops every --key=value on the command line, \
                        silently: no warning, no log line, and the instance starts on the defaults. \
                        Measured before SH680 — `--server.port=8099` came up on 2710. Pass `%s`."""
                        .formatted(parameter))
                .contains(parameter);
    }

    @Test
    @DisplayName("SH680: the lint reads the shape that shipped, and clears the one that fixed it")
    void theLintDetectsTheFormThatDroppedThem() {
        // Guards the guard (SH275's habit). A textual rule that silently matches nothing is worse
        // than none, and this is the exact pair of lines, reduced.
        String broken = "public static void main(String[] args) {\n"
                + "    SpringApplication application = new SpringApplication(Shio.class);\n"
                + "    application.run();\n}";
        String fixed = broken.replace("application.run();", "application.run(args);");

        assertThat(argumentsOfRunAfterMain(broken))
                .as("the form that shipped, and the one this test exists to refuse")
                .isEmpty();
        assertThat(argumentsOfRunAfterMain(fixed)).isEqualTo("args");
        assertThat(argumentsOfRunAfterMain(
                "public static void main(String[] argv) {\n  app.run(argv);\n}"))
                .as("the parameter is read rather than assumed to be called `args`")
                .isEqualTo("argv");
    }

    /** What the first `run(` after a `main` declaration is passed, for the guard above. */
    private static String argumentsOfRunAfterMain(String source) {
        Matcher main = MAIN_METHOD.matcher(source);
        if (!main.find()) {
            return null;
        }
        Matcher run = RUN_CALL.matcher(source.substring(main.end()));
        return run.find() ? run.group(1).trim() : null;
    }
}
