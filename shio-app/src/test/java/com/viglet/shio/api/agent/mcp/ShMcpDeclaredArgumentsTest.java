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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH952 — a tool that advertises an argument reads it, or refuses it.
 *
 * <h2>The defect this generalises</h2>
 *
 * <p>{@code shio_write}'s schema advertised {@code when} — "post.publish: ISO-8601 instant to
 * schedule for" — and {@code call} built every op through {@code ShAgentBatch.Op}'s <b>six-field</b>
 * constructor, whose javadoc says it is for the ops that never schedule. So the other five fields
 * arrived as {@code null}: over MCP, which is how most agents write, a publish meant for next
 * Monday happened <em>now</em>, and reported success. The REST batch deserialises all eleven, which
 * is why nothing looked wrong from the console, and the golden {@code tools/list} only ever
 * recorded the schema — so the one artefact that named {@code when} was the one that could not
 * tell whether anybody read it.</p>
 *
 * <h2>Why the instrument rather than the one-line fix</h2>
 *
 * <p>SH338's order, and it earns it here: an argument silently dropped is invisible from both
 * ends. The caller sees a success, the schema still says the argument exists, and no test that
 * passes the argument exists <em>because</em> nobody thought it was ignored. That is a class, not
 * an instance, and the only thing that finds the rest of it is a reader over every tool.</p>
 *
 * <h2>Declared at runtime, read from source</h2>
 *
 * <p>The declared side comes from {@code tools/list} itself, which is the artefact a model reads
 * and therefore the only authoritative list. The read side is the tool's own source, scanned for
 * the name as a string literal — because "is this argument used" has no runtime form short of
 * calling every tool with every argument and diffing the effects, which is a suite and not a
 * check. A name that appears in the code is not proof it is used correctly; a name that appears
 * <b>nowhere</b> is proof it cannot be.</p>
 *
 * <p>Comments are stripped (SH206's rule via {@link ShSourceText}), so the javadoc explaining why
 * an argument was dropped cannot read as the code reading it — which is exactly how this defect
 * would have hidden from a raw grep.</p>
 *
 * <h2>Why the read is a call and not a mention, which this got wrong first</h2>
 *
 * <p>The first version asked whether the tool's source contains the name as a string literal, and
 * it <b>passed on the defect it was written for</b>. Of course it did: the schema that declares
 * {@code when} is in the same class, so the literal is always there — the reader was comparing a
 * declaration against itself. What distinguishes a read is the <em>call</em>:
 * {@code ShMcpArgs.string(op, "when")}, which is how every tool here takes an argument. So the
 * pattern is the accessor, and a name that is declared and never passed to one is the finding.</p>
 */
@SpringBootTest
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShMcpDeclaredArgumentsTest {

    /** Where a tool's implementation lives, by the name it publishes. */
    private static final Path TOOLS = Path.of("src", "main", "java", "com", "viglet", "shio",
            "api", "agent", "mcp", "tools");

    /**
     * Arguments a tool declares and deliberately does not read in its own class.
     *
     * <p>Empty, and that is the point: every entry here would be a tool whose schema promises
     * something its handler never looks at, which is the defect. An addition needs a reason in the
     * diff somebody reviews.</p>
     */
    private static final Set<String> EXEMPT = Set.of();

    @Autowired
    private ShMcpServer shMcpServer;
    @Autowired
    private ObjectMapper objectMapper;

    private ObjectNode call(String method) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("jsonrpc", "2.0");
        message.put("id", 1);
        message.put("method", method);
        return shMcpServer.handle(message);
    }

    /** Every property name a schema declares, at any depth — `ops.items.properties` included. */
    private static void collectProperties(JsonNode node, Set<String> out) {
        if (node == null || !node.isObject()) {
            return;
        }
        JsonNode properties = node.get("properties");
        if (properties != null && properties.isObject()) {
            properties.propertyNames().forEach(out::add);
        }
        node.propertyStream().forEach(entry -> collectProperties(entry.getValue(), out));
        if (node.get("items") != null) {
            collectProperties(node.get("items"), out);
        }
    }

    @Test
    void sh952EveryDeclaredArgumentIsReadByTheToolThatDeclaresIt() throws IOException {
        JsonNode tools = call("tools/list").get("result").get("tools");
        assertThat(tools).as("SH952: tools/list answered nothing to read").isNotEmpty();

        List<String> unread = new ArrayList<>();
        int declared = 0;

        for (JsonNode tool : tools) {
            String name = tool.get("name").asString();
            Set<String> properties = new LinkedHashSet<>();
            collectProperties(tool.get("inputSchema"), properties);
            declared += properties.size();

            String source = ShSourceText.code(sourceOf(name));
            for (String property : properties) {
                if (EXEMPT.contains(name + "." + property)) {
                    continue;
                }
                if (!readsArgument(source, property)) {
                    unread.add(name + "." + property);
                }
            }
        }

        // A walk that stops finding schemas passes for the wrong reason (SH322). Twelve tools
        // declaring 40-odd properties when this shipped; the floor is a floor, not the number.
        assertThat(declared)
                .as("SH952: no properties were read out of tools/list, so this asserted nothing")
                .isGreaterThan(25);

        assertThat(unread)
                .as("SH952: a tool declares an argument its own class never names, so a caller who "
                        + "passes it gets a success and no effect. That is invisible from both "
                        + "ends: the schema still advertises it, and no test passes it precisely "
                        + "because nobody suspected it was dropped.\n"
                        + "  Read it in the handler, or stop declaring it. If it is genuinely "
                        + "accepted elsewhere, add it to EXEMPT with the reason.")
                .isEmpty();
    }

    /**
     * Whether this source <em>reads</em> the argument, rather than merely naming it.
     *
     * <p>{@code ShMcpArgs.<kind>(<node>, "<name>")} is how a tool usually takes one. Matching the
     * accessor is what separates a read from the schema entry that declares it — the two sit in the
     * same class, so a plain search for the literal compares a declaration against itself and finds
     * every argument "used".</p>
     *
     * <p><b>Two forms, and the second was found by a false positive.</b> The first run reported
     * {@code shio_apply.publish} beside the real defect; it is read, by {@code post.get("publish")}
     * inside a helper that has to tell absent from {@code false}, because omitting the field means
     * "leave alone" and {@code false} means "unpublish". So a raw {@code get} on a node is the other
     * way a tool reads an argument, and a reader that knew only one accessor would have cost
     * somebody a rewrite of code that was already correct.</p>
     */
    private static boolean readsArgument(String source, String property) {
        String name = Pattern.quote(property);
        return Pattern
                .compile("ShMcpArgs\\.\\w+\\s*\\(\\s*[\\w.]+\\s*,\\s*\"" + name + "\""
                        + "|\\.get\\s*\\(\\s*\"" + name + "\"\\s*\\)")
                .matcher(source)
                .find();
    }

    /** A tool's own source, by the class whose {@code name()} returns the published name. */
    private static String sourceOf(String toolName) throws IOException {
        assertThat(Files.isDirectory(TOOLS))
                .as("SH952: expected to run from the shio-app module, with %s on disk", TOOLS)
                .isTrue();
        try (Stream<Path> files = Files.list(TOOLS)) {
            for (Path java : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(java);
                // The class that publishes this name, found by its own `name()` rather than by a
                // filename convention — `shio_write` is `ShWriteTool`, and the mapping is a habit
                // rather than a rule.
                if (ShSourceText.code(text).contains("\"" + toolName + "\"")) {
                    return text;
                }
            }
        }
        throw new AssertionError("SH952: no class under " + TOOLS + " publishes " + toolName
                + ", so its declared arguments cannot be checked against anything");
    }
}
