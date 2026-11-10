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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * SH1437 — the server's strings name the product Viglet Shio, by an agreed list.
 *
 * <p>SH1433 put the console's locale files under a full-names lint; the server's strings had none,
 * and 34 string literals in {@code shio-app/src/main/java} said a bare {@code Shio}. They are not one
 * population, so this is not a sweep: a string a person reads, or an agent meets only when something
 * is refused or found (a problem document, a verify finding, the preview banner, the browser's
 * Basic dialog), says Viglet Shio. The rest are held below by file, with how many and why, so a new
 * one is a diff that has to argue for itself.</p>
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShServerFullNamesLintTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /** A string literal or a text block, after comments are gone. */
    private static final Pattern LITERAL = Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|\"(?:[^\"\\\\\\n]|\\\\.)*\"");

    /** A bare product name: not after {@code Viglet }, not part of a path, a package or a URI. */
    private static final Pattern BARE = Pattern.compile("(?<!Viglet )(?<![\\w/.@:-])Shio\\b(?![\\w-]*\\.(?:java|ts|mjs|md))");

    /**
     * The agreed exceptions: file, how many bare mentions it may hold, and why.
     *
     * <p>A count rather than a file-wide pass, so a second mention in an excepted file is still news.</p>
     */
    private static final Map<String, Exception> EXCEPTIONS = new LinkedHashMap<>();

    private record Exception(int count, String why) {
    }

    private static void except(String file, int count, String why) {
        EXCEPTIONS.put(file, new Exception(count, why));
    }

    static {
        String everyTurn = "sent to an agent on every tools/list or resource read, so each rename is a "
                + "measured token cost (P3); the agent addresses the server it is connected to";
        except("ShMcpResources.java", 9, "resource titles, " + everyTurn);
        except("ShMcpPrompts.java", 5, "prompt text, " + everyTurn);
        except("ShContextTool.java", 1, "the context tool's description, " + everyTurn);
        except("ShAgentManifestService.java", 1, "a manifest sentence, read once per session and "
                + "held by the token budget");
        except("ShAgentAgentsMd.java", 1, "the heading of the generated AGENTS.md, which an agent "
                + "loads into every turn of its own session");
        except("ShAgentRenderService.java", 1, "a field note in every render digest an agent asks for");
        String seeded = "a row seeded on first start whose label the console shows; renaming it is a "
                + "data migration on every instance, and the docs lint holds it as a label";
        except("ShAuthProviderInstanceOnStartup.java", 2, seeded);
        except("ShAuthProviderVendorOnStartup.java", 2, seeded);
        except("ShExchangeProviderVendorOnStartup.java", 2, seeded);
        except("ShUserOnStartup.java", 1, "the seeded admin's first name, " + seeded);
        except("ShDeviceTokenFilter.java", 1, "a Bearer realm, which no browser shows to anyone");
    }

    @Test
    @DisplayName("SH1437: no server string a person reads calls the product by its short name")
    void everyServerStringNamesViglet() throws IOException {
        Map<String, List<String>> found = new TreeMap<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String code = ShSourceText.code(Files.readString(file, StandardCharsets.UTF_8));
                Matcher literal = LITERAL.matcher(code);
                while (literal.find()) {
                    if (BARE.matcher(literal.group()).find()) {
                        found.computeIfAbsent(file.getFileName().toString(), key -> new ArrayList<>())
                                .add(literal.group().length() > 90
                                        ? literal.group().substring(0, 90) : literal.group());
                    }
                }
            }
        }
        System.out.printf("%n[SH1437] %d files hold a bare Shio in a string, %d excepted%n%n",
                found.size(), EXCEPTIONS.size());

        List<String> unexplained = new ArrayList<>();
        found.forEach((file, literals) -> {
            Exception allowed = EXCEPTIONS.get(file);
            if (allowed == null || literals.size() > allowed.count()) {
                unexplained.add(file + " " + literals);
            }
        });
        assertThat(unexplained)
                .as("SH1437: a bare Shio in a server string. Write Viglet Shio; if an agent meets it on "
                        + "every turn or it is seeded data, add it to EXCEPTIONS with the count and why")
                .isEmpty();

        List<String> stale = new ArrayList<>();
        EXCEPTIONS.forEach((file, allowed) -> {
            int holds = found.getOrDefault(file, List.of()).size();
            if (holds < allowed.count()) {
                stale.add(file + " is excepted for " + allowed.count() + " and holds " + holds);
            }
        });
        assertThat(stale).as("SH1437: an exception wider than what it excuses; lower it").isEmpty();
    }
}
