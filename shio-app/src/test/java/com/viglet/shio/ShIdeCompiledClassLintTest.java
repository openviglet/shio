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
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * SH282 — a build that skipped the module cannot say so, so the class it left behind has to.
 *
 * <h2>The defect, measured</h2>
 *
 * <p>With a call to a method that does not exist, {@code ./mvnw -pl shio-app -am compile} prints
 * <b>"Nothing to compile - all classes are up to date"</b> and <b>BUILD SUCCESS</b>, while
 * {@code clean compile} fails correctly with {@code cannot find symbol}. The compiler is not the
 * problem (this build uses plain javac): the <b>IDE</b> — Eclipse JDT, via the VS Code Java
 * extension — compiles into {@code target/classes} on save, and for broken source it emits a class
 * with {@code java.lang.Error: Unresolved compilation problem} embedded rather than emitting nothing.
 * That {@code .class} is newer than its {@code .java}, so {@code maven-compiler-plugin}'s staleness
 * check skips the module, and the broken member becomes a <b>runtime</b> error wherever it is called.
 * One missing accessor read as 53 errors across five unrelated test classes.</p>
 *
 * <h2>Why this is the fix, given that the obvious one is closed</h2>
 *
 * <p>The task proposed giving the IDE its own output directory. That option does not exist:
 * {@code java.project.outputPath} is documented as having no effect on a Maven project — the output
 * path comes from the {@code pom} — and {@code .vscode/} is {@code .gitignore}d here, so no IDE
 * setting can be a guarantee the repository makes. {@code -Dmaven.compiler.useIncrementalCompilation=false}
 * does not help either, because what short-circuits is the staleness check rather than the
 * incremental mode. What is left is the {@code clean} rule, which {@code agents.md} already states,
 * plus the thing nobody had: <b>an instrument</b>. So this turns the tell into a build failure that
 * names the file, instead of a {@code java.lang.Error} raised somewhere else entirely.</p>
 *
 * <h2>What it can and cannot see</h2>
 *
 * <p>It catches the case that <em>bites</em> — a class carrying an embedded compilation error, which
 * is always a defect and never intentional. It cannot catch a <em>stale but valid</em> class, because
 * the timestamps of an IDE-written class and a Maven-written one are indistinguishable: both are
 * newer than the source, which is why the module was skipped in the first place. That case remains
 * the {@code clean} rule's, and saying so is the point — an unenforced premise written down as
 * unenforced is not the same defect as one nobody has noticed.</p>
 *
 * <p>Cost, measured before it was written: 813 class files, 4.4 MB, ~94 ms.</p>
 *
 * @author Alexandre Oliveira
 */
class ShIdeCompiledClassLintTest {

    /**
     * JDT's own text, and it reaches the class file verbatim.
     *
     * <p>Verified rather than assumed: {@code javac} was given
     * {@code throw new Error("Unresolved compilation problem: …")} and the compiled class contains
     * that byte sequence, because a message literal is a constant-pool entry and this one is ASCII,
     * where modified UTF-8 is identity.</p>
     *
     * <p><b>Built by {@code String.join}, and that is load-bearing.</b> Written as a literal — or as
     * {@code "Unresolved compilation" + " problem"}, which javac folds at compile time — the marker
     * would land in <em>this</em> class's constant pool, and the lint would flag itself the moment it
     * scanned {@code target/test-classes}. A lint whose first finding is its own class file is one
     * the next person switches off. {@link #theMarkerIsNotInThisLintsOwnClassFile()} asserts it.</p>
     */
    private static final String MARKER = String.join(" ", "Unresolved", "compilation", "problem");

    /** A literal that <em>is</em> in this class's constant pool, so the premise can be asserted. */
    private static final String CONSTANT_POOL_PROBE = "Sh282ConstantPoolProbe";

