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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.render.ShRenderModel.ShThemeAssets;

/**
 * SH195 — design tokens compile to custom properties, and a broken one takes nothing with it.
 *
 * <p>No Spring context: this is a pure function from a JSON string to a stylesheet, and SH317
 * measured what a context costs (~1.5 s).</p>
 *
 * @author Alexandre Oliveira
 */
class ShThemeTokensTest {

    private static final String DTCG = """
            {
              "color": {
                "accent":  { "$value": "#0b5", "$type": "color" },
                "surface": { "$value": "#fff", "$type": "color" }
              },
              "space": { "gutter": { "$value": "1.5rem", "$type": "dimension" } }
            }""";

    @Test
    @DisplayName("a DTCG file becomes a :root block, in front of the theme's own CSS")
    void tokensCompileToCustomProperties() {
        ShThemeAssets assets = ShThemeTokens.assets(DTCG, "body{color:var(--color-accent)}", null);

        assertThat(assets.css())
                .as("""
                        the declarations come first, which is the order CSS needs and the reason \
                        they are prepended rather than offered as a separate {{theme.tokens.css}} \
                        handle — a handle would ship the feature switched off on every layout \
                        nobody edits""")
                .isEqualTo(":root{--color-accent:#0b5;--color-surface:#fff;--space-gutter:1.5rem;}"
                        + "\nbody{color:var(--color-accent)}");
    }

    @Test
    @DisplayName("and the same values are readable as a dotted map, for where a var() cannot go")
    void tokensAreAlsoAMap() {
        assertThat(ShThemeTokens.assets(DTCG, null, null).tokens())
                .as("an inline style, an SVG fill, an email template — same value, two forms")
                .containsExactly(
                        entry("color.accent", "#0b5"),
                        entry("color.surface", "#fff"),
                        entry("space.gutter", "1.5rem"));
    }

    @Test
    @DisplayName("a theme with no tokens renders byte-identically to before SH195")
    void noTokensChangesNothing() {
        assertThat(ShThemeTokens.assets(null, "body{}", "x").css())
                .as("""
                        not even an empty :root{} — SH101's digest is computed over exactly these \
                        bytes, so emitting one would move every digest in the instance for a \
                        feature nobody had used""")
                .isEqualTo("body{}");
        assertThat(ShThemeTokens.assets("   ", "body{}", null).css()).isEqualTo("body{}");
        assertThat(ShThemeTokens.assets("{}", "body{}", null).css()).isEqualTo("body{}");
    }

    @Test
    @DisplayName("an unparseable token file throws here, so the renderer can report it")
    void brokenJsonThrowsForTheCaller() {
        assertThatThrownBy(() -> ShThemeTokens.assets("{\"color\": ", "body{}", null))
                .as("""
                        a token file is content, so the page must still render — refusing a site \
                        over one missing brace is the wrong severity, and it is the split SH183 \
                        makes between a missing layout (409) and a missing region (a marker). But \
                        degrading SILENTLY is what SH270 refuses, and this class cannot report: it \
                        is a pure function and the incident log is a bean. So it throws, and \
                        ShPageRenderService.themeAssets catches beside the recorder — which is \
                        also what keeps ShSilentCatchInventoryTest's count honest.""")
                .isInstanceOf(tools.jackson.core.JacksonException.class);
    }

    @Test
    @DisplayName("a composite value is skipped and its siblings still compile")
    void compositeValuesAreSkipped() {
        String withComposite = """
                {
                  "shadow": { "soft": { "$value": { "offsetX": "0", "blur": "4px" } } },
                  "color":  { "accent": { "$value": "#0b5" } }
                }""";

        assertThat(ShThemeTokens.assets(withComposite, null, null).tokens())
                .as("""
                        DTCG composites have no single CSS form, so guessing one would emit a \
                        declaration the browser drops — and dropping it silently would take the \
                        rest of the :root block with it in some parsers. The sibling survives, \
                        which is the property that matters: one bad token is not a broken theme.""")
                .containsExactly(entry("color.accent", "#0b5"));
    }

    @Test
    @DisplayName("a name that cannot be a custom property is rewritten, not emitted raw")
    void unsafeNamesAreSanitised() {
        String awkward = """
                { "font size": { "base": { "$value": "16px" } } }""";

        assertThat(ShThemeTokens.assets(awkward, null, null).css())
                .as("a custom property name is not quotable, so the space had to go somewhere")
                .isEqualTo(":root{--font-size-base:16px;}");
        assertThat(ShThemeTokens.assets(awkward, null, null).tokens())
                .as("but the map keeps the author's own name — it is a lookup, not a CSS identifier")
                .containsExactly(entry("font size.base", "16px"));
    }

    @Test
    @DisplayName("group metadata is not a token")
    void groupMetadataIsIgnored() {
        String withMeta = """
                {
                  "$description": "brand",
                  "color": { "$type": "color", "accent": { "$value": "#0b5" } }
                }""";

        assertThat(ShThemeTokens.assets(withMeta, null, null).tokens())
                .containsExactly(entry("color.accent", "#0b5"));
    }
}
