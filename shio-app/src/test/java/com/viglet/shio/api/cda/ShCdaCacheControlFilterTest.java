/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * Verifies the SH3 caching headers — {@code Cache-Control} + body-hash
 * {@code ETag} with an {@code If-None-Match} → {@code 304} round-trip — using
 * the two CDA filters around a stub controller.
 *
 * @author Alexandre Oliveira
 */
class ShCdaCacheControlFilterTest {

    @RestController
    static class StubCdaController {
        @GetMapping(value = "/api/v2/cda/site", produces = "application/json")
        String site() {
            return "[{\"id\":\"s1\",\"name\":\"Site\"}]";
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        ShCdaProperties properties = new ShCdaProperties(); // max-age 60 default
        mockMvc = MockMvcBuilders.standaloneSetup(new StubCdaController())
                .addFilters(new ShallowEtagHeaderFilter(), new ShCdaCacheControlFilter(properties))
                .build();
    }

    @Test
    void cdaGetCarriesCacheControlAndEtag() throws Exception {
        mockMvc.perform(get("/api/v2/cda/site"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=60")))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(header().string("ETag", notNullValue()));
    }

    @Test
    void ifNoneMatchYields304() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/v2/cda/site"))
                .andExpect(status().isOk())
                .andReturn();
        String etag = first.getResponse().getHeader("ETag");

        mockMvc.perform(get("/api/v2/cda/site").header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("Cache-Control", containsString("max-age=60")));
    }
}
