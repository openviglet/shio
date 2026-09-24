/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.post.block;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH1015 — a page body an agent can address a paragraph of.
 *
 * <h2>The defect this removes</h2>
 *
 * <p>A rich-text value is {@code innerHTML}: one string with no schema. P4 promises that
 * {@code Edit} and {@code Grep} are the agent's cheapest tools, and it lands them inside a single
 * unstructured blob where a paragraph has no address, an edit is a substring match, and two saves
 * of the same document can differ in whitespace no one typed. Four laws argue for structure and
 * this is the shape that satisfies them:</p>
 *
 * <ul>
 *   <li><b>P4</b> — blocks project to Markdown with real headings, so an {@code Edit} targets a
 *       paragraph rather than a byte offset into a blob.</li>
 *   <li><b>P5</b> — {@link #canonical} is byte-identical for equal content. {@code innerHTML} is
 *       not: attribute order, whitespace and entity spelling all vary without the content doing.</li>
 *   <li><b>P9</b> — a heading is a field of a block, so an outline can be <em>read</em> rather than
 *       parsed out of markup. The digest still parses the rendered page, which is the right
 *       subject for it; what changes is that the document itself can now answer the question.</li>
 *   <li><b>P6</b> — an unknown block type is refused with the registered ones listed.</li>
 * </ul>
 *
 * <h2>Marks are ranges, not nesting</h2>
 *
 * <p>Inline emphasis is {@code {from, to, type}} over the block's own text, not a tree of spans.
 * Nesting is what makes {@code innerHTML} uncanonical — {@code <b><i>x</i></b>} and
 * {@code <i><b>x</b></i>} are the same content and different bytes — and ranges have exactly one
 * spelling. They also survive a text edit as an offset problem rather than as a re-parse.</p>
 *
 * <h2>Every block carries a key</h2>
 *
 * <p>Stable across a reorder, which is what lets an edit name a block rather than a position, and
 * what an editor needs to tell two paragraphs apart. It is the block's identity and not its order:
 * order is the array's, as it is for a relator row (SH1012).</p>
 */
public final class ShBlocks {

    /** The registered kinds. An unknown one is refused with this list, which is P6's whole shape. */
    public static final List<String> TYPES = List.of(
            "heading", "paragraph", "list", "quote", "code", "image", "embed", "reference");

    /** The inline marks a paragraph's text may carry. */
    public static final List<String> MARKS = List.of("strong", "em", "code", "link", "strike");

    private static final int MAX_HEADING_LEVEL = 6;

    private ShBlocks() {
    }

    /**
     * Validates a blocks value, or refuses it with a message that says what to write instead.
     *
     * @param field the field name, so a refusal names the box rather than the document
     * @param value the stored or proposed value
     * @return the same node, validated
     */
    public static JsonNode require(String field, JsonNode value) {
        if (value == null || value.isNull()) {
            return JsonNodeFactory.instance.arrayNode();
        }
        if (!value.isArray()) {
            throw refusal(field, "A Blocks field holds an ARRAY of blocks, and this is "
                    + kindOf(value) + ".");
        }
        Set<String> keys = new LinkedHashSet<>();
        int index = 0;
        for (JsonNode block : value) {
            requireBlock(field, index, block, keys);
            index++;
        }
        return value;
    }

    private static void requireBlock(String field, int index, JsonNode block, Set<String> keys) {
        String at = field + "." + index;
        if (!block.isObject()) {
            throw refusal(at, "Every block is an object, and this one is " + kindOf(block) + ".");
        }
        String type = text(block, "type");
        if (type == null || type.isBlank()) {
            throw refusal(at, "Every block names a `type`.");
        }
        if (!TYPES.contains(type)) {
            throw refusal(at, "`" + type + "` is not a block type.");
        }
        String key = text(block, "key");
        if (key == null || key.isBlank()) {
            throw refusal(at, "Every block carries a `key`, which is its identity across a reorder.");
        }
        if (!keys.add(key)) {
            // Two blocks with one key is the defect an editor would surface as a paragraph that
            // will not move, long after the write that caused it.
            throw refusal(at, "Two blocks share the key `" + key + "`.");
        }
        if ("heading".equals(type)) {
            int level = block.path("level").asInt(0);
            if (level < 1 || level > MAX_HEADING_LEVEL) {
                throw refusal(at, "A heading's `level` is 1 to " + MAX_HEADING_LEVEL
                        + ", and this is " + (level == 0 ? "absent" : String.valueOf(level)) + ".");
            }
        }
        requireMarks(at, block);
    }

    private static void requireMarks(String at, JsonNode block) {
        JsonNode marks = block.get("marks");
        if (marks == null || marks.isNull()) {
            return;
        }
        if (!marks.isArray()) {
            throw refusal(at, "`marks` is an array of {from, to, type}.");
        }
        int length = text(block, "text") == null ? 0 : text(block, "text").length();
        for (JsonNode mark : marks) {
            String type = text(mark, "type");
            if (type == null || !MARKS.contains(type)) {
                throw refusal(at, "`" + type + "` is not a mark. The marks are: "
                        + String.join(", ", MARKS) + ".");
            }
            int from = mark.path("from").asInt(-1);
            int to = mark.path("to").asInt(-1);
            // Bounded against the text, because a mark that runs off the end is the one defect
            // that renders as a silently truncated emphasis rather than as an error.
            if (from < 0 || to < from || to > length) {
                throw refusal(at, "A mark covers `from`..`to` within the block's own text, and "
                        + from + ".." + to + " is outside 0.." + length + ".");
            }
        }
    }

    /**
     * The document in its one spelling (P5).
     *
     * <p>Keys sorted inside each object, blocks left in the order the document puts them — order is
     * content here, exactly as it is for a list. Two equal documents serialize to equal bytes, so
     * {@code shio pull} can decide whether a file changed without rendering anything.</p>
     */
    public static JsonNode canonical(JsonNode value) {
        if (value == null || !value.isArray()) {
            return JsonNodeFactory.instance.arrayNode();
        }
        ArrayNode out = JsonNodeFactory.instance.arrayNode();
        for (JsonNode block : value) {
            out.add(canonicalNode(block));
        }
        return out;
    }

    private static JsonNode canonicalNode(JsonNode node) {
        if (node.isArray()) {
            ArrayNode out = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : node) {
                out.add(canonicalNode(item));
            }
            return out;
        }
        if (!node.isObject()) {
            return node;
        }
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        names.sort(String::compareTo);
        for (String name : names) {
            out.set(name, canonicalNode(node.get(name)));
        }
        return out;
    }

    /**
     * The blocks as Markdown — the projection that makes {@code Edit} and {@code Grep} work (P4).
     *
     * <p>A block nothing knows how to write is emitted as its own text rather than skipped: a
     * projection that silently drops content is how an author's work disappears through a
     * round trip, which is the one failure this whole model exists to make impossible.</p>
     */
    public static String markdown(JsonNode value) {
        if (value == null || !value.isArray()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (JsonNode block : value) {
            String written = markdownBlock(block);
            if (!written.isEmpty()) {
                out.append(written).append("\n\n");
            }
        }
        return out.toString().stripTrailing();
    }

    private static String markdownBlock(JsonNode block) {
        String type = text(block, "type");
        String body = marked(block);
        if (type == null) {
            return body == null ? "" : body;
        }
        return switch (type) {
            case "heading" -> "#".repeat(Math.max(1, block.path("level").asInt(1)))
                    + " " + nullToEmpty(body);
            case "quote" -> "> " + nullToEmpty(body).replace("\n", "\n> ");
            case "code" -> "```" + nullToEmpty(text(block, "language")) + "\n"
                    + nullToEmpty(text(block, "text")) + "\n```";
            case "image" -> "![" + nullToEmpty(text(block, "alt")) + "]("
                    + nullToEmpty(text(block, "src")) + ")";
            case "embed" -> "<!-- embed " + nullToEmpty(text(block, "url")) + " -->";
            case "reference" -> "<!-- reference " + nullToEmpty(text(block, "address")) + " -->";
            case "list" -> list(block);
            default -> nullToEmpty(body);
        };
    }

    private static String list(JsonNode block) {
        boolean ordered = block.path("ordered").asBoolean(false);
        StringBuilder out = new StringBuilder();
        int number = 1;
        for (JsonNode item : block.path("items")) {
            out.append(ordered ? number + ". " : "- ").append(item.asString("")).append("\n");
            number++;
        }
        return out.toString().stripTrailing();
    }

    /**
     * The block's text with its marks applied.
     *
     * <p>Applied from the end backwards, so an earlier mark's offsets are still valid after a later
     * one has inserted its delimiters — the alternative is tracking a shifting offset, which is the
     * same bug written more expensively.</p>
     */
    private static String marked(JsonNode block) {
        String text = text(block, "text");
        if (text == null) {
            return null;
        }
        JsonNode marks = block.get("marks");
        if (marks == null || !marks.isArray() || marks.isEmpty()) {
            return text;
        }
        List<JsonNode> ordered = new ArrayList<>();
        marks.forEach(ordered::add);
        ordered.sort((a, b) -> Integer.compare(b.path("from").asInt(0), a.path("from").asInt(0)));

        StringBuilder out = new StringBuilder(text);
        for (JsonNode mark : ordered) {
            int from = mark.path("from").asInt(0);
            int to = mark.path("to").asInt(0);
            if (from < 0 || to > out.length() || to < from) {
                continue;
            }
            String delimiter = switch (nullToEmpty(text(mark, "type"))) {
                case "strong" -> "**";
                case "em" -> "*";
                case "code" -> "`";
                case "strike" -> "~~";
                default -> "";
            };
            if ("link".equals(text(mark, "type"))) {
                out.insert(to, "](" + nullToEmpty(text(mark, "href")) + ")").insert(from, "[");
            } else if (!delimiter.isEmpty()) {
                out.insert(to, delimiter).insert(from, delimiter);
            }
        }
        return out.toString();
    }

    /**
     * SH1016 — the blocks as HTML, so a front end that has always received a string still gets one.
     *
     * <h2>Beside the blocks, never instead of them</h2>
     *
     * <p>A headless site that maps block types to its own components wants the array; one that has
     * been rendering {@code post.attrs.BODY} into a div for two years wants markup. Delivering only
     * the second is the shape this model replaced, and delivering only the first breaks every
     * existing reader — so the CDA carries both and the front end picks.</p>
     *
     * <h2>Escaped, because a block's text is text</h2>
     *
     * <p>A paragraph is a string an author typed, not markup they wrote: the marks say where the
     * emphasis is. So the text is escaped and the tags come from the model, which means a
     * {@code <script>} an author pasted into a heading renders as the characters they typed. That
     * is the property {@code innerHTML} could not have, and it is why SH956's sanitizer is needed
     * for an HTML body and not for this one.</p>
     */
    public static String html(JsonNode value) {
        if (value == null || !value.isArray()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (JsonNode block : value) {
            out.append(htmlBlock(block));
        }
        return out.toString();
    }

    private static String htmlBlock(JsonNode block) {
        String type = text(block, "type");
        String body = markedHtml(block);
        if (type == null) {
            return body == null ? "" : "<p>" + body + "</p>";
        }
        return switch (type) {
            case "heading" -> {
                int level = Math.min(MAX_HEADING_LEVEL, Math.max(1, block.path("level").asInt(1)));
                yield "<h" + level + ">" + nullToEmpty(body) + "</h" + level + ">";
            }
            case "quote" -> "<blockquote>" + nullToEmpty(body) + "</blockquote>";
            case "code" -> "<pre><code>" + escape(nullToEmpty(text(block, "text")))
                    + "</code></pre>";
            case "image" -> "<img src=\"" + escape(nullToEmpty(text(block, "src")))
                    + "\" alt=\"" + escape(nullToEmpty(text(block, "alt"))) + "\">";
            // An embed and a reference are addresses, not markup: what to DO with them is the front
            // end's decision, and guessing an iframe here would make that decision for everyone.
            case "embed" -> "<!-- embed " + escape(nullToEmpty(text(block, "url"))) + " -->";
            case "reference" -> "<!-- reference " + escape(nullToEmpty(text(block, "address")))
                    + " -->";
            case "list" -> htmlList(block);
            default -> "<p>" + nullToEmpty(body) + "</p>";
        };
    }

    private static String htmlList(JsonNode block) {
        String tag = block.path("ordered").asBoolean(false) ? "ol" : "ul";
        StringBuilder out = new StringBuilder("<").append(tag).append(">");
        for (JsonNode item : block.path("items")) {
            out.append("<li>").append(escape(item.asString(""))).append("</li>");
        }
        return out.append("</").append(tag).append(">").toString();
    }

    /**
     * The block's text with its marks as tags, escaping each run of the author's own characters.
     *
     * <h2>Why not escape first, or last</h2>
     *
     * <p>Escaping the whole string <b>first</b> moves every offset the marks are expressed in — a
     * single {@code &} shifts everything after it by four. Escaping <b>last</b> cannot tell the
     * tags this method just inserted from an angle bracket the author typed, and a test caught
     * exactly that: {@code a < b} with a mark over the {@code a} left the author's {@code <}
     * unescaped, because the walk read it as the start of a tag and stopped escaping.</p>
     *
     * <p>So the tags are placed by <em>original</em> offset and the text between them is escaped as
     * it is emitted. Neither the offsets nor the author's characters ever pass through the other's
     * hands.</p>
     */
    private static String markedHtml(JsonNode block) {
        String text = text(block, "text");
        if (text == null) {
            return null;
        }
        JsonNode marks = block.get("marks");
        if (marks == null || !marks.isArray() || marks.isEmpty()) {
            return escape(text);
        }

        // position → the tags to emit there. Closes come before opens at a shared boundary, so two
        // adjacent marks nest as `</em><strong>` rather than crossing.
        List<int[]> points = new ArrayList<>();
        List<String> opens = new ArrayList<>();
        List<String> closes = new ArrayList<>();
        for (JsonNode mark : marks) {
            int from = mark.path("from").asInt(-1);
            int to = mark.path("to").asInt(-1);
            String type = nullToEmpty(text(mark, "type"));
            String open = switch (type) {
                case "strong" -> "<strong>";
                case "em" -> "<em>";
                case "code" -> "<code>";
                case "strike" -> "<s>";
                case "link" -> "<a href=\"" + escape(nullToEmpty(text(mark, "href"))) + "\">";
                default -> "";
            };
            if (open.isEmpty() || from < 0 || to > text.length() || to < from) {
                continue;
            }
            String close = "link".equals(type) ? "</a>"
                    : "</" + open.substring(1, open.indexOf('>')) + ">";
            points.add(new int[] { from, opens.size(), 1 });
            opens.add(open);
            points.add(new int[] { to, closes.size(), 0 });
            closes.add(close);
        }
        // By position; at the same position a close (0) precedes an open (1).
        points.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[2], b[2]));

        StringBuilder out = new StringBuilder();
        int cursor = 0;
        for (int[] point : points) {
            if (point[0] > cursor) {
                out.append(escape(text.substring(cursor, point[0])));
                cursor = point[0];
            }
            out.append(point[2] == 1 ? opens.get(point[1]) : closes.get(point[1]));
        }
        out.append(escape(text.substring(cursor)));
        return out.toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node == null ? null : node.get(name);
        return value == null || !value.isString() ? null : value.asString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String kindOf(JsonNode node) {
        if (node.isArray()) {
            return "an array";
        }
        if (node.isObject()) {
            return "an object";
        }
        return "a " + node.getNodeType().toString().toLowerCase(Locale.ROOT);
    }

    /** A blocks document this instance cannot store, and what to write instead (P6). */
    public static class ShInvalidBlocksException extends ShTeachingException {
        public ShInvalidBlocksException(ShProblem problem) {
            super(problem);
        }
    }

    private static ShTeachingException refusal(String at, String detail) {
        return new ShInvalidBlocksException(ShProblem.badRequest("blocks-invalid",
                "This Blocks value is not a document this instance can store.")
                .withDetail(at + ": " + detail)
                .withAllowed(TYPES)
                .withFix("A block is {\"key\":\"<stable id>\",\"type\":\"<one of allowed>\",…}. "
                        + "A heading adds `level`, a list `items` and `ordered`, a code block "
                        + "`language`, an image `src` and `alt`. Inline emphasis is `marks`: "
                        + "[{\"from\":0,\"to\":4,\"type\":\"strong\"}] over the block's own text.")
                .withExample("[{\"key\":\"b1\",\"type\":\"heading\",\"level\":2,\"text\":\"Why\"},"
                        + "{\"key\":\"b2\",\"type\":\"paragraph\",\"text\":\"Because.\"}]"));
    }
}
