/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.ShAgentContextService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;

/**
 * SH272 — what a template may call is discoverable, and only where templates exist.
 *
 * <p><b>Its own database, and one test rather than three.</b> The condition is the instance's own
 * model, and the render post-types are created on first use by <em>anything</em> in the JVM — so an
 * "this instance has no templates" assertion is a claim about what every other test class did
 * first. Written as two methods it failed exactly that way: the second method's {@code ensure()}
 * ran before the first method looked. Asserting absence and then presence in one sequence removes
 * the shared state and the method-ordering assumption at once, and it states the rule better than
 * separate tests would — the line appears <em>because</em> the instance gained the model.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh272;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShTemplateHelperDiscoveryTest {

    @Autowired
    private ShRenderPostTypes shRenderPostTypes;
    @Autowired
    private ShTemplateEngine shTemplateEngine;
    @Autowired
    private ShAgentBatchService batchService;
    @Autowired
    private ShAgentContextService contextService;

    @Test
    @DisplayName("the context pack names the helpers, once the instance has templates")
    void theContextPackNamesTheHelpers() {
        String site = "sh272-" + UUID.randomUUID().toString().substring(0, 8);
        batchService.run(new ShAgentBatch(false, List.of(new ShAgentBatch.Op(
                "site.upsert", "site:" + site, null, null, null))), false, "tester");

        assertThat(conventions(site))
                .as("P3 — the pack is budgeted and read once per session, including by the many "
                        + "sessions that never write a template. An instance with no render model "
                        + "is told nothing about one, and the condition costs no extra query "
                        + "because the model is already in hand.")
                .noneMatch(line -> line.contains("Handlebars"));

        shRenderPostTypes.ensure();

        assertThat(conventions(site))
                .as("before this, the only way to learn the capability set was to write a template "
                        + "calling something else and read the 400 — a teaching error doing the "
                        + "job of documentation, one name per attempt, which is the opposite of "
                        + "P2. SH260 had already made the engine the one source; every reader of "
                        + "it was internal.")
                .anySatisfy(line -> assertThat(line)
                        .contains("{{#query folder= type= limit=}}")
                        .contains("{{#navigation folder=}}")
                        .contains("{{#search q= type= limit=}}")
                        .contains("{{#getRelation FIELD}}")
                        .as("a signature and not a bare list: the call shape is the part an author "
                                + "gets wrong, and a hash parameter's name exists nowhere but "
                                + "inside the helper's own body, so no reader could have derived "
                                + "it from the registry alone")
                        .contains("{{file_url FIELD}}")
                        .contains("sh-region"));

        assertThat(conventions(site))
                .as("`form` is deliberately unregistered (SH247) because a form needs somewhere "
                        + "for a submission to go. A capability list that named it would be worse "
                        + "than no list: the author would write it, and SH192 would refuse the "
                        + "write the documentation had just recommended.")
                .noneMatch(line -> line.contains("{{#form"));
        assertThat(shTemplateEngine.registeredSignatures())
                .as("and the engine is the one source — nothing downstream restates the set")
                .noneMatch(signature -> signature.contains("form"));
    }

    private List<String> conventions(String site) {
        return contextService.context(site, null, 0, "conventions", null).conventions();
    }
}
