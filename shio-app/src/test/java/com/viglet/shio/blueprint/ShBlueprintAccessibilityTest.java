/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.verify.ShAgentVerifyService;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.blueprint.dto.ShBlueprint;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH1121 — a blueprint nobody applies is a blueprint nothing checks.
 *
 * <h2>What went unreported, and for how long</h2>
 *
 * <p>{@code checks=a11y} found two rules broken by {@code marketing-site}'s layout — no
 * {@code <main>}, and a first heading of {@code <h2>} — and that one was repaired in the same
 * commit, because the headline benchmark applies it and went over its token budget. {@code landing}
 * had <b>exactly the same defect and no benchmark</b>, so nothing reported it: its {@code PageLayout}
 * emitted {@code <html lang>} and a {@code <body>} of sections, no main region, and no {@code h1}
 * anywhere on any page it built. A package this product recommends as a starting point shipped two
 * accessibility failures our own gate names, and the only reason the sibling was clean is that
 * something unrelated happened to render it.</p>
 *
 * <h2>Why this is a discovery and not a list of three names</h2>
 *
 * <p>Three of the six blueprints ship a {@code PageLayout} today. Writing those three into an
 * assertion would hold exactly today's packages to the gate and say nothing about the one added next
 * year — which is the defect, not the symptom: {@code landing} was already in the tree, already
 * recommended, and already broken. So the subjects are <b>read out of the registry</b>: every
 * blueprint whose content carries a {@code PageLayout} is applied to a fresh site, and
 * {@code checks=a11y} has to report nothing about it. A blueprint that gains a layout joins this
 * test by existing, and one that ships a broken one fails it the day it lands.</p>
 *
 * <p>The counts are printed rather than asserted, for the reason SH974 gives about every inventory in
 * this project: a figure in prose is a figure that goes stale, and the run is the place to read it.
 * The three that carry no layout are named too — {@code docs}, {@code product-catalog} and
 * {@code nextjs-starter} carry their markup in {@code files/app/layout.tsx}, which no Shio check
 * renders, and §LXXIV.11 says that is worth confirming rather than assuming. This confirms it by
 * construction: they are absent from the subjects because nothing in them declares a layout.</p>
 *
 * <h2>Its own database, and the cheaper answer the project refuses</h2>
 *
 * <p>Post-types are instance-wide and this applies several packages that declare their own, so it
 * cannot share with {@code ShBlueprintApplyTest}, which bends a {@code Page} type on purpose — SH315's
 * rule, the same one {@code ShBlueprintRendersTest} states for itself.</p>
 *
 * <p>SH347's guard asks, before a slot is spent, whether the class needs a context at all, and there
 * was exactly one slot left — so the cheaper answer was tried: share
 * {@code ShBlueprintRendersTest}'s database, since that class isolates one <em>for this same reason</em>
 * and already applies {@code marketing-site} to a site of its own. It works, and
 * {@code ShBenchmarkIsolationLintTest#everyDatabaseNameIsClaimedOnce} refuses it —
 * <i>two classes on one in-memory database is the shared fixture again, wearing the isolation's
 * clothes</i> — with an exemption only for the SH22 cross-database ITs, which are one scenario across
 * files. That rule is right and this is not its exception: two classes applying blueprints into one
 * instance is precisely a shared fixture. Recorded here because it is the next reader's first idea
 * too, and finding out costs a full suite.</p>
 *
 * <p>So the slot is spent deliberately, and {@code DECLARED_CONTEXTS} and {@code maxSize} moved with
 * it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh1121;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShBlueprintAccessibilityTest {

    /** The post-type that makes a blueprint's pages Shio's to render, and so this test's to judge. */
    private static final String LAYOUT_TYPE = "PageLayout";

    @Autowired
    private ShBlueprintRegistry registry;
    @Autowired
    private ShBlueprintService blueprintService;
    @Autowired
    private ShAgentVerifyService verifyService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("SH1121: every blueprint that ships a layout renders pages the a11y check passes")
    void everyShippedLayoutIsAccessible() {
        Map<String, ShBlueprint> all = registry.all();
        List<String> withLayout = new ArrayList<>();
        List<String> withoutLayout = new ArrayList<>();
        for (String name : all.keySet().stream().sorted().toList()) {
            (shipsALayout(all.get(name)) ? withLayout : withoutLayout).add(name);
        }
        System.out.printf("SH1121: %d blueprint(s) ship a %s and are judged here %s;"
                + " %d carry their markup outside Shio and are not %s%n",
                withLayout.size(), LAYOUT_TYPE, withLayout, withoutLayout.size(), withoutLayout);

        // The vacuity guard. A registry that failed to load, or a rename of the layout post-type,
        // would leave no subjects at all — and an empty loop agrees with every claim including this
        // one. SH1121 exists because a package went unchecked; a check that checks nothing is that
        // same failure wearing this test's name.
        assertThat(withLayout)
                .as("SH1121: no blueprint appears to ship a %s, so this test is measuring nothing. "
                        + "Either the registry did not load or the post-type was renamed — in which "
                        + "case rename LAYOUT_TYPE here, because the subjects are the point.",
                        LAYOUT_TYPE)
                .isNotEmpty();

        Map<String, List<ShAgentReport.Finding>> dirty = new LinkedHashMap<>();
        for (String name : withLayout) {
            String site = freshSite(name);
            blueprintService.apply(name, site, paramsFor(all.get(name)), "admin");
            ShAgentReport report = verifyService.verify(site, "a11y", 200);
            System.out.printf("SH1121:   %s — %d page(s) checked, %d finding(s)%n",
                    name, report.checked(), report.findings().size());
            if (!report.findings().isEmpty()) {
                dirty.put(name, report.findings());
            }
        }

        assertThat(dirty)
                .as("""
                        SH1121: a blueprint this product recommends as a starting point builds pages \
                        our own accessibility check reports on. That is the whole defect: `landing` \
                        shipped with no <main> and no h1 on any page, and nothing said so because no \
                        benchmark happened to apply it. The repair follows `marketing-site`'s — wrap \
                        the section loop in <main>, and choose <h1> for the first section's heading \
                        through the `@first` data frame `getRelation` has published since SH772. \
                        Findings: %s""", describe(dirty))
                .isEmpty();
    }

    /**
     * Does this package's content declare a layout Shio renders through?
     *
     * <p>Read off the content documents rather than the manifest: a manifest names its content files
     * and says nothing about what is in them, and the question here is what the package <em>builds</em>.
     * A blueprint that carries its markup in {@code files/} is not a subject — Shio never renders that
     * tree, so our gate has no page to judge and a claim about it would be a claim about nothing.</p>
     */
    private static boolean shipsALayout(ShBlueprint blueprint) {
        for (JsonNode document : blueprint.content()) {
            for (JsonNode post : document.path("posts")) {
                if (LAYOUT_TYPE.equals(post.path("type").asString(""))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A value for every parameter the package requires, and nothing else.
     *
     * <p>Discovered from the manifest for the same reason the subjects are: a hard-coded
     * {@code {"brand": "..."}} works for the two packages that want a brand today and refuses the
     * one added next year, which would make this test's failure a message about this test. Defaults
     * are left alone — a package's own default is part of what it ships, and overriding it would
     * check a configuration nobody gets.</p>
     */
    private static JsonNode paramsFor(ShBlueprint blueprint) {
        ObjectNode params = new ObjectMapper().createObjectNode();
        for (JsonNode required : blueprint.manifest().params().path("required")) {
            params.put(required.asString(), "Acme");
        }
        return params;
    }

    /** A site of its own per package, since a blueprint composes a whole site. */
    private String freshSite(String blueprint) {
        String name = "sh1121-" + blueprint + "-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName(name);
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            root.setOwner("admin");
            entityManager.persist(root);
        });
        return name;
    }

    /** The findings, in the failure message, because a count is not something anybody can repair. */
    private static String describe(Map<String, List<ShAgentReport.Finding>> dirty) {
        StringBuilder said = new StringBuilder();
        for (Iterator<Map.Entry<String, List<ShAgentReport.Finding>>> it =
                dirty.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, List<ShAgentReport.Finding>> entry = it.next();
            said.append(entry.getKey()).append(": ");
            for (ShAgentReport.Finding finding : entry.getValue()) {
                said.append("[").append(finding.rule()).append(" at ").append(finding.address())
                        .append(" — ").append(finding.message()).append("] ");
            }
            if (it.hasNext()) {
                said.append("; ");
            }
        }
        return said.toString();
    }
}
