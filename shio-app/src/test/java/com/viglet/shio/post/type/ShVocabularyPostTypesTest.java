/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.post.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.post.type.ShPostTypeAdminService;
import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH607 — the SH95 vocabulary is installable, and a conversion into a bare instance works.
 *
 * <h2>The defect, and why every suite was green over it</h2>
 *
 * <p>{@code shio convert} writes {@code Page} and {@code Section*} posts. Nothing provisioned them:
 * they shipped only inside a blueprint, and a bare instance carries exactly the six types something
 * creates on demand. So {@code POST /api/v2/agent/apply} answered
 *
 * <pre>{@code
 * "No post-type named 'SectionFeatureGrid'"
 * "allowed": ["File","PageLayout","Redirect","Region","SiteScripts","Theme"]
 * }</pre>
 *
 * <p>on <b>every</b> replication scenario including fidelity — and no test saw it, because a
 * development instance has the types from an earlier console site or blueprint apply. It took CI
 * booting a jar against an empty database to produce the first bare instance this chain had ever
 * met, which is the same lesson {@code requireInstance} learned one level up (SH460): the instance
 * was carrying the test.</p>
 *
 * <p>Its own database, for {@link com.viglet.shio.render.ShRenderPostTypesTest}'s reason: these
 * types are instance-wide and the SH164/SH239 benchmarks measure documents whose size grows with
 * how many post-types exist.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-vocabulary-types;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
class ShVocabularyPostTypesTest {

    private static final String SITE = "SH607Site";

    @Autowired
    private ShVocabularyPostTypes shVocabularyPostTypes;
    @Autowired
    private ShPostTypeAdminService shPostTypeAdminService;
    @Autowired
    private ShAgentBatchService batchService;

    @Test
    @DisplayName("SH607: a conversion's model and its content land in one batch, on a bare instance")
    void aConversionProvisionsWhatItWrites() {
        // The assertion that would have caught it (SH527), at the boundary it crossed: `apply`, with
        // no `Page` and no `SectionHero` in the instance beforehand. Everything below this line was
        // a 400 before this task.
        assertThat(shPostTypeAdminService.get("Page"))
                .as("the precondition IS the defect: a bare instance has no Page, which is what "
                        + "every development instance quietly did have")
                .isEmpty();

        ShAgentChangeSet applied = batchService.run(new ShAgentBatch(false, List.of(
                op(ShAgentBatchService.SITE_UPSERT, "site:" + SITE, null, null, null),
                op(ShAgentBatchService.RENDER_PROVISION, null, null, null,
                        types("Page", "SectionHero")),
                op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + SITE + "/sections",
                        null, null, null),
                op(ShAgentBatchService.POST_UPSERT, "post:" + SITE + "/sections/hero",
                        "SectionHero", "/sections", hero()),
                op(ShAgentBatchService.POST_UPSERT, "post:" + SITE + "/", "Page", "/", homePage()))),
                false, "tester");

