/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A page's <em>structural</em> digest (SH101).
 *
 * <h2>Why not a screenshot</h2>
 *
 * <p>What actually goes wrong when an agent builds a page is structural and textual: a
 * section that renders empty, a hero that never got its heading, an image with no alt,
 * a list of one item where ten were meant. All of that is visible in the outline below,
 * at a few hundred tokens. A PNG costs orders of magnitude more, and tells the agent
 * less — it can see that something looks wrong but not what is missing. Pixels are
 * SH102, framed as an artifact for the human, which is the job they are actually good
 * at.</p>
 *
 * <h2>The digest field is the point</h2>
 *
 * <p>{@code digest} hashes the structure and nothing else — not word counts, not the
 * source, not the link text. So "did my edit change the shape of this page?" is a
 * string comparison rather than a re-read, and an unchanged page costs one line of
 * context on the next visit. Same discipline as SH77's context digest.</p>
 *
 * @param source    where the markup came from: {@code content} (the post's own
 *                  rich-text fields) or {@code fetch} (a delivery URL)
 * @param address   the post's SH79 address, when digesting content
 * @param url       the URL fetched, when digesting a delivery response
 * @param status    HTTP status of the fetch; absent for a content digest
 * @param fields    the rich-text fields that contributed, in order — so "the body is
 *                  empty" is distinguishable from "the post has no body field"
 * @param headings  the heading outline in document order
 * @param landmarks landmark element → count, name-sorted
 * @param links     every link, internal ones marked
 * @param images    every image with its alt text
 * @param words     words of visible text
 * @param digest    structural hash — compare it instead of re-reading
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentDigest(String source, String address, String url, Integer status,
                            List<String> fields, List<Heading> headings,
                            Map<String, Integer> landmarks, List<Link> links,
                            List<Image> images, int words, String digest,
                            Appearance appearance, String appearanceDigest) {

    /**
     * SH187 — the part of <em>appearance</em> that is honestly answerable without a browser.
     *
     * <p>Present only for a {@code source: rendered} digest, because it describes the document a
     * visitor gets; a post's own markup fields are not a page and have no stylesheet. Absent, not
     * empty, for everything else — the rule SH121 set for ids and SH193 followed for
     * {@code affects}.</p>
     *
     * <p><b>It is not a rendering.</b> Computed style and box geometry need a layout engine. What is
     * here is the stylistic surface the document itself carries, and its most useful member is
     * {@code unstyledClasses} — markup written against class names the stylesheet does not have,
     * which is the appearance defect an <em>agent</em> actually creates. That list is
     * <b>advisory</b>: a page may load a stylesheet this instance has never seen, which is why
     * {@code externalStylesheets} is beside it.</p>
     *
     * @param classes             every class the markup uses, sorted
     * @param unstyledClasses     those no stylesheet the page carries defines — advisory
     * @param inlineStyles        {@code style="…"} attributes: styling the theme cannot reach
     * @param imagesWithoutSize   images with no declared width/height, so the page reflows on load
     * @param externalStylesheets sheets loaded from elsewhere — why unstyledClasses may be long
     * @param definedClasses      class selectors the page's own stylesheets define
     */
    @JsonInclude(Include.NON_NULL)
    public record Appearance(List<String> classes, List<String> unstyledClasses, int inlineStyles,
                             List<String> imagesWithoutSize, List<String> externalStylesheets,
                             int definedClasses) {
    }

    /** @param level 1–6 */
    public record Heading(int level, String text) {
    }

    /**
     * @param href     as written in the markup
     * @param internal site-relative — the ones SH100's route check can resolve
     * @param text     the anchor's visible text, so an empty link is visible
     */
    @JsonInclude(Include.NON_NULL)
    public record Link(String href, boolean internal, String text) {
    }

    /**
     * @param alt {@code null} means the attribute is absent (a defect); {@code ""} means
     *            explicitly decorative (correct markup). Collapsing the two would make
     *            the digest unable to report the thing it is most often read for
     */
    @JsonInclude(Include.NON_NULL)
    public record Image(String src, String alt) {
    }
}
