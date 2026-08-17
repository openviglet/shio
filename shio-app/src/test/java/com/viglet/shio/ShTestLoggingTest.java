/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;

/**
 * SH806 — the suite writes no log file, and therefore has nothing to roll or clean up.
 *
 * <h2>The failure this asserts against</h2>
 *
 * <p>One run of the Java gate reported <b>8 errors over 1856 tests</b>, none of them an assertion.
 * Three {@code @SpringBootTest} classes failed to start and the rest was Spring's failure-threshold
 * cascade skipping the repeats:</p>
 *
 * <pre>
 * Logging system failed to initialize using configuration from 'null'
 * ERROR in SizeAndTimeBasedRollingPolicy - Timeout while waiting for clean-up job to finish
 * </pre>
 *
 * <p>The next run was green, 1894 of 1894, with the string absent from the log entirely — a false
 * red, which SH755 says is the thing that teaches people red means nothing, and which SH800 made
 * more expensive by recording it: the stamp says {@code red: true} and the comparators then demand a
 * {@code red-suites.json} entry against an open task for a defect nobody can find.</p>
 *
 * <h2>What it was, measured rather than guessed</h2>
 *
 * <p>{@code logback-spring.xml} includes Spring Boot's {@code file-appender.xml}, whose
 * {@code LOG_FILE} falls back to <b>{@code ${java.io.tmpdir}/spring.log}</b>. Tests never set
 * {@code logging.file.name} — the test tree's own {@code application.properties} shadows
 * production's, where that key lives — so every Spring context in the suite opened a rolling file
 * appender <b>in the system temp directory</b>, whose rolling policy then ran a clean-up job over
 * that directory hunting for archives to delete.</p>
 *
 * <p>On the machine that produced the red, {@code %TEMP%} held <b>309,064 entries</b> and
 * {@code spring.log} had reached 3.2 MB with a fortnight of gzipped archives beside it. Dozens of
 * contexts each scanning that, while the suite is already running, is the timeout.</p>
 *
 * <h2>Why this assertion and not a check for the string</h2>
 *
 * <p>Asserting the error message never appears would be a check on the symptom, and it passes on any
 * run where the clean-up happens to finish in time — which is every run but one. This asserts the
 * <b>cause</b>: a suite with no file appender has no rolling policy, no clean-up job, and no
 * directory to scan. It also holds the second, quieter half of the same defect, which nobody had
 * noticed at all: the suite was writing megabytes into a developer's temp directory on every run.</p>
 *
 * <h2>Why it boots a context, which is not free</h2>
 *
 * <p>Because {@code logback-spring.xml} is read by <b>Spring Boot's</b> logging system and not by
 * logback itself. A plain JUnit version of this test passed with the configuration deleted — logback
 * had fallen back to its own console default, so the check was asking a question about a world the
 * test was not in. That is the vacuity every instrument here is warned about, and it survived one
 * round of breaking-to-verify because a stale copy of the resource sat in {@code target/} (SH282's
 * trap, arriving through a resource rather than a class).</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
class ShTestLoggingTest {

    @Test
    @DisplayName("SH806: no appender writes a file, so there is nothing to roll and nothing to clean")
    void theSuiteWritesNoLogFile() {
        List<String> files = new ArrayList<>();
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (Logger logger : context.getLoggerList()) {
            for (var appenders = logger.iteratorForAppenders(); appenders.hasNext();) {
                Appender<?> appender = appenders.next();
                if (appender instanceof FileAppender<?> file) {
                    files.add(logger.getName() + " → " + appender.getName() + " → " + file.getFile());
                }
            }
        }

        assertThat(files)
                .as("""
                        SH806: a file appender is configured for the test suite. Every Spring context \
                        opens one, its rolling policy runs a clean-up job over the containing \
                        directory, and when that directory is a developer's temp folder — measured at \
                        309,064 entries — the job times out and Spring reports it as a logging \
                        configuration error. Three context loads then fail and the cascade is eight \
                        test errors that have nothing to do with the code.
                        `src/test/resources/logback-test.xml` is what keeps this empty: logback and \
                        Spring Boot both prefer it over `logback-spring.xml`, so the suite logs to a \
                        console it can discard and writes nothing. Deleting it brings the red back.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH806: and the reader is not vacuous — it can see the appenders that do exist")
    void theReaderSeesTheConsoleAppender() {
        // A scan that found no appenders at all would report no *file* appenders and pass for ever,
        // which is the zero-reporting failure every instrument here is warned about. The console one
        // is what `logback-test.xml` keeps, deliberately, so a test's output is unchanged.
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        List<String> appenders = new ArrayList<>();
        for (Logger logger : context.getLoggerList()) {
            for (var each = logger.iteratorForAppenders(); each.hasNext();) {
                appenders.add(each.next().getName());
            }
        }

        assertThat(appenders)
                .as("SH806: logback reports no appenders whatever, so the check above is asking an "
                        + "empty list whether it contains a file — and it always will not")
                .isNotEmpty();
    }
}
