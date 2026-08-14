/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * Every place an {@link IOException}'s own words become a caller's message (SH756).
 *
 * <h2>Why a lint and not four edits</h2>
 *
 * <p>SH756 was found as one flake: a marketplace install that failed on one run in N and reported
 * two paths. The repair at that one site is three characters wide, and SH338's rule is that when a
 * review finds a <em>law</em> being broken — here P6, errors are instructions — the instrument that
 * finds every <b>other</b> break lands before the fixes. There were eight, in three files, and only
 * one had ever been noticed.</p>
 *
 * <h2>What makes this class of message uniquely bad</h2>
 *
 * <p>Most exceptions carry a sentence. {@code FileSystemException} carries the two paths, and
 * appends {@code ": " + reason} only when the platform supplied one — so on the sighting that
 * opened this task the message was <em>paths alone</em>, meaning the reason was null and the
 * exception's <b>class</b> was the last remaining fact. {@code FileAlreadyExistsException} and
 * {@code AtomicMoveNotSupportedException} are different diagnoses printed identically, and the
 * discriminator was the thing being thrown away.</p>
 *
 * <h2>An asserted list, not a refusal</h2>
 *
 * <p>Same shape as {@code ShOffThreadTenantLintTest}: a site that deliberately does something else
 * is declared with its reason, so the population grows only through a diff somebody reads.</p>
 *
 * @author Alexandre Oliveira
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShIoCauseLintTest {

    private static final Path SOURCE = Path.of("src/main/java/com/viglet/shio");

    /** {@code catch (IOException failed)} and its subclasses — the variable is what matters. */
    private static final Pattern CATCH = Pattern.compile(
            "catch\\s*\\(\\s*(?:final\\s+)?(?:java\\.io\\.|java\\.nio\\.file\\.)?"
                    + "(IOException|UncheckedIOException|FileSystemException|NoSuchFileException"
                    + "|FileAlreadyExistsException|AtomicMoveNotSupportedException)"
                    + "\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\)");

    /**
     * Sites that pass an I/O failure's raw message on purpose, and why.
     *
     * <p>Empty is the honest state today and it is left declared rather than deleted, for the
     * reason {@code UNEXERCISED.tools} is: a key at zero makes a reintroduction a change to a
     * declared number that a diff shows, where an absent map makes it a new idea nobody weighs.</p>
     */
    private static final Map<String, String> DECLARED = Map.of();

    @Test
    @DisplayName("SH756: an IOException's message never reaches a caller without its class")
    void everyIoFailureCarriesItsCause() throws IOException {
        List<String> offenders = new ArrayList<>();
        List<String> scanned = new ArrayList<>();

        for (Path file : sources()) {
            String body = ShSourceText.code(Files.readString(file, StandardCharsets.UTF_8));
            Matcher caught = CATCH.matcher(body);
            while (caught.find()) {
                String variable = caught.group(2);
                String name = relative(file);
                scanned.add(name + ":" + variable);
                // The catch BLOCK, not the file. The first draft matched the whole file and its
                // comment argued that was good enough; the first run answered that with six false
                // positives out of nine, all from ShFileStorageService, where `catch (IOException
                // e)` and `catch (RuntimeException e)` share a name and only the first is this
                // lint's business. A lint that cries wolf on two thirds of its findings is one
                // somebody relaxes, and a relaxed lint is off.
                if (blockAfter(body, caught.end()).contains(variable + ".getMessage()")
                        && !DECLARED.containsKey(name)) {
                    offenders.add(name + " — " + variable + ".getMessage()");
                }
            }
        }

        assertThat(scanned)
                .as("SH756: the scan reaches the files this task repaired. An empty walk is a lint "
                        + "that has stopped looking rather than a codebase that stopped doing it")
                .anyMatch(entry -> entry.startsWith("marketplace/ShMarketplaceInstaller.java"))
                .anyMatch(entry -> entry.startsWith("blueprint/ShBlueprintRegistry.java"));

        assertThat(offenders)
                .as("""
                        SH756: a FileSystemException's message is its two paths, and the reason is \
                        appended only when the platform gave one — so this hands a caller "src -> \
                        dst" and nothing else on exactly the failures nobody can reproduce. Use \
                        ShIoFailure.describe(e), which names the class first, or declare the site \
                        here with the reason it is different.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH756: describe names the class first, and the reason where there is one")
    void describeSaysWhatGetMessageWouldNot() {
        // The sighting that opened the task, reconstructed: two paths, no reason. getMessage()
        // yields `a -> b`, which is what got printed and what decided nothing.
        FileAlreadyExistsException taken = new FileAlreadyExistsException("a", "b", null);
        assertThat(taken.getMessage())
                .as("SH756: the premise — a message that is paths alone, on a failure whose class "
                        + "is the only remaining discriminator")
                .isEqualTo("a -> b");
        assertThat(ShIoFailure.describe(taken))
                .as("SH756: FileAlreadyExistsException and AtomicMoveNotSupportedException print "
                        + "identically through getMessage() and are different diagnoses")
                .isEqualTo("FileAlreadyExistsException: a -> b");

        NoSuchFileException missing = new NoSuchFileException("a", "b", "it is not there");
        assertThat(ShIoFailure.describe(missing))
                .as("SH756: where the platform gave a reason it leads, and the paths follow in "
                        + "brackets — without being said twice, which the raw message would do")
                .isEqualTo("NoSuchFileException: it is not there (a -> b)");

        assertThat(ShIoFailure.describe(new IOException("plain")))
                .isEqualTo("IOException: plain");
        assertThat(ShIoFailure.describe(new IOException()))
                .as("SH756: never empty — a caller substituting this into a detail must not have "
                        + "to guard it, because the guard is the branch nobody tests")
                .isEqualTo("IOException");
        assertThat(ShIoFailure.describe(null)).isNotBlank();
    }

    /**
     * The body of the block starting at the first {@code &#123;} after {@code from}, brace-matched.
     *
     * <p>Comments are already stripped by {@link ShSourceText#code}, so the only braces left are
     * code's. A string literal containing one would end the block early and under-report, which is
     * the direction a guard should be wrong in — and none of the blocks this walks has one.</p>
     */
    private static String blockAfter(String body, int from) {
        int open = body.indexOf('{', from);
        if (open < 0) {
            return "";
        }
        int depth = 0;
        for (int i = open; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return body.substring(open, i + 1);
            }
        }
        return body.substring(open);
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> walk = Files.walk(SOURCE)) {
            return walk.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static String relative(Path file) {
        return SOURCE.relativize(file).toString().replace('\\', '/');
    }
}
