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
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH383 — {@code translation-missing}, and the three ways it must NOT behave.
 *
 * <h2>Why the negative cases are most of this file</h2>
 *
 * <p>The rule is easy to write and easy to make useless. The naive version — warn for every post
 * missing any of the site's locales — emits 499 findings on a site with 500 English pages and one
 * Portuguese page, and a report nobody reads is worse than no rule. So what is asserted here is mostly
 * where it stays <b>silent</b>: on an untranslated page (nobody declared it translated, so there is no
 * evidence of intent), on a monolingual site (its locale set has one member, by construction rather
 * than by a flag), and on a <b>scoped</b> run — the finding is an <i>absence</i>, and a run that cannot
 * see the whole site cannot tell a missing translation from an excluded one.
 *
 * <p>It is a <b>warning</b>, never an error: a half-translated site is normal work in progress. The
 * write side already refuses a {@code translationOf} that resolves to nothing (SH348), which is a
 * different claim — a broken link rather than unfinished work.
 *
 * <p>Its own database: it persists posts and a post-type, and the SH164/SH239 benchmarks in the shared
 * context measure documents whose size depends on how many exist (SH246's reason).
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh383v;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentTranslationVerifyTest {

    private static final String RULE = "translation-missing";

    @Autowired
    private ShAgentVerifyService shAgentVerifyService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String multilingual;
    private String monolingual;
    private String withTemplates;

    @BeforeAll
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        multilingual = "sh383v-many-" + suffix;
        monolingual = "sh383v-one-" + suffix;
        withTemplates = "sh1264-tpl-" + suffix;

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShPostType page = postType("Sh383vPage" + suffix, when);

            ShSite many = site(multilingual, when);
            ShFolder manyRoot = folder(many, when);
            // A fully translated group: en, pt-br, de. No finding.
            String complete = UUID.randomUUID().toString();
            post(many, manyRoot, page, "Home", "/", when, "en", complete, complete);
            post(many, manyRoot, page, "Início", "/pt/", when, "pt-br", complete, null);
            post(many, manyRoot, page, "Startseite", "/de/", when, "de", complete, null);

            // A group translated into en and pt-br only — the site has de, so de is the gap.
            String partial = UUID.randomUUID().toString();
            post(many, manyRoot, page, "About", "/about", when, "en", partial, partial);
            post(many, manyRoot, page, "Sobre", "/pt/sobre", when, "pt-br", partial, null);

            // A page nobody has ever translated. Not a *missing translation* — an untranslated page,
            // and there is no evidence anybody intended otherwise. This is the case that would make
            // the rule unusable if it fired.
            String lone = UUID.randomUUID().toString();
            post(many, manyRoot, page, "Careers", "/careers", when, "en", lone, lone);

            // A whole site in one language: its locale set has one member, so nothing can be missing.
            ShSite one = site(monolingual, when);
            ShFolder oneRoot = folder(one, when);
            String pair = UUID.randomUUID().toString();
            post(one, oneRoot, page, "Home", "/", when, "en", pair, pair);
            post(one, oneRoot, page, "Home copy", "/copy", when, "en", pair, null);

            // SH1264 — a site written in en-us and pt-br whose layout carries `en`, as every
            // template does on write. The layout is not a language the pages lack.
            ShSite templated = site(withTemplates, when);
            ShFolder templatedRoot = folder(templated, when);
            String both = UUID.randomUUID().toString();
            post(templated, templatedRoot, page, "Home", "/", when, "en-us", both, both);
            post(templated, templatedRoot, page, "Início", "/inicio", when, "pt-br", both, null);
            post(templated, templatedRoot, systemType("PageLayout", when), "Layout", "/layout", when,
                    "en", null, null);
        });
    }

    @Test
    @DisplayName("SH1264: a layout's locale is not a language the site's translated pages lack")
    void templatesAreNotALanguage() {
        assertThat(findings(withTemplates))
                .as("SH1264: the only `en` row is a PageLayout, so a group in en-us and pt-br is "
                        + "complete; counting the layout asked every page for an en version")
                .isEmpty();
    }

    /** A system post-type by name, reusing the instance's row when the seed already made it. */
    private ShPostType systemType(String name, Instant when) {
        return entityManager.createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                .setParameter("name", name).getResultStream().findFirst()
                .orElseGet(() -> postType(name, when));
    }

    @Test
    @DisplayName("a group missing a language the rest of the site has is a warning, naming both sets")
    void aPartialGroupIsAWarning() {
        List<Finding> found = findings(null);

        assertThat(found).hasSize(1);
        Finding finding = found.getFirst();
        assertThat(finding.severity()).isEqualTo("warning");
        assertThat(finding.address()).isEqualTo("post:" + multilingual + "/about");
        assertThat(finding.message())
                .as("both halves, because the useful sentence is what exists AND what does not")
                .contains("en, pt-br")
                .contains("not into de");
        assertThat(finding.fix()).contains("translationOf").contains("work in progress");
    }

    @Test
    @DisplayName("a fully translated group is silent, so the rule is firing on the gap and not the shape")
    void aCompleteGroupIsSilent() {
        assertThat(findings(null))
                .noneMatch(finding -> finding.address().endsWith("/pt/")
                        || finding.address().equals("post:" + multilingual + "/"));
    }

    @Test
    @DisplayName("a page nobody translated is silent — an untranslated page is not a missing translation")
    void anUntranslatedPageIsSilent() {
        // The difference between a useful rule and 499 findings. A group of one carries no evidence
        // that anybody meant it to be translated; a group of two or more does.
        assertThat(findings(null))
                .noneMatch(finding -> finding.address().endsWith("/careers"));
    }

    @Test
    @DisplayName("a monolingual site is silent by construction, with no setting to leave off")
    void aMonolingualSiteIsSilent() {
        // The site's languages are the union of what its posts carry, so a one-language site has a
        // one-member set and nothing can be absent from it. A configured locale list would be a new
        // setting for every site to get wrong.
        assertThat(findings(monolingual)).isEmpty();
    }

    @Test
    @DisplayName("a scoped run does not report it at all, because an absence needs the whole site")
    void aScopedRunIsSilent() {
        // `translation-target-missing` downgrades to a warning when scoped, because its evidence is a
        // present thing. There is no equivalent downgrade here: scoped to one address, every group
        // would look like it was missing whatever the scope excluded — and this is the very page that
        // *is* missing a translation, so an unscoped run reports it and this one must not.
        ShAgentReport scoped = shAgentVerifyService.verify(multilingual, "content", 200,
                null, "post:" + multilingual + "/about", null);

        assertThat(scoped.partial()).isTrue();
        assertThat(scoped.findings()).noneMatch(finding -> RULE.equals(finding.rule()));
    }

    private List<Finding> findings(String site) {
        return shAgentVerifyService.verify(site, "content", 200).findings().stream()
                .filter(finding -> RULE.equals(finding.rule()))
                .filter(finding -> site != null || finding.address().startsWith("post:" + multilingual))
                .toList();
    }

    // ── fixture ─────────────────────────────────────────────────────────────────────

    private ShSite site(String name, Instant when) {
        ShSite site = new ShSite();
        site.setName(name);
        site.setDate(when);
        site.setOwner("admin");
        entityManager.persist(site);
        return site;
    }

    private ShFolder folder(ShSite site, Instant when) {
        ShFolder folder = new ShFolder();
        folder.setName("root");
        folder.setRootFolder((byte) 1);
        // Only a root folder carries shSite — SH128's invariant.
        folder.setShSite(site);
        folder.setPosition((byte) 1);
        folder.setDate(when);
        folder.setOwner("admin");
        entityManager.persist(folder);
        return folder;
    }

    private ShPostType postType(String name, Instant when) {
        ShPostType type = new ShPostType();
        type.setName(name);
        type.setTitle(name);
        type.setSystem((byte) 0);
        type.setDate(when);
        type.setOwner("admin");
        entityManager.persist(type);
        return type;
    }

    /** @param anchor the id to use, so the group's anchor is the post that names nothing (SH348) */
    private void post(ShSite site, ShFolder folder, ShPostType type, String title, String furl,
                      Instant when, String locale, String group, String anchor) {
        ShPostUnified post = new ShPostUnified();
        post.setId(anchor == null ? UUID.randomUUID().toString() : anchor);
        post.setState(ShPostState.DRAFT);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(type);
        post.setTitle(title);
        post.setFurl(furl);
        post.setData("{}");
        post.setDate(when);
        post.setOwner("admin");
        post.setLocale(locale);
        post.setTranslationGroupId(group);
        entityManager.persist(post);
    }
}
