/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.locale;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.persistence.model.system.ShLocale;
import com.viglet.shio.persistence.repository.system.ShLocaleRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Read-only catalog of the locales known to the install (SH53). The
 * {@link ShLocale} table is seeded at startup but had no surface; this exposes
 * it so the console can offer a language picker when authoring translations.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping("/api/v2/locale")
@Tag(name = "Locale", description = "Available content locales (read-only)")
public class ShLocaleAPI {

    private final ShLocaleRepository shLocaleRepository;

    public ShLocaleAPI(ShLocaleRepository shLocaleRepository) {
        this.shLocaleRepository = shLocaleRepository;
    }

    @GetMapping
    public List<ShLocale> list() {
        return shLocaleRepository.findAll().stream()
                .sorted((a, b) -> {
                    String an = a.getInitials() == null ? "" : a.getInitials();
                    String bn = b.getInitials() == null ? "" : b.getInitials();
                    return an.compareToIgnoreCase(bn);
                })
                .toList();
    }
}
