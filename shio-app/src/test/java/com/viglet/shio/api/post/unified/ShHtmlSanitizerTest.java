/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * SH956 — what a rich-text field is allowed to contain.
 *
 * <p>The widget is {@code execCommand} over a {@code contentEditable} with no paste handler, the
 * value is its {@code innerHTML}, and the renderer emits field HTML unescaped by design. Nothing
 * sanitised content anywhere: the only {@code sanitize} calls in the tree were for tenant and file
 * names. So a paste, or an agent's {@code post.upsert}, put script on the published page.</p>
 *
 * <p>{@code ShHtmlSanitizerWriteBoundaryTest} is the other half — that this runs at the one
 * boundary every writer crosses. This is the rule itself.</p>
 */
class ShHtmlSanitizerTest {

    private final ShHtmlSanitizer sanitizer = new ShHtmlSanitizer();

    @Test
    void sh956ScriptAndEventHandlersDoNotSurviveAWrite() {
        ShHtmlSanitizer.Result result = sanitizer.sanitize(
                "<p>Hello</p><script>alert(document.domain)</script>"
                        + "<img src=x onerror=\"alert(1)\">"
                        + "<a href=\"javascript:alert(1)\">click</a>"
                        + "<iframe src=\"https://evil.test\"></iframe>");

        assertThat(result.changed()).isTrue();
        assertThat(result.html())
                .doesNotContain("<script", "alert(", "onerror", "javascript:", "<iframe");
        assertThat(result.html())
                .as("SH956: the content survives — a sanitizer that empties the field is a refusal "
                        + "with extra steps, and a curator would paste it again somewhere worse")
                .contains("Hello");
    }

    @Test
    void sh956WhatWasRefusedIsNamed() {
        ShHtmlSanitizer.Result result = sanitizer.sanitize("<img src=x onerror=\"x()\"><script>y()</script>");

        // Named rather than counted: "3 things removed" is not a fact anybody can act on, and an
        // agent whose markup silently changed shape otherwise finds out on the page.
        assertThat(result.removed()).anyMatch(entry -> entry.contains("onerror"));
        assertThat(result.removed()).contains("<script>");
    }

    @Test
    void sh956OrdinaryRichTextIsReturnedByteForByte() {
        String body = "<p>A <strong>plan</strong> with <a href=\"/pricing\" class=\"cta\">a link</a>"
                + " and <img src=\"/file_source/abc/hero.png\" alt=\"Hero\"></p>"
                + "<ul><li>One</li><li>Two</li></ul>"
                + "<table><tbody><tr><td>Cell</td></tr></tbody></table>";

        ShHtmlSanitizer.Result result = sanitizer.sanitize(body);

        assertThat(result.removed())
                .as("SH956: a clean value must survive a save unchanged, or every digest moves and "
                        + "`shio diff` reports a change nobody made. Refused here: %s",
                        result.removed())
                .isEmpty();
        assertThat(result.changed()).isFalse();
        assertThat(result.html()).isEqualTo(body);
    }

    @Test
    void sh956AnEmptyOrAbsentValueIsLeftAlone() {
        assertThat(sanitizer.sanitize(null).html()).isNull();
        assertThat(sanitizer.sanitize("").changed()).isFalse();
        assertThat(sanitizer.sanitize("   ").changed()).isFalse();
    }

    @Test
    void sh956TheAllowListRefusesWhatItDoesNotName() {
        // The property that makes an allow-list worth having: it survives an attack nobody thought
        // of, because the question is "is this permitted" rather than "is this on a list of bad
        // things". `<object>` and `<embed>` are not enumerated anywhere in the sanitizer.
        ShHtmlSanitizer.Result result = sanitizer.sanitize(
                "<object data=\"x.swf\"></object><embed src=\"y\"><form action=\"/steal\"></form>");

        assertThat(result.html()).doesNotContain("<object", "<embed", "<form");
        assertThat(result.removed()).contains("<object>", "<embed>", "<form>");
    }

    @Test
    void sh956AStyleAttributeGoesAndAClassStays() {
        // `class` is added to the allow-list deliberately: a rich-text body uses it as a styling
        // hook and a site's stylesheet is what gives it meaning. `style` is not — an inline style
        // is a vector (`behavior:`, `expression()`) and a theme's job besides.
        ShHtmlSanitizer.Result result =
                sanitizer.sanitize("<p class=\"lead\" style=\"color:red\">Text</p>");

        assertThat(result.html()).contains("class=\"lead\"").doesNotContain("style=");
    }

