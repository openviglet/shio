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
                .anySatisfy(line -> {
                    // **Derived, not restated** (SH384). This block used to name four signatures as
                    // literals, so adding one hash parameter to `{{#query}}` failed here — in the one
                    // test whose subject is that the pack is derived from the engine. It is the same
                    // defect SH382 found two assertions into `ShTemplateValidatorTest`, and the same
                    // fix: ask the engine, and the test then proves the *plumbing* rather than
                    // re-declaring the payload.
                    assertThat(shTemplateEngine.registeredSignatures())
                            .as("an empty registry would make the loop below vacuous")
                            .isNotEmpty();
                    for (String signature : shTemplateEngine.registeredSignatures()) {
                        assertThat(line)
                                .as("""
                                        a signature and not a bare list: the call shape is the part \
                                        an author gets wrong, and a hash parameter's name exists \
                                        nowhere but inside the helper's own body, so no reader could \
                                        have derived it from the registry alone. %s""", signature)
                                .contains(signature);
                    }
                    assertThat(line).contains("sh-region");
                });

        // SH247 — this used to assert the *opposite*: `form` was unregistered, and a capability
        // list naming it would have sent an author to write a call SH192 then refused. The
        // seventh capability shipped, and the line above needed no edit to say so — the pack is
        // derived from the engine (SH260/SH272), so registering the helper published it. That is
        // P2 costing nothing, which is the whole reason the set has one source.
        assertThat(conventions(site))
                .as("""
                        all seven of shObject.js' capabilities are named now, and `form` arrived                         in this list by registration rather than by anybody editing it.""")
                .anyMatch(line -> line.contains("{{#form type=}}"));
        assertThat(shTemplateEngine.registeredSignatures())
                .as("and the engine is still the one source — nothing downstream restates the set")
                .contains("{{#form type=}}…{{/form}}");
    }

    @Test
    @DisplayName("SH1265: a field named like a block helper renders its value, and the block still works")
    void aFieldNamedLikeABlockHelperRendersItsValue() {
        // SectionHero has an `image` field and `{{#image}}` is a helper, so `{{image}}` called the
        // helper with no block and every page emitted `src=""`. Every block helper's name is checked,
        // derived from the engine, so the next one registered cannot reopen this.
        var helpers = shTemplateEngine.registeredHelpers();
        assertThat(helpers).as("the engine registered no helpers, so this would check nothing")
                .containsKey("image");
        for (var entry : helpers.entrySet()) {
            String helper = entry.getKey();
            if (entry.getValue() == ShTemplateEngine.ShHelperShape.INLINE_PATH_ARG) {
                continue;
            }
            assertThat(shTemplateEngine.render("<p>{{" + helper + "}}</p><p>{{{" + helper + "}}}</p>",
                    java.util.Map.of(helper, "/images/a&b.webp")))
                    .as("SH1265: `{{%s}}` is the field, escaped, and `{{{%s}}}` is the field, raw",
                            helper, helper)
                    .isEqualTo("<p>/images/a&amp;b.webp</p><p>/images/a&b.webp</p>");
        }
    }

    private List<String> conventions(String site) {
        return contextService.context(site, null, 0, "conventions", null).conventions();
    }
}
