/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import java.util.List;

import org.springframework.http.HttpStatus;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * A site's home picture was refused (SH1218), with what to send instead.
 *
 * <p>Each refusal names the read that answers it, because the caller is usually a console that
 * captured a picture a moment ago and needs to know whether to capture again or stop.</p>
 */
public class ShSiteThumbnailRefusedException extends ShTeachingException {

    private ShSiteThumbnailRefusedException(ShProblem problem) {
        super(problem);
    }

    static ShSiteThumbnailRefusedException noHome(String site) {
        return new ShSiteThumbnailRefusedException(ShProblem.conflict("site-thumbnail-no-home",
                        "The site has no published home page to take a picture of")
                .withDetail("A site's picture is its published home, the post at `post:" + site
                        + "/`, and there is none.")
                .withFix("Publish the home page first; the grid shows the site's initials until then."));
    }

    static ShSiteThumbnailRefusedException stale(String sent, String current) {
        return new ShSiteThumbnailRefusedException(ShProblem.conflict("site-thumbnail-stale",
                        "The picture was taken of a home that has changed since")
                .withDetail("It was sent for appearance " + sent + ", and the published home is "
                        + current + " now.")
                .withFix("Read GET /api/v2/site/{id}/thumbnail/status, capture the home again and "
                        + "send it with that digest."));
    }

    static ShSiteThumbnailRefusedException type(String sent, List<String> allowed) {
        return new ShSiteThumbnailRefusedException(ShProblem.of("site-thumbnail-type",
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "A site's picture is an image")
                .withDetail("It was sent as " + sent + ".")
                .withAllowed(allowed)
                .withFix("Send the captured picture with one of the allowed content types."));
    }

    static ShSiteThumbnailRefusedException size(int sent, int max) {
        return new ShSiteThumbnailRefusedException(ShProblem.of("site-thumbnail-too-large",
                        HttpStatus.CONTENT_TOO_LARGE, "The picture is larger than a card needs")
                .withDetail(sent + " bytes against a ceiling of " + max + ".")
                .withFix("Capture at card size, a few hundred pixels wide, as WebP or JPEG."));
    }
}
