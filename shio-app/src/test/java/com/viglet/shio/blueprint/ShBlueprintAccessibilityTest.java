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
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.verify.ShAgentVerifyService;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.api.staticfile.ShStaticFileAPI;
import com.viglet.shio.api.staticfile.ShStaticFileItem;
import com.viglet.shio.api.staticfile.ShStaticFileListing;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.blueprint.dto.ShBlueprint;
import com.viglet.shio.blueprint.dto.ShBlueprintApplied;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.api.form.ShFormSubmissionAPI;
import com.viglet.shio.api.form.ShSiteFormAPI;
import com.viglet.shio.api.form.dto.ShSiteFormRequest;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.token.ShApiToken;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.token.ShApiTokenRepository;
import com.viglet.shio.render.ShPageRenderService;
import com.viglet.shio.spring.security.auth.ShApiTokenFilter;

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
// SH1254 — the showcase is published from `marketplace/`, not the classpath, so the test reads that
// directory the way an operator's instance would; its layouts are then judged like any other.
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:shio-sh1121;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
                "shio.blueprint.path=../marketplace"})
@TestInstance(Lifecycle.PER_CLASS)
class ShBlueprintAccessibilityTest {

    /** SH1254 — the showcase package, the one subject read from outside the classpath. */
    private static final String SHOWCASE = "quilhara";

    /** What applying the showcase answered, in estimated tokens; -1 until the loop applies it. */
    private int showcaseApplyTokens = -1;

    /** SH1356 — what applying each judged package answered, in estimated tokens. */
    private final Map<String, Integer> applyTokens = new java.util.TreeMap<>();

    /** The post-type that makes a blueprint's pages Shio's to render, and so this test's to judge. */
    private static final String LAYOUT_TYPE = "PageLayout";

    @Autowired
    private ShBlueprintRegistry registry;
    @Autowired
    private ShBlueprintService blueprintService;
    @Autowired
    private ShAgentVerifyService verifyService;
    @Autowired
    private ShStaticFileAPI staticFileAPI;
    @Autowired
    private ShSiteRepository siteRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;
    // SH1356 — what the contact form's end-to-end test needs beside the a11y sweep. In this class
    // rather than its own: the suite has no context slot left (SH423) and one database is claimed
    // by one class (the isolation lint), while the shared default context's post-types are other
    // tests' to mutate, which is the failure that put it here.
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShPageRenderService renderService;
    @Autowired
    private ShSiteFormAPI siteFormAPI;
    @Autowired
    private ShApiTokenRepository apiTokenRepository;
    @Autowired
    private ShPostUnifiedRepository postRepository;

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
            var applied = blueprintService.apply(name, site, paramsFor(all.get(name)), "admin");
            int tokens = com.viglet.shio.api.agent.ShTokenBudget.estimate(
                    new ObjectMapper().writeValueAsString(applied));
            applyTokens.put(name, tokens);
            if (SHOWCASE.equals(name)) {
                showcaseApplyTokens = tokens;
            }
            if (SHOWCASE.equals(name)) {
                assertFontsAreUsed(site);
            }
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

        // SH1254 — the showcase is reached outside the classpath (shio.blueprint.path), and what its
        // apply answers is measured: the report grows with every op, so P3 makes it a number.
        assertThat(withLayout)
                .as("SH1254: the showcase is published from marketplace/ and must be judged here too")
                .contains(SHOWCASE);
        int ceiling = Integer.parseInt(budgets().getProperty("blueprint.apply.quilhara"));
        System.out.printf("SH1254: applying %s answered ~%d tokens (ceiling %d)%n",
                SHOWCASE, showcaseApplyTokens, ceiling);
        assertThat(showcaseApplyTokens)
                .as("SH1254: the showcase's apply report is over its recorded ceiling; a package that "
                        + "grew is a cost every agent that applies it pays, so raise the number with "
                        + "the reason beside it in token-budgets.properties")
                .isBetween(1, ceiling);

