/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.url.ShSiteFurlConflictException;
import com.viglet.shio.url.ShSiteFurlNaming;

/**
 * SH744 — one writer decides a site's address, and it applies the posts' rule.
 *
 * <p>There were two and they disagreed: the exchange import checked whether the furl was taken and
 * appended a short id, and {@code create} took the caller's value or the name's slug and saved it
 * unchecked. Two sites could therefore answer at one address, which
 * {@link com.viglet.shio.persistence.repository.site.ShSiteRepository#findByFurl} resolves to
 * <b>one</b> row — so the database chose which site the renderer served.</p>
 *
 * <p>The rule was not re-decided here. SH81/SH651 settled it for a post: a <b>declared</b> address
 * is identity and is refused when taken; a <b>derived</b> one has no claim and is numbered.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShSiteFurlNamingTest {

    @Autowired
    private ShSiteWriteService shSiteWriteService;
    @Autowired
    private ShSiteFurlNaming shSiteFurlNaming;

    @Test
    @DisplayName("SH744: a declared address that is taken is refused, not renumbered")
    void aDeclaredFurlIsRefusedWhenTaken() {
        String furl = "ut-sh744-declared-" + UUID.randomUUID().toString().substring(0, 8);
        shSiteWriteService.create(payload(furl), "admin");

        // The assertion that would have caught it. Before SH744 this second create SUCCEEDED and
        // left two live sites answering at one address, with `findByFurl` picking one — the
        // SH278/SH279 shape: invisible on H2, wrong on another engine.
        assertThatThrownBy(() -> shSiteWriteService.create(payload(furl), "admin"))
                .as("SH744: a declared furl is identity (SH81/SH651), so the honest answer is to "
                        + "refuse and name the occupant rather than silently save `%s-2` into a "
                        + "link the caller is about to send", furl)
                .isInstanceOf(ShSiteFurlConflictException.class)
                .hasMessageContaining(furl);
    }

    @Test
    @DisplayName("SH744: a derived address that is taken is numbered, because a name is not a promise")
    void aDerivedFurlIsNumberedWhenTaken() {
        // Both sites carry the same NAME and declare no furl, so both derive the same slug. This
        // is the other half of the rule and the reason the refusal above is not simply "refuse
        // everything": nobody promised this address, so taking the next one surprises nobody.
        String name = "ut-sh744-derived-" + UUID.randomUUID().toString().substring(0, 8);
        ShSite first = shSiteWriteService.create(named(name), "admin");
        ShSite second = shSiteWriteService.create(named(name), "admin");

        assertThat(first.getFurl()).isNotBlank();
        assertThat(second.getFurl())
                .as("SH744: derived, so numbered — and numbered rather than given a random short "
                        + "id, which is what the import used to do and what a human cannot predict")
                .isEqualTo(first.getFurl() + "-2")
                .isNotEqualTo(first.getFurl());
    }

    @Test
    @DisplayName("SH744: the naming owner answers both questions, so the two writers cannot drift")
    void theOwnerAnswersBothQuestions() {
        // Guards the guard: an owner that said everything was free would make the refusal above
        // unreachable and the numbering silently wrong, and both tests would still pass on a
        // fixture that happened not to collide.
        String furl = "ut-sh744-owner-" + UUID.randomUUID().toString().substring(0, 8);
        assertThat(shSiteFurlNaming.isFree(furl)).isTrue();

        shSiteWriteService.create(payload(furl), "admin");

        assertThat(shSiteFurlNaming.isFree(furl)).isFalse();
        assertThat(shSiteFurlNaming.freeFurl(furl, null)).isEqualTo(furl + "-2");
        assertThatThrownBy(() -> shSiteFurlNaming.requireFree(furl))
                .isInstanceOf(ShSiteFurlConflictException.class);
    }

    private static ShSite payload(String furl) {
        ShSite payload = named("ut-sh744-" + UUID.randomUUID());
        payload.setFurl(furl);
        return payload;
    }

    private static ShSite named(String name) {
        ShSite payload = new ShSite();
        payload.setName(name);
        payload.setDescription("Seeded by ShSiteFurlNamingTest");
        return payload;
    }
}
