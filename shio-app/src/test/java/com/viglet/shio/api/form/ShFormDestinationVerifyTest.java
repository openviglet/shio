/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.form;

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

import com.viglet.shio.api.agent.verify.ShAgentVerifyService;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.form.ShSiteForm;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.form.ShSiteFormRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1066 — verify names a form whose destination folder is gone.
 *
 * <p>The submission endpoint already refuses such a site, with a 500 only the visitor sees. What was
 * missing is the same fact where the people who can fix it look: the site reported clean while
 * every submission to it was refused. Verify reads the destination through the endpoint's own
 * resolver, so this pins that a missing folder is an error, that creating it clears the error, and
 * that a switched-off form is not reported, since an operator may still be setting it up.</p>
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShFormDestinationVerifyTest {

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ShAgentVerifyService verifyService;
    @Autowired
    private ShSiteFormRepository shSiteFormRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String siteId;
    private String rootId;
    private String formId;

    @BeforeAll
    void seed() {
        siteName = "sh1066dest-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(EPOCH);
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(EPOCH);
            root.setOwner("admin");
            entityManager.persist(root);
            rootId = root.getId();
        });
        ShSiteForm form = new ShSiteForm();
        form.setSiteId(siteId);
        form.setFolderUrl("/leads");
        form.setAllowedTypes("Article");
        form.setEnabled(true);
        form.setCreatedDate(EPOCH);
        form.setOwner("admin");
        formId = shSiteFormRepository.saveAndFlush(form).getId();
    }

    @Test
    @DisplayName("SH1066: an enabled form pointing at a missing folder is an error, until it exists")
    void aMissingDestinationIsNamed() {
        assertThat(destinationFindings())
                .as("SH1066: every submission to this site is refused, and verify said nothing")
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.severity()).isEqualTo("error");
                    assertThat(finding.address()).isEqualTo("site:" + siteName);
                    assertThat(finding.fix()).contains("/api/v2/site-form/" + formId);
                });

        ShSiteForm off = shSiteFormRepository.findById(formId).orElseThrow();
        off.setEnabled(false);
        shSiteFormRepository.saveAndFlush(off);
        assertThat(destinationFindings())
                .as("SH1066: a switched-off form is an operator still setting it up, not a defect")
                .isEmpty();

        off.setEnabled(true);
        shSiteFormRepository.saveAndFlush(off);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShFolder leads = new ShFolder();
            leads.setName("leads");
            leads.setParentFolder(entityManager.find(ShFolder.class, rootId));
            leads.setDate(EPOCH);
            leads.setOwner("admin");
            entityManager.persist(leads);
        });
        assertThat(destinationFindings())
                .as("SH1066: the folder exists now, and the finding must go with the cause")
                .isEmpty();
    }

    private List<ShAgentReport.Finding> destinationFindings() {
        return verifyService.verify(siteName, null, 500).findings().stream()
                .filter(finding -> "form-destination-missing".equals(finding.rule()))
                .toList();
    }
}
