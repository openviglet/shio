/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.viglet.shio.render.ShTemplateEngine.ShHelperShape;

/**
 * SH251 — which field names a template interpolates, and <b>whose</b> they are.
 *
 * <h2>Why the scope matters more than the names</h2>
 *
 * <p>SH192 refuses a template that will not compile and could not answer the question §XV.10
 * cared about most: {@code {{headline}}} against a post-type that renamed the field to
 * {@code title} compiles, renders empty, and looks exactly like missing content. Catching that
 * needs to know which post-type a given interpolation belongs to — and a template has two
 * kinds of interpolation, with different owners:</p>
 *
 * <pre>{@code
 * {{TEXT}}                              <- the page's own field
 * {{#query type="Article"}}{{TEXT}}{{/query}}   <- the listed post's field
 * }</pre>
 *
 * <p>The first is checkable: the page's post-type is known, so an undeclared name is a defect.
 * The second is not, because the rows come from whatever posts the helper found. That is the
 * whole reason this returns a <em>scope</em> beside each name rather than a flat list — and the
 * reason {@code Template#collect(TagType.VAR)}, which Handlebars.java does offer, is not enough:
 * it returns every variable in the template with no indication of which block it sits in.</p>
 *
 * <h2>Why it is textual</h2>
 *
 * <p>The same reason {@code ShTemplateValidator}'s missing-helper scan is: Handlebars.java's AST
 * is internal, and its public surface does not distinguish a helper call from a section over a
 * context value. A brace scanner that tracks block depth is small, has no dependency on an
 * internal API, and is exact about the one thing it reports — the name and the nesting.</p>
 *
 * <h2>What it deliberately does not report</h2>
 *
 * <p>Anything that is not a plain path: a subexpression, a literal, a data variable
 * ({@code @index}, {@code @first}), {@code this}, a partial, or a comment. Each is either not a
 * field reference at all or is a construct whose resolution this cannot model — and a rule with
 * false positives is one authors learn to ignore (SH192's finding, applied again).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShTemplateFieldScanner {

    /**
     * Handlebars' own block helpers. Their first positional argument is a path resolved in the
     * <em>enclosing</em> scope, which is why it is reported there and not inside the block.
     */
    private static final Set<String> BUILTIN_BLOCKS = Set.of("if", "unless", "each", "with");

    /**
     * The helper shapes, <b>passed in</b> rather than restated (SH260).
     *
     * <p>This class used to keep two copies of the capability set — which helpers open blocks, and
     * which take a field as their argument — and both had to be edited in step with
     * {@code ShRenderHelpers.register()}. The failure mode was silent and backwards: a registered
     * helper missing from the list was reported as an unknown <em>field</em>, so the rule that
     * exists to catch typos would invent one. The engine now declares the shape with the helper and
     * the caller hands the map over, so there is one source and no drift.</p>
     *
     * <p>It stays a <em>parameter</em> rather than an injected dependency because this class is
     * pure — text in, references out — which is what makes its own test cheap enough to assert
     * every false-positive case.</p>
     */
    public record ShHelperShapes(Set<String> blocks, Set<String> pathArguments) {

        /** Derived from what the engine has registered. */
        public static ShHelperShapes of(Map<String, ShHelperShape> registered) {
            Set<String> blocks = new java.util.TreeSet<>();
            Set<String> pathArguments = new java.util.TreeSet<>();
            for (Map.Entry<String, ShHelperShape> entry : registered.entrySet()) {
                if (entry.getValue().isBlock()) {
                    blocks.add(entry.getKey());
                }
                if (entry.getValue().takesPathArgument()) {
                    pathArguments.add(entry.getKey());
                }
            }
            // TreeSet-backed and not `Set.copyOf`, which SH136's lint flags: that copy's iteration
            // order comes from a per-JVM salt. Membership is all that is read today, and the next
            // caller to iterate one would inherit an unstable order with nothing to fail.
            return new ShHelperShapes(
                    java.util.Collections.unmodifiableSet(blocks),
                    java.util.Collections.unmodifiableSet(pathArguments));
        }

        /** Nothing registered: every name reads as a plain reference, which is the safe default. */
        public static ShHelperShapes none() {
            return new ShHelperShapes(Set.of(), Set.of());
        }
    }

    /** Names that are never a field: Handlebars' own, plus the reserved model frames. */
    private static final Set<String> NOT_A_FIELD =
            Set.of("this", "else", "log", "lookup", "if", "unless", "each", "with");

    private ShTemplateFieldScanner() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * One interpolation.
     *
     * @param name  the path as written, e.g. {@code TEXT} or {@code post.title}
     * @param scope the block it sits inside, or {@code null} at the top level — which is the
     *              only scope whose owner is known
     */
    public record ShTemplateReference(String name, String scope) {

        /** Whether this reference belongs to the page's own post-type. */
        public boolean isPageScoped() {
            return scope == null;
        }

        /** The first path segment, which is what a reserved frame is recognised by. */
        public String root() {
            int dot = name.indexOf('.');
            return dot < 0 ? name : name.substring(0, dot);
        }
    }

    /**
     * Every field reference in {@code template}, in document order.
     *
     * <p>There is deliberately <b>no overload that omits {@code helpers}</b>. A convenience form
     * defaulting to "nothing is registered" would make a forgotten argument report every helper's
     * own name as an undeclared field — a false positive produced by the rule that exists to catch
     * typos, which is the worst possible failure for it to have. Callers with no engine pass
     * {@link ShHelperShapes#none()} and say so.</p>
     *
     * @param template a {@code PageLayout}'s or {@code Region}'s HTML
     * @param helpers  what the engine has registered, so a helper's own name is not read as a field
     */
    public static List<ShTemplateReference> referencesIn(String template, ShHelperShapes helpers) {
        List<ShTemplateReference> found = new ArrayList<>();
        if (template == null || template.isBlank()) {
            return found;
        }
        Deque<String> blocks = new ArrayDeque<>();
        int at = 0;
        while (true) {
            int open = template.indexOf("{{", at);
            if (open < 0) {
                break;
            }
            int close = template.indexOf("}}", open + 2);
            if (close < 0) {
                // An unterminated mustache is SH192's business — it will not compile — and
                // guessing at its contents here would report a name nobody wrote.
                break;
            }
            String body = template.substring(open + 2, close).trim();
            at = close + 2;
            // A triple stash leaves a leading `{` on the body and a trailing `}` before the
            // closer; both are stripped rather than parsed, since they change escaping only.
            body = strip(body);
            if (body.isEmpty()) {
                continue;
            }
            char kind = body.charAt(0);
            // No `&` case, and it would be unreachable if there were one: `strip` above has already
            // removed it, because `{{& TEXT}}` is Handlebars' unescaped-output form — the same
            // reference as `{{{TEXT}}}`. It used to be skipped here, which dropped a real field
            // silently, and the fix is in `strip` rather than a deletion here. Found by enumerating
            // the grammar for SH273, not by anything failing.
            if (kind == '!' || kind == '>' || kind == '=') {
                continue;
            }
            if (kind == '/') {
                blocks.pollFirst();
                continue;
            }
            if (kind == '#' || kind == '^') {
                String name = firstToken(body.substring(1));
                // The block's own argument is resolved where the block is written, not inside
                // it: `{{#if TEXT}}` asks about the current scope's TEXT.
                if (BUILTIN_BLOCKS.contains(name) || helpers.pathArguments().contains(name)) {
                    addIfPath(found, argumentOf(body.substring(1), name), blocks.peekFirst());
                }
                // SH261 — and its hash arguments, in the same enclosing scope for the same reason.
                addHashPaths(found, body.substring(1), blocks.peekFirst());
                blocks.addFirst(name);
                continue;
            }
            String token = firstToken(body);
            // SH261 — an inline helper may take hash arguments too (`{{file_url HERO alt=TITLE}}`),
            // and they resolve in the scope the call is written in, which is the current one.
            addHashPaths(found, body, blocks.peekFirst());
            if (helpers.pathArguments().contains(token) || helpers.blocks().contains(token)) {
                // The helper's name is not a field; its argument is. `{{file_url HERO}}` asks
                // about HERO, and reporting `file_url` would be a false positive on the very
                // helper the 2018 navigation region used.
                addIfPath(found, argumentOf(body, token), blocks.peekFirst());
                continue;
            }
            addIfPath(found, token, blocks.peekFirst());
        }
        return List.copyOf(found);
    }

    /**
     * The hash values that are field paths rather than literals (SH261).
     *
     * <h3>The blind spot this closes</h3>
     *
     * <p>{@code {{#search q=TOPIC}}} and {@code {{#query folder=SECTION}}} produced no reference at
     * all, because a {@code name=value} pair is not a positional argument and only the positional
     * one was read. That was right for {@code folder="/blog"} — a quoted literal is not a field —
     * and wrong for the <em>model-driven</em> form, which is the one SH247 shipped {@code search}
     * for: "a term the template supplies, literal <b>or read from the model</b>". A page whose
     * {@code TOPIC} field drives its own related-content listing is the case that makes the helper
     * worth having, and a typo there renders an empty listing — precisely the failure SH251 exists
     * to catch, so the rule was blind exactly where its subject is most likely to be wrong.</p>
     *
     * <h3>Only the unambiguous case</h3>
     *
     * <p>A quoted value is a literal; a value starting with {@code (} is a subexpression whose
     * resolution this deliberately does not parse; a numeric value is a number. What is left — an
     * unquoted token made entirely of path characters — is a path, and nothing else looks like one.
     * That is the same "weak claim, always true" bar the in-block warning uses, and it is why this
     * needs no cleverness about nesting: a subexpression is skipped whole rather than descended
     * into.</p>
     */
    private static void addHashPaths(List<ShTemplateReference> found, String body, String scope) {
        for (String token : tokens(body)) {
            int equals = token.indexOf('=');
            if (equals <= 0 || equals == token.length() - 1) {
                continue;
            }
            addIfPath(found, token.substring(equals + 1), scope);
        }
    }

    /**
     * Whitespace-separated tokens, with quotes and parentheses kept whole.
     *
     * <p>Splitting on whitespace alone would cut {@code q="two words"} in half and produce
     * {@code q="two} — whose value is not a path, so it would be dropped, which is the *right*
     * answer reached for the wrong reason and stops being right the day somebody writes
     * {@code q=(concat A B)}. Tracking the two nestings costs six lines and removes the class.</p>
     */
    private static List<String> tokens(String body) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        int depth = 0;
        for (char character : body.trim().toCharArray()) {
            if (quote != 0) {
                current.append(character);
                if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '"' || character == '\'') {
                quote = character;
                current.append(character);
                continue;
            }
            if (character == '(') {
                depth++;
            } else if (character == ')' && depth > 0) {
                depth--;
            }
            if (Character.isWhitespace(character) && depth == 0) {
                if (current.length() > 0) {
                    out.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(character);
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
        return out;
    }

    private static void addIfPath(List<ShTemplateReference> found, String token, String scope) {
        if (isPath(token)) {
            found.add(new ShTemplateReference(token, scope));
        }
    }

    /**
     * Whether a token is a plain path this can reason about.
     *
     * <p>Excluded: the empty token, a data variable, {@code this} and its {@code ./} forms, a
     * parent reference ({@code ../}), a subexpression, a quoted literal, a number, and anything
     * with a character a path cannot contain. Every one of those is either not a field or is
     * resolved somewhere this cannot see.</p>
     */
    private static boolean isPath(String token) {
        if (token == null || token.isEmpty() || NOT_A_FIELD.contains(token)) {
            return false;
        }
        char first = token.charAt(0);
        if (first == '@' || first == '.' || first == '"' || first == '\'' || first == '('
                || Character.isDigit(first)) {
            return false;
        }
        // A path segment may hold letters, digits, `_`, `-` and `.`; a `=` means this was a hash
        // pair and not a positional argument.
        return token.chars().allMatch(c -> Character.isLetterOrDigit(c)
                || c == '_' || c == '-' || c == '.');
    }

    /** The first whitespace-delimited token. */
    private static String firstToken(String body) {
        String trimmed = body.trim();
        int space = indexOfWhitespace(trimmed);
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }

    /**
     * The first positional argument after a block's name, or {@code ""} when it has none.
     *
     * <p>A hash pair ({@code folder="/blog"}) is not positional, so a block whose first token
     * contains {@code =} is reported as having no path argument — which is correct for
     * {@code {{#query folder="/blog"}}} and is why the hash form is left alone entirely.</p>
     */
    private static String argumentOf(String body, String name) {
        String rest = body.trim().substring(name.length()).trim();
        return rest.isEmpty() ? "" : firstToken(rest);
    }

    private static int indexOfWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Removes the punctuation that changes escaping or whitespace but never resolution: a triple
     * stash's inner braces, and Handlebars' {@code ~} whitespace-control markers.
     *
     * <p>The {@code ~} pair is easy to miss and the failure is silent in the wrong direction —
     * {@code {{~TEXT~}}} left as the token {@code ~TEXT~} is not a valid path, so the reference is
     * dropped and a real defect goes unreported. Found by the scanner's own test.</p>
     */
    private static String strip(String body) {
        String out = body;
        // `{` a triple stash, `~` whitespace control, `&` unescaped output. All three change how the
        // value is emitted and none changes what it resolves to, so all three come off.
        while (out.startsWith("{") || out.startsWith("~") || out.startsWith("&")) {
            out = out.substring(1).trim();
        }
        while (out.endsWith("}") || out.endsWith("~")) {
            out = out.substring(0, out.length() - 1).trim();
        }
        return out.trim();
    }
}