    private static Path targetDirectory() {
        try {
            return Path.of(ShIdeCompiledClassLintTest.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot locate this test's own output directory", e);
        }
    }

    /**
     * Every {@code .class} under {@code root} whose bytes carry the marker, sorted (P5).
     *
     * <p>Resolved from the running class's own code source rather than from a relative path, so it
     * scans the tree that was actually used to run — a lint that reads a directory the JVM is not
     * loading from can pass while the loaded classes are broken.</p>
     */
    private static List<Path> classesCarryingAnEmbeddedError(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        byte[] marker = MARKER.getBytes(StandardCharsets.US_ASCII);
        List<Path> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".class")).sorted().toList()) {
                if (contains(Files.readAllBytes(file), marker)) {
                    found.add(file);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return found;
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    @Test
    @DisplayName("no compiled class carries an embedded compilation error")
    void noCompiledClassCarriesAnEmbeddedCompilationError() {
        Path target = targetDirectory();
        List<Path> broken = new ArrayList<>(classesCarryingAnEmbeddedError(target.resolve("classes")));
        broken.addAll(classesCarryingAnEmbeddedError(target.resolve("test-classes")));

        assertThat(broken)
                .as("""
                        SH282: these classes were written by the IDE, not by this build. Eclipse JDT \
                        emits a class with `java.lang.Error: %s` embedded instead of failing, that \
                        class is newer than its source, so maven-compiler-plugin's staleness check \
                        skipped the module and reported "Nothing to compile - all classes are up to \
                        date". Whatever calls the broken member will fail at *runtime*, far from the \
                        cause - that is how one missing accessor read as 53 errors across five \
                        unrelated test classes. (ASCII deliberately: this is read in a terminal, and \
                        a Windows console prints an em dash as a replacement character.)

                        Fix: re-run with `clean`, which is not optional here:
                          ./mvnw -pl shio-app -am clean test -Dskip.npm=true
                        If it then fails to compile, that failure is the real one and the source is \
                        broken. `-Dmaven.compiler.useIncrementalCompilation=false` does not help.""",
                        MARKER)
                .isEmpty();
    }

    @Test
    @DisplayName("the scan finds a planted marker, so a green run means something")
    void theScanFindsAPlantedMarker(@TempDir Path tempDir) throws IOException {
        // Class magic written as bytes rather than as a literal, so the fixture does not depend on
        // the source file's encoding — a test that is about bytes should not be reading a charset.
        byte[] magic = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
        byte[] text = (MARKER + ": foo() is undefined").getBytes(StandardCharsets.US_ASCII);
        byte[] classBytes = new byte[magic.length + text.length];
        System.arraycopy(magic, 0, classBytes, 0, magic.length);
        System.arraycopy(text, 0, classBytes, magic.length, text.length);

        Path planted = tempDir.resolve("Broken.class");
        Files.write(planted, classBytes);
        Files.writeString(tempDir.resolve("Fine.class"), "nothing to see");
        Files.writeString(tempDir.resolve("Broken.txt"), MARKER);

        assertThat(classesCarryingAnEmbeddedError(tempDir))
                .as("""
                        A lint that cannot fail is worse than no lint, because it reads as coverage. \
                        The `.txt` is here for the other half: only compiled output is scanned, so a \
                        doc or a source file quoting the message — several do — is not a finding.""")
                .containsExactly(planted);
    }

    @Test
    @DisplayName("a string literal survives into the class file, which is what the scan relies on")
    void aStringLiteralSurvivesIntoTheClassFile() throws IOException {
        Path own = targetDirectory().resolve("test-classes")
                .resolve(Path.of("com", "viglet", "shio", "ShIdeCompiledClassLintTest.class"));

        assertThat(Files.exists(own)).as("the running test's own output must be findable").isTrue();
        assertThat(contains(Files.readAllBytes(own),
                        CONSTANT_POOL_PROBE.getBytes(StandardCharsets.US_ASCII)))
                .as("""
                        The premise, asserted rather than remembered (SH274's habit): a string \
                        constant in source appears verbatim in the emitted class. JDT's embedded \
                        error message is such a constant, which is why scanning bytes finds it.""")
                .isTrue();
    }

    @Test
    @DisplayName("the marker is not in this lint's own class file")
    void theMarkerIsNotInThisLintsOwnClassFile() throws IOException {
        Path own = targetDirectory().resolve("test-classes")
                .resolve(Path.of("com", "viglet", "shio", "ShIdeCompiledClassLintTest.class"));

        assertThat(contains(Files.readAllBytes(own), MARKER.getBytes(StandardCharsets.US_ASCII)))
                .as("""
                        Written as a literal — or as a compile-time-folded concatenation — the marker \
                        would be in this class's constant pool and the lint would report itself. \
                        String.join defers it to runtime; this is the assertion that keeps somebody \
                        from "simplifying" it back. Note the failure message above interpolates the \
                        marker at runtime for the same reason.""")
                .isFalse();
    }
}
