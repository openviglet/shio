/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * What a rich-text field is allowed to contain (SH956).
 *
 * <h2>The defect</h2>
 *
 * <p>{@code html-editor.widget.tsx} is {@code document.execCommand} over a {@code contentEditable},
 * reads {@code innerHTML} as its value, and has no paste handler. Nothing anywhere sanitised
 * content — the only {@code sanitize} calls in the tree were for tenant and file names — and the
 * renderer emits field HTML unescaped by design. So a paste carrying {@code <img onerror=…>}, or an
 * agent's {@code post.upsert} writing the same field, put script on the published page.</p>
 *
 * <h2>At the boundary, not in each writer</h2>
 *
 * <p>This runs in the unified write service, the one place the console, REST, the agent batch and
 * the Universal Editor's draft merge all pass through. A sanitizer in the widget would defend the
 * writer that is easiest to go around, and the analysis found the editor's draft merge proposing
 * its own sanitize step separately — two sanitizers disagreeing about one field is worse than one
 * that is sometimes late.</p>
 *
 * <h2>Which fields, and the one that would have broken every site</h2>
 *
 * <p><b>{@code HTML Editor} only</b> — the rich-text widget a curator types and pastes into.
 * Deliberately <em>not</em> {@code Ace Editor - HTML}, which {@code PageLayout} and {@code Region}
 * declare for their {@code HTML} field: those are Handlebars templates carrying {@code <script>},
 * {@code {{#region}}} and a whole document on purpose, and an allow-list over them would strip the
 * site's own layout on its next save. A code editor is where writing markup <em>is</em> the point;
 * a rich-text box is where it is an accident.</p>
 *
 * <h2>Why the URL check is ours and not jsoup's</h2>
 *
 * <p>jsoup restricts {@code href} and {@code src} by protocol, and it cannot pass a <b>relative</b>
 * URL either way round: with {@code preserveRelativeLinks(false)} it resolves against a base and
 * there is none, so {@code /pricing} becomes empty and is refused; with it {@code true} it tests
 * the raw value against a list of schemes, which {@code /pricing} also fails. Measured, and it
 * refused {@code href="/pricing"} and {@code src="/file_source/abc/hero.png"} out of ordinary rich
 * text — which is most of the internal linking in any Shio site, so shipping that would have been
 * an outage rather than a hardening.</p>
 *
 * <p>So the protocol restriction is removed from those attributes and replaced by
 * {@link #hasDangerousScheme}: anything with no scheme at all is relative and fine, and anything
 * that declares one must declare a safe one. It is written as an allow-list of schemes for the
 * same reason the tag list is one.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShHtmlSanitizer {

    /** Attributes that carry a URL, and therefore a scheme somebody can choose. */
    private static final Set<String> URL_ATTRIBUTES = Set.of("href", "src", "action", "formaction");

    /** Schemes a rich-text body may point at. Everything else — {@code javascript:} and whatever
     * is invented next — is refused by not being here. */
    private static final Set<String> SAFE_SCHEMES = Set.of("http", "https", "mailto", "tel", "ftp");

    /**
     * The allow-list: jsoup's {@code relaxed} set — headings, lists, tables, links, images,
     * emphasis — plus the two attributes a rich-text body genuinely uses.
     *
     * <p>No {@code script}, no {@code iframe}, no {@code style} element, no {@code on*} handler,
     * none of which has to be enumerated: an allow-list refuses what it does not name, which is
     * why it survives an attack nobody has thought of yet. The protocol restrictions on
     * {@code href} and {@code src} are removed because they cannot pass a relative URL (above);
     * {@link #hasDangerousScheme} is what replaces them, and it runs <em>before</em> this.</p>
     */
    private static final Safelist ALLOWED = Safelist.relaxed()
            // jsoup's `relaxed` predates HTML5 sectioning and allows none of it. Caught by
            // `ShAgentRenderTest.landmarksAreCounted` going red: the render digest counts
            // `header`/`main`/`section`/`footer` as landmarks *because content has them*, so an
            // allow-list without them would flatten every page's structure on its next save and
            // take the accessibility landmarks with it. None of these can carry behaviour.
            .addTags("header", "footer", "main", "section", "article", "nav", "aside",
                    "figure", "figcaption", "hgroup", "time", "mark", "details", "summary")
            .addAttributes(":all", "class")
            .addAttributes("a", "target", "rel")
            .removeProtocols("a", "href", "ftp", "http", "https", "mailto")
            .removeProtocols("img", "src", "http", "https")
            .preserveRelativeLinks(true);

    /** What came out, and what was taken to get there. */
    public record Result(String html, List<String> removed) {

        public boolean changed() {
            return !removed.isEmpty();
        }
    }

    /**
     * Clean one value, reporting what was refused.
     *
     * <p>The report is what makes this teachable rather than mysterious: an agent whose markup
     * silently changed shape learns nothing and finds out on the page. {@code removed} names the
     * constructs rather than counting them, because "3 things removed" is not a fact anybody can
     * act on.</p>
     */
    public Result sanitize(String html) {
        if (html == null || html.isBlank()) {
            return new Result(html, List.of());
        }

        Document document = Jsoup.parseBodyFragment(html);
        List<String> removed = new ArrayList<>();
        boolean strippedUrl = stripDangerousUrls(document, removed);
        removed.addAll(refusedByAllowList(document));

        if (removed.isEmpty()) {
            // Nothing to take out. Return the ORIGINAL rather than jsoup's re-serialisation: a
            // clean value must survive a save byte for byte, or every digest moves and `shio diff`
            // reports a change nobody made.
            return new Result(html, List.of());
        }
        // Clean the tree the URL pass already edited, so a refused `javascript:` href is gone
        // rather than being handed back by a Safelist that no longer checks protocols.
        String source = strippedUrl ? document.body().html() : html;
        return new Result(Jsoup.clean(source, ALLOWED), List.copyOf(new LinkedHashSet<>(removed)));
    }

    /** Remove every URL attribute whose scheme is not one a rich-text body may point at. */
    private static boolean stripDangerousUrls(Document document, List<String> removed) {
        boolean stripped = false;
        for (Element element : document.body().getAllElements()) {
            for (Attribute attribute : element.attributes().asList()) {
                String name = attribute.getKey().toLowerCase(Locale.ROOT);
                if (!URL_ATTRIBUTES.contains(name) || !hasDangerousScheme(attribute.getValue())) {
                    continue;
                }
                element.removeAttr(attribute.getKey());
                removed.add(name + " with an unsafe scheme on <" + element.tagName() + ">");
                stripped = true;
            }
        }
        return stripped;
    }

    /**
     * Whether this URL declares a scheme that is not on the allow-list.
     *
     * <p>No scheme is not dangerous: {@code /pricing}, {@code ../about} and {@code #section} are
     * ordinary. A scheme is the text before the first {@code :}, and only when that colon comes
     * before any {@code /}, {@code ?} or {@code #} — otherwise {@code /a:b} would read as one.
     * {@code data:} is refused outright rather than narrowed to images: a {@code data:} document is
     * a same-origin navigation in some browsers, and an inline image belongs in the media library
     * where it can be found again.</p>
     */
    static boolean hasDangerousScheme(String url) {
        if (url == null) {
            return false;
        }
        // Control characters and whitespace inside a scheme are how `java\tscript:` gets past a
        // naive check; the browser strips them and then reads the scheme.
        String value = url.replaceAll("[\\u0000-\\u0020]", "").toLowerCase(Locale.ROOT);
        int colon = value.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        String beforeColon = value.substring(0, colon);
        if (beforeColon.contains("/") || beforeColon.contains("?") || beforeColon.contains("#")) {
            return false;
        }
        return !SAFE_SCHEMES.contains(beforeColon);
    }

    /** The tags and attributes this value carries that the allow-list does not permit, named. */
    private static Set<String> refusedByAllowList(Document document) {
        Set<String> refused = new LinkedHashSet<>();
        for (Element element : document.body().getAllElements()) {
            String tag = element.tagName().toLowerCase(Locale.ROOT);
            if ("body".equals(tag)) {
                continue;
            }
            if (!ALLOWED.isSafeTag(tag)) {
                refused.add("<" + tag + ">");
                continue;
            }
            for (Attribute attribute : element.attributes()) {
                String name = attribute.getKey().toLowerCase(Locale.ROOT);
                if (name.startsWith("on")) {
                    // Named as a family: a reader told `onerror` was removed will ask about
                    // `onload`, and the answer is the same for every one of them.
                    refused.add("event handler " + name);
                } else if (!ALLOWED.isSafeAttribute(tag, element, attribute)) {
                    refused.add(name + " on <" + tag + ">");
                }
            }
        }
        return refused;
    }
}
