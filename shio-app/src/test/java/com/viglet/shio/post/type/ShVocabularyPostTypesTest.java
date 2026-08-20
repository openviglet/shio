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
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
 * <h2>SH871 lives here, and the ordering below is what pays for it</h2>
 *
 * <p>{@code data.fields} is the same op, the same question — "make this instance able to hold what
 * this document writes" — asked of a field instead of a type, so a second isolated context for it
 * would have bought nothing and {@code ShContextCacheHeadroomTest} says there were none left.
 * What it costs instead is one declared order: {@code aConversionProvisionsWhatItWrites} asserts a
 * bare instance <em>has no</em> {@code Page}, and every SH871 test widens one.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-vocabulary-types;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestMethodOrder(OrderAnnotation.class)
class ShVocabularyPostTypesTest {

    private static final String SITE = "SH607Site";

    /** One of SH871's forty, spelled the way `verbatimField` spells them: block hash plus role. */
    private static final String ANNOTATED = "b1a2c3dHeading";

    @Autowired
    private ShVocabularyPostTypes shVocabularyPostTypes;
    @Autowired
    private ShPostTypeAdminService shPostTypeAdminService;
    @Autowired
    private ShAgentBatchService batchService;

    @Test
    @Order(1)
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

    // --- SH871: the fields a capture invented, declared by the run that invented them ---

    /**
     * The measured defect, at the boundary it crossed (SH527).
     *
     * <p>{@code shio convert --annotate} stamps a verbatim page's own markup with {@code sh-field}
     * attributes taken from the mapping {@code propose} already computed. The names carry the block
     * hash they came from — {@code b1a2c3dHeading} — so <b>no catalogue can ship them</b>: they are
     * computed from somebody's website, and the run that computes them was the only thing that
     * could ever declare them. It could not. Measured on {@code www.viglet.org}: 40 distinct
     * fields named, the first {@code post.upsert} refused against a {@code Page} declaring none of
     * them, and the conversion dead having uploaded 18 assets, written zero pages and left a 404
     * at the home page. It ran only after those 40 were added by hand through {@code pull}, an edit
     * and {@code push} — a precondition the run itself can satisfy, which is the shape SH763
     * was.</p>
     */
    @Test
    @DisplayName("SH871: a conversion declares the fields it invented and writes them, in one batch")
    void aRunDeclaresTheFieldsItInvented() {
        ShAgentChangeSet applied = batchService.run(new ShAgentBatch(false, List.of(
                op(ShAgentBatchService.SITE_UPSERT, "site:SH871Site", null, null, null),
                op(ShAgentBatchService.RENDER_PROVISION, null, null, null,
                        model(List.of("Page"), ANNOTATED)),
                op(ShAgentBatchService.POST_UPSERT, "post:SH871Site/", "Page", "/", annotated()))),
                false, "tester");

        assertThat(applied.results()).hasSize(3);
        assertThat(applied.results().get(1).detail())
                .as("the provision names the field it declared, so a reader can tell it from a "
                        + "noop and diff two runs (P5)")
                .contains("declared").contains("Page." + ANNOTATED);
        assertThat(fieldNames("Page"))
                .as("appended, and the four the catalogue ships are still there in their own "
                        + "order — a declaration widens a type and never redefines one")
                .startsWith("title", "description", "sections", "content")
                .contains(ANNOTATED);
    }

    @Test
    @DisplayName("SH871: the declared field is a plain optional Text — names, never definitions")
    void aDeclaredFieldIsPlainText() {
        batchService.run(new ShAgentBatch(false, List.of(
                op(ShAgentBatchService.RENDER_PROVISION, null, null, null,
                        model(List.of("Page"), "e5f6a7bIntro")))),
                false, "tester");

        assertThat(shPostTypeAdminService.get("Page").orElseThrow().fields())
                .filteredOn(field -> "e5f6a7bIntro".equals(field.name()))
                .singleElement()
                .satisfies(field -> {
                    assertThat(field.widget()).isEqualTo("Text");
                    assertThat(field.required())
                            .as("a required field would refuse every OTHER page the same run "
                                    + "writes, since only one of them carries this block")
                            .isFalse();
                    assertThat(field.isTitle()).isFalse();
                    assertThat(field.isSummary()).isFalse();
                });
    }

    @Test
    @DisplayName("SH871: a dry run resolves against its own declaration, and writes nothing")
    void aPlanResolvesAgainstItsOwnDeclaration() {
        // SH153's problem one level in, and the case the feature exists for: `convert --annotate
        // --dry-run` is how an operator finds out whether the real run is safe, so a plan that
        // refused on every field the same document creates would make it unplannable.
        ShAgentChangeSet planned = batchService.run(new ShAgentBatch(true, List.of(
                op(ShAgentBatchService.SITE_UPSERT, "site:SH871Planned", null, null, null),
                op(ShAgentBatchService.RENDER_PROVISION, null, null, null,
                        model(List.of("Page"), "c3d4e5fHeading")),
                op(ShAgentBatchService.POST_UPSERT, "post:SH871Planned/", "Page", "/",
                        plannedPage("c3d4e5fHeading")))),
                false, "tester");

        assertThat(planned.results()).hasSize(3);
        assertThat(planned.results().get(1).detail()).contains("would declare");
        assertThat(fieldNames("Page"))
                .as("the plan wrote nothing — the declaration is a planning aid, not a write")
                .doesNotContain("c3d4e5fHeading");
    }

