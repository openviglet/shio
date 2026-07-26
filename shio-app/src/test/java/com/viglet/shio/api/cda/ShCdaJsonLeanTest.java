/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.cda.dto.ShCdaFolder;
import com.viglet.shio.api.cda.dto.ShCdaListing;
import com.viglet.shio.api.cda.dto.ShCdaPath;
import com.viglet.shio.api.cda.dto.ShCdaPost;
import com.viglet.shio.api.cda.dto.ShCdaPostSummary;
import com.viglet.shio.api.cda.dto.ShCdaPostTypeSchema;
import com.viglet.shio.api.cda.dto.ShCdaSchemaField;
import com.viglet.shio.api.cda.dto.ShCdaSite;

import org.springframework.test.context.junit.jupiter.SpringExtension;

import tools.jackson.databind.ObjectMapper;

/**
 * SH127 — the delivery path is lean too, and the SDK types moved with it.
 *
 * <h2>Why this could not be half a change</h2>
 *
 * <p>SH125 leaned the agent surface and deliberately stopped at the CDA, because
 * {@code @viglet/shio-client} declared nullable delivery fields as
 * {@code string | null} rather than optional. Omitting the null on the wire while
 * the types still promised one would have handed every typed consumer
 * {@code undefined} from a comparison it believed was exhaustive — the payload gets
 * smaller and the contract gets a lie in it. So the two halves shipped in one commit:
 * {@code @JsonInclude(NON_NULL)} on the DTOs here, {@code field?: T} in
 * {@code shio-client/src/types.ts}.</p>
 *
 * <h2>The rule that survives, unchanged</h2>
 *
 * <p><b>NON_NULL, never NON_DEFAULT.</b> A null says nothing that absence does not
 * already say; a {@code false} or a {@code 0} frequently <em>is</em> the answer.
 * {@code published: false} is the difference between a draft and a page nobody
 * mentioned, and {@code totalPosts: 0} is the answer to "is this folder empty?".
 * Dropping either makes the response smaller and the protocol worse — silence reads
 * as "unknown", and an unknown sends the caller probing, which is the cost the whole
 * token-economy block exists to remove.</p>
 *
 * @author Alexandre Oliveira
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class ShCdaJsonLeanTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    void aSiteWithNothingToSayOmitsItRatherThanSayingNull() {
        String json = json(new ShCdaSite("s1", "Blog", null, null, null));

        assertThat(json).doesNotContain(":null");
        assertThat(json).contains("\"id\":\"s1\"").contains("\"name\":\"Blog\"");
    }

    @Test
    void aPostOmitsEveryUnsetField() {
        String json = json(new ShCdaPost("p1", null, null, null, null, null, null,
                false, null, null, null, null, List.of(),
                objectMapper.createObjectNode()));

        assertThat(json).doesNotContain(":null");
    }

    @Test
    void publishedFalseSurvivesBecauseItIsTheAnswer() {
        // The counter-rule. NON_DEFAULT would have eaten this, and a consumer reading
        // an absent `published` cannot tell a draft from an old payload shape.
        String json = json(new ShCdaPost("p1", "T", null, "/t", "Article", "s1", "f1",
                false, Instant.parse("2026-01-01T00:00:00Z"), null, null, "en", List.of(),
                objectMapper.createObjectNode()));

        assertThat(json).contains("\"published\":false");
    }

    @Test
    void anEmptyFolderStillReportsItsZeroCount() {
        String json = json(new ShCdaListing(null, List.of(), List.of(), 0, 20, 0L));

        assertThat(json)
                .doesNotContain(":null")
                .contains("\"page\":0")
                .contains("\"totalPosts\":0");
    }

    @Test
    void everyReadDtoIsNullFree() {
        List<Object> payloads = List.of(
                new ShCdaSite("s1", "Blog", null, null, null),
                new ShCdaFolder("f1", "root", true),
                new ShCdaPostSummary("p1", null, null, null, null, null, null, null),
                new ShCdaPath(null, List.of(), null),
                new ShCdaSchemaField("headline", null, null, null, false, false, false,
                        false, 1, List.of()),
                new ShCdaPostTypeSchema("Article", null, null, null, List.of()));

        assertThat(payloads).allSatisfy(payload -> assertThat(json(payload))
                .withFailMessage("CDA JSON must not ship nulls (SH127). Offending payload:%n%s",
                        json(payload))
                .doesNotContain(":null"));
    }

    @Test
    void theSchemaFieldShapeIsTheOneTheAgentContextInherits() {
        // The reason SH125 left a documented null behind: the SH77 context pack's `model`
        // section reuses this exact record (P10 — one field shape across the product), so
        // leaning it here is what removes that null from the agent surface as well.
        // ShAgentJsonLeanTest asserts the other end of the same fact.
        String json = json(new ShCdaSchemaField("headline", "Headline", null, "Text",
                true, false, true, false, 1, List.of()));

        assertThat(json).doesNotContain(":null").contains("\"required\":true");
    }
}
