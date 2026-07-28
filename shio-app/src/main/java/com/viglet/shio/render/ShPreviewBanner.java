/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.render.ShPageRenderService.ShRenderedPage;

/**
 * The mark that says this is a preview — SH183's "unmistakable banner", and P7.
 *
 * <h2>Why it is a rule and not a nicety</h2>
 *
 * <p>The renderer's stated risk is that a preview route becomes a delivery path by
 * accident. The technical guards are on {@link ShRenderAPI} (a distinct route, no
 * freshness headers, authentication); this is the human one, and it is the guard that
 * works on the person rather than on the infrastructure. Somebody looking at a rendered
 * page has to be able to tell, without checking the address bar, whether they are seeing
 * the site or seeing a draft of it — and which of the two, because "this page is fine"
 * means opposite things about a draft and a published row.</p>
 *
 * <h2>Why it is added here and not by the renderer</h2>
 *
 * <p>{@link ShPageRenderService} returns the page a front end would serve, byte for byte,
 * and nothing else. That separation is what lets SH101's digest and SH102's snapshot
 * compare the <em>page</em> rather than the page plus a banner, and it is what keeps the
 * renderer honest if the delivery question is ever reopened deliberately. The banner
 * belongs to the preview <em>surface</em>.</p>
 *
 * <h2>Styling is inline and scoped</h2>
 *
 * <p>The bar carries its own inline styles rather than a class, because the page it is
 * injected into is content nobody here controls: a site whose stylesheet happens to define
 * {@code .shio-preview-banner} would otherwise be able to hide the thing that tells the
 * viewer this is not live.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShPreviewBanner {

    private static final String STYLE = String.join(";",
            "position:sticky", "top:0", "z-index:2147483647",
            "display:block", "box-sizing:border-box", "width:100%",
            "padding:6px 12px",
            "font:600 13px/1.4 ui-sans-serif,system-ui,-apple-system,Segoe UI,sans-serif",
            "color:#7c2d12", "background:#fed7aa", "border-bottom:1px solid #ea580c",
            "text-align:center");

    /**
     * @param page the rendered page
     * @return the same page with the banner as the first element of {@code <body>}
     */
    public String wrap(ShRenderedPage page) {
        Document document = Jsoup.parse(page.html());
        document.outputSettings().prettyPrint(false);
        Element body = document.body();
        Element bar = document.createElement("div");
        bar.attr("data-shio-preview", "true");
        bar.attr("style", STYLE);
        bar.text(label(page));
        body.prependChild(bar);
        return document.html();
    }

    private String label(ShRenderedPage page) {
        String what = page.state() == ShPostState.DRAFT
                ? "Draft preview — not what visitors see"
                : "Published preview — rendered by Shio, not by this site's front end";
        return "Shio preview · " + what + " · " + page.site() + page.url()
                + " · layout " + page.layout();
    }
}
