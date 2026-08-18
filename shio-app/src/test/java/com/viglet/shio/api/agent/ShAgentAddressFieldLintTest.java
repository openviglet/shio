/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * SH810 — an address is called {@code address} on this surface, everywhere.
 *
 * <h2>The slip</h2>
 *
 * <p>SH809 shipped {@code /agent/editable} answering rows of {@code { post, field, region }}. Every
 * other endpoint here — {@code read}, {@code find}, {@code batch}, {@code plan}, {@code apply} —
 * calls an address {@code address}, and that record was the only place in the agent packages
 * spelling one {@code post}.</p>
 *
 * <p>It happened for an understandable reason, which is why a guard is worth more than the fix: the
 * <em>attribute</em> it is read from is {@code data-shio-post}, and that is correct for the HTML —
 * the page says which post owns an element. The mistake was letting the wire format inherit a name
 * from the markup it was parsed out of.</p>
 *
 * <h2>Why it matters more than a rename usually would</h2>
 *
 * <p>P2 is that one call replaces a session, and it only works if what comes back is the shape the
 * next call takes. The row was designed to hand straight to {@code post.upsert}; a field named
 * {@code post} where the write wants {@code address} means an agent has to learn that two names are
 * one idea, and nothing on this surface teaches it that.</p>
 *
 * <h2>What is asserted</h2>
 *
 * <p>No record component in the agent packages is named {@code post}. Narrow on purpose: an address
 * is the only reason a component would take that name, and a broader rule — "a component holding an
 * address is called address" — cannot be decided by reading, which is how a lint starts reporting
 * findings nobody can act on.</p>
 *
 * @author Alexandre Oliveira
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShAgentAddressFieldLintTest {

    /** The packages this surface's wire format lives in. */
    private static final Path AGENT = Path.of("src", "main", "java", "com", "viglet", "shio", "api",
            "agent");

    /**
     * A component named `post`, of any type — `String post`, `ShAddress post`.
     *
     * <p>Matched against a record's <b>parameter list</b> and never against the file, which the
     * first draft got wrong. Applied to whole sources it flagged nine files, every one of them
     * correctly named: they declare <em>local variables</em> holding a post entity, and a variable
     * holding a {@code ShPostUnified} should be called {@code post}. The rule is about the wire
     * format, so it reads only what a record declares — the narrower question was the right one and
     * the broad version would have been deleted within a week for crying wolf.</p>
     */
    private static final Pattern COMPONENT = Pattern.compile("\\b[A-Z]\\w*(?:<[^>]*>)?\\s+post\\b");

    /** Any record declaration, so a scan that parsed nothing cannot pass by finding nothing. */
    private static final Pattern RECORD = Pattern.compile("\\brecord\\s+[A-Z]\\w*\\s*\\(");

    private record Scan(List<String> offenders, int records) {
    }

    private static Scan scan() {
        List<String> offenders = new ArrayList<>();
        int records = 0;
        try (Stream<Path> files = Files.walk(AGENT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                // The shared stripper, because this file's own header quotes the defect and a raw
                // read would take the explanation for the thing (SH613).
                String code = ShSourceText.code(Files.readString(file));
                Matcher declarations = RECORD.matcher(code);
                while (declarations.find()) {
                    records += 1;
                    if (COMPONENT.matcher(componentsAt(code, declarations.end() - 1)).find()) {
                        offenders.add(AGENT.relativize(file).toString().replace('\\', '/'));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Scan(offenders, records);
    }

    /** What a record declares, from its opening parenthesis to the one that closes it. */
    private static String componentsAt(String code, int open) {
        int depth = 0;
        for (int at = open; at < code.length(); at += 1) {
            char ch = code.charAt(at);
            if (ch == '(') {
                depth += 1;
            } else if (ch == ')') {
                depth -= 1;
                if (depth == 0) {
                    return code.substring(open + 1, at);
                }
            }
        }
        return "";
    }

    @Test
    @DisplayName("SH810: no record on the agent surface names an address `post`")
    void anAddressIsCalledAddress() {
        assertThat(scan().offenders())
                .as("""
                        SH810: a record component here is named `post`, and on this surface an \
                        address is called `address` — read, find, batch, plan and apply all spell \
                        it that way.
                        The reason it happens is that the annotation being read is `data-shio-post`, \
                        which is right for the HTML and wrong to inherit on the wire: P2 says one \
                        call replaces a session, and that needs what comes back to be the shape the \
                        next call takes. An agent should not have to learn that two names are one \
                        idea.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH810: and the reader is not vacuous — it parses records that exist")
    void theReaderFindsRecords() {
        // A walk that matched no record at all would report no offenders for ever, which is the
        // zero-reporting failure every instrument here is warned about.
        assertThat(scan().records())
                .as("SH810: the scan found no record declaration in the agent packages, so the "
                        + "check above is asking an empty set whether it contains a `post`")
                .isGreaterThan(10);
    }
}
