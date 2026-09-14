/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.lint;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Which controller roots exist, and which HTTP verbs each declares (SH973).
 *
 * <h2>Why this is a helper and not a third copy</h2>
 *
 * <p>{@code ShStatelessReachabilityLintTest} asked this question about mutating mappings, from
 * patterns and a walk it carried itself. SH973 asks the same question about {@code GET} — a
 * read-only controller is classified by neither P1 instrument, so the surface an agent cannot
 * <em>read</em> was never inventoried at all. Two lints reading one program need one reader:
 * a second copy of {@code CLASS_MAPPING} is a second answer to "what is a controller root", and the
 * cheapest way for the two inventories to disagree about the same file.</p>
 *
 * <p>Read from source rather than from a booted {@code RequestMappingHandlerMapping}, which is the
 * choice its first caller made and for its reason: the question is answered by the code as written,
 * and a lint that needs a Spring context is a lint nobody runs while editing.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
public final class ShControllerScan {

    /** Where the controllers are. Relative, like every other scan here — surefire runs in the module. */
    private static final Path MAIN = Path.of("src", "main", "java", "com", "viglet", "shio");

    /** {@code @RequestMapping} on a class, in each of the three spellings this codebase uses. */
    private static final Pattern CLASS_MAPPING = Pattern.compile(
            "@RequestMapping\\(\\s*(?:value\\s*=\\s*)?(\"([^\"]*)\"|[A-Za-z_][\\w.]*)");
    /**
     * A method's mapping, with whatever it was given.
     *
     * <p>Group 2 is the argument list, which matters only for a controller that declares no
     * class-level root: {@code ShCsrfController} and {@code ShIndexContext} spell the whole path on
     * the method, and a scan that skipped them would under-report by exactly the two most
     * infrastructure-shaped routes in the tree.</p>
     */
    private static final Pattern VERB_MAPPING = Pattern.compile(
            "@(Get|Post|Put|Delete|Patch)Mapping\\b\\s*(?:\\(([^)]*)\\))?");

    /** A path literal inside a mapping's arguments — a path starts with `/`, a media type does not. */
    private static final Pattern PATH_LITERAL = Pattern.compile("\"(/[^\"]*)\"");
    /** {@code public static final String X = "/literal";} — for a mapping given as a constant. */
    private static final Pattern CONSTANT = Pattern.compile(
            "String\\s+(\\w+)\\s*=\\s*\"([^\"]*)\"");

    private ShControllerScan() {
        // utility
    }

    /**
     * One scan's result.
     *
     * @param byRoot     controller root → the wanted verbs it declares, both sorted so a diff of a
     *                   printed inventory reads as an addition rather than a reshuffle
     * @param unresolved files that declare a wanted verb and no resolvable root. <b>Never silently
     *                   dropped</b>: under-reporting is the one way a lint like this goes wrong,
     *                   so the caller asserts this is empty
     */
    public record Scan(Map<String, Set<String>> byRoot, List<String> unresolved) {
    }

    /**
     * Every controller root declaring at least one of {@code wanted}, with the verbs it declares.
     *
     * <p>Two controllers may share a root, so verb sets are merged rather than replaced.</p>
     *
     * @param wanted upper-case HTTP verbs, e.g. {@code Set.of("GET")}
     */
    public static Scan of(Set<String> wanted) {
        Map<String, Set<String>> byRoot = new TreeMap<>();
        List<String> unresolved = new ArrayList<>();
        sources().forEach((name, body) -> {
            Map<String, Set<String>> onMethods = new LinkedHashMap<>();
            Set<String> verbs = new TreeSet<>();
            Matcher mapping = VERB_MAPPING.matcher(body);
            while (mapping.find()) {
                String verb = mapping.group(1).toUpperCase(Locale.ROOT);
                if (!wanted.contains(verb)) {
                    continue;
                }
                verbs.add(verb);
                for (String path : paths(mapping.group(2), body)) {
                    onMethods.computeIfAbsent(path, ignored -> new TreeSet<>()).add(verb);
                }
            }
            if (verbs.isEmpty()) {
                return;
            }
            String root = rootOf(body);
            if (root != null && !root.isBlank()) {
                byRoot.computeIfAbsent(root, ignored -> new TreeSet<>()).addAll(verbs);
                return;
            }
            // No class-level root: the methods carry whole paths, and each is its own root.
            if (onMethods.isEmpty()) {
                unresolved.add(name + " (no resolvable @RequestMapping on the class)");
                return;
            }
            onMethods.forEach((path, methodVerbs) ->
                    byRoot.computeIfAbsent(path, ignored -> new TreeSet<>()).addAll(methodVerbs));
        });
        return new Scan(Map.copyOf(byRoot), List.copyOf(unresolved));
    }

