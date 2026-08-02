/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH276 — the largest inherited premise in this repository, made to show its age.
 *
 * <h2>The one premise SH274's method cannot reach</h2>
 *
 * <p>SH75 is hand-rolled MCP, and the justification was one sentence in the present tense: <em>"the
 * MCP Java SDK is built on Jackson 2 and a Spring Boot 3 baseline, while this application runs Boot
 * 4 with Jackson 3"</em>. Measured by the code it defends — roughly fifteen hundred lines of
 * JSON-RPC, dispatch, routing and a wire golden — it is the heaviest claim here, and it is the one
 * kind SH274 turned into tripwires cannot touch: <b>the artefact is not on the classpath</b>, so no
 * test can ask it anything, and adding it to find out is the change being evaluated. Asserting the
 * absence of a conflict you avoided by not depending on something is a tautology.</p>
 *
 * <h2>So the instrument is a date, and the date is the assertion</h2>
 *
 * <p>What can be checked is <b>whether anybody has looked recently</b>. {@link ShMcpServer} now
 * carries a dated finding — the version examined, the coordinates read, and what they said — and
 * this reads it back. A premise about somebody else's release train cannot be true forever, and the
 * failure mode is not that it is wrong; it is that nobody can tell. Three years of silence and a
 * sentence in the present tense look identical.</p>
 *
 * <p><b>Looking once already found it moved.</b> On 2026-08-02 the SDK is 2.0.0 and publishes
 * {@code mcp-json-jackson3}, which depends on {@code tools.jackson.core:jackson-databind:3.0.3} —
 * the same Jackson 3 this application runs. So the sentence had gone stale, which is exactly the
 * outcome this task predicted and is not, on its own, an argument to adopt the SDK: see the note in
 * {@code ShMcpServer} for what survives the check and what the standing argument is now.</p>
 *
 * @author Alexandre Oliveira
 */
class ShMcpSdkPremiseTest {

    private static final Path SERVER = Path.of("src", "main", "java", "com", "viglet", "shio",
            "api", "agent", "mcp", "ShMcpServer.java");

    /**
     * How long a finding about somebody else's release train stays worth trusting.
     *
     * <p>Eighteen months is deliberately generous and deliberately finite. Shorter would make this
     * a recurring chore for a decision nobody is revisiting; unbounded would restore exactly the
     * property the task objects to, which is that staleness is invisible.</p>
     */
    private static final Period SHELF_LIFE = Period.ofMonths(18);

    private static final Pattern CHECKED = Pattern.compile("SDK-PREMISE-CHECKED:\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern VERSION = Pattern.compile("SDK-PREMISE-VERSION:\\s*(\\S+)");

    @Test
    @DisplayName("the SDK premise carries the date and the version somebody actually read")
    void thePremiseIsDated() {
        String source = read(SERVER);

        Matcher checked = CHECKED.matcher(source);
        assertThat(checked.find())
                .as("""
                        ShMcpServer's justification for hand-rolling MCP must carry \
                        `SDK-PREMISE-CHECKED: <yyyy-mm-dd>`. Without it the claim is a sentence in \
                        the present tense about a release train nobody controls, which is the whole \
                        objection: it cannot be wrong in a way anybody notices.""")
                .isTrue();

        Matcher version = VERSION.matcher(source);
        assertThat(version.find())
                .as("""
                        and `SDK-PREMISE-VERSION: <coordinate>`, because "we looked" is not a \
                        finding — the version examined is what a later reader compares against.""")
                .isTrue();
    }

    @Test
    @DisplayName("and the last look is recent enough to still mean something")
    void thePremiseHasNotGoneStale() {
        String source = read(SERVER);
        Matcher checked = CHECKED.matcher(source);
        assertThat(checked.find()).as("see thePremiseIsDated").isTrue();

        LocalDate when = date(checked.group(1));
        LocalDate expires = when.plus(SHELF_LIFE);

        assertThat(LocalDate.now())
                .as("""
                        the MCP SDK premise was last checked on %s and this build is past its \
                        %s-month shelf life. Re-read the coordinates in ShMcpServer's note against \
                        the current release, write down what they say now, and move the date — \
                        whichever way the finding goes. Deciding to keep the hand-rolled server is \
                        a fine outcome; not knowing is the one this exists to prevent.""",
                        when, SHELF_LIFE.getMonths())
                .isBeforeOrEqualTo(expires);
    }

    private static LocalDate date(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("SDK-PREMISE-CHECKED is not an ISO date: " + text, e);
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
