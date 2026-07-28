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
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.agent.ShAgentReadService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH277 — a template written through the documented flow has a title, and no two share one.
 *
 * <p>Everything else in this package builds its fixture by persisting entities and calling
 * {@code setTitle} by hand, which is what let the first half go unnoticed: the flow SH183
 * documents — {@code POST /api/v2/render/post-types} then {@code post.upsert} — was the one
 * nothing exercised end to end. So these tests drive the batch service, which is the path an
 * agent actually takes.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh277;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShTemplateTitleWriteTest {

    @Autowired
    private ShRenderPostTypes shRenderPostTypes;
    @Autowired
    private ShAgentBatchService batchService;
    @Autowired
    private ShAgentReadService readService;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("the render post-types declare a title field")
    void theRenderTypesDeclareATitle() {
        List<ShPostTypeDef> defs = shRenderPostTypes.ensure();

        assertThat(defs).allSatisfy(def -> assertThat(def.fields())
                .as("""
                        %s must declare a title field. The renderer addresses a template by \
                        title, and a write derives the title from whichever field carries \
                        isTitle — with none, every template written through post.upsert lands \
                        with an empty title, and no sh-region slot or postTypeLayout binding can \
                        ever resolve to it.""", def.name())
                .anyMatch(ShPostTypeAttrDef::isTitle));
    }

    @Test
    @DisplayName("a region written by post.upsert carries the title it declared")
    void upsertSetsTheTitle() {
        String site = newSite();

        ShAgentChangeSet written = batchService.run(new ShAgentBatch(false, List.of(
                region(site, "/r-nav", "NAV"))), false, "tester");

        assertThat(written.changed()).isEqualTo(1);
        assertThat(readService.read(List.of("post:" + site + "/r-nav"), null, false, true))
                .singleElement()
                .satisfies(doc -> assertThat(doc.data().get(ShSystemPostTypeAttr.TITLE).asString())
                        .isEqualTo("NAV"));
    }

    @Test
    @DisplayName("a second template claiming the same title is refused")
    void aDuplicateTitleIsRefused() {
        String site = newSite();
        batchService.run(new ShAgentBatch(false, List.of(region(site, "/r-one", "HEADER"))),
                false, "tester");

        assertThat(catchThrowable(() -> batchService.run(new ShAgentBatch(false, List.of(
                region(site, "/r-two", "HEADER"))), false, "tester")))
                .as("""
                        SH263 reported this and nothing stopped it: two Regions called HEADER \
                        make sh-region="HEADER" resolve to whichever row the fold reaches first, \
                        so the site renders consistently wrongly (SH278) with no write refused.""")
                .isInstanceOf(ShTeachingException.class)
                .satisfies(failure -> {
                    // The batch service re-throws every op failure wrapped so the report can say
                    // `at: ops[n]`, so the assertion is on the problem rather than the class.
                    var problem = ((ShTeachingException) failure).problem().toProblemDetail();
                    assertThat(problem.getStatus())
                            .as("409, like every other occupied-identity refusal on this surface "
                                    + "(post.move onto a taken URL, a rename onto a live type)")
                            .isEqualTo(409);
                    assertThat(problem.getProperties().get("fix").toString())
                            .as("P6 — the holder is named as an address the caller can read next, "
                                    + "and `id:` is the one form that is right for a console-made "
                                    + "template as well as an upserted one (SH172)")
                            .contains("id:");
                    assertThat(problem.getDetail()).contains("HEADER");
                });
    }

    @Test
    @DisplayName("the same template keeps its own title on a second write")
    void anUpdateIsNotItsOwnDuplicate() {
        String site = newSite();
        batchService.run(new ShAgentBatch(false, List.of(region(site, "/r-keep", "FOOTER"))),
                false, "tester");

        ShAgentChangeSet again = batchService.run(new ShAgentBatch(false, List.of(
                new ShAgentBatch.Op("post.upsert", "post:" + site + "/r-keep",
                        ShSystemPostType.REGION, "/", data("FOOTER", "<footer>v2</footer>")))),
                false, "tester");

        assertThat(again.changed())
                .as("""
                        a template's own row is not a collision — it has a DRAFT row and a \
                        PUBLISHED row sharing id and title, so comparing rows instead of ids \
                        would make every edit and every publish of a template fail.""")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a plan reports the collision instead of dying on it")
    void aPlanReportsTheCollision() {
        String site = newSite();
        batchService.run(new ShAgentBatch(false, List.of(region(site, "/r-plan", "SIDEBAR"))),
                false, "tester");

        ShAgentChangeSet plan = batchService.run(new ShAgentBatch(true, List.of(
                region(site, "/r-plan-2", "SIDEBAR"),
                region(site, "/r-plan-3", "UNIQUE_ONE"))), false, "tester");

        assertThat(plan.results().getFirst().detail())
                .as("""
                        SH168's rule: a plan that threw would hide every later row, and the \
                        report is the reason to plan. It says a real run will refuse, so the \
                        plan can never come back greener than the write it predicts.""")
                .contains("SIDEBAR").contains("a real run will refuse");
        assertThat(plan.results().get(1).detail())
                .as("and the row that is fine says nothing at all")
                .isNull();
    }

    @Test
    @DisplayName("SH297: a Theme's title is an identity too, and that was never decided")
    void aThemeTitleIsAlsoAnIdentity() {
        // SH277 keyed the refusal on the post-type rather than on how a title is *used*, so a
        // second Theme is refused as well — which is right, since SH286 made a theme reachable by
        // title through a layout's THEME field. It arrived without anyone deciding it, which is
        // exactly the kind of behaviour worth an assertion before somebody "simplifies" the rule
        // to the two types that have slots and bindings.
        String site = newSite();
        batchService.run(new ShAgentBatch(false, List.of(theme(site, "/t-one", "SKIN"))),
                false, "tester");

        assertThat(catchThrowable(() -> batchService.run(new ShAgentBatch(false, List.of(
                theme(site, "/t-two", "SKIN"))), false, "tester")))
                .isInstanceOf(ShTeachingException.class)
                .satisfies(failure -> assertThat(((ShTeachingException) failure).problem()
                        .toProblemDetail().getStatus()).isEqualTo(409));
    }

    private ShAgentBatch.Op theme(String site, String url, String title) {
        return new ShAgentBatch.Op("post.upsert", "post:" + site + url,
                ShSystemPostType.THEME, "/", data(title, "body{color:red}"));
    }

    // --- fixture ---

    private String newSite() {
        shRenderPostTypes.ensure();
        String site = "sh277-" + UUID.randomUUID().toString().substring(0, 8);
        batchService.run(new ShAgentBatch(false, List.of(new ShAgentBatch.Op(
                "site.upsert", "site:" + site, null, null, null))), false, "tester");
        return site;
    }

    private ShAgentBatch.Op region(String site, String url, String title) {
        return new ShAgentBatch.Op("post.upsert", "post:" + site + url,
                ShSystemPostType.REGION, "/", data(title, "<nav>" + title + "</nav>"));
    }

    private JsonNode data(String title, String body) {
        // The second key is whichever field the type declares — HTML for a Region, CSS for a Theme
        // — so one helper serves both. An undeclared key would be refused by SH168.
        String field = body.startsWith("body{")
                ? ShSystemPostTypeAttr.CSS : ShSystemPostTypeAttr.HTML;
        return objectMapper.valueToTree(Map.of(
                ShSystemPostTypeAttr.TITLE, title,
                field, body));
    }

    private static Throwable catchThrowable(Runnable work) {
        try {
            work.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }
}