    /**
     * Does a path pattern cover this controller root — the security config's question.
     *
     * <p>A pattern covers a root only when it names the root itself or a prefix of it. One declared
     * path <em>inside</em> a root does not cover the root, which is the difference from
     * {@link #declares}: exempting {@code /api/v2/agent/read} from CSRF says nothing about the rest
     * of the controller, while declaring it in the manifest does make the controller findable.</p>
     */
    public static boolean covers(String pattern, String root) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return root.equals(prefix) || root.startsWith(prefix + "/");
        }
        return pattern.equals(root);
    }

    /** Does an index declare this root — the root itself, or any path beneath it (SH973)? */
    public static boolean declares(java.util.Collection<String> paths, String root) {
        return paths.stream().anyMatch(path -> path.equals(root) || path.startsWith(root + "/")
                || covers(path, root));
    }

    /**
     * The path patterns a session-less caller may write to (SH975).
     *
     * <p>Every CSRF exemption in the security configuration, with its constant resolved to the
     * literal. Here rather than in one lint because two instruments now ask it: the reachability
     * inventory asks which mutating roots are writable, and the P1 table asks whether an
     * {@code OPERATOR_HTTP} row is telling the truth. Two readers of one configuration is how they
     * came to disagree about the review queue in the first place.</p>
     */
    public static List<String> csrfExemptions() {
        String config = sources().get("ShSecurityConfig.java");
        if (config == null) {
            throw new IllegalStateException("ShSecurityConfig.java is gone — move this reader with "
                    + "the configuration rather than deleting it");
        }
        int start = config.indexOf(".ignoringRequestMatchers(");
        if (start < 0) {
            throw new IllegalStateException("the CSRF configuration moved; see above");
        }
        int end = config.indexOf("))\n", start);
        String block = config.substring(start, end < 0 ? config.length() : end);

        Map<String, String> constants = constants(config);
        List<String> patterns = new ArrayList<>();
        Matcher matcher = Pattern.compile("mvc\\.matcher\\((\\w+)\\)").matcher(block);
        while (matcher.find()) {
            String literal = constants.get(matcher.group(1));
            if (literal == null) {
                throw new IllegalStateException(matcher.group(1)
                        + " is exempted and its literal could not be resolved in the config");
            }
            patterns.add(literal);
        }
        return List.copyOf(patterns);
    }

    /** Every main source file, keyed by file name, with its comments stripped (SH613). */
    public static Map<String, String> sources() {
        try (Stream<Path> files = Files.walk(MAIN)) {
            Map<String, String> byName = new TreeMap<>();
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> byName.put(path.getFileName().toString(),
                            ShSourceText.code(read(path))));
            return byName;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The paths a method mapping names, literal or constant, or empty when it names none. */
    private static List<String> paths(String arguments, String body) {
        if (arguments == null || arguments.isBlank()) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        Matcher literal = PATH_LITERAL.matcher(arguments);
        while (literal.find()) {
            paths.add(literal.group(1));
        }
        if (paths.isEmpty()) {
            Map<String, String> constants = constants(body);
            for (String token : arguments.split("[,{}()\\s]+")) {
                String simple = token.substring(token.lastIndexOf('.') + 1);
                String resolved = constants.get(simple);
                if (resolved != null && resolved.startsWith("/")) {
                    paths.add(resolved);
                }
            }
        }
        return paths;
    }

    /** The class-level mapping's literal, resolving a constant declared in the same file. */
    private static String rootOf(String body) {
        Matcher mapping = CLASS_MAPPING.matcher(body);
        if (!mapping.find()) {
            return null;
        }
        String literal = mapping.group(2);
        if (literal != null) {
            return literal;
        }
        // A constant: `@RequestMapping(ShMcpAPI.MCP_PATH)`. Resolve it in the same file.
        String reference = mapping.group(1);
        String simple = reference.substring(reference.lastIndexOf('.') + 1);
        Map<String, String> constants = constants(body);
        return constants.get(simple);
    }

    private static Map<String, String> constants(String body) {
        Map<String, String> byName = new LinkedHashMap<>();
        Matcher constant = CONSTANT.matcher(body);
        while (constant.find()) {
            byName.putIfAbsent(constant.group(1), constant.group(2));
        }
        return byName;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path.toAbsolutePath(), e);
        }
    }
}
