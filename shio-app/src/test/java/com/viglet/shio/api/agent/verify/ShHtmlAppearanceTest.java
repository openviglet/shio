/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH686 — the selector the reader could not spell.
 *
 * <h2>The measurement</h2>
 *
 * <p>On the {@code www.viglet.org} replica {@code appearance} reported <b>205 used, 1002 defined,
 * 42 unstyled</b>, and {@code verify} turned that into {@code page-unstyled}: <i>"draws with 42
 * class name(s) its own stylesheet does not define, and it loads no other."</i> Every one of the 42
 * is defined in the stored {@code theme/captured} CSS (1,061,479 chars), and <b>all 42 carry a
 * {@code /} or a {@code :}</b> — the trait is the whole population, not a sample.</p>
 *
 * <p>CSS requires those characters to be escaped in an identifier, so Tailwind emits
 * {@code .bg-background\/80}. The collector's identifier pattern stopped at the first character
 * outside {@code [_a-zA-Z0-9-]}, and a backslash is outside it, so it collected
 * {@code bg-background} — while the {@code class} attribute carries {@code bg-background/80}. The
 * two halves were comparing different languages and could never agree.</p>
 *
 * <h2>Why a unit test and not a render test</h2>
 *
 * <p>{@code ShPageRenderTest} already owns "does {@code page-unstyled} fire", and it passed
 * throughout — its fixtures are hand-written CSS with no escapes, which is the one shape the defect
 * does not touch. The property that broke is a string-to-string one about a grammar, and it belongs
 * where it can be stated in a line: given this stylesheet and this markup, is the class defined.</p>
 *
 * @author Alexandre Oliveira
 */
class ShHtmlAppearanceTest {

    private static final String PAGE =
            "<html><head><style>%s</style></head><body class=\"%s\">x</body></html>";

    private static ShHtmlAppearance.Appearance read(String css, String classes) {
        // `of(html)` reads the page's own <style> blocks, which is the path a rendered page takes.
        return ShHtmlAppearance.of(String.format(PAGE, css, classes));
    }

    @Test
    @DisplayName("SH686: a Tailwind class escaped with a slash resolves against its own stylesheet")
    void anEscapedSlashResolves() {
        var appearance = read(".bg-background\\/80{opacity:.8}", "bg-background/80");

        assertThat(appearance.unstyledClasses())
                .as("""
                        `.bg-background\\/80` IS the definition of `bg-background/80` — CSS requires \
                        the slash to be escaped in an identifier, and the markup carries the \
                        unescaped name. Reporting it unstyled accuses the half of the page that is \
                        fine (SH686).""")
                .isEmpty();
        assertThat(appearance.definedClasses()).isEqualTo(1);
    }

    @Test
    @DisplayName("SH686: and one escaped with a colon, which is the variant form")
    void anEscapedColonResolves() {
        var appearance = read(".dark\\:text-slate-400{color:#94a3b8}"
                + ".disabled\\:opacity-50{opacity:.5}", "dark:text-slate-400 disabled:opacity-50");

        assertThat(appearance.unstyledClasses()).isEmpty();
    }

    @Test
    @DisplayName("SH686: a hex escape consumes its whitespace terminator, and no more")
    void aHexEscapeIsDecoded() {
        // `\3a ` is the colon. The trailing space is a terminator the grammar requires — it is how
        // `\31 23` means "1" then "23" rather than a six-digit code point — so it is consumed and
        // does not become a space inside a class name that has none.
        var appearance = read(".md\\3a flex{display:flex}", "md:flex");

        assertThat(appearance.unstyledClasses())
                .as("the terminator is part of the escape, not part of the name")
                .isEmpty();
    }

    @Test
    @DisplayName("SH686: a class the sheet really does not define is still reported")
    void anUndefinedClassIsStillFound() {
        // Guards the guard. A fix that made every class resolve would pass all three tests above and
        // delete the check — the failure mode of a false-positive fix is a false negative.
        var appearance = read(".bg-background\\/80{opacity:.8}", "bg-background/80 genuinely-absent");

        assertThat(appearance.unstyledClasses())
                .as("the escaped one resolves and the missing one does not: the rule still works")
                .containsExactly("genuinely-absent");
    }

    @Test
    @DisplayName("SH686: the escaped name does not resolve the unescaped one, or vice versa")
    void theTwoSpellingsAreNotInterchangeable() {
        // The cheap fix would have been to strip backslashes from the *markup* side too, or to
        // compare a normalized form. Both make `bg-background` and `bg-background/80` the same
        // class, and a sheet defining only the second would then "define" the first.
        var appearance = read(".bg-background\\/80{opacity:.8}", "bg-background");

        assertThat(appearance.unstyledClasses())
                .as("`bg-background` is a different class from `bg-background/80` and this sheet "
                        + "defines only the latter")
                .containsExactly("bg-background");
    }

