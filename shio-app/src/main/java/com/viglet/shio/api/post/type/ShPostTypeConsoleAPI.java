/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Console post-type API ({@code /api/v2/post/type}) — id-keyed, entity-shaped CRUD
 * backing the React console's {@code ShPostTypeService} (post-type editor, the
 * "New [PostType]" pickers, form building). The legacy controller at this path was
 * removed in the unified-model nuclear refactor; this is the rebuild.
 *
 * <p>Distinct from the by-name, lossless SH16 admin API at {@code /api/v2/post-type}
 * (the {@code shio} CLI). Reads carry {@code @JsonView(ShJsonViewPostType)} so the
 * {@code shPostTypeAttrs} tree (annotated with that view on the entity) serializes;
 * writes run through {@link ShPostTypeConsoleWriteService} for cascade safety.</p>
 *
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/post/type")
@Tag(name = "Post Type (Console)", description = "Console post-type CRUD (id-keyed, entity-shaped)")
public class ShPostTypeConsoleAPI {

    private final ShPostTypeRepository shPostTypeRepository;
    private final ShPostTypeConsoleWriteService shPostTypeConsoleWriteService;

    public ShPostTypeConsoleAPI(ShPostTypeRepository shPostTypeRepository,
                                ShPostTypeConsoleWriteService shPostTypeConsoleWriteService) {
        this.shPostTypeRepository = shPostTypeRepository;
        this.shPostTypeConsoleWriteService = shPostTypeConsoleWriteService;
    }

    @GetMapping
    public List<ShPostType> list() {
        return shPostTypeConsoleWriteService.list();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShPostType> get(@PathVariable String id) {
        return shPostTypeConsoleWriteService.get(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ShPostType> create(@RequestBody ShPostTypeRequest request, Principal principal) {
        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (shPostTypeRepository.findByName(request.name()) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        ShPostType created = shPostTypeConsoleWriteService.create(request.toShPostType(),
                principal != null ? principal.getName() : null);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShPostType> update(@PathVariable String id, @RequestBody ShPostTypeRequest request,
                                             Principal principal) {
        return shPostTypeConsoleWriteService.update(id, request.toShPostType(),
                        principal != null ? principal.getName() : null)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return shPostTypeConsoleWriteService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
