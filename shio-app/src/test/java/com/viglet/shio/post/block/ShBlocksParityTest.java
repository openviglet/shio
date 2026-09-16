/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.post.block;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1015 — the Java projection and the CLI's write the same Markdown.
 *
 * <h2>Why the rule is implemented twice at all</h2>
 *
 * <p>{@code ShBlocks.markdown} is the reference and {@code cli/src/blocks.mjs} is what
 * {@code shio pull} runs. Asking the instance to project was the alternative and it is worse: a
 * projection is what {@code pull} writes to disk, and a pull that needs a round trip to the server
 * to format a file is not the files tier P4 describes.</p>
 *
 * <h2>So the duplication is made safe rather than argued about</h2>
 *
 * <p>Both sides read <b>this same fixture file</b> — {@code cli/test/fixtures/blocks-parity.json} —
 * and this asserts the Java output. {@code blocks-projection.test.mjs} asserts the round trip on the
 * JS side over the same corpus. Two copies of a rule drift silently; two copies pinned to one corpus
 * fail the build on the first disagreement, which is the only version of this that is safe to
 * have.</p>
 *
 * <p>It reads a file outside this module on purpose. A fixture copied here would be a third copy of
 * the thing being kept in step, and the first to go stale.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShBlocksParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The corpus both implementations are held to. */
    private static JsonNode fixtures() throws IOException {
        Path path = Path.of("..", "cli", "test", "fixtures", "blocks-parity.json");
        assertThat(Files.exists(path))
                .as("SH1015: the shared fixture is gone, so this test is comparing nothing — it "
                        + "lives in cli/test/fixtures/blocks-parity.json and both sides read it")
                .isTrue();
        return MAPPER.readTree(Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("SH1015: every fixture projects to the Markdown the CLI's projection produces")
    void bothProjectionsAgree() throws IOException {
        JsonNode fixtures = fixtures();
        assertThat(fixtures.size())
                .as("SH1015: the corpus is the whole of the evidence for this parity, so a "
                        + "shrunken one is a weaker claim wearing the same name")
                .isGreaterThanOrEqualTo(4);

        // The expectations are written here rather than generated, because a test that computed
        // both sides from one implementation would pass whatever that implementation did.
        List<String> expected = List.of(
                """
                ## Why

                Because.

                - one
                - two

                1. first
                2. second""",
                """
                **one** two *three*

                call `run` now""",
                """
                ```js
                x()
                ```

                ![A chart](/a.png)

                > Said once.

                <!-- embed https://example.com/v -->

                <!-- reference post:Acme/about -->""",
                "");

        List<String> actual = new ArrayList<>();
        for (JsonNode fixture : fixtures) {
            actual.add(ShBlocks.markdown(fixture.get("blocks")));
        }

        for (int index = 0; index < expected.size(); index++) {
            assertThat(actual.get(index))
                    .as("SH1015: fixture '%s' — the Java projection and the CLI's must write the "
                            + "same bytes, or `shio pull` and the instance disagree about what a "
                            + "document looks like and every pull reports a change nobody made",
                            fixtures.get(index).path("name").asString(""))
                    .isEqualTo(expected.get(index));
        }
    }

    @Test
    @DisplayName("SH1015: the fixture corpus covers every registered block type")
    void theCorpusIsNotPartial() throws IOException {
        List<String> seen = new ArrayList<>();
        for (JsonNode fixture : fixtures()) {
            for (JsonNode block : fixture.path("blocks")) {
                String type = block.path("type").asString("");
                if (!type.isEmpty() && !seen.contains(type)) {
                    seen.add(type);
                }
            }
        }

        assertThat(seen)
                .as("""
                        SH1015: a parity corpus that covers six of eight types keeps two \
                        implementations in step for six of them and says nothing about the other \
                        two — which is exactly where they would drift, since an unexercised branch \
                        is the one nobody notices changing.""")
                .containsExactlyInAnyOrderElementsOf(ShBlocks.TYPES);
    }
}