        // SH1356 — the same measurement for every package that declares a ceiling, so the phone's
        // first run (small-business) is a number too; a package with no row is printed, not judged.
        assertThat(budgets().getProperty("blueprint.apply.small-business"))
                .as("SH1356: the small-business package's apply is measured, like the showcase's")
                .isNotNull();
        applyTokens.forEach((name, tokens) -> {
            String row = budgets().getProperty("blueprint.apply." + name);
            System.out.printf("SH1356: applying %s answered ~%d tokens (ceiling %s)%n", name, tokens, row);
            if (row != null) {
                assertThat(tokens)
                        .as("SH1356: %s's apply report is over its recorded ceiling; raise it with the "
                                + "reason beside it in token-budgets.properties", name)
                        .isBetween(1, Integer.parseInt(row));
            }
        });
    }

    /**
     * SH1385 — the showcase plans, which is what the Marketplace dialog does before it applies.
     *
     * <p>Every check here applied Quilhara and none planned it, and a plan is a dry run: the op that
     * binds the layouts writes nothing, so the op after it naming {@code InfoPage} as the page type was
     * judged against the database alone and answered 400 page-type-not-bound at ops[2]. The first thing
     * the README offers a new user failed with nothing on screen.</p>
     */
    @Test
    @DisplayName("SH1385: the showcase plans on a fresh site and on one the plan creates")
    void theShowcasePlansOnAFreshSite() {
        Map<String, ShBlueprint> all = registry.all();
        assertThat(all).as("SH1385: the showcase is read from marketplace/ here").containsKey(SHOWCASE);
        JsonNode params = paramsFor(all.get(SHOWCASE));

        ShBlueprintApplied onAnEmptySite = blueprintService.plan(SHOWCASE, freshSite("plan"), params, "admin");
        assertThat(onAnEmptySite.content().results())
                .as("SH1385: a plan of the showcase on a site with no bindings answers, it does not refuse")
                .isNotEmpty();

        String created = "sh1385-" + UUID.randomUUID().toString().substring(0, 8);
        ShBlueprintApplied onANewSite = blueprintService.plan(SHOWCASE, created, params, "admin",
                new ShBlueprintWriteService.Options(true, true));
        assertThat(onANewSite.site())
                .as("SH1385: the Marketplace's own path, a plan that creates the site it names")
                .isEqualTo(created);
    }

    private static final String SB_ORIGIN = "https://bakery.example";
    private static final String SB_TOKEN = "sh1356-form-key";

    /**
     * SH1356 — a contact form on a blueprint page submits with script off.
     *
     * <p>The task's own criterion, end to end: apply {@code small-business}, see the contact page draw
     * no form while its token is empty (applying a package never opens the door, SH632), enable the
     * site's form configuration and fill the token as a curator would, then post exactly the fields
     * the rendered form carries, urlencoded, through the whole security chain.</p>
     */
    @Test
    @DisplayName("SH1356: a contact form on a blueprint page submits with script off")
    void aContactFormOnABlueprintPageSubmitsWithScriptOff() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        String site = freshSite("small-business-form");
        String siteId = siteRepository.findByName(site).getId();
        blueprintService.apply("small-business", site,
                new ObjectMapper().readTree("{\"brand\":\"Bakery\"}"), "admin");

        assertThat(renderService.render(site, "/contact", ShPostState.DRAFT).html())
                .as("SH1356: applying the package never opens the door; with no token there is no form")
                .doesNotContain("<form");

        siteFormAPI.create(new ShSiteFormRequest(siteId, "/messages", "Contact", true, SB_ORIGIN), null);
        ShApiToken token = new ShApiToken();
        token.setTitle("contact form");
        token.setToken(SB_TOKEN);
        token.setEnabled(true);
        token.setScope(ShApiTokenScope.FORM);
        token.setSiteIds(Set.of(siteId));
        token.setOwner("admin");
        token.setCreatedDate(Instant.now());
        apiTokenRepository.save(token);
        fillFormToken(siteId, SB_TOKEN);

        String html = renderService.render(site, "/contact", ShPostState.DRAFT).html();
        assertThat(html)
                .as("SH1356: the form posts natively to this site's endpoint with its hidden fields")
                .contains("method=\"post\" action=\"/api/v2/cda/form/" + site + "\"")
                .contains("name=\"" + ShFormSubmissionAPI.TYPE_FIELD + "\" value=\"Contact\"")
                .contains("name=\"" + ShApiTokenFilter.FORM_TOKEN_FIELD + "\" value=\"" + SB_TOKEN + "\"")
                .contains("name=\"name\"").contains("name=\"email\"")
                .contains("<textarea id=\"shio-form-message\" name=\"message\" required></textarea>");

        MvcResult sent = mvc.perform(post("/api/v2/cda/form/" + site)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Origin", SB_ORIGIN).header("Referer", SB_ORIGIN + "/contact")
                        .param(ShFormSubmissionAPI.TYPE_FIELD, "Contact")
                        .param(ShApiTokenFilter.FORM_TOKEN_FIELD, SB_TOKEN)
                        .param(ShFormSubmissionAPI.HONEYPOT_FIELD, "")
                        .param("name", "Ana").param("email", "ana@example.com")
                        .param("message", "Do you bake on Sundays?"))
                .andReturn();
        assertThat(sent.getResponse().getStatus())
                .as("SH1356: a browser with script off follows the 303 back to the page: "
                        + sent.getResponse().getContentAsString())
                .isEqualTo(303);
        assertThat(sent.getResponse().getHeader("Location")).isEqualTo(SB_ORIGIN + "/contact?shio_form=sent");

        assertThat(postRepository.findAll().stream()
                .filter(each -> each.getShSite() != null && siteId.equals(each.getShSite().getId()))
                .filter(each -> each.getData() != null && each.getData().contains("Do you bake on Sundays?"))
                .toList())
                .as("SH1356: the message is a Contact draft in the folder the site named")
                .singleElement()
                .satisfies(each -> assertThat(each.getState()).isEqualTo(ShPostState.DRAFT));
    }

    private static final String QUILHARA_ORIGIN = "https://quilhara.example";
    private static final String QUILHARA_TOKEN = "sh1387-form-key";

    /**
     * SH1387 — the showcase's trip enquiry submits with script off, once a curator opens it.
     *
     * <p>The matrix promised a contact form and a trip enquiry and Quilhara rendered no {@code <form>}
     * on either page. The package now carries both, closed the way {@code small-business}'s is (SH632):
     * this applies it, sees the trip page draw no form, opens the site's form as a curator would, and
     * posts what the rendered form carries. The Portuguese trip page labels its fields from its own
     * string table, so the form is not an English island on a Portuguese page.</p>
     */
    @Test
    @DisplayName("SH1387: the showcase's trip enquiry submits with script off once the site's form is opened")
    void theShowcaseTripEnquirySubmitsWithScriptOff() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        String site = freshSite("quilhara-form");
        String siteId = siteRepository.findByName(site).getId();
        blueprintService.apply(SHOWCASE, site, paramsFor(registry.all().get(SHOWCASE)), "admin");

        assertThat(renderService.render(site, "/trips/wind-crest-overnight", ShPostState.DRAFT).html())
                .as("SH1387: applying the showcase never opens the door; with no token there is no form")
                .doesNotContain("<form");

        siteFormAPI.create(new ShSiteFormRequest(siteId, "/enquiries", "Enquiry", true, QUILHARA_ORIGIN), null);
        ShApiToken token = new ShApiToken();
        token.setTitle("enquiry form");
        token.setToken(QUILHARA_TOKEN);
        token.setEnabled(true);
        token.setScope(ShApiTokenScope.FORM);
        token.setSiteIds(Set.of(siteId));
        token.setOwner("admin");
        token.setCreatedDate(Instant.now());
        apiTokenRepository.save(token);
        fillFormToken(siteId, QUILHARA_TOKEN);

        for (String page : List.of("/trips/wind-crest-overnight", "/contact")) {
            assertThat(renderService.render(site, page, ShPostState.DRAFT).html())
                    .as("SH1387: %s posts natively to this site's endpoint with its hidden fields", page)
                    .contains("method=\"post\" action=\"/api/v2/cda/form/" + site + "\"")
                    .contains("name=\"" + ShFormSubmissionAPI.TYPE_FIELD + "\" value=\"Enquiry\"")
                    .contains("name=\"" + ShApiTokenFilter.FORM_TOKEN_FIELD + "\" value=\"" + QUILHARA_TOKEN + "\"")
                    .contains("<label for=\"q-form-name\">Your name</label>")
                    .contains("<textarea id=\"q-form-message\" name=\"message\" required></textarea>");
        }
        assertThat(renderService.render(site, "/viagens/travessia-crista-do-vento", ShPostState.DRAFT).html())
                .as("SH1387: the Portuguese trip page labels the fields in Portuguese")
                .contains("<label for=\"q-form-name\">Seu nome</label>")
                .contains(">Enviar pergunta</button>");
        // The sweep above judges the package as applied, which is closed; this judges it open.
        assertThat(verifyService.verify(site, "a11y", 200).findings())
                .as("SH1387: with the form open, the showcase's pages still pass the a11y check")
                .isEmpty();

        MvcResult sent = mvc.perform(post("/api/v2/cda/form/" + site)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Origin", QUILHARA_ORIGIN)
                        .header("Referer", QUILHARA_ORIGIN + "/trips/wind-crest-overnight")
                        .param(ShFormSubmissionAPI.TYPE_FIELD, "Enquiry")
                        .param(ShApiTokenFilter.FORM_TOKEN_FIELD, QUILHARA_TOKEN)
                        .param(ShFormSubmissionAPI.HONEYPOT_FIELD, "")
                        .param("name", "Rafa").param("email", "rafa@example.com")
                        .param("message", "Is there room for two on the March overnight?"))
                .andReturn();
        assertThat(sent.getResponse().getStatus())
                .as("SH1387: a browser with script off follows the 303 back to the trip: "
                        + sent.getResponse().getContentAsString())
                .isEqualTo(303);
        assertThat(postRepository.findAll().stream()
                .filter(each -> each.getShSite() != null && siteId.equals(each.getShSite().getId()))
                .filter(each -> each.getData() != null
                        && each.getData().contains("Is there room for two on the March overnight?"))
                .toList())
                .as("SH1387: the enquiry is an Enquiry draft in the folder the site named")
                .singleElement()
                .satisfies(each -> assertThat(each.getState()).isEqualTo(ShPostState.DRAFT));
    }

    @Autowired
    private com.viglet.shio.api.form.ShSiteFormOpener siteFormOpener;

    /**
     * SH1387 — the Marketplace's "accept enquiries now": the showcase proposes its destination, the
     * plan names it, and the administrator's one act opens every closed form section so a browser on
     * the instance's own origin can submit. Applying still opens nothing.
     */
    @Test
    @DisplayName("SH1387: opening the showcase's proposed forms makes the trip page take an enquiry")
    void theProposedFormsOpenInOneAct() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        String site = freshSite("quilhara-open");
        blueprintService.apply(SHOWCASE, site, paramsFor(registry.all().get(SHOWCASE)), "admin");
        var forms = registry.all().get(SHOWCASE).manifest().forms();
        assertThat(forms).as("SH1387: the showcase proposes where its enquiries go").isNotNull();
        assertThat(forms.folder()).isEqualTo("/enquiries");
        assertThat(forms.types()).containsExactly("Enquiry");

        var plan = siteFormOpener.plan(site, forms);
        assertThat(plan.opened()).isFalse();
        assertThat(plan.configuration()).isEqualTo("created");
        assertThat(plan.sections()).as("SH1387: the plan counts the closed form sections it would fill").isPositive();
        assertThat(renderService.render(site, "/trips/wind-crest-overnight", ShPostState.DRAFT).html())
                .as("SH1387: planning opens nothing").doesNotContain("<form");

        var opened = siteFormOpener.open(site, forms, "admin");
        assertThat(opened.opened()).isTrue();
        assertThat(opened.enabled()).isTrue();
        assertThat(opened.sections()).isEqualTo(plan.sections());
        String origin = opened.origins().getFirst();
        assertThat(origin).as("SH1387: the instance's own origin may submit").startsWith("http");
        String html = renderService.render(site, "/trips/wind-crest-overnight", ShPostState.DRAFT).html();
        java.util.regex.Matcher token = java.util.regex.Pattern
                .compile("name=\"" + ShApiTokenFilter.FORM_TOKEN_FIELD + "\" value=\"([^\"]+)\"").matcher(html);
        assertThat(token.find()).as("SH1387: the trip page draws its form with the site's token").isTrue();

        MvcResult sent = mvc.perform(post("/api/v2/cda/form/" + site)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Origin", origin)
                        .param(ShFormSubmissionAPI.TYPE_FIELD, "Enquiry")
                        .param(ShApiTokenFilter.FORM_TOKEN_FIELD, token.group(1))
                        .param(ShFormSubmissionAPI.HONEYPOT_FIELD, "")
                        .param("name", "Ana").param("email", "ana@example.com")
                        .param("message", "SH1387 opened in one act"))
                .andReturn();
        assertThat(sent.getResponse().getStatus())
                .as("SH1387: an install opened from the Marketplace accepts a trip enquiry: "
                        + sent.getResponse().getContentAsString())
                .isIn(201, 303);

        var again = siteFormOpener.open(site, forms, "admin");
        assertThat(again.configuration())
                .as("SH1387: a second opening keeps the configuration it made").isEqualTo("kept");
        assertThat(again.sections()).as("SH1387: and fills nothing that already has a token").isZero();
    }

    /** The curator's edit: the site's FORM token on the contact form section. */
    private void fillFormToken(String siteId, String value) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<ShPostUnified> sections = entityManager
                    .createQuery("select p from ShPostUnified p where p.shSite.id = :site", ShPostUnified.class)
                    .setParameter("site", siteId).getResultList().stream()
                    .filter(each -> each.getData() != null && each.getData().contains("\"formType\""))
                    .toList();
            assertThat(sections).as("the package ships one form section").isNotEmpty();
            for (ShPostUnified section : sections) {
                section.setData(section.getData().replaceAll("\"formToken\"\\s*:\\s*\"\"",
                        "\"formToken\":\"" + value + "\""));
            }
        });
    }

    /**
     * SH1268 — the showcase self-hosts its fonts through the theme's {@code @font-face}, which no
     * File field names, and every page loads them. With the page check run they are used; without
     * it the rule falls back to the reference index, and the report says that is what it did.
     */
    private void assertFontsAreUsed(String site) {
        ShAgentReport withPages = verifyService.verify(site, "content,page", 200);
        assertThat(withPages.findings())
                .as("SH1268: a font every page loads was reported unused, and the fix offered was "
                        + "to delete it")
                .noneMatch(finding -> "asset-unused".equals(finding.rule())
                        && finding.message().contains(".woff2"));

        ShAgentReport contentOnly = verifyService.verify(site, "content", 200);
        assertThat(contentOnly.findings())
                .as("SH1268: without the page check the index is all there is, so the fonts read "
                        + "as unused there; this is what the note below has to explain")
                .anyMatch(finding -> "asset-unused".equals(finding.rule()));
        assertThat(contentOnly.notes())
                .as("SH1268: a content-only run says the page check did not count loaded assets")
                .anyMatch(note -> note.contains("checks=page"));

        // SH1280 — the media listing and `shio assets prune` mean the same thing by "used": with
        // `loads=true` each font is counted as loaded by every page rendered, so prune keeps it.
        ShStaticFileListing listing = staticFileAPI.list(null, siteRepository.findByName(site).getId(),
                null, null, 0, 500, true);
        List<ShStaticFileItem> fonts = listing.items().stream()
                .filter(item -> item.fileName() != null && item.fileName().endsWith(".woff2"))
                .toList();
        System.out.printf("SH1280: %d font(s); rendered %s of %s page(s); loadedBy %s%n", fonts.size(),
                listing.pagesRendered(), listing.pagesTotal(),
                fonts.stream().map(ShStaticFileItem::loadedBy).toList());
        assertThat(fonts).as("SH1280: the showcase ships three self-hosted fonts").hasSize(3);
        assertThat(listing.pagesRendered())
                .as("SH1280: loads=true rendered every page, so a zero would be a fact")
                .isPositive().isEqualTo(listing.pagesTotal());
        assertThat(fonts)
                .as("SH1280: a font no field names and every page fetches was listed reclaimable by "
                        + "prune while verify said it was used; loadedBy is the count both now read")
                .allMatch(font -> Integer.valueOf(listing.pagesRendered()).equals(font.loadedBy()));
        assertThat(staticFileAPI.list(null, siteRepository.findByName(site).getId(), null, null, 0, 500,
                false).items())
                .as("SH1280: and a plain listing renders nothing, so loadedBy stays unasked")
                .allMatch(item -> item.loadedBy() == null);

        // SH1283 — the package names its page type, so an empty folder offers InfoPage rather than
        // the catalogue's first type ("Novo How this was built" at the site root).
        assertThat(siteRepository.findByName(site).getPageType())
                .as("SH1283: applying Quilhara leaves its site with the page type the package declares")
                .isEqualTo("InfoPage");
    }

    private static java.util.Properties budgets() {
        java.util.Properties properties = new java.util.Properties();
        try (var in = ShBlueprintAccessibilityTest.class.getResourceAsStream("/token-budgets.properties")) {
            properties.load(in);
        } catch (java.io.IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
        return properties;
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
