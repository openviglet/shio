/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.acl.ShContentAclService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * SH1105 — one section of a type, as it would look on this page: the visual editor's rail thumbnail.
 *
 * <p>HTML the console draws in a sandboxed frame, through the page's own layout and theme (see
 * {@link ShPageRenderService#sectionPreview}). A picture for a person choosing a shape: an agent reads
 * the catalogue's fields from {@code /agent/editable?include=insertable}, which is where the same
 * choice is data, so this is classified console-only rather than declared in the manifest.</p>
 */
@RestController
@RequestMapping("/api/v2/render/section-preview")
@Tag(name = "Render", description = "The page → layout → region model")
public class ShSectionPreviewAPI {

    private final ShPageRenderService shPageRenderService;
    private final ShContentAclService shContentAclService;

    public ShSectionPreviewAPI(ShPageRenderService shPageRenderService, ShContentAclService shContentAclService) {
        this.shPageRenderService = shPageRenderService;
        this.shContentAclService = shContentAclService;
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "One section of a type, rendered on a page as HTML, for the editor's rail")
    public ResponseEntity<String> preview(@RequestParam String page, @RequestParam String field,
                                          @RequestParam String type) {
        shContentAclService.requireRead(page);
        return shPageRenderService.sectionPreview(page, field, type)
                .map(html -> ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
