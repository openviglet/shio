/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * SH924 &mdash; which create endpoints let a client choose the primary key, asked of all of them.
 *
 * <h2>The sweep is the deliverable, and it contradicted the report that asked for it</h2>
 *
 * <p>SH924 was filed on one endpoint and predicted a class: "groups are unlikely to be the only
 * entity that does this &mdash; every {@code POST /api/v2/{entity}} taking a full entity shape has
 * the same hole." <b>Measured, it was the only one.</b> Of the 23 POST handlers taking a
 * {@code @RequestBody}, twenty-two take a purpose-built {@code *Request} / {@code *Payload} /
 * {@code *Def} record with no id component at all, so the key is the server's by construction and
 * there is nothing for a client to set. {@code ShGroupAPI} was the last entity-shaped create body
 * in the tree.</p>
 *
 * <p>That is worth a lint precisely <i>because</i> the count is one. A prediction that turns out
 * false today says nothing about tomorrow: the defect is one {@code @RequestBody ShThingDTO} away,
 * it compiles, it answers 200, and the only person who can tell is the client that sent it. The
 * cheap thing to keep is the shape, not the fix.</p>
 *
 * <h2>What it reads, and the one thing it deliberately does not</h2>
 *
 * <p>This asks a <b>structural</b> question &mdash; does a create endpoint's body type carry a
 * settable {@code String id}? &mdash; and not a behavioural one. Whether a given handler then
 * <i>honours</i> that id is a data-flow question no regex answers honestly, and a lint that
 * pretended to answer it would be the kind that goes quiet the first time somebody renames a
 * variable. The behaviour is asserted where behaviour belongs, in
 * {@code ShGroupCreateIdTest}, against a running context.</p>
 *
 * <p>So a body type carrying an id is a <b>finding to explain</b>, not proof of a defect. The map
 * below is the explanation, one row per type, with the assertion that covers it named. A named list
 * rather than a ceiling, for the reason the console-chrome census states: a ceiling lets one
 * exempted type pay for the next.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShCreateBodyKeyLintTest {

    private static final Path API_ROOT =
            Paths.get("src", "main", "java", "com", "viglet", "shio", "api");

    /**
     * Create-body types that carry a {@code String id}, and why each is not a stranded row waiting
     * to happen. Add a row only with the assertion that proves it.
     */
    private static final Map<String, String> EXPLAINED = new LinkedHashMap<>();

    static {
        EXPLAINED.put("ShGroupDTO",
                "Shared with PUT /{id} and used for the response, so the field has to exist. POST "
                        + "mints over a blank one and PUT takes its id from the path; both are "
                        + "asserted by ShGroupCreateIdTest (SH924).");
    }

    /**
     * {@code @PostMapping ... public X method(... @RequestBody T body ...)}, comments removed.
     *
     * <p>The signature between {@code public} and the parameter list is matched with a negated
     * class rather than a character union: {@code [\w<>,.\s\[\]]+?} backtracks super-linearly on a
     * long non-matching run, and this runs over every controller on every build.</p>
     */
    private static final Pattern POST_HANDLER = Pattern.compile(
            "@PostMapping(?:\\([^)]*\\))?[\\s\\S]{0,700}?public\\s[^(]{0,200}\\(([^)]*)\\)");

    private static final Pattern REQUEST_BODY =
            Pattern.compile("@RequestBody\\s+(?:@\\w+\\s+)*([\\w<>]+)\\s+\\w+");

    /**
     * The shared stripper, not a local regex. This reader asks about declarations, and a javadoc
     * sentence naming one must not read as one &mdash; SH206's build went red for explaining why a
     * class no longer carried an annotation. {@code ShSourceText.code} is also what knows that a
     * {@code "/*"} inside a string literal is not a comment.
     */
    private static String withoutComments(String source) {
        return ShSourceText.code(source);
    }

    private static List<Path> javaFiles(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every type in the tree declaring a settable {@code String id}, by simple name. */
    private static Map<String, Path> typesDeclaringAnId() {
        Path main = Paths.get("src", "main", "java");
        Map<String, Path> found = new LinkedHashMap<>();
        for (Path file : javaFiles(main)) {
            String bare = withoutComments(read(file));
            Matcher declaration = Pattern.compile(
                    "(?:^|\\n)\\s*(?:public\\s+|final\\s+|abstract\\s+|static\\s+)*(?:class|record)\\s+(\\w+)")
                    .matcher(bare);
            if (!declaration.find()) {
                continue;
            }
            String name = declaration.group(1);
            boolean asField = Pattern.compile("private\\s+String\\s+id\\s*[;=]").matcher(bare).find();
            boolean asComponent = Pattern.compile(
                    "record\\s+" + Pattern.quote(name) + "\\s*\\([^)]*\\bString\\s+id\\b", Pattern.DOTALL)
                    .matcher(bare).find();
            if (asField || asComponent) {
                found.put(name, file);
            }
        }
        return found;
    }

    private static List<String> createBodiesCarryingAnId() {
        Map<String, Path> withId = typesDeclaringAnId();
        List<String> carrying = new ArrayList<>();
        for (Path file : javaFiles(API_ROOT)) {
            if (!file.getFileName().toString().endsWith("API.java")) {
                continue;
            }
            Matcher handler = POST_HANDLER.matcher(withoutComments(read(file)));
            while (handler.find()) {
                Matcher body = REQUEST_BODY.matcher(handler.group(1));
                if (!body.find()) {
                    continue;
                }
                String type = body.group(1).replaceAll("<.*", "");
                if (withId.containsKey(type) && !carrying.contains(type)) {
                    carrying.add(type);
                }
            }
        }
        return carrying;
    }

    @Test
    void everyCreateBodyCarryingAKeyIsExplained() {
        List<String> carrying = createBodiesCarryingAnId();

        List<String> unexplained = carrying.stream().filter(type -> !EXPLAINED.containsKey(type)).toList();
        assertTrue(unexplained.isEmpty(),
                "SH924: a POST endpoint takes a request body that carries a settable String id: "
                        + unexplained + ".\n"
                        + "  A client can then choose the primary key of the row it creates, and a "
                        + "BLANK one is the case that hurts: it persists verbatim, the write answers "
                        + "200, the row is listed by the collection endpoint, and nothing can address "
                        + "it - /v2/thing/ does not match /v2/thing/{id}, so removing it takes a "
                        + "database statement.\n"
                        + "  Prefer a request record with no id: twenty-two of the twenty-three POST "
                        + "handlers already do, which is why this list is as short as it is.\n"
                        + "  If the type genuinely needs the field - shared with PUT, or echoed in "
                        + "the response - add it to EXPLAINED with the assertion that proves a blank "
                        + "id cannot strand a row.");

        List<String> stale = EXPLAINED.keySet().stream().filter(type -> !carrying.contains(type)).toList();
        assertTrue(stale.isEmpty(),
                "SH924: " + stale + " is explained here and is no longer a create body carrying an "
                        + "id. Either the endpoint took a request record - delete the row - or this "
                        + "reader stopped matching it, which is the direction that goes quiet.");
    }

    @Test
    void theReaderIsNotVacuous() {
        // Both halves, because each fails silently in its own way. A walk that finds no handlers
        // reports a clean tree; a type scan that finds no ids reports every body as safe.
        Map<String, Path> withId = typesDeclaringAnId();
        assertTrue(withId.size() > 30,
                "SH924: only " + withId.size() + " types in src/main/java declare a String id. The "
                        + "entity model alone has more than that, so this scan has stopped matching "
                        + "and every create body would read as carrying no key.");
        assertTrue(withId.containsKey("ShGroupDTO"),
                "SH924: ShGroupDTO is not seen as declaring an id, so the one type this lint was "
                        + "written against would not be found. Comment stripping or the field "
                        + "pattern has drifted.");

        long handlers = javaFiles(API_ROOT).stream()
                .filter(file -> file.getFileName().toString().endsWith("API.java"))
                .flatMap(file -> {
                    List<String> bodies = new ArrayList<>();
                    Matcher handler = POST_HANDLER.matcher(withoutComments(read(file)));
                    while (handler.find()) {
                        Matcher body = REQUEST_BODY.matcher(handler.group(1));
                        if (body.find()) {
                            bodies.add(body.group(1));
                        }
                    }
                    return bodies.stream();
                })
                .count();
        assertTrue(handlers > 15,
                "SH924: only " + handlers + " POST handlers with a request body were found. There "
                        + "were 23 when this shipped, so the handler pattern no longer matches the "
                        + "controllers and this lint is reading almost nothing.");

        assertEquals(List.of("ShGroupDTO"), createBodiesCarryingAnId(),
                "SH924: the set of create bodies carrying a key changed. This is the measurement "
                        + "the task turned on - one, where the report predicted many - so a change "
                        + "here is a finding either way: a new entity-shaped body, or the last one "
                        + "finally replaced by a request record.");
    }
}