    @Test
    @DisplayName("SH871: declaring the same field twice is a noop, so a re-conversion is free")
    void declaringTwiceIsANoop() {
        ShAgentBatch.Op declare = op(ShAgentBatchService.RENDER_PROVISION, null, null, null,
                model(List.of("Page"), "a1b2c3dHeading"));
        batchService.run(new ShAgentBatch(false, List.of(declare)), false, "tester");

        ShAgentChangeSet again = batchService.run(new ShAgentBatch(false, List.of(declare)),
                false, "tester");

        assertThat(again.results()).singleElement()
                .satisfies(row -> assertThat(row.action()).isEqualTo("noop"));
        assertThat(again.changed()).isZero();
    }

    @Test
    @DisplayName("SH871: a type this instance does not have is a 400 that names the door")
    void declaringOnAnAbsentTypeTeaches() {
        assertThatThrownBy(() -> batchService.run(new ShAgentBatch(false, List.of(
                op(ShAgentBatchService.RENDER_PROVISION, null, null, null,
                        model(List.of(), "heroHeading", "NoSuchType")))),
                false, "tester"))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(thrown -> {
                    ShProblem problem = ((ShTeachingException) thrown).problem();
                    assertThat(problem.toProblemDetail().getTitle())
                            .isEqualTo("No post-type named 'NoSuchType'");
                    assertThat(problem.toProblemDetail().getProperties())
                            .as("P6: `data.fields` appends to what is there, so the refusal has "
                                    + "to say what would create it")
                            .containsKey("allowed");
                });
    }

    @Test
    @DisplayName("SH871: a system type is refused — its model is this code's, not a caller's")
    void declaringOnASystemTypeTeaches() {
        batchService.run(new ShAgentBatch(false, List.of(
                op(ShAgentBatchService.RENDER_PROVISION, null, null, null, null))),
                false, "tester");

        assertThatThrownBy(() -> batchService.run(new ShAgentBatch(false, List.of(
                op(ShAgentBatchService.RENDER_PROVISION, null, null, null,
                        model(List.of(), "sneak", "Region")))),
                false, "tester"))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(thrown -> assertThat(((ShTeachingException) thrown).problem()
                        .toProblemDetail().getTitle())
                        .isEqualTo("'Region' is a system post-type"));
    }

    @Test
    @DisplayName("SH871: a name that would not survive being a field is refused before anything runs")
    void anUnusableNameTeaches() {
        // Not a `BatchOpException`, and deliberately: the names are read once more when the run
        // works out what it would declare, which happens BEFORE the first op. So a batch carrying
        // an unusable name is refused whole rather than at the op that would have used it — which
        // is the whole subject here, since the alternative leaves assets uploaded.
        assertThatThrownBy(() -> batchService.run(new ShAgentBatch(false, List.of(
                op(ShAgentBatchService.SITE_UPSERT, "site:SH871Unusable", null, null, null),
                op(ShAgentBatchService.RENDER_PROVISION, null, null, null,
                        model(List.of("Page"), "not a field name")))),
                false, "tester"))
                .isInstanceOf(ShTeachingException.class)
                .satisfies(thrown -> assertThat(((ShTeachingException) thrown).problem()
                        .toProblemDetail().getTitle())
                        .isEqualTo("'not a field name' is not a usable field name"));
    }

    private static ShAgentBatch.Op op(String name, String address, String type, String folder,
                                      JsonNode data) {
        return new ShAgentBatch.Op(name, address, type, folder, data);
    }

    /** SH871 — `data.types` and `data.fields` on one op, the way a converted document compiles. */
    private static JsonNode model(List<String> types, String field) {
        return model(types, field, "Page");
    }

    private static JsonNode model(List<String> types, String field, String onType) {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        ArrayNode array = data.putArray("types");
        types.forEach(array::add);
        data.putObject("fields").putArray(onType).add(field);
        return data;
    }

    /** A page whose markup carries the annotation and whose data holds the value it addresses. */
    private static JsonNode annotated() {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("title", "Home");
        data.put("content", "<h1 sh-field=\"" + ANNOTATED + "\">Start in an afternoon</h1>");
        data.put(ANNOTATED, "Start in an afternoon");
        return data;
    }

    private static JsonNode plannedPage(String field) {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("title", "Home");
        data.put(field, "Start in an afternoon");
        return data;
    }

    private List<String> fieldNames(String typeName) {
        return shPostTypeAdminService.get(typeName).orElseThrow().fields().stream()
                .map(ShPostTypeAttrDef::name)
                .toList();
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
