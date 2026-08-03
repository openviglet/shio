/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport.Finding;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.render.ShTemplateEngine;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH251 — {@code shio verify} reports a template that interpolates a field nothing declares.
 *
 * <p>The defect this closes is the one SH192 explicitly could not: {@code {{headline}}} against a
 * post-type that renamed the field to {@code title} compiles, renders empty, and is
 * indistinguishable from content nobody wrote. It needed a <em>resolved</em> page, which is why it
 * is a lint and not a write-time refusal.</p>
 *
 * <p>The fixture is one site with a deliberate mix, because the interesting assertions are the
 * negative ones: the rule has to fire on the renamed field and stay silent on the reserved frames,
 * the row values inside a listing, the helper names, and the fields of the listed posts. A rule
 * with false positives here would be reported against every layout in every instance.</p>
 *
 * <p>Its own database, for {@link com.viglet.shio.render.ShPageRenderTest}'s reason (SH246).</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh251;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShTemplateFieldLintTest {

    private static final String LAYOUT = "SH251_LAYOUT";
    private static final String REGION = "SH251_LIST_REGION";

    /**
     * The layout, holding one real defect and a great deal that must not be reported: the frames,
     * a triple stash, a helper argument that is a declared field, and an {@code {{#if}}}.
     */
    private static final String LAYOUT_HTML = """
            <html><head><title>{{post.title}}</title><style>{{theme.css}}</style></head>
            <body>
              <h1>{{TITLE}}</h1>
              {{#if SUMMARY}}<p class="lede">{{SUMMARY}}</p>{{/if}}
              <div>{{{BODY}}}</div>
              <img src="{{file_url HERO}}">
              {{#getRelation HERO}}<i>{{title}}</i>{{/getRelation}}
              <p class="byline">{{headline}}</p>
              <div sh-region="SH251_LIST_REGION"></div>
              <footer>{{site.name}} — {{render.state}}</footer>
            </body></html>""";

    /**
     * The region: everything inside the block belongs to the listed posts, so only the one name no
     * post-type anywhere declares may be reported — and only as a warning.
     */
    private static final String REGION_HTML = """
            <ul>{{#query type="%s" limit=5}}
              <li><a href="{{link}}">{{title}}</a> <span>{{summary}}</span> <em>{{BODY}}</em>
              <b>{{nosuchfieldanywhere}}</b></li>
            {{else}}<li>none</li>{{/query}}</ul>
            <nav>{{#navigation folder="/"}}<a href="{{url}}">{{name}}</a>{{/navigation}}</nav>
            <div>{{#search q="x" type="%s" limit=3}}<span>{{summary}}</span>{{/search}}</div>
            <form>{{#form type="%s"}}<label for="{{name}}">{{label}}</label>
              <input type="{{input}}" data-w="{{widget}}" data-o="{{ordinal}}"
              {{#if required}}required{{/if}}>{{/form}}</form>""";

    @Autowired
    private ShAgentVerifyService verifyService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ShTemplateEngine engine;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String pageType;

    @BeforeAll
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        siteName = "sh251-" + suffix;
        pageType = "Sh251Page" + suffix;

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");

            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            site.setPostTypeLayout("{\"" + pageType + "\":\"" + LAYOUT + "\"}");
            entityManager.persist(site);

            ShFolder root = folder(site, when);

            // TITLE, SUMMARY, BODY and HERO are declared. `headline` is not — it is the renamed
            // field this whole rule exists for.
            ShPostType page = postType(pageType, (byte) 0, when,
                    "TITLE", "SUMMARY", "BODY", "HERO");
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when, "HTML");
            ShPostType regionType = postType(ShSystemPostType.REGION, (byte) 1, when, "HTML");

            post(site, root, layoutType, LAYOUT, "/layout", json("HTML", LAYOUT_HTML), when);
            post(site, root, regionType, REGION, "/region",
                    json("HTML", REGION_HTML.formatted(pageType, pageType, pageType)), when);
            post(site, root, page, "Home", "/",
                    json("TITLE", "Home", "SUMMARY", "s", "BODY", "<p>b</p>", "HERO", ""), when);
        });
    }

    private List<Finding> templateFindings() {
        ShAgentReport report = verifyService.verify(siteName, "content", 200);
        return report.findings().stream()
                .filter(finding -> "template-unknown-field".equals(finding.rule()))
                .toList();
    }

    @Test
    @DisplayName("SH247: a correct {{#form}} body is not a pile of undeclared fields")
    void aFormBodyIsReadAgainstTheFormsOwnKeys() {
        // `{{#form}}` yields a post-type's *declaration*, so its row keys are not a listing's.
        // The set existed and was read by nothing, which meant `{{label}}`, `{{input}}`,
        // `{{widget}}` and `{{ordinal}}` in a perfectly correct form were each reported as a
        // field no post-type declares. That is the one failure a field lint cannot afford: an
        // author who meets a false finding stops reading the true ones — and it is precisely the
        // drift the row-keys comment warns about, committed while adding the second set.
        assertThat(templateFindings())
                .extracting(Finding::field)
                .doesNotContain("label", "input", "widget", "ordinal", "required");
    }

    @Test
    @DisplayName("a field the bound post-type does not declare is an error naming both")
    void anUndeclaredFieldIsAnError() {
        Finding found = templateFindings().stream()
                .filter(finding -> "headline".equals(finding.field()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the renamed field was not reported"));

        assertThat(found.severity()).isEqualTo("error");
        assertThat(found.message())
                .as("""
                        an error, because the owner is known: the page's post-type is reachable \
                        through site.postTypeLayout, so this interpolation renders empty on every \
                        page of that type, always.""")
                .contains("headline")
                .contains(pageType)
                .contains("TITLE");
        assertThat(found.fix())
                .as("P6 — a renamed field is the usual cause, so the fix says so")
                .contains("Rename")
                .contains("post-type");
        assertThat(found.address())
                .as("""
                        addressed at the layout post, because that is the artefact somebody edits \
                        to fix it — not at the page, of which there could be a thousand.""")
                .isEqualTo("post:" + siteName + "/layout");
    }

    @Test
    @DisplayName("the reserved frames, the row values and the helper names are not reported")
    void thereAreNoFalsePositives() {
        assertThat(templateFindings())
                .as("""
                        The negative case is the one that decides whether this rule survives \
                        contact with a real instance. post./site./theme./render. are frames; \
                        link/title/summary are row values every listing supplies; file_url is a \
                        helper and HERO is its argument, which *is* declared; BODY inside the \
                        block belongs to the listed post and is declared too. Exactly two names \
                        in this fixture are wrong, and a third finding would mean the rule fires \
                        on correct templates.""")
                .extracting(Finding::field)
                .containsExactlyInAnyOrder("headline", "nosuchfieldanywhere");
    }

    @Test
    @DisplayName("SH362: the fixture calls every helper the engine registers")
    void theFixtureExercisesEveryRegisteredHelper() {
        // The question nobody asks about a fixture. `thereAreNoFalsePositives` was present,
        // passing and silent when SH247 registered `form`, because the fixture it judges held a
        // `{{#query}}` block and nothing else — so the sixth helper's row keys were never read
        // and SH360's defect reached a release under a green guard. A fixture is written once
        // against the world as it was, and nothing made it keep up.
        //
        // The engine is the single source of the capability set (SH260), so the list costs a
        // call. What is added here is only the comparison — and it fails naming the helper,
        // because "extend the fixture" is not actionable without knowing which one arrived.
        String fixture = LAYOUT_HTML + REGION_HTML;
        assertThat(engine.registeredNames())
                .as("""
                        an empty capability set would make the comparison below vacuous, which is \
                        this task's own defect one level up: a guard that passes because it read \
                        nothing.""")
                .isNotEmpty()
                .allSatisfy(name -> assertThat(fixture)
                        .as("""
                                `%s` is registered and no template in this fixture calls it, so \
                                every negative assertion in this class is silent about its rows. \
                                Add a block that uses it to LAYOUT_HTML or REGION_HTML — a \
                                correct one — and let thereAreNoFalsePositives judge it.""", name)
                        .contains(name));
    }

    @Test
    @DisplayName("a name no post-type declares is a warning inside a block, not an error")
    void insideABlockItIsAWarning() {
        Finding found = templateFindings().stream()
                .filter(finding -> "nosuchfieldanywhere".equals(finding.field()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the in-block reference was not reported"));

        assertThat(found.severity())
                .as("""
                        SH99's line: `ok` is false only for what the CMS can prove from inside \
                        itself. Which posts a helper lists is not enumerable, so the strongest \
                        honest claim is "no post-type anywhere declares this name".""")
                .isEqualTo("warning");
        assertThat(found.message()).contains("query").contains("no post-type");
        assertThat(found.address()).isEqualTo("post:" + siteName + "/region");
    }

    @Test
    @DisplayName("the warning does not fail the run and the error does")
    void severityDecidesTheGate() {
        ShAgentReport report = verifyService.verify(siteName, "content", 200);

        assertThat(report.ok())
                .as("an undeclared field on a bound type is a real defect, so the gate fails")
                .isFalse();
        assertThat(report.errors()).isPositive();
    }

    @Test
    @DisplayName("a site with no layout binding pays nothing and reports nothing")
    void anUnboundSiteIsSilent() {
        String other = "sh251-none-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName(other);
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);
            folder(site, Instant.parse("2026-01-01T00:00:00Z"));
        });

        // The reason this rule lives in `content` rather than a group of its own: with no
        // postTypeLayout there are no bindings to walk, so it costs nothing on a site that has no
        // render model at all.
        assertThat(verifyService.verify(other, "content", 200).findings())
                .noneMatch(finding -> "template-unknown-field".equals(finding.rule()));
    }

    // ── fixture ─────────────────────────────────────────────────────────────────────

    private ShFolder folder(ShSite site, Instant when) {
        ShFolder folder = new ShFolder();
        folder.setName("root");
        folder.setRootFolder((byte) 1);
        folder.setShSite(site);
        folder.setDate(when);
        folder.setOwner("admin");
        entityManager.persist(folder);
        return folder;
    }

    private ShPostType postType(String name, byte system, Instant when, String... fields) {
        ShPostType existing = entityManager
                .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                .setParameter("name", name)
                .getResultStream().findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }
        ShPostType type = new ShPostType();
        type.setName(name);
        type.setTitle(name);
        type.setSystem(system);
        type.setDate(when);
        type.setOwner("admin");
        entityManager.persist(type);

        ShWidget text = entityManager
                .createQuery("select w from ShWidget w where w.name = :name", ShWidget.class)
                .setParameter("name", ShSystemWidget.TEXT)
                .getResultStream().findFirst().orElseThrow();
        int ordinal = 1;
        for (String field : fields) {
            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setName(field);
            attr.setLabel(field);
            attr.setOrdinal(ordinal++);
            attr.setRequired((byte) 0);
            attr.setIsTitle((byte) 0);
            attr.setIsSummary((byte) 0);
            attr.setShWidget(text);
            attr.setShPostType(type);
            entityManager.persist(attr);
        }
        entityManager.flush();
        return type;
    }

    private void post(ShSite site, ShFolder folder, ShPostType type, String title, String furl,
                      String data, Instant when) {
        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(ShPostState.DRAFT);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(type);
        post.setTitle(title);
        post.setFurl(furl);
        post.setData(data);
        post.setDate(when);
        post.setOwner("admin");
        entityManager.persist(post);
    }

    private String json(String... pairs) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(pairs[i]).append("\":\"")
                    .append(pairs[i + 1].replace("\\", "\\\\").replace("\"", "\\\"")
                            .replace("\n", "\\n").replace("\r", ""))
                    .append('"');
        }
        return sb.append('}').toString();
    }
}
