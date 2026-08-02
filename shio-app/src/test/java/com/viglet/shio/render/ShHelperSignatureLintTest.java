/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH291 — a helper's registered signature must name every hash parameter its body reads, and no more.
 *
 * <h2>Why this exists</h2>
 *
 * <p>SH272 put the call form at the registration, which is the one place that knows both the name and
 * the shape (SH260's rule applied to a third fact). It is still <b>a string somebody has to keep
 * true</b>: nothing failed if {@code search} gained a {@code folder=} and the signature did not say so.
 * The failure would be a context pack teaching an incomplete call, and the author would find out by
 * writing a template whose parameter is <em>silently ignored</em> — worse than the 400 SH272 replaced,
 * because an ignored hash key is not an error at all. The reverse direction is the same defect wearing
 * the other hat: a signature that advertises a parameter no code reads teaches a call that does
 * nothing.</p>
 *
 * <h2>Why a source lint, out of the three candidates</h2>
 *
 * <p>§XVI.43 listed three and asked for a deliberate choice rather than a default.</p>
 *
 * <ul>
 *   <li><b>Reflection cannot answer it.</b> A hash key is read by {@code options.hash("q")} inside a
 *       method body — there is no signature, annotation or type that carries it. Confirmed rather than
 *       assumed: {@code Options.hash} takes a {@code String} at runtime.</li>
 *   <li><b>Declaring it unenforced</b> (SH275's pattern) was the honest fallback, and it is the weaker
 *       option here for a reason specific to this surface: the signature is not documentation, it is
 *       <em>served</em> — the context pack is where an agent learns what it may call, so a stale one
 *       is a wrong instruction with an audience, not a comment nobody reads.</li>
 *   <li><b>A source lint</b>, in the shape {@code ShAgentIncidentFramesTest} and
 *       {@code ShByteStabilityLintTest} already use. Chosen — with the brittleness turned into a
 *       failure instead of a footnote, which is what the other two options do not offer.</li>
 * </ul>
 *
 * <h2>The two ways a text scan can be wrong, both of which fail the build here</h2>
 *
 * <p>A lint that reads source as text has known blind spots, and the difference between a brittle
 * guard and a false one is whether it can tell that it is being fooled:</p>
 *
 * <ul>
 *   <li><b>A key that is not a literal</b> — {@code options.hash(SOME_CONSTANT)} — is invisible to a
 *       scan. Rather than pass and mean nothing, {@link #noHashKeyEscapesTheScan()} fails and says so.
 *       The one legitimate case is an <em>accessor</em> whose key is its own parameter
 *       ({@code hash(Options, String key, String fallback)}), which is recognised by exactly that.</li>
 *   <li><b>A key read one call deeper</b> — {@code query} reads {@code limit} through
 *       {@code limit(options)} — is followed, but only one level. {@link #noHashKeyIsReadTwoLevelsDown()}
 *       fails if a hash-reading private method is called from another private method, so the day that
 *       chain gets longer is the day this test says the expansion is no longer enough.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 */
class ShHelperSignatureLintTest {

    private static final Path HELPERS = Path.of("src", "main", "java", "com", "viglet", "shio",
            "render", "ShRenderHelpers.java");

    /** {@code engine.register("query", ShHelperShape.BLOCK, "{{#query folder= …}}…", this::query)}. */
    private static final Pattern REGISTRATION = Pattern.compile(
            "engine\\.register\\(\\s*\"(\\w+)\"\\s*,\\s*ShHelperShape\\.\\w+\\s*,\\s*"
                    + "\"([^\"]*)\"\\s*,\\s*this::(\\w+)\\s*\\)");

    /** A hash parameter in a signature: the {@code folder} of {@code folder=}. */
    private static final Pattern SIGNATURE_PARAMETER = Pattern.compile("(\\w+)=");

    /** {@code hash(options, "folder", null)} and {@code options.hash("limit")}. */
    private static final Pattern LITERAL_KEY = Pattern.compile(
            "hash\\(\\s*options\\s*,\\s*\"(\\w+)\"|options\\.hash\\(\\s*\"(\\w+)\"\\s*\\)");

    /** The same two reads with something that is not a literal in the key position. */
    private static final Pattern NON_LITERAL_KEY = Pattern.compile(
            "hash\\(\\s*options\\s*,\\s*([A-Za-z_]\\w*)|options\\.hash\\(\\s*([A-Za-z_]\\w*)\\s*\\)");

    /** A method declared in this class, with its parameter list — used to find bodies and accessors. */
    private static final Pattern METHOD = Pattern.compile(
            "(?m)^    (?:(?:public|private|protected|static|final)\\s+)*[\\w<>\\[\\], ?]+\\s+"
                    + "(\\w+)\\(([^)]*)\\)[^{;]*\\{");

    private static String source() {
        try {
            return Files.readString(HELPERS, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("SH291's lint cannot read " + HELPERS.toAbsolutePath(), e);
        }
    }

    // ------------------------------------------------------------------
    // A very small amount of Java parsing: method bodies, and nothing else
    // ------------------------------------------------------------------

    private record Method(String name, String parameters, String body) {

        /** True for {@code hash(Options, String key, String fallback)} — the key is its own parameter. */
        boolean declaresParameter(String token) {
            return Pattern.compile("\\b" + Pattern.quote(token) + "\\b").matcher(parameters).find();
        }

        boolean takesOnlyOptions() {
            return parameters.trim().matches("Options\\s+\\w+");
        }
    }

    /** Every method in the file, in declaration order, keyed by name (last wins on an overload). */
    private static Map<String, Method> methods(String source) {
        Map<String, Method> found = new TreeMap<>();
        Matcher matcher = METHOD.matcher(source);
        while (matcher.find()) {
            int open = source.indexOf('{', matcher.end() - 1);
            found.put(matcher.group(1),
                    new Method(matcher.group(1), matcher.group(2), bodyFrom(source, open)));
        }
        return found;
    }

    /**
     * The text between {@code source[open]}'s brace and its match.
     *
     * <p>Comments and string literals are skipped rather than counted: a helper body holds
     * {@code "{{else}}"} and {@code // {…}} often enough that a naive counter would end the body in
     * the middle of one — and a lint that reads the wrong half of a method is worse than none, because
     * it passes.</p>
     */
    private static String bodyFrom(String source, int open) {
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            switch (c) {
                case '{' -> depth++;
                case '}' -> {
                    depth--;
                    if (depth == 0) {
                        return source.substring(open + 1, i);
                    }
                }
                case '"' -> i = endOfStringLiteral(source, i);
                case '\'' -> i = endOfCharLiteral(source, i);
                case '/' -> i = endOfComment(source, i);
                default -> { /* ordinary code */ }
            }
        }
        throw new IllegalStateException("Unbalanced braces from offset " + open + " in " + HELPERS);
    }

    private static int endOfStringLiteral(String source, int start) {
        boolean textBlock = source.startsWith("\"\"\"", start);
        int i = start + (textBlock ? 3 : 1);
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (textBlock ? source.startsWith("\"\"\"", i) : c == '"') {
                return i + (textBlock ? 2 : 0);
            }
            i++;
        }
        return source.length() - 1;
    }

    private static int endOfCharLiteral(String source, int start) {
        int i = start + 1;
        while (i < source.length() && source.charAt(i) != '\'') {
            i += source.charAt(i) == '\\' ? 2 : 1;
        }
        return i;
    }

    private static int endOfComment(String source, int start) {
        if (source.startsWith("//", start)) {
            int end = source.indexOf('\n', start);
            return end < 0 ? source.length() - 1 : end;
        }
        if (source.startsWith("/*", start)) {
            int end = source.indexOf("*/", start);
            return end < 0 ? source.length() - 1 : end + 1;
        }
        return start;
    }

    // ------------------------------------------------------------------
    // What a helper declares, and what it reads
    // ------------------------------------------------------------------

    private record Helper(String name, String signature, String method) {
    }

    private static List<Helper> registrations(String source) {
        List<Helper> helpers = new ArrayList<>();
        Matcher matcher = REGISTRATION.matcher(source);
        while (matcher.find()) {
            helpers.add(new Helper(matcher.group(1), matcher.group(2), matcher.group(3)));
        }
        return helpers;
    }

    private static Set<String> declaredParameters(Helper helper) {
        Set<String> declared = new TreeSet<>();
        Matcher matcher = SIGNATURE_PARAMETER.matcher(helper.signature());
        while (matcher.find()) {
            declared.add(matcher.group(1));
        }
        return declared;
    }

    private static Set<String> literalKeysIn(String body) {
        Set<String> keys = new TreeSet<>();
        Matcher matcher = LITERAL_KEY.matcher(body);
        while (matcher.find()) {
            keys.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
        }
        return keys;
    }

    /** Private {@code (Options)} methods that read hash keys — the one level of indirection followed. */
    private static Map<String, Set<String>> indirectReaders(Map<String, Method> methods) {
        Map<String, Set<String>> readers = new TreeMap<>();
        methods.values().stream()
                .filter(Method::takesOnlyOptions)
                .forEach(method -> {
                    Set<String> keys = literalKeysIn(method.body());
                    if (!keys.isEmpty()) {
                        readers.put(method.name(), keys);
                    }
                });
        return readers;
    }

    /** Everything a helper actually reads: its own literal keys plus one level of {@code f(options)}. */
    private static Set<String> keysRead(Helper helper, Map<String, Method> methods) {
        Method body = methods.get(helper.method());
        assertThat(body).as("SH291's lint found no body for helper method %s", helper.method()).isNotNull();
        Set<String> keys = new TreeSet<>(literalKeysIn(body.body()));
        indirectReaders(methods).forEach((name, indirect) -> {
            if (Pattern.compile("\\b" + Pattern.quote(name) + "\\(\\s*options\\s*\\)")
                    .matcher(body.body()).find()) {
                keys.addAll(indirect);
            }
        });
        return keys;
    }

    // ------------------------------------------------------------------
    // The lint
    // ------------------------------------------------------------------

    @Test
    @DisplayName("every registered signature names exactly the hash keys its helper reads")
    void everySignatureNamesExactlyTheKeysItsHelperReads() {
        String source = source();
        Map<String, Method> methods = methods(source);
        Map<String, String> wrong = new TreeMap<>();

        for (Helper helper : registrations(source)) {
            Set<String> declared = declaredParameters(helper);
            Set<String> read = keysRead(helper, methods);
            if (!declared.equals(read)) {
                Set<String> undeclared = new LinkedHashSet<>(read);
                undeclared.removeAll(declared);
                Set<String> unread = new LinkedHashSet<>(declared);
                unread.removeAll(read);
                wrong.put(helper.name(), "signature `" + helper.signature() + "` declares " + declared
                        + " and the body reads " + read
                        + (undeclared.isEmpty() ? "" : " — undeclared: " + undeclared)
                        + (unread.isEmpty() ? "" : " — declared but never read: " + unread));
            }
        }

        assertThat(wrong)
                .as("""
                        SH291: the signature at engine.register(...) is what the context pack serves \
                        as "what may a template call", so a stale one is a wrong instruction with an \
                        audience. A key the body reads and the signature omits teaches an incomplete \
                        call; a key the signature declares and nothing reads teaches a parameter that \
                        is silently ignored, which is not even an error at write time.

                        Fix: update the signature string beside this::<helper> in ShRenderHelpers, or \
                        stop reading the key.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the parse anchors on the real registrations, so a green run is not an empty one")
    void theParseAnchorsOnTheRealRegistrations() {
        String source = source();
        List<Helper> helpers = registrations(source);

        assertThat(helpers).extracting(Helper::name)
                .as("""
                        The closed helper set (SH260 — the engine is its single source). A parser that \
                        silently matched nothing would make every assertion above vacuously true, \
                        which is the failure mode of a text lint nobody anchored.""")
                .containsExactly("query", "navigation", "search", "getRelation", "file_url", "form");

        Helper query = helpers.stream().filter(h -> h.name().equals("query")).findFirst().orElseThrow();
        assertThat(keysRead(query, methods(source)))
                .as("""
                        Read from the body, and `limit` is the interesting one: it arrives through \
                        limit(options), so this also asserts the one level of indirection actually \
                        resolves rather than being dead code in the lint.""")
                .containsExactly("folder", "limit", "type");
        assertThat(declaredParameters(query)).containsExactly("folder", "limit", "type");
    }

    @Test
    @DisplayName("no hash key escapes the scan by not being a literal")
    void noHashKeyEscapesTheScan() {
        String source = source();
        Map<String, String> escaping = new TreeMap<>();

        for (Method method : methods(source).values()) {
            Matcher matcher = NON_LITERAL_KEY.matcher(method.body());
            while (matcher.find()) {
                String token = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                if (!method.declaresParameter(token)) {
                    escaping.put(method.name(), token);
                }
            }
        }

        assertThat(escaping)
                .as("""
                        This lint reads source as text, so a key that is not a string literal is \
                        invisible to it — and a guard that cannot see its subject passes. Rather than \
                        be quietly wrong, it fails here.

                        Fix: inline the literal at the read, or teach this lint to resolve the \
                        constant. An accessor whose key is its own parameter — hash(Options, String \
                        key, String fallback) — is exempt by construction, because the literal is at \
                        its call sites, where the scan already finds it.""")
                .isEmpty();
    }

    @Test
    @DisplayName("no hash key is read two levels below a helper")
    void noHashKeyIsReadTwoLevelsDown() {
        String source = source();
        Map<String, Method> methods = methods(source);
        Set<String> helperMethods = new TreeSet<>();
        registrations(source).forEach(helper -> helperMethods.add(helper.method()));

        Map<String, String> tooDeep = new TreeMap<>();
        indirectReaders(methods).forEach((reader, keys) -> methods.values().stream()
                .filter(caller -> !helperMethods.contains(caller.name()))
                .filter(caller -> !caller.name().equals(reader))
                .filter(caller -> !indirectReaders(methods).containsKey(caller.name())
                        || !caller.name().equals(reader))
                .filter(caller -> Pattern.compile("\\b" + Pattern.quote(reader) + "\\(\\s*options\\s*\\)")
                        .matcher(caller.body()).find())
                .forEach(caller -> tooDeep.put(reader, "called from " + caller.name()
                        + ", which is not a registered helper — keys " + keys
                        + " would not be attributed to any signature")));

        assertThat(tooDeep)
                .as("""
                        The expansion above follows exactly one call: helper -> f(options). A chain \
                        one link longer is not wrong code, but it is invisible to this lint, so it \
                        fails here instead of passing while measuring nothing.

                        Fix: read the key in the helper's own body, or extend keysRead to follow the \
                        chain (and then delete this test, deliberately).""")
                .isEmpty();
    }
}