    /**
     * The scheme check, which is ours rather than jsoup's — and the measurement that forced it.
     *
     * <p>jsoup restricts a URL attribute by protocol and cannot pass a <b>relative</b> URL either
     * way round: resolving needs a base and there is none, and testing the raw value against a
     * scheme list fails for a path. It refused {@code href="/pricing"} and
     * {@code src="/file_source/abc/hero.png"} out of ordinary rich text, which is most of the
     * internal linking in any Shio site — shipping that would have been an outage.</p>
     */
    @Test
    void sh956ARelativeUrlIsOrdinaryAndSurvives() {
        assertThat(ShHtmlSanitizer.hasDangerousScheme("/pricing")).isFalse();
        assertThat(ShHtmlSanitizer.hasDangerousScheme("../about")).isFalse();
        assertThat(ShHtmlSanitizer.hasDangerousScheme("#section")).isFalse();
        assertThat(ShHtmlSanitizer.hasDangerousScheme("/file_source/abc/hero.png")).isFalse();
        // A colon after a path separator is part of the path, not a scheme.
        assertThat(ShHtmlSanitizer.hasDangerousScheme("/a/b:c")).isFalse();
        assertThat(ShHtmlSanitizer.hasDangerousScheme("?q=a:b")).isFalse();

        assertThat(ShHtmlSanitizer.hasDangerousScheme("https://example.test/x")).isFalse();
        assertThat(ShHtmlSanitizer.hasDangerousScheme("mailto:a@b.test")).isFalse();
    }

    @Test
    void sh956ADeclaredSchemeMustBeOneWeAllow() {
        assertThat(ShHtmlSanitizer.hasDangerousScheme("javascript:alert(1)")).isTrue();
        assertThat(ShHtmlSanitizer.hasDangerousScheme("JaVaScRiPt:alert(1)")).isTrue();
        // The browser strips control characters and whitespace and then reads the scheme, which is
        // how a naive check is walked past.
        assertThat(ShHtmlSanitizer.hasDangerousScheme("java\tscript:alert(1)")).isTrue();
        assertThat(ShHtmlSanitizer.hasDangerousScheme(" javascript :alert(1)")).isTrue();
        assertThat(ShHtmlSanitizer.hasDangerousScheme("vbscript:msgbox(1)")).isTrue();
        // `data:` is refused outright rather than narrowed to images: a data: document is a
        // same-origin navigation in some browsers, and an inline image belongs in the library.
        assertThat(ShHtmlSanitizer.hasDangerousScheme("data:text/html;base64,PHNjcmlwdD4=")).isTrue();
        assertThat(ShHtmlSanitizer.hasDangerousScheme("data:image/png;base64,iVBOR")).isTrue();
    }

    @Test
    void sh956AJavascriptHrefIsStrippedAndTheLinkTextSurvives() {
        ShHtmlSanitizer.Result result = sanitizer.sanitize(
                "<p><a href=\"javascript:alert(1)\">Read more</a> and "
                        + "<a href=\"/pricing\">Pricing</a></p>");

        assertThat(result.html()).doesNotContain("javascript:");
        assertThat(result.html())
                .as("SH956: the text is the curator's content — only the scheme was the problem")
                .contains("Read more");
        assertThat(result.html())
                .as("SH956: and the honest link beside it is untouched")
                .contains("href=\"/pricing\"");
        assertThat(result.removed()).anyMatch(entry -> entry.contains("unsafe scheme"));
    }

    /**
     * HTML5 sectioning, which jsoup's {@code relaxed} predates and allows none of.
     *
     * <p>Found by {@code ShAgentRenderTest.landmarksAreCounted} going red on the first full run:
     * the render digest counts {@code header}/{@code main}/{@code section}/{@code footer} as
     * landmarks <em>because content has them</em>. An allow-list without them would flatten every
     * page's structure on its next save and take the accessibility landmarks with it — a
     * sanitizer doing more damage than the thing it defends against.</p>
     */
    @Test
    void sh956SemanticSectioningSurvives() {
        String body = "<header><h1>Pricing</h1></header>"
                + "<main><section><h2>Plans</h2><p>Text</p></section>"
                + "<figure><img src=\"/a.png\" alt=\"x\"><figcaption>Cap</figcaption></figure></main>"
                + "<aside><nav><a href=\"/about\">About</a></nav></aside>"
                + "<footer><p>Footer</p></footer>";

        ShHtmlSanitizer.Result result = sanitizer.sanitize(body);

        assertThat(result.removed())
                .as("SH956: sectioning carries no behaviour, and the digest counts it. Refused: %s",
                        result.removed())
                .isEmpty();
        assertThat(result.html()).isEqualTo(body);
    }
}
