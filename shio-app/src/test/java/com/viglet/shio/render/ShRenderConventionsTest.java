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
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.api.agent.ShTokenBudget;

/**
 * SH292 — the pack tells an author what a template may call <em>and</em> the model it runs against,
 * derived from the two places that own those facts, under the first ceiling that section has had.
 *
 * <h2>Three claims, one per test</h2>
 *
 * <ul>
 *   <li>The text is <b>derived</b>: every {@link ShRenderHelpers#ROW_KEYS} and every
 *       {@link ShRenderModel#FRAMES} appears in it, so a key added to either is taught rather than
 *       silently omitted. A hand-written list would be a wrong instruction the moment either changed,
 *       and this text is <em>served</em> — the pack is where an agent learns what it may write.</li>
 *   <li>{@code FRAMES} is a <b>source</b> and not a fourth copy: it matches what the render package's
 *       writers actually put, in both directions.</li>
 *   <li>It is <b>budgeted</b>. The conventions section had no ceiling — the budget test measures the
 *       pack with {@code include=instance,sites,sitemap}, so the one part of the pack that carries
 *       paragraphs was the one part nothing measured. §XVI.44 feared the render facts would "roughly
 *       double" that line, which is why this task hesitated; measured, they add <b>~49%</b> — 120
 *       tokens of helpers, 59 of model, 179 total — and the measurement itself removed 15 of them by
 *       showing the draft repeated the block-helper names one line after listing them.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 */
class ShRenderConventionsTest {

    /** The real capability set's shape — five forms, as SH272 registers them. */
    private static final List<String> SIGNATURES = List.of(
            "{{#query folder= type= limit=}}…{{/query}}",
            "{{#navigation folder=}}…{{/navigation}}",
            "{{#search q= type= limit=}}…{{/search}}",
            "{{#getRelation FIELD}}…{{/getRelation}}",
            "{{file_url FIELD}}");

    private static final Path BUDGETS = Path.of("src", "test", "resources", "token-budgets.properties");
    private static final Path RENDER_SOURCES = Path.of("src", "main", "java", "com", "viglet", "shio",
            "render");

    /** {@code model.put("post", …)} — a frame being written. */
    private static final Path RENDER_SERVICE = RENDER_SOURCES.resolve("ShPageRenderService.java");
    private static final Path RENDER_MODEL = RENDER_SOURCES.resolve("ShRenderModel.java");
    private static final Pattern FRAME_WRITE = Pattern.compile("model\\.put\\(\"(\\w+)\"");

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("SH292's test cannot read " + path.toAbsolutePath(), e);
        }
    }

    private static int budget(String key) {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(BUDGETS)) {
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String value = properties.getProperty(key);
        assertThat(value).as("token-budgets.properties must declare %s", key).isNotNull();
        return Integer.parseInt(value.trim());
    }

    @Test
    @DisplayName("the row keys and the reserved frames are named, not restated")
    void theRowKeysAndFramesAreNamed() {
        String text = String.join("\n", ShRenderConventions.lines(SIGNATURES));

        assertThat(ShRenderHelpers.ROW_KEYS).allSatisfy(key -> assertThat(text)
                .as("""
                        SH292: a row key the pack does not name is a field an author will not know \
                        they have — and inside a listing block SH251's lint only *warns*, so nothing \
                        else will tell them either. ROW_KEYS is the source (SH260); the text reads it.""")
                .contains(key));
        assertThat(ShRenderModel.FRAMES).allSatisfy(frame -> assertThat(text)
                .as("a reserved frame the pack does not name is a name an author may use for a "
                        + "field and lose silently")
                .contains(frame));
        assertThat(text)
                .as("the scoping rule is the point of the second line: it is the fact the write-time "
                        + "lint cannot enforce inside a block, so it has to arrive before the write")
                .contains("bare {{FIELD}} is the page's own field")
                .contains("it is the listed post's");
    }

    @Test
    @DisplayName("FRAMES matches what the renderer actually puts, both ways")
    void framesMatchesWhatTheRendererPuts() {
        Set<String> written = new TreeSet<>();
        Stream.of(RENDER_MODEL, RENDER_SERVICE).forEach(file -> {
            Matcher matcher = FRAME_WRITE.matcher(read(file));
            while (matcher.find()) {
                written.add(matcher.group(1));
            }
        });

        assertThat(written)
                .as("""
                        SH292: three files put these frames and a fourth (ShTemplateFieldLint) used to \
                        hold a private copy to filter them - a set nobody owned. Now ShRenderModel \
                        declares it and the others read it, so this asserts the declaration against \
                        the writers in both directions: a frame added without declaring it, or \
                        declared without being written, fails here.

                        Fix: update ShRenderModel.FRAMES, or the writer.""")
                .isEqualTo(new TreeSet<>(ShRenderModel.FRAMES));
    }

    @Test
    @DisplayName("the render conventions stay inside the ceiling this task gave them")
    void theRenderConventionsStayInsideTheirCeiling() {
        List<String> lines = ShRenderConventions.lines(SIGNATURES);
        int helpers = ShTokenBudget.estimate(lines.get(0));
        int model = ShTokenBudget.estimate(lines.get(1));
        int total = ShTokenBudget.estimate(String.join(" ", lines));

        assertThat(total)
                .as("""
                        P3 is a budget and not a preference, and until SH292 this section had no \
                        ceiling at all: the response budget test measures the pack with \
                        include=instance,sites,sitemap, so the conventions - the only part of the \
                        pack that carries sentences - were unmeasured. Measured now: helpers %d \
                        tokens, model %d, total %d.

                        Raising response.context.conventions.render is allowed and is a deliberate, \
                        reviewable act - say why in the commit message. Adding a paragraph and \
                        raising it silently is the mechanism defeated.""", helpers, model, total)
                .isLessThanOrEqualTo(budget("response.context.conventions.render"));
    }
}
