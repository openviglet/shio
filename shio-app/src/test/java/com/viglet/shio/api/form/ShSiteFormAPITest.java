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

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import com.viglet.shio.api.form.dto.ShSiteFormRequest;
import com.viglet.shio.persistence.model.form.ShSiteForm;
import com.viglet.shio.persistence.repository.form.ShSiteFormRepository;

/**
 * A site's form configuration, and the two defaults that point at refusal (SH632).
 *
 * <h2>What is worth asserting here</h2>
 *
 * <p>Not CRUD — this mirrors {@code ShSiteWebhookAPI} and repository-direct CRUD is the least
 * interesting thing about it. What matters is that the row cannot become an open door by accident,
 * because SH633's endpoint is reachable from a public page and reads exactly this:</p>
 *
 * <ul>
 *   <li><b>Creating is not enabling.</b> An operator sets the folder and the types, looks at it,
 *       and then turns it on.</li>
 *   <li><b>One row per site.</b> Two configurations mean the endpoint resolves to whichever sorts
 *       first, and an operator editing the other would see no effect.</li>
 *   <li><b>The site cannot be moved by an edit</b>, which would silently leave the first site
 *       accepting nothing.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShSiteFormAPITest {

    @Autowired
    private ShSiteFormAPI api;
    @Autowired
    private ShSiteFormRepository repository;

    private static ShSiteFormRequest request(String siteId, boolean enabled) {
        return new ShSiteFormRequest(siteId, "/contact", "Contact,Enquiry", enabled);
    }

    private static String site() {
        return "sh632-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("SH632: creating a configuration does not open the door")
    void creatingIsNotEnabling() {
        ResponseEntity<Object> created = api.create(request(site(), false), null);

        assertThat(created.getStatusCode().value()).isEqualTo(200);
        assertThat(created.getBody()).isInstanceOfSatisfying(ShSiteForm.class, form -> {
            assertThat(form.isEnabled())
                    .as("""
                            a configuration row that exists but has not been turned on must not be \
                            an open door — the direction every default on this path takes, because \
                            the caller of the endpoint that reads it is the internet (SH364, P7)""")
                    .isFalse();
            assertThat(form.getFolderUrl()).isEqualTo("/contact");
            assertThat(form.getAllowedTypes()).isEqualTo("Contact,Enquiry");
            assertThat(form.getOwner()).isEqualTo("system");
        });
    }

    @Test
    @DisplayName("SH632: a second configuration for one site is refused, naming the row to edit")
    void oneRowPerSite() {
        String siteId = site();
        api.create(request(siteId, false), null);

        ResponseEntity<Object> again = api.create(request(siteId, true), null);

        assertThat(again.getStatusCode().value())
                .as("""
                        two configurations for one site means the endpoint resolves to whichever \
                        sorts first, so an operator who edited the other would watch their change \
                        have no effect — a defect with no symptom at the place it is made""")
                .isEqualTo(409);
        assertThat(again.getBody().toString())
                .as("and the refusal names the row to edit rather than only saying no (P6)")
                .contains("PUT /api/v2/site-form/");
        assertThat(repository.findAllByOrderBySiteIdAsc().stream()
                .filter(form -> siteId.equals(form.getSiteId())).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("SH632: an update may change the destination but never the site")
    void theSiteIsNotMovable() {
        String siteId = site();
        ShSiteForm created = (ShSiteForm) api.create(request(siteId, false), null).getBody();

        ResponseEntity<ShSiteForm> updated = api.update(created.getId(),
                new ShSiteFormRequest(site(), "/enquiries", "Enquiry", true));

        assertThat(updated.getStatusCode().value()).isEqualTo(200);
        assertThat(updated.getBody().getSiteId())
                .as("""
                        moving a configuration to another site IS creating that site's \
                        configuration; doing it by edit would leave the first site silently \
                        accepting nothing""")
                .isEqualTo(siteId);
        assertThat(updated.getBody().getFolderUrl()).isEqualTo("/enquiries");
        assertThat(updated.getBody().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("SH632: a configuration with no site is refused with what to pass")
    void theSiteIsRequired() {
        ResponseEntity<Object> refused = api.create(new ShSiteFormRequest(null, "/x", "Contact", true), null);

        assertThat(refused.getStatusCode().value()).isEqualTo(400);
        assertThat(refused.getBody().toString())
                .as("a form configuration belongs to one site — that is what keeps the destination "
                        + "out of template content (SH632)")
                .contains("siteId is required");
    }
}
