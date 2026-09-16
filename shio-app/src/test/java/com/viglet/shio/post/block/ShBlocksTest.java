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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1015 — the four laws the block model exists to satisfy, each asserted rather than argued.
 *
 * <p>No Spring context: this is a pure function over JSON, and a model whose rules need an
 * application running to check is one nobody checks.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShBlocksTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String raw) {
        return MAPPER.readTree(raw);
    }

    // ── P5: one document, one spelling ──────────────────────────────────────────────

    @Test
    @DisplayName("SH1015: key order is not content, so two equal documents canonicalize equal")
    void theCanonicalFormIsByteIdenticalForEqualContent() {
        JsonNode one = ShBlocks.canonical(json(
                "[{\"key\":\"b1\",\"type\":\"paragraph\",\"text\":\"Hello\"}]"));
        JsonNode other = ShBlocks.canonical(json(
                "[{\"text\":\"Hello\",\"type\":\"paragraph\",\"key\":\"b1\"}]"));

        assertThat(one.toString())
                .as("""
                        SH1015: this is the whole P5 claim, and the reason a block array replaces \
                        innerHTML: the same content written twice produces the same bytes, so \
                        `shio pull` can decide whether a file changed without rendering anything. \
                        innerHTML cannot do this — attribute order, whitespace and entity spelling \
                        all vary while the content does not.""")
                .isEqualTo(other.toString());
    }

    @Test
    @DisplayName("SH1015: block order IS content, so it is never sorted")
    void theOrderOfBlocksIsNotNormalised() {
        JsonNode canonical = ShBlocks.canonical(json(
                "[{\"key\":\"z\",\"type\":\"paragraph\",\"text\":\"first\"},"
                        + "{\"key\":\"a\",\"type\":\"paragraph\",\"text\":\"second\"}]"));

        assertThat(canonical.get(0).path("key").asString())
                .as("SH1015: sorting blocks by key would reorder somebody's page, which is the one "
                        + "thing a canonical form must never do — the array's order is the document")
                .isEqualTo("z");
    }

    // ── P4: an Edit targets a paragraph ─────────────────────────────────────────────

    @Test
    @DisplayName("SH1015: the Markdown projection has real headings, lists and code fences")
    void blocksProjectToMarkdownAnEditCanTarget() {
        String markdown = ShBlocks.markdown(json("""
                [{"key":"b1","type":"heading","level":2,"text":"Why"},
                 {"key":"b2","type":"paragraph","text":"Because."},
                 {"key":"b3","type":"list","ordered":false,"items":["one","two"]},
                 {"key":"b4","type":"code","language":"js","text":"x()"},
                 {"key":"b5","type":"image","src":"/a.png","alt":"A chart"}]"""));

        assertThat(markdown)
                .as("SH1015: P4 says Edit and Grep are the agent's cheapest tools, and this is what "
                        + "gives them something to aim at — a heading is a line, not a substring "
                        + "of a blob")
                .contains("## Why")
                .contains("Because.")
                .contains("- one\n- two")
                .contains("```js\nx()\n```")
                .contains("![A chart](/a.png)");
    }

    @Test
    @DisplayName("SH1015: marks become delimiters, applied from the end so earlier offsets hold")
    void inlineMarksSurviveAsRanges() {
        String markdown = ShBlocks.markdown(json(
                "[{\"key\":\"b1\",\"type\":\"paragraph\",\"text\":\"one two three\","
                        + "\"marks\":[{\"from\":0,\"to\":3,\"type\":\"strong\"},"
                        + "{\"from\":8,\"to\":13,\"type\":\"em\"}]}]"));

        assertThat(markdown)
                .as("""
                        SH1015: applied back to front, so the first mark's offsets are still valid \
                        after the second has inserted its delimiters. Front to back needs a \
                        shifting offset, which is the same bug written more expensively.""")
                .isEqualTo("**one** two *three*");
    }

    @Test
    @DisplayName("SH1015: a block the projection does not know still writes its text")
    void anUnwrittenBlockIsNotSilentlyDropped() {
        // `reference` has a spelling; the guard is that nothing falls through to nothing.
        String markdown = ShBlocks.markdown(json(
                "[{\"key\":\"b1\",\"type\":\"reference\",\"address\":\"post:Acme/about\"}]"));

        assertThat(markdown)
                .as("SH1015: a projection that silently drops a block is how an author's work "
                        + "disappears through a round trip, which is what this model exists to "
                        + "make impossible")
                .contains("post:Acme/about");
    }

    // ── P6: a refusal says what to write instead ────────────────────────────────────

    @Test
    @DisplayName("SH1015: an unknown block type is refused with the registered ones listed")
    void anUnknownTypeIsATeachingRefusal() {
        assertThatThrownBy(() -> ShBlocks.require("body", json(
                "[{\"key\":\"b1\",\"type\":\"carousel\"}]")))
                .isInstanceOf(ShBlocks.ShInvalidBlocksException.class)
                .hasMessageContaining("carousel");

        assertThat(ShBlocks.TYPES)
                .as("SH1015: the list a refusal carries IS this one, so a caller that reads the "
                        + "error learns the vocabulary rather than guessing at it again")
                .contains("heading", "paragraph", "list", "quote", "code", "image", "embed",
                        "reference");
    }

    @Test
    @DisplayName("SH1015: two blocks cannot share a key, and a heading declares a level")
    void theIdentityAndShapeRulesAreRefusedAtTheWrite() {
        assertThatThrownBy(() -> ShBlocks.require("body", json(
                "[{\"key\":\"b1\",\"type\":\"paragraph\",\"text\":\"a\"},"
                        + "{\"key\":\"b1\",\"type\":\"paragraph\",\"text\":\"b\"}]")))
                .as("SH1015: a duplicate key surfaces in an editor as a paragraph that will not "
                        + "move, long after the write that caused it")
                .isInstanceOf(ShBlocks.ShInvalidBlocksException.class);

        assertThatThrownBy(() -> ShBlocks.require("body", json(
                "[{\"key\":\"b1\",\"type\":\"heading\",\"level\":9,\"text\":\"x\"}]")))
                .isInstanceOf(ShBlocks.ShInvalidBlocksException.class);

        assertThatThrownBy(() -> ShBlocks.require("body", json(
                "[{\"type\":\"paragraph\",\"text\":\"no key\"}]")))
                .isInstanceOf(ShBlocks.ShInvalidBlocksException.class);
    }

    @Test
    @DisplayName("SH1015: a mark that runs off the end of its text is refused, not truncated")
    void aMarkIsBoundedByTheTextItCovers() {
        assertThatThrownBy(() -> ShBlocks.require("body", json(
                "[{\"key\":\"b1\",\"type\":\"paragraph\",\"text\":\"short\","
                        + "\"marks\":[{\"from\":0,\"to\":99,\"type\":\"strong\"}]}]")))
                .as("SH1015: the alternative renders as a silently truncated emphasis, which "
                        + "nobody reports and nobody can find")
                .isInstanceOf(ShBlocks.ShInvalidBlocksException.class);
    }

    @Test
    @DisplayName("SH1015: an absent value is an empty document, not a refusal")
    void nothingIsAValidDocument() {
        assertThat(ShBlocks.require("body", null).isArray()).isTrue();
        assertThat(ShBlocks.require("body", json("[]")).isEmpty())
                .as("SH1015: a field nobody has filled in yet is every new post, and refusing it "
                        + "would make the widget unusable before it is used")
                .isTrue();
    }
}
