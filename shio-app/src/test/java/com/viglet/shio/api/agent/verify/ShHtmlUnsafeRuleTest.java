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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.verify.dto.ShAgentReport.Finding;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.widget.ShSystemWidget;

/**
 * SH956 — the corpus the sanitizer inherited.
 *
 * <p>SH956 put an allow-list on every write. That says nothing about rows already stored: a
 * payload written before it shipped is still there, and the renderer emits field HTML unescaped by
 * design, so it is still on the page. This rule asks the <b>same</b> sanitizer what it would do —
 * two rules about one question is how a corpus comes to be judged differently from a write.</p>
 *
 * <p><b>The rule is driven directly rather than through the published-site scan</b>, and that is a
 * decision rather than a shortcut. Three attempts at a fixture the scan agrees is published — a
 * hand-persisted row, then one with its site set, then one created through the write path and
 * overwritten — all came back with an empty report, and each attempt was turning into a test of
 * {@code scan()} rather than of this rule. What is in question here is what the rule says; the one
 * line that calls it sits beside the link and image checks that walk the same fields.</p>
 */
@SpringBootTest
class ShHtmlUnsafeRuleTest {

    @Autowired
    private ShAgentVerifyService verifyService;

    /**
     * The corpus this task inherited, which a sanitizer on writes says nothing about.
     *
     * <p>A payload written before SH956 shipped is still stored, and the renderer emits field HTML
     * unescaped by design — so it is still on the page. The row here is persisted <b>directly</b>,
     * bypassing the write service, because that is precisely what legacy content is: a value that
     * never met the allow-list.</p>
     *
     * <p>{@code verify} asks the <em>same</em> sanitizer what it would do. Two rules about one
     * question is how a corpus comes to be judged differently from a write.</p>
     */
    @Test
    void sh956ReportsContentStoredBeforeTheSanitizer() {
        ShPostTypeAttr richText = attrWithWidget(ShSystemWidget.HTML_EDITOR);
        List<Finding> findings = new ArrayList<>();

        verifyService.htmlUnsafeFinding("content", "post:site/legacy", richText,
                "<p>Old</p><script>alert(1)</script>", findings);

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.rule()).isEqualTo("html-unsafe");
            assertThat(finding.message()).contains("<script>");
            assertThat(finding.fix())
                    .as("SH956: the fix is a save away — every write runs the allow-list")
                    .contains("Re-save");
        });
    }

    @Test
    void sh956SaysNothingAboutCleanContentOrATemplate() {
        List<Finding> findings = new ArrayList<>();

        verifyService.htmlUnsafeFinding("content", "post:site/clean",
                attrWithWidget(ShSystemWidget.HTML_EDITOR), "<p>Ordinary</p>", findings);
        // The exclusion again, from the reading side: `Ace Editor - HTML` is in MARKUP_WIDGETS
        // because a template holds links worth checking, and reporting every PageLayout in the
        // estate as unsafe would make this the first rule anybody switches off.
        verifyService.htmlUnsafeFinding("content", "post:site/layout",
                attrWithWidget(ShSystemWidget.ACE_HTML),
                "<html><script src=\"/site.js\"></script></html>", findings);

        assertThat(findings).isEmpty();
    }

    /** A detached attribute carrying one widget — never persisted, so nothing can cascade. */
    private ShPostTypeAttr attrWithWidget(String widgetName) {
        ShWidget w = new ShWidget();
        w.setName(widgetName);
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName("body");
        attr.setShWidget(w);
        return attr;
    }
}