        assertThat(applied.results()).hasSize(5);
        assertThat(applied.results().get(1).detail())
                .as("the op is second, right after the site and before anything written against "
                        + "the model it installs")
                .as("the provision names what it made, so a reader can tell it from a noop (P5)")
                .contains("created").contains("Page").contains("SectionHero");
        assertThat(shPostTypeAdminService.get("Page")).isPresent();
        assertThat(shPostTypeAdminService.get("SectionHero")).isPresent();
    }

    @Test
    @DisplayName("SH607: Page holds all four keys a conversion writes — three shipped defs held none")
    void thePageTypeHoldsWhatAConversionWrites() {
        // The finding underneath the failure, and the one that would have survived provisioning the
        // *wrong* Page. Three definitions shipped and none of them could hold what `buildConversion`
        // emits: the SH71 bootstrap starter declares title/content and no `sections`, and the two
        // blueprints declared title/description/sections and no `content`. So the type this chain
        // has been writing against on every green run matched no file in this repository.
        assertThat(shVocabularyPostTypes.get("Page")).isPresent();
        assertThat(shVocabularyPostTypes.get("Page").orElseThrow().fields())
                .extracting(ShPostTypeAttrDef::name)
                .containsExactly("title", "description", "sections", "content");
    }

    @Test
    @DisplayName("SH607: it is idempotent, and the second run says so rather than reporting a create")
    void provisioningTwiceIsANoop() {
        ShAgentBatch.Op provision =
                op(ShAgentBatchService.RENDER_PROVISION, null, null, null, types("SectionFaq"));
        batchService.run(new ShAgentBatch(false, List.of(provision)), false, "tester");

        ShAgentChangeSet again =
                batchService.run(new ShAgentBatch(false, List.of(provision)), false, "tester");
        assertThat(again.results()).singleElement().satisfies(row -> {
            assertThat(row.action()).isEqualTo("noop");
            assertThat(row.detail())
                    .as("and it names the vocabulary it checked: a report that mentioned only the "
                            + "render types would read as though `data.types` had been ignored, "
                            + "which is precisely what it used to be")
                    .contains("SectionFaq");
        });
        assertThat(again.changed()).isZero();
    }

    @Test
    @DisplayName("SH607: a dry run of the same batch resolves the type it has not created yet")
    void aPlanResolvesAgainstItsOwnProvision() {
        // SH153's problem reached from the conversion side. Without `Declared.withPostTypesFrom` a
        // plan of this document dies on the post — so the one case the feature exists for, planning
        // a conversion into a bare instance, is the case that could not be planned.
        ShAgentChangeSet planned = batchService.run(new ShAgentBatch(true, List.of(
                op(ShAgentBatchService.SITE_UPSERT, "site:SH607Planned", null, null, null),
                op(ShAgentBatchService.RENDER_PROVISION, null, null, null, types("SectionCta")),
                op(ShAgentBatchService.FOLDER_UPSERT, "folder:SH607Planned/sections",
                        null, null, null),
                op(ShAgentBatchService.POST_UPSERT, "post:SH607Planned/sections/cta",
                        "SectionCta", "/sections", cta()))),
                false, "tester");

        assertThat(planned.results()).hasSize(4);
        assertThat(shPostTypeAdminService.get("SectionCta"))
                .as("and the plan wrote nothing at all — the declaration is a planning aid")
                .isEmpty();
    }

    @Test
    @DisplayName("SH607: a name this instance does not ship is a 400 that lists the ones it does")
    void anUnknownTypeTeaches() {
        // Re-thrown rather than wrapped, with `at` added — so the assertion is on the problem the
        // caller reads, not on the class the server threw.
        assertThatThrownBy(() -> batchService.run(new ShAgentBatch(false, List.of(
                op(ShAgentBatchService.RENDER_PROVISION, null, null, null, types("SectionPricing")))),
                false, "tester"))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(thrown -> {
                    ShProblem problem = ((ShTeachingException) thrown).problem();
                    assertThat(problem.toProblemDetail().getTitle())
                            .isEqualTo("No vocabulary post-type named 'SectionPricing'");
                    assertThat(problem.toProblemDetail().getProperties())
                            .as("P6: a refusal that names what this instance does ship, so the "
                                    + "caller's next call is a correct one")
                            .containsKey("allowed");
                });

        assertThat(shVocabularyPostTypes.names())
                .as("the catalogue is the SH95 vocabulary, sorted so two instances print one list")
                .containsExactly("Menu", "MenuItem", "Page", "SectionCta", "SectionFaq",
                        "SectionFeatureGrid", "SectionHero", "SectionLogoWall", "SectionMarkup",
                        "SectionRichText", "SectionTestimonial");
    }

    private static ShAgentBatch.Op op(String name, String address, String type, String folder,
                                      JsonNode data) {
        return new ShAgentBatch.Op(name, address, type, folder, data);
    }

    private static JsonNode types(String... names) {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        ArrayNode array = data.putArray("types");
        for (String name : names) {
            array.add(name);
        }
        return data;
    }

    /** All four keys, which is the point: the widened `Page` is what makes this batch legal. */
    private static JsonNode homePage() {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("title", "Home");
        data.put("description", "The replica's front door");
        data.putArray("sections").add("/sections/hero");
        data.put("content", "<footer>kept verbatim</footer>");
        return data;
    }

    private static JsonNode hero() {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("heading", "Start in an afternoon");
        return data;
    }

    private static JsonNode cta() {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("heading", "Get started");
        return data;
    }
}