    @Test
    @DisplayName("SH686: captured CSS cannot crash the reader, however long a run of escapes is")
    void aLongRunOfEscapesDoesNotOverflowTheStack() {
        // Found by measuring the fix rather than by reading it, and it is the reason this collector
        // is a scan and not a regex. The obvious repair — letting the pattern's identifier accept
        // escapes, `(?:[_a-zA-Z0-9-]|\\…)*` — works and adds a StackOverflowError reachable from
        // *captured* CSS, which is content from somebody else's website. Measured over `.a` and
        // 40,000 backslashes: old regex 1.5 ms (it never enters a loop), escape-aware regex STACK
        // OVERFLOW, this scan 3.9 ms. java.util.regex compiles `[class]*` to a non-recursive loop
        // and `(?:A|B)*` to a recursive one, so the repair would have traded a false positive for a
        // crash.
        String css = ".a" + "\\".repeat(40_000) + "!{color:red}";

        var appearance = read(css, "no-such-class");

        assertThat(appearance.unstyledClasses())
                .as("it terminates, and the pathological selector still yields a name")
                .containsExactly("no-such-class");
    }

    @Test
    @DisplayName("with no stylesheet at all the list stays empty — every class would be 'undefined'")
    void noStylesheetMeansNoClaim() {
        // The pre-existing rule, asserted here because the new pattern must not have changed it: a
        // true statement that tells the reader nothing is not a finding.
        String html = "<html><body class=\"a b c\">x</body></html>";

        assertThat(ShHtmlAppearance.of(html, null).unstyledClasses()).isEmpty();
    }

    // --- SH851: the other half of the same question, on the half of the vocabulary that is used ---

    @Test
    @DisplayName("SH851: a var() the stylesheet never declares is reported, with how often it is read")
    void aReferenceWithNoDeclarationIsCounted() {
        // The measurement this closes: SH850 took a replica from 231 dangling references to 958 and
        // `verify` answered `0 error(s), 2 warning(s)` on both rounds. `appearance` counted class
        // names — 205 of them — on a page making 3,005 `var()` calls.
        var appearance = read(":root{--kept:#111}"
                + ".a{color:var(--gone)}.b{background:var(--gone)}.c{border-color:var(--kept)}",
                "a");

        assertThat(appearance.danglingTokens())
                .as("declared set minus referenced set, off the CSS the page already carries — no "
                        + "rendering and no browser")
                .containsExactly("--gone");
        assertThat(appearance.danglingTokenRefs())
                .as("62 names becoming 194 and 231 reads becoming 958 are different sentences, and "
                        + "the second is the one that says how much of the page went out")
                .isEqualTo(2);
        assertThat(appearance.definedTokens())
                .as("the denominator, for `definedClasses`' reason: an empty list over a stylesheet "
                        + "that declares nothing is not a clean page")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("SH851: a fallback is a declaration the author made at the call site")
    void aReferenceWithAFallbackIsNotDangling() {
        var appearance = read(".a{padding:var(--space, 1rem);color:var(--ink,#111)}", "a");

        assertThat(appearance.danglingTokens())
                .as("counting these would report every defensive stylesheet ever written as broken")
                .isEmpty();
    }

    @Test
    @DisplayName("SH851: a declaration is a declaration wherever it is read, and reads nest")
    void aDeclarationSatisfiesEveryReadOfIt() {
        var appearance = read(":root{--a:#111;--b:var(--a)}.x{color:var(--b)}.y{color:var(--a)}", "x");

        assertThat(appearance.danglingTokens())
                .as("`--b:var(--a)` declares one and reads the other, and both tests are "
                        + "independent — a name followed by `:` is a declaration, a name preceded "
                        + "by `var(` is a reference")
                .isEmpty();
        assertThat(appearance.definedTokens()).isEqualTo(2);
    }

    @Test
    @DisplayName("SH851: a commented-out reference is not a finding, which is the unsafe direction")
    void aCommentIsNotARead() {
        // `definedIn` does not strip comments and is right not to: over-collecting a DECLARATION
        // can only shorten a finding list. Over-collecting a REFERENCE invents one, so this scan
        // errs the other way — the two directions are opposite because the questions are.
        var appearance = read("/* .a{color:var(--never)} */ .a{color:#111}", "a");

        assertThat(appearance.danglingTokens()).isEmpty();
    }

    @Test
    @DisplayName("SH851: with no stylesheet there is nothing to compare, exactly as for classes")
    void noStylesheetMeansNoTokenClaim() {
        assertThat(ShHtmlAppearance.of("<html><body>x</body></html>", null).danglingTokens())
                .isEmpty();
        assertThat(ShHtmlAppearance.of("<html><body>x</body></html>", null).definedTokens())
                .isZero();
    }

    @Test
    @DisplayName("SH851: the scan terminates on the pathological input the class scan was measured on")
    void theTokenScanTerminatesOnEscapes() {
        // SH686's finding, inherited rather than re-learned: `java.util.regex` compiles `(?:A|B)*`
        // to a recursive loop, so an escape-aware pattern over captured CSS is a StackOverflowError
        // waiting for somebody else's website. This reuses the same scan, so it inherits the same
        // answer — asserted, because "it reuses it" is a claim about code and this is about bytes.
        String css = ":root{--a" + "\\".repeat(40_000) + "!:#111}.x{color:var(--gone)}";

        var appearance = read(css, "x");

        assertThat(appearance.danglingTokens()).containsExactly("--gone");
    }
}
